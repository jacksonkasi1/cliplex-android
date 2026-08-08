# ClipLex Whisper benchmark

This document makes ClipLex's Arm64 Whisper optimization evidence auditable and
repeatable. The headline result below comes from committed raw device output,
not an estimate or a reconstructed benchmark.

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

- ClipLex benchmark runner revision:
  `7af8ee98d997a37953aabbd4b8c5daf4a654e184`
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

## Measured result

On 2026-08-08 the physical device completed two warm-ups and five measured runs
per configuration using the first 9.4 seconds of the committed JFK WAV. The
input WAV after deterministic decode/resample/crop has SHA-256
`f2871e112ba83f00d1b5b21d4147decbf40990d9b43618309c42cf8577caa3bd`.

| Configuration | Five measured native inference times (ms) | Median |
|---|---|---:|
| Baseline, `audio_ctx=0` | 2344.113, 2389.644, 2422.145, 2393.764, 2855.499 | **2393.764 ms** |
| Optimized, `audio_ctx=512` | 691.849, 689.897, 692.659, 693.885, 703.163 | **692.659 ms** |

The measured median is **3.456x faster**, a **71.06% latency reduction**. All
measured rows report a warm model and Android thermal status 0. The normalized
transcript content matched across all runs; the optimized decoder added only a
final period. Sentence timestamps remained enabled.

Every warm-up and measured observation is preserved in the
[raw CSV](results/oppo-cph2781-2026-08-08-raw.csv). Results are specific to this
device, model, input, build, and device state; they are not a cross-device
latency guarantee.

## Public fixed input

The exact post-conversion input is committed directly at:

```text
benchmarks/samples/jfk-first-9.4s-16khz-mono.wav
```

It is a 300,844-byte PCM16 WAV containing exactly 150,400 mono samples at
16 kHz. Its SHA-256 is the input hash recorded above. The original phone run
created the same bytes by deterministically decoding, resampling, and cropping
the upstream `native/whisper.cpp/samples/jfk.wav`; the committed fixture is
byte-for-byte identical to that benchmark input. The runner checks the
input/model hashes and transcript content, and flushes every observation
directly to CSV on the device.

## Repeatable procedure

1. Initialize submodules and build `safeDebug` plus `safeDebugAndroidTest`.
2. Install both APKs on an Arm64 phone.
3. Download/provision **Whisper Tiny English (Q5_1)** in the debug app and
   verify the model SHA-256 listed above.
4. Close unnecessary background apps and let the device return to normal
   temperature. Record Android thermal status before each configuration.
5. Run `WhisperArm64BenchmarkTest` with the source revision as the
   `cliplexBenchmarkCommit` instrumentation argument.
6. The test performs two baseline warm-ups, five baseline measurements, two
   optimized warm-ups, and five optimized measurements in that order.
7. Pull `files/benchmarks/whisper-arm64-raw.csv` with `adb shell run-as`.
8. Confirm the test passed, retain all rows, and calculate each median from the
   five rows whose phase is `measured`.

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
