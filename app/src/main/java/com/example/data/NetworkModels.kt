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
    val top_p: Float? = null,
    val max_tokens: Int?,
    val stream: Boolean,
    val stream_options: StreamOptions? = null,
    val enable_thinking: Boolean? = null,
    val extra_body: ExtraBody? = null
)

@JsonClass(generateAdapter = true)
data class ExtraBody(
    val top_k: Int? = null,
    val chat_template_kwargs: ChatTemplateKwargs? = null
)

@JsonClass(generateAdapter = true)
data class ChatTemplateKwargs(
    val enable_thinking: Boolean? = null
)

@JsonClass(generateAdapter = true)
data class StreamOptions(
    val include_usage: Boolean = true
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
