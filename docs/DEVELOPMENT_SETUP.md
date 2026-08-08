# Development setup

Verified local tool locations on 2026-08-07:

- Project: `D:\WORK\WORK\OPENSOURCE\cliplex-android`
- Android SDK / ADB: `D:\Program\Android\sdk`
- NDK: `27.2.12479018`
- CMake: `3.22.1`
- JDK: `D:\Program\Java\jdk-17`
- Gradle wrapper: `8.11.1`
- compile/target SDK: 36; minimum SDK: 29
- native ABI: `arm64-v8a`

Set `JAVA_HOME` to the JDK 17 directory if the shell default differs. `local.properties` should contain the local SDK path and must not be committed.

```powershell
git submodule update --init --recursive
.\gradlew.bat testSafeDebugUnitTest testOverlayDebugUnitTest
.\gradlew.bat assembleOverlayDebug
D:\Program\Android\sdk\platform-tools\adb.exe devices -l
.\gradlew.bat installOverlayDebug
```

`safeDebug` omits the floating-control behavior; `overlayDebug` uses application-overlay permission and provides notification actions as the capture control. Release builds require the caller's signing configuration.
