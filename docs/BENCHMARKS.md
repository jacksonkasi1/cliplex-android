# Benchmarks

The current ClipLex performance claim is based only on the controlled physical
device run documented in [`benchmarks/BENCHMARK.md`](../benchmarks/BENCHMARK.md).

## Verified result

- Device: OPPO CPH2781 / MediaTek MT6835 / Android 16
- ABI: `arm64-v8a`
- Input: committed JFK WAV, deterministically cropped to 9.4 seconds
- Input SHA-256:
  `f2871e112ba83f00d1b5b21d4147decbf40990d9b43618309c42cf8577caa3bd`
- Model: Whisper Tiny English Q5_1, six threads
- Procedure: two warm-ups followed by five measured runs per configuration
- Baseline median (`audio_ctx=0`): 2,393.764 ms
- Optimized median (`audio_ctx=512`): 692.659 ms
- Speedup: 3.456x
- Latency reduction: 71.06%
- Quality: normalized transcript content matched across every run
- Thermal status: 0 for every recorded row

The phone-generated [raw CSV](../benchmarks/results/oppo-cph2781-2026-08-08-raw.csv)
contains all 14 rows, including warm-ups, timestamps, hashes, native/Kotlin
timings, thermal state, and transcripts. No earlier unarchived timing is used as
the published performance claim.

Longer clips, multilingual quality, sustained thermal behavior, memory, and
battery usage require separate controlled datasets and must not be inferred
from this result.
