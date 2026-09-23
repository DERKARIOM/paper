#!/usr/bin/env bash
set -euo pipefail
umask 022

# Collect reproducibility evidence for native builds
# - Per ABI: copies libopencv_java4.so, computes sha256, grabs jni_state_*.txt and map files
# - Records environment (NDK/CMake/Python versions, SOURCE_DATE_EPOCH etc.)
# - Outputs everything under scripts/repro_out/

OUT_DIR="$(cd "$(dirname "$0")" && pwd)/repro_out"
rm -rf "$OUT_DIR"
mkdir -p "$OUT_DIR"

ABIS=${ABIS:-"arm64-v8a armeabi-v7a x86_64 x86"}

hash_cmd=""
if command -v sha256sum >/dev/null 2>&1; then hash_cmd=sha256sum; elif command -v shasum >/dev/null 2>&1; then hash_cmd="shasum -a 256"; fi

# Environment manifest
{
  echo "date: $(date -u +%Y-%m-%dT%H:%M:%SZ)"
  echo "uname: $(uname -a)"
  echo "SOURCE_DATE_EPOCH=${SOURCE_DATE_EPOCH:-}"
  echo "TZ=${TZ:-}"
  echo "LC_ALL=${LC_ALL:-}"
  echo "LANG=${LANG:-}"
  echo "PYTHONHASHSEED=${PYTHONHASHSEED:-}"
  echo "ANDROID_SDK_ROOT=${ANDROID_SDK_ROOT:-}"
  echo "ANDROID_NDK_HOME=${ANDROID_NDK_HOME:-}"
  echo -n "cmake_version: "; command -v cmake >/dev/null 2>&1 && cmake --version | head -n1 || echo "(not found)"
  echo -n "python_version: "; command -v python3 >/dev/null 2>&1 && python3 --version || echo "(not found)"
} > "$OUT_DIR/env.txt"

# Locate build roots that our scripts use
# The OpenCV build script places staged .so into /tmp/opencv-build/lib/<ABI>
for ABI in $ABIS; do
  abi_out="$OUT_DIR/$ABI"
  mkdir -p "$abi_out"
  src_dir="/tmp/opencv-build/lib/$ABI"
  alt_dir="/tmp/opencv-build_$ABI/lib/$ABI"
  jni_dir_main="/tmp/opencv-build_${ABI}"
  jni_dir_alt="/tmp/opencv-build_${ABI}/modules/java/jni"

  found=0
  for d in "$src_dir" "$alt_dir"; do
    if [ -d "$d" ]; then
      if ls "$d"/libopencv_java4.so >/dev/null 2>&1; then
        cp -f "$d"/libopencv_java4.so "$abi_out/" && found=1
        break
      fi
    fi
  done
  # If not found in staged lib path, try searching fallback
  if [ $found -eq 0 ]; then
    candidate=$(find "/tmp" -type f -path "*/lib/$ABI/libopencv_java4.so" 2>/dev/null | head -n1 || true)
    if [ -n "${candidate:-}" ] && [ -f "$candidate" ]; then
      cp -f "$candidate" "$abi_out/"
      found=1
    fi
  fi

  # Optional debug dumps (won't fail if missing)
  for p in \
    "$jni_dir_main/jni_state_${ABI}.txt" \
    "$jni_dir_alt/jni_state_${ABI}.txt" \
    "$jni_dir_main/libopencv_java4_${ABI}.map" \
    "$jni_dir_alt/libopencv_java4_${ABI}.map"; do
    [ -f "$p" ] && cp -f "$p" "$abi_out/" || true
  done

  # Hashes
  if [ -f "$abi_out/libopencv_java4.so" ] && [ -n "$hash_cmd" ]; then
    ( cd "$abi_out" && $hash_cmd libopencv_java4.so > libopencv_java4.so.sha256 ) || true
  fi

done

# Summary manifest across ABIs
{
  echo "ABIS: $ABIS"
  for ABI in $ABIS; do
    f="$OUT_DIR/$ABI/libopencv_java4.so"
    if [ -f "$f" ]; then
      size=$(stat -c %s "$f" 2>/dev/null || stat -f %z "$f" 2>/dev/null || echo "?")
      echo "- $ABI: size=${size} bytes"
      if [ -f "$OUT_DIR/$ABI/libopencv_java4.so.sha256" ]; then
        echo -n "  sha256: "; cat "$OUT_DIR/$ABI/libopencv_java4.so.sha256" | awk '{print $1}'
      fi
    else
      echo "- $ABI: lib not found"
    fi
  done
} > "$OUT_DIR/manifest.txt"

# Optional: produce a tarball to upload as single artifact
( cd "$(dirname "$OUT_DIR")" && tar czf repro_out.tgz "$(basename "$OUT_DIR")" ) >/dev/null 2>&1 || true

echo "Repro evidence collected under: $OUT_DIR"
exit 0
