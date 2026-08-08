package com.jacksonkasi.cliplex.domain.model

/** The media presentation is independent from the transcript/translation display mode. */
enum class MediaView(val label: String) {
	VIDEO("Video"),
	AUDIO("Audio Only"),
}

@Suppress("UNUSED_PARAMETER")
fun defaultMediaView(hasVideo: Boolean): MediaView = MediaView.AUDIO

fun MediaView.availableWhen(hasVideo: Boolean): MediaView = if (this == MediaView.VIDEO && !hasVideo) MediaView.AUDIO else this
