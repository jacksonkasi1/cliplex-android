# Development setup

Verified toolchain:

- Android SDK / compile and target API: 36
- Minimum Android API: 29
- NDK: `27.2.12479018`
- CMake: `3.22.1`
- JDK: 21 for CI and all variants
- Gradle wrapper: `8.11.1`
- Native ABI: `arm64-v8a`

Set `JAVA_HOME` to JDK 21 if the shell default differs. `local.properties` should contain the local SDK path and must not be committed.

```powershell
git submodule update --init --recursive
.\gradlew.bat :app:testSafeDebugUnitTest :app:assembleSafeDebug
.\gradlew.bat :app:testSafeSubmissionUnitTest :app:lintSafeSubmission :app:assembleSafeSubmission
```

Install the compact direct-distribution build:

```powershell
adb install -r app\build\outputs\apk\safe\submission\app-safe-submission.apk
```

Build the unsigned Play-ready app bundle:

```powershell
.\gradlew.bat :app:bundleSafeRelease
```

Variant responsibilities:

- `safeDebug`: diagnostics and benchmark tooling; no overlay permission; no LiteRT-LM runtime.
- `safeSubmission`: minified, resource-shrunk, non-debuggable, ARM64-only and directly installable for challenge judging.
- `safeRelease`: minified Play-ready output that must be signed with the maintainer's permanent upload key.
- `overlayDebug`: floating capture control plus the experimental optional Gemma/LiteRT-LM runtime.

The challenge-safe manifest removes `SYSTEM_ALERT_WINDOW` and the overlay service. Whisper and translation models remain downloadable assets and are not bundled in the APK.
