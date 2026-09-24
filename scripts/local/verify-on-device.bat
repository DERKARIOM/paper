@echo off
REM Paper - installs the Paper debug APK on a connected Android device/emulator (adb),
REM launches it, and captures screenshots + crash log in build-logs\device\.
REM Build first with scripts\local\build-app-windows.bat.
setlocal EnableDelayedExpansion
pushd "%~dp0..\.."
if "%ANDROID_HOME%"=="" set "ANDROID_HOME=%LOCALAPPDATA%\Android\Sdk"
set "ADB=%ANDROID_HOME%\platform-tools\adb.exe"
set OUT=build-logs\device
if not exist %OUT% mkdir %OUT%
del /q %OUT%\* 2>nul
"%ADB%" start-server > %OUT%\adb.txt 2>&1
"%ADB%" devices -l > %OUT%\devices.txt 2>&1
set SERIAL=
for /f "skip=1 tokens=1,2" %%a in ('"%ADB%" devices') do if "%%b"=="device" if "!SERIAL!"=="" set SERIAL=%%a
if "%SERIAL%"=="" (
  echo NO_DEVICE> %OUT%\result.txt
  goto end
)
for /f %%a in ('"%ADB%" -s %SERIAL% shell getprop ro.product.cpu.abi') do set ABI=%%a
for /f "delims=" %%a in ('"%ADB%" -s %SERIAL% shell getprop ro.build.version.release') do set REL=%%a
set APK=app\build\outputs\apk\paddle\debug\app-paddle-%ABI%-debug.apk
echo serial=%SERIAL% abi=%ABI% android=%REL% apk=%APK%> %OUT%\result.txt
"%ADB%" -s %SERIAL% install -r "%APK%" > %OUT%\install.txt 2>&1
echo install_exit=!ERRORLEVEL!>> %OUT%\result.txt
"%ADB%" -s %SERIAL% logcat -c
"%ADB%" -s %SERIAL% shell monkey -p com.qwiper.paper -c android.intent.category.LAUNCHER 1 > %OUT%\launch.txt 2>&1
ping -n 9 127.0.0.1 >nul
"%ADB%" -s %SERIAL% shell pidof com.qwiper.paper > %OUT%\pid.txt 2>&1
"%ADB%" -s %SERIAL% exec-out screencap -p > %OUT%\app.png
"%ADB%" -s %SERIAL% logcat -d -b crash > %OUT%\crash.txt 2>&1
"%ADB%" -s %SERIAL% logcat -d -s AndroidRuntime:E ActivityManager:I > %OUT%\logcat.txt 2>&1
"%ADB%" -s %SERIAL% shell input keyevent KEYCODE_HOME
ping -n 3 127.0.0.1 >nul
"%ADB%" -s %SERIAL% exec-out screencap -p > %OUT%\home.png
echo done>> %OUT%\result.txt
:end
popd
endlocal
