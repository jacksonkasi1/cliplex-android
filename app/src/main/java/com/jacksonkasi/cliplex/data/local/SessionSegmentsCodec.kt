package com.jacksonkasi.cliplex.data.local

import com.jacksonkasi.cliplex.domain.model.TranscriptionSegment
import org.json.JSONArray
import org.json.JSONObject

object SessionSegmentsCodec {
	fun encode(segments: List<TranscriptionSegment>): String = JSONArray().apply {
		segments.forEach { segment ->
			put(JSONObject().apply {
				put("text", segment.text)
				put("startMs", segment.startTimeMs)
				put("endMs", segment.endTimeMs)
				put("language", segment.language)
				put("translatedText", segment.translatedText ?: JSONObject.NULL)
				put("words", JSONArray().apply {
					segment.words.forEach { word ->
						put(JSONObject().apply {
							put("text", word.text)
							put("startMs", word.startTimeMs ?: JSONObject.NULL)
							put("confidence", word.confidence ?: JSONObject.NULL)
						})
					}
				})
			})
		}
	}.toString()

	fun decode(json: String): List<TranscriptionSegment> = runCatching {
		val array = JSONArray(json)
		buildList(array.length()) {
			for (index in 0 until array.length()) {
				val item = array.getJSONObject(index)
				add(TranscriptionSegment(
					text = item.optString("text"),
					startTimeMs = item.optLong("startMs"),
					endTimeMs = item.optLong("endMs"),
					language = item.optString("language"),
					translatedText = item.optString("translatedText").takeUnless { it.isBlank() || it == "null" },
					words = item.optJSONArray("words")?.let { words ->
						buildList(words.length()) {
							for (wordIndex in 0 until words.length()) {
								val word = words.getJSONObject(wordIndex)
								add(com.jacksonkasi.cliplex.domain.model.TranscriptWord(
									text = word.optString("text"),
									startTimeMs = if (word.isNull("startMs")) null else word.optLong("startMs"),
									confidence = if (word.isNull("confidence")) null else word.optDouble("confidence").toFloat(),
								))
							}
						}
					}.orEmpty(),
				))
			}
		}
	}.getOrDefault(emptyList())
}
