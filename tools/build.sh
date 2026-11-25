#!/usr/bin/env bash
# Build, sign and verify the tool-store APK.
#
# Default flow (local build + local signing):
#   1. compile on this machine;
#   2. align and sign locally with a key stored outside the repository;
#   3. verify the signature, manifest, permissions, native runtime and absence
#      of any WebView/web assets; publish dist/tool-store-<version>.apk.
#
# Optional remote flow:
#   1. sync only source/runtime files to a unique workspace under an independent
#      remote root;
#   2. compile there behind flock; signing material is never copied remotely;
#   3. fetch the unsigned APK, align and sign locally;
#   4. verify and publish as above;
#   5. delete the unique remote workspace and result directory.
#
# Usage:
#   tools/build.sh                 # local build + local signing
#   tools/build.sh --remote --ssh user@host --remote-root ~/toolstore_build
#   tools/build.sh --check         # toolchain check; remote only with --remote
#   tools/build.sh --keep          # keep this run's remote and local workspaces
#   tools/build.sh --wait 1800
#
# Environment:
#   TOOLSTORE_KEYSTORE_DIR, TOOLSTORE_SSH, TOOLSTORE_REMOTE_ROOT,
#   TOOLSTORE_SDK_ROOT, TOOLSTORE_BUILD_TOOLS, TOOLSTORE_ANDROID_JAR
#
# Exit codes: 0 ok; 2 usage; 3 local preflight; 4 remote/toolchain;
#             6 compile/package; 7 fetch; 8 signing or verification;
#             75 remote build lock timeout.
#
# Signing key (generated on first build, never committed):
#   ${TOOLSTORE_KEYSTORE_DIR:-$HOME/.config/tool-store}/tool-store-release.jks
#   ${TOOLSTORE_KEYSTORE_DIR:-$HOME/.config/tool-store}/tool-store-release.pass
# Back the key up offline. It is never synced to a remote build host.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

if [ -t 1 ]; then
  C_RESET=$'\033[0m'; C_CYAN=$'\033[0;36m'; C_GREEN=$'\033[0;32m'
  C_YELLOW=$'\033[1;33m'; C_RED=$'\033[0;31m'
else
  C_RESET=""; C_CYAN=""; C_GREEN=""; C_YELLOW=""; C_RED=""
fi
log()   { printf '%s[build]%s %s\n'  "$C_CYAN"   "$C_RESET" "$*"; }
ok()    { printf '%s[build] [OK]%s %s\n' "$C_GREEN" "$C_RESET" "$*"; }
warn()  { printf '%s[build] [!]%s %s\n'  "$C_YELLOW" "$C_RESET" "$*" >&2; }
error() { printf '%s[build] [x]%s %s\n'  "$C_RED"    "$C_RESET" "$*" >&2; }
die() {
  local code="$1"; shift
  error "$*"
  exit "$code"
}
usage() {
  awk 'NR > 1 && /^#/ { sub(/^# ?/, ""); print; next } NR > 1 { exit }' "${BASH_SOURCE[0]}"
}

MODE="local"
DO_CHECK=0
KEEP=0
WAIT=900
REMOTE_SSH="${TOOLSTORE_SSH:-}"
REMOTE_ROOT_DISPLAY="${TOOLSTORE_REMOTE_ROOT:-~/toolstore_build}"
REMOTE_HOME=""
REMOTE_ROOT_ABS=""
REMOTE_WORK=""
REMOTE_RESULT=""
REMOTE_MARKER=".toolstore-build-root"

while [ $# -gt 0 ]; do
  case "$1" in
    --local)  MODE="local"; shift ;;
    --remote) MODE="remote"; shift ;;
    --check)  DO_CHECK=1; shift ;;
    --keep)   KEEP=1; shift ;;
    --ssh)
      [ $# -ge 2 ] || die 2 "--ssh needs user@host"
      REMOTE_SSH="$2"; shift 2 ;;
    --remote-root)
      [ $# -ge 2 ] || die 2 "--remote-root needs a directory"
      REMOTE_ROOT_DISPLAY="$2"; shift 2 ;;
    --wait)
      [ $# -ge 2 ] || die 2 "--wait needs seconds"
      WAIT="$2"; shift 2 ;;
    -h|--help) usage; exit 0 ;;
    *) error "unknown argument: $1"; usage >&2; exit 2 ;;
  esac
done
[[ "$WAIT" =~ ^[0-9]+$ ]] || die 2 "--wait must be a non-negative integer: $WAIT"
[ "$MODE" != "remote" ] || [ -n "$REMOTE_SSH" ] \
  || die 2 "remote mode requires --ssh user@host or TOOLSTORE_SSH"

SSH_OPTS=(
  -o BatchMode=yes
  -o ConnectTimeout=10
  -o StrictHostKeyChecking=accept-new
  -o ControlPath=none
  -o ForwardAgent=no
)

# ---------------------------------------------------------------- local tools

SDK_ROOT="${TOOLSTORE_SDK_ROOT:-${ANDROID_SDK_ROOT:-${ANDROID_HOME:-$HOME/Android/Sdk}}}"
[ -d "$SDK_ROOT" ] || die 3 "Android SDK not found: $SDK_ROOT"
if [ -n "${TOOLSTORE_BUILD_TOOLS:-}" ]; then
  BUILD_TOOLS="$TOOLSTORE_BUILD_TOOLS"
else
  BUILD_TOOLS=""
  for candidate in "$SDK_ROOT"/build-tools/35.0.0 "$SDK_ROOT"/build-tools/34.0.0 \
                   "$SDK_ROOT"/build-tools/36.0.0; do
    if [ -x "$candidate/apksigner" ]; then
      BUILD_TOOLS="$candidate"
      break
    fi
  done
  if [ -z "$BUILD_TOOLS" ]; then
    BUILD_TOOLS="$(ls -1d "$SDK_ROOT"/build-tools/*/ 2>/dev/null | sort -V | tail -1 || true)"
    BUILD_TOOLS="${BUILD_TOOLS%/}"
  fi
fi
APKSIGNER="$BUILD_TOOLS/apksigner"
ZIPALIGN="$BUILD_TOOLS/zipalign"
[ -x "$APKSIGNER" ] || die 3 "apksigner not found under $SDK_ROOT/build-tools"
[ -x "$ZIPALIGN" ] || die 3 "zipalign not found under $BUILD_TOOLS"
command -v keytool >/dev/null 2>&1 || die 3 "keytool is required"
command -v python3 >/dev/null 2>&1 || die 3 "python3 is required to generate the signing password"
command -v sha256sum >/dev/null 2>&1 || die 3 "sha256sum is required"
command -v unzip >/dev/null 2>&1 || die 3 "unzip is required for APK verification"

# ---------------------------------------------------------------- remote setup

remote_connect() {
  [ -n "$REMOTE_HOME" ] && return 0
  REMOTE_HOME="$(ssh "${SSH_OPTS[@]}" "$REMOTE_SSH" 'printf %s "$HOME"' 2>/dev/null)" \
    || die 4 "cannot connect to $REMOTE_SSH (passwordless ssh required)"
  [ -n "$REMOTE_HOME" ] || die 4 "$REMOTE_SSH returned an empty HOME"

  case "$REMOTE_ROOT_DISPLAY" in
    "~")   REMOTE_ROOT_ABS="$REMOTE_HOME" ;;
    "~/"*) REMOTE_ROOT_ABS="$REMOTE_HOME/${REMOTE_ROOT_DISPLAY:2}" ;;
    /*)    REMOTE_ROOT_ABS="$REMOTE_ROOT_DISPLAY" ;;
    *) die 2 "--remote-root must start with ~/ or /: $REMOTE_ROOT_DISPLAY" ;;
  esac
  while [ "${REMOTE_ROOT_ABS%/}" != "$REMOTE_ROOT_ABS" ]; do
    REMOTE_ROOT_ABS="${REMOTE_ROOT_ABS%/}"
  done
  [ -n "$REMOTE_ROOT_ABS" ] || die 2 "remote root is empty"
  [ "$REMOTE_ROOT_ABS" != "/" ] || die 2 "remote root must not be /"
  [[ "$REMOTE_ROOT_ABS" =~ ^[A-Za-z0-9._/-]+$ ]] \
    || die 2 "remote root contains unsupported characters: $REMOTE_ROOT_ABS"
  case "/$REMOTE_ROOT_ABS/" in
    *"/../"*|*"/./"*) die 2 "remote root must not contain . or ..: $REMOTE_ROOT_ABS" ;;
  esac
  [ "$REMOTE_ROOT_ABS" != "$REMOTE_HOME" ] \
    || die 2 "remote root must not be the remote home directory"
}

remote_check() {
  remote_connect
  log "remote check: $REMOTE_SSH:$REMOTE_ROOT_ABS"
  ssh "${SSH_OPTS[@]}" "$REMOTE_SSH" "ROOT='$REMOTE_ROOT_ABS' bash -s" <<'REMOTE'
set -uo pipefail
root="$ROOT"
missing=0
say() { printf '  %-22s %s\n' "$1" "$2"; }
need() {
  local name="$1" path="$2"
  if [ -e "$path" ]; then say "$name" "$path"; else say "$name" "MISSING"; missing=1; fi
}
if [ -f "$root/.toolstore-build-root" ]; then
  say "remote.root" "$root (managed)"
elif [ -e "$root" ] && [ -n "$(find "$root" -mindepth 1 -maxdepth 1 -print -quit 2>/dev/null)" ]; then
  say "remote.root" "$root (NON-EMPTY, unmanaged)"
  missing=1
else
  say "remote.root" "$root (will be created)"
fi
for bin in rsync flock python3 javac jar; do
  if command -v "$bin" >/dev/null 2>&1; then
    say "$bin" "$(command -v "$bin")"
  else
    say "$bin" "MISSING"
    missing=1
  fi
done
sdk="${TOOLSTORE_SDK_ROOT:-${ANDROID_SDK_ROOT:-${ANDROID_HOME:-$HOME/Android/Sdk}}}"
say "android.sdk" "$sdk"
[ -d "$sdk" ] || missing=1
bt=""
for c in "$sdk/build-tools/35.0.0" "$sdk/build-tools/34.0.0" "$sdk/build-tools/36.0.0"; do
  if [ -x "$c/aapt2" ]; then bt="$c"; break; fi
done
if [ -z "$bt" ] && [ -d "$sdk/build-tools" ]; then
  bt="$(find "$sdk/build-tools" -mindepth 1 -maxdepth 1 -type d 2>/dev/null | sort -V | tail -1)"
fi
say "build.tools" "${bt:-MISSING}"
[ -n "$bt" ] || missing=1
if [ -n "$bt" ]; then
  [ -x "$bt/aapt2" ] && [ -f "$bt/lib/d8.jar" ] || missing=1
fi
jar=""
for c in "$sdk/platforms/android-35/android.jar" \
         "$sdk/platforms/android-34/android.jar" \
         "$sdk/platforms/android-36/android.jar"; do
  if [ -f "$c" ]; then jar="$c"; break; fi
done
say "android.jar" "${jar:-MISSING}"
[ -n "$jar" ] || missing=1
say "disk.free" "$(df -h "$HOME" 2>/dev/null | awk 'NR==2 {print $4}')"
if [ "$missing" != 0 ]; then
  echo "remote check: incomplete" >&2
  exit 4
fi
echo "remote check: OK"
REMOTE
}

ensure_remote_workspace() {
  remote_connect
  ssh "${SSH_OPTS[@]}" "$REMOTE_SSH" \
    "ROOT='$REMOTE_ROOT_ABS' WORK='$REMOTE_WORK' RESULT='$REMOTE_RESULT' MARKER='$REMOTE_MARKER' bash -s" <<'REMOTE'
set -euo pipefail
if [ -e "$ROOT" ] && [ ! -d "$ROOT" ]; then
  echo "remote build root is not a directory: $ROOT" >&2
  exit 4
fi
mkdir -p "$ROOT"
if [ ! -f "$ROOT/$MARKER" ]; then
  if [ -n "$(find "$ROOT" -mindepth 1 -maxdepth 1 ! -name "$MARKER" -print -quit)" ]; then
    echo "refusing non-empty unmanaged remote root: $ROOT" >&2
    echo "choose another --remote-root or remove that directory manually" >&2
    exit 4
  fi
  printf 'managed by tool-store tools/build.sh; safe to delete when no build is running\n' \
    > "$ROOT/$MARKER"
fi
mkdir -p "$WORK/tools" "$RESULT"
REMOTE
}

cleanup_remote() {
  [ -n "$REMOTE_WORK" ] || return 0
  if [ "$KEEP" = 1 ]; then
    warn "keeping remote workspace: $REMOTE_SSH:$REMOTE_WORK"
    warn "keeping remote result:    $REMOTE_SSH:$REMOTE_RESULT"
  else
    if ssh "${SSH_OPTS[@]}" "$REMOTE_SSH" \
         "rm -rf -- '$REMOTE_WORK' '$REMOTE_RESULT'" >/dev/null 2>&1; then
      ok "remote workspace and result removed"
    else
      warn "remote cleanup failed: $REMOTE_SSH:$REMOTE_WORK"
    fi
  fi
  REMOTE_WORK=""
  REMOTE_RESULT=""
}
trap cleanup_remote EXIT

# ---------------------------------------------------------------- signing key

KEYSTORE_DIR="${TOOLSTORE_KEYSTORE_DIR:-$HOME/.config/tool-store}"
KEYSTORE="$KEYSTORE_DIR/tool-store-release.jks"
KEY_PASS_FILE="$KEYSTORE_DIR/tool-store-release.pass"
KEY_ALIAS="toolstore"

ensure_signing_key() {
  mkdir -p "$KEYSTORE_DIR"
  chmod 700 "$KEYSTORE_DIR"

  if [ -f "$KEYSTORE" ] && [ ! -f "$KEY_PASS_FILE" ]; then
    die 8 "signing password file is missing: $KEY_PASS_FILE (keystore was not changed)"
  fi
  if [ ! -f "$KEYSTORE" ] && [ -f "$KEY_PASS_FILE" ]; then
    die 8 "signing keystore is missing: $KEYSTORE (password file was not changed)"
  fi

  if [ ! -f "$KEYSTORE" ]; then
    log "creating local signing key under $KEYSTORE_DIR"
    (umask 077; python3 -c 'import secrets; print(secrets.token_urlsafe(36))' \
      > "$KEY_PASS_FILE.tmp")
    chmod 600 "$KEY_PASS_FILE.tmp"
    mv -f "$KEY_PASS_FILE.tmp" "$KEY_PASS_FILE"
    if ! keytool -genkeypair \
          -keystore "$KEYSTORE" \
          -storetype PKCS12 \
          -alias "$KEY_ALIAS" \
          -keyalg RSA -keysize 4096 -validity 10000 \
          -dname "CN=Tool Store, OU=Local, O=Tool Store, L=Local, ST=Local, C=CN" \
          -storepass:file "$KEY_PASS_FILE" \
          -keypass:file "$KEY_PASS_FILE" >/dev/null 2>&1; then
      rm -f "$KEYSTORE" "$KEY_PASS_FILE"
      die 8 "keytool failed to create the signing key"
    fi
    chmod 600 "$KEYSTORE"
    ok "local signing key created (key and password never leave this host)"
  fi

  chmod 600 "$KEYSTORE" "$KEY_PASS_FILE"
  if ! keytool -list -keystore "$KEYSTORE" -alias "$KEY_ALIAS" \
        -storepass:file "$KEY_PASS_FILE" >/dev/null 2>&1; then
    die 8 "cannot read signing key or alias $KEY_ALIAS: $KEYSTORE"
  fi
}

sign_and_verify() {
  local unsigned="$1"
  local aligned="$2"
  local signed="$3"
  local logfile="$4"

  "$ZIPALIGN" -f -p 4 "$unsigned" "$aligned" >> "$logfile" 2>&1 \
    || { error "zipalign failed"; return 1; }
  # Passwords are supplied by file so they never appear in argv or the log.
  "$APKSIGNER" sign \
    --ks "$KEYSTORE" \
    --ks-key-alias "$KEY_ALIAS" \
    --ks-pass "file:$KEY_PASS_FILE" \
    --min-sdk-version 24 \
    --out "$signed" \
    "$aligned" >> "$logfile" 2>&1 \
    || { error "apksigner failed"; return 2; }
  if ! "$ROOT/tools/verify-apk.sh" "$signed" 2>&1 | tee -a "$logfile"; then
    return 3
  fi
  return 0
}

# ---------------------------------------------------------------- preflight

log "local SDK: $SDK_ROOT"
log "build-tools: $BUILD_TOOLS"
"$ROOT/tools/build-inner.sh" --check >/dev/null \
  || die 3 "local compile toolchain check failed; run tools/build-inner.sh --check"

if [ "$MODE" = "remote" ]; then
  command -v ssh >/dev/null 2>&1 || die 3 "ssh is required for remote builds"
  command -v rsync >/dev/null 2>&1 || die 3 "rsync is required for remote builds"
  remote_connect
  if [ "$DO_CHECK" = 1 ]; then
    remote_check
    if [ -f "$KEYSTORE" ] && [ -f "$KEY_PASS_FILE" ]; then
      ok "signing key present: $(basename "$KEYSTORE")"
    else
      log "signing key absent; it will be generated on the first real build"
    fi
    log "check complete; no build was performed"
    exit 0
  fi
elif [ "$MODE" = "local" ]; then
  if [ "$DO_CHECK" = 1 ]; then
    if [ -f "$KEYSTORE" ] && [ -f "$KEY_PASS_FILE" ]; then
      ok "signing key present: $(basename "$KEYSTORE")"
    else
      log "signing key absent; it will be generated on the first real build"
    fi
    log "check complete; no build was performed"
    exit 0
  fi
else
  die 2 "unknown build mode: $MODE"
fi

VERSION="$(sed -n 's/.*android:versionName="\([^"]*\)".*/\1/p' "$ROOT/AndroidManifest.xml" | head -1)"
[ -n "$VERSION" ] || die 3 "cannot read versionName from AndroidManifest.xml"
RUN_ID="$(date +%Y%m%d_%H%M%S)-$$"
WORK="$ROOT/.build/$RUN_ID"
LOG="$ROOT/.build/build-$RUN_ID.log"
mkdir -p "$WORK" "$ROOT/.build" "$ROOT/dist"
UNSIGNED="$WORK/tool-store-unsigned.apk"
ALIGNED="$WORK/tool-store-aligned.apk"
SIGNED="$WORK/tool-store-signed.apk"
FINAL="$ROOT/dist/tool-store-$VERSION.apk"

{
  printf 'tool-store build\n'
  printf 'run: %s\n' "$RUN_ID"
  printf 'mode: %s\n' "$MODE"
  printf 'version: %s\n' "$VERSION"
  printf 'local sdk: %s\n' "$SDK_ROOT"
  printf 'build-tools: %s\n' "$BUILD_TOOLS"
  printf 'started: %s\n' "$(date -Is)"
  echo
} | tee "$LOG"

BUILD_RC=0
if [ "$MODE" = "remote" ]; then
  REMOTE_WORK="$REMOTE_ROOT_ABS/work-$RUN_ID"
  REMOTE_RESULT="$REMOTE_ROOT_ABS/result-$RUN_ID"
  ensure_remote_workspace

  log "syncing sources to $REMOTE_SSH:$REMOTE_WORK"
  rsync -a --safe-links \
    "$ROOT/AndroidManifest.xml" "$ROOT/src" "$ROOT/res" "$ROOT/assets" "$ROOT/jniLibs" \
    "$REMOTE_SSH:$REMOTE_WORK/" >> "$LOG" 2>&1 \
    || die 3 "failed to sync sources to $REMOTE_SSH"
  rsync -a "$ROOT/tools/build-inner.sh" "$REMOTE_SSH:$REMOTE_WORK/tools/" >> "$LOG" 2>&1 \
    || die 3 "failed to sync build-inner.sh to $REMOTE_SSH"

  log "compiling on $REMOTE_SSH (lock wait <= ${WAIT}s)"
  set +e
  ssh "${SSH_OPTS[@]}" "$REMOTE_SSH" \
    "WORK='$REMOTE_WORK' RESULT='$REMOTE_RESULT' ROOT='$REMOTE_ROOT_ABS' WAIT='$WAIT' bash -s" \
    <<'REMOTE' 2>&1 | tee -a "$LOG"
set -euo pipefail
cd "$WORK"
exec 9>"$ROOT/build.lock"
if ! flock -w "$WAIT" 9; then
  echo "[remote] build lock timeout after ${WAIT}s" >&2
  exit 75
fi
echo "[remote] workdir: $WORK"
echo "[remote] packaging unsigned APK (no signing material on this host)"
./tools/build-inner.sh --out "$RESULT/out"
echo "[remote] done"
REMOTE
  BUILD_RC=${PIPESTATUS[0]}
  set -e
  [ "$BUILD_RC" -eq 0 ] || {
    cleanup_remote
    if [ "$BUILD_RC" -eq 75 ]; then
      die 75 "remote build lock timed out; raise --wait or wait for the other build"
    fi
    die 6 "remote build failed (ssh exit $BUILD_RC); log: $LOG"
  }

  log "fetching unsigned APK"
  rsync -a "$REMOTE_SSH:$REMOTE_RESULT/out/unsigned.apk" "$UNSIGNED" >> "$LOG" 2>&1 \
    || { cleanup_remote; die 7 "failed to fetch unsigned APK"; }
  cleanup_remote
else
  log "compiling locally"
  set +e
  "$ROOT/tools/build-inner.sh" --out "$WORK/out" 2>&1 | tee -a "$LOG"
  BUILD_RC=${PIPESTATUS[0]}
  set -e
  [ "$BUILD_RC" -eq 0 ] || die 6 "local build failed; log: $LOG"
  cp "$WORK/out/unsigned.apk" "$UNSIGNED"
fi
[ -s "$UNSIGNED" ] || die 7 "unsigned APK is missing or empty"

ensure_signing_key
log "aligning and signing locally"
set +e
sign_and_verify "$UNSIGNED" "$ALIGNED" "$SIGNED" "$LOG"
SIGN_RC=$?
set -e
[ "$SIGN_RC" -eq 0 ] || die 8 "signing or verification failed; log: $LOG"

install -m 0644 "$SIGNED" "$FINAL.tmp"
mv -f "$FINAL.tmp" "$FINAL"
SHA="$(sha256sum "$FINAL" | awk '{print $1}')"
printf '%s  %s\n' "$SHA" "$(basename "$FINAL")" > "$FINAL.sha256"

if [ "$KEEP" = 1 ]; then
  log "kept local workspace: $WORK"
else
  rm -rf "$WORK"
fi

echo
ok "APK: $FINAL"
ok "SHA-256: $SHA"
log "log: $LOG"
log "install (LAN/USB only): adb install -r \"$FINAL\""