package com.example.data

import android.content.Context
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

class AppRepository(context: Context) {
    private val database = AppDatabase.getDatabase(context)
    val settingsDao = database.settingsDao()
    val apiEndpointHistoryDao = database.apiEndpointHistoryDao()
    val npcDao = database.npcDao()
    val agentDao = database.agentDao()
    val sessionDao = database.sessionDao()
    val messageDao = database.messageDao()
    val mcpToolDao = database.mcpToolDao()
    private val openAiService = OpenAiService()

    val settingsFlow: Flow<AppSettings?> = settingsDao.getSettingsFlow()
    val apiEndpointHistoryFlow: Flow<List<ApiEndpointHistory>> = apiEndpointHistoryDao.getAllFlow()
    val allNpcsFlow: Flow<List<NpcCharacter>> = npcDao.getAllNpcsFlow()
    val allAgentsFlow: Flow<List<AgentConfig>> = agentDao.getAllAgentsFlow()
    val allSessionsFlow: Flow<List<ChatSession>> = sessionDao.getAllSessionsFlow()
    val allMcpToolsFlow: Flow<List<McpTool>> = mcpToolDao.getAllMcpToolsFlow()
    val allMessagesFlow: Flow<List<ChatMessage>> = messageDao.getAllMessagesFlow()

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

    suspend fun rememberApiEndpoint(url: String) {
        val normalizedUrl = url.trim()
        if (normalizedUrl.isNotBlank()) {
            apiEndpointHistoryDao.upsertHistory(
                ApiEndpointHistory(
                    url = normalizedUrl,
                    createdAt = System.currentTimeMillis()
                )
            )
        }
    }

    suspend fun deleteApiEndpointHistory(url: String) {
        apiEndpointHistoryDao.deleteByUrl(url)
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
        val insertedId = messageDao.insertMessage(message)
        refreshSessionPreview(message.sessionId, message.timestamp)
        return insertedId
    }

    suspend fun deleteMessage(id: Long) {
        val msg = messageDao.getMessageById(id)
        messageDao.deleteMessageById(id)
        if (msg != null) {
            refreshSessionPreview(msg.sessionId)
        }
    }

    suspend fun updateMessage(message: ChatMessage) {
        messageDao.updateMessage(message)
        refreshSessionPreview(message.sessionId, message.timestamp)
    }

    suspend fun deleteMessagesAfterId(sessionId: Long, messageId: Long) {
        messageDao.deleteMessagesAfterId(sessionId, messageId)
        refreshSessionPreview(sessionId)
    }

    private suspend fun refreshSessionPreview(sessionId: Long, updatedAtHint: Long? = null) {
        val session = sessionDao.getSessionById(sessionId) ?: return
        val messages = messageDao.getMessagesForSession(sessionId)

        val latestAssistantBody = messages
            .asReversed()
            .asSequence()
            .filter { it.role == "assistant" }
            .map { sanitizeAssistantSummary(it.content) }
            .firstOrNull { it.isNotBlank() }

        val summary = latestAssistantBody?.let { shortenForSessionPreview(it) } ?: "No messages yet"
        val updatedAt = updatedAtHint ?: messages.lastOrNull()?.timestamp ?: System.currentTimeMillis()

        sessionDao.updateSession(
            session.copy(
                lastMessage = summary,
                updatedAt = updatedAt
            )
        )
    }

    private fun sanitizeAssistantSummary(content: String): String {
        return content
            .replace(Regex("""<tool_call\\b[^>]*>[\\s\\S]*?</tool_call>""", RegexOption.IGNORE_CASE), " ")
            .replace(Regex("""<thinking>[\\s\\S]*?</thinking>""", RegexOption.IGNORE_CASE), " ")
            .replace(Regex("""<think>[\\s\\S]*?</think>""", RegexOption.IGNORE_CASE), " ")
            .replace(Regex("""</?think>""", RegexOption.IGNORE_CASE), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()
    }

    private fun shortenForSessionPreview(content: String): String {
        return if (content.length > 60) content.take(60) + "..." else content
    }

    // Models & LLM Services
    suspend fun fetchModelsFromEndpoint(baseUrl: String, apiKey: String): ModelFetchResult {
        return openAiService.testConnectionAndGetModels(baseUrl, apiKey)
    }

    fun streamAssistantResponse(
        baseUrl: String,
        apiKey: String,
        request: ChatCompletionRequest
    ): Flow<ChatStreamChunk> {
        return openAiService.streamChatCompletions(baseUrl, apiKey, request)
    }

    fun serializeChatCompletionRequest(request: ChatCompletionRequest): String {
        return openAiService.serializeChatCompletionRequest(request)
    }

    // McpTool methods
    suspend fun insertMcpTool(tool: McpTool): Long {
        return mcpToolDao.insertMcpTool(tool)
    }

    suspend fun deleteMcpTool(id: Long) {
        mcpToolDao.deleteMcpToolById(id)
    }
}
