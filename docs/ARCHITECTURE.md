# Architecture & Complete Implementation Plan

## Project Overview
On-device short-video language learning app. Captures playback audio → transcribes with Whisper → translates with ML Kit → shows progressive results. All on-device, no cloud.

## Directory Structure
```
learn-this-android/
├── app/
│ └── src/main/
│ ├── java/.../learnthis/
│ │ ├── LearnThisApplication.kt
│ │ ├── di/ # DI modules (manual)
│ │ ├── data/
│ │ │ ├── local/
│ │ │ │ ├── PreferencesDataStore.kt
│ │ │ │ └── AppDatabase.kt
│ │ │ ├── model/
│ │ │ │ ├── Language.kt
│ │ │ │ ├── TranscriptionSegment.kt
│ │ │ │ └── SessionState.kt
│ │ │ └── repository/
│ │ │ ├── PreferencesRepository.kt
│ │ │ └── SessionRepository.kt
│ │ ├── domain/
│ │ │ ├── model/
│ │ │ │ ├── TranscriptionResult.kt
│ │ │ │ ├── TranslationResult.kt
│ │ │ │ ├── AudioHealth.kt
│ │ │ │ └── CaptureError.kt
│ │ │ └── usercases/
│ │ ├── presentation/
│ │ │ ├── onboarding/
│ │ │ ├── home/
│ │ │ ├── learning/
│ │ │ ├── diagnostics/
│ │ │ └── settings/
│ │ ├── service/
│ │ │ ├── LearningForegroundService.kt
│ │ │ ├── AudioCaptureService.kt
│ │ │ └── MediaControlService.kt
│ │ ├── overlay/
│ │ │ ├── FloatingButtonService.kt
│ │ │ └── LearnThisButtonView.kt
│ │ ├── capture/
│ │ │ ├── AudioCaptureEngine.kt
│ │ │ ├── AudioProcessor.kt
│ │ │ ├── AudioHealthMonitor.kt
│ │ │ └── Resampler.kt
│ │ ├── whisper/
│ │ │ ├── WhisperNative.kt # JNI bridge
│ │ │ ├── WhisperEngine.kt
│ │ │ └── ModelManager.kt
│ │ ├── transcription/
│ │ │ ├── TranscriptionEngine.kt
│ │ │ └── SentenceMerger.kt
│ │ ├── translation/
│ │ │ ├── TranslationEngine.kt
│ │ │ └── MLKitManager.kt
│ │ ├── vad/
│ │ │ └── VadEngine.kt
│ │ └── util/
│ │ ├── AudioUtils.kt
│ │ ├── WavWriter.kt
│ │ └── CoroutineExtensions.kt
│ │ └── MainActivity.kt
│ └── res/
├── native/
│ └── whisper.cpp/ # git submodule
│ ├── CMakeLists.txt
│ └── ...
├── models/
│ ├── download-whisper-tiny.sh
│ └── download-whisper-base.sh
├── scripts/
│ ├── check-device.bat
│ ├── install-debug.bat
│ ├── capture-logs.bat
│ ├── pull-debug-audio.bat
│ ├── run-benchmark.bat
│ └── setup-whisper.bat
├── benchmarks/
│ └── benchmark-config.json
├── docs/
│ ├── ARCHITECTURE.md
│ ├── DEVELOPMENT_SETUP.md
│ ├── AUDIO_CAPTURE_DIAGNOSTICS.md
│ ├── MODEL_MANAGEMENT.md
│ ├── ARM_OPTIMIZATION.md
│ ├── BENCHMARKS.md
│ ├── KNOWN_LIMITATIONS.md
│ ├── IMPLEMENTATION_PROGRESS.md
│ └── PLAN_PHASE_01.md
├── test-assets/
├── temporary/
├── .gitignore
├── .gitmodules
├── settings.gradle.kts
├── build.gradle.kts
├── gradle.properties
├── local.properties.example
├── gradlew
├── gradlew.bat
└── README.md
```

## Phased Implementation

### Phase 01: Foundation (COMPLETE SCAFFOLD)
**Files:** Project structure, Gradle config, CMake, Application class, empty MainActivity, .gitignore
**Branch:** `feature/phase-01-foundation`
**Acceptance:** `gradlew.bat assembleDebug` succeeds, APK installs, app launches to empty screen

### Phase 02: Onboarding
**Files:** Onboarding screen, mother-tongue selection (Tamil, Hindi, English, Telugu, Malayalam, Kannada, Bengali, Marathi), DataStore persistence, home screen
**Branch:** `feature/phase-02-onboarding`
**Acceptance:** First launch shows language picker, selection persists across restarts, home screen shows selected language

### Phase 03: Model Management
**Files:** ModelManager, download logic with resume/checksum, model status tracking, storage in app-private dir
**Branch:** `feature/phase-03-model-management`
**Acceptance:** Tiny Q5 download succeeds, checksum validated, download resumes after interruption, model listed in settings

### Phase 04: Permissions
**Files:** Permission request flow with explanations, permission state tracking, overlay permission helper
**Branch:** `feature/phase-04-permissions`
**Acceptance:** Permissions requested with rationale, overlay denied fallback works, state tracked

### Phase 05: Media Control
**Files:** MediaSessionManager integration, MediaController wrapper, play/pause/seek detection
**Branch:** `feature/phase-05-media-control`
**Acceptance:** Detects YouTube/Instagram sessions, reports supported actions, seeks when available

### Phase 06: Playback Capture
**Files:** MediaProjection setup, AudioPlaybackCaptureConfiguration, AudioRecord wrapper, ring buffer
**Branch:** `feature/phase-06-playback-capture`
**Acceptance:** Captures playback audio from local media player, saves diagnostic WAV

### Phase 07: Audio Diagnostics
**Files:** AudioHealthMonitor, RMS/peak/zero analysis, debug screen, WAV export, error classification
**Branch:** `feature/phase-07-audio-diagnostics`
**Acceptance:** Debug screen shows real-time metrics, diagnostic WAV contains valid speech PCM

### Phase 08: Whisper JNI
**Files:** whisper.cpp integration, CMake build, JNI bridge, model loading/release, transcription call
**Branch:** `feature/phase-08-whisper-jni`
**Acceptance:** Model loads via JNI, known-good WAV transcribes correctly

### Phase 09: Progressive Processing
**Files:** Producer-consumer pipeline, chunk management, sentence merger, progressive UI updates
**Branch:** `feature/phase-09-progressive-processing`
**Acceptance:** First sentence appears within target latency, timestamps correct

### Phase 10: Translation
**Files:** ML Kit integration, language pair validation, translation worker, error handling
**Branch:** `feature/phase-10-translation`
**Acceptance:** Sentences translate to mother tongue, unsupported pairs show clear message

### Phase 11: Learning Results UI
**Files:** Learning screen, sentence cards, replay button, copy button, processing states
**Branch:** `feature/phase-11-learning-results`
**Acceptance:** Progressive results shown, replay works, UI responsive during processing

### Phase 12: ARM Optimization
**Files:** ARM Neon flags, thread tuning, KleidiAI evaluation, buffer reuse, release build
**Branch:** `feature/phase-12-arm-optimization`
**Acceptance:** Benchmark compares thread counts, optimized build verified on device

### Phase 13: Reliability
**Files:** Error classification, cleanup handlers, foreground service lifecycle, crash recovery
**Branch:** `feature/phase-13-reliability`
**Acceptance:** All error states distinguished, cleanup verified after stop

### Phase 14: Benchmarking
**Files:** Benchmark mode, JSON/CSV export, comparison baseline vs optimized
**Branch:** `feature/phase-14-benchmarking`
**Acceptance:** Benchmarks run, results exportable, targets met or bottlenecks documented
