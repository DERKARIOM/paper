@echo off
REM Paper - runs scripted UI steps on the connected Android device (adb) and records the result.
REM Steps are read from build-logs\device\steps.txt, one per line:
REM   tap X Y | swipe X1 Y1 X2 Y2 MS | key KEYCODE | text WORD | wait SECONDS | shell ARGS
REM   shot NAME   (screenshot NAME.png + UI hierarchy NAME.xml in build-logs\device)
REM   push LOCAL REMOTE | pull REMOTE LOCAL | install APK | clearlog | logcat NAME [filter]
REM Output log: build-logs\device\steps-log.txt
setlocal
pushd "%~dp0..\.."
if "%ANDROID_HOME%"=="" set "ANDROID_HOME=%LOCALAPPDATA%\Android\Sdk"
set "ADB=%ANDROID_HOME%\platform-tools\adb.exe"
set "OUT=build-logs\device"
if not exist "%OUT%" mkdir "%OUT%"
set "LOG=%OUT%\steps-log.txt"
echo start %date% %time%> "%LOG%"
"%ADB%" devices >> "%LOG%" 2>&1
for /f "usebackq tokens=1,*" %%a in ("%OUT%\steps.txt") do (
  echo [%%a] %%b>> "%LOG%"
  if /i "%%a"=="tap" "%ADB%" shell input tap %%b >> "%LOG%" 2>&1
  if /i "%%a"=="swipe" "%ADB%" shell input swipe %%b >> "%LOG%" 2>&1
  if /i "%%a"=="key" "%ADB%" shell input keyevent %%b >> "%LOG%" 2>&1
  if /i "%%a"=="text" "%ADB%" shell input text %%b >> "%LOG%" 2>&1
  if /i "%%a"=="shell" "%ADB%" shell %%b >> "%LOG%" 2>&1
  if /i "%%a"=="wait" ping -n %%b 127.0.0.1 >nul
  if /i "%%a"=="install" "%ADB%" install -r %%b >> "%LOG%" 2>&1
  if /i "%%a"=="clearlog" "%ADB%" logcat -c >> "%LOG%" 2>&1
  if /i "%%a"=="push" (
    for /f "tokens=1,2" %%x in ("%%b") do "%ADB%" push "%%x" "%%y" >> "%LOG%" 2>&1
  )
  if /i "%%a"=="pull" (
    for /f "tokens=1,2" %%x in ("%%b") do "%ADB%" pull "%%x" "%%y" >> "%LOG%" 2>&1
  )
  if /i "%%a"=="logcat" (
    for /f "tokens=1,*" %%x in ("%%b") do "%ADB%" logcat -d %%y > "%OUT%\%%x.txt" 2>&1
  )
  if /i "%%a"=="shot" (
    "%ADB%" exec-out screencap -p > "%OUT%\%%b.png"
    "%ADB%" shell uiautomator dump /sdcard/paper-ui.xml >nul 2>&1
    "%ADB%" pull /sdcard/paper-ui.xml "%OUT%\%%b.xml" >nul 2>&1
  )
)
echo end %date% %time%>> "%LOG%"
popd
endlocal
