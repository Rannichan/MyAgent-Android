package com.example.ui

import androidx.compose.animation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.text.selection.SelectionContainer
import com.example.ui.theme.AgentHubTheme
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.core.spring
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.*
import kotlinx.coroutines.launch

// Color index mapper for dynamic avatars
val AvatarColors = listOf(
    Color(0xFF6200EE), // 0: Violet
    Color(0xFF1E88E5), // 1: Ocean
    Color(0xFF2E7D32), // 2: Emerald
    Color(0xFFE65100), // 3: Amber
    Color(0xFF00796B), // 4: Teal
    Color(0xFFC2185B), // 5: Rose
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppContainer(viewModel: MainViewModel) {
    val settings by viewModel.settingsFlow.collectAsStateWithLifecycle(initialValue = AppSettings())
    val activeSettings = settings ?: AppSettings()

    var currentTab by remember { mutableStateOf("chat") }

    AgentHubTheme(
        themeMode = activeSettings.themeMode,
        themeColor = activeSettings.themeColor
    ) {
        Scaffold(
            bottomBar = {
                NavigationBar(
                    windowInsets = WindowInsets.navigationBars,
                    tonalElevation = 8.dp
                ) {
                    NavigationBarItem(
                        selected = currentTab == "chat",
                        onClick = { currentTab = "chat" },
                        icon = { Icon(Icons.Default.Chat, contentDescription = "对话") },
                        label = { Text("对话") }
                    )
                    NavigationBarItem(
                        selected = currentTab == "personas",
                        onClick = { currentTab = "personas" },
                        icon = { Icon(Icons.Default.RecentActors, contentDescription = "人设配置") },
                        label = { Text("人设/Agent") }
                    )
                    NavigationBarItem(
                        selected = currentTab == "settings",
                        onClick = { currentTab = "settings" },
                        icon = { Icon(Icons.Default.Settings, contentDescription = "设置机能") },
                        label = { Text("设置/分析") }
                    )
                }
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                AnimatedContent(
                    targetState = currentTab,
                    transitionSpec = {
                        fadeIn(animationSpec = spring()) togetherWith fadeOut(animationSpec = spring())
                    },
                    label = "TabTransition"
                ) { targetTab ->
                    when (targetTab) {
                        "chat" -> ChatScreen(viewModel = viewModel)
                        "personas" -> PersonaManagementScreen(viewModel = viewModel)
                        "settings" -> SettingsScreen(viewModel = viewModel)
                    }
                }
            }
        }
    }
}

// ----------------== CHAT SCREEN ==----------------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(viewModel: MainViewModel) {
    val sessions by viewModel.allSessionsFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val currentSessionId by viewModel.currentSessionId.collectAsStateWithLifecycle()
    val activeMessages by viewModel.activeMessages.collectAsStateWithLifecycle()
    val npcs by viewModel.allNpcsFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val agents by viewModel.allAgentsFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val isStreaming by viewModel.isStreamingActive.collectAsStateWithLifecycle()

    var showSessionDrawer by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var showCreateChatDialog by remember { mutableStateOf(false) }

    val filteredSessions = sessions.filter {
        it.title.contains(searchQuery, ignoreCase = true) || it.mode.contains(searchQuery, ignoreCase = true)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header Action Hub
            val activeSession = sessions.find { it.id == currentSessionId }
            val context = LocalContext.current

            TopAppBar(
                title = {
                    Column {
                        Text(
                            activeSession?.title ?: "Agent Hub 对话",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        val settingsItem by viewModel.settingsFlow.collectAsStateWithLifecycle(initialValue = AppSettings())
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.padding(top = 2.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF4ADE80))
                            )
                            Text(
                                (settingsItem?.defaultModel ?: "gpt-4o-mini").uppercase(),
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                },
                navigationIcon = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(start = 4.dp, end = 4.dp)
                    ) {
                        IconButton(onClick = { showSessionDrawer = !showSessionDrawer }) {
                            Icon(Icons.Default.Menu, contentDescription = "会话菜单")
                        }
                        if (activeSession != null) {
                            IconButton(onClick = { viewModel.selectSession(null) }) {
                                Icon(Icons.Default.ArrowBack, contentDescription = "返回主页")
                            }
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.SmartToy,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                },
                actions = {
                    if (activeSession != null) {
                        IconButton(onClick = {
                            viewModel.exportChatHistoryText(activeSession.id) { log ->
                                val sendIntent = android.content.Intent().apply {
                                    action = android.content.Intent.ACTION_SEND
                                    putExtra(android.content.Intent.EXTRA_TEXT, log)
                                    type = "text/plain"
                                }
                                val shareIntent = android.content.Intent.createChooser(sendIntent, "分享对话历史")
                                context.startActivity(shareIntent)
                            }
                        }) {
                            Icon(Icons.Default.Share, contentDescription = "分享对话")
                        }
                        IconButton(onClick = {
                            viewModel.deleteSession(activeSession.id)
                        }) {
                            Icon(Icons.Default.DeleteOutline, contentDescription = "清除会话")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))

            if (activeSession != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.background)
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = Color(0xFF332D41),
                            modifier = Modifier.padding(vertical = 2.dp)
                        ) {
                            Text(
                                " ${activeSession.mode} MODE ",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                        val detailText = when (activeSession.mode) {
                            "NPC" -> {
                                val npcList = npcs.filter { it.name == activeSession.title }
                                if (npcList.isNotEmpty()) "Soul: ${npcList.first().prompt.take(24)}..." else "Custom Persona Config"
                            }
                            "AGENT" -> "Rulebook: Action Workflows"
                            else -> "Sandbox: Standard Prompting"
                        }
                        Text(
                            detailText,
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            modifier = Modifier.widthIn(max = 180.dp)
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)))
                        Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)))
                        Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary))
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
            }

            if (currentSessionId == null) {
                // Intro empty screen
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f)
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier.fillMaxWidth(0.85f)
                    ) {
                        Icon(
                            Icons.Default.SmartToy,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(72.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            "欢迎体验 Agent Hub",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "一个搭载了高级自定义人设（NPC模式）以及专属微型工作规范体系（Agent模式）的AI沙盒系统。请先创建会话或配置模型密钥以触发完美的流式互动！",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Button(onClick = { showCreateChatDialog = true }) {
                                Icon(Icons.Default.Add, contentDescription = null)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("立即创建")
                            }
                        }
                    }
                }
            } else {
                // Main Dialog Stream render panel
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    // Conversation history list
                    val listState = rememberLazyListState()
                    val coroutineScope = rememberCoroutineScope()

                    LaunchedEffect(activeMessages.size, isStreaming) {
                        if (activeMessages.isNotEmpty()) {
                            listState.animateScrollToItem(activeMessages.lastIndex)
                        }
                    }

                    val latency by viewModel.latencyMs.collectAsStateWithLifecycle()
                    val tokenPrompt by viewModel.tokenCountPrompt.collectAsStateWithLifecycle()
                    val tokenCompletion by viewModel.tokenCountCompletion.collectAsStateWithLifecycle()
                    val speedTPS by viewModel.tokensPerSec.collectAsStateWithLifecycle()

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    ) {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            contentPadding = PaddingValues(top = 12.dp, bottom = 86.dp)
                        ) {
                            items(activeMessages) { message ->
                                MessageBubbleItem(
                                    message = message,
                                    isStreamingActive = isStreaming,
                                    onSimulateToolOutput = { toolName, output ->
                                        viewModel.simulateToolResponse(toolName, output)
                                    },
                                    onDeleteMessage = { viewModel.deleteSingleMessage(message.id) }
                                )
                            }
                        }

                        // Floating Stats Dashboard (High Density Overlay matching HTML layout spec)
                        val estimatedCost = (tokenPrompt * 0.0000015 + tokenCompletion * 0.000002)
                        Card(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .padding(12.dp)
                                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f), RoundedCornerShape(12.dp)),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.background.copy(alpha = 0.92f)
                            ),
                            shape = RoundedCornerShape(12.dp),
                            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 10.dp, horizontal = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Latency Column
                                Column(horizontalAlignment = Alignment.Start) {
                                    Text(
                                        "LATENCY",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp, fontSize = 9.sp),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = if (latency > 0) "${latency}ms" else "READY",
                                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold),
                                        color = if (latency > 0) Color(0xFF4ADE80) else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                Box(modifier = Modifier.width(1.dp).height(24.dp).background(MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)))

                                // Throughput Column
                                Column(horizontalAlignment = Alignment.Start) {
                                    Text(
                                        "THROUGHPUT",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp, fontSize = 9.sp),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = "${String.format("%.1f", speedTPS)} t/s",
                                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                Box(modifier = Modifier.width(1.dp).height(24.dp).background(MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)))

                                // Tokens Column
                                Column(horizontalAlignment = Alignment.Start) {
                                    Text(
                                        "TOKENS",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp, fontSize = 9.sp),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = "${tokenPrompt + tokenCompletion}",
                                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                Box(modifier = Modifier.width(1.dp).height(24.dp).background(MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)))

                                // Cost Column
                                Column(horizontalAlignment = Alignment.Start) {
                                    Text(
                                        "COST",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp, fontSize = 9.sp),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = "$${String.format("%.5f", estimatedCost)}",
                                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))

                    // Text Dialog Input box
                    ChatInputPanel(
                        viewModel = viewModel,
                        isStreaming = isStreaming
                    )
                }
            }
        }

        // Navigation Drawer Overlay
        if (showSessionDrawer) {
            // Scrim
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        showSessionDrawer = false
                    }
            )

            // Drawer content surface
            Surface(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(280.dp)
                    .align(Alignment.CenterStart)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        // Prevent click bubble propagation to scrim
                    },
                tonalElevation = 8.dp,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
            ) {
                Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "会话管理",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        IconButton(onClick = { showSessionDrawer = false }) {
                            Icon(Icons.Default.Close, contentDescription = "关闭", modifier = Modifier.size(18.dp))
                        }
                    }

                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("搜索会话...") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )

                    Button(
                        onClick = { showCreateChatDialog = true },
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("开启新对话")
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                    if (filteredSessions.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "暂无会话历史",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(filteredSessions) { item ->
                                val isSelected = item.id == currentSessionId
                                Card(
                                    onClick = {
                                        viewModel.selectSession(item.id)
                                        showSessionDrawer = false
                                    },
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                                    ),
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        // Mode Badge Color
                                        val badgeColor = when (item.mode) {
                                            "NPC" -> Color(0xFF6200EE)
                                            "AGENT" -> Color(0xFF00796B)
                                            else -> Color(0xFF1E88E5)
                                        }

                                        Box(
                                            modifier = Modifier
                                                .size(10.dp)
                                                .clip(CircleShape)
                                                .background(badgeColor)
                                        )

                                        Spacer(modifier = Modifier.width(10.dp))

                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                item.title,
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1
                                            )
                                            Text(
                                                item.lastMessage,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1
                                            )
                                        }

                                        IconButton(
                                            onClick = { viewModel.deleteSession(item.id) },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.Delete,
                                                contentDescription = "删除该会话",
                                                tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // New conversation creation dialog
    if (showCreateChatDialog) {
        Dialog(onDismissRequest = { showCreateChatDialog = false }) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                tonalElevation = 6.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "创建对话沙盒",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )

                    var titleText by remember { mutableStateOf("未命名会话") }
                    var modeChoice by remember { mutableStateOf("STANDARD") } // STANDARD, NPC, AGENT
                    var selectedItemId by remember { mutableStateOf(-1L) }

                    OutlinedTextField(
                        value = titleText,
                        onValueChange = { titleText = it },
                        label = { Text("对话名称") },
                        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                        singleLine = true
                    )

                    // Mode Selection
                    Text(
                        "请选择执行模态:",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("STANDARD" to "标准", "NPC" to "NPC人设", "AGENT" to "Agent").forEach { (v, l) ->
                            FilterChip(
                                selected = modeChoice == v,
                                onClick = {
                                    modeChoice = v
                                    if (v == "STANDARD") selectedItemId = -1L
                                    else if (v == "NPC" && npcs.isNotEmpty()) selectedItemId = npcs.first().id
                                    else if (v == "AGENT" && agents.isNotEmpty()) selectedItemId = agents.first().id
                                },
                                label = { Text(l) }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    if (modeChoice == "NPC") {
                        if (npcs.isEmpty()) {
                            Text(
                                "⚠️ 请先在第二栏“人设/Agent”标签页创立或载入NPC人设数据！",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(bottom = 12.dp)
                            )
                        } else {
                            Text(
                                "绑定NPC个体:",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold
                            )
                            var expandedDropdown by remember { mutableStateOf(false) }
                            val activeNpc = npcs.find { it.id == selectedItemId } ?: npcs.first()
                            if (selectedItemId == -1L) selectedItemId = activeNpc.id

                            Box(modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 12.dp)) {
                                OutlinedButton(
                                    onClick = { expandedDropdown = true },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(activeNpc.name)
                                    Spacer(modifier = Modifier.weight(1f))
                                    Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                                }
                                DropdownMenu(
                                    expanded = expandedDropdown,
                                    onDismissRequest = { expandedDropdown = false }
                                ) {
                                    npcs.forEach { npcItem ->
                                        DropdownMenuItem(
                                            text = { Text(npcItem.name) },
                                            onClick = {
                                                selectedItemId = npcItem.id
                                                if (titleText == "未命名会话" || titleText.startsWith("聊：")) {
                                                    titleText = "聊：Npc ${npcItem.name}"
                                                }
                                                expandedDropdown = false
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    if (modeChoice == "AGENT") {
                        if (agents.isEmpty()) {
                            Text(
                                "⚠️ 请先在第二栏配置或生成Agent！",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(bottom = 12.dp)
                            )
                        } else {
                            Text(
                                "绑定Agent实体:",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold
                            )
                            var expandedDropdown by remember { mutableStateOf(false) }
                            val activeAgent = agents.find { it.id == selectedItemId } ?: agents.first()
                            if (selectedItemId == -1L) selectedItemId = activeAgent.id

                            Box(modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 12.dp)) {
                                OutlinedButton(
                                    onClick = { expandedDropdown = true },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(activeAgent.name)
                                    Spacer(modifier = Modifier.weight(1f))
                                    Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                                }
                                DropdownMenu(
                                    expanded = expandedDropdown,
                                    onDismissRequest = { expandedDropdown = false }
                                ) {
                                    agents.forEach { agentItem ->
                                        DropdownMenuItem(
                                            text = { Text(agentItem.name) },
                                            onClick = {
                                                selectedItemId = agentItem.id
                                                if (titleText == "未命名会话" || titleText.startsWith("跑：")) {
                                                    titleText = "跑：Agent ${agentItem.name}"
                                                }
                                                expandedDropdown = false
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = { showCreateChatDialog = false }) {
                            Text("取消")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                if (modeChoice == "NPC" && selectedItemId == -1L) return@Button
                                if (modeChoice == "AGENT" && selectedItemId == -1L) return@Button
                                viewModel.createNewSession(titleText, modeChoice, selectedItemId)
                                showCreateChatDialog = false
                            }
                        ) {
                            Text("确定")
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatInputPanel(
    viewModel: MainViewModel,
    isStreaming: Boolean
) {
    val settings by viewModel.settingsFlow.collectAsStateWithLifecycle(initialValue = AppSettings())
    val activeSettings = settings ?: AppSettings()
    var inputStr by remember { mutableStateOf("") }
    var modelExpanded by remember { mutableStateOf(false) }
    val modelsList by viewModel.modelsList.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp)
    ) {
        // Option quick toggle strip
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Model choose dropdown trigger
            Box {
                OutlinedButton(
                    onClick = { modelExpanded = true },
                    modifier = Modifier.height(36.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.ModelTraining, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        activeSettings.defaultModel,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1
                    )
                    Icon(Icons.Default.ArrowDropDown, contentDescription = null, modifier = Modifier.size(16.dp))
                }
                DropdownMenu(
                    expanded = modelExpanded,
                    onDismissRequest = { modelExpanded = false }
                ) {
                    if (modelsList.isEmpty()) {
                        DropdownMenuItem(
                            text = { Text("没有载入模型，请到设置测试连接") },
                            onClick = { modelExpanded = false }
                        )
                    } else {
                        modelsList.forEach { modelName ->
                            DropdownMenuItem(
                                text = { Text(modelName) },
                                onClick = {
                                    viewModel.updateSelectedModel(modelName)
                                    modelExpanded = false
                                }
                            )
                        }
                    }
                }
            }

            // Toggles
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Stream toggler
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("流式:", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(end = 4.dp))
                    Switch(
                        checked = activeSettings.isStreaming,
                        onCheckedChange = { viewModel.updateToggles(it, activeSettings.isThinkingModeEnabled, activeSettings.isToolCallsEnabled) },
                        modifier = Modifier.scale(0.7f)
                    )
                }

                // Thinking toggler
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("思考:", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(end = 4.dp))
                    Switch(
                        checked = activeSettings.isThinkingModeEnabled,
                        onCheckedChange = { viewModel.updateToggles(activeSettings.isStreaming, it, activeSettings.isToolCallsEnabled) },
                        modifier = Modifier.scale(0.7f)
                    )
                }

                // Tools toggler
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("工具:", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(end = 4.dp))
                    Switch(
                        checked = activeSettings.isToolCallsEnabled,
                        onCheckedChange = { viewModel.updateToggles(activeSettings.isStreaming, activeSettings.isThinkingModeEnabled, it) },
                        modifier = Modifier.scale(0.7f)
                    )
                }
            }
        }

        // Input text line
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = inputStr,
                onValueChange = { inputStr = it },
                placeholder = { Text("说点什么...") },
                modifier = Modifier
                    .weight(1f)
                    .imePadding(),
                maxLines = 4,
                shape = RoundedCornerShape(12.dp),
                keyboardOptions = KeyboardOptions(
                    imeAction = ImeAction.Send,
                    keyboardType = KeyboardType.Text
                ),
                keyboardActions = KeyboardActions(
                    onSend = {
                        if (inputStr.isNotBlank() && !isStreaming) {
                            viewModel.sendMessage(inputStr)
                            inputStr = ""
                        }
                    }
                )
            )

            Spacer(modifier = Modifier.width(8.dp))

            FloatingActionButton(
                onClick = {
                    if (inputStr.isNotBlank() && !isStreaming) {
                        viewModel.sendMessage(inputStr)
                        inputStr = ""
                    }
                },
                containerColor = if (isStreaming) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.primary,
                contentColor = if (isStreaming) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onPrimary,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.size(52.dp)
            ) {
                if (isStreaming) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Icon(Icons.Default.Send, contentDescription = "发送")
                }
            }
        }
    }
}

// Custom scale extension helper for easy sizing
fun Modifier.scale(scale: Float): Modifier = this.then(
    Modifier.graphicsLayer(scaleX = scale, scaleY = scale)
)

// ----------------== MSG BUBBLE COMPONENT ==----------------
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MessageBubbleItem(
    message: ChatMessage,
    isStreamingActive: Boolean,
    onSimulateToolOutput: (String, String) -> Unit,
    onDeleteMessage: () -> Unit
) {
    val isUser = message.role == "user"
    val isSystem = message.role == "system"
    val clipboardManager = LocalClipboardManager.current

    // Collapse when streaming is done
    var isThinkingExpanded by remember(message.id) { mutableStateOf(isStreamingActive) }
    var isToolExpanded by remember(message.id) { mutableStateOf(isStreamingActive) }
    var contextMenuExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(isStreamingActive) {
        if (!isStreamingActive) {
            isThinkingExpanded = false
            isToolExpanded = false
        }
    }

    val bgBrush = if (isUser) {
        Brush.linearGradient(
            colors = listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.primary)
        )
    } else if (isSystem) {
        Brush.linearGradient(
            colors = listOf(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.surfaceVariant)
        )
    } else {
        Brush.linearGradient(
            colors = listOf(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.primaryContainer)
        )
    }

    val paddingValues = if (isUser) {
        PaddingValues(start = 50.dp, end = 4.dp)
    } else {
        PaddingValues(start = 4.dp, end = 50.dp)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(paddingValues),
        contentAlignment = if (isUser) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        Surface(
            tonalElevation = if (isUser) 0.dp else 1.dp,
            border = if (!isUser && !isSystem) BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)) else null,
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (isUser) 16.dp else 2.dp,
                bottomEnd = if (isUser) 2.dp else 16.dp
            ),
            modifier = Modifier.combinedClickable(
                onClick = {},
                onLongClick = { contextMenuExpanded = true }
            )
        ) {
            Box(
                modifier = Modifier
                    .background(bgBrush)
                    .padding(12.dp)
            ) {
                Column {
                    // Display Badge Name/Role
                    Row(
                        modifier = Modifier.padding(bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = when {
                                isUser -> Icons.Default.Person
                                isSystem -> Icons.Default.Terminal
                                else -> Icons.Default.SmartToy
                            },
                            contentDescription = null,
                            modifier = Modifier.size(12.dp),
                            tint = if (isUser) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f) else MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = when {
                                isUser -> "你"
                                isSystem -> "系统/工具反馈"
                                else -> "AI 助理"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = if (isUser) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }

                    // Render thinking content block if any
                    if (!message.thinkingContent.isNullOrBlank()) {
                        ElevatedCard(
                            onClick = { isThinkingExpanded = !isThinkingExpanded },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            colors = CardDefaults.elevatedCardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                            )
                        ) {
                            Column(modifier = Modifier.padding(8.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(
                                        Icons.Outlined.Psychology,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.tertiary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        "思考/分析深度链路...",
                                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.tertiary,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Icon(
                                        imageVector = if (isThinkingExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                        contentDescription = "展缩",
                                        modifier = Modifier.size(16.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (isThinkingExpanded) {
                                    Text(
                                        message.thinkingContent,
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            fontStyle = FontStyle.Italic,
                                            fontFamily = FontFamily.Monospace,
                                            lineHeight = 16.sp
                                        ),
                                        color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.9f),
                                        modifier = Modifier.padding(top = 6.dp)
                                    )
                                }
                            }
                        }
                    }

                    // Render standard or intercepted content
                    val toolRegex = Regex("""<tool_call name="([^"]+)">([^<]*)</tool_call>""")
                    val matches = toolRegex.findAll(message.content).toList()

                    if (matches.isNotEmpty() && !isUser) {
                        ElevatedCard(
                            onClick = { isToolExpanded = !isToolExpanded },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            colors = CardDefaults.elevatedCardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                            )
                        ) {
                            Column(modifier = Modifier.padding(8.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(
                                        Icons.Default.Build,
                                        contentDescription = null,
                                        tint = Color(0xFFD97706),
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        "拦截工具执行链路... (${matches.size})",
                                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color(0xFFD97706),
                                        modifier = Modifier.weight(1f)
                                    )
                                    Icon(
                                        imageVector = if (isToolExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                        contentDescription = "展缩",
                                        modifier = Modifier.size(16.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (isToolExpanded) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    matches.forEach { match ->
                                        val toolName = match.groupValues[1]
                                        val toolArgs = match.groupValues[2]

                                        ToolCallInterceptCard(
                                            toolName = toolName,
                                            argumentsJson = toolArgs,
                                            onSimulateResult = { output ->
                                                onSimulateToolOutput(toolName, output)
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        // Also render any leftover text around matches
                        val contentWithoutToolCall = message.content.replace(toolRegex, "").trim()
                        if (contentWithoutToolCall.isNotBlank()) {
                            Text(
                                text = renderMarkdown(contentWithoutToolCall),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    } else {
                        // Regular Message Text
                        Text(
                            text = renderMarkdown(message.content),
                            style = MaterialTheme.typography.bodyMedium,
                            color = when {
                                isUser -> MaterialTheme.colorScheme.onPrimary
                                isSystem -> MaterialTheme.colorScheme.onSurfaceVariant
                                else -> MaterialTheme.colorScheme.onPrimaryContainer
                            }
                        )
                    }

                    // Display metrics metadata badge in Assistant footer
                    if (!isUser && !isSystem && message.promptTokens > 0) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "⏱️ ${message.latencyMs}ms | ⚡ ${String.format("%.1f", message.tokensPerSec)} t/s | 📝 ${message.totalTokens} tokens",
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                            )
                        }
                    }
                }
            }
        }

        // Long press menu
        DropdownMenu(
            expanded = contextMenuExpanded,
            onDismissRequest = { contextMenuExpanded = false }
        ) {
            DropdownMenuItem(
                text = { Text("复制文本内容") },
                onClick = {
                    clipboardManager.setText(AnnotatedString(message.content))
                    contextMenuExpanded = false
                }
            )
            DropdownMenuItem(
                text = { Text("删除该气泡") },
                onClick = {
                    onDeleteMessage()
                    contextMenuExpanded = false
                }
            )
        }
    }
}

// Custom Markdown styler mapping
fun renderMarkdown(raw: String): AnnotatedString {
    val builder = AnnotatedString.Builder()
    var index = 0

    // Bold search state regex-ready mapping helper
    val blockSplit = raw.split("```")
    for (i in blockSplit.indices) {
        val segment = blockSplit[i]
        if (i % 2 == 1) { // It's a code block
            builder.withStyle(
                SpanStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                    background = Color(0xFF1F1E24),
                    color = Color(0xFFD0BCFF)
                )
            ) {
                append("\n" + segment.trim() + "\n")
            }
        } else { // Standard markup text segment, look for inline code `code` or bold **bold**
            val inlineSplit = segment.split("`")
            for (j in inlineSplit.indices) {
                val subSegment = inlineSplit[j]
                if (j % 2 == 1) { // It's inline code
                    builder.withStyle(
                        SpanStyle(
                            fontFamily = FontFamily.Monospace,
                            background = Color(0xFF332D41),
                            color = Color(0xFFD0BCFF),
                            fontSize = 13.sp
                        )
                    ) {
                        append(subSegment)
                    }
                } else { // Handle **bold**
                    val boldSplit = subSegment.split("**")
                    for (k in boldSplit.indices) {
                        val boldText = boldSplit[k]
                        if (k % 2 == 1) {
                            builder.withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                                append(boldText)
                            }
                        } else {
                            builder.append(boldText)
                        }
                    }
                }
            }
        }
    }
    return builder.toAnnotatedString()
}

@Composable
fun ToolCallInterceptCard(
    toolName: String,
    argumentsJson: String,
    onSimulateResult: (String) -> Unit
) {
    var showSimulatorModal by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Build,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "拦截工具调用请求",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                "工具名称: $toolName",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold
            )

            SelectionContainer {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp)
                ) {
                    Text(
                        argumentsJson,
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        modifier = Modifier.padding(6.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Button(
                onClick = { showSimulatorModal = true },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("输入并模拟该函数执行反馈")
            }
        }
    }

    if (showSimulatorModal) {
        Dialog(onDismissRequest = { showSimulatorModal = false }) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                tonalElevation = 8.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "沙盒工具模拟响应馈送",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    Text(
                        "执行函数 '$toolName' 并注入伪造数据:",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    var simulatedValueText by remember {
                        mutableStateOf(
                            when (toolName) {
                                "get_weather" -> "{\"status\":\"success\",\"location\":\"Tokyo\",\"temp\":\"22°C\",\"condition\":\"Cloudy\"}"
                                "get_server_status" -> "{\"status\":\"active\",\"redundantNodes\":2,\"cpuLoad\":\"12%\",\"latency\":\"14ms\"}"
                                "execute_query" -> "{\"rowsAffected\": 42, \"metrics\": \"OK\", \"duration\": \"1ms\"}"
                                else -> "{\"status\":\"success\",\"message\":\"Mock return string for execution sandbox\"}"
                            }
                        )
                    }

                    OutlinedTextField(
                        value = simulatedValueText,
                        onValueChange = { simulatedValueText = it },
                        label = { Text("反馈 JSON/文本") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp)
                            .padding(bottom = 12.dp),
                        maxLines = 8
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = { showSimulatorModal = false }) {
                            Text("取消")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                onSimulateResult(simulatedValueText)
                                showSimulatorModal = false
                            }
                        ) {
                            Text("投递反馈数据")
                        }
                    }
                }
            }
        }
    }
}

// ----------------== PERSONA / AGENT MANAGEMENT ==----------------
@Composable
fun PersonaManagementScreen(viewModel: MainViewModel) {
    val npcs by viewModel.allNpcsFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val agents by viewModel.allAgentsFlow.collectAsStateWithLifecycle(initialValue = emptyList())

    var activeSubTab by remember { mutableStateOf("npcs") } // "npcs" or "agents"
    var showCreateNpcDialog by remember { mutableStateOf(false) }
    var showCreateAgentDialog by remember { mutableStateOf(false) }
    var editingNpc by remember { mutableStateOf<NpcCharacter?>(null) }
    var editingAgent by remember { mutableStateOf<AgentConfig?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            "系统模态个体管理工作室",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        // Sub tab strip selector
        TabRow(
            selectedTabIndex = if (activeSubTab == "npcs") 0 else 1,
            modifier = Modifier.padding(bottom = 16.dp)
        ) {
            Tab(
                selected = activeSubTab == "npcs",
                onClick = { activeSubTab = "npcs" },
                text = { Text("Custom NPCs (${npcs.size})") }
            )
            Tab(
                selected = activeSubTab == "agents",
                onClick = { activeSubTab = "agents" },
                text = { Text("Apt Agents (${agents.size})") }
            )
        }

        if (activeSubTab == "npcs") {
            // NPC List UI Grid
            Box(modifier = Modifier.weight(1f)) {
                if (npcs.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.Contacts, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("尚未建立任何自定义NPC人设", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                } else {
                    LazyVerticalGridInside(
                        items = npcs,
                        modifier = Modifier.fillMaxSize()
                    ) { npcItem ->
                        NpcConfigCard(
                            npc = npcItem,
                            onEdit = { editingNpc = npcItem },
                            onDelete = { viewModel.removeNpc(npcItem.id) },
                            onStartChat = {
                                viewModel.createNewSession("聊：Npc ${npcItem.name}", "NPC", npcItem.id)
                            }
                        )
                    }
                }

                FloatingActionButton(
                    onClick = { showCreateNpcDialog = true },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(16.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = "新增")
                }
            }
        } else {
            // Agent workspace list
            Box(modifier = Modifier.weight(1f)) {
                if (agents.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.Architecture, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("尚无 Agent 设定规范，请点击右下角自动装配", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                } else {
                    LazyVerticalGridInside(
                        items = agents,
                        modifier = Modifier.fillMaxSize()
                    ) { agentItem ->
                        AgentConfigCard(
                            agent = agentItem,
                            onEdit = { editingAgent = agentItem },
                            onDelete = { viewModel.removeAgent(agentItem.id) },
                            onStartChat = {
                                viewModel.createNewSession("跑：Agent ${agentItem.name}", "AGENT", agentItem.id)
                            }
                        )
                    }
                }

                FloatingActionButton(
                    onClick = { showCreateAgentDialog = true },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(16.dp)
                ) {
                    Icon(Icons.Default.NoteAdd, contentDescription = "配置系统规格文档")
                }
            }
        }
    }

    // New NPC dialogue builder modal
    val activeNpcEditMode = editingNpc != null
    if (showCreateNpcDialog || activeNpcEditMode) {
        Dialog(onDismissRequest = {
            showCreateNpcDialog = false
            editingNpc = null
        }) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                tonalElevation = 8.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp)
            ) {
                val originalNpc = editingNpc
                var nameText by remember(originalNpc) { mutableStateOf(originalNpc?.name ?: "") }
                var promptText by remember(originalNpc) { mutableStateOf(originalNpc?.prompt ?: "") }
                var greetingText by remember(originalNpc) { mutableStateOf(originalNpc?.greeting ?: "") }
                var colorIndexSelected by remember(originalNpc) { mutableStateOf(originalNpc?.avatarColorOrdinal ?: 0) }

                Column(
                    modifier = Modifier
                        .padding(16.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        if (activeNpcEditMode) "编辑 NPC 人设特征" else "定制全新 NPC 伴侣",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    OutlinedTextField(
                        value = nameText,
                        onValueChange = { nameText = it },
                        label = { Text("人设名称 (e.g. 雅典娜)") },
                        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = greetingText,
                        onValueChange = { greetingText = it },
                        label = { Text("对话开场白 (Greeting)") },
                        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                    )

                    OutlinedTextField(
                        value = promptText,
                        onValueChange = { promptText = it },
                        label = { Text("全息 System Prompt (行动纲领)") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(150.dp)
                            .padding(bottom = 12.dp),
                        maxLines = 10
                    )

                    Text(
                        "选择标志化代表色:",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AvatarColors.forEachIndexed { idx, col ->
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(col)
                                    .clickable { colorIndexSelected = idx }
                                    .border(
                                        width = if (colorIndexSelected == idx) 3.dp else 0.dp,
                                        color = if (colorIndexSelected == idx) MaterialTheme.colorScheme.onBackground else Color.Transparent,
                                        shape = CircleShape
                                    )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = {
                            showCreateNpcDialog = false
                            editingNpc = null
                        }) {
                            Text("放弃")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                if (nameText.isNotBlank() && promptText.isNotBlank()) {
                                    if (activeNpcEditMode && originalNpc != null) {
                                        viewModel.saveNpc(
                                            originalNpc.copy(
                                                name = nameText,
                                                prompt = promptText,
                                                greeting = greetingText.ifBlank { "你好！我们准备开始啦" },
                                                avatarColorOrdinal = colorIndexSelected
                                            )
                                        )
                                        editingNpc = null
                                    } else {
                                        viewModel.saveNpc(
                                            NpcCharacter(
                                                name = nameText,
                                                prompt = promptText,
                                                greeting = greetingText.ifBlank { "你好！我们准备开始啦" },
                                                avatarColorOrdinal = colorIndexSelected
                                            )
                                        )
                                        showCreateNpcDialog = false
                                    }
                                }
                            }
                        ) {
                            Text(if (activeNpcEditMode) "保存并更新" else "构建实体并入库")
                        }
                    }
                }
            }
        }
    }

    // New Agent config template modal (featuring 5 separate Markdown files tabs editing)
    val activeAgentEditMode = editingAgent != null
    if (showCreateAgentDialog || activeAgentEditMode) {
        Dialog(onDismissRequest = {
            showCreateAgentDialog = false
            editingAgent = null
        }) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                tonalElevation = 8.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(4.dp)
            ) {
                val originalAgent = editingAgent
                var agentName by remember(originalAgent) { mutableStateOf(originalAgent?.name ?: "") }
                var activeEditorTab by remember { mutableStateOf("agent") } // "agent", "identity", "memory", "soul", "user", "tools"
                
                // Content stores initialized with templates
                var fAgent by remember(originalAgent) { mutableStateOf(originalAgent?.agentMd ?: DEFAULT_AGENT_MD) }
                var fIdentity by remember(originalAgent) { mutableStateOf(originalAgent?.identityMd ?: DEFAULT_IDENTITY_MD) }
                var fMemory by remember(originalAgent) { mutableStateOf(originalAgent?.memoryMd ?: DEFAULT_MEMORY_MD) }
                var fSoul by remember(originalAgent) { mutableStateOf(originalAgent?.soulMd ?: DEFAULT_SOUL_MD) }
                var fUser by remember(originalAgent) { mutableStateOf(originalAgent?.userMd ?: DEFAULT_USER_MD) }
                var fTools by remember(originalAgent) {
                    mutableStateOf(
                        originalAgent?.toolsJson ?: """[
  {
    "name": "get_weather",
    "description": "Query meteorological dynamics for an indicated location.",
    "parametersJson": "{\"type\":\"object\"}"
  }
]"""
                    )
                }

                var colorIndexSelected by remember(originalAgent) { mutableStateOf(originalAgent?.avatarColorOrdinal ?: 3) }

                Column(
                    modifier = Modifier
                        .padding(14.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        if (activeAgentEditMode) "编辑微型 Agent 规格" else "装配超级 Agent 实体 (Markdown 矩阵)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    OutlinedTextField(
                        value = agentName,
                        onValueChange = { agentName = it },
                        label = { Text("Agent 名称 (e.g. 运维分析官)") },
                        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                        singleLine = true
                    )

                    // Markdown File tabs
                    ScrollableTabRow(
                        selectedTabIndex = when(activeEditorTab) {
                            "agent" -> 0
                            "identity" -> 1
                            "memory" -> 2
                            "soul" -> 3
                            "user" -> 4
                            else -> 5
                        },
                        modifier = Modifier.padding(bottom = 8.dp)
                    ) {
                        Tab(selected = activeEditorTab == "agent", onClick = { activeEditorTab = "agent" }, text = { Text("Agent.md") })
                        Tab(selected = activeEditorTab == "identity", onClick = { activeEditorTab = "identity" }, text = { Text("Identity.md") })
                        Tab(selected = activeEditorTab == "memory", onClick = { activeEditorTab = "memory" }, text = { Text("Memory.md") })
                        Tab(selected = activeEditorTab == "soul", onClick = { activeEditorTab = "soul" }, text = { Text("Soul.md") })
                        Tab(selected = activeEditorTab == "user", onClick = { activeEditorTab = "user" }, text = { Text("User.md") })
                        Tab(selected = activeEditorTab == "tools", onClick = { activeEditorTab = "tools" }, text = { Text("Tools.json") })
                    }

                    // Edit body area
                    when (activeEditorTab) {
                        "agent" -> {
                            OutlinedTextField(
                                value = fAgent,
                                onValueChange = { fAgent = it },
                                label = { Text("Agent.md (规范蓝图)") },
                                modifier = Modifier.fillMaxWidth().height(160.dp),
                                maxLines = 15
                            )
                        }
                        "identity" -> {
                            OutlinedTextField(
                                value = fIdentity,
                                onValueChange = { fIdentity = it },
                                label = { Text("Identity.md (人设立项)") },
                                modifier = Modifier.fillMaxWidth().height(160.dp),
                                maxLines = 15
                            )
                        }
                        "memory" -> {
                            OutlinedTextField(
                                value = fMemory,
                                onValueChange = { fMemory = it },
                                label = { Text("Memory.md (认知日志)") },
                                modifier = Modifier.fillMaxWidth().height(160.dp),
                                maxLines = 15
                            )
                        }
                        "soul" -> {
                            OutlinedTextField(
                                value = fSoul,
                                onValueChange = { fSoul = it },
                                label = { Text("Soul.md (性格底层)") },
                                modifier = Modifier.fillMaxWidth().height(160.dp),
                                maxLines = 15
                            )
                        }
                        "user" -> {
                            OutlinedTextField(
                                value = fUser,
                                onValueChange = { fUser = it },
                                label = { Text("User.md (用户肖像)") },
                                modifier = Modifier.fillMaxWidth().height(160.dp),
                                maxLines = 15
                            )
                        }
                        "tools" -> {
                            OutlinedTextField(
                                value = fTools,
                                onValueChange = { fTools = it },
                                label = { Text("自定义工具 JSON 格式") },
                                modifier = Modifier.fillMaxWidth().height(160.dp),
                                maxLines = 15
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        "定制特征色彩 (Avatar Color):",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        AvatarColors.forEachIndexed { index, color ->
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(color)
                                    .clickable { colorIndexSelected = index }
                                    .border(
                                        width = if (colorIndexSelected == index) 3.dp else 0.dp,
                                        color = if (colorIndexSelected == index) MaterialTheme.colorScheme.onBackground else Color.Transparent,
                                        shape = CircleShape
                                    )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = {
                            showCreateAgentDialog = false
                            editingAgent = null
                        }) {
                            Text("放弃")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                if (agentName.isNotBlank()) {
                                    if (activeAgentEditMode && originalAgent != null) {
                                        viewModel.saveAgent(
                                            originalAgent.copy(
                                                name = agentName,
                                                agentMd = fAgent,
                                                identityMd = fIdentity,
                                                memoryMd = fMemory,
                                                soulMd = fSoul,
                                                userMd = fUser,
                                                toolsJson = fTools,
                                                avatarColorOrdinal = colorIndexSelected
                                            )
                                        )
                                        editingAgent = null
                                    } else {
                                        viewModel.saveAgent(
                                            AgentConfig(
                                                name = agentName,
                                                agentMd = fAgent,
                                                identityMd = fIdentity,
                                                memoryMd = fMemory,
                                                soulMd = fSoul,
                                                userMd = fUser,
                                                toolsJson = fTools,
                                                avatarColorOrdinal = colorIndexSelected
                                            )
                                        )
                                        showCreateAgentDialog = false
                                    }
                                }
                            }
                        ) {
                            Text(if (activeAgentEditMode) "保存并更新" else "组装并激活")
                        }
                    }
                }
            }
        }
    }
}

// Custom Grid List items container for flexible density view
@Composable
fun <T> LazyVerticalGridInside(
    items: List<T>,
    modifier: Modifier = Modifier,
    content: @Composable (T) -> Unit
) {
    Column(
        modifier = modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items.forEach { item ->
            content(item)
        }
    }
}

@Composable
fun NpcConfigCard(
    npc: NpcCharacter,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onStartChat: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Colored Circle avatar with first letter name
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(AvatarColors[npc.avatarColorOrdinal.coerceIn(0, AvatarColors.lastIndex)]),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = npc.name.take(1).uppercase(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        npc.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "开场白: ${npc.greeting}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }

                IconButton(onClick = onEdit) {
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = "编辑",
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                    )
                }

                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "删除",
                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Persona prompt body summary shaded
            Surface(
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.03f),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    npc.prompt,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(8.dp),
                    maxLines = 3
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = onStartChat,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(Icons.Default.Forum, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("以此NPC身份开启新聊天")
            }
        }
    }
}

@Composable
fun AgentConfigCard(
    agent: AgentConfig,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onStartChat: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(AvatarColors[agent.avatarColorOrdinal.coerceIn(0, AvatarColors.lastIndex)]),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = agent.name.take(1).uppercase(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            agent.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(4.dp))
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                        ) {
                            Text(
                                "5 md files active",
                                style = androidx.compose.ui.text.TextStyle(fontSize = 10.sp, fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                    Text(
                        "集成了自定义工具调用的底层配置...",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                IconButton(onClick = onEdit) {
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = "编辑该Agent",
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                    )
                }

                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "删除该Agent",
                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Show custom files labels
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                listOf("Agent.md", "Identity.md", "Memory.md", "Soul.md", "User.md").forEach { lab ->
                    AssistChip(
                        onClick = {},
                        label = { Text(lab, fontSize = 10.sp) },
                        modifier = Modifier.height(24.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Button(
                onClick = onStartChat,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(Icons.Default.PlayCircleFilled, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("启动该 Agent 双向沙盒")
            }
        }
    }
}

// ----------------== GENERAL SYSTEM SETTINGS ==----------------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: MainViewModel) {
    val settings by viewModel.settingsFlow.collectAsStateWithLifecycle(initialValue = AppSettings())
    val activeSettings = settings ?: AppSettings()
    val isTesting by viewModel.isTestingConnection.collectAsStateWithLifecycle()
    val modelsList by viewModel.modelsList.collectAsStateWithLifecycle()
    val allMessagesHistory by viewModel.allSessionsFlow.collectAsStateWithLifecycle(initialValue = emptyList())

    // Internal edits
    var editedUrl by remember { mutableStateOf(activeSettings.baseUrl) }
    var editedKey by remember { mutableStateOf(activeSettings.apiKey) }
    var isApiKeyMasked by remember { mutableStateOf(true) }

    LaunchedEffect(activeSettings.baseUrl, activeSettings.apiKey) {
        editedUrl = activeSettings.baseUrl
        editedKey = activeSettings.apiKey
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            "设置与基准性能仪表盘",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        // API Configurations section
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    "OpenAI Compatible API 配置",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                OutlinedTextField(
                    value = editedUrl,
                    onValueChange = {
                        editedUrl = it
                        viewModel.updateBaseUrl(it)
                    },
                    label = { Text("API 端点 (Base URL)") },
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                    placeholder = { Text("https://api.openai.com/v1/") },
                    singleLine = true
                )

                OutlinedTextField(
                    value = editedKey,
                    onValueChange = {
                        editedKey = it
                        viewModel.updateApiKey(it)
                    },
                    label = { Text("API 凭证 (API Key)") },
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                    visualTransformation = if (isApiKeyMasked) PasswordVisualTransformation() else VisualTransformation.None,
                    trailingIcon = {
                        IconButton(onClick = { isApiKeyMasked = !isApiKeyMasked }) {
                            Icon(
                                imageVector = if (isApiKeyMasked) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = null
                            )
                        }
                    },
                    singleLine = true
                )

                Button(
                    onClick = { viewModel.fetchAvailableModels() },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isTesting
                ) {
                    if (isTesting) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("连接测试并拉取模型中...")
                    } else {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("测试连接并强制获取模型列表 (/v1/models)")
                    }
                }

                if (modelsList.isNotEmpty()) {
                    Text(
                        "已加载模型总量: ${modelsList.size}个 ⬇️",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF00796B),
                        modifier = Modifier.padding(top = 10.dp)
                    )
                }
            }
        }

        // Hyperparameters Tuning Configuration
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    "对话核心超参数调谐",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                // Temperature
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("温度 (Temperature): ${String.format("%.2f", activeSettings.temperature)}", style = MaterialTheme.typography.bodyMedium)
                }
                Slider(
                    value = activeSettings.temperature,
                    onValueChange = { viewModel.updateHyperparams(it, activeSettings.maxTokens) },
                    valueRange = 0.0f..1.5f,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Max length
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("单次最大输出代币限制 (Max Tokens): ${activeSettings.maxTokens}", style = MaterialTheme.typography.bodyMedium)
                }
                Slider(
                    value = activeSettings.maxTokens.toFloat(),
                    onValueChange = { viewModel.updateHyperparams(activeSettings.temperature, it.toInt()) },
                    valueRange = 256.0f..4096.0f,
                    steps = 15,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        // Palette Themes
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    "风格美学与主题配置",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                // Light / Dark modes options
                Text("主题色彩模式:", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("system" to "System", "light" to "明亮", "dark" to "暗夜").forEach { (m, l) ->
                        FilterChip(
                            selected = activeSettings.themeMode == m,
                            onClick = { viewModel.updateThemeOptions(m, activeSettings.themeColor) },
                            label = { Text(l) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Color Themes selector
                Text("精美美化皮肤色调:", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf("violet" to "薰衣紫", "blue" to "深邃蓝", "green" to "翡翠绿", "amber" to "赛博橙").forEach { (c, title) ->
                        FilterChip(
                            selected = activeSettings.themeColor == c,
                            onClick = { viewModel.updateThemeOptions(activeSettings.themeMode, c) },
                            label = { Text(title) }
                        )
                    }
                }
            }
        }

        // Metrics Visual Chart (Custom Canvas performance graphs)
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    "沙盒延迟及大模吞吐极速基准",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                // Visual Analytics via Canvas (drawing visual benchmarks comparing models throughput speed e.g. tokens per second)
                val primaryColor = MaterialTheme.colorScheme.primary
                val secondaryColor = MaterialTheme.colorScheme.secondary
                val textOnSurface = MaterialTheme.colorScheme.onSurface

                Text(
                    "大模型平均输出速度基准 (Tokens / Sec)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 10.dp)
                )

                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp)
                ) {
                    val width = size.width
                    val height = size.height

                    // Draw Grid lines
                    for (x in 1..4) {
                        val gap = height / 5
                        drawLine(
                            color = textOnSurface.copy(alpha = 0.08f),
                            start = Offset(0f, gap * x),
                            end = Offset(width, gap * x),
                            strokeWidth = 2f
                        )
                    }

                    // Simulated models tokens Benchmark: gpt-4o-mini (55 tps), reasoning model (32 tps), local model (70 tps), other (20 tps)
                    val benchmarks = listOf(
                        BenchmarkItem("4o-mini", 55f),
                        BenchmarkItem("DeepSeek", 32f),
                        BenchmarkItem("Gemini", 68f),
                        BenchmarkItem("Custom", 22f)
                    )

                    val maxVal = 80f
                    val barSpacing = width / benchmarks.size
                    val barWidth = barSpacing * 0.5f

                    benchmarks.forEachIndexed { i, item ->
                        val barHeight = (item.value / maxVal) * (height - 30.dp.toPx())
                        val topLeftX = (barSpacing * i) + (barSpacing * 0.25f)
                        val topLeftY = height - barHeight - 20.dp.toPx()

                        // Draw solid visual bars with rounded visual borders
                        drawRoundRect(
                            color = if (i == 2) primaryColor else secondaryColor.copy(alpha = 0.7f),
                            topLeft = Offset(topLeftX, topLeftY),
                            size = Size(barWidth, barHeight),
                            cornerRadius = CornerRadius(8f, 8f)
                        )
                    }
                }

                // Benchmarks label legends
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    Text("GPT-4o (55 t/s)", style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp), fontWeight = FontWeight.Bold)
                    Text("DeepSeek (32 t/s)", style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp), fontWeight = FontWeight.Bold)
                    Text("Gemini (68 t/s)", style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp), fontWeight = FontWeight.Bold)
                    Text("Custom (22 t/s)", style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp), fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    "诊断提示: 优化代理吞吐量时，流式参数 (isStreaming) 能显著抹消首字传输延迟感知，最大代币限制将决定上下文窗口保留厚度。",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp, fontStyle = FontStyle.Italic),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

data class BenchmarkItem(val name: String, val value: Float)
