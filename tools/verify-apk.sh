#!/usr/bin/env bash
# Verify a signed tool-store APK against the repository and the security
# invariants of this project.
#
# Usage: tools/verify-apk.sh dist/tool-store-0.1.1.apk
#
# Checks:
#   - package/version/minSdk/targetSdk and launchable activity
#   - only INTERNET + ACCESS_NETWORK_STATE + REQUEST_INSTALL_PACKAGES
#   - debuggable=false, allowBackup=false, cleartext=false, native libs extracted
#   - exact native runtime inventory (arm64-v8a), including libadb.so
#   - runtime licence assets are present
#   - no WebView classes and no HTML/JS/CSS assets (pure native UI)
#   - ZIP alignment, APK Signature Scheme v2 signature, one signer
#   - SHA-256 of the final artifact
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

fail() {
  echo "verify-apk: [x] $*" >&2
  exit 1
}

[ $# -eq 1 ] || { echo "usage: tools/verify-apk.sh <signed.apk>" >&2; exit 2; }
APK="$1"
case "$APK" in
  /*) ;;
  *) APK="$PWD/$APK" ;;
esac
[ -f "$APK" ] || fail "APK not found: $APK"
APK="$(cd "$(dirname "$APK")" && pwd)/$(basename "$APK")"

SDK_ROOT="${TOOLSTORE_SDK_ROOT:-${ANDROID_SDK_ROOT:-${ANDROID_HOME:-$HOME/Android/Sdk}}}"
[ -d "$SDK_ROOT" ] || fail "Android SDK not found: $SDK_ROOT"
if [ -n "${TOOLSTORE_BUILD_TOOLS:-}" ]; then
  BUILD_TOOLS="$TOOLSTORE_BUILD_TOOLS"
else
  BUILD_TOOLS=""
  for candidate in "$SDK_ROOT"/build-tools/35.0.0 "$SDK_ROOT"/build-tools/34.0.0 \
                   "$SDK_ROOT"/build-tools/36.0.0; do
    if [ -x "$candidate/aapt" ]; then
      BUILD_TOOLS="$candidate"
      break
    fi
  done
  if [ -z "$BUILD_TOOLS" ]; then
    BUILD_TOOLS="$(ls -1d "$SDK_ROOT"/build-tools/*/ 2>/dev/null | sort -V | tail -1 || true)"
    BUILD_TOOLS="${BUILD_TOOLS%/}"
  fi
fi
AAPT="$BUILD_TOOLS/aapt"
APKSIGNER="$BUILD_TOOLS/apksigner"
ZIPALIGN="$BUILD_TOOLS/zipalign"
[ -x "$AAPT" ] || fail "aapt not found under $SDK_ROOT/build-tools"
[ -x "$APKSIGNER" ] || fail "apksigner not found under $BUILD_TOOLS"
[ -x "$ZIPALIGN" ] || fail "zipalign not found under $BUILD_TOOLS"
command -v unzip >/dev/null 2>&1 || fail "unzip is required"
command -v sha256sum >/dev/null 2>&1 || fail "sha256sum is required"

TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

MANIFEST="$ROOT/AndroidManifest.xml"
[ -f "$MANIFEST" ] || fail "manifest not found: $MANIFEST"
PKG="$(sed -n 's/.*package="\([^"]*\)".*/\1/p' "$MANIFEST" | head -1)"
VERSION="$(sed -n 's/.*android:versionName="\([^"]*\)".*/\1/p' "$MANIFEST" | head -1)"
VCODE="$(sed -n 's/.*android:versionCode="\([0-9]*\)".*/\1/p' "$MANIFEST" | head -1)"
MIN_SDK="$(sed -n 's/.*android:minSdkVersion="\([0-9]*\)".*/\1/p' "$MANIFEST" | head -1)"
TARGET_SDK="$(sed -n 's/.*android:targetSdkVersion="\([0-9]*\)".*/\1/p' "$MANIFEST" | head -1)"
[ -n "$PKG" ] && [ -n "$VERSION" ] && [ -n "$VCODE" ] && [ -n "$MIN_SDK" ] && [ -n "$TARGET_SDK" ] \
  || fail "cannot read package/version/sdk from AndroidManifest.xml"

ENTRIES="$(unzip -Z1 "$APK")" || fail "cannot list APK entries"
has_entry() { printf '%s\n' "$ENTRIES" | grep -qx -- "$1"; }
for entry in AndroidManifest.xml resources.arsc classes.dex; do
  has_entry "$entry" || fail "APK is missing $entry"
done

BADGING="$("$AAPT" dump badging "$APK" 2>/dev/null)" \
  || fail "aapt dump badging failed"
printf '%s\n' "$BADGING" \
  | grep -q "^package: name='$PKG' versionCode='$VCODE' versionName='$VERSION'" \
  || fail "package/version mismatch (expected $PKG $VCODE $VERSION)"
printf '%s\n' "$BADGING" | grep -q "^sdkVersion:'$MIN_SDK'$" \
  || fail "minSdk mismatch (expected $MIN_SDK)"
printf '%s\n' "$BADGING" | grep -q "^targetSdkVersion:'$TARGET_SDK'$" \
  || fail "targetSdk mismatch (expected $TARGET_SDK)"
printf '%s\n' "$BADGING" \
  | grep -q "^launchable-activity: name='$PKG.MainActivity'" \
  || fail "MainActivity is not the launcher activity"
if printf '%s\n' "$BADGING" | grep -q '^application-debuggable'; then
  fail "APK is debuggable"
fi

# Exact permission allowlist. Adding a permission must be a deliberate change
# here as well as in the manifest.
"$AAPT" dump permissions "$APK" 2>/dev/null \
  | sed -n "s/^uses-permission: name='\([^']*\)'.*/\1/p" \
  | LC_ALL=C sort > "$TMP/perms.txt"
printf '%s\n' \
  'android.permission.ACCESS_NETWORK_STATE' \
  'android.permission.INTERNET' \
  'android.permission.REQUEST_INSTALL_PACKAGES' \
  | LC_ALL=C sort > "$TMP/expected-perms.txt"
if ! diff -u "$TMP/expected-perms.txt" "$TMP/perms.txt" > "$TMP/perms.diff"; then
  cat "$TMP/perms.diff" >&2
  fail "unexpected permission set"
fi

XMLTREE="$("$AAPT" dump xmltree "$APK" AndroidManifest.xml 2>/dev/null)" \
  || fail "cannot dump binary manifest"
printf '%s\n' "$XMLTREE" | grep -F 'android:debuggable' | grep -q '0x0$' \
  || fail "manifest debuggable is not false"
printf '%s\n' "$XMLTREE" | grep -F 'android:allowBackup' | grep -q '0x0$' \
  || fail "manifest allowBackup is not false"
printf '%s\n' "$XMLTREE" | grep -F 'android:usesCleartextTraffic' | grep -q '0x0$' \
  || fail "manifest usesCleartextTraffic is not false"
printf '%s\n' "$XMLTREE" | grep -F 'android:extractNativeLibs' | grep -Eq '0x(1|ffffffff)$' \
  || fail "manifest extractNativeLibs is not true"

# Exact native runtime inventory, derived from the repository. This also
# rejects unexpected ABIs or libraries in the packaged APK.
(
  cd "$ROOT/jniLibs"
  find . -type f -name '*.so' -printf '%P\n' | sed 's|^|lib/|'
) | LC_ALL=C sort > "$TMP/expected-libs.txt"
[ -s "$TMP/expected-libs.txt" ] || fail "no native runtime libraries in jniLibs"
grep -q '^lib/arm64-v8a/libadb\.so$' "$TMP/expected-libs.txt" \
  || fail "jniLibs is missing arm64-v8a/libadb.so"
printf '%s\n' "$ENTRIES" | grep -E '^lib/.+\.so$' \
  | LC_ALL=C sort > "$TMP/apk-libs.txt" || true
if ! diff -u "$TMP/expected-libs.txt" "$TMP/apk-libs.txt" > "$TMP/libs.diff"; then
  cat "$TMP/libs.diff" >&2
  fail "packaged native libraries differ from jniLibs"
fi
STRAY_SO="$(printf '%s\n' "$ENTRIES" | grep -E '\.so$' | grep -vE '^lib/' || true)"
[ -z "$STRAY_SO" ] || fail "native library outside lib/: $STRAY_SO"
LIB_COUNT="$(wc -l < "$TMP/apk-libs.txt" | tr -d ' ')"

has_entry 'assets/NOTICE-adb-runtime.txt' || fail "missing ADB runtime notice"
has_entry 'assets/licenses/TERMUX-ADB-RUNTIME.txt' || fail "missing Termux runtime licence"
printf '%s\n' "$ENTRIES" | grep -q '^assets/licenses/runtime/' \
  || fail "missing third-party runtime licences"

# The UI must remain native: no WebView bytecode and no web asset payload.
WEB_ASSETS="$(printf '%s\n' "$ENTRIES" \
  | grep -Ei '^assets/www/|\.(html?|mjs|js|css)$' || true)"
[ -z "$WEB_ASSETS" ] || fail "web assets found in APK: $WEB_ASSETS"
for dex in $(printf '%s\n' "$ENTRIES" | grep -E '^classes([0-9]+)?\.dex$' | LC_ALL=C sort); do
  unzip -p "$APK" "$dex" > "$TMP/current.dex" || fail "cannot read $dex"
  if LC_ALL=C grep -a -q -e 'Landroid/webkit/' -e 'android.webkit.WebView' "$TMP/current.dex"; then
    fail "WebView bytecode found in $dex"
  fi
done
unzip -p "$APK" classes.dex > "$TMP/classes.dex" || fail "cannot read classes.dex"
LC_ALL=C grep -a -q 'com/toolstore/installer/MainActivity' "$TMP/classes.dex" \
  || fail "MainActivity bytecode is missing from classes.dex"

"$ZIPALIGN" -c -p 4 "$APK" >/dev/null 2>&1 \
  || fail "APK is not zipaligned"
if ! SIG="$("$APKSIGNER" verify --verbose --print-certs "$APK" 2>&1)"; then
  printf '%s\n' "$SIG" >&2
  fail "APK signature verification failed"
fi
printf '%s\n' "$SIG" | grep -qx 'Verifies' \
  || fail "apksigner did not report Verifies"
printf '%s\n' "$SIG" | grep -q 'Verified using v2 scheme (APK Signature Scheme v2): true' \
  || fail "APK has no APK Signature Scheme v2 signature"
printf '%s\n' "$SIG" | grep -qx 'Number of signers: 1' \
  || fail "expected exactly one signer"
CERT="$(printf '%s\n' "$SIG" \
  | sed -n 's/^Signer #1 certificate SHA-256 digest: //p' | head -1)"
[ -n "$CERT" ] || fail "cannot read signer certificate digest"

SHA="$(sha256sum "$APK" | awk '{print $1}')"
SIZE="$(du -h "$APK" | awk '{print $1}')"
echo "verify-apk: package       $PKG $VCODE ($VERSION), minSdk $MIN_SDK, targetSdk $TARGET_SDK"
echo "verify-apk: permissions   3 (INTERNET, ACCESS_NETWORK_STATE, REQUEST_INSTALL_PACKAGES)"
echo "verify-apk: native        $LIB_COUNT arm64-v8a libraries, libadb.so present"
echo "verify-apk: ui            native Java/XML; no WebView, HTML, JS or CSS"
echo "verify-apk: signature     v2 verified, 1 signer"
echo "verify-apk: signer cert   SHA-256 $CERT"
echo "verify-apk: size          $SIZE"
echo "verify-apk: SHA-256       $SHA"
echo "verify-apk: OK"