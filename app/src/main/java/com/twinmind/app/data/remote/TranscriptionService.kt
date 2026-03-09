package com.twinmind.app.data.remote

import java.io.File

interface TranscriptionService {
    suspend fun transcribe(audioFile: File): String
}
