package com.twinmind.app.di

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.twinmind.app.BuildConfig
import com.twinmind.app.data.remote.*
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .addInterceptor(
                HttpLoggingInterceptor().apply {
                    level = HttpLoggingInterceptor.Level.BASIC
                }
            )
            .build()
    }

    @Provides
    @Singleton
    fun provideGson(): Gson = GsonBuilder().create()

    @Provides
    @Singleton
    fun provideTranscriptionService(
        mockService: MockTranscriptionService,
        okHttpClient: OkHttpClient
    ): TranscriptionService {
        return if (BuildConfig.USE_MOCK_API) {
            mockService
        } else {
            OpenAiTranscriptionService(okHttpClient)
        }
    }

    @Provides
    @Singleton
    fun provideSummaryApiService(
        mockService: MockSummaryService,
        okHttpClient: OkHttpClient,
        gson: Gson
    ): SummaryApiService {
        return if (BuildConfig.USE_MOCK_API) {
            mockService
        } else {
            OpenAiSummaryService(okHttpClient, gson)
        }
    }
}
