package com.codeside.app.ui.terminal

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.codeside.app.CodeSideApplication
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map

data class TerminalUiState(
    val lines: List<String> = emptyList(),
    val input: String = "",
    val running: Boolean = false,
)

class TerminalViewModel(app: Application) : AndroidViewModel(app) {
    private val terminal = (app as CodeSideApplication).terminalManager
    private val input = MutableStateFlow("")

    val uiState: StateFlow<TerminalUiState> = combine(terminal.state, input) { term, inp ->
        TerminalUiState(lines = term.lines, input = inp, running = term.running)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), TerminalUiState(lines = terminal.state.value.lines, running = terminal.state.value.running))

    fun onInputChange(value: String) { input.value = value }

    fun start() = terminal.start()

    fun sendInput() {
        val text = input.value
        if (text.isBlank()) return
        terminal.sendLine(text)
        input.value = ""
    }

    fun stop() = terminal.stop()
    fun clear() = terminal.clear()
    fun bootstrap() = terminal.bootstrap()
    fun installBootstrap(url: String) = terminal.installBootstrap(url)
    fun installDeb(url: String) = terminal.installDeb(url)
}
