package com.twinmind.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "recording_sessions")
data class RecordingSession(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String = "",
    val startTime: Long = System.currentTimeMillis(),
    val endTime: Long? = null,
    val status: String = STATUS_RECORDING,
    val duration: Long = 0,
    val totalChunks: Int = 0,
    val transcribedChunks: Int = 0,
    val summaryStatus: String = SUMMARY_NONE
) {
    companion object {
        const val STATUS_RECORDING = "recording"
        const val STATUS_PAUSED = "paused"
        const val STATUS_STOPPED = "stopped"
        const val STATUS_COMPLETED = "completed"

        const val SUMMARY_NONE = "none"
        const val SUMMARY_GENERATING = "generating"
        const val SUMMARY_COMPLETED = "completed"
        const val SUMMARY_FAILED = "failed"
    }
}
