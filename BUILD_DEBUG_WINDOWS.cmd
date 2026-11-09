@echo off
setlocal EnableExtensions
cd /d "%~dp0"
echo JARVIS BLUE MOBILE v1.1.8 - Gemini and Voice fixes debug build
set "JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-17.0.21.9-hotspot"
if not exist "%JAVA_HOME%\bin\java.exe" (
  echo ERROR: JDK 17 not found: %JAVA_HOME%
  exit /b 1
)
set "PATH=%JAVA_HOME%\bin;%PATH%"
set "ANDROID_HOME=%LOCALAPPDATA%\Android\Sdk"
if not exist "%ANDROID_HOME%\platforms\android-35\android.jar" (
  echo ERROR: Android SDK API 35 not installed at %ANDROID_HOME%
  exit /b 1
)
set "ANDROID_SDK_ROOT=%ANDROID_HOME%"
set "SDK_SLASHES=%ANDROID_HOME:\=/%"
> local.properties echo sdk.dir=%SDK_SLASHES%
echo Using Java:
"%JAVA_HOME%\bin\java.exe" -version
call gradlew.bat :app:assembleDebug --no-daemon --console=plain
if errorlevel 1 (
  echo ERROR: Gradle build failed; do not install the old APK as a replacement.
  exit /b 1
)
if not exist "app\build\outputs\apk\debug\app-debug.apk" (
  echo ERROR: Build reported success but no APK was found.
  exit /b 1
)
echo.
echo SUCCESS: app\build\outputs\apk\debug\app-debug.apk
set "SIGNER=%ANDROID_HOME%\build-tools\36.0.0\apksigner.bat"
if exist "%SIGNER%" (
  call "%SIGNER%" verify --verbose "app\build\outputs\apk\debug\app-debug.apk"
  if errorlevel 1 exit /b 1
)
echo.
echo APK ready. It has NOT been installed automatically.
echo Play Protect may still flag its sensitive permissions. Do not disable Play Protect.
exit /b 0
