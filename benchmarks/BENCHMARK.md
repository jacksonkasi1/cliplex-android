# ClipLex Whisper benchmark

This document makes ClipLex's Arm64 Whisper optimization evidence auditable and
repeatable. It separates measurements already observed during development from
the protocol that future benchmark reports must follow.

## Test device

- Manufacturer/model identifier: OPPO CPH2781 (`OP5E1BL1`)
- SoC identifier: MediaTek MT6835
- ABI under test: `arm64-v8a`
- Android: 16 / API 36
- Native backend: CPU, NEON + Arm FMA + OpenMP + ggml repack
- Device check date: 2026-08-08

The app is built only for `arm64-v8a`. `GGML_NATIVE` and KleidiAI are disabled
so the binary remains portable across supported Arm64 Android devices.

## Software and model

- ClipLex source revision for the original measurements: pre-`1.0.0-alpha01`
  development build
- `whisper.cpp` revision: `a8d002cfd879315632a579e73f0148d06959de36`
- Model: `ggml-tiny.en-q5_1.bin`
- Model SHA-256:
  `c77c5766f1cef09b6b7d47f21b546cbddd4157886b3b5d6d4f709e91e66c7c2b`
- Input PCM: 16 kHz, mono, signed 16-bit
- Threads: 6
- Sampling: greedy
- Context reuse: enabled after model load
- Translation: disabled
- Timed sentence segments: enabled in the production configuration

## Existing optimization evidence

The same private 9.4-second captured English clip produced the exact expected
transcript in both configurations:

| Configuration | `audio_ctx` | Timed segments | Warm native inference |
|---|---:|---:|---:|
| Baseline | model default | yes | about 2,380 ms |
| Optimized | 512 | yes | about 819 ms |

This is approximately **2.9x faster** and a **66% latency reduction** for that
clip. Removing timestamps reduced the optimized run to about 791 ms, only about
28 ms faster, so ClipLex keeps timestamps for synchronized learning sessions.
Contexts 384 and 256 were rejected because they truncated, degraded, or
repeated output.

These are representative warm measurements retained from development, not a
claim that five raw runs were archived or that 819 ms is a cross-device
guarantee. The original private capture is not committed because its source
media is not cleared for redistribution.

## Public fixed input

After initializing submodules, the repository contains the upstream diagnostic
audio at:

```text
native/whisper.cpp/samples/jfk.wav
```

ClipLex debug builds package this known-good sample and expose **Run known-good
ASR test** in diagnostics. It is suitable for validating the model, native
runtime, transcript, and timing fields. The historical warm optimized result
for this diagnostic path was about 696 ms on the test device; device state and
the exact input window can change the number.

The headline baseline-versus-optimized comparison must be repeated with the
same newly recorded, redistributable 8-10 second English WAV before it is
presented as a statistical median. Place that clip in `benchmarks/samples/` and
record its provenance and SHA-256 in the result report.

## Repeatable procedure

1. Record or select a redistributable 8-10 second English WAV. Convert it to
   16 kHz mono PCM16, then record its duration and SHA-256.
2. Build and install `safeDebug` using the commands in the root README.
3. Download **Whisper Tiny English (Q5_1)** in ClipLex and select English.
4. Close unnecessary background apps and let the device return to normal
   temperature. Record Android thermal status before each configuration.
5. Load the fixed input and run two unrecorded warm-up transcriptions.
6. Run five baseline transcriptions with `shortEnglishFastMode=false`
   (`audio_ctx=0`, the model default). Save each native `whisperInferenceMs`
   value and transcript.
7. Run two optimized warm-ups, then five optimized transcriptions with
   `shortEnglishFastMode=true` (`audio_ctx=512`). Save the same fields.
8. Confirm that baseline and optimized transcripts match the expected text and
   retain acceptable timed segments.
9. Sort each five-value series and report the middle value as the median. Also
   retain all raw values, the source commit, APK variant, model hash, and device
   state in `benchmarks/results/`.

Do not mix cold model-load time with warm inference. ClipLex diagnostics expose
model load, native inference, JNI/JSON, and Kotlin total timings separately.
For product UX evidence, additionally report **Finish-to-visible source text**
as a separate end-to-end metric.

## Acceptance criteria

- All five optimized runs complete successfully.
- The optimized median is materially lower than the baseline median.
- The optimized transcript preserves the expected content.
- No fallback caused by context truncation/repetition occurs.
- Any thermal throttling or abnormal background load is disclosed.

See [the device result](results/oppo-cph2781.md) and the broader historical
[benchmark notes](../docs/BENCHMARKS.md).
