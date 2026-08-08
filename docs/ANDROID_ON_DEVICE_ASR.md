# Android on-device ASR

ClipLex routes captured playback PCM through Android's strictly on-device `SpeechRecognizer`
before considering the local Whisper engine. It never constructs the general
`createSpeechRecognizer` recognizer, so the primary route cannot silently become cloud speech.

## Runtime route

1. Capture remains 16 kHz, mono, PCM16.
2. `AndroidSpeechRecognizerEngine` checks the exact captured-audio request with
   `checkRecognitionSupport` on API 33+.
3. The engine resolves the selected language to an installed BCP-47 variant reported by the
   device. The request remains explicit; language detection is not enabled.
4. A `RecognizerAudioPipe` feeds PCM through `EXTRA_AUDIO_SOURCE`. Unsupported API levels,
   languages, configurations, Android errors, and empty results route through
   `SpeechRecognitionCoordinator` to `WhisperSpeechRecognizerEngine`.
5. Learner UI receives normalized segments regardless of engine. Stable segment translation can
   begin before final recognition. API 34 recognition parts are persisted for exact word timing;
   segment timing and proportional sentence timing remain the lower tiers.

Android language downloads are requested only through `triggerModelDownload`. Android 14+ callback
progress is displayed when supplied; older services remain indeterminate. Whisper models are
downloaded as additional speech support only when the Android route is unsupported, and are not
downloaded alongside an Android language model.

## Developer diagnostics

Debug builds show API level, selected locale, Android model status, audio-injection status, segment
and word timing availability, engine used, timing, fallback reason, and audio format. Transcript
text is visible in this explicit developer screen but is not part of the technical metrics model.

## OPPO CPH2781 / Android 16 evidence (2026-08-08)

- Exact captured-audio configuration query: passed.
- English installed locale resolved from the device: `en-GB`.
- Bundled known-good JFK PCM through `EXTRA_AUDIO_SOURCE`: passed using
  `ANDROID_ON_DEVICE`; repeated measured recognition processing time was approximately 0.9–1.2
  seconds. The service returned six timed recognition parts, exercising exact word timing.
- Hindi status: `DownloadRequired`.
- Tamil status: `AndroidUnsupported`; the coordinator therefore requires the local fallback.

Hindi/Tamil accuracy, Hindi model-download completion, the 2/5/12/30-second benchmark matrix, and
full MediaProjection video-learning verification require the corresponding known-good fixtures and
interactive capture/model-download runs on the target device. Do not infer those results from the
English diagnostic.
