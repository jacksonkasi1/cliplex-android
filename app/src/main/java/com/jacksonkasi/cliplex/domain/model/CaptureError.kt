package com.jacksonkasi.cliplex.domain.model

enum class CaptureError {
 CAPTURE_NOT_STARTED,
 MEDIA_PROJECTION_REVOKED,
 SOURCE_CAPTURE_BLOCKED,
 CAPTURED_SILENCE,
 AUDIO_FORMAT_INVALID,
 AUDIO_TOO_SHORT,
 NO_SPEECH_DETECTED,
 ASR_EMPTY_RESULT,
 MODEL_NOT_LOADED,
 TRANSLATION_FAILED,
 UNKNOWN
}

fun CaptureError.toUserMessage(): String = when (this) {
 CaptureError.SOURCE_CAPTURE_BLOCKED ->
 "The source app or this video blocked playback capture. Try a regular YouTube video or another application."
 CaptureError.CAPTURED_SILENCE ->
 "No playback audio was captured. Start capture after the video begins playing. If this continues, the source app or video may block capture."
 CaptureError.NO_SPEECH_DETECTED ->
 "Audio was captured, but spoken dialogue was not detected. Try a clip with clearer speech."
 CaptureError.MODEL_NOT_LOADED ->
 "The speech model could not process this audio. Retry or install the accuracy model."
 CaptureError.TRANSLATION_FAILED -> "Translation failed. Please try again."
 CaptureError.MEDIA_PROJECTION_REVOKED ->
 "Screen capture permission was revoked. Please restart Learning Mode."
 CaptureError.CAPTURE_NOT_STARTED -> "Audio capture did not start."
 CaptureError.AUDIO_FORMAT_INVALID -> "Audio format was invalid."
 CaptureError.AUDIO_TOO_SHORT -> "The captured audio is too short to process."
 CaptureError.ASR_EMPTY_RESULT -> "Speech recognition returned no result."
 else -> "An unexpected error occurred. Please try again."
}
