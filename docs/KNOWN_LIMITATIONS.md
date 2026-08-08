# Known limitations

- Playback capture is physically verified on an OPPO CPH2781 running Android 16, including non-zero YouTube PCM. Instagram and other app/content combinations remain unverified and can vary by source capture policy.
- The bundled spoken JFK sample and captured YouTube speech are verified through on-device Whisper on the OPPO. Recognition quality still varies with the Tiny model, speech clarity, background music, and language.
- Processing begins after Finish Capture; overlapping streaming inference and sentence deduplication are not yet implemented.
- The current energy diagnostics are not Silero/whisper VAD; adaptive VAD and automatic larger-model retry for genuinely difficult clips are not implemented. Users can explicitly select the validated Base multilingual "Recommended" tier.
- Android 14+ requires the system MediaProjection consent dialog for every new Learning Mode session; Android does not provide a permanent grant for playback capture.
- Word highlighting estimates each word's position within a timed Whisper segment. Native token-level word timestamps are not yet exposed, so highlighting is synchronized but approximate.
- ML Kit supplies a translated word meaning, not dictionary-grade IPA, part of speech, or definitions. Pronunciation uses Android TTS and the captured sentence is used as the example without inventing lexical data.
- Protected/DRM video surfaces can appear blank, and source apps or individual content can prohibit playback-audio capture. Learn This cannot bypass either Android policy.
- Capture is limited to 60 seconds. Rotation or major aspect changes during a clip use the fixed recording canvas selected at Start.
- The floating control is optional. Android can provide temporary overlay capability during an active projection, but explicit denial or OEM policy can still require enabling it under Settings; in-app and notification controls remain available.
- ML Kit does not support Malayalam in the selected dependency, so that target reports translation failure while preserving the transcript.
