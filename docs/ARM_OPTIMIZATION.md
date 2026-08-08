# ARM optimization

The native build ships only `arm64-v8a`, uses quantized Whisper models, compiles JNI/Whisper/ggml release-style with `-O3`, and relies on ggml's NEON, ARM FMA, OpenMP, and repack CPU paths. `GGML_NATIVE` and KleidiAI remain disabled. Inference defaults to six threads and the model/context is reused.

Physical benchmarking on the OPPO CPH2781 found six threads fastest among one through six. For validated English clips from 0.1–10 seconds, `audio_ctx=512` reduced a 9.4-second clip from about 2.38 seconds to about 0.79 seconds warm while preserving the exact transcript. Contexts 384 and 256 degraded or repeated text, so 512 is the production floor. Timed segments cost only about 28 ms versus the no-timestamp variant and are retained for synchronized lessons.
