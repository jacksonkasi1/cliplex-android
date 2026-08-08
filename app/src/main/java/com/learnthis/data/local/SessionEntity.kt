package com.learnthis.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sessions")
data class SessionEntity(
	@PrimaryKey(autoGenerate = true)
	val id: Long = 0,
	val title: String = "Captured lesson",
	val sourceLanguage: String = "",
	val targetLanguage: String = "",
	val durationMs: Long = 0L,
	val segmentCount: Int = 0,
	val videoPath: String? = null,
	val audioPath: String? = null,
	val segmentsJson: String = "[]",
	val processingState: String = "PREPARING",
	val captureError: String? = null,
	val createdAt: Long = System.currentTimeMillis(),
)
