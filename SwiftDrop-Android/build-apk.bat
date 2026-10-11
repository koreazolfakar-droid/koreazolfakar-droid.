@echo off
setlocal
cd /d "%~dp0"
where gradle >nul 2>&1
if errorlevel 1 (
 echo Gradle 8.13 is not installed/on PATH. Use GitHub Actions or install Gradle.
 exit /b 1
)
if "%ANDROID_HOME%"=="" if "%ANDROID_SDK_ROOT%"=="" (
 echo Android SDK not configured. Set ANDROID_HOME in Android Studio.
 exit /b 1
)
call gradle --no-daemon :app:testDebugUnitTest :app:assembleDebug
if errorlevel 1 exit /b 1
echo APK ready: %CD%\app\build\outputs\apk\debug\app-debug.apk
