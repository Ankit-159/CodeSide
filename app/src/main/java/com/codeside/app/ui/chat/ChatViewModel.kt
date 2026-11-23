package com.codeside.app.ui.chat

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.codeside.app.CodeSideApplication
import com.codeside.app.data.model.ChatMessage
import com.codeside.app.data.model.ChatRole
import com.codeside.app.data.provider.Cancellable
import com.codeside.app.data.provider.ProviderEvent
import com.codeside.app.data.provider.ProviderFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    val input: String = "",
    val isStreaming: Boolean = false,
    val error: String? = null,
)

class ChatViewModel(app: Application) : AndroidViewModel(app) {
    private val settingsStore = (app as CodeSideApplication).settingsStore
    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private var currentCall: Cancellable? = null

    fun onInputChange(value: String) {
        _uiState.update { it.copy(input = value) }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    fun send() {
        val text = _uiState.value.input.trim()
        if (text.isBlank() || _uiState.value.isStreaming) return

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

        val userMsg = ChatMessage(role = ChatRole.USER, content = text)
        val assistantMsg = ChatMessage(role = ChatRole.ASSISTANT, content = "", isStreaming = true)
        _uiState.update {
            it.copy(
                messages = it.messages + userMsg + assistantMsg,
                input = "",
                isStreaming = true,
                error = null,
            )
        }

        val messagesForApi = _uiState.value.messages.filter { !it.isStreaming }
        val client = ProviderFactory.get(profile.type)
        currentCall = client.streamChat(profile, messagesForApi) { event ->
            viewModelScope.launch(Dispatchers.Main) {
                handleEvent(event)
            }
        }
    }

    fun stop() {
        currentCall?.cancel()
        currentCall = null
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
            is ProviderEvent.Error -> {
                currentCall = null
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
                _uiState.update { state ->
                    state.copy(
                        isStreaming = false,
                        messages = state.messages.map { if (it.isStreaming) it.copy(isStreaming = false) else it },
                    )
                }
            }
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
