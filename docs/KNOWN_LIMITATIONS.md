# Known limitations

- Physical end-to-end playback capture and ASR are not yet verified in this working session because the OPPO disconnected during ADB installation.
- YouTube and Instagram capture/media-control support is unverified and may vary by app version and source policy.
- Processing begins after Finish Capture; overlapping streaming inference and sentence deduplication are not yet implemented.
- The current energy diagnostics are not Silero/whisper VAD; adaptive no-VAD/Base retry is pending.
- Audio is captured at requested 16 kHz mono; explicit fallback capture at a device-native rate and resampling is not yet connected to `AudioRecord`.
- Active media-session seek/restart, automatic completion by media duration, and benchmark UI/export are pending.
- The overlay flavor requires Android overlay permission; notification controls remain available during Learning Mode.
- ML Kit does not support Malayalam in the selected dependency, so that target reports translation failure while preserving the transcript.
