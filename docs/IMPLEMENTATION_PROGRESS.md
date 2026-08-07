# Implementation progress

## 2026-08-07 — vertical workflow recovery

- Branch: `feature/phase-10-history`
- Status: local build/test complete; physical installation pending device reconnection
- Changes: restored Room with exported schema; corrected language persistence; added resumable/checksummed Tiny/Base model downloads; pinned `whisper.cpp` v1.7.6; replaced JNI stub with reusable ARM64 inference; rebuilt capture as an armed foreground service with overlay/notification controls; added audio-health classification, on-device translation model download, sentence replay/copy, and Room history UI.
- Automated tests: `testSafeDebugUnitTest` and `testOverlayDebugUnitTest` pass (PCM conversion, normalization, downmixing, resampling, WAV write/read, diagnostics, VAD).
- Builds: `assembleSafeDebug`, `assembleOverlayDebug`, and the minified `assembleOverlayRelease` pass. `lintOverlayDebug` passes with zero errors.
- ADB: OPPO CPH2781 / Android 16 / arm64-v8a was initially detected. The install attempt timed out and the device then disappeared from `adb devices`; no physical result is claimed.
- Known limitations: see `KNOWN_LIMITATIONS.md`.
- Next: reconnect phone, install/launch, inspect logcat, download Tiny, test permitted local playback, then record capture/ASR/translation measurements.

## 2026-08-07 — device-feedback recovery

- Added a persistent active-model choice with Selected/Downloaded states and a simple Use action for switching between installed Tiny and Base models.
- Removed overlay permission from the primary Learning Mode gate. Floating control and notification configuration now live under the Settings icon; in-app capture remains available without an overlay.
- Added explicit mediaProjection foreground-service typing, device-native AudioRecord format fallback, stereo downmixing, 16 kHz resampling, overlay visibility/error diagnostics, and an armed-only overlay lifecycle.
- Verification: 10/10 overlay-debug unit tests pass, Android lint passes with zero errors, and both the ARM64 overlay debug APK and minified unsigned overlay release APK build successfully.
- Physical verification remains pending because `adb devices -l` currently reports no connected device.
