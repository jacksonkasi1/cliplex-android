# Learn This for Android

Learn This is an Android 10+ video-first language-learning app. A user explicitly captures a short moment from another app through Android's `MediaProjection` and `AudioPlaybackCapture` APIs, then reviews that exact private clip with local `whisper.cpp` transcription, on-device ML Kit translation, synchronized subtitles, and vocabulary actions.

No source video is downloaded or scraped. User-initiated lesson clips, audio, transcripts, translations, and saved words stay in app-private storage and are not uploaded.

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

Speech models are not bundled. Choose **English Only** or **Multiple Languages** separately from the translation mother tongue. The app downloads only that mode's approximately 31 MiB Tiny model. Downloads use a private `.part` file and are accepted only after exact-size and SHA-256 verification.

## Current flow

1. Choose a learning mode and a supported mother tongue.
2. Download the required offline Tiny model. Switching modes keeps already downloaded models and requests only the missing one.
3. Tap Start Learning and approve the audio and Android screen-capture prompts. Android 14+ requires fresh screen-capture consent for each new learning session.
4. Open a source app and play permitted spoken media. Learning Mode is armed but does not save anything yet.
5. Tap the small green floating control (or Start Capture in the app/notification) to begin. Tap the red control to stop.
6. Learn This opens the exact new lesson automatically. The captured video appears first, English transcription appears next, and translation follows without blocking the source text.
7. Replay or seek the lesson, switch Word by Word/Sentence/Tamil View, tap English words, pronounce or save them, or reopen the lesson from History.

Source applications can prohibit playback capture. The app does not bypass that Android restriction and reports zero-filled/blocked capture separately from quiet audio and ASR failures. Capture negotiates 48 kHz, 44.1 kHz, and 16 kHz device formats, then downmixes and resamples locally to Whisper's 16 kHz mono input.

**Capture video** is on by default and can be disabled in Settings. If video capture is disabled or unavailable, the same flow produces an audio/text lesson. Delete Video preserves the lesson, transcript, audio, and saved words; Delete Lesson removes the session and its private media.

The floating control is optional. Its permission and notification settings are secondary actions under the Settings icon; in-app and notification controls remain available.

See [development setup](docs/DEVELOPMENT_SETUP.md), [capture diagnostics](docs/AUDIO_CAPTURE_DIAGNOSTICS.md), and [known limitations](docs/KNOWN_LIMITATIONS.md).
