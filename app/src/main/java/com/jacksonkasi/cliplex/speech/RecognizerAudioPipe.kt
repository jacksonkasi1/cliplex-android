package com.jacksonkasi.cliplex.speech

import android.os.ParcelFileDescriptor
import java.io.Closeable
import java.io.IOException

class RecognizerAudioPipe private constructor(
	val readSide: ParcelFileDescriptor,
	private val writeSide: ParcelFileDescriptor,
) : Closeable {
	@Volatile private var closed = false

	fun writePcm16(samples: ShortArray) {
		if (closed) return
		ParcelFileDescriptor.AutoCloseOutputStream(writeSide).use { output ->
			val buffer = ByteArray(8 * 1024)
			var sampleIndex = 0
			while (sampleIndex < samples.size && !closed) {
				val count = minOf(buffer.size / 2, samples.size - sampleIndex)
				for (index in 0 until count) {
					val value = samples[sampleIndex + index].toInt()
					buffer[index * 2] = (value and 0xff).toByte()
					buffer[index * 2 + 1] = ((value ushr 8) and 0xff).toByte()
				}
				try {
					output.write(buffer, 0, count * 2)
				} catch (_: IOException) {
					if (!closed) throw
					return
				}
				sampleIndex += count
			}
			if (!closed) output.flush()
		}
	}

	override fun close() {
		closed = true
		runCatching { writeSide.close() }
		runCatching { readSide.close() }
	}

	companion object {
		fun open(): RecognizerAudioPipe {
			val descriptors = ParcelFileDescriptor.createReliablePipe()
			return RecognizerAudioPipe(readSide = descriptors[0], writeSide = descriptors[1])
		}
	}
}
