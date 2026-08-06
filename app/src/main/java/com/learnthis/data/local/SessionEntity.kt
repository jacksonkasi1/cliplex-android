package com.learnthis.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sessions")
data class SessionEntity(
 @PrimaryKey(autoGenerate = true)
 val id: Long = 0,
 val sourceLanguage: String,
 val targetLanguage: String,
 val durationMs: Long,
 val segmentCount: Int,
 val createdAt: Long = System.currentTimeMillis(),
)
