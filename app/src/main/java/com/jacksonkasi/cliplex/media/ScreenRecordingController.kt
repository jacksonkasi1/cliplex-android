package com.jacksonkasi.cliplex.media

import android.content.Context
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.os.Build
import android.util.Log
import android.view.WindowManager
import java.io.File

/** Records only the projected display. Playback audio is muxed from AudioPlaybackCapture later. */
class ScreenRecordingController(
	private val context: Context,
	private val mediaProjection: MediaProjection,
) {
	private var recorder: MediaRecorder? = null
	private var virtualDisplay: VirtualDisplay? = null
	private var outputFile: File? = null

	fun start(file: File): Result<Unit> = runCatching {
		check(recorder == null) { "Screen recording is already active" }
		require(context.filesDir.usableSpace >= MIN_FREE_BYTES) { "Not enough private storage to capture video" }
		file.parentFile?.mkdirs()
		if (file.exists()) file.delete()

		val size = captureSize()
		val configured = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
			MediaRecorder(context)
		} else {
			@Suppress("DEPRECATION")
			MediaRecorder()
		}
		configured.setVideoSource(MediaRecorder.VideoSource.SURFACE)
		configured.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
		configured.setVideoEncoder(MediaRecorder.VideoEncoder.H264)
		configured.setVideoSize(size.width, size.height)
		configured.setVideoFrameRate(FRAME_RATE)
		configured.setVideoEncodingBitRate(VIDEO_BIT_RATE)
		configured.setOutputFile(file.absolutePath)
		configured.prepare()

		val display = mediaProjection.createVirtualDisplay(
			"ClipLex lesson capture",
			size.width,
			size.height,
			context.resources.configuration.densityDpi,
			DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
			configured.surface,
			null,
			null,
		) ?: error("Android could not create the screen-capture display")

		try {
			configured.start()
		} catch (error: Exception) {
			display.release()
			configured.release()
			throw error
		}
		recorder = configured
		virtualDisplay = display
		outputFile = file
		Log.i(TAG, "Screen recording started ${size.width}x${size.height} at ${file.name}")
	}

	fun stop(): Result<File?> = runCatching {
		val activeRecorder = recorder ?: return@runCatching null
		recorder = null
		try {
			activeRecorder.stop()
		} finally {
			try { activeRecorder.release() } catch (_: Exception) { }
			virtualDisplay?.release()
			virtualDisplay = null
		}
		val file = outputFile
		outputFile = null
		if (file != null && file.isFile && file.length() > MIN_VALID_VIDEO_BYTES) {
			Log.i(TAG, "Screen recording completed bytes=${file.length()}")
			file
		} else {
			file?.delete()
			null
		}
	}.onFailure { error ->
		Log.e(TAG, "Screen recording could not be finalized", error)
		outputFile?.delete()
		outputFile = null
		virtualDisplay?.release()
		virtualDisplay = null
	}

	fun release(deleteIncomplete: Boolean) {
		val activeRecorder = recorder
		recorder = null
		if (activeRecorder != null) {
			try { activeRecorder.stop() } catch (_: Exception) { }
			try { activeRecorder.release() } catch (_: Exception) { }
		}
		virtualDisplay?.release()
		virtualDisplay = null
		if (deleteIncomplete) outputFile?.delete()
		outputFile = null
	}

	private fun captureSize(): CaptureSize {
		val bounds = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
			context.getSystemService(WindowManager::class.java).maximumWindowMetrics.bounds
		} else {
			@Suppress("DEPRECATION")
			context.resources.displayMetrics.run { android.graphics.Rect(0, 0, widthPixels, heightPixels) }
		}
		val scale = minOf(1f, MAX_LONG_EDGE.toFloat() / maxOf(bounds.width(), bounds.height()))
		return CaptureSize(
			width = align16((bounds.width() * scale).toInt()),
			height = align16((bounds.height() * scale).toInt()),
		)
	}

	private fun align16(value: Int): Int = (value.coerceAtLeast(16) / 16) * 16

	private data class CaptureSize(val width: Int, val height: Int)

	private companion object {
		const val TAG = "ScreenRecorder"
		const val FRAME_RATE = 30
		const val VIDEO_BIT_RATE = 4_000_000
		const val MAX_LONG_EDGE = 1_920
		const val MIN_VALID_VIDEO_BYTES = 1_024L
		const val MIN_FREE_BYTES = 64L * 1024 * 1024
	}
}
