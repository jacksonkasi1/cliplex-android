# Audio capture diagnostics

Every finished capture records sample count, duration, RMS, peak, dBFS, zero percentage, clipping percentage, and estimated non-silent duration before Whisper runs.

- `SOURCE_CAPTURE_BLOCKED`: more than 99.5% exact zero samples after a usable duration. The source likely prohibited playback capture.
- `CAPTURED_SILENCE`: non-zero PCM arrived but remained below the conservative audible threshold.
- `AUDIO_TOO_SHORT`: less than 500 ms was collected.
- `AUDIO_FORMAT_INVALID`: sample rate/configuration was invalid.
- `NO_SPEECH_DETECTED`: valid audible capture produced no Whisper segments.
- `ASR_EMPTY_RESULT`: native inference failed or returned an unusable result.
- `MODEL_NOT_LOADED`: the verified model did not load.
- `MEDIA_PROJECTION_REVOKED`: Android stopped the consent token.
- `TRANSLATION_FAILED`: transcript remains visible but translation could not complete.

Use a local player and a permitted speech file before testing YouTube or Instagram. If local playback produces healthy non-zero diagnostics but a specific app produces zeros, the source app blocks capture; changing Whisper cannot fix it.

Debug builds can save the latest 10 seconds as a valid WAV in app-specific external diagnostics storage; files older than 24 hours are cleaned on the next export and can be pulled with `scripts\pull-debug-audio.bat`. The pinned `whisper.cpp` submodule includes the permitted `samples\jfk.wav`; debug builds expose **Run known-good ASR test** to exercise WAV parsing, PCM preprocessing, JNI, and Whisper independently of playback capture. Do not claim the source-app matrix as verified until it has been exercised on the physical device.
