package com.twinmind.app.workers

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.twinmind.app.data.repository.TranscriptionRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class ChunkUploadWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val transcriptionRepository: TranscriptionRepository
) : CoroutineWorker(context, params) {

    companion object {
        const val TAG = "ChunkUploadWorker"
        const val KEY_CHUNK_ID = "chunk_id"
    }

    override suspend fun doWork(): Result {
        val chunkId = inputData.getLong(KEY_CHUNK_ID, -1L)
        if (chunkId == -1L) {
            Log.e(TAG, "No chunk ID provided")
            return Result.failure()
        }

        Log.d(TAG, "Processing chunk: $chunkId")

        return try {
            val result = transcriptionRepository.transcribeChunk(chunkId)
            if (result.isSuccess) {
                Log.d(TAG, "Chunk $chunkId transcribed successfully")
                Result.success()
            } else {
                Log.e(TAG, "Chunk $chunkId transcription failed", result.exceptionOrNull())
                if (runAttemptCount < 3) {
                    Result.retry()
                } else {
                    Result.failure()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error transcribing chunk $chunkId", e)
            if (runAttemptCount < 3) {
                Result.retry()
            } else {
                Result.failure()
            }
        }
    }
}
