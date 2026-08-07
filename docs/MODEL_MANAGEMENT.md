# Model management

The APK contains no model binaries. The app offers:

| Model | Exact bytes | SHA-256 |
|---|---:|---|
| Tiny multilingual Q5_1 | 32,152,673 | `818710568da3ca15689e31a743197b520007872ff9576237bda97bd1b469c3d7` |
| Base multilingual Q5_1 | 59,707,625 | `422f1ae452ade6f30a004d7e5c6a43195e4433bc370bf23fac9cc591f01a8898` |

Downloads use HTTPS from the official `ggerganov/whisper.cpp` Hugging Face repository, resume with HTTP Range from a private `.part` file, and are atomically renamed only after size and SHA-256 validation. Duplicate taps reuse the active download job. The selected model is marked clearly and persisted; any downloaded model can be re-selected from the Settings icon. Tiny becomes the default only when no valid selection exists, and the native context is reused across captures.

Adaptive Tiny-to-Base retry and Silero VAD model management remain future work.
