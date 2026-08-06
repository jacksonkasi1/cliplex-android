package com.learnthis.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Binder
import android.os.IBinder
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class CaptureService : Service() {

 companion object {
 const val CHANNEL_ID = "learn_this_capture_channel"
 const val NOTIFICATION_ID = 1001
 const val ACTION_START = "com.learnthis.action.START_CAPTURE"
 const val ACTION_STOP = "com.learnthis.action.STOP_CAPTURE"
 const val EXTRA_MEDIA_PROJECTION_RESULT_DATA = "media_projection_result_data"
 const val EXTRA_MEDIA_PROJECTION_RESULT_CODE = "media_projection_result_code"
 const val SAMPLE_RATE_HZ = 16000
 const val CHANNEL_CONFIG = android.media.AudioFormat.CHANNEL_IN_MONO
 const val AUDIO_FORMAT = android.media.AudioFormat.ENCODING_PCM_16BIT
 }

 private val binder = LocalBinder()
 private var mediaProjection: MediaProjection? = null
 private var audioRecord: AudioRecord? = null
 private var captureJob: Job? = null
 private val serviceScope = CoroutineScope(Dispatchers.Main + Job())
 private val _captureState = MutableStateFlow<CaptureState>(CaptureState.Idle)
 val captureState: StateFlow<CaptureState> = _captureState
 private val _capturedAudioDuration = MutableStateFlow(0L)
 val capturedAudioDuration: StateFlow<Long> = _capturedAudioDuration

 enum class CaptureState {
 Idle, RequestingPermission, Capturing, Stopping, Error
 }

 inner class LocalBinder : Binder() {
 fun getService(): CaptureService = this@CaptureService
 }

 override fun onBind(intent: Intent?): IBinder = binder

 override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
 when (intent?.action) {
 ACTION_START -> {
 val resultCode = intent.getIntExtra(EXTRA_MEDIA_PROJECTION_RESULT_CODE, -1)
 val resultData = intent.getParcelableExtra<Intent>(EXTRA_MEDIA_PROJECTION_RESULT_DATA)
 if (resultData != null && resultCode != -1) {
 startForeground(NOTIFICATION_ID, buildNotification("Preparing capture..."))
 startCapture(resultCode, resultData)
 } else {
 _captureState.value = CaptureState.Error
 stopSelf()
 }
 }
 ACTION_STOP -> stopCapture()
 }
 return START_NOT_STICKY
 }

 private fun startCapture(resultCode: Int, resultData: Intent) {
 _captureState.value = CaptureState.RequestingPermission
 val projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
 mediaProjection = projectionManager.getMediaProjection(resultCode, resultData)
 val config = AudioPlaybackCaptureConfiguration.Builder(mediaProjection!!)
 .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
 .addMatchingUsage(AudioAttributes.USAGE_GAME)
 .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
 .build()
 val minBufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE_HZ, CHANNEL_CONFIG, AUDIO_FORMAT)
 val bufferSize = minBufferSize * 4
 try {
 audioRecord = AudioRecord.Builder()
 .setAudioFormat(
 android.media.AudioFormat.Builder()
 .setEncoding(AUDIO_FORMAT)
 .setSampleRate(SAMPLE_RATE_HZ)
 .setChannelMask(CHANNEL_CONFIG)
 .build()
 )
 .setBufferSizeInBytes(bufferSize)
 .setAudioPlaybackCaptureConfig(config)
 .build()
 audioRecord?.startRecording()
 _captureState.value = CaptureState.Capturing
 startForeground(NOTIFICATION_ID, buildNotification("Capturing audio..."))
 captureJob = serviceScope.launch(Dispatchers.IO) {
 processAudioStream(bufferSize)
 }
 } catch (e: SecurityException) {
 _captureState.value = CaptureState.Error
 stopSelf()
 } catch (e: Exception) {
 _captureState.value = CaptureState.Error
 stopSelf()
 }
 }

 private fun processAudioStream(bufferSize: Int) {
 val buffer = ShortArray(bufferSize / 2)
 val ringBuffer = RingBuffer()
 while (_captureState.value == CaptureState.Capturing) {
 val readSize = audioRecord?.read(buffer, 0, buffer.size) ?: 0
 if (readSize > 0) {
 ringBuffer.write(buffer, readSize)
 _capturedAudioDuration.value = (ringBuffer.totalSamples / SAMPLE_RATE_HZ) * 1000L
 }
 }
 }

 fun stopCapture() {
 _captureState.value = CaptureState.Stopping
 captureJob?.cancel()
 try {
 audioRecord?.stop()
 audioRecord?.release()
 } catch (e: Exception) { }
 audioRecord = null
 try {
 mediaProjection?.stop()
 } catch (e: Exception) { }
 mediaProjection = null
 _captureState.value = CaptureState.Idle
 stopForeground(STOP_FOREGROUND_REMOVE)
 stopSelf()
 }

 override fun onDestroy() {
 super.onDestroy()
 stopCapture()
 serviceScope.cancel()
 }

 private fun buildNotification(content: String): Notification {
 return NotificationCompat.Builder(this, CHANNEL_ID)
 .setContentTitle("Learn This")
 .setContentText(content)
 .setSmallIcon(android.R.drawable.ic_btn_speak_now)
 .setOngoing(true)
 .setPriority(NotificationCompat.PRIORITY_LOW)
 .build()
 }

 private class RingBuffer {
 private val capacity = 480000
 private val buffer = ShortArray(capacity)
 private val lock = Any()
 var writePos = 0
 var totalSamples = 0
 private var filled = 0

 fun write(data: ShortArray, count: Int) {
 synchronized(lock) {
 for (i in 0 until count) {
 buffer[writePos] = data[i]
 writePos = (writePos + 1) % capacity
 if (filled < capacity) filled++
 totalSamples++
 }
 }
 }

 fun readChunk(target: ShortArray, offset: Int, count: Int): Int {
 synchronized(lock) {
 val available = filled.coerceAtMost(count)
 for (i in 0 until available) {
 val readPos = (writePos - filled + i + capacity) % capacity
 target[offset + i] = buffer[readPos]
 }
 return available
 }
 }
 }
}
