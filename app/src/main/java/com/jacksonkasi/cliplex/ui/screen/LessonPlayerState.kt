package com.jacksonkasi.cliplex.ui.screen

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.widget.VideoView
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.io.File

@Stable
internal class LessonPlayerState(fallbackDurationMs: Long) {
    var positionMs by mutableLongStateOf(0L)
        private set
    var durationMs by mutableLongStateOf(fallbackDurationMs.coerceAtLeast(0L))
        private set
    var isPlaying by mutableStateOf(false)
        private set
    var isPrepared by mutableStateOf(false)
        private set
    var playbackError by mutableStateOf<String?>(null)
        private set
    var videoAspectRatio by mutableStateOf<Float?>(null)
        private set

    private var videoView: VideoView? = null
    private var audioPlayer: MediaPlayer? = null
    private var boundVideoSource: String? = null
    private var boundAudioSource: String? = null
    private var resumeAfterSwitch = false

    fun prepareForMediaSwitch() {
        refreshPosition()
        resumeAfterSwitch = resumeAfterSwitch || isPlaying
    }

    fun bindVideo(view: VideoView, source: String) {
        if (videoView === view && boundVideoSource == source) return
        releaseAudio()
        if (videoView !== view) videoView?.stopPlayback()
        videoView = view
        boundVideoSource = source
        isPrepared = false
        isPlaying = false
        playbackError = null
        view.setOnPreparedListener { mediaPlayer ->
            durationMs = mediaPlayer.duration.toLong().takeIf { it > 0L } ?: durationMs
            if (mediaPlayer.videoWidth > 0 && mediaPlayer.videoHeight > 0) {
                videoAspectRatio = mediaPlayer.videoWidth.toFloat() / mediaPlayer.videoHeight.toFloat()
            }
            isPrepared = true
            view.seekTo(positionMs.coerceAtLeast(1L).coerceAtMost(durationMs).toInt())
            if (resumeAfterSwitch) {
                view.start()
                isPlaying = true
            }
            resumeAfterSwitch = false
        }
        view.setOnCompletionListener {
            isPlaying = false
            positionMs = durationMs
        }
        view.setOnErrorListener { _, _, _ ->
            isPrepared = false
            isPlaying = false
            playbackError = "The captured video could not be opened."
            true
        }
        runCatching { view.setVideoURI(mediaUri(source)) }
            .onFailure {
                playbackError = "The captured video could not be opened."
                isPrepared = false
            }
    }

    fun bindAudio(context: Context, source: String) {
        if (boundAudioSource == source && audioPlayer != null) return
        videoView?.stopPlayback()
        videoView = null
        boundVideoSource = null
        releaseAudio()
        boundAudioSource = source
        isPrepared = false
        isPlaying = false
        playbackError = null
        val player = MediaPlayer()
        audioPlayer = player
        player.setAudioAttributes(
            AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .build(),
        )
        player.setOnPreparedListener {
            durationMs = it.duration.toLong().takeIf { value -> value > 0L } ?: durationMs
            isPrepared = true
            it.seekTo(positionMs.coerceAtMost(durationMs), MediaPlayer.SEEK_CLOSEST)
            if (resumeAfterSwitch) {
                it.start()
                isPlaying = true
            }
            resumeAfterSwitch = false
        }
        player.setOnCompletionListener {
            isPlaying = false
            positionMs = durationMs
        }
        player.setOnErrorListener { _, _, _ ->
            isPrepared = false
            isPlaying = false
            playbackError = "The captured audio could not be opened."
            true
        }
        runCatching {
            val uri = mediaUri(source)
            if (uri.scheme.equals("file", ignoreCase = true)) {
                player.setDataSource(requireNotNull(uri.path))
            } else {
                player.setDataSource(context, uri)
            }
            player.prepareAsync()
        }.onFailure {
            playbackError = "The captured audio could not be opened."
            isPrepared = false
        }
    }

    fun togglePlayback() {
        if (!isPrepared) return
        if (isPlaying) pause() else play()
    }

    fun play() {
        if (!isPrepared) return
        runCatching {
            if (positionMs >= durationMs && durationMs > 0L) seekTo(0L)
            videoView?.start() ?: audioPlayer?.start()
            isPlaying = true
        }.onFailure {
            isPlaying = false
            playbackError = "Playback could not start."
        }
    }

    fun pause() {
        runCatching {
            videoView?.takeIf(VideoView::isPlaying)?.pause()
            audioPlayer?.takeIf(MediaPlayer::isPlaying)?.pause()
        }
        isPlaying = false
        refreshPosition()
    }

    fun seekBy(deltaMs: Long) = seekTo(positionMs + deltaMs)

    fun seekTo(targetMs: Long) {
        if (!isPrepared) return
        val clamped = targetMs.coerceIn(0L, durationMs.coerceAtLeast(0L))
        runCatching {
            videoView?.seekTo(clamped.toInt())
            audioPlayer?.seekTo(clamped, MediaPlayer.SEEK_CLOSEST)
            positionMs = clamped
        }.onFailure { playbackError = "Could not move to that point in the lesson." }
    }

    fun refreshPosition() {
        runCatching {
            when {
                videoView != null -> {
                    positionMs = videoView?.currentPosition?.toLong()?.coerceAtLeast(0L) ?: positionMs
                    isPlaying = videoView?.isPlaying == true
                }
                audioPlayer != null && isPrepared -> {
                    positionMs = audioPlayer?.currentPosition?.toLong()?.coerceAtLeast(0L) ?: positionMs
                    isPlaying = audioPlayer?.isPlaying == true
                }
            }
        }
    }

    fun release() {
        runCatching { videoView?.stopPlayback() }
        videoView = null
        boundVideoSource = null
        releaseAudio()
        isPrepared = false
        isPlaying = false
        resumeAfterSwitch = false
    }

    private fun releaseAudio() {
        runCatching { audioPlayer?.release() }
        audioPlayer = null
        boundAudioSource = null
    }
}

internal fun mediaUri(source: String): Uri {
    val parsed = Uri.parse(source)
    return if (parsed.scheme.isNullOrBlank()) Uri.fromFile(File(source)) else parsed
}
