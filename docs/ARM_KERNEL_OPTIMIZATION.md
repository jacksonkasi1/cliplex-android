# Arm kernel optimization

## Scope

In ClipLex, “kernel-level optimization” means selecting optimized ggml CPU compute kernels inside
the app process. It does not mean changing the Android/Linux kernel, CPU governor, bootloader, or
ROM. This change keeps the public `arm64-v8a` APK portable and leaves `GGML_NATIVE` disabled.

The integration commit is `d3fc125` and the physical-device benchmark implementation commit is
`81d812b`. Both are based on `origin/main` commit
`6a2df0eceecc41b0a11058eb786eb4dcd8b91157`.

## Build control

KleidiAI is opt-in and defaults to off:

```text
./gradlew :app:assembleSafeDebug -PcliplexKleidiAi=false
./gradlew :app:assembleSafeDebug -PcliplexKleidiAi=true
```

The Gradle property becomes `CLIPLEX_ENABLE_KLEIDIAI` in CMake, which in turn controls
`GGML_CPU_KLEIDIAI`. The opt-in build uses the KleidiAI v1.9.0 archive and checksum already pinned
by whisper.cpp v1.7.6. ClipLex predeclares that FetchContent dependency because the pinned
whisper.cpp declaration uses a CMake keyword unsupported by the repository's required CMake 3.22.

No build uses `-march=native`, a device-specific `-mcpu`, or a global dot-product/I8MM ISA flag.
The default remains `false` because the production Q5_1 model does not use this whisper.cpp
version's Q4_0-only KleidiAI path.

## Runtime dispatch evidence

The native bridge reads `AT_HWCAP` and `AT_HWCAP2` with `getauxval` and publishes a typed Kotlin
`ArmBackendDiagnostics` structure. The same values are included in native logs, transcription JSON,
and benchmark CSV:

- ABI and Arm64 state
- NEON/ASIMD, dot-product, and I8MM capabilities
- whether KleidiAI was compiled and is eligible for the loaded model
- selected ggml backend and observable kernel path
- generic fallback state and reason
- loaded model quantization and inference thread count

whisper.cpp v1.7.6 does not expose the individual per-operation micro-kernel through its public API.
ClipLex therefore reports `runtime-dispatch-unobserved` instead of naming a kernel when it cannot
prove selection. For the current Q5_1 model, the integration can prove that KleidiAI is not eligible,
so it reports `cpu`, `generic-ggml`, and the Q5_1 fallback reason.

## Generic fallback

Unsupported hardware, builds with the option disabled, unsupported model quantization, and
operators without a KleidiAI implementation continue through generic ggml. The public APK remains
an Arm64 baseline build, so compiling the optional backend cannot introduce an unsupported
instruction into the generic path.

## Model scope

Production remains on verified `ggml-tiny.en-q5_1.bin`:

- size: 32,166,155 bytes
- SHA-256: `c77c5766f1cef09b6b7d47f21b546cbddd4157886b3b5d6d4f709e91e66c7c2b`
- source revision: `c521a4b02f422512d734391fdf08bb08c0862f68`

The matching `ggml-tiny.en-q4_0.bin` asset was queried at that exact pinned source revision and the
source returned `Entry not found`. No URL, checksum, or file size was invented, so Q4_0 integration
is intentionally absent from this change.

## Production thread decision

The physical-device matrix tested 2, 4, 6, and 8 threads. Eight threads improved the median, but the
KleidiAI-compiled run had a materially less stable p95. The production default therefore remains six
threads. See [the comparison report](../benchmarks/ARM_KERNEL_COMPARISON.md) and the committed CSV
for raw measured timings.

## Deferred work

A custom Q5_1 SDOT/assembly micro-kernel is out of scope. It should only be considered after
operator-level profiling demonstrates that the maintenance and portability cost is justified.
