package com.learnthis.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import androidx.core.app.NotificationCompat
import com.learnthis.BuildConfig
import com.learnthis.MainActivity
import com.learnthis.audio.AudioDiagnostics
import com.learnthis.domain.model.AudioHealth
import com.learnthis.domain.model.CaptureError
import com.learnthis.overlay.OverlayService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicLong

class CaptureService : Service() {

	companion object {
		const val CHANNEL_ID = "learn_this_capture_channel"
		const val NOTIFICATION_ID = 1001
		const val ACTION_START = "com.learnthis.action.START_LEARNING_MODE"
		const val ACTION_BEGIN = "com.learnthis.action.BEGIN_CAPTURE"
		const val ACTION_FINISH = "com.learnthis.action.FINISH_CAPTURE"
		const val ACTION_TOGGLE = "com.learnthis.action.TOGGLE_CAPTURE"
		const val ACTION_STOP = "com.learnthis.action.STOP_LEARNING_MODE"
		const val EXTRA_MEDIA_PROJECTION_RESULT_DATA = "media_projection_result_data"
		const val EXTRA_MEDIA_PROJECTION_RESULT_CODE = "media_projection_result_code"
		const val SAMPLE_RATE_HZ = 16_000
		const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
		const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
		private const val MAX_CAPTURE_SAMPLES = SAMPLE_RATE_HZ * 60

		private val _captureState = MutableStateFlow<CaptureState>(CaptureState.Idle)
		val captureState: StateFlow<CaptureState> = _captureState.asStateFlow()
		private val _capturedAudioDuration = MutableStateFlow(0L)
		val capturedAudioDuration: StateFlow<Long> = _capturedAudioDuration.asStateFlow()
		private val _latestSession = MutableStateFlow<CapturedAudioSession?>(null)
		val latestSession: StateFlow<CapturedAudioSession?> = _latestSession.asStateFlow()
		private val sessionIds = AtomicLong()
	}

	sealed interface CaptureState {
		data object Idle : CaptureState
		data object Preparing : CaptureState
		data object Armed : CaptureState
		data object Capturing : CaptureState
		data object Processing : CaptureState
		data class Error(val error: CaptureError, val detail: String? = null) : CaptureState
	}

	data class CapturedAudioSession(
		val id: Long,
		val samples: ShortArray,
		val health: AudioHealth,
	)

	private var mediaProjection: MediaProjection? = null
	private var projectionCallback: MediaProjection.Callback? = null
	private var audioRecord: AudioRecord? = null
	private var captureJob: Job? = null
	private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
	private val ringBuffer = RingBuffer(MAX_CAPTURE_SAMPLES)
	private var audioCaptureBuffer = ShortArray(0)
	@Volatile private var intentionallyStopping = false

	override fun onBind(intent: Intent?): IBinder? = null

	override fun onCreate() {
		super.onCreate()
		createNotificationChannel()
	}

	override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
		when (intent?.action) {
			ACTION_START -> {
				val resultCode = intent.getIntExtra(EXTRA_MEDIA_PROJECTION_RESULT_CODE, -1)
				val resultData = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
					intent.getParcelableExtra(EXTRA_MEDIA_PROJECTION_RESULT_DATA, Intent::class.java)
				} else {
					@Suppress("DEPRECATION")
					intent.getParcelableExtra(EXTRA_MEDIA_PROJECTION_RESULT_DATA)
				}
				startForeground(NOTIFICATION_ID, buildNotification(CaptureState.Preparing))
				if (resultCode == -1 || resultData == null) {
					fail(CaptureError.CAPTURE_NOT_STARTED, "MediaProjection consent data was missing")
				} else {
					startLearningMode(resultCode, resultData)
				}
			}
			ACTION_BEGIN -> beginSession()
			ACTION_FINISH -> finishSession()
			ACTION_TOGGLE -> if (_captureState.value == CaptureState.Capturing) finishSession() else beginSession()
			ACTION_STOP -> stopLearningMode()
		}
		return START_NOT_STICKY
	}

	private fun startLearningMode(resultCode: Int, resultData: Intent) {
		if (_captureState.value != CaptureState.Idle && _captureState.value !is CaptureState.Error) return
		_captureState.value = CaptureState.Preparing
		intentionallyStopping = false
		try {
			val projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
			val projection = projectionManager.getMediaProjection(resultCode, resultData)
			if (projection == null) {
				fail(CaptureError.CAPTURE_NOT_STARTED, "MediaProjection could not be created")
				return
			}
			val callback = object : MediaProjection.Callback() {
				override fun onStop() {
					if (!intentionallyStopping) {
						fail(CaptureError.MEDIA_PROJECTION_REVOKED, "MediaProjection callback stopped")
					}
				}
			}
			projection.registerCallback(callback, null)
			mediaProjection = projection
			projectionCallback = callback

			val config = AudioPlaybackCaptureConfiguration.Builder(projection)
				.addMatchingUsage(AudioAttributes.USAGE_MEDIA)
				.addMatchingUsage(AudioAttributes.USAGE_GAME)
				.addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
				.build()
			val minBufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE_HZ, CHANNEL_CONFIG, AUDIO_FORMAT)
			if (minBufferSize <= 0) {
				fail(CaptureError.AUDIO_FORMAT_INVALID, "getMinBufferSize returned $minBufferSize")
				return
			}
			val bufferSize = maxOf(minBufferSize * 4, SAMPLE_RATE_HZ * 2)
			audioCaptureBuffer = ShortArray(bufferSize / 2)
			val recorder = AudioRecord.Builder()
				.setAudioFormat(AudioFormat.Builder()
					.setEncoding(AUDIO_FORMAT)
					.setSampleRate(SAMPLE_RATE_HZ)
					.setChannelMask(CHANNEL_CONFIG)
					.build())
				.setAudioPlaybackCaptureConfig(config)
				.setBufferSizeInBytes(bufferSize)
				.build()
			if (recorder.state != AudioRecord.STATE_INITIALIZED) {
				recorder.release()
				fail(CaptureError.CAPTURE_NOT_STARTED, "AudioRecord did not initialize")
				return
			}
			audioRecord = recorder
			captureJob = serviceScope.launch(Dispatchers.IO) { processAudioStream(recorder) }
			showOverlayIfAllowed()
		} catch (security: SecurityException) {
			fail(CaptureError.CAPTURE_NOT_STARTED, security.message)
		} catch (error: Exception) {
			fail(CaptureError.CAPTURE_NOT_STARTED, error.message)
		}
	}

	private fun processAudioStream(recorder: AudioRecord) {
		try {
			recorder.startRecording()
			if (recorder.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
				serviceScope.launch { fail(CaptureError.CAPTURE_NOT_STARTED, "AudioRecord did not enter recording state") }
				return
			}
			_captureState.value = CaptureState.Armed
			updateNotification()
			while (!Thread.currentThread().isInterrupted) {
				val read = recorder.read(audioCaptureBuffer, 0, audioCaptureBuffer.size, AudioRecord.READ_BLOCKING)
				if (read > 0 && _captureState.value == CaptureState.Capturing) {
					ringBuffer.write(audioCaptureBuffer, read)
					_capturedAudioDuration.value = ringBuffer.size * 1000L / SAMPLE_RATE_HZ
					if (ringBuffer.size >= MAX_CAPTURE_SAMPLES) serviceScope.launch { finishSession() }
				} else if (read < 0) {
					serviceScope.launch { fail(CaptureError.CAPTURE_NOT_STARTED, "AudioRecord read error $read") }
					return
				}
			}
		} catch (error: Exception) {
			if (!intentionallyStopping) serviceScope.launch { fail(CaptureError.CAPTURE_NOT_STARTED, error.message) }
		}
	}

	private fun beginSession() {
		if (_captureState.value != CaptureState.Armed) return
		ringBuffer.reset()
		_capturedAudioDuration.value = 0L
		_captureState.value = CaptureState.Capturing
		updateNotification()
	}

	private fun finishSession() {
		if (_captureState.value != CaptureState.Capturing) return
		_captureState.value = CaptureState.Processing
		val samples = ringBuffer.snapshot()
		val health = AudioDiagnostics.analyze(samples, SAMPLE_RATE_HZ)
		_latestSession.value = CapturedAudioSession(sessionIds.incrementAndGet(), samples, health)
		_captureState.value = CaptureState.Armed
		updateNotification()
	}

	private fun fail(error: CaptureError, detail: String?) {
		_captureState.value = CaptureState.Error(error, detail)
		releaseResources(stopProjection = true)
		stopForeground(STOP_FOREGROUND_REMOVE)
		stopSelf()
	}

	private fun stopLearningMode() {
		if (_captureState.value == CaptureState.Capturing) finishSession()
		intentionallyStopping = true
		releaseResources(stopProjection = true)
		_captureState.value = CaptureState.Idle
		_capturedAudioDuration.value = 0L
		hideOverlay()
		stopForeground(STOP_FOREGROUND_REMOVE)
		stopSelf()
	}

	private fun releaseResources(stopProjection: Boolean) {
		val recorder = audioRecord
		audioRecord = null
		try { if (recorder?.recordingState == AudioRecord.RECORDSTATE_RECORDING) recorder.stop() } catch (_: Exception) { }
		captureJob?.cancel()
		captureJob = null
		try { recorder?.release() } catch (_: Exception) { }
		val projection = mediaProjection
		val callback = projectionCallback
		mediaProjection = null
		projectionCallback = null
		if (projection != null && callback != null) try { projection.unregisterCallback(callback) } catch (_: Exception) { }
		if (stopProjection) try { projection?.stop() } catch (_: Exception) { }
	}

	private fun showOverlayIfAllowed() {
		if (BuildConfig.OVERLAY_SUPPORTED && Settings.canDrawOverlays(this)) {
			startService(Intent(this, OverlayService::class.java).setAction(OverlayService.ACTION_SHOW_OVERLAY))
		}
	}

	private fun hideOverlay() {
		startService(Intent(this, OverlayService::class.java).setAction(OverlayService.ACTION_HIDE_OVERLAY))
	}

	private fun createNotificationChannel() {
		val manager = getSystemService(NotificationManager::class.java)
		manager.createNotificationChannel(NotificationChannel(
			CHANNEL_ID, "Learn This capture", NotificationManager.IMPORTANCE_LOW,
		))
	}

	private fun updateNotification() {
		getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, buildNotification(_captureState.value))
	}

	private fun buildNotification(state: CaptureState): Notification {
		val builder = NotificationCompat.Builder(this, CHANNEL_ID)
			.setContentTitle("Learn This")
			.setSmallIcon(android.R.drawable.ic_btn_speak_now)
			.setOngoing(true)
			.setPriority(NotificationCompat.PRIORITY_LOW)
			.setContentIntent(PendingIntent.getActivity(
				this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
			))
			.addAction(0, "Stop learning mode", servicePendingIntent(ACTION_STOP, 3))
		when (state) {
			CaptureState.Armed -> builder
				.setContentText("Ready — play a video, then tap Start capture")
				.addAction(0, "Start capture", servicePendingIntent(ACTION_BEGIN, 1))
			CaptureState.Capturing -> builder
				.setContentText("Capturing playback audio")
				.addAction(0, "Finish", servicePendingIntent(ACTION_FINISH, 2))
			CaptureState.Processing -> builder.setContentText("Processing captured speech")
			CaptureState.Preparing -> builder.setContentText("Preparing playback capture")
			is CaptureState.Error -> builder.setContentText("Capture error")
			CaptureState.Idle -> builder.setContentText("Learning mode stopped")
		}
		return builder.build()
	}

	private fun servicePendingIntent(action: String, requestCode: Int): PendingIntent = PendingIntent.getService(
		this, requestCode, Intent(this, CaptureService::class.java).setAction(action),
		PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
	)

	override fun onDestroy() {
		intentionallyStopping = true
		releaseResources(stopProjection = true)
		hideOverlay()
		serviceScope.cancel()
		super.onDestroy()
	}

	private class RingBuffer(private val capacity: Int) {
		private val buffer = ShortArray(capacity)
		private val lock = Any()
		private var writePosition = 0
		private var filled = 0
		val size: Int get() = synchronized(lock) { filled }

		fun reset() = synchronized(lock) { writePosition = 0; filled = 0 }

		fun write(data: ShortArray, count: Int) = synchronized(lock) {
			for (index in 0 until count) {
				buffer[writePosition] = data[index]
				writePosition = (writePosition + 1) % capacity
				if (filled < capacity) filled++
			}
		}

		fun snapshot(): ShortArray = synchronized(lock) {
			ShortArray(filled).also { result ->
				val start = (writePosition - filled + capacity) % capacity
				for (index in 0 until filled) result[index] = buffer[(start + index) % capacity]
			}
		}
	}
}
