package com.jacksonkasi.cliplex.audio

import java.nio.ByteBuffer
import java.nio.ByteOrder

object Pcm16 {
	data class WavData(val samples: ShortArray, val sampleRate: Int, val channels: Int)
	fun littleEndianBytesToShorts(bytes: ByteArray): ShortArray {
		require(bytes.size % 2 == 0) { "PCM16 byte count must be even" }
		return ShortArray(bytes.size / 2).also { output ->
			ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(output)
		}
	}

	fun normalize(samples: ShortArray): FloatArray = FloatArray(samples.size) { samples[it] / 32768f }

	fun stereoToMono(interleaved: ShortArray): ShortArray {
		require(interleaved.size % 2 == 0) { "Stereo PCM must contain complete frames" }
		val averaged = ShortArray(interleaved.size / 2) { frame ->
			((interleaved[frame * 2].toInt() + interleaved[frame * 2 + 1].toInt()) / 2).toShort()
		}
		if (averaged.isEmpty()) return averaged
		var leftEnergy = 0.0
		var rightEnergy = 0.0
		var mixedEnergy = 0.0
		for (frame in averaged.indices) {
			val left = interleaved[frame * 2].toDouble()
			val right = interleaved[frame * 2 + 1].toDouble()
			val mixed = averaged[frame].toDouble()
			leftEnergy += left * left
			rightEnergy += right * right
			mixedEnergy += mixed * mixed
		}
		val strongerEnergy = maxOf(leftEnergy, rightEnergy)
		if (strongerEnergy > 0.0 && mixedEnergy < strongerEnergy * 0.01) {
			val channelOffset = if (leftEnergy >= rightEnergy) 0 else 1
			return ShortArray(averaged.size) { frame -> interleaved[frame * 2 + channelOffset] }
		}
		return averaged
	}

	fun resampleLinear(samples: ShortArray, sourceRate: Int, targetRate: Int): ShortArray {
		require(sourceRate > 0 && targetRate > 0)
		if (samples.isEmpty() || sourceRate == targetRate) return samples.copyOf()
		val outputSize = ((samples.size.toLong() * targetRate) / sourceRate).toInt().coerceAtLeast(1)
		return ShortArray(outputSize) { index ->
			val position = index.toDouble() * sourceRate / targetRate
			val left = position.toInt().coerceAtMost(samples.lastIndex)
			val right = (left + 1).coerceAtMost(samples.lastIndex)
			val fraction = position - left
			(samples[left] * (1.0 - fraction) + samples[right] * fraction).toInt().coerceIn(-32768, 32767).toShort()
		}
	}

	fun wav(samples: ShortArray, sampleRate: Int, channels: Int = 1): ByteArray {
		require(sampleRate > 0 && channels > 0)
		val dataSize = samples.size * 2
		return ByteBuffer.allocate(44 + dataSize).order(ByteOrder.LITTLE_ENDIAN).apply {
			put("RIFF".toByteArray(Charsets.US_ASCII)); putInt(36 + dataSize)
			put("WAVEfmt ".toByteArray(Charsets.US_ASCII)); putInt(16); putShort(1)
			putShort(channels.toShort()); putInt(sampleRate); putInt(sampleRate * channels * 2)
			putShort((channels * 2).toShort()); putShort(16)
			put("data".toByteArray(Charsets.US_ASCII)); putInt(dataSize)
			for (sample in samples) putShort(sample)
		}.array()
	}

	fun readWav(bytes: ByteArray): WavData {
		require(bytes.size >= 44 && String(bytes, 0, 4) == "RIFF" && String(bytes, 8, 4) == "WAVE") { "Invalid WAV" }
		val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
		var offset = 12
		var sampleRate = 0
		var channels = 0
		var bits = 0
		var dataOffset = -1
		var dataSize = 0
		while (offset + 8 <= bytes.size) {
			val id = String(bytes, offset, 4, Charsets.US_ASCII)
			val size = buffer.getInt(offset + 4)
			require(size >= 0 && offset + 8L + size <= bytes.size) { "Invalid WAV chunk" }
			if (id == "fmt ") {
				require(buffer.getShort(offset + 8).toInt() == 1) { "Only PCM WAV is supported" }
				channels = buffer.getShort(offset + 10).toInt()
				sampleRate = buffer.getInt(offset + 12)
				bits = buffer.getShort(offset + 22).toInt()
			} else if (id == "data") {
				dataOffset = offset + 8; dataSize = size; break
			}
			offset += 8 + size + (size and 1)
		}
		require(dataOffset >= 0 && bits == 16 && channels > 0 && sampleRate > 0) { "Unsupported WAV format" }
		return WavData(littleEndianBytesToShorts(bytes.copyOfRange(dataOffset, dataOffset + dataSize)), sampleRate, channels)
	}
}
