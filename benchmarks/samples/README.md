# Benchmark samples

Only commit audio that the project is permitted to redistribute. For every
sample, record the speaker/source, license or consent, exact duration, sample
rate, channel count, encoding, and SHA-256 in the corresponding result report.

The controlled 2026-08-08 benchmark input is committed as
[`jfk-first-9.4s-16khz-mono.wav`](jfk-first-9.4s-16khz-mono.wav). It contains
150,400 mono PCM16 samples at 16 kHz (9.4 seconds) and has SHA-256
`f2871e112ba83f00d1b5b21d4147decbf40990d9b43618309c42cf8577caa3bd`.

The fixture is a deterministic crop of `native/whisper.cpp/samples/jfk.wav`
and remains subject to the upstream notices in `native/whisper.cpp`.
