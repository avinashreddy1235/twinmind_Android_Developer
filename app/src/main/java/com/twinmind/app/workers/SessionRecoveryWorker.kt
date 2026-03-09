package com.twinmind.app.workers

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.twinmind.app.data.local.entity.RecordingSession
import com.twinmind.app.data.repository.RecordingRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class SessionRecoveryWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val recordingRepository: RecordingRepository
) : CoroutineWorker(context, params) {

    companion object {
        const val TAG = "SessionRecoveryWorker"
    }

    override suspend fun doWork(): Result {
        Log.d(TAG, "Checking for interrupted sessions...")

        return try {
            val interrupted = recordingRepository.getInterruptedSessions()

            for (session in interrupted) {
                Log.d(TAG, "Recovering session: ${session.id}")

                // Mark session as stopped
                recordingRepository.updateSessionStatus(
                    session.id,
                    RecordingSession.STATUS_STOPPED,
                    System.currentTimeMillis(),
                    session.duration
                )

                // Re-enqueue transcription for untranscribed chunks
                val untranscribed = recordingRepository.getUntranscribedChunks(session.id)
                for (chunk in untranscribed) {
                    val workRequest = OneTimeWorkRequestBuilder<ChunkUploadWorker>()
                        .setInputData(workDataOf("chunk_id" to chunk.id))
                        .build()
                    WorkManager.getInstance(applicationContext).enqueue(workRequest)
                }

                Log.d(TAG, "Session ${session.id}: ${untranscribed.size} chunks queued for transcription")
            }

            Log.d(TAG, "Recovery complete. ${interrupted.size} sessions recovered.")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Error recovering sessions", e)
            Result.failure()
        }
    }
}
