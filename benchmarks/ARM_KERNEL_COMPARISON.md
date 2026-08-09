# Arm kernel comparison

## Build and device

| Item | Value |
|---|---|
| Baseline commit | `6a2df0eceecc41b0a11058eb786eb4dcd8b91157` |
| Integration commit | `d3fc125` |
| Benchmark commit | `81d812b` |
| Device | OPPO CPH2781 |
| SoC | MediaTek MT6835 |
| Android | 16 (API 36) |
| ABI | `arm64-v8a` |
| Runtime CPU features | Arm64=true, NEON=true, DotProd=true, I8MM=false |
| Model | `ggml-tiny.en-q5_1.bin` (Q5_1) |
| Model SHA-256 | `c77c5766f1cef09b6b7d47f21b546cbddd4157886b3b5d6d4f709e91e66c7c2b` |
| Fixture | `jfk-first-9.4s-16khz-mono.wav`, 9.4 s, 16 kHz mono |
| Fixture PCM SHA-256 | `f2871e112ba83f00d1b5b21d4147decbf40990d9b43618309c42cf8577caa3bd` |

No ADB serial, account data, or other personal identifier is stored in the report or CSV.

## APK evidence

| Build | Filename | Bytes | SHA-256 |
|---|---|---:|---|
| Unmodified baseline safeDebug | `app-safe-debug.apk` | 74,157,546 | `5931ab7f14c0223891afc555b06b2c79ca1253921fd998c2eab47181e8bfde13` |
| Branch generic safeDebug | `app-safe-debug.apk` | 74,166,842 | `e93cbe123129e3f34768ec902e027bbd6c156b8cdf74fb165ab519c173cf7c91` |
| Branch KleidiAI-compiled safeDebug | `cliplex-safe-debug-kleidiai.apk` | 74,173,858 | `93631b88ce23345855055b88f789a94f34ab8e6073cd9aa208b33f349553438f` |

All three are debug-signed installable APKs. They are not release-signed, and no APK is committed.

## Method

- Same physical device, verified model, and fixed fixture for every run.
- Full Whisper audio context (`audio_ctx=0`) so this is not a short-context speedup comparison.
- Branch matrices: two warm-ups followed by ten measured runs for each of 2, 4, 6, and 8 threads.
- Median uses the midpoint of the two central sorted samples. p95 uses nearest rank.
- Native inference, native total, Kotlin end-to-end time, transcript hash, failures, thermal status,
  backend, and CPU features were recorded for every run.
- The unchanged main benchmark only supported five measured runs per configuration. Its available
  six-thread full-context baseline is reported separately and is not presented as a 10-run matrix.

## Unmodified main baseline

At six threads, full audio context, generic ggml: median 2,330.257 ms; p95 2,438.960 ms; minimum
2,320.037 ms; maximum 2,438.960 ms; failures 0; thermal status 0.

Main's separate 512-frame short-context path measured 735.575 ms median and 800.209 ms p95. That
result changes audio context and is not counted as a kernel speedup.

## Branch thread matrix

| Configuration | Threads | Backend / path | Median inference (ms) | p95 (ms) | Min (ms) | Max (ms) | Failures |
|---|---:|---|---:|---:|---:|---:|---:|
| Generic | 2 | CPU / generic-ggml | 3,718.650 | 3,813.107 | 3,695.218 | 3,813.107 | 0 |
| Generic | 4 | CPU / generic-ggml | 2,885.302 | 2,944.928 | 2,873.080 | 2,944.928 | 0 |
| Generic | 6 | CPU / generic-ggml | 2,339.591 | 2,352.584 | 2,329.691 | 2,352.584 | 0 |
| Generic | 8 | CPU / generic-ggml | 2,039.019 | 2,159.958 | 2,026.234 | 2,159.958 | 0 |
| KleidiAI compiled | 2 | CPU / generic-ggml | 3,698.348 | 3,766.179 | 3,692.495 | 3,766.179 | 0 |
| KleidiAI compiled | 4 | CPU / generic-ggml | 2,883.754 | 3,031.722 | 2,874.691 | 3,031.722 | 0 |
| KleidiAI compiled | 6 | CPU / generic-ggml | 2,357.556 | 2,367.711 | 2,344.626 | 2,367.711 | 0 |
| KleidiAI compiled | 8 | CPU / generic-ggml | 2,118.392 | 2,563.975 | 2,065.461 | 2,563.975 | 0 |

At the existing six-thread default, compiling KleidiAI changed median by +0.77% and p95 by +0.64%.
Because runtime selected the same generic path, this is run-to-run variance rather than a kernel
speedup. Eight threads reduced median versus six in both builds, but the compiled matrix's p95 was
8.29% worse than its six-thread p95. Six remains the production default.

## Runtime backend evidence

The opt-in APK reported:

```text
ClipLex CPU ABI: arm64-v8a
ClipLex KleidiAI compiled: true
ClipLex DotProd supported: true
ClipLex I8MM supported: false
ClipLex selected backend: cpu
ClipLex selected kernel path: generic-ggml
ClipLex generic fallback: true
ClipLex fallback reason: Q5_1 model operators are not supported by this KleidiAI integration
```

KleidiAI was therefore not selected, and no KleidiAI micro-kernel speedup is claimed.

## Correctness

Every measured run produced the same normalized transcript and transcript hash
`e4321c13fadcb49fb78e4d1e8124e900d646238e2239121da98d0ddc465451e6`:

> And so my fellow Americans ask not what your country can do for you ask what you can do

Thermal status remained 0, both 48-inference matrices completed without failure, the APK installed
with `adb install -r -t`, and the app launched without an immediate AndroidRuntime crash. Capture and
media-projection behavior remains covered by existing automated tests; no OS kernel, governor,
bootloader, ROM, model verification, language, or UI-flow behavior was changed.

The repository-wide connected safeDebug suite then ran three tests on the same phone with zero
failures, including injected-audio speech diagnostics. Its install lifecycle cleared debug app data;
the verified Q5_1 model was restored afterward and its 32,166,155-byte size was confirmed in app
storage.

## Raw data

Machine-readable measured timings are in
[`results/oppo-cph2781-2026-08-09-arm-kernel.csv`](results/oppo-cph2781-2026-08-09-arm-kernel.csv).
The test writes the full warm-up and measured CSV to app-specific external benchmark storage for ADB
collection.

## Limitations

- The pinned model source did not contain a verifiable tiny English Q4_0 asset, so the requested Q4_0
  matrix was not fabricated.
- whisper.cpp does not expose per-operation kernel names through its public API.
- A custom Q5_1 SDOT/assembly micro-kernel is intentionally deferred until profiling proves it is
  required.
