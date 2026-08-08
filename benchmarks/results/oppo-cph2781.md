# OPPO CPH2781 result

## Environment

| Field | Value |
|---|---|
| Device | OPPO CPH2781 (`OP5E1BL1`) |
| SoC | MediaTek MT6835 |
| OS | Android 16 / API 36 |
| ABI | `arm64-v8a` |
| CPU backend | NEON + Arm FMA + OpenMP + ggml repack |
| Whisper model | `ggml-tiny.en-q5_1.bin` |
| Threads | 6 |
| Measurement date | 2026-08-08 |
| Runner commit | `7af8ee98d997a37953aabbd4b8c5daf4a654e184` |
| Input SHA-256 | `f2871e112ba83f00d1b5b21d4147decbf40990d9b43618309c42cf8577caa3bd` |

## Controlled result

| Configuration | Measured inference times (ms) | Median |
|---|---|---:|
| Baseline (`audio_ctx=0`) | 2344.113, 2389.644, 2422.145, 2393.764, 2855.499 | 2393.764 ms |
| Optimized (`audio_ctx=512`) | 691.849, 689.897, 692.659, 693.885, 703.163 | 692.659 ms |

Measured speedup: **3.456x**. Measured latency reduction: **71.06%**. All ten
measured rows used the already-warm model and recorded thermal status 0. Every
run returned the same normalized transcript content; optimized output differed
only by adding final punctuation.

The complete phone-generated evidence, including warm-ups, UTC timestamps,
device/build identifiers, hashes, thermal state, native/Kotlin timings, and
transcripts, is committed as
[`oppo-cph2781-2026-08-08-raw.csv`](oppo-cph2781-2026-08-08-raw.csv).

## Next controlled run

Use the procedure in [`../BENCHMARK.md`](../BENCHMARK.md). Preserve failures
instead of silently excluding them and add a new dated CSV for every rerun.
