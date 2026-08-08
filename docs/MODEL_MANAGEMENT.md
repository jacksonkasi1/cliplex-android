# Model management

The APK contains no model binaries. Learning language and speech quality are persisted independently from the translation mother tongue. `ModelResolver` is the only component that maps those choices to a runtime model and Whisper language code:

| Learning choice | Runtime model | Language policy | Exact bytes | SHA-256 |
|---|---|---|---:|---|
| English | `ggml-tiny.en-q5_1.bin` | fixed `en` | 32,166,155 | `c77c5766f1cef09b6b7d47f21b546cbddd4157886b3b5d6d4f709e91e66c7c2b` |
| Known non-English · Fast | `ggml-tiny-q5_1.bin` | forced selected code (`hi`, `ta`, `te`, etc.) | 32,152,673 | `818710568da3ca15689e31a743197b520007872ff9576237bda97bd1b469c3d7` |
| Known non-English · Recommended | `ggml-base-q5_1.bin` | forced selected code | 59,707,625 | `422f1ae452ade6f30a004d7e5c6a43195e4433bc370bf23fac9cc591f01a8898` |
| Any Language | Tiny or Base multilingual | `auto`, with decoding continuing after detection | tier-dependent | tier-dependent |

High Accuracy remains unavailable until a larger model or language pack passes licensing, conversion, quality, memory, thermal, and real-device validation. Normal UI never exposes GGML or quantization terminology; technical names remain debug-only.

All URLs are pinned to the official `whisper.cpp` model revision `c521a4b02f422512d734391fdf08bb08c0862f68`. A download:

1. resumes a private `<model>.part` with a validated HTTP Range response;
2. checks response length when provided and never accepts more than the catalog byte count;
3. validates the completed file's exact byte count and SHA-256;
4. syncs it to storage and atomically replaces the final file;
5. preserves a bounded incomplete partial after interruption for retry, while deleting invalid ranges, wrong sizes, and checksum failures; a valid final model is never replaced by a failed download.

Only the resolved model is required and downloaded. Switching languages or tiers never silently deletes another downloaded model. Legacy Tiny/Base and two-mode preferences are read as migration aliases; new preferences store the explicit learning-language code and speech-quality tier. When a newly selected model finishes downloading, the active Home ViewModel retries model loading without requiring an app restart.

Once the selected speech model and the required ML Kit translation resources have been downloaded, transcription and translation run on-device. Adaptive Tiny-to-Base retry and Silero VAD remain future work.
