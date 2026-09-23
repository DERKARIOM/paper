#!/usr/bin/env bash
set -Eeuo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
REPO_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
BUILD_SCRIPT="$REPO_DIR/scripts/build_opencv_android.sh"
DEFAULT_PIN_DIR="$REPO_DIR/external/opencv_pinned_jni"

ABIS="${ABIS:-arm64-v8a}"
PRIMARY_ARCH="${ABIS%% *}"

say(){ echo "[smoke] $*"; }

# Temp copy
TMP_PIN_DIR="$(mktemp -d "/tmp/opencv_pinned_jni_smoke_XXXXXX")"
say "Temp PINNED_JNI_DIR: $TMP_PIN_DIR"
cp -a "$DEFAULT_PIN_DIR/." "$TMP_PIN_DIR/"

# Marker
MARKER="PINNED_SMOKE_MARKER_$$"
say "Inject marker: $MARKER"

# Inject into all pinned files that exist
for f in core.inl.hpp imgcodecs.inl.hpp imgproc.inl.hpp video.inl.hpp videoio.inl.hpp opencv_jni.hpp; do
  if [ -f "$TMP_PIN_DIR/$f" ]; then
    echo "#pragma message(\"$MARKER in $f\")" >> "$TMP_PIN_DIR/$f"
    say "Instrumented: $f"
  fi
done

# Run build
say "Start build with REQUIRE_PINNED_JNI=1 ABIS=$ABIS (expect to see marker)..."
set +e
REQUIRE_PINNED_JNI=1 PINNED_JNI_DIR="$TMP_PIN_DIR" ABIS="$ABIS" "$BUILD_SCRIPT"
RC=$?
set -e

# Logs
ARCH_LOG="/tmp/opencv-build_${PRIMARY_ARCH}/opencv_build_${PRIMARY_ARCH}.log"
if [ -f "$ARCH_LOG" ]; then
  say "Check build log: $ARCH_LOG"
  if grep -q "$MARKER" "$ARCH_LOG"; then
    say "Marker found in build log ✅"
    LOG_HIT=1
  else
    say "Marker NOT found in build log ❌"
    LOG_HIT=0
  fi
else
  say "No build log found."
  LOG_HIT=0
fi

# Artefact check
OUT_SO="/tmp/opencv-build/lib/${PRIMARY_ARCH}/libopencv_java4.so"
if [ -f "$OUT_SO" ]; then
  if strings "$OUT_SO" | grep -q "$MARKER"; then
    say "Marker present in .so ✅"
    SO_HIT=1
  else
    say "Marker NOT in .so ❌"
    SO_HIT=0
  fi
else
  say "No .so artifact found."
  SO_HIT=0
fi

# Result
if [ "$RC" -eq 0 ] && [ "$LOG_HIT" -eq 1 -o "$SO_HIT" -eq 1 ]; then
  echo "RESULT: OK — Pinning greift, Marker gefunden."
  exit 0
else
  echo "RESULT: FAIL — Marker nicht nachweisbar. Prüfe Log/Artefakt."
  exit 1
fi
