@echo off
echo Benchmark UI/export is pending. Running build and unit verification only.
call "%~dp0..\gradlew.bat" testOverlayDebugUnitTest assembleOverlayDebug
