# Local build (QwiPaper)

This guide explains how to build the app on a developer machine. It supplements the CI
workflow (`.github/workflows/build-release.yml`), which is still the reference build.

## 1. What a clean clone is missing

The repository does **not** contain any native binaries (F-Droid policy). The app needs:

| Artifact | Produced by |
|---|---|
| `app/src/main/jniLibs/<abi>/libopencv_java4.so` | `scripts/build_opencv_android.sh` + `scripts/prepare_opencv.sh` (submodule `external/opencv`, 4.13.0) |
| `app/src/main/jniLibs/<abi>/libonnxruntime.so`, `libonnxruntime4j_jni.so` | `scripts/build_onnxruntime_android.sh` (submodule `external/onnxruntime`, 1.24.1) |
| `app/libs/onnxruntime-1.24.1.jar` | same script |

Without them, `compile*JavaWithJavac` fails (`package ai.onnxruntime does not exist`) and the
app would crash at startup (`System.loadLibrary("opencv_java4")`).

There are two ways to provide them, selected by the Gradle property `nativeDeps`.

## 2. Option A — `nativeDeps=source` (default, reproducible, same as CI/F-Droid)

The native scripts are Bash/Linux scripts. Run them on Linux, macOS or **WSL** (Windows).

Requirements (same as CI): JDK 21, Android SDK (`platforms;android-36`, `build-tools;36.0.0`),
NDK `28.0.13004108`, CMake **3.31.6** (≥ 3.28 required by ONNX Runtime), Python 3 with
`flatbuffers`, `git`, `make`, `perl`, and ~25 GB free disk. ONNX Runtime takes 20–60 min per ABI.

Windows + WSL (Debian/Ubuntu), fully automated:

```bat
scripts\local\build-natives-wsl.bat
```

It installs the toolchain inside WSL (`/opt/android-sdk`, `/opt/jdk-21`, `/opt/qwipaper-venv`),
clones the repo + submodules on the Linux filesystem, runs the three official scripts and copies
the artifacts back into `app/src/main/jniLibs` and `app/libs`. Default ABIs: `arm64-v8a x86_64`
(phone + emulator); override with `set ABIS=arm64-v8a armeabi-v7a x86 x86_64`.
Logs: `build-logs/natives*.{log,txt}`.

Linux/macOS: follow the same steps as the CI job (`git submodule update --init --recursive`,
then the three scripts with `ANDROID_NDK_HOME`, `ANDROID_SDK_ROOT`, `ABIS` exported).

### Option A bis — natives compiled by GitHub Actions (no local Linux needed)

Workflow `.github/workflows/build-natives.yml` runs the same three scripts on a GitHub Linux
runner (all four ABIs, ~1–2 h). It starts automatically when a `qwipaper/**` branch touching the
native scripts/submodules is pushed, or manually: **Actions → Build Native Libraries → Run workflow**.

1. Open the finished run and download the **`native-libs`** artifact (zip).
2. Unzip it **at the repository root**: it creates `app/src/main/jniLibs/<abi>/*.so` and
   `app/libs/onnxruntime-1.24.1.jar` (+ `native-libs.sha256`).
3. Build normally (default `-PnativeDeps=source`), e.g. `scripts\local\build-app-windows.bat`,
   which detects the libraries and switches to `source` automatically.

GitHub keeps the artifact 90 days.

## 3. Option B — `nativeDeps=prebuilt` (development only, no native toolchain)

```bat
gradlew.bat :app:assembleStandardDebug -PnativeDeps=prebuilt
```

Uses the official binaries from Maven Central, pinned to the same versions as the submodules:

* `org.opencv:opencv:4.13.0` (AAR) — only its `jni/<abi>/*.so` are extracted
  (`:app:extractPrebuiltOpenCvNatives`); the Java wrappers stay the in-tree `org.opencv` sources.
* `com.microsoft.onnxruntime:onnxruntime-android:1.24.1` (AAR, replaces jar + .so).

Limits: not reproducible, larger APK (full OpenCV + full ONNX Runtime), never use it for
releases or F-Droid. Behaviour should be identical; if in doubt, validate with Option A.

## 4. Building the app

`local.properties` (not versioned) must point to the SDK: `sdk.dir=C:/Users/<you>/AppData/Local/Android/Sdk`.
Android Studio creates it automatically.

Windows helper (Debug + Release + unit tests + lint, logs in `build-logs/`):

```bat
scripts\local\build-app-windows.bat
```

Useful tasks:

| Goal | Command |
|---|---|
| Debug APK (Tesseract flavor) | `gradlew :app:assembleStandardDebug` |
| Debug APK (PaddleOCR flavor, the one published on F-Droid) | `gradlew :app:assemblePaddleDebug` |
| Release APKs (unsigned without signing secrets) | `gradlew :app:assembleRelease` |
| Unit tests | `gradlew :app:testStandardDebugUnitTest` |
| Restrict ABIs | add `-PABIS="arm64-v8a x86_64"` |

APKs: `app/build/outputs/apk/<flavor>/<buildType>/app-<flavor>-<abi>-<buildType>.apk`.
Release APKs are unsigned unless `SIGNING_*` properties and `app/keystore.jks` are provided.
