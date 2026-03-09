package com.twinmind.app.data.remote

import com.twinmind.app.BuildConfig
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import org.json.JSONObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

class OpenAiTranscriptionService @Inject constructor(
    private val okHttpClient: OkHttpClient
) : TranscriptionService {

    override suspend fun transcribe(audioFile: File): String = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.OPENAI_API_KEY
        if (apiKey.isBlank()) {
            throw IllegalStateException("OpenAI API key is not configured. Set OPENAI_API_KEY in local.properties.")
        }

        val requestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart(
                "file",
                audioFile.name,
                audioFile.asRequestBody("audio/wav".toMediaType())
            )
            .addFormDataPart("model", "whisper-1")
            .addFormDataPart("language", "en")
            .build()

        val request = Request.Builder()
            .url("https://api.openai.com/v1/audio/transcriptions")
            .header("Authorization", "Bearer $apiKey")
            .post(requestBody)
            .build()

        val response = okHttpClient.newCall(request).execute()

        if (!response.isSuccessful) {
            val errorBody = response.body?.string() ?: "Unknown error"
            throw Exception("Transcription failed (${response.code}): $errorBody")
        }

        val responseBody = response.body?.string() ?: throw Exception("Empty response from Whisper API")
        val json = JSONObject(responseBody)
        json.getString("text")
    }
}
