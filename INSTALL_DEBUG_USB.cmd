@echo off
setlocal EnableExtensions
cd /d "%~dp0"
set "ADB=%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe"
set "APK=%~dp0app\build\outputs\apk\debug\app-debug.apk"
if not exist "%ADB%" (
  echo ERROR: adb.exe not found at %ADB%
  exit /b 1
)
if not exist "%APK%" (
  echo ERROR: No newly built debug APK found.
  echo First run BUILD_DEBUG_WINDOWS.cmd.
  exit /b 1
)
echo The app previously triggered a Play Protect sensitive-data warning.
echo This script will NOT disable Play Protect or bypass any verification.
choice /C YN /M "Attempt a normal Android USB install of this debug APK?"
if errorlevel 2 exit /b 0
"%ADB%" devices
"%ADB%" install -r "%APK%"
if errorlevel 1 (
  echo INSTALL FAILED. Do not bypass Play Protect. Review the error and run crash capture if needed.
  exit /b 1
)
echo INSTALL SUCCESS. Trying launch...
"%ADB%" shell monkey -p com.jarvisblue.mobile.debug -c android.intent.category.LAUNCHER 1
echo If it closes immediately, run CAPTURE_CRASH_WINDOWS.cmd.
exit /b 0
