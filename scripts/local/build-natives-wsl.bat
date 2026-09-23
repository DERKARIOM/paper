@echo off
REM QwiPaper - builds OpenCV + ONNX Runtime native libs in WSL (see wsl_build_natives.sh)
REM Optional: set ABIS=arm64-v8a x86_64  and  set WSL_DISTRO=Debian before running.
setlocal
if "%WSL_DISTRO%"=="" set WSL_DISTRO=Debian
if "%ABIS%"=="" set ABIS=arm64-v8a x86_64
pushd "%~dp0..\.."
if not exist build-logs mkdir build-logs
set LAUNCH_LOG=%CD%\build-logs\natives-launcher.log
echo [%DATE% %TIME%] launcher start in %CD%> "%LAUNCH_LOG%"
wsl --status >> "%LAUNCH_LOG%" 2>&1
wsl -l -v >> "%LAUNCH_LOG%" 2>&1
set WREPO=
for /f "usebackq delims=" %%i in (`wsl -d %WSL_DISTRO% -u root -- wslpath -a "%CD%"`) do set "WREPO=%%i"
echo WREPO=%WREPO%>> "%LAUNCH_LOG%"
if "%WREPO%"=="" (
  echo ERROR: could not resolve repo path inside WSL distro %WSL_DISTRO%>> "%LAUNCH_LOG%"
  goto end
)
wsl -d %WSL_DISTRO% -u root -- env WIN_REPO="%WREPO%" ABIS="%ABIS%" bash "%WREPO%/scripts/local/wsl_build_natives.sh" >> "%LAUNCH_LOG%" 2>&1
echo [%DATE% %TIME%] wsl exit code: %ERRORLEVEL%>> "%LAUNCH_LOG%"
:end
popd
endlocal
