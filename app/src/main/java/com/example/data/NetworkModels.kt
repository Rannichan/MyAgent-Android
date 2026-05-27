package com.example.data

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class NetworkMessage(
    val role: String,
    val content: String
)

@JsonClass(generateAdapter = true)
data class ChatCompletionRequest(
    val model: String,
    val messages: List<NetworkMessage>,
    val temperature: Float,
    val max_tokens: Int?,
    val stream: Boolean
)

@JsonClass(generateAdapter = true)
data class ModelItem(
    val id: String
)

@JsonClass(generateAdapter = true)
data class ModelListResponse(
    val data: List<ModelItem>
)

// Define custom tool data model
@JsonClass(generateAdapter = true)
data class CustomTool(
    val id: String = java.util.UUID.randomUUID().toString(),
    val name: String,
    val description: String,
    val parametersJson: String = "{}" // e.g. "{\"type\":\"object\",\"properties\":{}}"
)
