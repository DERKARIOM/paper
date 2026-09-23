#!/bin/bash
set -Eeuo pipefail
umask 022

# ===== User knobs =====
ABIS="${ABIS:-arm64-v8a armeabi-v7a x86 x86_64}"
VERBOSE="${VERBOSE:-0}"
export SOURCE_DATE_EPOCH="${SOURCE_DATE_EPOCH:-1700000000}"
export TZ=UTC LC_ALL=C LANG=C PYTHONHASHSEED=0

info(){ if [ "$VERBOSE" = "1" ]; then echo "$@"; else >&2 echo "$@"; fi }

# Parallel build jobs detection (portable)
detect_jobs(){
  local j
  if command -v nproc >/dev/null 2>&1; then j="$(nproc)"
  elif command -v getconf >/dev/null 2>&1; then j="$(getconf _NPROCESSORS_ONLN 2>/dev/null || echo 1)"
  elif command -v sysctl >/dev/null 2>&1; then j="$(sysctl -n hw.ncpu 2>/dev/null || echo 1)"
  else j=1; fi
  [ -z "$j" ] && j=1
  [ "$j" -lt 1 ] && j=1
  echo "$j"
}
JOBS="${JOBS:-$(detect_jobs)}"
info "Using parallel jobs: $JOBS"

# Paths
SCRIPT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
OPENCV_DIR_ORIG="$SCRIPT_DIR/external/opencv"
PINNED_JNI_DIR="${PINNED_JNI_DIR:-$SCRIPT_DIR/external/opencv_pinned_jni}"  # hier liegen die gepinnten Dateien
export PINNED_JNI_DIR
export REQUIRE_PINNED_JNI="${REQUIRE_PINNED_JNI:-1}"  # CI bricht ab, wenn gepinnte fehlen (unset/0 auf eigene Gefahr)
BUILD_DIR="/tmp/opencv-build"
OPENCV_DIR="/tmp/opencv-src"

# ===== Helpers =====
sedi(){ if sed --version >/dev/null 2>&1; then sed -i "$@"; else sed -i '' "$@"; fi }

# ===== Quick Pin-Check (ohne Build) =====
if [ "${QUICK_PATCH5_CHECK:-0}" = "1" ]; then
  info "Running QUICK_PATCH5_CHECK: validating pinned JNI files in PINNED_JNI_DIR='$PINNED_JNI_DIR'"
  COPIED=0; MISSING=()
  for f in core.inl.hpp imgcodecs.inl.hpp imgproc.inl.hpp photo.inl.hpp video.inl.hpp videoio.inl.hpp; do
    if [ -f "$PINNED_JNI_DIR/$f" ]; then
      echo "[pinned-jni] would copy $f -> gen/cpp"; COPIED=$((COPIED+1))
    else MISSING+=("$f"); fi
  done
  if [ -f "$PINNED_JNI_DIR/opencv_jni.hpp" ]; then
    echo "[pinned-jni] would copy opencv_jni.hpp -> gen/cpp"; COPIED=$((COPIED+1))
  else MISSING+=("opencv_jni.hpp"); fi
  if [ ${#MISSING[@]} -gt 0 ]; then IFS=","; echo "[pinned-jni] missing (not fatal): ${MISSING[*]}"; IFS=$' \t\n'; fi
  echo "Pin quick summary: REQUIRE_PINNED_JNI=${REQUIRE_PINNED_JNI:-unset}, PINNED_JNI_DIR=${PINNED_JNI_DIR:-unset}, present=$COPIED"
  if [ "${REQUIRE_PINNED_JNI:-0}" = "1" ] && [ "$COPIED" -eq 0 ]; then
    echo "[pinned-jni] No pinned files present in '$PINNED_JNI_DIR'." >&2; exit 1
  fi
  exit 0
fi

# ===== Clean + Copy OpenCV =====
cd "$OPENCV_DIR_ORIG"
git clean -xfd
git checkout .
info "Copying OpenCV sources to $OPENCV_DIR..."
rm -rf "$OPENCV_DIR"; mkdir -p "$OPENCV_DIR"
cp -a "$OPENCV_DIR_ORIG/." "$OPENCV_DIR"

# ===== Patch 1: deterministic ocv_output_status() =====
OPENCV_UTILS="$OPENCV_DIR/cmake/OpenCVUtils.cmake"
info "Patch ocv_output_status in $OPENCV_UTILS"
cp "$OPENCV_UTILS" "$OPENCV_UTILS.bak" || true
sedi '/^[[:space:]]*function(ocv_output_status/,/^[[:space:]]*endfunction/ d' "$OPENCV_UTILS"
cat <<'EOF' >> "$OPENCV_UTILS"

# Patched: deterministic ocv_output_status()
function(ocv_output_status msg)
  set(OPENCV_BUILD_INFO_STR "\"OpenCV 4.13.0 (reproducible build)\\n\"" CACHE INTERNAL "")
endfunction()
EOF

# ===== Patch 2: disable internal POST_BUILD strip (idempotent) =====
JNI_CMAKELISTS="$OPENCV_DIR/modules/java/jni/CMakeLists.txt"
info "Neutralize internal strip in $JNI_CMAKELISTS..."
cp "$JNI_CMAKELISTS" "$JNI_CMAKELISTS.bak" || true
if command -v perl >/dev/null 2>&1; then
  perl -0777 -pe 's/^[ \t]*add_custom_command\(TARGET[^\n]*POST_BUILD[^\n]*\n//m' -i "$JNI_CMAKELISTS"
else
  sedi '/^[[:space:]]*add_custom_command(TARGET[[:space:]]\+\${the_module}[[:space:]]\+POST_BUILD/d' "$JNI_CMAKELISTS"
fi

# ===== Patch 3: don’t auto-build Android AAR via Gradle =====
ANDROID_SDK_CMAKE="$OPENCV_DIR/modules/java/android_sdk/CMakeLists.txt"
info "Remove ALL from android_sdk target in $ANDROID_SDK_CMAKE"
cp "$ANDROID_SDK_CMAKE" "$ANDROID_SDK_CMAKE.bak" || true
perl -0777 -pe 's/add_custom_target\(([^)]*_android)\s+ALL/add_custom_target($1/g' -i "$ANDROID_SDK_CMAKE"

# ===== Patch 4: deterministic source ordering (harmlos) =====
JAVA_TOP="$OPENCV_DIR/modules/java/CMakeLists.txt"
JNI_TOP="$OPENCV_DIR/modules/java/jni/CMakeLists.txt"
info "Sort glob results & source lists"
cp -f "$JAVA_TOP" "$JAVA_TOP.bak" || true
cp -f "$JNI_TOP" "$JNI_TOP.bak" || true
perl -0777 -pe 's/(file\(GLOB _result[^\n]*\n)/$1  list(SORT _result)\n/s' -i "$JAVA_TOP"
awk '
  $0 ~ /^foreach\(m \${OPENCV_MODULES_BUILD}\)/ && !done {
    print "set(__mods ${OPENCV_MODULES_BUILD})";
    print "list(SORT __mods)";
    print "foreach(m ${__mods})";
    done=1; next
  }
  { print }
' "$JNI_TOP" > "${JNI_TOP}.tmp" && mv "${JNI_TOP}.tmp" "$JNI_TOP"
perl -0777 -pe 's/(file\(GLOB _result[^\n]*\n)/$1    list(SORT _result)\n/s' -i "$JNI_TOP"
perl -0777 -pe 's~(\n\s*ocv_add_library\(\$\{the_module\}.*\n)~\n# Repro: stable order of all source lists\nforeach(v handwritten_h_sources handwritten_cpp_sources generated_cpp_sources jni_sources java_sources srcs sources __srcs)\n  if(DEFINED \${v})\n    list(SORT \${v})\n  endif()\nendforeach()\n\1~s' -i "$JNI_TOP"

# ===== locate NDK (optional heuristics) =====
if [ -z "${ANDROID_NDK_HOME:-}" ]; then
  if [ -n "${ANDROID_SDK_ROOT:-}" ]; then
    if [ -d "$ANDROID_SDK_ROOT/ndk" ]; then
      LATEST_NDK="$(find "$ANDROID_SDK_ROOT/ndk" -maxdepth 1 -type d -name "[0-9]*" | sort -Vr | head -n1 || true)"
      [ -n "$LATEST_NDK" ] && export ANDROID_NDK_HOME="$LATEST_NDK" && info "Found NDK at $ANDROID_NDK_HOME"
    fi
    if [ -z "${ANDROID_NDK_HOME:-}" ] && [ -d "$ANDROID_SDK_ROOT/ndk-bundle" ]; then
      export ANDROID_NDK_HOME="$ANDROID_SDK_ROOT/ndk-bundle"; info "Found legacy NDK at $ANDROID_NDK_HOME"
    fi
  fi
  if [ -z "${ANDROID_NDK_HOME:-}" ]; then
    if [ -d "$HOME/Library/Android/sdk/ndk" ]; then
      LATEST_NDK="$(find "$HOME/Library/Android/sdk/ndk" -maxdepth 1 -type d -name "[0-9]*" | sort -Vr | head -n1 || true)"
      [ -n "$LATEST_NDK" ] && export ANDROID_NDK_HOME="$LATEST_NDK" && info "Found NDK at $ANDROID_NDK_HOME"
    elif [ -d "$HOME/Library/Android/sdk/ndk-bundle" ]; then
      export ANDROID_NDK_HOME="$HOME/Library/Android/sdk/ndk-bundle"; info "Found legacy NDK at $ANDROID_NDK_HOME"
    else
      echo "Error: ANDROID_NDK_HOME not set and NDK not found." >&2; exit 1
    fi
  fi
fi

# ===== CMake pick =====
if [ -z "${OPENCV_CMAKE:-}" ]; then
  if [ -n "${ANDROID_SDK_ROOT:-}" ] && [ -d "$ANDROID_SDK_ROOT/cmake" ]; then
    LATEST_CMAKE_DIR="$(find "$ANDROID_SDK_ROOT/cmake" -maxdepth 1 -type d -name "[0-9]*" | sort -Vr | head -n1 || true)"
    if [ -n "$LATEST_CMAKE_DIR" ] && [ -x "$LATEST_CMAKE_DIR/bin/cmake" ]; then
      OPENCV_CMAKE="$LATEST_CMAKE_DIR/bin/cmake"
    fi
  fi
fi
[ -z "${OPENCV_CMAKE:-}" ] && OPENCV_CMAKE="$(command -v cmake || true)"
[ -n "${OPENCV_CMAKE:-}" ] || { echo "ERROR: CMake not found (set OPENCV_CMAKE)"; exit 1; }
OPENCV_CMAKE_VER="$("$OPENCV_CMAKE" --version | awk '/version/{print $3; exit}')"
info "OpenCV CMake: $OPENCV_CMAKE (v $OPENCV_CMAKE_VER)"

# ===== toolchain dir detect =====
PREBUILT_BASE="$ANDROID_NDK_HOME/toolchains/llvm/prebuilt"
detect_toolchain_dir(){
  local host_os host_arch
  case "$(uname -s)" in Darwin) host_os=darwin;; Linux) host_os=linux;; *) host_os=linux;; esac
  case "$(uname -m)" in arm64|aarch64) host_arch=aarch64;; x86_64|amd64) host_arch=x86_64;; *) host_arch=x86_64;; esac
  for cand in "$PREBUILT_BASE/${host_os}-${host_arch}" "$PREBUILT_BASE/${host_os}-aarch64" "$PREBUILT_BASE/${host_os}-arm64" "$PREBUILT_BASE/${host_os}-x86_64"; do
    [ -x "$cand/bin/llvm-ar" ] && { echo "$cand"; return 0; }
  done
  local any; any="$(find "$PREBUILT_BASE" -maxdepth 1 -type d -name "${host_os}-*" 2>/dev/null | while read -r d; do [ -x "$d/bin/llvm-ar" ] && { echo "$d"; break; } done)"
  [ -n "$any" ] && { echo "$any"; return 0; } || return 1
}
TOOLCHAIN_DIR="$(detect_toolchain_dir || true)"
[ -n "$TOOLCHAIN_DIR" ] || { echo "ERROR: llvm toolchain dir not found under $PREBUILT_BASE"; exit 1; }
info "Using NDK toolchain: $TOOLCHAIN_DIR"

# ===== prep build root =====
rm -rf "$BUILD_DIR"; mkdir -p "$BUILD_DIR/lib"
BUILD_LOG="$BUILD_DIR/opencv_build.log"
info "Build log: $BUILD_LOG"
echo "$(date): Starting OpenCV build" > "$BUILD_LOG"

# ===== build per ABI =====
build_for_arch(){
  local arch="$1"
  local arch_build_dir="${BUILD_DIR}_${arch}"
  rm -rf "$arch_build_dir"; mkdir -p "$arch_build_dir"; cd "$arch_build_dir"

  local arch_log="$arch_build_dir/opencv_build_$arch.log"
  echo "$(date): Start OpenCV build for $arch" > "$arch_log"

  export ZERO_AR_DATE=1
  local PY3_BIN="${PY3_BIN:-$(command -v python3 || true)}"
  info "Python: $($PY3_BIN --version 2>&1 || echo unknown)"

  mkdir -p "$arch_build_dir/3rdparty/lib/$arch" "$arch_build_dir/lib/$arch"

  local AR_BIN="$TOOLCHAIN_DIR/bin/llvm-ar"
  local RANLIB_BIN="$TOOLCHAIN_DIR/bin/llvm-ranlib"

  local BUILD_GENERATOR="${BUILD_GENERATOR:-Unix Makefiles}"
  info "Configure CMake ($BUILD_GENERATOR) for $arch"
  "$OPENCV_CMAKE" -G "$BUILD_GENERATOR" \
    -DCMAKE_TOOLCHAIN_FILE="$ANDROID_NDK_HOME/build/cmake/android.toolchain.cmake" \
    -DANDROID_ABI="$arch" \
    -DANDROID_NATIVE_API_LEVEL=21 \
    -DCMAKE_AR="$AR_BIN" -DCMAKE_RANLIB="$RANLIB_BIN" \
    -DPython3_EXECUTABLE="$PY3_BIN" \
    -DBUILD_opencv_python3=OFF -DBUILD_opencv_python_bindings_generator=OFF \
    -DCMAKE_C_FLAGS="-g0 -fdebug-prefix-map=$OPENCV_DIR=. -ffile-prefix-map=$OPENCV_DIR=. " \
    -DCMAKE_CXX_FLAGS="-g0 -fdebug-prefix-map=$OPENCV_DIR=. -ffile-prefix-map=$OPENCV_DIR=. " \
    -DCMAKE_BUILD_TYPE=Release \
    -DCMAKE_C_FLAGS_RELEASE="-g0 -fdebug-prefix-map=$OPENCV_DIR=. -ffile-prefix-map=$OPENCV_DIR=." \
    -DCMAKE_CXX_FLAGS_RELEASE="-g0 -fdebug-prefix-map=$OPENCV_DIR=. -ffile-prefix-map=$OPENCV_DIR=." \
    -DCMAKE_EXE_LINKER_FLAGS="-Wl,--build-id=none -Wl,-z,max-page-size=16384" \
    -DCMAKE_SHARED_LINKER_FLAGS="-Wl,--build-id=none -Wl,-z,max-page-size=16384" \
    -DCMAKE_INSTALL_PREFIX="/__repro" \
    -DBUILD_ANDROID_PROJECTS=ON \
    -DBUILD_SHARED_LIBS=ON -DBUILD_STATIC_LIBS=OFF \
    -DBUILD_TESTS=OFF -DBUILD_PERF_TESTS=OFF -DBUILD_EXAMPLES=OFF -DBUILD_DOCS=OFF -DBUILD_ANDROID_EXAMPLES=OFF \
    -DBUILD_JAVA=ON -DBUILD_opencv_java=ON \
    -DBUILD_opencv_imgproc=ON -DBUILD_opencv_imgcodecs=ON -DBUILD_opencv_video=ON -DBUILD_opencv_videoio=ON \
    -DBUILD_opencv_flann=OFF -DBUILD_opencv_calib3d=OFF -DBUILD_opencv_features2d=OFF -DBUILD_opencv_objdetect=OFF \
    -DBUILD_opencv_dnn=OFF -DBUILD_opencv_gapi=OFF -DBUILD_opencv_ml=OFF -DBUILD_opencv_highgui=OFF \
    -DBUILD_opencv_photo=ON -DBUILD_opencv_stitching=OFF \
    -DWITH_OPENCL=OFF -DWITH_IPP=OFF \
    -DCMAKE_CXX_STANDARD=11 -DCMAKE_CXX_STANDARD_REQUIRED=ON \
    "$OPENCV_DIR" >> "$arch_log" 2>&1 || { echo "ERROR: CMake config for $arch failed ($arch_log)"; return 1; }

  # ---- 1) Zuerst nur den Java-Generator bauen
  info "Build gen_opencv_java_source for $arch (-j1)"
  if [ "$BUILD_GENERATOR" = "Ninja" ]; then
    ninja -j1 gen_opencv_java_source >> "$arch_log" 2>&1 || { echo "Build (generator) failed ($arch)"; tail -n 120 "$arch_log" >&2; cd "$SCRIPT_DIR"; return 1; }
  else
    make -j1 gen_opencv_java_source >> "$arch_log" 2>&1 || { echo "Build (generator) failed ($arch)"; tail -n 120 "$arch_log" >&2; cd "$SCRIPT_DIR"; return 1; }
  fi

  # ---- 2) Gepinnte Header drüberkopieren (direkt in den Generator-Output)
  local GEN_CPP_DIR="$arch_build_dir/modules/java_bindings_generator/gen/cpp"
  mkdir -p "$GEN_CPP_DIR"
  local COPIED=0
  if [ -d "$PINNED_JNI_DIR" ]; then
    for f in core.inl.hpp imgcodecs.inl.hpp imgproc.inl.hpp photo.inl.hpp video.inl.hpp videoio.inl.hpp opencv_jni.hpp; do
      if [ -f "$PINNED_JNI_DIR/$f" ]; then
        cp -f "$PINNED_JNI_DIR/$f" "$GEN_CPP_DIR/" && info "[pinned-jni] copied $f"
        COPIED=$((COPIED+1))
      else
        info "[pinned-jni] missing (not fatal): $f"
      fi
    done
  else
    info "[pinned-jni] PINNED_JNI_DIR does not exist: $PINNED_JNI_DIR"
  fi

  if [ "${REQUIRE_PINNED_JNI:-0}" = "1" ] && [ "$COPIED" -eq 0 ]; then
    echo "[pinned-jni] No pinned files copied from '$PINNED_JNI_DIR' -> abort." >&2
    exit 1
  fi

  # ---- 3) Jetzt den Rest bauen (nutzt die überschriebenen Dateien)
  info "Build OpenCV for $arch (-j$JOBS)"
  if [ "$BUILD_GENERATOR" = "Ninja" ]; then
    ninja -j"$JOBS" >> "$arch_log" 2>&1 || { echo "Build failed ($arch)"; tail -n 120 "$arch_log" >&2; cd "$SCRIPT_DIR"; return 1; }
  else
    make -j"$JOBS" >> "$arch_log" 2>&1 || { echo "Build failed ($arch)"; tail -n 120 "$arch_log" >&2; cd "$SCRIPT_DIR"; return 1; }
  fi

  # Stage libopencv_java4.so
  local SRC_LIB_DIR="$arch_build_dir/lib/$arch"; mkdir -p "$SRC_LIB_DIR"
  local JNI_SO; JNI_SO="$(find "$arch_build_dir" -path "*/jni/$arch/libopencv_java4.so" -print -quit 2>/dev/null || true)"
  [ -n "$JNI_SO" ] && cp -f "$JNI_SO" "$SRC_LIB_DIR/" && info "Staged libopencv_java4.so ($arch)" || info "WARN: no libopencv_java4.so for $arch"

  local OUT_DIR="$BUILD_DIR/lib/$arch"
  rm -rf "$OUT_DIR"; mkdir -p "$OUT_DIR"
  shopt -s nullglob; cp -f "$SRC_LIB_DIR"/*.so "$OUT_DIR/" 2>/dev/null || true; shopt -u nullglob
  ls -1 "$OUT_DIR"/*.so >/dev/null 2>&1 || { echo "ERROR: no .so staged ($arch)"; tail -n 50 "$arch_log" >&2; cd "$SCRIPT_DIR"; return 1; }

  # Normalize timestamps + hashes + strip unneeded
  if touch -d "@$SOURCE_DATE_EPOCH" / >/dev/null 2>&1; then
    find "$OUT_DIR" -type f -name "*.so" -exec touch -d "@$SOURCE_DATE_EPOCH" {} +
  fi
  if command -v shasum >/dev/null 2>&1; then (cd "$OUT_DIR" && shasum -a 256 *.so) >> "$arch_log" 2>&1 || true
  elif command -v sha256sum >/dev/null 2>&1; then (cd "$OUT_DIR" && sha256sum *.so) >> "$arch_log" 2>&1 || true; fi

  local STRIP="$TOOLCHAIN_DIR/bin/llvm-strip"
  if [ -x "$STRIP" ]; then
    info "Strip $arch libs"
    find "$OUT_DIR" -name "*.so" -exec "$STRIP" --strip-unneeded \
      --remove-section=.comment \
      --remove-section=.note \
      --remove-section=.note.gnu.build-id \
      --remove-section=.note.ABI-tag {} \;
  fi

  # Summary (per-arch)
  info "Pin summary ($arch): REQUIRE_PINNED_JNI=${REQUIRE_PINNED_JNI:-unset}, PINNED_JNI_DIR=${PINNED_JNI_DIR:-unset}, copied=${COPIED:-0}"

  cd "$SCRIPT_DIR"
  info "Done $arch"
  return 0
}

# ===== Build loop =====
info "Building OpenCV for ABIs: [$ABIS]"
BUILD_FAILED=0
for ARCH in $ABIS; do build_for_arch "$ARCH" || BUILD_FAILED=1; done
[ $BUILD_FAILED -ne 0 ] && { echo "❌ Error: some builds failed"; exit 1; }
info "✅ OpenCV for Android built successfully."

# ===== Summary =====
if command -v sha256sum >/dev/null 2>&1; then H="sha256sum"; else H="shasum -a 256"; fi
echo "===== SHA256 summary (libopencv_java4.so) ====="
for ARCH in $ABIS; do
  OUT_DIR="$BUILD_DIR/lib/$ARCH"
  if [ -f "$OUT_DIR/libopencv_java4.so" ]; then $H "$OUT_DIR/libopencv_java4.so" || true
  else echo "$ARCH: libopencv_java4.so not found"; fi
done
echo "===== END SHA256 summary ====="
