# Audio capture diagnostics

Every finished capture records sample count, duration, RMS, peak, dBFS, zero percentage, clipping percentage, and estimated non-silent duration before Whisper runs.

`AudioRecord` tries device-native playback formats in this order: 48 kHz stereo/mono, 44.1 kHz stereo/mono, then 16 kHz mono. Captured PCM16 is downmixed and linearly resampled to 16 kHz mono before diagnostics and Whisper, avoiding devices that reject or silently mishandle direct 16 kHz playback capture.

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

The overlay permission affects only the optional floating control; it does not grant or improve audio capture. `RECORD_AUDIO` plus the Android MediaProjection consent dialog are required by the official playback-capture API. On Android 14+, consent cannot be reused for a new projection session.

Debug builds can save the latest 10 seconds as a valid WAV in app-specific external diagnostics storage; files older than 24 hours are cleaned on the next export and can be pulled with `scripts\pull-debug-audio.bat`. The pinned `whisper.cpp` submodule includes the permitted `samples\jfk.wav`; with English Only selected, debug builds expose **Run known-good ASR test** to exercise WAV parsing, PCM preprocessing, JNI, fixed-English decoding, and Whisper independently of playback capture.

Startup logs record the MediaProjection result boundary, projection creation, selected device format, first PCM read, and the first non-zero buffer. Session completion logs include sample count, duration, RMS, peak, zero percentage, and the classified error. Native inference logs record the input size, requested/detected language, and segment count without logging recognized speech. These stages distinguish consent/service failures from source-policy silence and from ASR failures.
