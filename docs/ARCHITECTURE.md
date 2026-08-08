# Architecture

The application is Kotlin/Compose with one ARM64 JNI library. `ServiceLocator` owns DataStore, Room, the model repository, and reusable Whisper/translation engines.

```text
Onboarding / Settings
  -> persisted LearningMode -> one required Tiny model + Whisper language policy
  -> persisted mother tongue -> ML Kit translation target
MainActivity permission flow
  -> CaptureService foreground service
     -> AudioPlaybackCaptureConfiguration + AudioRecord (device-native PCM16)
     -> phase-aware stereo downmix + 16 kHz resampling
     -> bounded 60-second in-memory ring buffer
     -> optional MediaProjection VirtualDisplay + H.264 screen recording
     -> AAC encode captured playback PCM + MP4 remux
     -> private WAV + optional MP4
     -> AudioDiagnostics
  -> HomeViewModel (one inference at a time)
     -> reusable WhisperEngine -> JNI -> whisper.cpp
     -> ML Kit Translator model download + sentence translation
     -> progressive UI list updates
     -> progressive Room LearningSession update
  -> LearningSessionScreen (video/audio playback + synchronized learning UI)
```

`CaptureService` becomes Armed after MediaProjection consent and a successful `AudioRecord.startRecording()`. PCM reads are intentionally discarded until the user explicitly taps Start. With Capture Video enabled, that tap creates the projection's single `VirtualDisplay` and starts an H.264 surface recorder; a second tap ends the one clip, adds captured playback PCM as AAC, writes the 16 kHz WAV, creates the Room row, ends projection, and opens that exact lesson. Android 14+'s one-token/one-display rule therefore maps to one explicit clip and fresh consent for the next clip.

`MainActivity` uses a single task so returning from a source app or opening the capture notification reuses the existing Compose/ViewModel instance. On Android 14+ it requests whole-display projection to avoid an OEM single-app chooser that can return without establishing playback capture. Session processing waits for the initial learning-mode model preload, and model loading/inference are serialized around the single native context.

Native code pins `whisper.cpp` v1.7.6 as a Git submodule. A mutex protects the single reusable native context. PCM16 samples are normalized to float in JNI, timed segments are emitted progressively, and the fixed/detected source language is passed to ML Kit. Short English clips retain the validated `audio_ctx=512` fast path while preserving timestamps for lesson synchronization. Models remain in private app storage and are never committed or silently bundled into the APK.

Room version 2 stores lesson metadata, private media paths, processing state, errors, and JSON-encoded timed segments. MP4/WAV files live only under the app's private learning-session directory. `SessionRepository` validates canonical paths before deletion. Delete Video clears only MP4; Delete Lesson removes both media files and the Room row. Saved vocabulary is independent of session deletion.
