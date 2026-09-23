#!/usr/bin/env bash
# =============================================================================
# QwiPaper – build the native libraries (OpenCV + ONNX Runtime) inside WSL.
#
# The Android app needs prebuilt native artifacts that are NOT stored in git:
#   app/src/main/jniLibs/<abi>/libopencv_java4.so
#   app/src/main/jniLibs/<abi>/libonnxruntime.so
#   app/src/main/jniLibs/<abi>/libonnxruntime4j_jni.so
#   app/libs/onnxruntime-1.24.1.jar
# They are built from source (F-Droid policy) by the existing Linux scripts
# scripts/build_opencv_android.sh, scripts/prepare_opencv.sh and
# scripts/build_onnxruntime_android.sh. This wrapper reproduces the CI setup
# (.github/workflows/build-release.yml) in a WSL Linux distro so that a
# Windows developer can produce those artifacts locally.
#
# Usage (from Windows): scripts\local\build-natives-wsl.bat
# Usage (inside WSL, as root):
#   WIN_REPO=/mnt/c/path/to/repo ABIS="arm64-v8a x86_64" bash scripts/local/wsl_build_natives.sh
#
# Everything heavy happens on the Linux filesystem (/opt/qwipaper-native);
# only the final artifacts are copied back into the Windows checkout.
# =============================================================================
set -Eeuo pipefail

WIN_REPO="${WIN_REPO:?WIN_REPO must point to the repository (e.g. /mnt/c/Users/me/scan-copy)}"
ABIS="${ABIS:-arm64-v8a x86_64}"
NDK_VERSION="28.0.13004108"          # = android.ndkVersion in app/build.gradle and CI
CMAKE_VERSION="3.31.6"               # = CI / F-Droid
SDK_DIR="${SDK_DIR:-/opt/android-sdk}"
JDK_DIR="${JDK_DIR:-/opt/jdk-21}"
VENV_DIR="${VENV_DIR:-/opt/qwipaper-venv}"
WORK_DIR="${WORK_DIR:-/opt/qwipaper-native}"
CMDLINE_TOOLS_ZIP="commandlinetools-linux-13114758_latest.zip"

LOG_DIR="$WIN_REPO/build-logs"
mkdir -p "$LOG_DIR"
PROGRESS="$LOG_DIR/natives-progress.txt"
: > "$PROGRESS"
exec > >(tee -a "$LOG_DIR/natives.log") 2>&1

step() { echo; echo "=== [$(date +%H:%M:%S)] $*"; echo "$(date '+%F %T') $*" >> "$PROGRESS"; }
fail() { echo "$(date '+%F %T') FAILED: $*" >> "$PROGRESS"; echo "ERROR: $*" >&2; exit 1; }
trap 'fail "line $LINENO: $BASH_COMMAND"' ERR

[ "$(id -u)" = "0" ] || fail "run as root (wsl -u root)"
step "Start – ABIs: $ABIS – $(uname -srm) – $(nproc) CPU – $(free -g | awk '/Mem/{print $2}') GB RAM"

# ---------------------------------------------------------------- 1. packages
step "1/7 apt packages"
export DEBIAN_FRONTEND=noninteractive
apt-get update -qq
apt-get install -y -qq --no-install-recommends \
  git ca-certificates curl unzip zip rsync file perl make build-essential \
  python3 python3-venv python3-pip ninja-build >/dev/null
git config --global --add safe.directory '*'

# ---------------------------------------------------------------- 2. JDK 21
step "2/7 JDK 21"
if [ ! -x "$JDK_DIR/bin/java" ]; then
  mkdir -p "$JDK_DIR"
  curl -fsSL "https://api.adoptium.net/v3/binary/latest/21/ga/linux/x64/jdk/hotspot/normal/eclipse" -o /tmp/jdk21.tgz
  tar -xzf /tmp/jdk21.tgz -C "$JDK_DIR" --strip-components=1
  rm -f /tmp/jdk21.tgz
fi
export JAVA_HOME="$JDK_DIR"
export PATH="$JAVA_HOME/bin:$PATH"
java -version

# ---------------------------------------------------------------- 3. CMake + Python deps
step "3/7 Python venv (cmake $CMAKE_VERSION, flatbuffers)"
[ -x "$VENV_DIR/bin/python3" ] || python3 -m venv "$VENV_DIR"
"$VENV_DIR/bin/pip" install -q --upgrade pip
"$VENV_DIR/bin/pip" install -q "cmake==$CMAKE_VERSION" flatbuffers
export PATH="$VENV_DIR/bin:$PATH"
cmake --version | head -1

# ---------------------------------------------------------------- 4. Android SDK (Linux)
step "4/7 Android SDK + NDK $NDK_VERSION"
if [ ! -x "$SDK_DIR/cmdline-tools/latest/bin/sdkmanager" ]; then
  mkdir -p "$SDK_DIR/cmdline-tools"
  curl -fsSL "https://dl.google.com/android/repository/$CMDLINE_TOOLS_ZIP" -o /tmp/cmdline-tools.zip
  rm -rf /tmp/cmdline-tools-unzip && unzip -q /tmp/cmdline-tools.zip -d /tmp/cmdline-tools-unzip
  rm -rf "$SDK_DIR/cmdline-tools/latest"
  mv /tmp/cmdline-tools-unzip/cmdline-tools "$SDK_DIR/cmdline-tools/latest"
  rm -rf /tmp/cmdline-tools.zip /tmp/cmdline-tools-unzip
fi
SDKM="$SDK_DIR/cmdline-tools/latest/bin/sdkmanager"
yes | "$SDKM" --sdk_root="$SDK_DIR" --licenses >/dev/null || true
"$SDKM" --sdk_root="$SDK_DIR" "platforms;android-36" "build-tools;36.0.0" "ndk;$NDK_VERSION" >/dev/null
export ANDROID_HOME="$SDK_DIR" ANDROID_SDK_ROOT="$SDK_DIR"
export ANDROID_NDK_HOME="$SDK_DIR/ndk/$NDK_VERSION"
[ -d "$ANDROID_NDK_HOME" ] || fail "NDK not installed"

# ---------------------------------------------------------------- 5. sources
step "5/7 Sources (repo + submodules opencv / onnxruntime)"
mkdir -p "$WORK_DIR"
SRC="$WORK_DIR/scan-copy"
if [ ! -d "$SRC/.git" ]; then
  git clone -q "$WIN_REPO" "$SRC"
else
  git -C "$SRC" fetch -q origin
  git -C "$SRC" reset -q --hard origin/HEAD 2>/dev/null || git -C "$SRC" reset -q --hard FETCH_HEAD
fi
# .gitmodules uses GitHub URLs; the pinned commits are fetched shallowly.
git -C "$SRC" submodule sync -q
git -C "$SRC" submodule update --init --recursive --depth 1 --jobs 2
git -C "$SRC" submodule status

# ---------------------------------------------------------------- 6. native builds
export SOURCE_DATE_EPOCH=1700000000 TZ=UTC LC_ALL=C LANG=C PYTHONHASHSEED=0
export BUILD_GENERATOR="Unix Makefiles"
export OPENCV_CMAKE="$(command -v cmake)" ORT_CMAKE="$(command -v cmake)" PY3_BIN="$(command -v python3)"
export ABIS
cd "$SRC"
chmod +x scripts/*.sh

step "6a/7 OpenCV 4.13.0 (build_opencv_android.sh)"
REQUIRE_PINNED_JNI=1 VERBOSE=1 ./scripts/build_opencv_android.sh
step "6b/7 prepare_opencv.sh"
./scripts/prepare_opencv.sh
step "6c/7 ONNX Runtime 1.24.1 (build_onnxruntime_android.sh) – long step"
./scripts/build_onnxruntime_android.sh

# ---------------------------------------------------------------- 7. copy back
step "7/7 Copy artifacts to Windows checkout"
rm -rf "$WIN_REPO/app/src/main/jniLibs"
mkdir -p "$WIN_REPO/app/src/main/jniLibs" "$WIN_REPO/app/libs"
rsync -a "$SRC/app/src/main/jniLibs/" "$WIN_REPO/app/src/main/jniLibs/"
rsync -a "$SRC/app/libs/" "$WIN_REPO/app/libs/"
( cd "$WIN_REPO" && find app/src/main/jniLibs app/libs -type f -exec ls -l {} \; ) | tee "$LOG_DIR/natives-artifacts.txt"
for f in "$WIN_REPO"/app/src/main/jniLibs/*/*.so; do file "$f"; done | tee -a "$LOG_DIR/natives-artifacts.txt"

step "DONE"
