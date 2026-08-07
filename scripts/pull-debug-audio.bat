@echo off
setlocal
if not exist "%~dp0..\debug-audio" mkdir "%~dp0..\debug-audio"
"D:\Program\Android\sdk\platform-tools\adb.exe" pull "/sdcard/Android/data/com.learnthis.overlay.debug/files/diagnostics" "%~dp0..\debug-audio"
