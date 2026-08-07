# Architecture

The application is Kotlin/Compose with one ARM64 JNI library. `ServiceLocator` owns DataStore, Room, the model repository, and reusable Whisper/translation engines.

```text
MainActivity permission flow
  -> CaptureService foreground service
     -> AudioPlaybackCaptureConfiguration + AudioRecord (16 kHz mono PCM16)
     -> bounded 60-second in-memory ring buffer
     -> AudioDiagnostics
  -> HomeViewModel (one inference at a time)
     -> reusable WhisperEngine -> JNI -> whisper.cpp
     -> ML Kit Translator model download + sentence translation
     -> progressive UI list updates
     -> Room session metadata
```

`CaptureService` is armed after MediaProjection consent. It continuously owns one `AudioRecord` but only writes into the session buffer between Start Capture and Finish. The overlay and notification issue those actions without reopening `AudioRecord`. Projection revocation, capture read errors, silence, zero-filled capture, short capture, native failure, and translation failure have distinct states.

Native code pins `whisper.cpp` v1.7.6 as a Git submodule. A mutex protects the single reusable native context. PCM16 samples are normalized to float in JNI, transcription uses four threads, timestamps are returned in milliseconds, and the detected source language is passed to ML Kit. Models remain in private app storage and are never committed.

Room stores session metadata only (language pair, duration, segment count, time). Captured PCM is held only for the active UI session so sentence replay can slice by Whisper timestamps; it is not written to Room or uploaded.
