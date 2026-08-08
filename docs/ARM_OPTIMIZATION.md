# ARM optimization

The native build ships only `arm64-v8a`, uses quantized Whisper models, compiles JNI/Whisper/ggml release-style with `-O3`, and relies on ggml's NEON, ARM FMA, OpenMP, and repack CPU paths. `GGML_NATIVE` and KleidiAI remain disabled. Inference defaults to six threads and the model/context is reused.

On 2026-08-08, a controlled OPPO CPH2781 run used two warm-ups and five measured runs per configuration with a committed 9.4-second input. The baseline native-inference median was 2,393.764 ms; `audio_ctx=512` reduced it to 692.659 ms while preserving normalized transcript content. That is a measured 3.456x speedup and 71.06% latency reduction. Timed segments remain enabled for synchronized lessons. See the [protocol and raw CSV](../benchmarks/BENCHMARK.md).
