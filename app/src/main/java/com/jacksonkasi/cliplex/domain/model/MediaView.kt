package com.jacksonkasi.cliplex.domain.model

/** The media presentation is independent from the transcript/translation display mode. */
enum class MediaView(val label: String) {
    VIDEO("Video"),
    AUDIO("Audio Only"),
}

/** Open captured video by default; fall back to audio when the lesson has no retained video. */
fun defaultMediaView(hasVideo: Boolean): MediaView = if (hasVideo) MediaView.VIDEO else MediaView.AUDIO

fun MediaView.availableWhen(hasVideo: Boolean): MediaView =
    if (this == MediaView.VIDEO && !hasVideo) MediaView.AUDIO else this
