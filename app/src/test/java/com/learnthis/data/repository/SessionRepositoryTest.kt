package com.learnthis.data.repository

import com.learnthis.data.local.SessionDao
import com.learnthis.data.local.SessionEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SessionRepositoryTest {
	@get:Rule val temporaryFolder = TemporaryFolder()

	@Test fun deleteVideo_preservesAudioTranscriptAndLesson() = runTest {
		val root = temporaryFolder.newFolder("learning_sessions")
		val video = root.resolve("lesson.mp4").apply { writeBytes(byteArrayOf(1, 2, 3)) }
		val audio = root.resolve("lesson.wav").apply { writeBytes(byteArrayOf(4, 5, 6)) }
		val dao = FakeSessionDao(SessionEntity(
			id = 7,
			videoPath = video.absolutePath,
			audioPath = audio.absolutePath,
			segmentsJson = "[{\"text\":\"hello\"}]",
			segmentCount = 1,
		))
		val repository = SessionRepository(dao, root)

		repository.deleteVideo(7)

		val remaining = dao.getSessionById(7)
		assertNotNull(remaining)
		assertNull(remaining?.videoPath)
		assertEquals(1, remaining?.segmentCount)
		assertFalse(video.exists())
		assertTrue(audio.exists())
	}

	@Test fun deleteLesson_removesOwnedMediaAndRow_butNeverDeletesOutsideRoot() = runTest {
		val root = temporaryFolder.newFolder("learning_sessions")
		val video = root.resolve("lesson.mp4").apply { writeBytes(byteArrayOf(1)) }
		val outsideAudio = temporaryFolder.newFile("outside.wav").apply { writeBytes(byteArrayOf(2)) }
		val dao = FakeSessionDao(SessionEntity(
			id = 11,
			videoPath = video.absolutePath,
			audioPath = outsideAudio.absolutePath,
		))
		val repository = SessionRepository(dao, root)

		repository.deleteSession(11)

		assertNull(dao.getSessionById(11))
		assertFalse(video.exists())
		assertTrue(outsideAudio.exists())
	}

	private class FakeSessionDao(initial: SessionEntity) : SessionDao {
		private val sessions = MutableStateFlow(listOf(initial))
		override fun getAllSessions(): Flow<List<SessionEntity>> = sessions
		override suspend fun getSessionById(id: Long): SessionEntity? = sessions.value.firstOrNull { it.id == id }
		override fun observeSessionById(id: Long): Flow<SessionEntity?> = sessions.map { rows -> rows.firstOrNull { it.id == id } }
		override suspend fun insertSession(session: SessionEntity): Long {
			val id = session.id.takeIf { it > 0 } ?: ((sessions.value.maxOfOrNull { it.id } ?: 0) + 1)
			sessions.value = sessions.value + session.copy(id = id)
			return id
		}
		override suspend fun updateSession(session: SessionEntity) {
			sessions.value = sessions.value.map { if (it.id == session.id) session else it }
		}
		override suspend fun getAllSessionsOnce(): List<SessionEntity> = sessions.value
		override suspend fun deleteSession(id: Long) { sessions.value = sessions.value.filterNot { it.id == id } }
		override suspend fun deleteAllSessions() { sessions.value = emptyList() }
	}
}
