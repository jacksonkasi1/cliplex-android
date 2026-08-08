# ClipLex

**Turn every clip into a language lesson.**

ClipLex is a private, on-device Android language-learning app that transforms short moments from videos into replayable lessons, translated vocabulary, quizzes, speaking practice, and AI tutoring.

[Download the latest ClipLex APK](https://github.com/jacksonkasi1/cliplex-android/releases/latest/download/cliplex-v1.0.0-alpha01.apk)

## What ClipLex does

ClipLex learns from media you already watch—YouTube Shorts, Instagram Reels, TikTok, local videos, podcasts, and other apps that allow Android playback capture. It does not download or scrape the source video.

- Captures a short user-selected audio or video moment.
- Transcribes speech locally with `whisper.cpp` or Android on-device recognition.
- Translates sentences and vocabulary with on-device ML Kit models.
- Opens words with their native-language meaning and English-letter pronunciation, such as `भाई → bhai`.
- Saves useful words with their correct source and meaning languages.
- Creates lesson-grounded quizzes automatically.
- Scores pronunciation privately using the microphone and local Whisper inference.
- Provides an optional Gemma 3 1B tutor grounded in the captured lesson.
- Uses an audio-focused player by default, with video available when the learner wants it.

## How it works

1. Choose the language you are learning and your native language.
2. Start Learning Mode and approve Android's playback-capture prompt.
3. Play a permitted video or audio source, then start and finish a short capture.
4. ClipLex creates a private lesson containing synchronized audio/video, transcript, and translation.
5. Tap words to hear them, view meanings, see English-script pronunciation, or save them.
6. Open **Practice** for quizzes, Speak & Match pronunciation, and lesson-grounded AI help.

Captured media, transcripts, vocabulary, and AI prompts stay on the device. Protected or DRM media may block Android playback capture; ClipLex does not bypass platform or source-app restrictions.

## Product identity

- App name: **ClipLex**
- Play Store title: **ClipLex – Learn from Videos**
- Tagline: **Turn every clip into a language lesson.**
- Android package: `com.jacksonkasi.cliplex`
- Repository: `cliplex-android`

## Install

ClipLex currently requires Android 10 or newer on an ARM64 device.

1. Download [`cliplex-v1.0.0-alpha01.apk`](https://github.com/jacksonkasi1/cliplex-android/releases/latest/download/cliplex-v1.0.0-alpha01.apk).
2. Allow installation from your browser or file manager when Android asks.
3. Open ClipLex and download the suggested local speech model during onboarding.

The application works without Gemma through its grounded quiz and tutor fallback. Gemma smart explanations require the separately licensed local Gemma 3 1B model.

## Build from source

Requirements: Android SDK 36, NDK `27.2.12479018`, CMake `3.22.1`, JDK 21, and the initialized `whisper.cpp` submodule.

```powershell
git submodule update --init --recursive
.\gradlew.bat :app:testSafeDebugUnitTest :app:assembleSafeDebug
```

Install the safe build on a connected device:

```powershell
adb install -r app\build\outputs\apk\safe\debug\app-safe-debug.apk
```

The `overlay` flavor additionally provides an optional floating capture control.

## Goal

ClipLex aims to turn everyday watching into active learning: capture a meaningful moment, understand it immediately, retain its vocabulary, practise saying it, and revisit it through personalized exercises without sending private learning data to a server.

See [development setup](docs/DEVELOPMENT_SETUP.md), [capture diagnostics](docs/AUDIO_CAPTURE_DIAGNOSTICS.md), and [known limitations](docs/KNOWN_LIMITATIONS.md).
