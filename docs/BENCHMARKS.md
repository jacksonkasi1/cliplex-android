# Benchmarks

Physical target: OPPO CPH2781 / MT6835 / Android 16, eight CPU cores, thermal status 0. Backend: NEON + ARM FMA + OpenMP + REPACK, CPU only. These are native inference timings, not guaranteed Finish-to-visible UI latency.

| Input | Configuration | Warm inference | Result |
|---|---|---:|---|
| JFK, 5 s | Tiny English Q5_1, six threads, `audio_ctx=512` | about 696 ms | exact expected sentence |
| captured English, 9.4 s | Tiny English Q5_1, six threads, default context | about 2,380 ms | exact transcript |
| captured English, 9.4 s | Tiny English Q5_1, six threads, `audio_ctx=512`, timestamps | about 819 ms | exact transcript and timed segments |
| captured English, 9.4 s | Tiny English Q5_1, six threads, `audio_ctx=512`, no timestamps | about 791 ms | exact transcript |
| captured Hindi TTS, 22.9 s | Tiny Multilingual Q5_1, forced `hi`, six threads | 15,958 ms | 2 segments; poor quality (`[Song]`) |
| captured Hindi TTS, 22.2 s | Base Multilingual Q5_1, forced `hi`, six threads | 15,895 ms | 1 recognizable Devanagari segment with spelling errors |
| captured Hindi TTS, 10.0 s | Tiny Multilingual Q5_1, `auto`, six threads | 6,770 ms | detected `hi`, returned 2 romanized segments |

One-through-six-thread means for the 5-second JFK clip were approximately 7,094 / 3,649 / 3,395 / 2,825 / 2,508 / 2,293 ms with default context; six was best. A real 7.8-second installed-app capture completed warm inference in 1,675 ms and Finish-to-visible source text in 1,684 ms. Context 384 truncated/degraded output and 256 caused repetition and six fallbacks, so neither is used.

Longer 30/60-second clips, battery drain, peak RSS, and sustained thermal behavior still need a controlled benchmark pass.

The Hindi runs were captured end to end through MediaProjection on the physical OPPO. Native diagnostics confirmed `translate=false` and `detect_language=false` for both forced and automatic language requests. The small sample suggests Base materially improves Hindi script recognition without increasing inference time for this clip, but it is not broad enough to change the default; clean multi-speaker Hindi/Tamil/Telugu/Malayalam datasets are still required.
