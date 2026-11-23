package com.codeside.app.ui.terminal

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.codeside.app.terminal.TerminalSession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

data class TerminalUiState(
    val lines: List<String> = listOf("CodeSide terminal (M2 MVP). Press Start to launch /system/bin/sh."),
    val input: String = "",
    val running: Boolean = false,
)

class TerminalViewModel(app: Application) : AndroidViewModel(app) {
    private val _uiState = MutableStateFlow(TerminalUiState())
    val uiState: StateFlow<TerminalUiState> = _uiState.asStateFlow()

    private var session: TerminalSession? = null
    private val completed = mutableListOf<String>()
    private val tail = StringBuilder()

    fun onInputChange(value: String) {
        _uiState.update { it.copy(input = value) }
    }

    fun start() {
        if (_uiState.value.running) return
        val home = File(getApplication<Application>().filesDir, "home").apply { mkdirs() }
        completed.clear()
        tail.clear()
        _uiState.update { it.copy(lines = emptyList(), running = true) }
        val s = TerminalSession(
            onOutput = { text -> viewModelScope.launch { appendOutput(text) } },
            onExit = { code -> viewModelScope.launch { onExit(code) } },
        )
        session = s
        try {
            val env = arrayOf(
                "HOME=${home.absolutePath}",
                "PATH=/system/bin:/system/xbin",
                "TMPDIR=${getApplication<Application>().cacheDir.absolutePath}",
                "TERM=xterm-256color",
            )
            s.start(cmd = "/system/bin/sh", cwd = home.absolutePath, env = env)
        } catch (t: Throwable) {
            _uiState.update { it.copy(running = false, lines = listOf("[failed to start: ${t.message}]")) }
        }
    }

    fun sendInput() {
        val text = _uiState.value.input
        if (text.isBlank() || !_uiState.value.running) return
        session?.write(text + "\n")
        _uiState.update { it.copy(input = "") }
    }

    fun stop() {
        session?.close()
        session = null
        _uiState.update { it.copy(running = false) }
    }

    fun clear() {
        completed.clear()
        tail.clear()
        _uiState.update { it.copy(lines = emptyList()) }
    }

    private fun appendOutput(text: String) {
        val clean = stripAnsi(text).replace("\r", "")
        tail.append(clean)
        val parts = tail.toString().split('\n')
        completed.addAll(parts.dropLast(1))
        tail.clear()
        tail.append(parts.last())
        publishLines()
    }

    private fun onExit(code: Int) {
        session = null
        if (tail.isNotEmpty()) {
            completed.add(tail.toString())
            tail.clear()
        }
        completed.add("[process exited: $code]")
        _uiState.update { it.copy(running = false, lines = completed.takeLast(500)) }
    }

    private fun publishLines() {
        val snapshot = buildList {
            addAll(completed.takeLast(500))
            if (tail.isNotEmpty()) add(tail.toString())
        }
        _uiState.update { it.copy(lines = snapshot) }
    }

    private fun stripAnsi(text: String): String {
        return text.replace(Regex("\u001B\\[[;?0-9]*[ -/]*[@-~]"), "")
    }

    override fun onCleared() {
        session?.close()
        super.onCleared()
    }
}
