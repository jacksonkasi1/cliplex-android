package com.learnthis.data.repository

import com.learnthis.data.local.SessionDao
import com.learnthis.data.local.SessionEntity
import kotlinx.coroutines.flow.Flow

class SessionRepository(private val sessionDao: SessionDao) {

 fun getAllSessions(): Flow<List<SessionEntity>> = sessionDao.getAllSessions()

 suspend fun getSessionById(id: Long): SessionEntity? = sessionDao.getSessionById(id)

 suspend fun insertSession(session: SessionEntity): Long = sessionDao.insertSession(session)

 suspend fun deleteSession(id: Long) = sessionDao.deleteSession(id)

 suspend fun deleteAllSessions() = sessionDao.deleteAllSessions()
}
