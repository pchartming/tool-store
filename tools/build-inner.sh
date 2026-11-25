#!/usr/bin/env bash
# Compile resources + Java and package an UNSIGNED APK.
#
# Deliberately signing-free: this script is also executed inside the isolated
# remote build workspace. Signing keys and passwords never leave this machine.
#
# Usage: tools/build-inner.sh --check
#        tools/build-inner.sh --out <empty-output-dir>
#
# Toolchain overrides (optional):
#   TOOLSTORE_SDK_ROOT, TOOLSTORE_BUILD_TOOLS, TOOLSTORE_ANDROID_JAR
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
CHECK=0
OUT=""

while [ $# -gt 0 ]; do
  case "$1" in
    --check) CHECK=1; shift ;;
    --out)
      [ $# -ge 2 ] || { echo "build-inner: --out needs a directory" >&2; exit 2; }
      OUT="$2"; shift 2 ;;
    -h|--help)
      sed -n '2,14p' "$0" | sed 's/^# \{0,1\}//'
      exit 0 ;;
    *)
      echo "build-inner: unknown argument: $1" >&2
      exit 2 ;;
  esac
done

fail() { echo "build-inner: $*" >&2; exit 3; }

SDK_ROOT="${TOOLSTORE_SDK_ROOT:-${ANDROID_SDK_ROOT:-${ANDROID_HOME:-$HOME/Android/Sdk}}}"
[ -d "$SDK_ROOT" ] || fail "Android SDK not found: $SDK_ROOT"

if [ -n "${TOOLSTORE_BUILD_TOOLS:-}" ]; then
  BUILD_TOOLS="$TOOLSTORE_BUILD_TOOLS"
else
  BUILD_TOOLS=""
  for candidate in "$SDK_ROOT"/build-tools/35.0.0 "$SDK_ROOT"/build-tools/34.0.0 \
                   "$SDK_ROOT"/build-tools/36.0.0; do
    if [ -x "$candidate/aapt2" ]; then
      BUILD_TOOLS="$candidate"
      break
    fi
  done
  if [ -z "$BUILD_TOOLS" ]; then
    BUILD_TOOLS="$(ls -1d "$SDK_ROOT"/build-tools/*/ 2>/dev/null | sort -V | tail -1 || true)"
    BUILD_TOOLS="${BUILD_TOOLS%/}"
  fi
fi
[ -n "$BUILD_TOOLS" ] && [ -x "$BUILD_TOOLS/aapt2" ] \
  || fail "aapt2 not found under $SDK_ROOT/build-tools"

AAPT2="$BUILD_TOOLS/aapt2"
D8="$BUILD_TOOLS/d8"
D8_JAR="$BUILD_TOOLS/lib/d8.jar"

if [ -n "${TOOLSTORE_ANDROID_JAR:-}" ]; then
  ANDROID_JAR="$TOOLSTORE_ANDROID_JAR"
else
  ANDROID_JAR=""
  for candidate in "$SDK_ROOT"/platforms/android-35/android.jar \
                   "$SDK_ROOT"/platforms/android-34/android.jar \
                   "$SDK_ROOT"/platforms/android-36/android.jar; do
    if [ -f "$candidate" ]; then
      ANDROID_JAR="$candidate"
      break
    fi
  done
fi
[ -n "$ANDROID_JAR" ] && [ -f "$ANDROID_JAR" ] \
  || fail "android.jar not found under $SDK_ROOT/platforms"

command -v javac >/dev/null 2>&1 || fail "javac is required"
command -v jar >/dev/null 2>&1 || fail "jar is required"
command -v python3 >/dev/null 2>&1 || fail "python3 is required for APK packaging"
if [ ! -x "$D8" ] && [ ! -f "$D8_JAR" ]; then
  fail "d8 or d8.jar not found under $BUILD_TOOLS"
fi

JAVAC_VERSION="$(javac -version 2>&1)"
if [ "$CHECK" = 1 ]; then
  echo "android.sdk   = $SDK_ROOT"
  echo "build.tools   = $BUILD_TOOLS"
  echo "android.jar   = $ANDROID_JAR"
  echo "javac         = $JAVAC_VERSION"
  python3 --version
  echo "build-inner: toolchain OK"
  exit 0
fi

[ -n "$OUT" ] || { echo "build-inner: --out is required" >&2; exit 2; }
mkdir -p "$OUT"
OUT="$(cd "$OUT" && pwd)"

# Only remove paths this script owns inside the caller-provided output dir.
rm -rf "$OUT/gen" "$OUT/classes" "$OUT/dex" "$OUT/res.zip" "$OUT/sources.txt" \
       "$OUT/base.apk" "$OUT/classes.jar" "$OUT/unsigned.apk"
mkdir -p "$OUT/gen" "$OUT/classes" "$OUT/dex"

echo "[1/5] compiling resources"
"$AAPT2" compile --dir "$ROOT/res" -o "$OUT/res.zip"

echo "[2/5] linking manifest, resources and assets"
"$AAPT2" link \
  -o "$OUT/base.apk" \
  -I "$ANDROID_JAR" \
  --manifest "$ROOT/AndroidManifest.xml" \
  --java "$OUT/gen" \
  --min-sdk-version 24 \
  --target-sdk-version 34 \
  -A "$ROOT/assets" \
  --auto-add-overlay \
  "$OUT/res.zip"

echo "[3/5] compiling Java"
find "$ROOT/src" "$OUT/gen" -name '*.java' -print | LC_ALL=C sort > "$OUT/sources.txt"
[ -s "$OUT/sources.txt" ] || fail "no Java sources found"
javac -encoding UTF-8 -source 8 -target 8 -Xlint:-options \
  -classpath "$ANDROID_JAR" -d "$OUT/classes" @"$OUT/sources.txt"

echo "[4/5] dexing"
(cd "$OUT/classes" && jar cf "$OUT/classes.jar" .)
if [ -x "$D8" ]; then
  "$D8" --lib "$ANDROID_JAR" --min-api 24 --output "$OUT/dex" "$OUT/classes.jar"
else
  java -cp "$D8_JAR" com.android.tools.r8.D8 \
    --lib "$ANDROID_JAR" --min-api 24 --output "$OUT/dex" "$OUT/classes.jar"
fi
[ -f "$OUT/dex/classes.dex" ] || fail "d8 did not produce classes.dex"

echo "[5/5] packaging unsigned APK"
python3 - "$OUT/base.apk" "$OUT/dex" "$ROOT/jniLibs" "$OUT/unsigned.apk" <<'PY'
import pathlib
import re
import shutil
import sys
import zipfile

base, dex_dir, jni_root, out = sys.argv[1:5]
dex_dir = pathlib.Path(dex_dir)
jni_root = pathlib.Path(jni_root)
libs = sorted(jni_root.rglob("*.so"))
if not any(path.name == "libadb.so" for path in libs):
    raise SystemExit("missing jniLibs/*/libadb.so")
# Refuse to accidentally package non-library files with the runtime.
for path in jni_root.rglob("*"):
    if path.is_file() and path.suffix != ".so":
        raise SystemExit(f"unexpected file in jniLibs: {path}")

dex_files = sorted(dex_dir.glob("classes*.dex"))
dex_names = [path.name for path in dex_files]
if "classes.dex" not in dex_names:
    raise SystemExit("d8 output has no classes.dex")
for name in dex_names:
    if not re.fullmatch(r"classes(?:[2-9][0-9]*)?\.dex", name):
        raise SystemExit(f"unexpected dex output: {name}")

shutil.copyfile(base, out)
with zipfile.ZipFile(out, "a", compression=zipfile.ZIP_DEFLATED,
                     allowZip64=True) as apk:
    existing = set(apk.namelist())
    if any(name.endswith(".dex") for name in existing):
        raise SystemExit("base APK already contains dex files")
    for path in dex_files:
        apk.write(path, path.name, compress_type=zipfile.ZIP_DEFLATED)
    for path in libs:
        arcname = "lib/" + path.relative_to(jni_root).as_posix()
        if arcname in existing:
            raise SystemExit(f"duplicate entry: {arcname}")
        apk.write(path, arcname, compress_type=zipfile.ZIP_DEFLATED)

with zipfile.ZipFile(out) as apk:
    names = set(apk.namelist())
    required = {
        "AndroidManifest.xml",
        "classes.dex",
    }
    missing = required - names
    if missing:
        raise SystemExit("packaged APK missing: " + ", ".join(sorted(missing)))
    if not any(name.endswith("/libadb.so") for name in names):
        raise SystemExit("packaged APK missing libadb.so")
print(f"packaged: {out} ({len(dex_files)} dex, {len(libs)} native libraries)")
PY

echo "build-inner: unsigned APK ready at $OUT/unsigned.apk"