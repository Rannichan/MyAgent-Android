package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "app_settings")
data class AppSettings(
    @PrimaryKey val id: Int = 1,
    val baseUrl: String = "https://api.openai.com/v1/",
    val apiKey: String = "",
    val defaultModel: String = "gpt-4o-mini",
    val themeMode: String = "system", // system, light, dark
    val themeColor: String = "violet", // violet, blue, green, amber
    val temperature: Float = 0.7f,
    val topP: Float = 0.9f,
    val maxTokens: Int = 2048,
    val isStreaming: Boolean = true,
    val isThinkingModeEnabled: Boolean = true,
    val isToolCallsEnabled: Boolean = false,
    val statsResetTime: Long = 0L
)

@Entity(tableName = "npcs")
data class NpcCharacter(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val prompt: String,
    val greeting: String,
    val avatarColorOrdinal: Int = 0, // index for color
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "agents")
data class AgentConfig(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val agentMd: String = "",
    val identityMd: String = "",
    val memoryMd: String = "",
    val soulMd: String = "",
    val userMd: String = "",
    val toolsJson: String = "[]", // JSON representation of list of custom tools
    val avatarColorOrdinal: Int = 1, // index for color
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "chat_sessions")
data class ChatSession(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val mode: String, // "NPC", "AGENT", or "STANDARD"
    val associatedId: Long = 0L, // referring to NpcCharacter.id or AgentConfig.id
    val lastMessage: String = "",
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "messages")
data class ChatMessage(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val role: String, // "system", "user", or "assistant"
    val content: String,
    val thinkingContent: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val latencyMs: Long = 0L,
    val promptTokens: Int = 0,
    val completionTokens: Int = 0,
    val totalTokens: Int = 0,
    val tokensPerSec: Double = 0.0,
    val modelUsed: String = ""
)

@Entity(tableName = "mcp_tools")
data class McpTool(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val jsonContent: String,
    val createdAt: Long = System.currentTimeMillis()
)

