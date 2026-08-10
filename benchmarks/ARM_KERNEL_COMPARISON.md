# Arm capability and experimental integration comparison

## Scope and claim

This evidence compares the production-default generic build with an opt-in KleidiAI integration
experiment. It does **not** claim that the current Q5_1 production model uses a KleidiAI
microkernel. Both device runs selected the observable `generic-ggml` compute path.

## Reproducibility identity

| Item | Value |
|---|---|
| Source commit used to build both APKs | `04135227079df550d0652c4d12786a66ab491bef` |
| Baseline commit | `6a2df0eceecc41b0a11058eb786eb4dcd8b91157` |
| whisper.cpp commit / tag | `a8d002cfd879315632a579e73f0148d06959de36` / v1.7.6 |
| Pinned KleidiAI version | v1.9.0 |
| Device | OPPO CPH2781 |
| SoC / OS / ABI | MediaTek MT6835 / Android 16 (API 36) / `arm64-v8a` |
| Runtime CPU features | Arm64=true, NEON=true, DotProd=true, I8MM=false |
| Model | `ggml-tiny.en-q5_1.bin` (metadata ftype `Q5_1`) |
| Model SHA-256 | `c77c5766f1cef09b6b7d47f21b546cbddd4157886b3b5d6d4f709e91e66c7c2b` |
| Fixture | `jfk-first-9.4s-16khz-mono.wav`, 9.4 s, 16 kHz mono |
| Fixture PCM SHA-256 | `f2871e112ba83f00d1b5b21d4147decbf40990d9b43618309c42cf8577caa3bd` |

No ADB serial, account data, or personal identifier is stored in the evidence.

## APK evidence

| Configuration | Local filename | Bytes | SHA-256 |
|---|---|---:|---|
| Generic safeDebug (production default) | `cliplex-generic-safe-debug.apk` | 74,172,451 | `45049ce7986f206a40dcc544c865a245b5708629c317c9b116c347672833be24` |
| KleidiAI experimental safeDebug | `cliplex-kleidiai-experimental-safe-debug.apk` | 74,804,464 | `ab6a78d2b17208b3755536d9a4ca88441f938e33bf22e782ff1390d509440b0d` |

Both APKs are debug-signed, installed with ADB, cold-launched successfully, and contain the packaged
KleidiAI attribution and license assets. No APK is committed.

## Method

- Same physical device, verified model, and fixed fixture for both configurations.
- Full Whisper audio context (`audio_ctx=0`).
- Two warm-ups followed by ten measured runs at 2, 4, 6, and 8 threads.
- Median is the midpoint of the central samples; p95 is nearest rank.
- Every row was flushed during execution and copied from internal to external app storage in
  `finally`, before final assertions.
- Every measured transcript was checked against the fixed canonical transcript and for consistency
  across all thread counts. The generator repeats the gate across both configuration exports.
- Both repository-wide connected safeDebug suites ran three tests with zero failures.

## Derived measured comparison

| Configuration | Threads | Median (ms) | p95 (ms) | Min (ms) | Max (ms) | Failures | Thermal max |
|---|---:|---:|---:|---:|---:|---:|---:|
| Generic | 2 | 3,695.338 | 3,836.510 | 3,692.438 | 3,836.510 | 0 | 0 |
| Generic | 4 | 2,885.838 | 3,018.553 | 2,865.173 | 3,018.553 | 0 | 0 |
| Generic | 6 | 2,332.137 | 3,224.802 | 2,318.495 | 3,224.802 | 0 | 0 |
| Generic | 8 | 2,145.029 | 2,458.276 | 2,043.860 | 2,458.276 | 0 | 0 |
| KleidiAI experimental | 2 | 3,716.336 | 3,942.426 | 3,690.577 | 3,942.426 | 0 | 0 |
| KleidiAI experimental | 4 | 2,888.372 | 3,120.597 | 2,874.307 | 3,120.597 | 0 | 0 |
| KleidiAI experimental | 6 | 2,361.392 | 2,409.848 | 2,332.298 | 2,409.848 | 0 | 0 |
| KleidiAI experimental | 8 | 2,125.053 | 2,304.703 | 2,076.754 | 2,304.703 | 0 | 0 |

Timing differences are run-to-run variance on the same observable generic compute path. They are not
presented as a KleidiAI speedup. Six threads remains the production default because this is one
device/input and tail latency varied materially.

## Truthful runtime evidence

The experimental Q5_1 run recorded the following on every row:

```text
kleidiaiIntegrationEnabled=true
kleidiaiSourcesIncluded=true
kleidiaiKernelSelectionObserved=false
modelEligibleForKleidiAi=false
selectedComputePath=generic-ggml
fallbackReason=Q5_1 is not supported by the pinned KleidiAI integration
```

`kleidiaiSourcesIncluded` is backed by a CMake check that the pinned ggml integration wrapper and
registry sources are present in the `ggml-cpu` target. It does not mean that an ISA-specific compute
microkernel was compiled or selected. CPU HWCAP values are diagnostics only and never prove kernel
selection. Model quantization comes from loaded-model metadata via `whisper_model_ftype()`, not the
filename.

## Correctness

All 80 measured runs matched this canonical normalized transcript:

```text
and so my fellow americans ask not what your country can do for you ask what you can do
```

Normalized transcript SHA-256:
`9fa91294a98ddc0b0d60009df41a2a1d5dbc9ccd83b8b9e9859fa278c9e318c3`.

## Evidence chain

Untouched device outputs:

- [`raw/oppo-cph2781-20260809-175842Z-0413522-generic-q5_1-45049ce7.csv`](raw/oppo-cph2781-20260809-175842Z-0413522-generic-q5_1-45049ce7.csv)
  — SHA-256 `10aaeab9091c7dc056dbbbb002487c373417164e81d43a169fad8b2c9d506698`
- [`raw/oppo-cph2781-20260809-180301Z-0413522-kleidiai-experimental-q5_1-ab6a78d2.csv`](raw/oppo-cph2781-20260809-180301Z-0413522-kleidiai-experimental-q5_1-ab6a78d2.csv)
  — SHA-256 `364695fe6d28158e3561e71539744e5d614da06daafd97eb37c2c3244886b7e8`

Deterministic derived output:

- [`derived/oppo-cph2781-20260809-0413522-comparison.csv`](derived/oppo-cph2781-20260809-0413522-comparison.csv)
  — SHA-256 `d28db35862eeceefa7a12eb4bd2ed30b11f21d1dee04e80c1b7f4ffc41c5f25c`

Regenerate it without modifying the raw exports:

```text
python scripts/generate-arm-kernel-comparison.py \
  --generic benchmarks/raw/oppo-cph2781-20260809-175842Z-0413522-generic-q5_1-45049ce7.csv \
  --experimental benchmarks/raw/oppo-cph2781-20260809-180301Z-0413522-kleidiai-experimental-q5_1-ab6a78d2.csv \
  --output benchmarks/derived/oppo-cph2781-20260809-0413522-comparison.csv
```

This provides an auditable chain from source commit → APK hash → device run → untouched raw CSV →
derived comparison.

## Limitations

- The verified production model is Q5_1 and is not eligible for the pinned integration.
- Pinned whisper.cpp does not expose per-operation KleidiAI kernel selection through its public API.
- No global `-march`, `-mcpu`, DotProd, I8MM, SME, or device-specific ISA flag was added by ClipLex.
- A real baseline-plus-specialized dispatch target and custom Q5_1 microkernel remain out of scope.
