package com.twinmind.app.data.repository

import com.twinmind.app.data.local.dao.AudioChunkDao
import com.twinmind.app.data.local.dao.RecordingSessionDao
import com.twinmind.app.data.local.entity.AudioChunk
import com.twinmind.app.data.local.entity.RecordingSession
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RecordingRepository @Inject constructor(
    private val sessionDao: RecordingSessionDao,
    private val chunkDao: AudioChunkDao
) {
    fun getAllSessions(): Flow<List<RecordingSession>> = sessionDao.getAllSessions()

    fun observeSession(sessionId: Long): Flow<RecordingSession?> = sessionDao.observeSession(sessionId)

    suspend fun createSession(title: String = ""): Long {
        val session = RecordingSession(title = title)
        return sessionDao.insert(session)
    }

    suspend fun getSessionById(id: Long): RecordingSession? = sessionDao.getSessionById(id)

    suspend fun updateSessionStatus(sessionId: Long, status: String, endTime: Long? = null, duration: Long = 0) {
        sessionDao.updateSessionStatus(sessionId, status, endTime, duration)
    }

    suspend fun updateTitle(sessionId: Long, title: String) {
        sessionDao.updateTitle(sessionId, title)
    }

    suspend fun updateSummaryStatus(sessionId: Long, status: String) {
        sessionDao.updateSummaryStatus(sessionId, status)
    }

    suspend fun insertChunk(chunk: AudioChunk): Long {
        val id = chunkDao.insert(chunk)
        updateChunkCounts(chunk.sessionId)
        return id
    }

    suspend fun getChunksBySession(sessionId: Long): List<AudioChunk> =
        chunkDao.getChunksBySession(sessionId)

    fun observeChunksBySession(sessionId: Long): Flow<List<AudioChunk>> =
        chunkDao.observeChunksBySession(sessionId)

    suspend fun getUntranscribedChunks(sessionId: Long): List<AudioChunk> =
        chunkDao.getUntranscribedChunks(sessionId)

    suspend fun markChunkTranscribed(chunkId: Long) {
        val chunk = chunkDao.getChunkById(chunkId)
        chunkDao.markTranscribed(chunkId)
        chunk?.let { updateChunkCounts(it.sessionId) }
    }

    suspend fun markChunkTranscriptionFailed(chunkId: Long) {
        chunkDao.markTranscriptionFailed(chunkId)
    }

    suspend fun getFailedChunks(): List<AudioChunk> = chunkDao.getFailedChunks()

    suspend fun getMaxChunkIndex(sessionId: Long): Int = chunkDao.getMaxChunkIndex(sessionId) ?: -1

    suspend fun getInterruptedSessions(): List<RecordingSession> =
        sessionDao.getSessionsByStatus(RecordingSession.STATUS_RECORDING) +
                sessionDao.getSessionsByStatus(RecordingSession.STATUS_PAUSED)

    suspend fun deleteSession(session: RecordingSession) = sessionDao.delete(session)

    private suspend fun updateChunkCounts(sessionId: Long) {
        val total = chunkDao.getChunkCount(sessionId)
        val transcribed = chunkDao.getTranscribedChunkCount(sessionId)
        sessionDao.updateChunkCounts(sessionId, total, transcribed)
    }
}
