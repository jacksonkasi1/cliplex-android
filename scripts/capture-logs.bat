@echo off
if not exist "%~dp0..\capture-logs" mkdir "%~dp0..\capture-logs"
"D:\Program\Android\sdk\platform-tools\adb.exe" logcat -v threadtime > "%~dp0..\capture-logs\cliplex-logcat.txt"
