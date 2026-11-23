package com.codeside.app.data.model

import java.util.UUID

enum class ChatRole { USER, ASSISTANT, SYSTEM, TOOL }

enum class ToolCardState { RUNNING, DONE, ERROR }

data class ToolCard(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val detail: String = "",
    val state: ToolCardState = ToolCardState.RUNNING,
    val expanded: Boolean = false,
)

data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val role: ChatRole,
    val content: String = "",
    val thinking: String = "",
    val toolCards: List<ToolCard> = emptyList(),
    val isStreaming: Boolean = false,
)
