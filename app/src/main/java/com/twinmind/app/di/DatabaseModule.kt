package com.twinmind.app.di

import android.content.Context
import androidx.room.Room
import com.twinmind.app.data.local.TwinMindDatabase
import com.twinmind.app.data.local.dao.AudioChunkDao
import com.twinmind.app.data.local.dao.RecordingSessionDao
import com.twinmind.app.data.local.dao.SummaryDao
import com.twinmind.app.data.local.dao.TranscriptChunkDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): TwinMindDatabase {
        return Room.databaseBuilder(
            context,
            TwinMindDatabase::class.java,
            "twinmind_database"
        )
            .fallbackToDestructiveMigration()
            .build()
    }

    @Provides
    fun provideRecordingSessionDao(database: TwinMindDatabase): RecordingSessionDao =
        database.recordingSessionDao()

    @Provides
    fun provideAudioChunkDao(database: TwinMindDatabase): AudioChunkDao =
        database.audioChunkDao()

    @Provides
    fun provideTranscriptChunkDao(database: TwinMindDatabase): TranscriptChunkDao =
        database.transcriptChunkDao()

    @Provides
    fun provideSummaryDao(database: TwinMindDatabase): SummaryDao =
        database.summaryDao()
}
