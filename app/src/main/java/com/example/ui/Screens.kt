package com.example.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.text.selection.TextSelectionColors
import com.example.ui.theme.AgentHubTheme
import androidx.activity.compose.BackHandler
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.data.*
import kotlinx.coroutines.flow.sample
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

private fun randomAvatarColorOrdinal(): Int = (0..AvatarColors.lastIndex).random()

private data class AvatarUiModel(
    val name: String,
    val avatarColorOrdinal: Int,
    val avatarUri: String?
)

private fun avatarInitial(name: String): String {
    return name.trim().takeIf { it.isNotEmpty() }?.take(1)?.uppercase() ?: "?"
}

@Composable
private fun EntityAvatar(
    name: String,
    avatarColorOrdinal: Int,
    avatarUri: String?,
    modifier: Modifier = Modifier,
    textStyle: TextStyle? = null
) {
    val avatarColor = AvatarColors[avatarColorOrdinal.coerceIn(0, AvatarColors.lastIndex)]
    val avatarModel = remember(avatarUri) { avatarUri?.takeIf { it.isNotBlank() }?.let(Uri::parse) }
    val resolvedTextStyle = textStyle ?: MaterialTheme.typography.titleMedium

    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(avatarColor),
        contentAlignment = Alignment.Center
    ) {
        if (avatarModel != null) {
            AsyncImage(
                model = avatarModel,
                contentDescription = name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Text(
                text = avatarInitial(name),
                style = resolvedTextStyle,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
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
                    tonalElevation = 8.dp,
                    containerColor = MaterialTheme.colorScheme.surface
                ) {
                    NavigationBarItem(
                        selected = currentTab == "chat",
                        onClick = { currentTab = "chat" },
                        icon = { Icon(Icons.Default.Chat, contentDescription = "对话") },
                        label = { Text("对话") },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                        )
                    )
                    NavigationBarItem(
                        selected = currentTab == "personas",
                        onClick = { currentTab = "personas" },
                        icon = { Icon(Icons.Default.RecentActors, contentDescription = "角色工坊") },
                        label = { Text("角色工坊") },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                        )
                    )
                    NavigationBarItem(
                        selected = currentTab == "settings",
                        onClick = { currentTab = "settings" },
                        icon = { Icon(Icons.Default.Settings, contentDescription = "设置") },
                        label = { Text("设置") },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                        )
                    )
                }
            }
        ) { innerPadding ->
            AnimatedContent(
                targetState = currentTab,
                transitionSpec = {
                    val tabOrder = mapOf("chat" to 0, "personas" to 1, "settings" to 2)
                    val initialIndex = tabOrder[initialState] ?: 0
                    val targetIndex = tabOrder[targetState] ?: 0
                    val direction = if (targetIndex >= initialIndex) 1 else -1

                    slideInHorizontally(
                        initialOffsetX = { fullWidth -> fullWidth * direction },
                        animationSpec = spring()
                    ) togetherWith
                            slideOutHorizontally(
                                targetOffsetX = { fullWidth -> -fullWidth * direction },
                                animationSpec = spring()
                            )
                },
                label = "TabTransition",
                modifier = Modifier.fillMaxSize()
            ) { targetTab ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(
                            top = if (targetTab == "chat") 0.dp else innerPadding.calculateTopPadding(),
                            bottom = if (targetTab == "chat") 0.dp else innerPadding.calculateBottomPadding()
                        )
                ) {
                    when (targetTab) {
                        "chat" -> ChatScreen(
                            viewModel = viewModel,
                            bottomPadding = innerPadding.calculateBottomPadding()
                        )
                        "personas" -> PersonaManagementScreen(viewModel = viewModel, onNavigateToTab = { currentTab = it })
                        "settings" -> SettingsScreen(viewModel = viewModel)
                    }
                }
            }
        }
    }
}

// ----------------== CHAT SCREEN ==----------------
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun ChatScreen(
    viewModel: MainViewModel,
    bottomPadding: androidx.compose.ui.unit.Dp = 0.dp
) {
    val sessions by viewModel.allSessionsFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val currentSessionId by viewModel.currentSessionId.collectAsStateWithLifecycle()
    val activeMessages by viewModel.activeMessages.collectAsStateWithLifecycle()
    val npcs by viewModel.allNpcsFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val agents by viewModel.allAgentsFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val isStreaming by viewModel.isStreamingActive.collectAsStateWithLifecycle()
    val streamingSessionId by viewModel.streamingSessionId.collectAsStateWithLifecycle()
    val currentStreamContent by viewModel.currentStreamContent.collectAsStateWithLifecycle()
    val currentStreamThinking by viewModel.currentStreamThinking.collectAsStateWithLifecycle()

    var searchQuery by remember { mutableStateOf("") }
    var isSearching by remember { mutableStateOf(false) }
    var showCreateChatDialog by remember { mutableStateOf(false) }
    var topModelExpanded by remember { mutableStateOf(false) }
    var sessionToDeleteId by remember { mutableStateOf<Long?>(null) }

    val filteredSessions = sessions.filter {
        it.title.contains(searchQuery, ignoreCase = true) || it.mode.contains(searchQuery, ignoreCase = true)
    }

    BackHandler(enabled = currentSessionId == null && isSearching) {
        isSearching = false
        searchQuery = ""
    }

    BackHandler(enabled = currentSessionId != null) {
        viewModel.selectSession(null)
    }

    val context = LocalContext.current

    AnimatedContent(
        targetState = currentSessionId,
        transitionSpec = {
            if (targetState != null) {
                slideInHorizontally(initialOffsetX = { it }) + fadeIn() togetherWith
                        slideOutHorizontally(targetOffsetX = { -it }) + fadeOut()
            } else {
                slideInHorizontally(initialOffsetX = { -it }) + fadeIn() togetherWith
                        slideOutHorizontally(targetOffsetX = { it }) + fadeOut()
            }
        },
        label = "ChatTransition"
    ) { targetSessionId ->
        if (targetSessionId == null) {
            // Combined Session Management list view
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = {
                            if (isSearching) {
                                OutlinedTextField(
                                    value = searchQuery,
                                    onValueChange = { searchQuery = it },
                                    placeholder = { Text("搜索会话...", style = MaterialTheme.typography.bodyMedium) },
                                    singleLine = true,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedContainerColor = Color.Transparent,
                                        unfocusedContainerColor = Color.Transparent,
                                        focusedBorderColor = Color.Transparent,
                                        unfocusedBorderColor = Color.Transparent
                                    ),
                                    textStyle = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            } else {
                                Text(
                                    "对话",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        },
                        navigationIcon = if (isSearching) {
                            {
                                IconButton(onClick = {
                                    isSearching = false
                                    searchQuery = ""
                                }) {
                                    Icon(Icons.Default.ArrowBack, contentDescription = "取消搜索")
                                }
                            }
                        } else {
                            {}
                        },
                        actions = {
                            if (!isSearching) {
                                IconButton(onClick = { isSearching = true }) {
                                    Icon(Icons.Default.Search, contentDescription = "搜索会话")
                                }
                            } else {
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { searchQuery = "" }) {
                                        Icon(Icons.Default.Clear, contentDescription = "清除搜索")
                                    }
                                }
                            }
                            IconButton(onClick = { showCreateChatDialog = true }) {
                                Icon(Icons.Default.Add, contentDescription = "开启新对话")
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        )
                    )
                }
            ) { innerPadding ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .background(MaterialTheme.colorScheme.background)
                ) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))

                    if (filteredSessions.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    Icons.Default.ChatBubbleOutline,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                    modifier = Modifier.size(64.dp)
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    if (searchQuery.isNotEmpty()) "没有找到匹配的对话" else "暂无对话，点击右上角加号创建",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            contentPadding = PaddingValues(top = 12.dp, bottom = bottomPadding + 20.dp)
                        ) {
                            items(filteredSessions, key = { it.id }, contentType = { "session" }) { item ->
                                val avatar = remember(item, npcs, agents) {
                                    when (item.mode) {
                                        "NPC" -> npcs.find { it.id == item.associatedId }?.let {
                                            AvatarUiModel(it.name, it.avatarColorOrdinal, it.avatarUri)
                                        }
                                        "AGENT" -> agents.find { it.id == item.associatedId }?.let {
                                            AvatarUiModel(it.name, it.avatarColorOrdinal, it.avatarUri)
                                        }
                                        else -> null
                                    } ?: AvatarUiModel(item.title, 1, null)
                                }
                                val badgeColor = when (item.mode) {
                                    "NPC" -> Color(0xFF6200EE)
                                    "AGENT" -> Color(0xFF00796B)
                                    else -> Color(0xFF1E88E5)
                                }

                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .combinedClickable(
                                            onClick = { viewModel.selectSession(item.id) },
                                            onLongClick = { sessionToDeleteId = item.id }
                                        ),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                                    ),
                                    shape = RoundedCornerShape(14.dp),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        EntityAvatar(
                                            name = avatar.name,
                                            avatarColorOrdinal = avatar.avatarColorOrdinal,
                                            avatarUri = avatar.avatarUri,
                                            modifier = Modifier.size(44.dp),
                                            textStyle = MaterialTheme.typography.titleSmall
                                        )

                                        Spacer(modifier = Modifier.width(12.dp))

                                        Column(modifier = Modifier.weight(1f)) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    item.title,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.onSurface,
                                                    maxLines = 1
                                                )
                                                Surface(
                                                    shape = RoundedCornerShape(4.dp),
                                                    color = badgeColor.copy(alpha = 0.15f)
                                                ) {
                                                    Text(
                                                        " ${item.mode} ",
                                                        style = MaterialTheme.typography.labelSmall.copy(
                                                            fontWeight = FontWeight.Bold,
                                                            fontSize = 9.sp
                                                        ),
                                                        color = badgeColor,
                                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                                    )
                                                }
                                            }
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                item.lastMessage.ifEmpty { "暂无消息" },
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } else {
            // Interactive Chat Screen View
            val activeSession = sessions.find { it.id == targetSessionId }
            val displayTitle = if (activeSession != null) {
                when (activeSession.mode) {
                    "NPC" -> {
                        val npc = npcs.find { it.id == activeSession.associatedId }
                        npc?.name ?: activeSession.title
                    }
                    "AGENT" -> {
                        val agent = agents.find { it.id == activeSession.associatedId }
                        agent?.name ?: activeSession.title
                    }
                    else -> activeSession.title
                }
            } else {
                "对话"
            }
            val sessionAvatar = remember(activeSession, npcs, agents, displayTitle) {
                when (activeSession?.mode) {
                    "NPC" -> npcs.find { it.id == activeSession.associatedId }?.let {
                        AvatarUiModel(it.name, it.avatarColorOrdinal, it.avatarUri)
                    }
                    "AGENT" -> agents.find { it.id == activeSession.associatedId }?.let {
                        AvatarUiModel(it.name, it.avatarColorOrdinal, it.avatarUri)
                    }
                    else -> null
                } ?: AvatarUiModel(displayTitle, 1, null)
            }
            val settingsItem by viewModel.settingsFlow.collectAsStateWithLifecycle(initialValue = AppSettings())
            val isApiConnected by viewModel.isApiConnected.collectAsStateWithLifecycle()
            val modelsList by viewModel.modelsList.collectAsStateWithLifecycle()

            Column(modifier = Modifier.fillMaxSize()) {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                displayTitle,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Box {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .clickable { topModelExpanded = true }
                                        .padding(horizontal = 4.dp, vertical = 2.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(7.dp)
                                            .clip(CircleShape)
                                            .background(if (isApiConnected) Color(0xFF10B981) else Color(0xFFEF4444))
                                    )
                                    Spacer(modifier = Modifier.width(2.dp))
                                    Text(
                                        text = settingsItem?.defaultModel ?: "gpt-4o-mini",
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.widthIn(max = 96.dp)
                                    )
                                    Icon(
                                        Icons.Default.ArrowDropDown,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                                DropdownMenu(
                                    expanded = topModelExpanded,
                                    onDismissRequest = { topModelExpanded = false }
                                ) {
                                    if (modelsList.isEmpty()) {
                                        DropdownMenuItem(
                                            text = { Text("没有载入模型，请到设置测试连接") },
                                            onClick = { topModelExpanded = false }
                                        )
                                    } else {
                                        modelsList.forEach { modelName ->
                                            DropdownMenuItem(
                                                text = { Text(modelName) },
                                                onClick = {
                                                    viewModel.updateSelectedModel(modelName)
                                                    topModelExpanded = false
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    },
                    navigationIcon = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(start = 4.dp, end = 4.dp)
                        ) {
                            IconButton(onClick = { viewModel.selectSession(null) }) {
                                Icon(Icons.Default.ArrowBack, contentDescription = "返回列表")
                            }
                            Spacer(modifier = Modifier.width(4.dp))
                            EntityAvatar(
                                name = sessionAvatar.name,
                                avatarColorOrdinal = sessionAvatar.avatarColorOrdinal,
                                avatarUri = sessionAvatar.avatarUri,
                                modifier = Modifier.size(36.dp),
                                textStyle = MaterialTheme.typography.bodyMedium
                            )
                        }
                    },
                    actions = {
                        if (activeSession != null) {
                            IconButton(onClick = {
                                viewModel.exportSessionRequestJsonFile(activeSession.id) { jsonFile ->
                                    val jsonUri = FileProvider.getUriForFile(
                                        context,
                                        "${context.packageName}.fileprovider",
                                        jsonFile
                                    )
                                    val sendIntent = android.content.Intent().apply {
                                        action = android.content.Intent.ACTION_SEND
                                        putExtra(android.content.Intent.EXTRA_STREAM, jsonUri)
                                        addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        type = "application/json"
                                    }
                                    val shareIntent = android.content.Intent.createChooser(sendIntent, "分享对话 JSON")
                                    context.startActivity(shareIntent)
                                }
                            }) {
                                Icon(Icons.Default.Share, contentDescription = "分享对话")
                            }
                            IconButton(onClick = {
                                sessionToDeleteId = activeSession.id
                            }) {
                                Icon(Icons.Default.DeleteOutline, contentDescription = "清除会话")
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    windowInsets = TopAppBarDefaults.windowInsets
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    val density = LocalDensity.current
                    val keyboardBottomPadding = with(density) { WindowInsets.ime.getBottom(density).toDp() }
                    val listState = rememberLazyListState()
                    val coroutineScope = rememberCoroutineScope()
                    var lastAutoScrolledSessionId by remember { mutableStateOf<Long?>(null) }
                    var collapseThinkingSignal by remember { mutableStateOf(0) }
                    var lastHandledUserMessageId by remember(currentSessionId) { mutableStateOf<Long?>(null) }
                    val lastMessage = activeMessages.lastOrNull()
                    val shouldStickToBottom by remember {
                        derivedStateOf {
                            val totalItems = listState.layoutInfo.totalItemsCount
                            val lastVisibleIndex = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
                            totalItems == 0 || lastVisibleIndex >= totalItems - 2
                        }
                    }

                    LaunchedEffect(targetSessionId, activeMessages.size) {
                        if (targetSessionId != null && activeMessages.isNotEmpty() && lastAutoScrolledSessionId != targetSessionId) {
                            listState.scrollToItem(activeMessages.lastIndex)
                            lastAutoScrolledSessionId = targetSessionId
                        }
                    }

                    LaunchedEffect(lastMessage?.id, lastMessage?.role) {
                        if (lastMessage != null && lastMessage.role == "user" && lastMessage.id != lastHandledUserMessageId) {
                            lastHandledUserMessageId = lastMessage.id
                            collapseThinkingSignal++
                            listState.scrollToItem(activeMessages.lastIndex)
                        }
                    }

                    LaunchedEffect(activeMessages.size, isStreaming, shouldStickToBottom) {
                        if (activeMessages.isNotEmpty() && shouldStickToBottom && !isStreaming) {
                            listState.scrollToItem(activeMessages.lastIndex)
                        }
                    }

                    LaunchedEffect(isStreaming, shouldStickToBottom, activeMessages.size) {
                        if (!isStreaming || !shouldStickToBottom) return@LaunchedEffect
                        snapshotFlow { currentStreamContent.length to currentStreamThinking.length }
                            .sample(33L)
                            .collect {
                                val targetIndex = activeMessages.lastIndex + 1
                                if (targetIndex >= 0) {
                                    listState.scrollToItem(targetIndex)
                                }
                            }
                    }

                    LaunchedEffect(keyboardBottomPadding, shouldStickToBottom, activeMessages.size, isStreaming) {
                        if (keyboardBottomPadding > 0.dp && shouldStickToBottom) {
                            val targetIndex = activeMessages.lastIndex + if (isStreaming) 1 else 0
                            if (targetIndex >= 0) {
                                listState.scrollToItem(targetIndex)
                            }
                        }
                    }

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
                            contentPadding = PaddingValues(top = 12.dp, bottom = 16.dp)
                        ) {
                            items(activeMessages, key = { it.id }, contentType = { "message" }) { message ->
                                MessageBubbleItem(
                                    message = message,
                                    isStreamingActive = isStreaming && streamingSessionId == currentSessionId,
                                    collapseThinkingSignal = collapseThinkingSignal,
                                    assistantName = displayTitle,
                                    onSimulateToolOutput = { toolName, output ->
                                        viewModel.simulateToolResponse(toolName, output)
                                    },
                                    onDeleteMessage = { viewModel.deleteSingleMessage(message.id) },
                                    onEditMessage = { newContent ->
                                        viewModel.editUserMessage(message.id, newContent)
                                    }
                                )
                            }

                            if (isStreaming && streamingSessionId == currentSessionId) {
                                item(key = "live-streaming-message") {
                                    LiveStreamingMessageItem(
                                        viewModel = viewModel,
                                        sessionId = currentSessionId,
                                        isStreamingActive = true,
                                        collapseThinkingSignal = collapseThinkingSignal,
                                        assistantName = displayTitle,
                                        onSimulateToolOutput = { toolName, output ->
                                            viewModel.simulateToolResponse(toolName, output)
                                        },
                                        onDeleteMessage = {},
                                        onEditMessage = {}
                                    )
                                }
                            }
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))

                    ChatInputPanel(
                        viewModel = viewModel,
                        isStreaming = isStreaming,
                        bottomPadding = bottomPadding,
                        onInputFocused = {
                            coroutineScope.launch {
                                if (activeMessages.isNotEmpty()) {
                                    listState.scrollToItem(activeMessages.lastIndex)
                                }
                            }
                        }
                    )
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
                        "创建对话",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )

                    val currentDateStr = remember {
                        java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date())
                    }
                    var modeChoice by remember { mutableStateOf(if (npcs.isNotEmpty()) "NPC" else "AGENT") } // NPC, AGENT
                    var selectedItemId by remember(modeChoice) {
                        mutableStateOf(
                            if (modeChoice == "NPC" && npcs.isNotEmpty()) npcs.first().id
                            else if (modeChoice == "AGENT" && agents.isNotEmpty()) agents.first().id
                            else -1L
                        )
                    }
                    var titleText by remember {
                        val initialName = if (modeChoice == "NPC" && npcs.isNotEmpty()) {
                            npcs.first().name
                        } else if (modeChoice == "AGENT" && agents.isNotEmpty()) {
                            agents.first().name
                        } else {
                            "未命名"
                        }
                        mutableStateOf("${initialName}-${currentDateStr}")
                    }

                    LaunchedEffect(selectedItemId, modeChoice) {
                        if (modeChoice == "NPC" && npcs.isNotEmpty()) {
                            val activeNpc = npcs.find { it.id == selectedItemId } ?: npcs.first()
                            titleText = "${activeNpc.name}-${currentDateStr}"
                        } else if (modeChoice == "AGENT" && agents.isNotEmpty()) {
                            val activeAgent = agents.find { it.id == selectedItemId } ?: agents.first()
                            titleText = "${activeAgent.name}-${currentDateStr}"
                        }
                    }

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
                        listOf("NPC" to "NPC", "AGENT" to "Agent").forEach { (v, l) ->
                            FilterChip(
                                selected = modeChoice == v,
                                onClick = {
                                    modeChoice = v
                                    if (v == "NPC" && npcs.isNotEmpty()) selectedItemId = npcs.first().id
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
                                "⚠️ 请先在“角色工坊”标签页创立或载入NPC人设数据！",
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
                                "⚠️ 请先在“角色工坊”配置或生成Agent！",
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

    if (sessionToDeleteId != null) {
        AlertDialog(
            onDismissRequest = { sessionToDeleteId = null },
            title = { Text("确认删除会话") },
            text = { Text("您确定要彻底删除该对话及其所有历史记录吗？此操作无法撤销。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        sessionToDeleteId?.let { id ->
                            viewModel.deleteSession(id)
                        }
                        sessionToDeleteId = null
                    }
                ) {
                    Text("确认删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { sessionToDeleteId = null }) {
                    Text("取消")
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatInputPanel(
    viewModel: MainViewModel,
    isStreaming: Boolean,
    bottomPadding: androidx.compose.ui.unit.Dp = 0.dp,
    onInputFocused: () -> Unit = {}
) {
    val settings by viewModel.settingsFlow.collectAsStateWithLifecycle(initialValue = AppSettings())
    val activeSettings = settings ?: AppSettings()
    var inputStr by remember { mutableStateOf("") }
    val density = LocalDensity.current
    val keyboardBottomPadding = with(density) { WindowInsets.ime.getBottom(density).toDp() }
    val effectiveBottomPadding = if (keyboardBottomPadding > bottomPadding) keyboardBottomPadding else bottomPadding

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = effectiveBottomPadding)
            .padding(12.dp)
    ) {
        // Option quick toggle strip
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start
        ) {
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
                    .onFocusChanged { focusState ->
                        if (focusState.isFocused) {
                            onInputFocused()
                        }
                    },
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
                    if (isStreaming) {
                        viewModel.interruptGeneration()
                    } else if (inputStr.isNotBlank()) {
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
                    Icon(Icons.Default.Stop, contentDescription = "停止生成")
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
    collapseThinkingSignal: Int = 0,
    isLiveStream: Boolean = false,
    assistantName: String = "AI 助理",
    onSimulateToolOutput: (String, String) -> Unit,
    onDeleteMessage: () -> Unit,
    onEditMessage: (String) -> Unit
) {
    val isUser = message.role == "user"
    val isSystem = message.role == "system"
    val clipboardManager = LocalClipboardManager.current

    // Collapse when streaming is done
    var isThinkingExpanded by remember(message.id) { mutableStateOf(false) }
    var isToolExpanded by remember(message.id) { mutableStateOf(isStreamingActive) }
    var contextMenuExpanded by remember { mutableStateOf(false) }
    var showRawLogDialog by remember(message.id) { mutableStateOf(false) }

    LaunchedEffect(isStreamingActive) {
        if (isStreamingActive && isThinkingExpanded) {
            isThinkingExpanded = false
        }
    }

    var isEditing by remember(message.id) { mutableStateOf(false) }
    var editedText by remember(message.id) { mutableStateOf(TextFieldValue(message.content, TextRange(message.content.length))) }
    val focusRequester = remember(message.id) { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    LaunchedEffect(isStreamingActive) {
        if (!isStreamingActive) {
            isThinkingExpanded = false
            isToolExpanded = false
        }
    }

    LaunchedEffect(collapseThinkingSignal) {
        isThinkingExpanded = false
    }

    LaunchedEffect(isEditing) {
        if (isEditing) {
            focusRequester.requestFocus()
            keyboardController?.show()
        }
    }
    val thinkingArrowRotation by animateFloatAsState(
        targetValue = if (isThinkingExpanded) 180f else 0f,
        label = "ThinkingArrowRotation"
    )

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
    val isLightPalette = MaterialTheme.colorScheme.surface.luminance() > 0.5f
    val assistantThemedNearBlack = if (isLightPalette) {
        Color.Black
    } else {
        MaterialTheme.colorScheme.onPrimaryContainer
    }
    val messageBodyTextColor = when {
        isUser -> MaterialTheme.colorScheme.onPrimary
        isSystem -> MaterialTheme.colorScheme.onSurfaceVariant
        else -> assistantThemedNearBlack
    }
    val thinkingTextColor = when {
        !isUser && !isSystem -> assistantThemedNearBlack
        else -> messageBodyTextColor
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
        Box {
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
                                else -> assistantName
                            },
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = if (isUser) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }

                        if (isEditing) {
                        val editCursorColor = if (isUser) {
                            MaterialTheme.colorScheme.onPrimary
                        } else {
                            MaterialTheme.colorScheme.primary
                        }

                        OutlinedTextField(
                            value = editedText,
                            onValueChange = { editedText = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(focusRequester)
                                .padding(vertical = 4.dp),
                            textStyle = MaterialTheme.typography.bodyMedium.copy(
                                color = if (isUser) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                            ),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedTextColor = if (isUser) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                unfocusedTextColor = if (isUser) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                cursorColor = editCursorColor,
                                selectionColors = TextSelectionColors(
                                    handleColor = editCursorColor,
                                    backgroundColor = if (isUser) {
                                        MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.28f)
                                    } else {
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.24f)
                                    }
                                ),
                                focusedBorderColor = if (isUser) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = if (isUser) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outline
                            )
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(
                                onClick = {
                                    isEditing = false
                                    editedText = TextFieldValue(message.content, TextRange(message.content.length))
                                },
                                colors = ButtonDefaults.textButtonColors(
                                    contentColor = if (isUser) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f) else MaterialTheme.colorScheme.primary
                                )
                            ) {
                                Text("取消")
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            ElevatedButton(
                                onClick = {
                                    isEditing = false
                                    val newText = editedText.text
                                    if (newText.isNotBlank() && newText != message.content) {
                                        onEditMessage(newText)
                                    }
                                },
                                colors = ButtonDefaults.elevatedButtonColors(
                                    containerColor = if (isUser) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.primary,
                                    contentColor = if (isUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onPrimary
                                )
                            ) {
                                Text("保存并发送")
                            }
                        }
                    } else {

                    val normalizedThinkingContent = remember(message.thinkingContent) {
                        message.thinkingContent
                            ?.trim('\n', '\r')
                            ?.takeIf { it.isNotBlank() }
                    }
                    val thinkingLines = remember(normalizedThinkingContent) {
                        normalizedThinkingContent
                            ?.lineSequence()
                            ?.map { it.trimEnd() }
                            ?.filter { it.isNotBlank() }
                            ?.toList()
                            ?: emptyList()
                    }
                    val hasCompactThinkingPreview = thinkingLines.size > 3
                    val canExpandThinking = hasCompactThinkingPreview && !isStreamingActive

                    // Render thinking content block if any
                    if (!normalizedThinkingContent.isNullOrBlank()) {
                        val thinkingPreview = remember(thinkingLines) {
                            thinkingLines
                                .takeLast(3)
                                .joinToString("\n")
                        }

                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .clickable(enabled = canExpandThinking) {
                                    isThinkingExpanded = !isThinkingExpanded
                                },
                            shape = RoundedCornerShape(10.dp),
                            color = if (isLightPalette) Color.White.copy(alpha = 0.82f) else Color.Black.copy(alpha = 0.20f),
                            tonalElevation = 0.dp,
                            shadowElevation = 0.dp
                        ) {
                            Column(modifier = Modifier.padding(8.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(
                                        Icons.Outlined.Psychology,
                                        contentDescription = null,
                                        tint = thinkingTextColor.copy(alpha = 0.72f),
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        "思考过程",
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 10.sp
                                        ),
                                        fontWeight = FontWeight.SemiBold,
                                        color = thinkingTextColor.copy(alpha = 0.92f),
                                        modifier = Modifier.weight(1f)
                                    )
                                    if (hasCompactThinkingPreview) {
                                        Icon(
                                            imageVector = Icons.Default.ExpandMore,
                                            contentDescription = "展缩",
                                            modifier = Modifier
                                                .size(16.dp)
                                                .graphicsLayer { rotationZ = thinkingArrowRotation },
                                            tint = thinkingTextColor.copy(alpha = if (canExpandThinking) 0.86f else 0.42f)
                                        )
                                    }
                                }
                                if (!hasCompactThinkingPreview) {
                                    Text(
                                        text = normalizedThinkingContent,
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 10.sp,
                                            lineHeight = 14.sp
                                        ),
                                        color = thinkingTextColor.copy(alpha = 0.9f),
                                        modifier = Modifier.padding(top = 6.dp)
                                    )
                                } else if (!isThinkingExpanded && thinkingPreview.isNotBlank()) {
                                    Text(
                                        text = thinkingPreview,
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 10.sp,
                                            lineHeight = 14.sp
                                        ),
                                        maxLines = 3,
                                        overflow = TextOverflow.Ellipsis,
                                        color = thinkingTextColor.copy(alpha = 0.84f),
                                        modifier = Modifier.padding(top = 4.dp)
                                    )
                                }
                                if (hasCompactThinkingPreview) {
                                    AnimatedVisibility(
                                        visible = isThinkingExpanded,
                                        enter = expandVertically() + fadeIn(),
                                        exit = shrinkVertically() + fadeOut()
                                    ) {
                                        Text(
                                            normalizedThinkingContent,
                                            style = MaterialTheme.typography.bodySmall.copy(
                                                fontFamily = FontFamily.Monospace,
                                                fontSize = 10.sp,
                                                lineHeight = 14.sp
                                            ),
                                            color = thinkingTextColor.copy(alpha = 0.9f),
                                            modifier = Modifier.padding(top = 6.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    val normalizedBodyContent = remember(message.content, isUser, isSystem) {
                        if (!isUser && !isSystem) {
                            message.content.trim('\n', '\r')
                        } else {
                            message.content
                        }
                    }

                    val bodyTopSpacing = if (!isUser && !isSystem && !normalizedThinkingContent.isNullOrBlank()) 10.dp else 0.dp

                    // Use simplified rendering path for live stream chunks to reduce per-token recomposition cost.
                    Column(modifier = Modifier.padding(top = bodyTopSpacing)) {
                        if (isLiveStream) {
                            val renderedLiveContent = remember(normalizedBodyContent) { renderMarkdown(normalizedBodyContent) }
                            Text(
                                text = renderedLiveContent,
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                color = messageBodyTextColor
                            )
                        } else {
                            // Render standard or intercepted content
                            val toolRegex = remember { Regex("""<tool_call name="([^"]+)">([^<]*)</tool_call>""") }
                            val matches = remember(normalizedBodyContent, isUser) {
                                if (isUser) emptyList() else toolRegex.findAll(normalizedBodyContent).toList()
                            }

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
                            val contentWithoutToolCall = remember(normalizedBodyContent) {
                                normalizedBodyContent.replace(toolRegex, "").trim()
                            }
                            if (contentWithoutToolCall.isNotBlank()) {
                                val renderedRemainder = remember(contentWithoutToolCall) { renderMarkdown(contentWithoutToolCall) }
                                Text(
                                    text = renderedRemainder,
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                    color = messageBodyTextColor
                                )
                            }
                            } else {
                                // Regular Message Text
                                val renderedContent = remember(normalizedBodyContent) { renderMarkdown(normalizedBodyContent) }
                                Text(
                                    text = renderedContent,
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                    color = messageBodyTextColor
                                )
                            }
                        }
                    }

                    // Display metrics metadata badge in Assistant footer
                    if (!isUser && !isSystem && message.promptTokens > 0) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "⏱️ ${message.latencyMs}ms | ⚡ ${String.format("%.1f", message.tokensPerSec)} t/s | 📥 ${message.promptTokens} | 📤 ${message.completionTokens}",
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                            )
                        }
                    }
                    }
                }
            }
            }
            // Long press menu anchored to message bubble
            DropdownMenu(
                expanded = contextMenuExpanded,
                onDismissRequest = { contextMenuExpanded = false }
            ) {
                if (isUser) {
                    DropdownMenuItem(
                        text = { Text("编辑该消息") },
                        onClick = {
                            editedText = TextFieldValue(message.content, TextRange(message.content.length))
                            isEditing = true
                            contextMenuExpanded = false
                        }
                    )
                }
                DropdownMenuItem(
                    text = { Text("复制文本内容") },
                    onClick = {
                        clipboardManager.setText(AnnotatedString(message.content))
                        contextMenuExpanded = false
                    }
                )
                if (!isUser && !isSystem) {
                    DropdownMenuItem(
                        text = { Text("查看原始日志") },
                        onClick = {
                            showRawLogDialog = true
                            contextMenuExpanded = false
                        }
                    )
                }
            }
        }
    }

    if (showRawLogDialog) {
        RawLogDialog(
            requestBody = message.rawRequestBody,
            responseBody = message.rawResponseBody,
            onDismiss = { showRawLogDialog = false }
        )
    }
}

@Composable
private fun RawLogDialog(
    requestBody: String?,
    responseBody: String?,
    onDismiss: () -> Unit
) {
    val clipboardManager = LocalClipboardManager.current
    val requestText = requestBody?.takeIf { it.isNotBlank() } ?: "暂无原始请求体。"
    val responseText = responseBody?.takeIf { it.isNotBlank() } ?: "暂无原始响应内容。"

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(18.dp),
            tonalElevation = 8.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .heightIn(max = 560.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = "原始日志",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(12.dp))
                RawLogSection(
                    title = "请求体",
                    content = requestText,
                    onCopy = { clipboardManager.setText(AnnotatedString(requestText)) }
                )
                Spacer(modifier = Modifier.height(12.dp))
                RawLogSection(
                    title = "原始响应",
                    content = responseText,
                    onCopy = { clipboardManager.setText(AnnotatedString(responseText)) }
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("关闭")
                    }
                }
            }
        }
    }
}

@Composable
private fun RawLogSection(
    title: String,
    content: String,
    onCopy: () -> Unit
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold
            )
            TextButton(onClick = onCopy) {
                Text("一键复制")
            }
        }
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
            modifier = Modifier.fillMaxWidth()
        ) {
            SelectionContainer {
                Text(
                    text = content,
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp)
                )
            }
        }
    }
}

@Composable
private fun LiveStreamingMessageItem(
    viewModel: MainViewModel,
    sessionId: Long?,
    isStreamingActive: Boolean,
    collapseThinkingSignal: Int,
    assistantName: String,
    onSimulateToolOutput: (String, String) -> Unit,
    onDeleteMessage: () -> Unit,
    onEditMessage: (String) -> Unit
) {
    val currentStreamContent by viewModel.currentStreamContent.collectAsStateWithLifecycle()
    val currentStreamThinking by viewModel.currentStreamThinking.collectAsStateWithLifecycle()
    var displayContent by remember { mutableStateOf("") }
    var displayThinking by remember { mutableStateOf("") }

    // Keep true streaming feel while limiting redraw frequency to reduce visual jitter.
    LaunchedEffect(Unit) {
        displayContent = currentStreamContent
        displayThinking = currentStreamThinking
        snapshotFlow { currentStreamContent to currentStreamThinking }
            .sample(33L)
            .collect { (content, thinking) ->
                displayContent = content
                displayThinking = thinking
            }
    }

    if (displayContent.isBlank() && displayThinking.isBlank()) return

    MessageBubbleItem(
        message = ChatMessage(
            id = -1L,
            sessionId = sessionId ?: -1L,
            role = "assistant",
            content = displayContent,
            thinkingContent = displayThinking.takeIf { it.isNotBlank() }
        ),
        isStreamingActive = isStreamingActive,
        collapseThinkingSignal = collapseThinkingSignal,
        isLiveStream = true,
        assistantName = assistantName,
        onSimulateToolOutput = onSimulateToolOutput,
        onDeleteMessage = onDeleteMessage,
        onEditMessage = onEditMessage
    )
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
fun PersonaManagementScreen(viewModel: MainViewModel, onNavigateToTab: (String) -> Unit) {
    val npcs by viewModel.allNpcsFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val agents by viewModel.allAgentsFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val mcpTools by viewModel.allMcpToolsFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val context = LocalContext.current

    var activeSubTab by remember { mutableStateOf("npcs") } // "npcs", "agents" or "tools_mcp"
    var showCreateNpcDialog by remember { mutableStateOf(false) }
    var showCreateAgentDialog by remember { mutableStateOf(false) }
    var showCreateToolDialog by remember { mutableStateOf(false) }
    var editingNpc by remember { mutableStateOf<NpcCharacter?>(null) }
    var editingAgent by remember { mutableStateOf<AgentConfig?>(null) }
    var editingTool by remember { mutableStateOf<McpTool?>(null) }
    var npcToDelete by remember { mutableStateOf<NpcCharacter?>(null) }
    var agentToDelete by remember { mutableStateOf<AgentConfig?>(null) }
    var toolToDelete by remember { mutableStateOf<McpTool?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            "角色工坊",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        // Sub tab strip selector
        val subTabIndex = when (activeSubTab) {
            "npcs" -> 0
            "agents" -> 1
            else -> 2
        }
        TabRow(
            selectedTabIndex = subTabIndex,
            modifier = Modifier.padding(bottom = 16.dp)
        ) {
            Tab(
                selected = activeSubTab == "npcs",
                onClick = { activeSubTab = "npcs" },
                text = { Text("NPCs (${npcs.size})") }
            )
            Tab(
                selected = activeSubTab == "agents",
                onClick = { activeSubTab = "agents" },
                text = { Text("Agents (${agents.size})") }
            )
            Tab(
                selected = activeSubTab == "tools_mcp",
                onClick = { activeSubTab = "tools_mcp" },
                text = { Text("Tools&MCP (${mcpTools.size})") }
            )
        }

        val currentDateStr = remember {
            java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date())
        }

        when (activeSubTab) {
            "npcs" -> {
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
                            key = { it.id },
                            modifier = Modifier.fillMaxSize()
                        ) { npcItem ->
                            NpcConfigCard(
                                npc = npcItem,
                                onEdit = { editingNpc = npcItem },
                                onDelete = { npcToDelete = npcItem },
                                onStartChat = {
                                    viewModel.createNewSession("${npcItem.name}-${currentDateStr}", "NPC", npcItem.id)
                                    onNavigateToTab("chat")
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
            }
            "agents" -> {
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
                            key = { it.id },
                            modifier = Modifier.fillMaxSize()
                        ) { agentItem ->
                            AgentConfigCard(
                                agent = agentItem,
                                onEdit = { editingAgent = agentItem },
                                onDelete = { agentToDelete = agentItem },
                                onStartChat = {
                                    viewModel.createNewSession("${agentItem.name}-${currentDateStr}", "AGENT", agentItem.id)
                                    onNavigateToTab("chat")
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
                        Icon(Icons.Default.Add, contentDescription = "新增")
                    }
                }
            }
            else -> {
                // Tools & MCP list UI Grid
                Box(modifier = Modifier.weight(1f)) {
                    if (mcpTools.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.Build, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("此处可以自主编辑并维护仿真工具 (Simulated Tools/MCP JSON)...", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    } else {
                        LazyVerticalGridInside(
                            items = mcpTools,
                            key = { it.id },
                            modifier = Modifier.fillMaxSize()
                        ) { toolItem ->
                            McpToolCard(
                                tool = toolItem,
                                onEdit = { editingTool = toolItem },
                                onDelete = { toolToDelete = toolItem }
                            )
                        }
                    }

                    FloatingActionButton(
                        onClick = { showCreateToolDialog = true },
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(16.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "新增工具")
                    }
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
                val randomColorIndex = remember(originalNpc) { randomAvatarColorOrdinal() }
                var avatarUriText by remember(originalNpc) { mutableStateOf(originalNpc?.avatarUri) }
                val avatarPickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
                    if (uri != null) {
                        runCatching {
                            context.contentResolver.takePersistableUriPermission(
                                uri,
                                Intent.FLAG_GRANT_READ_URI_PERMISSION
                            )
                        }
                        avatarUriText = uri.toString()
                    }
                }

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
                        label = { Text("人设细节（System Prompt）") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(150.dp)
                            .padding(bottom = 12.dp),
                        maxLines = 10
                    )

                    Text(
                        "角色头像:",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        EntityAvatar(
                            name = nameText.ifBlank { "NPC" },
                            avatarColorOrdinal = randomColorIndex,
                            avatarUri = avatarUriText,
                            modifier = Modifier.size(56.dp)
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            TextButton(onClick = { avatarPickerLauncher.launch(arrayOf("image/*")) }) {
                                Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(if (avatarUriText.isNullOrBlank()) "上传头像" else "更换头像")
                            }
                            if (!avatarUriText.isNullOrBlank()) {
                                TextButton(onClick = { avatarUriText = null }) {
                                    Text("移除头像")
                                }
                            }
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
                                                avatarColorOrdinal = randomColorIndex,
                                                avatarUri = avatarUriText
                                            )
                                        )
                                        editingNpc = null
                                    } else {
                                        viewModel.saveNpc(
                                            NpcCharacter(
                                                name = nameText,
                                                prompt = promptText,
                                                greeting = greetingText.ifBlank { "你好！我们准备开始啦" },
                                                avatarColorOrdinal = randomColorIndex,
                                                avatarUri = avatarUriText
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
                var selectedToolNames by remember(originalAgent, mcpTools) {
                    val namesSet = mutableSetOf<String>()
                    val tJson = originalAgent?.toolsJson ?: ""
                    if (tJson.isNotBlank() && tJson != "[]") {
                        val regex = "\"name\"\\s*:\\s*\"([^\"]+)\"".toRegex()
                        regex.findAll(tJson).forEach { match ->
                            namesSet.add(match.groupValues[1])
                        }
                    } else if (originalAgent == null) {
                        mcpTools.forEach { namesSet.add(it.name) }
                    }
                    mutableStateOf(namesSet)
                }

                val randomColorIndex = remember(originalAgent) { randomAvatarColorOrdinal() }
                var avatarUriText by remember(originalAgent) { mutableStateOf(originalAgent?.avatarUri) }
                val avatarPickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
                    if (uri != null) {
                        runCatching {
                            context.contentResolver.takePersistableUriPermission(
                                uri,
                                Intent.FLAG_GRANT_READ_URI_PERMISSION
                            )
                        }
                        avatarUriText = uri.toString()
                    }
                }

                Column(
                    modifier = Modifier
                        .padding(14.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        if (activeAgentEditMode) "编辑Agent" else "装配超级 Agent 实体 (Markdown 矩阵)",
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

                    Text(
                        "Agent 头像:",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        EntityAvatar(
                            name = agentName.ifBlank { "Agent" },
                            avatarColorOrdinal = randomColorIndex,
                            avatarUri = avatarUriText,
                            modifier = Modifier.size(56.dp)
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            TextButton(onClick = { avatarPickerLauncher.launch(arrayOf("image/*")) }) {
                                Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(if (avatarUriText.isNullOrBlank()) "上传头像" else "更换头像")
                            }
                            if (!avatarUriText.isNullOrBlank()) {
                                TextButton(onClick = { avatarUriText = null }) {
                                    Text("移除头像")
                                }
                            }
                        }
                    }

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
                        Tab(selected = activeEditorTab == "tools", onClick = { activeEditorTab = "tools" }, text = { Text("Tools") })
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
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 240.dp)
                                    .verticalScroll(rememberScrollState())
                            ) {
                                Text(
                                    text = "勾选需要集成的仿真工具 (Simulated Tools):",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(bottom = 8.dp)
                                )
                                if (mcpTools.isEmpty()) {
                                    Text(
                                        text = "⚠️ 尚无已配工具，请前往 [角色工坊 -> Tools&MCP] 菜单创建工具！",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.padding(vertical = 8.dp)
                                    )
                                } else {
                                    mcpTools.forEach { mcpTool ->
                                        val isChecked = mcpTool.name in selectedToolNames
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    val updated = selectedToolNames.toMutableSet()
                                                    if (isChecked) updated.remove(mcpTool.name)
                                                    else updated.add(mcpTool.name)
                                                    selectedToolNames = updated
                                                }
                                                .padding(vertical = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Checkbox(
                                                checked = isChecked,
                                                onCheckedChange = { checked ->
                                                    val updated = selectedToolNames.toMutableSet()
                                                    if (checked == false) updated.remove(mcpTool.name)
                                                    else updated.add(mcpTool.name)
                                                    selectedToolNames = updated
                                                }
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Column {
                                                Text(
                                                    text = mcpTool.name,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    fontWeight = FontWeight.SemiBold
                                                )
                                                Text(
                                                    text = "可用模拟工具",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.08f))
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

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
                                    val checkedMcpObjects = mcpTools.filter { it.name in selectedToolNames }
                                    val computedToolsJson = "[" + checkedMcpObjects.map { it.jsonContent }.joinToString(",\n") + "]"

                                    if (activeAgentEditMode && originalAgent != null) {
                                        viewModel.saveAgent(
                                            originalAgent.copy(
                                                name = agentName,
                                                agentMd = fAgent,
                                                identityMd = fIdentity,
                                                memoryMd = fMemory,
                                                soulMd = fSoul,
                                                userMd = fUser,
                                                toolsJson = computedToolsJson,
                                                avatarColorOrdinal = randomColorIndex,
                                                avatarUri = avatarUriText
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
                                                toolsJson = computedToolsJson,
                                                avatarColorOrdinal = randomColorIndex,
                                                avatarUri = avatarUriText
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

    val activeToolEditMode = editingTool != null
    if (showCreateToolDialog || activeToolEditMode) {
        Dialog(onDismissRequest = {
            showCreateToolDialog = false
            editingTool = null
        }) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                tonalElevation = 8.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp)
            ) {
                val originalTool = editingTool
                var toolName by remember(originalTool) { mutableStateOf(originalTool?.name ?: "") }
                var toolJsonContent by remember(originalTool) {
                    mutableStateOf(
                        originalTool?.jsonContent ?: """{
  "name": "get_example",
  "description": "An example custom tool",
  "parametersJson": "{\"type\":\"object\",\"properties\":{\"arg1\":{\"type\":\"string\"}}}"
}"""
                    )
                }

                Column(
                    modifier = Modifier
                        .padding(16.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = if (activeToolEditMode) "编辑模拟工具" else "自定义模拟工具",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    OutlinedTextField(
                        value = toolName,
                        onValueChange = { toolName = it },
                        label = { Text("工具标志符 (e.g. get_weather)") },
                        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = toolJsonContent,
                        onValueChange = { toolJsonContent = it },
                        label = { Text("工具 JSON 定义") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(240.dp),
                        maxLines = 20
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = {
                            showCreateToolDialog = false
                            editingTool = null
                        }) {
                            Text("放弃")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                if (toolName.isNotBlank() && toolJsonContent.isNotBlank()) {
                                    if (activeToolEditMode && originalTool != null) {
                                        viewModel.saveMcpTool(
                                            originalTool.copy(
                                                name = toolName,
                                                jsonContent = toolJsonContent
                                            )
                                        )
                                        editingTool = null
                                    } else {
                                        viewModel.saveMcpTool(
                                            McpTool(
                                                name = toolName,
                                                jsonContent = toolJsonContent
                                            )
                                        )
                                        showCreateToolDialog = false
                                    }
                                }
                            }
                        ) {
                            Text(if (activeToolEditMode) "确认更新" else "写入注册")
                        }
                    }
                }
            }
        }
    }

    if (npcToDelete != null) {
        AlertDialog(
            onDismissRequest = { npcToDelete = null },
            title = { Text("确认删除角色") },
            text = { Text("您确定要永久删除 ${npcToDelete?.name} 角色配置吗？此操作无法撤销。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        npcToDelete?.let { viewModel.removeNpc(it.id) }
                        npcToDelete = null
                    }
                ) {
                    Text("确认删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { npcToDelete = null }) {
                    Text("取消")
                }
            }
        )
    }

    if (agentToDelete != null) {
        AlertDialog(
            onDismissRequest = { agentToDelete = null },
            title = { Text("确认删除工作流") },
            text = { Text("您确定要永久删除 ${agentToDelete?.name} 工作流配置吗？此操作无法撤销。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        agentToDelete?.let { viewModel.removeAgent(it.id) }
                        agentToDelete = null
                    }
                ) {
                    Text("确认删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { agentToDelete = null }) {
                    Text("取消")
                }
            }
        )
    }

    if (toolToDelete != null) {
        AlertDialog(
            onDismissRequest = { toolToDelete = null },
            title = { Text("确认删除工具") },
            text = { Text("您确定要永久删除工具 ${toolToDelete?.name} 吗？此操作无法撤销。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        toolToDelete?.let { viewModel.removeMcpTool(it.id) }
                        toolToDelete = null
                    }
                ) {
                    Text("确认删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { toolToDelete = null }) {
                    Text("取消")
                }
            }
        )
    }
}

@Composable
fun McpToolCard(
    tool: McpTool,
    onEdit: () -> Unit,
    onDelete: () -> Unit
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
                        .background(MaterialTheme.colorScheme.secondary),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Build,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        tool.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Custom simulated MCP Tool",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
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

            // Show JSON preview
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = tool.jsonContent,
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(10.dp)
                        .heightIn(max = 100.dp)
                        .verticalScroll(rememberScrollState()),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// Custom Grid List items container for flexible density view
@Composable
fun <T> LazyVerticalGridInside(
    items: List<T>,
    key: ((T) -> Any)? = null,
    modifier: Modifier = Modifier,
    content: @Composable (T) -> Unit
) {
    LazyColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(
            items = items,
            key = if (key != null) ({ item -> key(item) }) else null,
            contentType = { "grid-item" }
        ) { item ->
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
                EntityAvatar(
                    name = npc.name,
                    avatarColorOrdinal = npc.avatarColorOrdinal,
                    avatarUri = npc.avatarUri,
                    modifier = Modifier.size(40.dp)
                )

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
                Text("召唤Ta开启新聊天")
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
                EntityAvatar(
                    name = agent.name,
                    avatarColorOrdinal = agent.avatarColorOrdinal,
                    avatarUri = agent.avatarUri,
                    modifier = Modifier.size(40.dp)
                )

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        agent.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    
                    val activatedToolsCount = remember(agent.toolsJson) {
                        if (agent.toolsJson.isBlank()) 0
                        else {
                            val regex = "\"name\"\\s*:".toRegex()
                            regex.findAll(agent.toolsJson).count()
                        }
                    }

                    val activeMdCount = remember(agent) {
                        listOf(agent.agentMd, agent.identityMd, agent.memoryMd, agent.soulMd, agent.userMd).count { it.isNotBlank() }
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(4.dp))
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                        ) {
                            Text(
                                "${activeMdCount} md files",
                                style = androidx.compose.ui.text.TextStyle(fontSize = 10.sp, fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                        Box(
                            modifier = Modifier
                                .background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(4.dp))
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                        ) {
                            Text(
                                "${activatedToolsCount} tools",
                                style = androidx.compose.ui.text.TextStyle(fontSize = 10.sp, fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
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

            Spacer(modifier = Modifier.height(10.dp))

            Button(
                onClick = onStartChat,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(Icons.Default.PlayCircleFilled, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("召唤Ta开启新会话")
            }
        }
    }
}

// ----------------== GENERAL SYSTEM SETTINGS ==----------------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: MainViewModel) {
    val settings by viewModel.settingsFlow.collectAsStateWithLifecycle(initialValue = null)
    val activeSettings = settings ?: return
    val apiEndpointHistory by viewModel.apiEndpointHistoryFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val isTesting by viewModel.isTestingConnection.collectAsStateWithLifecycle()
    val modelsList by viewModel.modelsList.collectAsStateWithLifecycle()
    val testResultMessage by viewModel.testResultMessage.collectAsStateWithLifecycle()
    val careerStats by viewModel.careerStatsFlow.collectAsStateWithLifecycle()

    // Internal edits
    var editedUrl by remember { mutableStateOf(activeSettings.baseUrl) }
    var editedKey by remember { mutableStateOf(activeSettings.apiKey) }
    var isApiKeyMasked by remember { mutableStateOf(true) }
    var isEndpointHistoryMenuExpanded by remember { mutableStateOf(false) }
    var draftTemperature by remember { mutableStateOf(activeSettings.temperature) }
    var draftTopP by remember { mutableStateOf(activeSettings.topP) }
    var draftMaxTokens by remember { mutableStateOf(activeSettings.maxTokens.coerceIn(1, 20000)) }

    LaunchedEffect(activeSettings.baseUrl, activeSettings.apiKey) {
        editedUrl = activeSettings.baseUrl
        editedKey = activeSettings.apiKey
    }

    LaunchedEffect(activeSettings.temperature, activeSettings.topP, activeSettings.maxTokens) {
        draftTemperature = activeSettings.temperature
        draftTopP = activeSettings.topP
        draftMaxTokens = activeSettings.maxTokens.coerceIn(1, 20000)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            "设置",
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

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                ) {
                    OutlinedTextField(
                        value = editedUrl,
                        onValueChange = {
                            editedUrl = it
                        },
                        label = { Text("API 端点 (Base URL)") },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("https://api.openai.com/v1/") },
                        trailingIcon = {
                            IconButton(
                                onClick = { isEndpointHistoryMenuExpanded = !isEndpointHistoryMenuExpanded },
                                enabled = apiEndpointHistory.isNotEmpty()
                            ) {
                                Icon(Icons.Default.History, contentDescription = "历史端点")
                            }
                        },
                        singleLine = true
                    )

                    DropdownMenu(
                        expanded = isEndpointHistoryMenuExpanded,
                        onDismissRequest = { isEndpointHistoryMenuExpanded = false }
                    ) {
                        apiEndpointHistory.forEach { historyItem ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = historyItem.url,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                },
                                onClick = {
                                    editedUrl = historyItem.url
                                    isEndpointHistoryMenuExpanded = false
                                },
                                trailingIcon = {
                                    IconButton(onClick = { viewModel.deleteApiEndpointHistory(historyItem.url) }) {
                                        Icon(Icons.Default.Delete, contentDescription = "删除历史端点")
                                    }
                                }
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = editedKey,
                    onValueChange = {
                        editedKey = it
                    },
                    label = { Text("API 凭证 (API Key)") },
                    placeholder = { Text("请输入 API Key") },
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
                    onClick = {
                        viewModel.updateApiConfig(editedUrl, editedKey)
                        viewModel.fetchAvailableModels(editedUrl, editedKey)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isTesting
                ) {
                    if (isTesting) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("连通性测试中...")
                    } else {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("连通性测试")
                    }
                }

                testResultMessage?.let { msg ->
                    Spacer(modifier = Modifier.height(8.dp))
                    val isSuccess = msg.startsWith("SUCCESS:")
                    val displayMsg = msg.substringAfter(":")
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSuccess) Color(0xFFE8F5E9) else Color(0xFFFFEBEE)
                        ),
                        border = BorderStroke(1.dp, if (isSuccess) Color(0xFFC8E6C9) else Color(0xFFFFCDD2)),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (isSuccess) Icons.Default.CheckCircle else Icons.Default.Error,
                                contentDescription = null,
                                tint = if (isSuccess) Color(0xFF2E7D32) else Color(0xFFC62828),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = displayMsg,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (isSuccess) Color(0xFF2E7D32) else Color(0xFFC62828),
                                modifier = Modifier.weight(1f)
                            )
                        }
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
                    "对话核心超参数调节",
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
                    Text("Temperature: ${String.format("%.2f", draftTemperature)}", style = MaterialTheme.typography.bodyMedium)
                }
                Slider(
                    value = draftTemperature,
                    onValueChange = { draftTemperature = it },
                    onValueChangeFinished = {
                        viewModel.updateHyperparams(draftTemperature, draftMaxTokens, draftTopP)
                    },
                    valueRange = 0.0f..1.5f,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Top P
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Top P: ${String.format("%.2f", draftTopP)}", style = MaterialTheme.typography.bodyMedium)
                }
                Slider(
                    value = draftTopP,
                    onValueChange = { draftTopP = it },
                    onValueChangeFinished = {
                        viewModel.updateHyperparams(draftTemperature, draftMaxTokens, draftTopP)
                    },
                    valueRange = 0.0f..1.0f,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Max length
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Max Tokens: ${draftMaxTokens}", style = MaterialTheme.typography.bodyMedium)
                }
                Slider(
                    value = draftMaxTokens.toFloat(),
                    onValueChange = { draftMaxTokens = it.toInt().coerceIn(1, 20000) },
                    onValueChangeFinished = {
                        viewModel.updateHyperparams(draftTemperature, draftMaxTokens, draftTopP)
                    },
                    valueRange = 1.0f..20000.0f,
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
                    "主题",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                // Light / Dark modes options
                Text("夜间模式:", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
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
                Text("主色调:", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
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

        // Career Statistics Section
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "生涯统计",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    TextButton(
                        onClick = { viewModel.resetCareerStats() },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Icon(Icons.Default.DeleteOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("清零", style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold))
                    }
                }

                Text(
                    "统计您在本沙盒系统中的累计代币消耗与对话偏好：",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        StatItemCard(
                            title = "输入 Token (Input)",
                            value = "${careerStats.inputTokens}",
                            icon = "📥",
                            modifier = Modifier.weight(1f)
                        )
                        StatItemCard(
                            title = "输出 Token (Output)",
                            value = "${careerStats.outputTokens}",
                            icon = "📤",
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        StatItemCard(
                            title = "累计总 Token 量",
                            value = "${careerStats.totalTokens}",
                            icon = "📊",
                            modifier = Modifier.weight(1f)
                        )
                        StatItemCard(
                            title = "总对话轮数",
                            value = "${careerStats.totalRounds} 轮",
                            icon = "💬",
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        StatItemCard(
                            title = "平均单次对话轮数",
                            value = "${String.format("%.1f", careerStats.avgRounds)} 轮",
                            icon = "📈",
                            modifier = Modifier.weight(1f)
                        )
                        StatItemCard(
                            title = "最活跃 NPC 角色",
                            value = careerStats.mostChattedNpc,
                            icon = "🎭",
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        StatItemCard(
                            title = "最活跃 Agent 工作流",
                            value = careerStats.mostChattedAgent,
                            icon = "🤖",
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

    }
}

@Composable
fun StatItemCard(
    title: String,
    value: String,
    icon: String,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.08f)),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(icon, style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(
                    title,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    value,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
            }
        }
    }
}
