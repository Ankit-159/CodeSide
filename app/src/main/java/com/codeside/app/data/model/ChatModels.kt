package com.codeside.app.data.model

import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
enum class ChatRole { USER, ASSISTANT, SYSTEM, TOOL }

@Serializable
enum class ToolCardState { RUNNING, DONE, ERROR }

@Serializable
data class ToolCard(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val detail: String = "",
    val state: ToolCardState = ToolCardState.RUNNING,
    val expanded: Boolean = false,
)

@Serializable
data class ToolCallInfo(
    val id: String,
    val name: String,
    val arguments: String,
)

@Serializable
data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val role: ChatRole,
    val content: String = "",
    val thinking: String = "",
    val toolCards: List<ToolCard> = emptyList(),
    val isStreaming: Boolean = false,
    val toolCallId: String? = null,
    val toolName: String? = null,
    val toolArgs: String? = null,
    val toolCalls: List<ToolCallInfo> = emptyList(),
)
