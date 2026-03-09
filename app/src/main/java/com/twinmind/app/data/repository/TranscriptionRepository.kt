package com.twinmind.app.data.repository

import com.twinmind.app.data.local.dao.AudioChunkDao
import com.twinmind.app.data.local.dao.TranscriptChunkDao
import com.twinmind.app.data.local.entity.TranscriptChunk
import com.twinmind.app.data.remote.TranscriptionService
import kotlinx.coroutines.flow.Flow
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TranscriptionRepository @Inject constructor(
    private val transcriptChunkDao: TranscriptChunkDao,
    private val audioChunkDao: AudioChunkDao,
    private val transcriptionService: TranscriptionService
) {
    fun observeTranscripts(sessionId: Long): Flow<List<TranscriptChunk>> =
        transcriptChunkDao.observeTranscriptsBySession(sessionId)

    suspend fun getFullTranscript(sessionId: Long): String =
        transcriptChunkDao.getFullTranscript(sessionId) ?: ""

    suspend fun transcribeChunk(chunkId: Long): Result<String> {
        val chunk = audioChunkDao.getChunkById(chunkId)
            ?: return Result.failure(Exception("Chunk not found: $chunkId"))

        return try {
            val audioFile = File(chunk.filePath)
            if (!audioFile.exists()) {
                return Result.failure(Exception("Audio file not found: ${chunk.filePath}"))
            }

            val text = transcriptionService.transcribe(audioFile)

            // Save transcript
            transcriptChunkDao.insert(
                TranscriptChunk(
                    sessionId = chunk.sessionId,
                    chunkIndex = chunk.chunkIndex,
                    text = text
                )
            )

            // Mark chunk as transcribed
            audioChunkDao.markTranscribed(chunkId)

            Result.success(text)
        } catch (e: Exception) {
            audioChunkDao.markTranscriptionFailed(chunkId)
            Result.failure(e)
        }
    }

    suspend fun retryFailedChunks(): Int {
        val failedChunks = audioChunkDao.getFailedChunks()
        var successCount = 0

        for (chunk in failedChunks) {
            val result = transcribeChunk(chunk.id)
            if (result.isSuccess) successCount++
        }

        return successCount
    }

    suspend fun getTranscriptsBySession(sessionId: Long): List<TranscriptChunk> =
        transcriptChunkDao.getTranscriptsBySession(sessionId)
}
