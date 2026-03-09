package com.twinmind.app.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "audio_chunks",
    foreignKeys = [
        ForeignKey(
            entity = RecordingSession::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["sessionId"])]
)
data class AudioChunk(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val sessionId: Long,
    val filePath: String,
    val chunkIndex: Int,
    val createdAt: Long = System.currentTimeMillis(),
    val duration: Long = 0,
    val uploaded: Boolean = false,
    val transcribed: Boolean = false,
    val transcriptionFailed: Boolean = false
)
