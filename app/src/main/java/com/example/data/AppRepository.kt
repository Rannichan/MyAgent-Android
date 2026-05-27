package com.example.data

import android.content.Context
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

class AppRepository(context: Context) {
    private val database = AppDatabase.getDatabase(context)
    val settingsDao = database.settingsDao()
    val npcDao = database.npcDao()
    val agentDao = database.agentDao()
    val sessionDao = database.sessionDao()
    val messageDao = database.messageDao()
    private val openAiService = OpenAiService()

    val settingsFlow: Flow<AppSettings?> = settingsDao.getSettingsFlow()
    val allNpcsFlow: Flow<List<NpcCharacter>> = npcDao.getAllNpcsFlow()
    val allAgentsFlow: Flow<List<AgentConfig>> = agentDao.getAllAgentsFlow()
    val allSessionsFlow: Flow<List<ChatSession>> = sessionDao.getAllSessionsFlow()

    suspend fun getSettings(): AppSettings {
        var settings = settingsDao.getSettings()
        if (settings == null) {
            settings = AppSettings()
            settingsDao.saveSettings(settings)
        }
        return settings
    }

    suspend fun updateSettings(settings: AppSettings) {
        settingsDao.saveSettings(settings)
    }

    // NPC methods
    suspend fun insertNpc(npc: NpcCharacter): Long {
        return npcDao.insertNpc(npc)
    }

    suspend fun deleteNpc(id: Long) {
        npcDao.deleteNpcById(id)
    }

    // Agent methods
    suspend fun insertAgent(agent: AgentConfig): Long {
        return agentDao.insertAgent(agent)
    }

    suspend fun deleteAgent(id: Long) {
        agentDao.deleteAgentById(id)
    }

    // Session methods
    suspend fun createSession(title: String, mode: String, associatedId: Long): Long {
        val session = ChatSession(
            title = title,
            mode = mode,
            associatedId = associatedId,
            lastMessage = "No messages yet",
            updatedAt = System.currentTimeMillis()
        )
        return sessionDao.insertSession(session)
    }

    suspend fun deleteSession(id: Long) {
        sessionDao.deleteSessionById(id)
        messageDao.deleteMessagesBySessionId(id)
    }

    // Message methods
    fun getMessagesFlow(sessionId: Long): Flow<List<ChatMessage>> {
        return messageDao.getMessagesFlow(sessionId)
    }

    suspend fun getMessages(sessionId: Long): List<ChatMessage> {
        return messageDao.getMessagesForSession(sessionId)
    }

    suspend fun insertMessage(message: ChatMessage): Long {
        // Update session's last message text and timestamp
        val session = sessionDao.getSessionById(message.sessionId)
        if (session != null) {
            val shortText = if (message.content.length > 60) {
                message.content.take(60) + "..."
            } else {
                message.content
            }
            sessionDao.updateSession(
                session.copy(
                    lastMessage = shortText,
                    updatedAt = message.timestamp
                )
            )
        }
        return messageDao.insertMessage(message)
    }

    suspend fun deleteMessage(id: Long) {
        messageDao.deleteMessageById(id)
    }

    // Models & LLM Services
    suspend fun fetchModelsFromEndpoint(baseUrl: String, apiKey: String): List<String> {
        return openAiService.testConnectionAndGetModels(baseUrl, apiKey)
    }

    fun streamAssistantResponse(
        baseUrl: String,
        apiKey: String,
        request: ChatCompletionRequest
    ): Flow<ChatStreamChunk> {
        return openAiService.streamChatCompletions(baseUrl, apiKey, request)
    }
}
