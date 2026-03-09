package com.twinmind.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.twinmind.app.data.local.dao.AudioChunkDao
import com.twinmind.app.data.local.dao.RecordingSessionDao
import com.twinmind.app.data.local.dao.SummaryDao
import com.twinmind.app.data.local.dao.TranscriptChunkDao
import com.twinmind.app.data.local.entity.AudioChunk
import com.twinmind.app.data.local.entity.Converters
import com.twinmind.app.data.local.entity.MeetingSummary
import com.twinmind.app.data.local.entity.RecordingSession
import com.twinmind.app.data.local.entity.TranscriptChunk

@Database(
    entities = [
        RecordingSession::class,
        AudioChunk::class,
        TranscriptChunk::class,
        MeetingSummary::class
    ],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class TwinMindDatabase : RoomDatabase() {
    abstract fun recordingSessionDao(): RecordingSessionDao
    abstract fun audioChunkDao(): AudioChunkDao
    abstract fun transcriptChunkDao(): TranscriptChunkDao
    abstract fun summaryDao(): SummaryDao
}
