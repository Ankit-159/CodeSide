package com.codeside.app.ui.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import com.codeside.app.data.model.ChatMessage
import com.codeside.app.data.model.ChatRole
import com.codeside.app.data.model.ToolCard
import com.codeside.app.data.model.ToolCardState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(onOpenDrawer: () -> Unit = {}, vm: ChatViewModel = viewModel(), settingsVm: com.codeside.app.ui.settings.SettingsViewModel = viewModel()) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    val settingsState by settingsVm.state.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    var showQuickSettings by remember { mutableStateOf(false) }
    var showModelMenu by remember { mutableStateOf(false) }
    var showHistory by remember { mutableStateOf(false) }
    var historyQuery by remember { mutableStateOf("") }
    var renameSessionId by remember { mutableStateOf<String?>(null) }
    var renameText by remember { mutableStateOf("") }
    var chatModels by remember { mutableStateOf<List<String>>(emptyList()) }
    var loadingChatModels by remember { mutableStateOf(false) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val historySheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = androidx.compose.ui.platform.LocalContext.current
    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
    val attachLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch(kotlinx.coroutines.Dispatchers.IO) {
            runCatching {
                val name = uri.lastPathSegment?.substringAfterLast('/') ?: "attachment"
                val text = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText().take(12000) }.orEmpty()
                if (text.isNotBlank()) {
                    val block = "\n\nAttached file: $name\n```\n$text\n```\n"
                    val current = vm.uiState.value.input
                    vm.onInputChange((current + block).trimStart())
                }
            }
        }
    }

    LaunchedEffect(state.messages.size, state.messages.lastOrNull()?.content, state.messages.lastOrNull()?.thinking) {
        if (state.messages.isNotEmpty()) {
            listState.animateScrollToItem(state.messages.lastIndex)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    val title = state.sessions.firstOrNull { it.id == state.activeSessionId }?.title?.takeIf { it.isNotBlank() } ?: "CodeSide"
                    Text(title, maxLines = 1, modifier = Modifier.clickable { showHistory = true })
                },
                navigationIcon = {
                    IconButton(onClick = onOpenDrawer) {
                        Icon(Icons.Default.Menu, contentDescription = "Menu")
                    }
                },
                actions = {
                    IconButton(onClick = { showHistory = true }) {
                        Icon(Icons.Default.History, contentDescription = "History")
                    }
                    IconButton(onClick = { showQuickSettings = true }) {
                        Icon(Icons.Default.Settings, contentDescription = "API settings")
                    }
                    IconButton(onClick = { vm.newChat() }) {
                        Icon(Icons.Default.Add, contentDescription = "New chat")
                    }
                },
                windowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
            )
        },
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding(),
        ) {
            val activeProfile = settingsState.profiles.firstOrNull { it.id == settingsState.activeProfileId } ?: settingsState.profiles.firstOrNull()
            if (activeProfile != null) {
                androidx.compose.foundation.layout.Box(Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
                    androidx.compose.material3.OutlinedButton(onClick = {
                        showModelMenu = true
                        if (chatModels.isEmpty() && !loadingChatModels) {
                            loadingChatModels = true
                            scope.launch {
                                val r = com.codeside.app.data.provider.ModelFetcher.fetchModels(activeProfile)
                                loadingChatModels = false
                                r.onSuccess { chatModels = it }
                            }
                        }
                    }) {
                        Text(activeProfile.model.ifBlank { "Select model" })
                        Text(" ▾", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    androidx.compose.material3.DropdownMenu(expanded = showModelMenu, onDismissRequest = { showModelMenu = false }) {
                        if (loadingChatModels) {
                            androidx.compose.material3.DropdownMenuItem(text = { Text("Loading models...") }, onClick = {})
                        }
                        chatModels.take(80).forEach { m ->
                            androidx.compose.material3.DropdownMenuItem(text = { Text(m) }, onClick = {
                                settingsVm.save(activeProfile.copy(model = m))
                                showModelMenu = false
                            })
                        }
                        if (!loadingChatModels && chatModels.isEmpty()) {
                            androidx.compose.material3.DropdownMenuItem(text = { Text("No models fetched") }, onClick = {})
                        }
                    }
                }
            }
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item { Spacer(Modifier.height(4.dp)) }
                if (state.messages.isEmpty()) {
                    item {
                        EmptyChatState(
                            needsSetup = activeProfile == null || (activeProfile.apiKey.isBlank() && activeProfile.type != com.codeside.app.data.model.ProviderType.CUSTOM),
                            onSuggestion = { vm.onInputChange(it); vm.send() },
                            onOpenSettings = { showQuickSettings = true },
                        )
                    }
                }
                val lastAssistantId = state.messages.lastOrNull { it.role == ChatRole.ASSISTANT && !it.isStreaming }?.id
                items(state.messages, key = { it.id }) { msg ->
                    MessageBubble(
                        msg,
                        reduceMotion = settingsState.reduceMotion,
                        onRegenerate = if (msg.id == lastAssistantId) vm::regenerate else null,
                    )
                }
                if (state.error != null) {
                    item {
                        ErrorCard(state.error!!, onDismiss = vm::clearError, onRetry = vm::retry)
                    }
                }
                item { Spacer(Modifier.height(8.dp)) }
            }

            if (state.pendingApproval != null) {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.08f)),
                ) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Approval required: ${state.pendingApproval!!.call.name}", style = MaterialTheme.typography.labelLarge)
                        Text(state.pendingApproval!!.call.arguments.take(500), style = MaterialTheme.typography.bodySmall)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = { vm.approvePendingTool() }) { Text("Approve") }
                            TextButton(onClick = { vm.allowToolAlways(state.pendingApproval!!.call.name) }) { Text("Always allow") }
                            TextButton(onClick = { vm.denyPendingTool() }) { Text("Deny") }
                        }
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                IconButton(onClick = { attachLauncher.launch(arrayOf("text/*", "application/json", "application/xml", "application/javascript", "*/*")) }) {
                    Icon(Icons.Default.AttachFile, contentDescription = "Attach file")
                }
                OutlinedTextField(
                    value = state.input,
                    onValueChange = vm::onInputChange,
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Ask CodeSide...") },
                    maxLines = 6,
                )
                IconButton(
                    onClick = { if (state.isStreaming) vm.stop() else vm.send() },
                ) {
                    Icon(
                        imageVector = if (state.isStreaming) Icons.Default.Stop else Icons.AutoMirrored.Filled.Send,
                        contentDescription = if (state.isStreaming) "Stop" else "Send",
                    )
                }
            }
        }

        if (showHistory) {
            ModalBottomSheet(
                onDismissRequest = { showHistory = false },
                sheetState = historySheetState,
            ) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("Chats", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        TextButton(onClick = {
                            vm.newChat()
                            showHistory = false
                        }) { Text("New chat") }
                    }
                    OutlinedTextField(
                        value = historyQuery,
                        onValueChange = { historyQuery = it },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        placeholder = { Text("Search chats") },
                        singleLine = true,
                    )
                    val filteredSessions = if (historyQuery.isBlank()) state.sessions else state.sessions.filter {
                        it.title.contains(historyQuery, ignoreCase = true) || it.preview.contains(historyQuery, ignoreCase = true)
                    }
                    LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp)) {
                        items(filteredSessions, key = { it.id }) { session ->
                            val active = session.id == state.activeSessionId
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .clickable {
                                        vm.switchSession(session.id)
                                        showHistory = false
                                    },
                                colors = CardDefaults.cardColors(
                                    containerColor = if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant
                                ),
                            ) {
                                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text(session.title.ifBlank { "New chat" }, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
                                        if (session.preview.isNotBlank()) {
                                            Text(session.preview, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                                        }
                                    }
                                    IconButton(onClick = {
                                        vm.exportSessionMarkdown(session.id) { md ->
                                            clipboard.setText(androidx.compose.ui.text.AnnotatedString(md))
                                        }
                                    }) {
                                        Icon(Icons.Default.Share, contentDescription = "Export chat")
                                    }
                                    IconButton(onClick = { renameSessionId = session.id; renameText = session.title }) {
                                        Icon(Icons.Default.Edit, contentDescription = "Rename chat")
                                    }
                                    IconButton(onClick = { vm.deleteSession(session.id) }) {
                                        Icon(Icons.Default.Delete, contentDescription = "Delete chat")
                                    }
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                }
            }
        }

        if (renameSessionId != null) {
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { renameSessionId = null },
                title = { Text("Rename chat") },
                text = {
                    OutlinedTextField(value = renameText, onValueChange = { renameText = it }, singleLine = true, modifier = Modifier.fillMaxWidth())
                },
                confirmButton = {
                    TextButton(onClick = {
                        val id = renameSessionId ?: return@TextButton
                        vm.renameSession(id, renameText)
                        renameSessionId = null
                    }) { Text("Save") }
                },
                dismissButton = { TextButton(onClick = { renameSessionId = null }) { Text("Cancel") } },
            )
        }

        if (showQuickSettings) {
            QuickApiSettingsDialog(
                settingsState = settingsState,
                onDismiss = { showQuickSettings = false },
                onSetActive = settingsVm::setActive,
                onSave = settingsVm::save,
            )
        }
    }
}

@Composable
private fun MessageBubble(msg: ChatMessage, reduceMotion: Boolean = false, onRegenerate: (() -> Unit)? = null) {
    val isUser = msg.role == ChatRole.USER
    val align = if (isUser) Alignment.CenterEnd else Alignment.CenterStart
    val bg = if (isUser) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surfaceVariant
    val shape = RoundedCornerShape(16.dp)

    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = align) {
        Column(
            modifier = Modifier
                .widthIn(max = 340.dp)
                .clip(shape)
                .background(bg)
                .then(if (reduceMotion) Modifier else Modifier.animateContentSize(animationSpec = tween(180)))
                .padding(12.dp),
        ) {
            if (!isUser && msg.model.isNotBlank()) {
                Text(msg.model, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(4.dp))
            }
            if (msg.thinking.isNotBlank()) {
                ExpandableCard(title = "Thinking", body = msg.thinking)
                Spacer(Modifier.height(8.dp))
            }
            msg.toolCards.forEach { card ->
                ToolCardView(card)
                Spacer(Modifier.height(6.dp))
            }
            if (msg.content.isNotBlank()) {
                if (isUser) Text(msg.content, style = MaterialTheme.typography.bodyMedium)
                else SimpleMarkdown(msg.content)
            } else if (msg.isStreaming) {
                if (reduceMotion) Text("…", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                else TypingIndicator()
            }
            if (msg.content.isNotBlank() && !msg.isStreaming) {
                val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = { clipboard.setText(androidx.compose.ui.text.AnnotatedString(msg.content)) }) {
                        Text("Copy", style = MaterialTheme.typography.labelSmall)
                    }
                    if (!isUser && onRegenerate != null) {
                        TextButton(onClick = onRegenerate) {
                            Text("Regenerate", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ToolCardView(card: ToolCard) {
    val status = when (card.state) {
        ToolCardState.RUNNING -> "running"
        ToolCardState.DONE -> "done"
        ToolCardState.ERROR -> "error"
    }
    ExpandableCard(title = "${card.title} · $status", body = card.detail.ifBlank { "(no output)" })
}

@Composable
private fun ExpandableCard(title: String, body: String) {
    val isError = title.contains("error", ignoreCase = true)
    val isFileChange = title.contains("write_file", true) || title.contains("edit_file", true)
    var expanded by remember(title, body) { mutableStateOf(isError || isFileChange) }
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(title, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                TextButton(onClick = { expanded = !expanded }) {
                    Text(if (expanded) "Hide" else "Show")
                }
            }
            val preview = body.lineSequence().firstOrNull { it.isNotBlank() }?.take(120).orEmpty()
            if (!expanded && preview.isNotBlank()) {
                Text(preview, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
            if (expanded) {
                Text(body, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun ErrorCard(message: String, onDismiss: () -> Unit, onRetry: () -> Unit = {}) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.12f))) {
        Column(Modifier.padding(12.dp)) {
            Text("Error", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.error)
            Spacer(Modifier.height(4.dp))
            Text(message, style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onRetry) { Text("Retry") }
                TextButton(onClick = onDismiss) { Text("Dismiss") }
            }
        }
    }
}

@Composable
private fun QuickApiSettingsDialog(
    settingsState: com.codeside.app.data.store.SettingsState,
    onDismiss: () -> Unit,
    onSetActive: (String) -> Unit,
    onSave: (com.codeside.app.data.model.ProviderProfile) -> Unit,
) {
    val active = settingsState.profiles.firstOrNull { it.id == settingsState.activeProfileId } ?: settingsState.profiles.firstOrNull()
    var selectedId by remember(settingsState.activeProfileId) { mutableStateOf(settingsState.activeProfileId ?: active?.id) }
    val selected = settingsState.profiles.firstOrNull { it.id == selectedId } ?: active
    var baseUrl by remember(selected?.id) { mutableStateOf(selected?.baseUrl ?: "") }
    var model by remember(selected?.id) { mutableStateOf(selected?.model ?: "") }
    var apiKey by remember(selected?.id) { mutableStateOf(selected?.apiKey ?: "") }
    var expanded by remember { mutableStateOf(false) }
    var models by remember(selected?.id) { mutableStateOf<List<String>>(emptyList()) }
    var loadingModels by remember(selected?.id) { mutableStateOf(false) }
    var modelsExpanded by remember { mutableStateOf(false) }
    var modelsError by remember(selected?.id) { mutableStateOf<String?>(null) }

    LaunchedEffect(selected?.id, baseUrl, apiKey, selected?.type) {
        val p = selected ?: return@LaunchedEffect
        if (p.apiKey.isBlank() && p.type != com.codeside.app.data.model.ProviderType.CUSTOM) return@LaunchedEffect
        loadingModels = true
        modelsError = null
        val r = com.codeside.app.data.provider.ModelFetcher.fetchModels(p.copy(baseUrl = baseUrl, model = model, apiKey = apiKey))
        loadingModels = false
        r.onSuccess { models = it }.onFailure { modelsError = it.message }
    }

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Quick API settings") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Box {
                    androidx.compose.material3.OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(selected?.name?.ifBlank { selected?.type?.name ?: "Profile" } ?: "No profile")
                    }
                    androidx.compose.material3.DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        settingsState.profiles.forEach { p ->
                            androidx.compose.material3.DropdownMenuItem(
                                text = { Text(p.name.ifBlank { p.type.name }) },
                                onClick = {
                                    selectedId = p.id
                                    onSetActive(p.id)
                                    expanded = false
                                },
                            )
                        }
                    }
                }
                OutlinedTextField(value = baseUrl, onValueChange = { baseUrl = it }, label = { Text("Base URL") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    OutlinedTextField(value = model, onValueChange = { model = it }, label = { Text("Model") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (loadingModels) Text("Fetching models...", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (models.isNotEmpty()) {
                            Box {
                                androidx.compose.material3.OutlinedButton(onClick = { modelsExpanded = true }) { Text("Pick model (${models.size})") }
                                androidx.compose.material3.DropdownMenu(expanded = modelsExpanded, onDismissRequest = { modelsExpanded = false }) {
                                    models.take(80).forEach { m ->
                                        androidx.compose.material3.DropdownMenuItem(text = { Text(m) }, onClick = { model = m; modelsExpanded = false })
                                    }
                                }
                            }
                        }
                    }
                    if (modelsError != null) Text(modelsError!!, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
                OutlinedTextField(value = apiKey, onValueChange = { apiKey = it }, label = { Text("API key") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val p = selected ?: return@TextButton
                onSave(p.copy(baseUrl = baseUrl, model = model, apiKey = apiKey))
                onDismiss()
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun TypingIndicator() {
    val transition = rememberInfiniteTransition(label = "typing")
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(vertical = 4.dp)) {
        repeat(3) { i ->
            val alpha by transition.animateFloat(
                initialValue = 0.25f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(500, delayMillis = i * 120),
                    repeatMode = RepeatMode.Reverse,
                ),
                label = "dot$i",
            )
            Box(
                Modifier
                    .size(7.dp)
                    .clip(RoundedCornerShape(50))
                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha))
            )
        }
    }
}

@Composable
private fun EmptyChatState(needsSetup: Boolean = false, onSuggestion: (String) -> Unit, onOpenSettings: () -> Unit = {}) {
    val suggestions = listOf(
        "Explain this project structure",
        "Find bugs in my code",
        "Write a script for my task",
        "Help me set up the terminal",
    )
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("What can I help you build?", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Chat with your AI agent, run tools, and work in your workspace.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (needsSetup) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f))) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Set up an API provider to start chatting.", style = MaterialTheme.typography.bodyMedium)
                    androidx.compose.material3.Button(onClick = onOpenSettings) { Text("Configure API") }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        suggestions.forEach { s ->
            androidx.compose.material3.OutlinedButton(onClick = { onSuggestion(s) }, modifier = Modifier.fillMaxWidth()) {
                Text(s)
            }
        }
    }
}
