# Implementation progress

## 2026-08-07 — video-first learning session

- Branch: `feature/phase-10-history`.
- Learning Mode now arms after consent and saves nothing until an explicit floating, in-app, or notification Start action.
- Capture Video defaults on. One clip records the projected display as H.264, captures permitted playback PCM, muxes AAC into the MP4, and retains a private 16 kHz mono WAV. Recorder or mux failure falls back to an audio lesson.
- Stop launches ClipLex directly, shows a specific preparing state, and opens the exact new Room-backed lesson. English transcription is persisted/displayed before translation.
- The lesson UI supports video/audio playback, play/pause/replay, ±10 seconds, scrubbing, Word by Word/Sentence/Tamil View, synchronized subtitles, word translation, Android TTS, saved words, Delete Video, and confirmed Delete Lesson.
- Room migrated from version 1 to 2 without destructive migration. Lesson media is app-private and excluded from backup/device transfer.
- English Only uses `ggml-tiny.en-q5_1.bin`, fixed `en`, six threads, and the validated `audio_ctx=512` short path with timed multi-segment output. Multiple Languages uses the verified multilingual Tiny model and automatic detection that continues into transcription.
- Model downloads validate pinned size/SHA-256, safely resume bounded partial files, reset canceled UI state, and retain already downloaded modes.
- Native JNI now preserves supplementary Unicode and returns structured model/inference diagnostics. ML Kit translator failures discard stale instances so retries are real.
- Explicit learning-language and speech-quality preferences now drive the active `ModelResolver` end to end. Known languages are forced, Any Language uses `auto` without detect-only mode, English-only mismatch is rejected, and a newly downloaded resolved model is loaded without restarting the app.
- Local verification: 46/46 safe-debug tests and 46/46 overlay-debug tests pass; both ARM64 debug APKs build. Minified release and lint are revalidated before release handoff.

## Physical evidence already completed

- Installed-app playback capture was proven on OPPO CPH2781 / Android 16/API 36 with active MediaProjection, media-projection foreground service, 48 kHz stereo remote-submix input, non-zero YouTube PCM, and visible application overlay.
- The known-good JFK WAV transcribed exactly on device. A 7.8-second real captured English clip produced correct source text in about 1.68 seconds after Finish; the user also observed a 12-second clip producing output in roughly 2–4 seconds.
- The final overlay APK was installed in place on the OPPO. Floating Start/Stop created private MP4/WAV lessons and opened the exact lesson automatically. Forced Hindi and automatic Hindi decoding both returned segments with `detectLanguage=false`; Base produced recognizable Devanagari where Tiny failed on the same synthetic sample.
