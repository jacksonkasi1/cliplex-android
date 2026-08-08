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
| Measurement date | 2026-08-07 |

## Preserved development observations

| Input | Configuration | Warm native inference | Quality |
|---|---|---:|---|
| Private captured English, 9.4 s | default context, timestamps | about 2,380 ms | exact expected transcript |
| Same private capture, 9.4 s | `audio_ctx=512`, timestamps | about 819 ms | exact transcript and timed segments |
| Same private capture, 9.4 s | `audio_ctx=512`, no timestamps | about 791 ms | exact transcript |
| Installed-app capture, 7.8 s | production optimized path | 1,675 ms native / 1,684 ms Finish-to-visible | correct source text |

The 2,380 ms and 819 ms observations imply roughly 2.9x speedup and 66%
latency reduction on this workload. They were used to select the production
configuration, but raw five-run series were not archived. They must therefore
be treated as preliminary representative evidence, not a reported median.

Thread-count exploration on the known-good English diagnostic found six
threads fastest among one through six. Default-context means were approximately
7,094 / 3,649 / 3,395 / 2,825 / 2,508 / 2,293 ms. Context 384 degraded output;
context 256 caused repetition and fallbacks. The production floor is 512.

## Next controlled run

Use the procedure in [`../BENCHMARK.md`](../BENCHMARK.md), attach a cleared test
WAV and its hash, and replace this section with the five baseline and five
optimized raw timings plus calculated medians. Preserve failures instead of
silently excluding them.
