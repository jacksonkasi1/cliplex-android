# ClipLex v1.0.0

ClipLex v1.0.0 is the submission-ready ARM64 Android build for the Arm AI Optimization Challenge.

## Highlights

- Private on-device playback capture, transcription and translation.
- Captures permitted learning moments for up to 180 seconds (3 minutes).
- Video-first lessons with safe audio fallback.
- Bounded video and audio captions that remain usable with long text.
- Tappable vocabulary, saved words, quizzes and pronunciation practice.
- Refined Android-native V2 interface with consistent hierarchy and accessible touch targets.
- `arm64-v8a`-only native packaging and quantified Whisper optimization on an OPPO CPH2781.

## Distribution profile

- Android 10+ (`minSdk 29`).
- ARM64 only.
- Release-optimized, R8-minified, resource-shrunk and non-debuggable.
- Direct-install APK for challenge judges.
- Compact safe flavor with no overlay permission and no bundled LiteRT-LM runtime.
- Speech and translation model files are downloaded separately after installation.

## Verified optimization result

A controlled 9.4-second Whisper test on the OPPO CPH2781 reduced median native inference from 2,393.764 ms to 692.659 ms, a 3.456x speedup and 71.06% latency reduction, while preserving normalized transcript content.

## Important scope

ClipLex works only with permitted Android playback-capture sources. Protected media, DRM, or source-app policy can block audio or video capture. The app does not bypass these restrictions.

Shorter focused clips usually process faster and produce cleaner practice activities, even though the maximum capture length is three minutes.

Google Play publishing requires the maintainer's permanent private upload key. The attached APK is intended for direct challenge installation and evaluation.
