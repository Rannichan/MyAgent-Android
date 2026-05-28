package com.example.data

import android.util.Log
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.BufferedReader
import java.io.IOException
import java.util.concurrent.TimeUnit

sealed class ChatStreamChunk {
    data class Content(val text: String) : ChatStreamChunk()
    data class Thinking(val text: String) : ChatStreamChunk()
    data class ToolCall(val name: String, val argJson: String) : ChatStreamChunk()
    data class Usage(val promptTokens: Int, val completionTokens: Int, val totalTokens: Int) : ChatStreamChunk()
    data class Error(val message: String) : ChatStreamChunk()
    object Done : ChatStreamChunk()
}

data class ModelFetchResult(
    val idList: List<String>,
    val rawResponse: String
)

class OpenAiService {
    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    private val requestAdapter = moshi.adapter(ChatCompletionRequest::class.java)
    private val responseAdapter = moshi.adapter(ModelListResponse::class.java)
    private val dynamicAdapter = moshi.adapter(Map::class.java)

    private fun cleanUrl(url: String): String {
        return if (url.trim().endsWith("/")) url.trim() else "${url.trim()}/"
    }

    suspend fun testConnectionAndGetModels(baseUrl: String, apiKey: String): ModelFetchResult {
        return withContext(Dispatchers.IO) {
            val url = cleanUrl(baseUrl) + "models"
            val requestBuilder = Request.Builder()
                .url(url)
                .get()
            
            if (apiKey.isNotBlank()) {
                requestBuilder.addHeader("Authorization", "Bearer $apiKey")
            }

            client.newCall(requestBuilder.build()).execute().use { response ->
                val bodyString = response.body?.string() ?: ""
                if (!response.isSuccessful) {
                    Log.e("OpenAiService", "Failed to retrieve models: code=${response.code}")
                    val errText = if (bodyString.isNotBlank()) bodyString else "Empty response"
                    throw IOException("HTTP ${response.code} ($errText)")
                }
                val modelsList = try {
                    responseAdapter.fromJson(bodyString)
                } catch (e: Exception) {
                    null
                }
                val ids = modelsList?.data?.map { it.id }?.sorted() ?: emptyList()
                ModelFetchResult(ids, bodyString)
            }
        }
    }

    fun streamChatCompletions(
        baseUrl: String,
        apiKey: String,
        request: ChatCompletionRequest
    ): Flow<ChatStreamChunk> = flow {
        val url = cleanUrl(baseUrl) + "chat/completions"
        val jsonPayload = requestAdapter.toJson(request)
        
        val requestBody = jsonPayload.toRequestBody("application/json".toMediaType())
        val builder = Request.Builder()
            .url(url)
            .post(requestBody)

        if (apiKey.isNotBlank()) {
            builder.addHeader("Authorization", "Bearer $apiKey")
        }

        val httpRequest = builder.build()

        try {
            val response = client.newCall(httpRequest).execute()
            if (!response.isSuccessful) {
                val errBody = response.body?.string() ?: "Unknown error"
                emit(ChatStreamChunk.Error("HTTP Error ${response.code}: $errBody"))
                return@flow
            }

            val source = response.body?.source() ?: throw IOException("Empty response body")
            val reader = BufferedReader(source.inputStream().reader())

            var line: String?
            while (reader.readLine().also { line = it } != null) {
                val currentLine = line ?: break
                if (currentLine.startsWith("data:")) {
                    val data = currentLine.substring(5).trim()
                    if (data == "[DONE]") {
                        emit(ChatStreamChunk.Done)
                        break
                    }
                    if (data.isNotBlank()) {
                        try {
                            val map = dynamicAdapter.fromJson(data) as? Map<*, *>
                            
                            // 1. Process Choices / Delta
                            val choices = map?.get("choices") as? List<*>
                            if (choices != null && choices.isNotEmpty()) {
                                val choice = choices[0] as? Map<*, *>
                                val delta = choice?.get("delta") as? Map<*, *>
                                
                                if (delta != null) {
                                    // Parse reasoning content (supporting thinking mode)
                                    val reasoning = (delta["reasoning_content"] ?: delta["thinking_content"] ?: delta["reasoning"]) as? String
                                    if (!reasoning.isNullOrEmpty()) {
                                        emit(ChatStreamChunk.Thinking(reasoning))
                                    }

                                    val content = delta["content"] as? String
                                    if (!content.isNullOrEmpty()) {
                                        emit(ChatStreamChunk.Content(content))
                                    }

                                    // Parse tool calls if returned by model
                                    val toolCalls = delta["tool_calls"] as? List<*>
                                    if (toolCalls != null && toolCalls.isNotEmpty()) {
                                        val firstTool = toolCalls[0] as? Map<*, *>
                                        val function = firstTool?.get("function") as? Map<*, *>
                                        val name = function?.get("name") as? String
                                        val arguments = function?.get("arguments") as? String
                                        if (name != null) {
                                            emit(ChatStreamChunk.ToolCall(name, arguments ?: ""))
                                        }
                                    }
                                }
                            }

                            // 2. Process Token Usage Stats (openai compatible)
                            val usage = map?.get("usage") as? Map<*, *>
                            if (usage != null) {
                                val promptTokens = (usage["prompt_tokens"] as? Double)?.toInt() ?: 0
                                val completionTokens = (usage["completion_tokens"] as? Double)?.toInt() ?: 0
                                val totalTokens = (usage["total_tokens"] as? Double)?.toInt() ?: 0
                                if (promptTokens > 0 || completionTokens > 0) {
                                    emit(ChatStreamChunk.Usage(promptTokens, completionTokens, totalTokens))
                                }
                            }

                        } catch (e: Exception) {
                            // Suppress block level issues during stream parsing
                            Log.w("OpenAiService", "Moshi chunk parsing failed", e)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            emit(ChatStreamChunk.Error("Execution error: ${e.localizedMessage ?: e.message}"))
        }
    }.flowOn(Dispatchers.IO)
}

// Kotlin withContext helper
private suspend fun <T> withContext(
    context: kotlin.coroutines.CoroutineContext,
    block: suspend kotlinx.coroutines.CoroutineScope.() -> T
): T = kotlinx.coroutines.withContext(context, block)
