package com.example.ui

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.*
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
import kotlinx.coroutines.flow.stateIn
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

    // Reactive states
    val settingsFlow = repository.settingsFlow
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

    private val _isNavigationBarVisible = MutableStateFlow(true)
    val isNavigationBarVisible: StateFlow<Boolean> = _isNavigationBarVisible.asStateFlow()

    fun setNavigationBarVisibility(visible: Boolean) {
        _isNavigationBarVisible.value = visible
    }

    private var activeStreamingJob: Job? = null

    init {
        // Initialize settings if they don't exist
        viewModelScope.launch {
            repository.getSettings()
            preloadDefaultNpcsAndAgents()
            fetchAvailableModels()
        }

        // Keep active message list updated
        viewModelScope.launch {
            currentSessionId.collect { sessionId ->
                if (sessionId != null) {
                    repository.getMessagesFlow(sessionId).collect { list ->
                        _activeMessages.value = list
                    }
                } else {
                    _activeMessages.value = emptyList()
                }
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
            fetchAvailableModels()
        }
    }

    fun updateApiKey(key: String) {
        viewModelScope.launch {
            val s = repository.getSettings()
            repository.updateSettings(s.copy(apiKey = key))
            fetchAvailableModels()
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
            repository.updateSettings(s.copy(temperature = temp, maxTokens = tokens, topP = topP))
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

    fun fetchAvailableModels() {
        viewModelScope.launch {
            _isTestingConnection.value = true
            _testResultMessage.value = null
            val s = repository.getSettings()
            try {
                val result = repository.fetchModelsFromEndpoint(s.baseUrl, s.apiKey)
                _modelsList.value = result.idList
                if (result.idList.isNotEmpty()) {
                    _isApiConnected.value = true
                    _testResultMessage.value = "SUCCESS:连接测试成功！已加载 ${result.idList.size} 个可用模型。\n\n【接口原始返回】:\n${result.rawResponse}"
                } else {
                    _isApiConnected.value = s.apiKey.isNotBlank()
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

    fun exportChatHistoryText(sessionId: Long, onReady: (String) -> Unit) {
        viewModelScope.launch {
            val messages = repository.getMessages(sessionId)
            val session = repository.sessionDao.getSessionById(sessionId)
            val formatStr = buildString {
                append("=== CONFIGURABLE AGENT/NPC CHAT LOG ===\n")
                append("Session: ${session?.title ?: "Chat"}\n")
                append("Mode: ${session?.mode ?: "Standard"}\n")
                append("Exported: ${java.util.Date()}\n\n")
                for (m in messages) {
                    append("[${m.role.uppercase()} - ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(m.timestamp)}]\n")
                    if (!m.thinkingContent.isNullOrBlank()) {
                        append("<thinking>\n${m.thinkingContent}\n</thinking>\n")
                    }
                    append("${m.content}\n")
                    if (m.promptTokens > 0) {
                        append("(Prompt: ${m.promptTokens} tokens, Completion: ${m.completionTokens} tokens, Speed: ${m.tokensPerSec} t/s, Latency: ${m.latencyMs}ms, Model: ${m.modelUsed})\n")
                    }
                    append("\n----------------------------\n")
                }
            }
            onReady(formatStr)
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
            _currentStreamContent.value = ""
            _currentStreamThinking.value = ""
            _latencyMs.value = 0L
            _tokenCountPrompt.value = 0
            _tokenCountCompletion.value = 0
            _tokensPerSec.value = 0.0

            val settings = repository.getSettings()
            val session = repository.sessionDao.getSessionById(sessionId) ?: return@launch
            val rawMessagesList = repository.getMessages(sessionId)

            // Organize standard LLM System prompts combined with NPC prompts / Agent MD files
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

            // Group existing database logs as network messages
            val networkMessages = promptSystemMessages + rawMessagesList.map { NetworkMessage(it.role, it.content) }

            val req = ChatCompletionRequest(
                model = settings.defaultModel,
                messages = networkMessages,
                temperature = settings.temperature,
                top_p = settings.topP,
                max_tokens = if (settings.maxTokens > 0) settings.maxTokens else null,
                stream = settings.isStreaming
            )

            val startTime = System.currentTimeMillis()
            var firstTokenTime = 0L
            var characterCollectorOfStream = 0
            var thinkingCollectorOfStream = 0

            // If user disabled stream, we can simulate stream by getting a bulk call, or OkHttp Service streams it fine anyway
            repository.streamAssistantResponse(settings.baseUrl, settings.apiKey, req)
                .catch { t ->
                    Log.e("MainViewModel", "Stream collection error", t)
                    _currentStreamContent.value = "Connection error: ${t.localizedMessage ?: t.message}"
                    _isStreamingActive.value = false
                }
                .collect { chunk ->
                    when (chunk) {
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

            // Calculation when stream finishes
            val finalLatencyMs = _latencyMs.value.takeIf { it > 0 } ?: (System.currentTimeMillis() - startTime)
            val textToSave = _currentStreamContent.value
            val thinkingToSave = _currentStreamThinking.value.takeIf { it.isNotBlank() }

            val totalCharCount = textToSave.length + (thinkingToSave?.length ?: 0)
            val estimatedCompletionTokens = _tokenCountCompletion.value.takeIf { it > 0 } ?: (totalCharCount / 3.8).toInt().coerceAtLeast(1)
            val lastUserMessage = rawMessagesList.lastOrNull { it.role == "user" }?.content ?: ""
            val estimatedPromptTokens = _tokenCountPrompt.value.takeIf { it > 0 } ?: (lastUserMessage.length / 3.8).toInt().coerceAtLeast(1)

            val totalElapsedSec = (System.currentTimeMillis() - finalLatencyMs) / 1000.0
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
                        totalTokens = estimatedPromptTokens + estimatedCompletionTokens,
                        tokensPerSec = finalTokensPerSec,
                        modelUsed = settings.defaultModel
                    )
                )
            }

            _isStreamingActive.value = false
            _currentStreamContent.value = ""
            _currentStreamThinking.value = ""
        }
    }
}
