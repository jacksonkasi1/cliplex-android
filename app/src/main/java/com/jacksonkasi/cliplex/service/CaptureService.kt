package com.jacksonkasi.cliplex.service

import android.Manifest
import android.app.Activity
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
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.jacksonkasi.cliplex.BuildConfig
import com.jacksonkasi.cliplex.ClipLexApplication
import com.jacksonkasi.cliplex.MainActivity
import com.jacksonkasi.cliplex.audio.AudioDiagnostics
import com.jacksonkasi.cliplex.audio.Pcm16
import com.jacksonkasi.cliplex.data.local.SessionEntity
import com.jacksonkasi.cliplex.domain.model.AudioHealth
import com.jacksonkasi.cliplex.domain.model.CaptureError
import com.jacksonkasi.cliplex.media.CapturedMediaMuxer
import com.jacksonkasi.cliplex.media.ScreenRecordingController
import com.jacksonkasi.cliplex.overlay.OverlayService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.atomic.AtomicLong

class CaptureService : Service() {

	companion object {
		private const val TAG = "CaptureService"
		const val CHANNEL_ID = "cliplex_capture_channel"
		const val NOTIFICATION_ID = 1001
		const val ACTION_START = "com.jacksonkasi.cliplex.action.START_LEARNING_MODE"
		const val ACTION_BEGIN = "com.jacksonkasi.cliplex.action.BEGIN_CAPTURE"
		const val ACTION_FINISH = "com.jacksonkasi.cliplex.action.FINISH_CAPTURE"
		const val ACTION_TOGGLE = "com.jacksonkasi.cliplex.action.TOGGLE_CAPTURE"
		const val ACTION_STOP = "com.jacksonkasi.cliplex.action.STOP_LEARNING_MODE"
		const val ACTION_REFRESH_OVERLAY = "com.jacksonkasi.cliplex.action.REFRESH_OVERLAY"
		const val ACTION_ENABLE_OVERLAY = "com.jacksonkasi.cliplex.action.ENABLE_OVERLAY"
		const val ACTION_DISMISS_OVERLAY = "com.jacksonkasi.cliplex.action.DISMISS_OVERLAY"
		const val EXTRA_MEDIA_PROJECTION_RESULT_DATA = "media_projection_result_data"
		const val EXTRA_MEDIA_PROJECTION_RESULT_CODE = "media_projection_result_code"
		const val EXTRA_OPEN_LESSON_ID = "open_lesson_id"
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

		internal fun isProjectionConsentAccepted(resultCode: Int, hasResultData: Boolean): Boolean =
			resultCode == Activity.RESULT_OK && hasResultData
		internal fun isUnexpectedAudioReadError(
			readResult: Int,
			intentionallyStopping: Boolean,
			recorderIsCurrent: Boolean,
		): Boolean = readResult < 0 && !intentionallyStopping && recorderIsCurrent

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
		val videoPath: String?,
		val audioPath: String?,
		val finishedAtElapsedRealtimeNanos: Long,
	)

	private var mediaProjection: MediaProjection? = null
	private var projectionCallback: MediaProjection.Callback? = null
	@Volatile private var audioRecord: AudioRecord? = null
	private var captureJob: Job? = null
	private var screenRecorder: ScreenRecordingController? = null
	private var overlayDismissedByUser = false
	private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
	private val ringBuffer = RingBuffer(MAX_CAPTURE_SAMPLES)
	private var audioCaptureBuffer = ShortArray(0)
	private var captureFormat = CaptureFormat(SAMPLE_RATE_HZ, CHANNEL_CONFIG, 1)
	private var firstReadLogged = false
	private var nonZeroReadLogged = false
	@Volatile private var intentionallyStopping = false
	@Volatile private var captureVideoEnabled = true
	private var videoCaptureWarning: String? = null

	override fun onBind(intent: Intent?): IBinder? = null

	override fun onCreate() {
		super.onCreate()
		createNotificationChannel()
		val preferences = (application as ClipLexApplication).serviceLocator.preferencesRepository
		serviceScope.launch {
			preferences.captureVideo.collectLatest { captureVideoEnabled = it }
		}
	}

	override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
		when (intent?.action) {
			ACTION_START -> {
				Log.i(TAG, "Starting Learning Mode service; sdk=${Build.VERSION.SDK_INT}")
				val resultCode = intent.getIntExtra(EXTRA_MEDIA_PROJECTION_RESULT_CODE, Activity.RESULT_CANCELED)
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
				if (!isProjectionConsentAccepted(resultCode, resultData != null)) {
					fail(CaptureError.CAPTURE_NOT_STARTED, "MediaProjection consent data was missing")
				} else {
					startLearningMode(resultCode, requireNotNull(resultData))
				}
			}
			ACTION_BEGIN -> beginSession()
			ACTION_FINISH -> finishSession()
			ACTION_TOGGLE -> if (_captureState.value == CaptureState.Capturing) finishSession() else beginSession()
			ACTION_REFRESH_OVERLAY -> {
				refreshOverlay()
				if (_captureState.value == CaptureState.Idle || _captureState.value is CaptureState.Error) stopSelf(startId)
			}
			ACTION_ENABLE_OVERLAY -> {
				overlayDismissedByUser = false
				refreshOverlay()
			}
			ACTION_DISMISS_OVERLAY -> {
				overlayDismissedByUser = true
				_overlayStatus.value = OverlayStatus.Disabled
				hideOverlay()
			}
			ACTION_STOP -> stopLearningMode()
		}
		return START_NOT_STICKY
	}

	private fun startLearningMode(resultCode: Int, resultData: Intent) {
		if (_captureState.value != CaptureState.Idle && _captureState.value !is CaptureState.Error) return
		_captureState.value = CaptureState.Preparing
		overlayDismissedByUser = false
		intentionallyStopping = false
		if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) !=
			android.content.pm.PackageManager.PERMISSION_GRANTED) {
			fail(CaptureError.CAPTURE_NOT_STARTED, "Record audio permission was not granted")
			return
		}
		try {
			Log.i(TAG, "Creating MediaProjection")
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
			Log.i(TAG, "MediaProjection created; configuring playback capture")

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
			firstReadLogged = false
			nonZeroReadLogged = false
			Log.i(TAG, "AudioRecord initialized; starting capture thread")
			captureJob = serviceScope.launch(Dispatchers.IO) { processAudioStream(recorder) }
		} catch (security: SecurityException) {
			Log.e(TAG, "Playback capture rejected by Android", security)
			fail(CaptureError.CAPTURE_NOT_STARTED, security.message)
		} catch (error: Exception) {
			Log.e(TAG, "Playback capture initialization failed", error)
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
			if (minimum <= 0) {
				Log.w(TAG, "Unsupported capture format=$format minBuffer=$minimum")
				continue
			}
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
					Log.i(TAG, "Playback capture format=${format.sampleRate}Hz channels=${format.channelCount} buffer=$bufferSize")
					return ConfiguredAudioRecord(record, format, readBufferSamples)
				}
				record.release()
			} catch (error: Exception) {
				Log.w(TAG, "Capture format rejected: $format", error)
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
			serviceScope.launch {
				updateNotification()
				refreshOverlay()
			}
			while (!Thread.currentThread().isInterrupted) {
				val read = recorder.read(audioCaptureBuffer, 0, audioCaptureBuffer.size, AudioRecord.READ_BLOCKING)
				if (read > 0) {
					if (!firstReadLogged) {
						firstReadLogged = true
						Log.i(TAG, "Playback PCM reads started; samples=$read")
					}
					if (!nonZeroReadLogged && audioCaptureBuffer.asSequence().take(read).any { it.toInt() != 0 }) {
						nonZeroReadLogged = true
						Log.i(TAG, "Non-zero playback PCM observed")
					}
				}
				if (read > 0 && _captureState.value == CaptureState.Capturing) {
					val input = audioCaptureBuffer.copyOf(read)
					val mono = if (captureFormat.channelCount == 2) Pcm16.stereoToMono(input) else input
					val targetPcm = Pcm16.resampleLinear(mono, captureFormat.sampleRate, SAMPLE_RATE_HZ)
					ringBuffer.write(targetPcm, targetPcm.size)
					_capturedAudioDuration.value = ringBuffer.size * 1000L / SAMPLE_RATE_HZ
					if (ringBuffer.size >= MAX_CAPTURE_SAMPLES) serviceScope.launch { finishSession() }
				} else if (read < 0) {
					if (isUnexpectedAudioReadError(read, intentionallyStopping, audioRecord === recorder)) {
						serviceScope.launch { fail(CaptureError.CAPTURE_NOT_STARTED, "AudioRecord read error $read") }
					} else {
						Log.i(TAG, "Playback PCM loop ended during recorder release; read=$read")
					}
					return
				}
			}
		} catch (error: Exception) {
			if (!intentionallyStopping) serviceScope.launch { fail(CaptureError.CAPTURE_NOT_STARTED, error.message) }
		}
	}

	private fun beginSession() {
		if (_captureState.value != CaptureState.Armed) {
			Log.w(TAG, "Ignoring begin request in state=${_captureState.value}")
			return
		}
		ringBuffer.reset()
		_capturedAudioDuration.value = 0L
		videoCaptureWarning = null
		val projection = mediaProjection
		if (captureVideoEnabled && projection != null) {
			val directory = (application as ClipLexApplication).serviceLocator.sessionMediaDirectory
			val rawFile = File(directory, "capture-${System.currentTimeMillis()}.video-only.mp4")
			val controller = ScreenRecordingController(this, projection)
			controller.start(rawFile).fold(
				onSuccess = { screenRecorder = controller },
				onFailure = { error ->
					controller.release(deleteIncomplete = true)
					videoCaptureWarning = "Video capture was unavailable; this lesson was saved with audio only."
					Log.e(TAG, "Video capture unavailable; continuing with audio", error)
				},
			)
		}
		_captureState.value = CaptureState.Capturing
		Log.i(TAG, "Capture session started; video=${screenRecorder != null}")
		updateNotification()
		refreshOverlay()
	}

	private fun finishSession() {
		if (_captureState.value != CaptureState.Capturing) {
			Log.w(TAG, "Ignoring finish request in state=${_captureState.value}")
			return
		}
		_captureState.value = CaptureState.Processing
		hideOverlay()
		_overlayStatus.value = OverlayStatus.Unavailable
		val samples = ringBuffer.snapshot()
		val health = AudioDiagnostics.analyze(samples, SAMPLE_RATE_HZ)
		val finishedAtNanos = SystemClock.elapsedRealtimeNanos()
		val activeScreenRecorder = screenRecorder
		screenRecorder = null
		Log.i(
			TAG,
			"Capture session finished; samples=${samples.size} durationMs=${health.durationMs} " +
				"rms=${health.rmsLevel} peak=${health.peakAmplitude} zeros=${health.zeroSamplePercent}% error=${health.error}",
		)
		updateNotification()
		serviceScope.launch(Dispatchers.IO) {
			finalizeLesson(samples, health, finishedAtNanos, activeScreenRecorder)
		}
	}

	private suspend fun finalizeLesson(
		samples: ShortArray,
		health: AudioHealth,
		finishedAtNanos: Long,
		activeScreenRecorder: ScreenRecordingController?,
	) {
		val locator = (application as ClipLexApplication).serviceLocator
		val directory = locator.sessionMediaDirectory
		val mediaKey = "lesson-${System.currentTimeMillis()}-${sessionIds.incrementAndGet()}"
		val audioFile = File(directory, "$mediaKey.wav")
		val audioPath = runCatching {
			audioFile.writeBytes(Pcm16.wav(samples, SAMPLE_RATE_HZ))
			audioFile.absolutePath
		}.onFailure { Log.e(TAG, "Could not persist captured lesson audio", it) }.getOrNull()

		val videoOnly = activeScreenRecorder?.stop()?.getOrElse { error ->
			Log.e(TAG, "Captured video could not be finalized", error)
			null
		}
		val finalVideo = if (videoOnly != null && samples.isNotEmpty()) {
			val output = File(directory, "$mediaKey.mp4")
			CapturedMediaMuxer.muxPlaybackAudio(videoOnly, samples, SAMPLE_RATE_HZ, output)
				.onFailure { Log.e(TAG, "Playback audio could not be merged into captured video", it) }
				.getOrElse {
					videoOnly.delete()
					videoCaptureWarning = "Video could not be finalized with playback audio; this lesson was saved with audio only."
					null
				}
		} else videoOnly

		val databaseId = runCatching {
			locator.sessionRepository.insertSession(SessionEntity(
				title = "Captured lesson",
				durationMs = health.durationMs,
				videoPath = finalVideo?.absolutePath,
				audioPath = audioPath,
				processingState = if (health.isValid) "TRANSCRIBING" else "ERROR",
				captureError = health.error?.name ?: videoCaptureWarning,
			))
		}.onFailure { Log.e(TAG, "Could not create the learning session", it) }.getOrElse {
			finalVideo?.delete()
			audioFile.delete()
			fail(CaptureError.CAPTURE_NOT_STARTED, "Learning session could not be saved: ${it.message}")
			return
		}

		_latestSession.value = CapturedAudioSession(
			id = databaseId,
			samples = samples,
			health = health,
			videoPath = finalVideo?.absolutePath,
			audioPath = audioPath,
			finishedAtElapsedRealtimeNanos = finishedAtNanos,
		)
		withContext(Dispatchers.Main.immediate) {
			openLesson(databaseId)
			intentionallyStopping = true
			releaseResources(stopProjection = true, deleteIncompleteVideo = false)
			_captureState.value = CaptureState.Idle
			_capturedAudioDuration.value = 0L
			stopForeground(STOP_FOREGROUND_REMOVE)
			stopSelf()
		}
	}

	private fun openLesson(sessionId: Long) {
		startActivity(Intent(this, MainActivity::class.java).apply {
			addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
			putExtra(EXTRA_OPEN_LESSON_ID, sessionId)
		})
	}

	private fun fail(error: CaptureError, detail: String?) {
		Log.e(TAG, "Learning Mode failed: error=$error detail=${detail ?: "none"}")
		_captureState.value = CaptureState.Error(error, detail)
		releaseResources(stopProjection = true, deleteIncompleteVideo = true)
		stopForeground(STOP_FOREGROUND_REMOVE)
		stopSelf()
	}

	private fun stopLearningMode() {
		if (_captureState.value == CaptureState.Capturing) {
			finishSession()
			return
		}
		intentionallyStopping = true
		releaseResources(stopProjection = true, deleteIncompleteVideo = true)
		_captureState.value = CaptureState.Idle
		_capturedAudioDuration.value = 0L
		ringBuffer.reset()
		hideOverlay()
		stopForeground(STOP_FOREGROUND_REMOVE)
		stopSelf()
	}

	private fun releaseResources(stopProjection: Boolean, deleteIncompleteVideo: Boolean) {
		screenRecorder?.release(deleteIncomplete = deleteIncompleteVideo)
		screenRecorder = null
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
		if (learningModeActive && !overlayDismissedByUser && BuildConfig.OVERLAY_SUPPORTED && Settings.canDrawOverlays(this)) {
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
			CHANNEL_ID, "ClipLex capture", NotificationManager.IMPORTANCE_LOW,
		))
	}

	private fun updateNotification() {
		getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, buildNotification(_captureState.value))
	}

	private fun buildNotification(state: CaptureState): Notification {
		val builder = NotificationCompat.Builder(this, CHANNEL_ID)
			.setContentTitle("ClipLex")
			.setSmallIcon(android.R.drawable.ic_btn_speak_now)
			.setOngoing(true)
			.setPriority(NotificationCompat.PRIORITY_LOW)
			.setContentIntent(PendingIntent.getActivity(
				this,
				0,
				Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
				PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
			))
		when (state) {
			CaptureState.Armed -> builder
				.setContentText("Ready — play a video, then tap Start capture")
				.addAction(0, "Start capture", servicePendingIntent(ACTION_BEGIN, 1))
				.addAction(0, "Stop learning mode", servicePendingIntent(ACTION_STOP, 3))
			CaptureState.Capturing -> builder
				.setContentText(if (screenRecorder != null) "Capturing your learning clip" else "Capturing playback audio")
				.addAction(0, "Finish", finishAndOpenPendingIntent())
			CaptureState.Processing -> builder.setContentText("Preparing your lesson…")
			CaptureState.Preparing -> builder
				.setContentText("Preparing playback capture")
				.addAction(0, "Stop", servicePendingIntent(ACTION_STOP, 3))
			is CaptureState.Error -> builder.setContentText("Capture error")
			CaptureState.Idle -> builder.setContentText("Learning mode stopped")
		}
		return builder.build()
	}

	private fun servicePendingIntent(action: String, requestCode: Int): PendingIntent = PendingIntent.getService(
		this, requestCode, Intent(this, CaptureService::class.java).setAction(action),
		PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
	)

	private fun finishAndOpenPendingIntent(): PendingIntent = PendingIntent.getActivity(
		this,
		2,
		Intent(this, MainActivity::class.java).apply {
			action = MainActivity.ACTION_FINISH_CAPTURE_AND_OPEN
			addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
		},
		PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
	)

	override fun onDestroy() {
		intentionallyStopping = true
		releaseResources(stopProjection = true, deleteIncompleteVideo = true)
		hideOverlay()
		if (_captureState.value !is CaptureState.Error) _captureState.value = CaptureState.Idle
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
