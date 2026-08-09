# Third-party notices

The root [MIT License](LICENSE) applies to ClipLex source code owned by its
copyright holder. It does not replace the licenses or terms that govern the
third-party software, services, model weights, fonts, icons, or other assets
used by the project.

## Native speech runtime

- [`whisper.cpp`](https://github.com/ggerganov/whisper.cpp), ggml, the upstream
  JFK diagnostic sample, and the deterministic benchmark crop derived from it
  are included through or sourced from the pinned
  `native/whisper.cpp` Git submodule. Their copyright and MIT license are in
  [`native/whisper.cpp/LICENSE`](native/whisper.cpp/LICENSE).
- The experimental build downloads and statically incorporates
  [Arm KleidiAI v1.9.0](https://github.com/ARM-software/kleidiai/tree/v1.9.0).
  Copyright 2024-2025 Arm Limited and/or its affiliates. The pinned source uses
  Apache License 2.0 for the integration and microkernel sources and also
  contains BSD-3-Clause material; it is not MIT-licensed. The attribution and
  complete license texts are packaged in every APK under `assets/licenses/`:
  [`kleidiai-NOTICE.txt`](app/src/main/assets/licenses/kleidiai-NOTICE.txt),
  [`kleidiai-Apache-2.0.txt`](app/src/main/assets/licenses/kleidiai-Apache-2.0.txt),
  and [`kleidiai-BSD-3-Clause.txt`](app/src/main/assets/licenses/kleidiai-BSD-3-Clause.txt).
- Whisper model files are downloaded separately from the
  [`whisper.cpp` model repository](https://huggingface.co/ggerganov/whisper.cpp).
  Model files are not covered by the ClipLex MIT license; review the model
  repository and the original model's terms before redistribution.

## Android and application dependencies

- AndroidX, Jetpack Compose, Room, and Material components are generally
  distributed under the Apache License 2.0. See each artifact's published
  metadata and notices for the authoritative terms.
- Kotlin and kotlinx.coroutines are distributed under the Apache License 2.0.
- OkHttp is distributed under the Apache License 2.0.
- Google ML Kit translation and its downloaded models are subject to Google's
  applicable SDK, API, and model terms; they are not relicensed by ClipLex.
- LiteRT-LM is distributed under its upstream terms. Gemma model weights are
  downloaded separately and remain subject to the Gemma Terms of Use and the
  model card for the selected model.
- Material Symbols/Icons and any other third-party visual assets remain under
  their respective upstream licenses.

The Gradle build may resolve transitive dependencies not individually listed
above. Before distributing a production binary, generate an inventory from the
resolved dependency graph and preserve all notices required by those packages.

No third-party trademark is granted by the ClipLex license.
