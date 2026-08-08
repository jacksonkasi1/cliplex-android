package com.learnthis.data.repository

import com.learnthis.data.local.SessionDao
import com.learnthis.data.local.SessionEntity
import kotlinx.coroutines.flow.Flow
import java.io.File

class SessionRepository(
	private val sessionDao: SessionDao,
	private val mediaDirectory: File,
) {

 fun getAllSessions(): Flow<List<SessionEntity>> = sessionDao.getAllSessions()

 suspend fun getSessionById(id: Long): SessionEntity? = sessionDao.getSessionById(id)

 fun observeSessionById(id: Long): Flow<SessionEntity?> = sessionDao.observeSessionById(id)

 suspend fun insertSession(session: SessionEntity): Long = sessionDao.insertSession(session)

 suspend fun updateSession(session: SessionEntity) = sessionDao.updateSession(session)

 suspend fun deleteVideo(id: Long) {
	val session = sessionDao.getSessionById(id) ?: return
	deleteOwnedMedia(session.videoPath)
	sessionDao.updateSession(session.copy(videoPath = null))
 }

 suspend fun deleteSession(id: Long) {
	val session = sessionDao.getSessionById(id)
	deleteOwnedMedia(session?.videoPath)
	deleteOwnedMedia(session?.audioPath)
	sessionDao.deleteSession(id)
 }

 suspend fun deleteAllSessions() {
	sessionDao.getAllSessionsOnce().forEach { session ->
		deleteOwnedMedia(session.videoPath)
		deleteOwnedMedia(session.audioPath)
	}
	sessionDao.deleteAllSessions()
 }

 private fun deleteOwnedMedia(path: String?) {
	if (path.isNullOrBlank()) return
	val root = runCatching { mediaDirectory.canonicalFile }.getOrNull() ?: return
	val file = runCatching { File(path).canonicalFile }.getOrNull() ?: return
	if (file.path.startsWith(root.path + File.separator)) file.delete()
 }
}
