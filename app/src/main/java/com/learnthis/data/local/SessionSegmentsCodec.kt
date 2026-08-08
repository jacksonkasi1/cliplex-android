package com.learnthis.data.local

import com.learnthis.domain.model.TranscriptionSegment
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
				))
			}
		}
	}.getOrDefault(emptyList())
}
