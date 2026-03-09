package com.twinmind.app.data.remote

import com.google.gson.Gson
import com.twinmind.app.BuildConfig
import com.twinmind.app.domain.model.SummaryResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import javax.inject.Inject

class OpenAiSummaryService @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val gson: Gson
) : SummaryApiService {

    private val systemPrompt = """
        You are a meeting summary assistant. Given a meeting transcript, generate a structured summary.
        Respond ONLY with valid JSON in this exact format:
        {
            "title": "Brief meeting title",
            "summary": "Comprehensive paragraph summarizing the meeting",
            "actionItems": ["Action item 1", "Action item 2"],
            "keyPoints": ["Key point 1", "Key point 2"]
        }
    """.trimIndent()

    override fun generateSummary(transcript: String): Flow<SummaryResult> = flow {
        val apiKey = BuildConfig.OPENAI_API_KEY
        if (apiKey.isBlank()) {
            throw IllegalStateException("OpenAI API key not configured")
        }

        val requestJson = JSONObject().apply {
            put("model", "gpt-4o-mini")
            put("messages", JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "system")
                    put("content", systemPrompt)
                })
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", "Please summarize this meeting transcript:\n\n$transcript")
                })
            })
            put("stream", true)
            put("max_tokens", 2000)
        }

        val request = Request.Builder()
            .url("https://api.openai.com/v1/chat/completions")
            .header("Authorization", "Bearer $apiKey")
            .header("Content-Type", "application/json")
            .post(requestJson.toString().toRequestBody("application/json".toMediaType()))
            .build()

        val response = okHttpClient.newCall(request).execute()

        if (!response.isSuccessful) {
            throw Exception("Summary generation failed (${response.code})")
        }

        val reader = BufferedReader(InputStreamReader(response.body!!.byteStream()))
        val contentBuilder = StringBuilder()

        reader.useLines { lines ->
            lines.forEach { line ->
                if (line.startsWith("data: ") && line != "data: [DONE]") {
                    try {
                        val data = JSONObject(line.removePrefix("data: "))
                        val choices = data.getJSONArray("choices")
                        if (choices.length() > 0) {
                            val delta = choices.getJSONObject(0).optJSONObject("delta")
                            val content = delta?.optString("content", "") ?: ""
                            if (content.isNotEmpty()) {
                                contentBuilder.append(content)
                                // Try to parse partial JSON
                                tryParseSummary(contentBuilder.toString())?.let { emit(it) }
                            }
                        }
                    } catch (_: Exception) {
                        // Skip malformed SSE events
                    }
                }
            }
        }

        // Parse final result
        val finalResult = tryParseSummary(contentBuilder.toString())
            ?: throw Exception("Failed to parse summary response")
        emit(finalResult)
    }.flowOn(Dispatchers.IO)

    override suspend fun generateSummaryBlocking(transcript: String): SummaryResult =
        withContext(Dispatchers.IO) {
            val apiKey = BuildConfig.OPENAI_API_KEY
            if (apiKey.isBlank()) {
                throw IllegalStateException("OpenAI API key not configured")
            }

            val requestJson = JSONObject().apply {
                put("model", "gpt-4o-mini")
                put("messages", JSONArray().apply {
                    put(JSONObject().apply {
                        put("role", "system")
                        put("content", systemPrompt)
                    })
                    put(JSONObject().apply {
                        put("role", "user")
                        put("content", "Please summarize this meeting transcript:\n\n$transcript")
                    })
                })
                put("max_tokens", 2000)
            }

            val request = Request.Builder()
                .url("https://api.openai.com/v1/chat/completions")
                .header("Authorization", "Bearer $apiKey")
                .header("Content-Type", "application/json")
                .post(requestJson.toString().toRequestBody("application/json".toMediaType()))
                .build()

            val response = okHttpClient.newCall(request).execute()

            if (!response.isSuccessful) {
                throw Exception("Summary generation failed (${response.code})")
            }

            val responseBody = response.body?.string() ?: throw Exception("Empty response")
            val json = JSONObject(responseBody)
            val content = json
                .getJSONArray("choices")
                .getJSONObject(0)
                .getJSONObject("message")
                .getString("content")

            tryParseSummary(content) ?: throw Exception("Failed to parse summary")
        }

    private fun tryParseSummary(content: String): SummaryResult? {
        return try {
            // Try to find JSON in the content
            val jsonStr = content.trim().let {
                if (it.startsWith("{")) it
                else {
                    val start = it.indexOf("{")
                    val end = it.lastIndexOf("}")
                    if (start >= 0 && end > start) it.substring(start, end + 1) else return null
                }
            }
            val json = JSONObject(jsonStr)
            SummaryResult(
                title = json.optString("title", ""),
                summary = json.optString("summary", ""),
                actionItems = json.optJSONArray("actionItems")?.let { arr ->
                    (0 until arr.length()).map { arr.getString(it) }
                } ?: emptyList(),
                keyPoints = json.optJSONArray("keyPoints")?.let { arr ->
                    (0 until arr.length()).map { arr.getString(it) }
                } ?: emptyList()
            )
        } catch (_: Exception) {
            null
        }
    }
}
