package com.codeside.app.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
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
import com.codeside.app.data.model.ChatMessage
import com.codeside.app.data.model.ChatRole
import com.codeside.app.data.model.ToolCard
import com.codeside.app.data.model.ToolCardState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(vm: ChatViewModel = viewModel()) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()

    LaunchedEffect(state.messages.size, state.messages.lastOrNull()?.content, state.messages.lastOrNull()?.thinking) {
        if (state.messages.isNotEmpty()) {
            listState.animateScrollToItem(state.messages.lastIndex)
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("CodeSide") }) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item { Spacer(Modifier.height(4.dp)) }
                items(state.messages, key = { it.id }) { msg ->
                    MessageBubble(msg)
                }
                if (state.error != null) {
                    item {
                        ErrorCard(state.error!!, onDismiss = vm::clearError)
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
    }
}

@Composable
private fun MessageBubble(msg: ChatMessage) {
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
                .padding(12.dp),
        ) {
            if (msg.thinking.isNotBlank()) {
                ExpandableCard(title = "Thinking", body = msg.thinking)
                Spacer(Modifier.height(8.dp))
            }
            msg.toolCards.forEach { card ->
                ToolCardView(card)
                Spacer(Modifier.height(6.dp))
            }
            if (msg.content.isNotBlank()) {
                Text(msg.content, style = MaterialTheme.typography.bodyMedium)
            } else if (msg.isStreaming) {
                Text("…", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
    var expanded by remember { mutableStateOf(false) }
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
            if (expanded) {
                Text(body, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun ErrorCard(message: String, onDismiss: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.12f))) {
        Column(Modifier.padding(12.dp)) {
            Text("Error", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.error)
            Spacer(Modifier.height(4.dp))
            Text(message, style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = onDismiss) { Text("Dismiss") }
        }
    }
}
