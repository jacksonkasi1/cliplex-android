package com.learnthis.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.learnthis.BuildConfig
import com.learnthis.MainActivity
import com.learnthis.audio.AudioDiagnostics
import com.learnthis.audio.Pcm16
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
		const val ACTION_REFRESH_OVERLAY = "com.learnthis.action.REFRESH_OVERLAY"
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
		private val _overlayStatus = MutableStateFlow<OverlayStatus>(OverlayStatus.Unavailable)
		val overlayStatus: StateFlow<OverlayStatus> = _overlayStatus.asStateFlow()

		internal fun reportOverlayVisible() { _overlayStatus.value = OverlayStatus.Visible }
		internal fun reportOverlayError(detail: String) { _overlayStatus.value = OverlayStatus.Error(detail) }
	}

	sealed interface OverlayStatus {
		data object Unavailable : OverlayStatus
		data object Disabled : OverlayStatus
		data object Visible : OverlayStatus
		data class Error(val detail: String) : OverlayStatus
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
	private var captureFormat = CaptureFormat(SAMPLE_RATE_HZ, CHANNEL_CONFIG, 1)
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
				ServiceCompat.startForeground(
					this,
					NOTIFICATION_ID,
					buildNotification(CaptureState.Preparing),
					ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION,
				)
				if (resultCode == -1 || resultData == null) {
					fail(CaptureError.CAPTURE_NOT_STARTED, "MediaProjection consent data was missing")
				} else {
					startLearningMode(resultCode, resultData)
				}
			}
			ACTION_BEGIN -> beginSession()
			ACTION_FINISH -> finishSession()
			ACTION_TOGGLE -> if (_captureState.value == CaptureState.Capturing) finishSession() else beginSession()
			ACTION_REFRESH_OVERLAY -> {
				refreshOverlay()
				if (_captureState.value == CaptureState.Idle || _captureState.value is CaptureState.Error) stopSelf(startId)
			}
			ACTION_STOP -> stopLearningMode()
		}
		return START_NOT_STICKY
	}

	private fun startLearningMode(resultCode: Int, resultData: Intent) {
		if (_captureState.value != CaptureState.Idle && _captureState.value !is CaptureState.Error) return
		_captureState.value = CaptureState.Preparing
		intentionallyStopping = false
		if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) !=
			android.content.pm.PackageManager.PERMISSION_GRANTED) {
			fail(CaptureError.CAPTURE_NOT_STARTED, "Record audio permission was not granted")
			return
		}
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
			val configured = createAudioRecord(config)
			if (configured == null) {
				fail(CaptureError.CAPTURE_NOT_STARTED, "AudioRecord did not initialize")
				return
			}
			val recorder = configured.record
			captureFormat = configured.format
			audioCaptureBuffer = ShortArray(configured.readBufferSamples)
			audioRecord = recorder
			captureJob = serviceScope.launch(Dispatchers.IO) { processAudioStream(recorder) }
		} catch (security: SecurityException) {
			fail(CaptureError.CAPTURE_NOT_STARTED, security.message)
		} catch (error: Exception) {
			fail(CaptureError.CAPTURE_NOT_STARTED, error.message)
		}
	}

	private fun createAudioRecord(config: AudioPlaybackCaptureConfiguration): ConfiguredAudioRecord? {
		if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) !=
			android.content.pm.PackageManager.PERMISSION_GRANTED) return null
		val candidates = listOf(
			CaptureFormat(48_000, AudioFormat.CHANNEL_IN_STEREO, 2),
			CaptureFormat(48_000, AudioFormat.CHANNEL_IN_MONO, 1),
			CaptureFormat(44_100, AudioFormat.CHANNEL_IN_STEREO, 2),
			CaptureFormat(44_100, AudioFormat.CHANNEL_IN_MONO, 1),
			CaptureFormat(SAMPLE_RATE_HZ, CHANNEL_CONFIG, 1),
		)
		for (format in candidates) {
			val minimum = AudioRecord.getMinBufferSize(format.sampleRate, format.channelMask, AUDIO_FORMAT)
			if (minimum <= 0) continue
			val bufferSize = maxOf(minimum * 4, format.sampleRate * format.channelCount)
			val desiredReadSamples = maxOf(minimum / 2, format.sampleRate * format.channelCount / 10)
			val readBufferSamples = desiredReadSamples - (desiredReadSamples % format.channelCount)
			try {
				val record = AudioRecord.Builder()
					.setAudioFormat(AudioFormat.Builder()
						.setEncoding(AUDIO_FORMAT)
						.setSampleRate(format.sampleRate)
						.setChannelMask(format.channelMask)
						.build())
					.setAudioPlaybackCaptureConfig(config)
					.setBufferSizeInBytes(bufferSize)
					.build()
				if (record.state == AudioRecord.STATE_INITIALIZED) {
					Log.i("CaptureService", "Playback capture format=${format.sampleRate}Hz channels=${format.channelCount} buffer=$bufferSize")
					return ConfiguredAudioRecord(record, format, readBufferSamples)
				}
				record.release()
			} catch (error: Exception) {
				Log.w("CaptureService", "Capture format rejected: $format", error)
			}
		}
		return null
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
			serviceScope.launch { refreshOverlay() }
			while (!Thread.currentThread().isInterrupted) {
				val read = recorder.read(audioCaptureBuffer, 0, audioCaptureBuffer.size, AudioRecord.READ_BLOCKING)
				if (read > 0 && _captureState.value == CaptureState.Capturing) {
					val input = audioCaptureBuffer.copyOf(read)
					val mono = if (captureFormat.channelCount == 2) Pcm16.stereoToMono(input) else input
					val targetPcm = Pcm16.resampleLinear(mono, captureFormat.sampleRate, SAMPLE_RATE_HZ)
					ringBuffer.write(targetPcm, targetPcm.size)
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
		refreshOverlay()
	}

	private fun finishSession() {
		if (_captureState.value != CaptureState.Capturing) return
		_captureState.value = CaptureState.Processing
		val samples = ringBuffer.snapshot()
		val health = AudioDiagnostics.analyze(samples, SAMPLE_RATE_HZ)
		_latestSession.value = CapturedAudioSession(sessionIds.incrementAndGet(), samples, health)
		_captureState.value = CaptureState.Armed
		updateNotification()
		refreshOverlay()
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

	private fun refreshOverlay() {
		val learningModeActive = _captureState.value == CaptureState.Armed ||
			_captureState.value == CaptureState.Capturing ||
			_captureState.value == CaptureState.Processing
		if (learningModeActive && BuildConfig.OVERLAY_SUPPORTED && Settings.canDrawOverlays(this)) {
			_overlayStatus.value = OverlayStatus.Unavailable
			startService(Intent(this, OverlayService::class.java).setAction(OverlayService.ACTION_SHOW_OVERLAY))
		} else {
			_overlayStatus.value = if (learningModeActive) OverlayStatus.Disabled else OverlayStatus.Unavailable
			hideOverlay()
		}
	}

	private fun hideOverlay() {
		stopService(Intent(this, OverlayService::class.java))
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

	private data class CaptureFormat(val sampleRate: Int, val channelMask: Int, val channelCount: Int)
	private data class ConfiguredAudioRecord(val record: AudioRecord, val format: CaptureFormat, val readBufferSamples: Int)
}
