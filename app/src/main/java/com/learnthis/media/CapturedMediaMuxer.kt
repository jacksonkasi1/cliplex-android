package com.learnthis.media

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Adds the already-authorized AudioPlaybackCapture PCM to the screen-recording MP4. */
object CapturedMediaMuxer {
	fun muxPlaybackAudio(
		videoOnlyFile: File,
		pcm: ShortArray,
		sampleRate: Int,
		outputFile: File,
	): Result<File> = runCatching {
		require(videoOnlyFile.isFile && videoOnlyFile.length() > 0L) { "Captured video is missing" }
		require(pcm.isNotEmpty()) { "Captured audio is empty" }
		outputFile.parentFile?.mkdirs()
		if (outputFile.exists()) outputFile.delete()
		val audioOnlyFile = File(outputFile.parentFile, "${outputFile.nameWithoutExtension}.audio.tmp.mp4")
		try {
			encodeAac(pcm, sampleRate, audioOnlyFile)
			merge(videoOnlyFile, audioOnlyFile, outputFile)
			require(outputFile.isFile && outputFile.length() > 0L) { "Final lesson video was empty" }
			videoOnlyFile.delete()
			outputFile
		} finally {
			audioOnlyFile.delete()
		}
	}

	private fun encodeAac(pcm: ShortArray, sampleRate: Int, outputFile: File) {
		if (outputFile.exists()) outputFile.delete()
		val format = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, sampleRate, 1).apply {
			setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
			setInteger(MediaFormat.KEY_BIT_RATE, 64_000)
			setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16_384)
		}
		val codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
		val muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
		var muxerStarted = false
		try {
			codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
			codec.start()
			val info = MediaCodec.BufferInfo()
			var inputOffset = 0
			var inputEnded = false
			var outputEnded = false
			var outputTrack = -1
			while (!outputEnded) {
				if (!inputEnded) {
					val inputIndex = codec.dequeueInputBuffer(CODEC_TIMEOUT_US)
					if (inputIndex >= 0) {
						val input = requireNotNull(codec.getInputBuffer(inputIndex)).apply {
							clear()
							order(ByteOrder.LITTLE_ENDIAN)
						}
						val count = minOf(input.remaining() / 2, pcm.size - inputOffset)
						val presentationTimeUs = inputOffset * 1_000_000L / sampleRate
						for (index in 0 until count) input.putShort(pcm[inputOffset + index])
						inputOffset += count
						val flags = if (inputOffset >= pcm.size) MediaCodec.BUFFER_FLAG_END_OF_STREAM else 0
						codec.queueInputBuffer(inputIndex, 0, count * 2, presentationTimeUs, flags)
						inputEnded = flags != 0
					}
				}

				when (val outputIndex = codec.dequeueOutputBuffer(info, CODEC_TIMEOUT_US)) {
					MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
						check(!muxerStarted) { "AAC output format changed twice" }
						outputTrack = muxer.addTrack(codec.outputFormat)
						muxer.start()
						muxerStarted = true
					}
					MediaCodec.INFO_TRY_AGAIN_LATER -> Unit
					else -> if (outputIndex >= 0) {
						val output = requireNotNull(codec.getOutputBuffer(outputIndex))
						if (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) info.size = 0
						if (info.size > 0) {
							check(muxerStarted && outputTrack >= 0) { "AAC muxer was not ready" }
							output.position(info.offset)
							output.limit(info.offset + info.size)
							muxer.writeSampleData(outputTrack, output, info)
						}
						outputEnded = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
						codec.releaseOutputBuffer(outputIndex, false)
					}
				}
			}
		} finally {
			try { codec.stop() } catch (_: Exception) { }
			codec.release()
			if (muxerStarted) try { muxer.stop() } catch (_: Exception) { }
			muxer.release()
		}
	}

	private fun merge(videoFile: File, audioFile: File, outputFile: File) {
		val videoExtractor = MediaExtractor()
		val audioExtractor = MediaExtractor()
		val muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
		var started = false
		try {
			videoExtractor.setDataSource(videoFile.absolutePath)
			audioExtractor.setDataSource(audioFile.absolutePath)
			val videoInputTrack = videoExtractor.findTrack("video/")
			val audioInputTrack = audioExtractor.findTrack("audio/")
			require(videoInputTrack >= 0) { "Captured MP4 has no video track" }
			require(audioInputTrack >= 0) { "Encoded playback audio has no track" }
			val videoFormat = videoExtractor.getTrackFormat(videoInputTrack)
			val audioFormat = audioExtractor.getTrackFormat(audioInputTrack)
			val videoOutputTrack = muxer.addTrack(videoFormat)
			val audioOutputTrack = muxer.addTrack(audioFormat)
			if (videoFormat.containsKey(MediaFormat.KEY_ROTATION)) {
				muxer.setOrientationHint(videoFormat.getInteger(MediaFormat.KEY_ROTATION))
			}
			muxer.start()
			started = true
			videoExtractor.selectTrack(videoInputTrack)
			audioExtractor.selectTrack(audioInputTrack)

			val videoBuffer = ByteBuffer.allocateDirect(trackBufferSize(videoFormat))
			val audioBuffer = ByteBuffer.allocateDirect(trackBufferSize(audioFormat))
			val info = MediaCodec.BufferInfo()
			var videoDone = false
			var audioDone = false
			while (!videoDone || !audioDone) {
				val videoTime = if (videoDone) Long.MAX_VALUE else videoExtractor.sampleTime
				val audioTime = if (audioDone) Long.MAX_VALUE else audioExtractor.sampleTime
				if (videoTime <= audioTime) {
					videoDone = !writeNextSample(videoExtractor, muxer, videoOutputTrack, videoBuffer, info)
				} else {
					audioDone = !writeNextSample(audioExtractor, muxer, audioOutputTrack, audioBuffer, info)
				}
			}
		} finally {
			videoExtractor.release()
			audioExtractor.release()
			if (started) try { muxer.stop() } catch (_: Exception) { }
			muxer.release()
		}
	}

	private fun writeNextSample(
		extractor: MediaExtractor,
		muxer: MediaMuxer,
		track: Int,
		buffer: ByteBuffer,
		info: MediaCodec.BufferInfo,
	): Boolean {
		val sampleTime = extractor.sampleTime
		if (sampleTime < 0L) return false
		buffer.clear()
		val size = extractor.readSampleData(buffer, 0)
		if (size < 0) return false
		info.set(0, size, sampleTime, extractor.sampleFlags.toCodecBufferFlags())
		muxer.writeSampleData(track, buffer, info)
		extractor.advance()
		return true
	}

	private fun Int.toCodecBufferFlags(): Int {
		check(this and MediaExtractor.SAMPLE_FLAG_ENCRYPTED == 0) {
			"Encrypted samples cannot be copied into a private captured lesson"
		}
		var codecFlags = 0
		if (this and MediaExtractor.SAMPLE_FLAG_SYNC != 0) {
			codecFlags = codecFlags or MediaCodec.BUFFER_FLAG_KEY_FRAME
		}
		if (this and MediaExtractor.SAMPLE_FLAG_PARTIAL_FRAME != 0) {
			codecFlags = codecFlags or MediaCodec.BUFFER_FLAG_PARTIAL_FRAME
		}
		return codecFlags
	}

	private fun MediaExtractor.findTrack(prefix: String): Int = (0 until trackCount).firstOrNull { index ->
		getTrackFormat(index).getString(MediaFormat.KEY_MIME)?.startsWith(prefix) == true
	} ?: -1

	private fun trackBufferSize(format: MediaFormat): Int = maxOf(
		format.getIntegerOrNull(MediaFormat.KEY_MAX_INPUT_SIZE) ?: 0,
		2 * 1024 * 1024,
	)

	private fun MediaFormat.getIntegerOrNull(key: String): Int? =
		if (containsKey(key)) runCatching { getInteger(key) }.getOrNull() else null

	private const val CODEC_TIMEOUT_US = 10_000L
}
