@echo off
set "ADB=D:\Program\Android\sdk\platform-tools\adb.exe"
"%ADB%" devices -l
"%ADB%" shell getprop ro.product.model
"%ADB%" shell getprop ro.product.cpu.abi
"%ADB%" shell getprop ro.build.version.release
