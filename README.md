# Learn This for Android

Learn This is an Android 10+ prototype that captures **permitted playback audio** through Android's `MediaProjection` and `AudioPlaybackCapture` APIs, transcribes it locally with `whisper.cpp`, translates complete segments with on-device ML Kit, and lets the learner replay captured sentence audio.

No video is downloaded or scraped. Audio, transcripts, translations, and viewing activity are not uploaded.

## Build

Prerequisites are Android SDK 36, NDK `27.2.12479018`, CMake `3.22.1`, JDK 17, and an initialized submodule:

```powershell
git submodule update --init --recursive
.\gradlew.bat testOverlayDebugUnitTest assembleOverlayDebug
```

Install the floating-control flavor on a connected ARM64 phone:

```powershell
.\gradlew.bat installOverlayDebug
```

The speech model is not bundled. Select a mother tongue, then download Tiny Q5_1 in the app. Downloads resume from a `.part` file and are accepted only after their exact size and SHA-256 match.

## Current flow

1. Choose one of the eight MVP mother tongues.
2. Download Tiny Q5_1 (Base Q5_1 is optional).
3. Start Learning Mode and approve overlay, microphone, notification, and screen-capture prompts.
4. Open a source app that permits playback capture and play spoken media.
5. Tap the floating control or notification action to begin; tap again to finish.
6. Review diagnostics, transcript, translation, timestamps, replay, and the Room-backed session history.

Source applications can prohibit playback capture. The app does not bypass that Android restriction and reports zero-filled/blocked capture separately from quiet audio and ASR failures.

See [development setup](docs/DEVELOPMENT_SETUP.md), [capture diagnostics](docs/AUDIO_CAPTURE_DIAGNOSTICS.md), and [known limitations](docs/KNOWN_LIMITATIONS.md).
