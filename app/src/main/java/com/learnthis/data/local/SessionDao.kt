package com.learnthis.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SessionDao {
	@Query("SELECT * FROM sessions ORDER BY createdAt DESC")
	fun getAllSessions(): Flow<List<SessionEntity>>
	@Query("SELECT * FROM sessions WHERE id = :id")
	suspend fun getSessionById(id: Long): SessionEntity?
	@Insert
	suspend fun insertSession(session: SessionEntity): Long
	@Query("DELETE FROM sessions WHERE id = :id")
	suspend fun deleteSession(id: Long)
	@Query("DELETE FROM sessions")
	suspend fun deleteAllSessions()
}
