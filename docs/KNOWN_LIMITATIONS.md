# Known limitations

- Physical end-to-end playback capture and ASR are not yet verified in this working session because the OPPO disconnected during ADB installation.
- YouTube and Instagram capture/media-control support is unverified and may vary by app version and source policy.
- Processing begins after Finish Capture; overlapping streaming inference and sentence deduplication are not yet implemented.
- The current energy diagnostics are not Silero/whisper VAD; adaptive no-VAD/Base retry is pending.
- Android 14+ requires the system MediaProjection consent dialog for every new Learning Mode session; Android does not provide a permanent grant for playback capture.
- Active media-session seek/restart, automatic completion by media duration, and benchmark UI/export are pending.
- The floating control is optional and requires Android/ColorOS overlay permission; in-app and notification controls remain available during Learning Mode.
- ML Kit does not support Malayalam in the selected dependency, so that target reports translation failure while preserving the transcript.
