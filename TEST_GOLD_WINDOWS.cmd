@echo off
setlocal EnableExtensions
cd /d "%~dp0"
set "JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-17.0.21.9-hotspot"
if not exist "%JAVA_HOME%\bin\java.exe" (
 echo ERROR: JDK 17 not found. Install JDK 17 or update JAVA_HOME in this script.
 exit /b 1
)
set "PATH=%JAVA_HOME%\bin;%PATH%"
set "ANDROID_HOME=%LOCALAPPDATA%\Android\Sdk"
set "ANDROID_SDK_ROOT=%ANDROID_HOME%"
set "SDK_SLASHES=%ANDROID_HOME:\=/%"
> local.properties echo sdk.dir=%SDK_SLASHES%
call gradlew.bat :feature:studio:testDebugUnitTest --no-daemon --console=plain
exit /b %errorlevel%
