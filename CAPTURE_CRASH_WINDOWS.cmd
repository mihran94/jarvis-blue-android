@echo off
setlocal EnableExtensions
set "ADB=%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe"
if not exist "%ADB%" (
  echo ERROR: adb.exe not found.
  exit /b 1
)
"%ADB%" devices
"%ADB%" logcat -c
"%ADB%" shell am force-stop com.jarvisblue.mobile.debug
"%ADB%" shell monkey -p com.jarvisblue.mobile.debug -c android.intent.category.LAUNCHER 1
ping 127.0.0.1 -n 5 >nul
"%ADB%" logcat -d -b main -b system -b crash -v time AndroidRuntime:E JarvisBlueStartup:E ActivityTaskManager:W '*:S' > "%~dp0jarvis-crash.txt"
echo Log saved to %~dp0jarvis-crash.txt
