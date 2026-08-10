# Arm capability diagnostics and experimental KleidiAI integration

## Scope

This change adds an opt-in KleidiAI build experiment, Arm CPU capability diagnostics,
reproducible benchmarking infrastructure, and truthful fallback reporting. It does not claim that
the current Q5_1 production model uses a KleidiAI microkernel.

“Kernel” here refers only to native app-process compute code. No Android/Linux kernel, CPU governor,
bootloader, ROM, or device policy is modified. `GGML_NATIVE` remains disabled and ClipLex adds no
global `-march`, `-mcpu`, or phone-specific ISA flag.

## Build control and CI

The integration is opt-in and defaults to off:

```text
./gradlew :app:assembleSafeDebug -PcliplexKleidiAi=false
./gradlew :app:assembleSafeDebug -PcliplexKleidiAi=true
```

The property controls `CLIPLEX_ENABLE_KLEIDIAI` and pinned ggml's `GGML_CPU_KLEIDIAI` option.
KleidiAI v1.9.0 is fetched with the upstream archive checksum. CMake verifies that the expected
ggml wrapper and registry sources were added to `ggml-cpu`; this supports
`kleidiaiSourcesIncluded`, not a microkernel-selection claim.

The PR workflow builds, unit-tests, lints, assembles the app APK, and assembles the Android-test APK
for both configurations. It uploads separate artifacts:

- `cliplex-generic-safe-debug` (production/default configuration)
- `cliplex-kleidiai-experimental-safe-debug` (explicitly experimental)

## Telemetry contract

Native logs, structured JSON, Kotlin, and benchmark CSV use these fields:

- `kleidiaiIntegrationEnabled`: the build option was enabled.
- `kleidiaiSourcesIncluded`: the pinned ggml integration wrapper and registry sources were verified
  in the native target.
- `kleidiaiKernelSelectionObserved`: the runtime directly observed selection through a supported
  API. This remains false because pinned whisper.cpp exposes no such API.
- `modelEligibleForKleidiAi`: loaded-model metadata is eligible for the pinned integration.
- `selectedComputePath`: only names a path that can be defended from available evidence.
- `fallbackReason`: explains why the active model remains generic or selection is unobservable.

ABI, Arm64, NEON, DotProd, and I8MM HWCAP/HWCAP2 values are reported independently as device
capabilities. They are never used as proof that a KleidiAI microkernel was compiled or selected.

Model quantization is read from `whisper_model_ftype()` after model load. It is not inferred from the
filename. For the production Q5_1 model, the expected experimental state is:

```text
kleidiaiIntegrationEnabled=true
kleidiaiSourcesIncluded=true
kleidiaiKernelSelectionObserved=false
modelEligibleForKleidiAi=false
selectedComputePath=generic-ggml
fallbackReason=Q5_1 is not supported by the pinned KleidiAI integration
```

## Benchmark evidence

Benchmark filenames contain the sanitized device, UTC timestamp, source commit, configuration,
model quantization, and APK SHA-256 prefix. Every raw row also contains the full APK hash, source
commit, build variant, whisper.cpp commit, and KleidiAI version.

Rows are flushed individually. Output is copied to app-specific external storage in `finally`, so
inference or final-assertion failures retain the evidence already collected. Every measured result
must match the fixed canonical JFK transcript and the other thread/configuration results.

Untouched raw device files live in `benchmarks/raw/`. The checked-in
`scripts/generate-arm-kernel-comparison.py` validates their identity and correctness fields before
writing the deterministic summary in `benchmarks/derived/`. See
[the comparison report](../benchmarks/ARM_KERNEL_COMPARISON.md).

## Licensing

KleidiAI v1.9.0 is Apache-2.0 with BSD-3-Clause material, not MIT. Arm attribution and the complete
license texts are documented in `THIRD_PARTY_NOTICES.md` and packaged inside every APK under
`assets/licenses/`.

## Deferred work

A real baseline-plus-specialized runtime dispatch target, direct upstream kernel-selection
observability, a verified Q4_0 model matrix, and custom Q5_1 SDOT/assembly work are intentionally out
of scope. They require separate design and portability validation before any optimized-kernel claim.
