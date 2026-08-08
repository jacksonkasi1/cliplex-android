# Phase 01: Foundation & Project Structure

**Goal:** Create a compilable Android project with CMake/NDK setup, build scripts, and basic app shell.

## What to build
1. Complete Gradle project structure (settings.gradle.kts, build.gradle.kts at root and app level)
2. AndroidManifest.xml with all required permissions declared
3. CMakeLists.txt for whisper.cpp native build
4. Basic MainActivity with empty Compose content
5. Application class for lifecycle management
6. Debug scripts (check-device.bat, install-debug.bat)
7. .gitignore for Android + native + models
8. Documentation skeleton

## Files to create (Phase 01)
- `settings.gradle.kts`
- `build.gradle.kts` (root)
- `app/build.gradle.kts`
- `app/src/main/AndroidManifest.xml`
- `app/src/main/java/.../ClipLexApplication.kt`
- `app/src/main/java/.../MainActivity.kt`
- `app/src/main/java/.../ui/theme/Theme.kt`
- `app/src/main/res/values/strings.xml`
- `app/src/main/res/xml/filepaths.xml`
- `app/proguard-rules.pro`
- `CMakeLists.txt` (root, for native)
- `native/whisper.cpp/` (git submodule placeholder)
- `.gitignore`
- `scripts/check-device.bat`
- `scripts/install-debug.bat`
- `scripts/capture-logs.bat`
- `docs/` directory structure

## Build configuration
- minSdk: 29 (Android 10)
- targetSdk: 36
- compileSdk: 36
- Kotlin 2.x
- AGP 8.x
- NDK: 27.2
- CMake: 3.22.1
- arm64-v8a only
- Release: -O3, no debug symbols

## Acceptance criteria
- `gradlew.bat assembleDebug` succeeds
- APK installs via `install-debug.bat` on OPPO F31 5G
- App launches to an empty screen
- No native crash at launch
