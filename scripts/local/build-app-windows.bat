@echo off
REM ===========================================================================
REM QwiPaper - Gradle build on Windows (Debug + Release APKs). Logs: build-logs\
REM
REM Native libraries (OpenCV, ONNX Runtime):
REM   NATIVE_DEPS=source   -> uses app\src\main\jniLibs + app\libs
REM                           (built by scripts\local\build-natives-wsl.bat)
REM   NATIVE_DEPS=prebuilt -> official Maven binaries (dev only, no toolchain needed)
REM   default: source if the built libs exist, prebuilt otherwise.
REM Optional: set ABIS=arm64-v8a x86_64   (default)
REM Optional: set QWI_INITIAL_CHECK=1 to first run a build without natives (diagnostic)
REM ===========================================================================
setlocal EnableDelayedExpansion
pushd "%~dp0..\.."
if not exist build-logs mkdir build-logs
set STATUS=build-logs\gradle-status.txt
echo started %DATE% %TIME%> %STATUS%

REM ---- JDK 21 (Temurin, else Android Studio's bundled JBR)
if "%JAVA_HOME%"=="" (
  for /d %%d in ("%ProgramFiles%\Eclipse Adoptium\jdk-21*") do set "JAVA_HOME=%%d"
)
if "%JAVA_HOME%"=="" set "JAVA_HOME=%ProgramFiles%\Android\Android Studio\jbr"
echo JAVA_HOME=%JAVA_HOME%>> %STATUS%

REM ---- Android SDK location -> local.properties (not versioned)
if "%ANDROID_HOME%"=="" set "ANDROID_HOME=%LOCALAPPDATA%\Android\Sdk"
if not exist local.properties (
  REM Java properties syntax: forward slashes + escaped drive colon (C\:/...), else lint PropertyEscape error
  set "SDKFWD=%ANDROID_HOME:\=/%"
  set "SDKFWD=!SDKFWD::=\:!"
  echo sdk.dir=!SDKFWD!> local.properties
)
type local.properties >> %STATUS%

REM ---- NDK version pinned by app/build.gradle (AGP uses it to strip .so files)
if not exist "%ANDROID_HOME%\ndk\28.0.13004108" (
  echo installing ndk 28.0.13004108>> %STATUS%
  echo y| call "%ANDROID_HOME%\cmdline-tools\latest\bin\sdkmanager.bat" "ndk;28.0.13004108" > build-logs\sdkmanager.log 2>&1
  echo sdkmanager exit=!ERRORLEVEL! ^(non-zero may be a harmless sdkmanager crash after install^)>> %STATUS%
)

if "%ABIS%"=="" set ABIS=arm64-v8a x86_64
if "%NATIVE_DEPS%"=="" (
  if exist app\src\main\jniLibs\arm64-v8a\libopencv_java4.so (set NATIVE_DEPS=source) else (set NATIVE_DEPS=prebuilt)
)
echo ABIS=%ABIS% NATIVE_DEPS=%NATIVE_DEPS%>> %STATUS%
set GRADLE_ARGS=--no-daemon --stacktrace --warning-mode=summary "-PABIS=%ABIS%"

if "%QWI_INITIAL_CHECK%"=="1" call :run initial "-PnativeDeps=source" :app:assembleStandardDebug
set GRADLE_ARGS=%GRADLE_ARGS% "-PnativeDeps=%NATIVE_DEPS%"
call :run debug      :app:assembleStandardDebug :app:assemblePaddleDebug
call :run release    :app:assembleStandardRelease :app:assemblePaddleRelease
call :run verifyapk  :app:verifyNoTestDataInApk
call :run unittests  :app:testStandardDebugUnitTest
call :run lint       :app:lintStandardDebug

dir /s /b app\build\outputs\apk\*.apk >> %STATUS% 2>nul
echo finished %DATE% %TIME%>> %STATUS%
popd
endlocal
exit /b 0

:run
set NAME=%1
shift
set TASKS=
:collect
if "%~1"=="" goto go
set TASKS=%TASKS% %1
shift
goto collect
:go
echo [%TIME%] %NAME%:%TASKS%>> %STATUS%
call gradlew.bat %GRADLE_ARGS% %TASKS% > build-logs\gradle-%NAME%.log 2>&1
echo [%TIME%] %NAME% exit=!ERRORLEVEL!>> %STATUS%
exit /b 0
