# ARM optimization

The native build ships only `arm64-v8a`, uses quantized Whisper models, compiles the JNI target with `-O3`, and relies on ggml's ARM64 CPU backend. `GGML_NATIVE` and KleidiAI are deliberately disabled until real OPPO measurements establish correctness and benefit. Inference defaults to four threads and the model/context is reused.

No thread-count, thermal, battery, or KleidiAI benchmark has yet been completed on the target phone. Do not infer performance numbers from desktop builds.
