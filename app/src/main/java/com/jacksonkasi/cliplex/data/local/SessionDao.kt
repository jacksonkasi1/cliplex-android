package com.jacksonkasi.cliplex.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface SessionDao {
	@Query("SELECT * FROM sessions ORDER BY createdAt DESC")
	fun getAllSessions(): Flow<List<SessionEntity>>
	@Query("SELECT * FROM sessions WHERE id = :id")
	suspend fun getSessionById(id: Long): SessionEntity?
	@Query("SELECT * FROM sessions WHERE id = :id")
	fun observeSessionById(id: Long): Flow<SessionEntity?>
	@Insert
	suspend fun insertSession(session: SessionEntity): Long
	@Update
	suspend fun updateSession(session: SessionEntity)
	@Query("SELECT * FROM sessions")
	suspend fun getAllSessionsOnce(): List<SessionEntity>
	@Query("DELETE FROM sessions WHERE id = :id")
	suspend fun deleteSession(id: Long)
	@Query("DELETE FROM sessions")
	suspend fun deleteAllSessions()
}
