@echo off
setlocal
call "%~dp0..\gradlew.bat" assembleOverlayDebug || exit /b 1
"D:\Program\Android\sdk\platform-tools\adb.exe" install -r "%~dp0..\app\build\outputs\apk\overlay\debug\app-overlay-debug.apk"
