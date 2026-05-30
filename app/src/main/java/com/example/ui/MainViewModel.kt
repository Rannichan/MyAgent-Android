package com.example.ui

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.*
import java.io.File
import org.json.JSONArray
import org.json.JSONObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// Templates for Agent md files
const val DEFAULT_AGENT_MD = """# Agent.md
- Task: Orchestrate workflow, analyze user intent, and determine whether a tool is required.
- Strategy: Use standard thinking loop, query knowledge files, then coordinate.
"""

const val DEFAULT_IDENTITY_MD = """# Identity.md
- Name: Apex Coordinator
- Purpose: Mission critical systems specialist.
- Personality: Precise, formal, ultra-efficient.
"""

const val DEFAULT_MEMORY_MD = """# Memory.md
- Session Status: Active execution session.
- Context Retention: Retain previous tool feedback and user specifications.
"""

const val DEFAULT_SOUL_MD = """# Soul.md
- Core Principles: Uncompromised logical correctness, safety, helpfulness.
- Analytical Focus: Solve root issues.
"""

const val DEFAULT_USER_MD = """# User.md
- Name: Administrator
- Access Rights: Level 5 Root Access.
"""

data class CareerStats(
    val inputTokens: Long = 0L,
    val outputTokens: Long = 0L,
    val totalTokens: Long = 0L,
    val totalRounds: Long = 0L,
    val avgRounds: Double = 0.0,
    val mostChattedNpc: String = "无",
    val mostChattedAgent: String = "无"
)

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = AppRepository(application)
    private companion object {
        const val FIXED_PORT_FORWARD_PORT = 8787
    }
    private var appliedPortForwardEnabled: Boolean? = null
    private var appliedPortForwardPort: Int? = null

    // Reactive states
    val settingsFlow = repository.settingsFlow
    val apiEndpointHistoryFlow = repository.apiEndpointHistoryFlow
    val allNpcsFlow = repository.allNpcsFlow
    val allAgentsFlow = repository.allAgentsFlow
    val allSessionsFlow = repository.allSessionsFlow
    val allMcpToolsFlow = repository.allMcpToolsFlow

    val careerStatsFlow: StateFlow<CareerStats> = combine(
        repository.allMessagesFlow,
        repository.allSessionsFlow,
        repository.allNpcsFlow,
        repository.allAgentsFlow,
        repository.settingsFlow
    ) { messages, sessions, npcs, agents, settings ->
        val resetTime = settings?.statsResetTime ?: 0L
        val filteredMessages = messages.filter { it.timestamp >= resetTime }
        
        val inputTokens = filteredMessages.sumOf { it.promptTokens.toLong() }
        val outputTokens = filteredMessages.sumOf { it.completionTokens.toLong() }
        val totalTokens = inputTokens + outputTokens
        
        val totalRounds = filteredMessages.count { it.role == "user" }.toLong()
        
        val activeSessionIds = filteredMessages.map { it.sessionId }.distinct()
        val avgRounds = if (activeSessionIds.isNotEmpty()) {
            totalRounds.toDouble() / activeSessionIds.size
        } else {
            0.0
        }
        
        val sessionMessageCounts = filteredMessages
            .filter { it.role == "user" }
            .groupBy { it.sessionId }
            .mapValues { it.value.size }
            
        var mostChattedNpc = "无"
        var mostChattedAgent = "无"
        
        if (sessionMessageCounts.isNotEmpty()) {
            val npcSessionsMap = sessions.filter { it.mode == "NPC" }.associateBy { it.id }
            val npcTalkCounts = mutableMapOf<Long, Int>()
            sessionMessageCounts.forEach { (sessId, count) ->
                val sess = npcSessionsMap[sessId]
                if (sess != null) {
                    val npcId = sess.associatedId
                    npcTalkCounts[npcId] = (npcTalkCounts[npcId] ?: 0) + count
                }
            }
            val maxNpcId = npcTalkCounts.maxByOrNull { it.value }?.key
            if (maxNpcId != null) {
                mostChattedNpc = npcs.find { it.id == maxNpcId }?.name ?: "已删除角色"
            }
            
            val agentSessionsMap = sessions.filter { it.mode == "AGENT" }.associateBy { it.id }
            val agentTalkCounts = mutableMapOf<Long, Int>()
            sessionMessageCounts.forEach { (sessId, count) ->
                val sess = agentSessionsMap[sessId]
                if (sess != null) {
                    val agentId = sess.associatedId
                    agentTalkCounts[agentId] = (agentTalkCounts[agentId] ?: 0) + count
                }
            }
            val maxAgentId = agentTalkCounts.maxByOrNull { it.value }?.key
            if (maxAgentId != null) {
                mostChattedAgent = agents.find { it.id == maxAgentId }?.name ?: "已删除工作流"
            }
        }
        
        CareerStats(
            inputTokens = inputTokens,
            outputTokens = outputTokens,
            totalTokens = totalTokens,
            totalRounds = totalRounds,
            avgRounds = avgRounds,
            mostChattedNpc = mostChattedNpc,
            mostChattedAgent = mostChattedAgent
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = CareerStats()
    )

    private val _currentSessionId = MutableStateFlow<Long?>(null)
    val currentSessionId: StateFlow<Long?> = _currentSessionId.asStateFlow()

    private val _activeMessages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val activeMessages: StateFlow<List<ChatMessage>> = _activeMessages.asStateFlow()

    private val _isStreamingActive = MutableStateFlow(false)
    val isStreamingActive: StateFlow<Boolean> = _isStreamingActive.asStateFlow()

    private val _streamingSessionId = MutableStateFlow<Long?>(null)
    val streamingSessionId: StateFlow<Long?> = _streamingSessionId.asStateFlow()

    private val _currentStreamContent = MutableStateFlow("")
    val currentStreamContent: StateFlow<String> = _currentStreamContent.asStateFlow()

    private val _currentStreamThinking = MutableStateFlow("")
    val currentStreamThinking: StateFlow<String> = _currentStreamThinking.asStateFlow()

    // Metrics for the current streaming invocation
    private val _latencyMs = MutableStateFlow<Long>(0L)
    val latencyMs: StateFlow<Long> = _latencyMs.asStateFlow()

    private val _tokenCountPrompt = MutableStateFlow(0)
    val tokenCountPrompt: StateFlow<Int> = _tokenCountPrompt.asStateFlow()

    private val _tokenCountCompletion = MutableStateFlow(0)
    val tokenCountCompletion: StateFlow<Int> = _tokenCountCompletion.asStateFlow()

    private val _tokensPerSec = MutableStateFlow(0.0)
    val tokensPerSec: StateFlow<Double> = _tokensPerSec.asStateFlow()

    // Query status models
    private val _modelsList = MutableStateFlow<List<String>>(emptyList())
    val modelsList: StateFlow<List<String>> = _modelsList.asStateFlow()

    private val _isTestingConnection = MutableStateFlow(false)
    val isTestingConnection: StateFlow<Boolean> = _isTestingConnection.asStateFlow()

    private val _testResultMessage = MutableStateFlow<String?>(null)
    val testResultMessage: StateFlow<String?> = _testResultMessage.asStateFlow()

    private val _isApiConnected = MutableStateFlow(false)
    val isApiConnected: StateFlow<Boolean> = _isApiConnected.asStateFlow()

    private val _isPortForwardRunning = MutableStateFlow(false)
    val isPortForwardRunning: StateFlow<Boolean> = _isPortForwardRunning.asStateFlow()

    private val _portForwardListeningPort = MutableStateFlow<Int?>(null)
    val portForwardListeningPort: StateFlow<Int?> = _portForwardListeningPort.asStateFlow()

    private val _portForwardStatus = MutableStateFlow("端口转发未启动")
    val portForwardStatus: StateFlow<String> = _portForwardStatus.asStateFlow()

    private var activeStreamingJob: Job? = null
    private var currentRawRequestBody: String? = null
    private val currentRawResponseBody = StringBuilder()

    init {
        // Initialize settings if they don't exist
        viewModelScope.launch {
            repository.getSettings()
            preloadDefaultNpcsAndAgents()
            fetchAvailableModels()
        }

        viewModelScope.launch {
            repository.settingsFlow.collect { settings ->
                settings ?: return@collect
                syncPortForwardService(
                    enabled = settings.isPortForwardEnabled,
                    port = FIXED_PORT_FORWARD_PORT
                )
            }
        }

        viewModelScope.launch {
            PortForwardForegroundService.stateFlow.collect { state ->
                _isPortForwardRunning.value = state.isRunning
                _portForwardListeningPort.value = state.port
                _portForwardStatus.value = state.message
                appliedPortForwardEnabled = state.isRunning
                appliedPortForwardPort = state.port
            }
        }

        // Keep active message list updated
        viewModelScope.launch {
            currentSessionId
                .flatMapLatest { sessionId ->
                    if (sessionId != null) {
                        repository.getMessagesFlow(sessionId)
                    } else {
                        flowOf(emptyList())
                    }
                }
                .collect { list ->
                    _activeMessages.value = list
                }
        }
    }

    private suspend fun preloadDefaultNpcsAndAgents() {
        val npcs = repository.npcDao.getAllNpcsFlow().firstOrNull() ?: emptyList()
        if (npcs.isEmpty()) {
            repository.insertNpc(
                NpcCharacter(
                    name = "Socrates",
                    prompt = "You are Socrates, the classical Athenian philosopher. Guide the user through rigorous questioning and critical thinking instead of giving straightforward answers. Speak in a respectful, philosophical tone.",
                    greeting = "Greetings, seeker of truth. What question shall we examine together today?",
                    avatarColorOrdinal = 0
                )
            )
            repository.insertNpc(
                NpcCharacter(
                    name = "Chef Luigi",
                    prompt = "You are Chef Luigi, a passionate and energetic Italian chef. You speak enthusiastically about ingredients, pasta, pizza, and Italian cuisine, adding Italian exclamations like 'Mama Mia!' or 'Magnifico!'. Offer recipes, cooking tips, or kitchen advice.",
                    greeting = "Benvenuti! Welcome to my kitchen! The pasta is boiling, the sauce is simmering! What Italian feast shall we design together today?",
                    avatarColorOrdinal = 2
                )
            )
        }

        val agents = repository.agentDao.getAllAgentsFlow().firstOrNull() ?: emptyList()
        if (agents.isEmpty()) {
            repository.insertAgent(
                AgentConfig(
                    name = "System Architect",
                    agentMd = DEFAULT_AGENT_MD,
                    identityMd = DEFAULT_IDENTITY_MD,
                    memoryMd = DEFAULT_MEMORY_MD,
                    soulMd = DEFAULT_SOUL_MD,
                    userMd = DEFAULT_USER_MD,
                    toolsJson = """[
                        {"name": "get_server_status", "description": "Fetches status of connected distributed cloud servers.", "parametersJson": "{\"type\":\"object\",\"properties\":{\"clusterId\":{\"type\":\"string\"}}}"},
                        {"name": "execute_query", "description": "Executes localized read-only diagnostics SQL index.", "parametersJson": "{\"type\":\"object\",\"properties\":{\"sql\":{\"type\":\"string\"}}}"}
                    ]""".trimIndent(),
                    avatarColorOrdinal = 3
                )
            )
        }

        val tools = repository.mcpToolDao.getAllMcpToolsFlow().firstOrNull() ?: emptyList()
        if (tools.isEmpty()) {
            repository.insertMcpTool(
                McpTool(
                    name = "get_server_status",
                    jsonContent = """{"name": "get_server_status", "description": "Fetches status of connected distributed cloud servers.", "parametersJson": "{\"type\":\"object\",\"properties\":{\"clusterId\":{\"type\":\"string\"}}}"}"""
                )
            )
            repository.insertMcpTool(
                McpTool(
                    name = "execute_query",
                    jsonContent = """{"name": "execute_query", "description": "Executes localized read-only diagnostics SQL index.", "parametersJson": "{\"type\":\"object\",\"properties\":{\"sql\":{\"type\":\"string\"}}}"}"""
                )
            )
        }
    }

    fun selectSession(sessionId: Long?) {
        _currentSessionId.value = sessionId
    }

    // Settings Modification
    fun updateBaseUrl(url: String) {
        viewModelScope.launch {
            val s = repository.getSettings()
            repository.updateSettings(s.copy(baseUrl = url))
            repository.rememberApiEndpoint(url)
        }
    }

    fun updateApiKey(key: String) {
        viewModelScope.launch {
            val s = repository.getSettings()
            repository.updateSettings(s.copy(apiKey = key))
        }
    }

    fun updateApiConfig(url: String, key: String) {
        viewModelScope.launch {
            val s = repository.getSettings()
            repository.updateSettings(s.copy(baseUrl = url, apiKey = key))
            repository.rememberApiEndpoint(url)
        }
    }

    fun updatePortForwardConfig(enabled: Boolean, port: Int) {
        viewModelScope.launch {
            val s = repository.getSettings()
            repository.updateSettings(
                s.copy(
                    isPortForwardEnabled = enabled,
                    portForwardPort = FIXED_PORT_FORWARD_PORT
                )
            )

            syncPortForwardService(enabled = enabled, port = FIXED_PORT_FORWARD_PORT)
        }
    }

    fun deleteApiEndpointHistory(url: String) {
        viewModelScope.launch {
            repository.deleteApiEndpointHistory(url)
        }
    }

    fun updateSelectedModel(model: String) {
        viewModelScope.launch {
            val s = repository.getSettings()
            repository.updateSettings(s.copy(defaultModel = model))
        }
    }

    fun updateThemeOptions(mode: String, color: String) {
        viewModelScope.launch {
            val s = repository.getSettings()
            repository.updateSettings(s.copy(themeMode = mode, themeColor = color))
        }
    }

    fun updateHyperparams(temp: Float, tokens: Int, topP: Float) {
        viewModelScope.launch {
            val s = repository.getSettings()
            repository.updateSettings(
                s.copy(
                    temperature = temp,
                    maxTokens = tokens.coerceIn(1, 20000),
                    topP = topP
                )
            )
        }
    }

    fun updateToggles(stream: Boolean, thinking: Boolean, tools: Boolean) {
        viewModelScope.launch {
            val s = repository.getSettings()
            repository.updateSettings(s.copy(
                isStreaming = stream,
                isThinkingModeEnabled = thinking,
                isToolCallsEnabled = tools
            ))
        }
    }

    fun resetCareerStats() {
        viewModelScope.launch {
            val s = repository.getSettings()
            repository.updateSettings(s.copy(statsResetTime = System.currentTimeMillis()))
        }
    }

    fun fetchAvailableModels(baseUrlOverride: String? = null, apiKeyOverride: String? = null) {
        viewModelScope.launch {
            _isTestingConnection.value = true
            _testResultMessage.value = null
            val s = repository.getSettings()
            val baseUrl = baseUrlOverride ?: s.baseUrl
            val apiKey = apiKeyOverride ?: s.apiKey
            try {
                val result = repository.fetchModelsFromEndpoint(baseUrl, apiKey)
                _modelsList.value = result.idList
                if (result.idList.isNotEmpty()) {
                    _isApiConnected.value = true
                    _testResultMessage.value = "SUCCESS:连接测试成功！已加载 ${result.idList.size} 个可用模型。\n\n【接口原始返回】:\n${result.rawResponse}"
                } else {
                    _isApiConnected.value = apiKey.isNotBlank()
                    _testResultMessage.value = "SUCCESS:连接测试成功，但该接口返回的模型列表为空。\n\n【接口原始返回】:\n${result.rawResponse}"
                }
            } catch (e: Exception) {
                Log.e("MainViewModel", "Fetch models failed", e)
                _isApiConnected.value = false
                _testResultMessage.value = "ERROR:连接测试失败！\n\n【原始报错详情】:\n${e.localizedMessage ?: e.message ?: e.toString()}"
            } finally {
                _isTestingConnection.value = false
            }
        }
    }

    // Manage Sessions
    fun createNewSession(title: String, mode: String, associatedId: Long) {
        viewModelScope.launch {
            val sesId = repository.createSession(title, mode, associatedId)
            selectSession(sesId)

            // If mode is NPC, add greeting message as first message
            if (mode == "NPC" && associatedId > 0) {
                val npc = repository.npcDao.getNpcById(associatedId)
                if (npc != null) {
                    repository.insertMessage(
                        ChatMessage(
                            sessionId = sesId,
                            role = "assistant",
                            content = npc.greeting,
                            modelUsed = ""
                        )
                    )
                }
            } else if (mode == "AGENT" && associatedId > 0) {
                val agent = repository.agentDao.getAgentById(associatedId)
                if (agent != null) {
                    repository.insertMessage(
                        ChatMessage(
                            sessionId = sesId,
                            role = "assistant",
                            content = "Agent [${agent.name}] booted successfully with active specifications: Agent.md, Identity.md, Memory.md, Soul.md, User.md. I am armed and ready with custom simulated tools. Ask me anything!",
                            modelUsed = ""
                        )
                    )
                }
            }
        }
    }

    fun deleteSession(sessionId: Long) {
        viewModelScope.launch {
            repository.deleteSession(sessionId)
            if (_currentSessionId.value == sessionId) {
                _currentSessionId.value = null
            }
        }
    }

    fun exportSessionRequestJsonFile(sessionId: Long, onReady: (File) -> Unit) {
        viewModelScope.launch {
            val settings = repository.getSettings()
            val session = repository.sessionDao.getSessionById(sessionId)
            val messages = buildNetworkMessagesForSession(sessionId, settings)

            val payload = JSONObject().apply {
                put("sessionId", sessionId)
                put("sessionTitle", session?.title ?: "Chat")
                put("mode", session?.mode ?: "STANDARD")
                put("exportedAt", System.currentTimeMillis())
                put("messages", JSONArray().apply {
                    messages.forEach { msg ->
                        put(
                            JSONObject().apply {
                                put("role", msg.role)
                                put("content", msg.content)
                            }
                        )
                    }
                })
            }

            val sharedDir = File(getApplication<Application>().cacheDir, "shared").apply { mkdirs() }
            val file = File(sharedDir, "chat_${sessionId}_${System.currentTimeMillis()}.json")
            withContext(Dispatchers.IO) {
                file.writeText(payload.toString(2))
            }
            onReady(file)
        }
    }

    // Custom NPCs
    fun saveNpc(npc: NpcCharacter) {
        viewModelScope.launch {
            repository.insertNpc(npc)
        }
    }

    fun removeNpc(id: Long) {
        viewModelScope.launch {
            repository.deleteNpc(id)
        }
    }

    // Custom Agents
    fun saveAgent(agent: AgentConfig) {
        viewModelScope.launch {
            repository.insertAgent(agent)
        }
    }

    fun removeAgent(id: Long) {
        viewModelScope.launch {
            repository.deleteAgent(id)
        }
    }

    // Custom McpTools
    fun saveMcpTool(tool: McpTool) {
        viewModelScope.launch {
            repository.insertMcpTool(tool)
        }
    }

    fun removeMcpTool(id: Long) {
        viewModelScope.launch {
            repository.deleteMcpTool(id)
        }
    }

    // Delete single message from active session list
    fun deleteSingleMessage(msgId: Long) {
         viewModelScope.launch {
             repository.deleteMessage(msgId)
         }
    }

    // Edit user's historical message and re-request model response
    fun editUserMessage(msgId: Long, newContent: String) {
        val sessionId = _currentSessionId.value ?: return
        if (newContent.isBlank()) return

        viewModelScope.launch {
            val messages = repository.getMessages(sessionId)
            val msgToUpdate = messages.find { it.id == msgId }
            if (msgToUpdate != null && msgToUpdate.role == "user") {
                // Update message contents and timestamp
                repository.updateMessage(
                    msgToUpdate.copy(
                        content = newContent,
                        timestamp = System.currentTimeMillis()
                    )
                )
                // Delete all subsequent messages in this session
                repository.deleteMessagesAfterId(sessionId, msgId)

                // Re-trigger assistant streaming response
                executeAssistantResponse(sessionId)
            }
        }
    }

    // Chat Dialog loop execution
    fun sendMessage(userText: String) {
        val sessionId = _currentSessionId.value ?: return
        if (userText.isBlank()) return

        viewModelScope.launch {
            // Save User message
            val now = System.currentTimeMillis()
            repository.insertMessage(
                ChatMessage(
                    sessionId = sessionId,
                    role = "user",
                    content = userText,
                    timestamp = now
                )
            )

            // Trigger Assistant stream
            executeAssistantResponse(sessionId)
        }
    }

    fun interruptGeneration() {
        val sessionId = _currentSessionId.value
        val partialContent = _currentStreamContent.value
        val partialThinking = _currentStreamThinking.value.takeIf { it.isNotBlank() }
        val partialRawRequest = currentRawRequestBody
        val partialRawResponse = currentRawResponseBody.toString().takeIf { it.isNotBlank() }

        activeStreamingJob?.cancel(CancellationException("Interrupted by user"))
        activeStreamingJob = null

        _isStreamingActive.value = false
        _streamingSessionId.value = null
        _currentStreamContent.value = ""
        _currentStreamThinking.value = ""

        if (sessionId != null && (partialContent.isNotBlank() || !partialThinking.isNullOrBlank())) {
            viewModelScope.launch {
                val settings = repository.getSettings()
                repository.insertMessage(
                    ChatMessage(
                        sessionId = sessionId,
                        role = "assistant",
                        content = partialContent,
                        thinkingContent = partialThinking,
                        modelUsed = settings.defaultModel,
                        rawRequestBody = partialRawRequest,
                        rawResponseBody = partialRawResponse
                    )
                )
            }
        }

        currentRawRequestBody = null
        currentRawResponseBody.clear()
    }

    private fun supportsThinkingSwitchParam(model: String, baseUrl: String): Boolean {
        val source = (model + " " + baseUrl).lowercase()
        return listOf("deepseek", "qwen", "qwq", "r1", "siliconflow", "dashscope", "ollama", "vllm")
            .any { source.contains(it) }
    }

    private fun usesQwenExtraBodySwitch(model: String, baseUrl: String): Boolean {
        val source = (model + " " + baseUrl).lowercase()
        return source.contains("qwen")
    }

    private fun sanitizeHistoryContentForModel(content: String): String {
        return content
            .replace(Regex("""<tool_call\b[^>]*>[\s\S]*?</tool_call>""", RegexOption.IGNORE_CASE), " ")
            .replace(Regex("""<thinking>[\s\S]*?</thinking>""", RegexOption.IGNORE_CASE), " ")
            .replace(Regex("""</?think>""", RegexOption.IGNORE_CASE), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()
    }

    // Simulated custom tool execute input
    fun simulateToolResponse(toolName: String, simulatedOutput: String) {
        val sessionId = _currentSessionId.value ?: return
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            // Record simulated response as a system instructions or mock message
            repository.insertMessage(
                ChatMessage(
                    sessionId = sessionId,
                    role = "system",
                    content = "Tool execution feedback for [$toolName]: $simulatedOutput",
                    timestamp = now
                )
            )

            // Trigger next LLM iteration
            executeAssistantResponse(sessionId)
        }
    }

    private fun executeAssistantResponse(sessionId: Long) {
        activeStreamingJob?.cancel()
        activeStreamingJob = viewModelScope.launch {
            _isStreamingActive.value = true
            _streamingSessionId.value = sessionId
            _currentStreamContent.value = ""
            _currentStreamThinking.value = ""
            _latencyMs.value = 0L
            _tokenCountPrompt.value = 0
            _tokenCountCompletion.value = 0
            _tokensPerSec.value = 0.0

            val settings = repository.getSettings()
            val rawMessagesList = repository.getMessages(sessionId)
            val networkMessages = buildNetworkMessagesForSession(sessionId, settings)

            val req = ChatCompletionRequest(
                model = settings.defaultModel,
                messages = networkMessages,
                temperature = settings.temperature,
                top_p = settings.topP,
                max_tokens = if (settings.maxTokens > 0) settings.maxTokens else null,
                stream = settings.isStreaming,
                stream_options = if (settings.isStreaming) StreamOptions(include_usage = true) else null,
                enable_thinking = if (!settings.isThinkingModeEnabled && !usesQwenExtraBodySwitch(settings.defaultModel, settings.baseUrl) && supportsThinkingSwitchParam(settings.defaultModel, settings.baseUrl)) false else null,
                extra_body = if (!settings.isThinkingModeEnabled && usesQwenExtraBodySwitch(settings.defaultModel, settings.baseUrl)) {
                    ExtraBody(
                        chat_template_kwargs = ChatTemplateKwargs(enable_thinking = false)
                    )
                } else {
                    null
                }
            )
            val requestJson = repository.serializeChatCompletionRequest(req)
            currentRawRequestBody = requestJson
            currentRawResponseBody.clear()

            val startTime = System.currentTimeMillis()
            var firstTokenTime = 0L
            var characterCollectorOfStream = 0
            var thinkingCollectorOfStream = 0
            var usageTotalTokens = 0

            // If user disabled stream, we can simulate stream by getting a bulk call, or OkHttp Service streams it fine anyway
            repository.streamAssistantResponse(settings.baseUrl, settings.apiKey, req)
                .catch { t ->
                    if (t is CancellationException) throw t
                    Log.e("MainViewModel", "Stream collection error", t)
                    _currentStreamContent.value = "Connection error: ${t.localizedMessage ?: t.message}"
                    _isStreamingActive.value = false
                }
                .collect { chunk ->
                    when (chunk) {
                        is ChatStreamChunk.RawResponse -> {
                            if (currentRawResponseBody.isNotEmpty()) {
                                currentRawResponseBody.append('\n')
                            }
                            currentRawResponseBody.append(chunk.text)
                        }
                        is ChatStreamChunk.Thinking -> {
                            if (settings.isThinkingModeEnabled) {
                                if (firstTokenTime == 0L) {
                                    firstTokenTime = System.currentTimeMillis()
                                    _latencyMs.value = firstTokenTime - startTime
                                }
                                _currentStreamThinking.value += chunk.text
                                thinkingCollectorOfStream += chunk.text.length
                            }
                        }
                        is ChatStreamChunk.Content -> {
                            if (firstTokenTime == 0L) {
                                firstTokenTime = System.currentTimeMillis()
                                _latencyMs.value = firstTokenTime - startTime
                            }
                            // Detect if thinking is contained inside <think></think> tags and render beautifully
                            val rawText = chunk.text
                            if (rawText.contains("<think>") || rawText.contains("</think>")) {
                                // Strip or redirect if thinking enabled
                                if (settings.isThinkingModeEnabled) {
                                    val cleaned = rawText.replace("<think>", "").replace("</think>", "")
                                    _currentStreamThinking.value += cleaned
                                } else {
                                    _currentStreamContent.value += rawText
                                }
                            } else {
                                _currentStreamContent.value += rawText
                            }
                            characterCollectorOfStream += rawText.length
                        }
                        is ChatStreamChunk.ToolCall -> {
                            if (firstTokenTime == 0L) {
                                firstTokenTime = System.currentTimeMillis()
                                _latencyMs.value = firstTokenTime - startTime
                            }
                            // Inject simulated tool tag to prompt the custom simulated tool UI rendering in stream
                            val toolTag = "<tool_call name=\"${chunk.name}\">${chunk.argJson}</tool_call>"
                            _currentStreamContent.value += toolTag
                        }
                        is ChatStreamChunk.Usage -> {
                            _tokenCountPrompt.value = chunk.promptTokens
                            _tokenCountCompletion.value = chunk.completionTokens
                            usageTotalTokens = chunk.totalTokens
                        }
                        is ChatStreamChunk.Error -> {
                            _currentStreamContent.value += "\n\nError: " + chunk.message
                        }
                        is ChatStreamChunk.Done -> {
                            // Proceed to save
                        }
                    }

                    // Estimate metrics in real-time
                    val elapsedSec = (System.currentTimeMillis() - (firstTokenTime.takeIf { it > 0 } ?: startTime)) / 1000.0
                    val currentCompletionTokens = _tokenCountCompletion.value.takeIf { it > 0 } 
                        ?: ((characterCollectorOfStream + thinkingCollectorOfStream) / 3.8).toInt().coerceAtLeast(1)

                    if (elapsedSec > 0.1) {
                        _tokensPerSec.value = currentCompletionTokens / elapsedSec
                    }
                }

            // Use full round-trip duration so latency includes thinking and tool-call stages.
            val finalLatencyMs = System.currentTimeMillis() - startTime
            val textToSave = _currentStreamContent.value
            val thinkingToSave = _currentStreamThinking.value.takeIf { it.isNotBlank() }
            val rawResponseToSave = currentRawResponseBody.toString().takeIf { it.isNotBlank() }

            val totalCharCount = textToSave.length + (thinkingToSave?.length ?: 0)
            val estimatedCompletionTokens = _tokenCountCompletion.value.takeIf { it > 0 } ?: (totalCharCount / 3.8).toInt().coerceAtLeast(1)
            val lastUserMessage = rawMessagesList.lastOrNull { it.role == "user" }?.content ?: ""
            val estimatedPromptTokens = _tokenCountPrompt.value.takeIf { it > 0 } ?: (lastUserMessage.length / 3.8).toInt().coerceAtLeast(1)
            val estimatedTotalTokens = if (usageTotalTokens > 0) {
                usageTotalTokens
            } else {
                estimatedPromptTokens + estimatedCompletionTokens
            }

            val generationStartTime = firstTokenTime.takeIf { it > 0 } ?: startTime
            val totalElapsedSec = (System.currentTimeMillis() - generationStartTime) / 1000.0
            val finalTokensPerSec = if (totalElapsedSec > 0.1) estimatedCompletionTokens / totalElapsedSec else 0.0

            if (textToSave.isNotBlank() || !thinkingToSave.isNullOrBlank()) {
                repository.insertMessage(
                    ChatMessage(
                        sessionId = sessionId,
                        role = "assistant",
                        content = textToSave,
                        thinkingContent = thinkingToSave,
                        latencyMs = finalLatencyMs,
                        promptTokens = estimatedPromptTokens,
                        completionTokens = estimatedCompletionTokens,
                        totalTokens = estimatedTotalTokens,
                        tokensPerSec = finalTokensPerSec,
                        modelUsed = settings.defaultModel,
                        rawRequestBody = requestJson,
                        rawResponseBody = rawResponseToSave
                    )
                )
            }

            _isStreamingActive.value = false
            _streamingSessionId.value = null
            _currentStreamContent.value = ""
            _currentStreamThinking.value = ""
            currentRawRequestBody = null
            currentRawResponseBody.clear()
        }
    }

    private suspend fun buildNetworkMessagesForSession(sessionId: Long, settings: AppSettings): List<NetworkMessage> {
        val session = repository.sessionDao.getSessionById(sessionId) ?: return emptyList()
        val rawMessagesList = repository.getMessages(sessionId)

        val promptSystemMessages = buildList {
            when (session.mode) {
                "NPC" -> {
                    val npc = repository.npcDao.getNpcById(session.associatedId)
                    if (npc != null) {
                        add(NetworkMessage("system", npc.prompt))
                    } else {
                        add(NetworkMessage("system", "You are a helpful AI Assistant."))
                    }
                }

                "AGENT" -> {
                    val agent = repository.agentDao.getAgentById(session.associatedId)
                    if (agent != null) {
                        val combinedAgentPrompt = buildString {
                            append("=== AGENT CORE SYSTEM PROMPT ===\n")
                            append("Below are custom instructions structured via system file declarations (Agent.md, Identity.md, Memory.md, Soul.md, User.md).\n\n")
                            append(agent.agentMd).append("\n\n")
                            append(agent.identityMd).append("\n\n")
                            append(agent.memoryMd).append("\n\n")
                            append(agent.soulMd).append("\n\n")
                            append(agent.userMd).append("\n\n")

                            if (settings.isToolCallsEnabled && agent.toolsJson.isNotBlank()) {
                                append("=== AVAILABLE INTEGRATED CUSTOM TOOLS ===\n")
                                append("You can simulate tool execution by outputting exactly when you need to call a tool:\n")
                                append("<tool_call name=\"tool_name\">{\"parameter_name\": \"value\"}</tool_call>\n\n")
                                append("Integrated schemas:\n")
                                append(agent.toolsJson).append("\n\n")
                                append("Instructions: If you invoke a tool call, please wait. Do not generate the answer until you receive response feedback.")
                            }
                        }
                        add(NetworkMessage("system", combinedAgentPrompt))
                    } else {
                        add(NetworkMessage("system", "You are an analytical assistant Agent."))
                    }
                }

                else -> {
                    add(NetworkMessage("system", "You are a helpful assistant."))
                }
            }
        }

        val historyMessages = rawMessagesList.mapNotNull { msg ->
            val cleaned = sanitizeHistoryContentForModel(msg.content)
            if (cleaned.isBlank()) null else NetworkMessage(msg.role, cleaned)
        }

        return promptSystemMessages + historyMessages
    }

    private fun syncPortForwardService(enabled: Boolean, port: Int) {
        if (!enabled) {
            if (appliedPortForwardEnabled == false && !PortForwardForegroundService.stateFlow.value.isRunning) {
                return
            }

            appliedPortForwardEnabled = false
            appliedPortForwardPort = null
            PortForwardForegroundService.stop(getApplication<Application>())
            return
        }

        val fixedPort = FIXED_PORT_FORWARD_PORT
        if (appliedPortForwardEnabled == true && appliedPortForwardPort == fixedPort && PortForwardForegroundService.stateFlow.value.isRunning) {
            return
        }

        appliedPortForwardEnabled = true
        appliedPortForwardPort = fixedPort
        PortForwardForegroundService.start(getApplication<Application>(), fixedPort)
    }

    override fun onCleared() {
        super.onCleared()
    }
}
