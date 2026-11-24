package com.codeside.app.ui.chat

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.codeside.app.CodeSideApplication
import com.codeside.app.agent.AgentTools
import com.codeside.app.data.model.ChatMessage
import com.codeside.app.data.model.ChatRole
import com.codeside.app.data.model.ToolCard
import com.codeside.app.data.model.ToolCardState
import com.codeside.app.data.provider.Cancellable
import com.codeside.app.data.provider.ProviderEvent
import com.codeside.app.data.provider.ProviderFactory
import com.codeside.app.data.store.ChatStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

data class PendingToolApproval(
    val call: ProviderEvent.ToolCall,
)

data class ChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    val input: String = "",
    val isStreaming: Boolean = false,
    val error: String? = null,
    val pendingApproval: PendingToolApproval? = null,
)

class ChatViewModel(app: Application) : AndroidViewModel(app) {
    private val settingsStore = (app as CodeSideApplication).settingsStore
    private val agentTools = AgentTools(app.applicationContext)
    private val chatStore = ChatStore(app.applicationContext)
    private val json = Json { ignoreUnknownKeys = true }
    private val _uiState = MutableStateFlow(ChatUiState(messages = chatStore.load()))
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private var currentCall: Cancellable? = null
    private val pendingToolCalls = mutableListOf<ProviderEvent.ToolCall>()
    private var pendingExecution: MutableList<ProviderEvent.ToolCall> = mutableListOf()
    private val completedToolMessages = mutableListOf<ChatMessage>()

    fun onInputChange(value: String) {
        _uiState.update { it.copy(input = value) }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    fun newChat() {
        stop()
        chatStore.clear()
        _uiState.update { ChatUiState() }
    }

    private fun persist() {
        chatStore.save(_uiState.value.messages)
    }

    fun send() {
        val text = _uiState.value.input.trim()
        if (text.isBlank() || _uiState.value.isStreaming) return
        val userMsg = ChatMessage(role = ChatRole.USER, content = text)
        _uiState.update { it.copy(messages = it.messages + userMsg, input = "", error = null) }
        persist()
        startAssistantResponse()
    }

    private fun startAssistantResponse() {
        val profile = settingsStore.activeProfile()
        if (profile == null) {
            _uiState.update { it.copy(error = "No active provider profile. Add one in Settings.") }
            return
        }
        if (profile.apiKey.isBlank() && profile.type != com.codeside.app.data.model.ProviderType.CUSTOM) {
            _uiState.update { it.copy(error = "API key missing for ${profile.name}.") }
            return
        }
        if (profile.baseUrl.isBlank() || profile.model.isBlank()) {
            _uiState.update { it.copy(error = "Profile is incomplete: base URL and model are required.") }
            return
        }

        val assistantMsg = ChatMessage(role = ChatRole.ASSISTANT, content = "", isStreaming = true)
        pendingToolCalls.clear()
        _uiState.update { it.copy(messages = it.messages + assistantMsg, isStreaming = true, error = null) }

        val messagesForApi = _uiState.value.messages.filter { !it.isStreaming }
        val client = ProviderFactory.get(profile.type)
        currentCall = client.streamChat(profile, messagesForApi) { event ->
            viewModelScope.launch(Dispatchers.Main) { handleEvent(event) }
        }
    }

    fun stop() {
        currentCall?.cancel()
        currentCall = null
        pendingToolCalls.clear()
        _uiState.update { state ->
            state.copy(
                isStreaming = false,
                messages = state.messages.map { if (it.isStreaming) it.copy(isStreaming = false) else it },
            )
        }
    }

    private fun handleEvent(event: ProviderEvent) {
        when (event) {
            is ProviderEvent.TextDelta -> updateStreaming { it.copy(content = it.content + event.text) }
            is ProviderEvent.ThinkingDelta -> updateStreaming { it.copy(thinking = it.thinking + event.text) }
            is ProviderEvent.ToolCall -> {
                pendingToolCalls += event
                updateStreaming {
                    it.copy(toolCards = it.toolCards + ToolCard(title = event.name, detail = event.arguments, state = ToolCardState.RUNNING))
                }
            }
            is ProviderEvent.Error -> {
                currentCall = null
                pendingToolCalls.clear()
                _uiState.update { state ->
                    state.copy(
                        isStreaming = false,
                        error = event.message,
                        messages = state.messages.map { msg ->
                            if (msg.isStreaming) msg.copy(isStreaming = false, content = msg.content.ifBlank { "Error: ${event.message}" }) else msg
                        },
                    )
                }
            }
            ProviderEvent.Done -> {
                currentCall = null
                val calls = pendingToolCalls.toList()
                pendingToolCalls.clear()
                _uiState.update { state ->
                    state.copy(
                        isStreaming = calls.isEmpty(),
                        messages = state.messages.map { if (it.isStreaming) it.copy(isStreaming = calls.isNotEmpty()) else it },
                    )
                }
                if (calls.isNotEmpty()) executeToolCalls(calls) else persist()
            }
        }
    }

    private fun executeToolCalls(calls: List<ProviderEvent.ToolCall>) {
        val toolInfos = calls.map { com.codeside.app.data.model.ToolCallInfo(it.id, it.name, it.arguments) }
        _uiState.update { state ->
            val idx = state.messages.indexOfLast { it.role == ChatRole.ASSISTANT }
            if (idx < 0) return@update state
            val updated = state.messages.toMutableList()
            updated[idx] = updated[idx].copy(toolCalls = toolInfos)
            state.copy(messages = updated)
        }
        pendingExecution = calls.toMutableList()
        completedToolMessages.clear()
        processNextTool()
    }

    private fun needsApproval(name: String): Boolean {
        if (settingsStore.state.value.allowedTools.contains(name)) return false
        return name == "run_command" || name == "write_file" || name == "edit_file"
    }

    fun allowToolAlways(name: String) {
        settingsStore.allowTool(name)
        approvePendingTool()
    }

    private fun processNextTool() {
        val next = pendingExecution.firstOrNull() ?: run {
            val msgs = completedToolMessages.toList()
            completedToolMessages.clear()
            _uiState.update { it.copy(messages = it.messages + msgs, isStreaming = false) }
            persist()
            startAssistantResponse()
            return
        }
        if (needsApproval(next.name)) {
            _uiState.update { it.copy(pendingApproval = PendingToolApproval(next)) }
            return
        }
        executeApprovedTool(next)
    }

    fun approvePendingTool() {
        val pending = _uiState.value.pendingApproval ?: return
        _uiState.update { it.copy(pendingApproval = null) }
        executeApprovedTool(pending.call)
    }

    fun denyPendingTool() {
        val pending = _uiState.value.pendingApproval ?: return
        _uiState.update { it.copy(pendingApproval = null) }
        pendingExecution.remove(pending.call)
        completedToolMessages += ChatMessage(
            role = ChatRole.TOOL,
            content = "Denied by user.",
            toolCallId = pending.call.id,
            toolName = pending.call.name,
            toolArgs = pending.call.arguments,
        )
        markToolCard(pending.call.name, ToolCardState.ERROR, "Denied by user.")
        processNextTool()
    }

    private fun executeApprovedTool(call: ProviderEvent.ToolCall) {
        pendingExecution.remove(call)
        viewModelScope.launch(Dispatchers.Default) {
            val result = runTool(call.name, call.arguments)
            val status = if (result.ok) ToolCardState.DONE else ToolCardState.ERROR
            completedToolMessages += ChatMessage(
                role = ChatRole.TOOL,
                content = result.output,
                toolCallId = call.id,
                toolName = call.name,
                toolArgs = call.arguments,
            )
            launch(Dispatchers.Main) {
                markToolCard(call.name, status, result.output.take(4000))
                processNextTool()
            }
        }
    }

    private fun markToolCard(name: String, status: ToolCardState, detail: String) {
        _uiState.update { state ->
            val idx = state.messages.indexOfLast { it.role == ChatRole.ASSISTANT && it.toolCalls.isNotEmpty() }
            if (idx < 0) return@update state
            val updated = state.messages.toMutableList()
            val msg = updated[idx]
            updated[idx] = msg.copy(toolCards = msg.toolCards.map { c ->
                if (c.title == name && c.state == ToolCardState.RUNNING) c.copy(state = status, detail = detail) else c
            })
            state.copy(messages = updated)
        }
    }

    private fun runTool(name: String, argsJson: String): com.codeside.app.agent.ToolResult {
        val args = runCatching { json.parseToJsonElement(argsJson).jsonObject }.getOrNull()
        fun s(key: String) = args?.get(key)?.jsonPrimitive?.contentOrNull ?: ""
        return when (name) {
            "run_command" -> agentTools.runCommand(s("command"))
            "read_file" -> agentTools.readFile(s("path"))
            "write_file" -> agentTools.writeFile(s("path"), s("content"))
            "edit_file" -> agentTools.editFile(s("path"), s("oldString"), s("newString"))
            "list_dir" -> agentTools.listDir(s("path").ifBlank { "." })
            "search" -> agentTools.search(s("query"), s("path").ifBlank { "." })
            "list_granted_folders" -> agentTools.listGrantedFolders()
            "read_granted_uri" -> agentTools.readGrantedUri(s("uri"))
            else -> com.codeside.app.agent.ToolResult(false, "Unknown tool: $name")
        }
    }

    private fun updateStreaming(transform: (ChatMessage) -> ChatMessage) {
        _uiState.update { state ->
            val idx = state.messages.indexOfLast { it.isStreaming }
            if (idx < 0) return@update state
            val updated = state.messages.toMutableList()
            updated[idx] = transform(updated[idx])
            state.copy(messages = updated)
        }
    }
}
