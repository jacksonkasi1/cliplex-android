package com.learnthis.vad

import kotlin.math.sqrt

/**
* Simple energy-based Voice Activity Detection.
* Detects speech segments in PCM audio based on RMS energy thresholds.
*
* For Phase 05 - production will switch to Silero VAD or whisper VAD.
*/
class EnergyVad(
private val sampleRateHz: Int = 16000,
private val frameMs: Int = 30,
private val energyThreshold: Float = 500f,
private val speechRatioThreshold: Float = 0.3f,
private val minSpeechFrames: Int = 10,
private val paddingFrames: Int = 5,
) {
private val frameSize = (sampleRateHz * frameMs) / 1000
private val frameBuffer = ShortArray(frameSize)

data class Segment(
val startSample: Long,
val endSample: Long,
val durationMs: Long
)

data class DetectionResult(
val isSpeech: Boolean,
val segments: List<Segment>,
val currentSpeechStartSample: Long?,
)

private var totalSamplesProcessed: Long = 0
private var speechFrameCount: Int = 0
private var totalFrameCount: Int = 0
private var currentSpeechStart: Long? = null
private val segments = mutableListOf<Segment>()
private var speechActive = false

fun reset() {
totalSamplesProcessed = 0
speechFrameCount = 0
totalFrameCount = 0
currentSpeechStart = null
segments.clear()
speechActive = false
}

fun process(input: ShortArray, offset: Int, length: Int): DetectionResult {
val samplesRemaining = length
var pos = offset

while (pos + frameSize <= offset + length) {
val energy = computeRms(input, pos, frameSize)
val isSpeech = energy > energyThreshold

totalFrameCount++
val frameStartSample = totalSamplesProcessed

if (isSpeech) {
speechFrameCount++
if (!speechActive) {
speechActive = true
currentSpeechStart = frameStartSample
}
} else {
if (speechActive) {
val segmentStart = currentSpeechStart ?: frameStartSample
segments.add(
Segment(
startSample = segmentStart,
endSample = frameStartSample,
durationMs = ((frameStartSample - segmentStart) * 1000) / sampleRateHz
)
)
speechActive = false
currentSpeechStart = null
}
}

totalSamplesProcessed += frameSize
pos += frameSize
}

val currentRatio = if (totalFrameCount > 0) speechFrameCount.toFloat() / totalFrameCount else 0f
return DetectionResult(
isSpeech = currentRatio > speechRatioThreshold && speechFrameCount >= minSpeechFrames,
segments = segments.toList(),
currentSpeechStartSample = currentSpeechStart
)
}

fun finalize(): List<Segment> {
if (speechActive && currentSpeechStart != null) {
segments.add(
Segment(
startSample = currentSpeechStart!!,
endSample = totalSamplesProcessed,
durationMs = ((totalSamplesProcessed - currentSpeechStart!!) * 1000) / sampleRateHz
)
)
}
val result = segments.toList()
reset()
return result
}

private fun computeRms(data: ShortArray, offset: Int, length: Int): Float {
var sum: Long = 0
val end = minOf(offset + length, data.size)
for (i in offset until end) {
val sample = data[i].toInt()
sum += sample * sample
}
val count = end - offset
return sqrt((sum / count).toFloat())
}
}
