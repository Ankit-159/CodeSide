package com.codeside.app.terminal

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class TerminalState(
    val lines: List<String> = emptyList(),
    val running: Boolean = false,
)

class TerminalManager(context: Context) {
    private val appContext = context.applicationContext
    private val envManager = TerminalEnvironment(appContext)
    private val bootstrapManager = BootstrapManager(appContext)
    private val debInstaller = DebInstaller(envManager)
    private val termuxRepo = TermuxRepo()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val completed = mutableListOf<String>()
    private val tail = StringBuilder()
    private var session: TerminalSession? = null

    private val _state = MutableStateFlow(
        TerminalState(lines = listOf("CodeSide terminal. ${envManager.bootstrapStatus()}. Press Start."))
    )
    val state: StateFlow<TerminalState> = _state.asStateFlow()

    fun start() {
        if (_state.value.running) return
        envManager.ensureDirectories()
        completed.clear()
        tail.clear()
        publish()
        val s = TerminalSession(
            onOutput = { text -> scope.launch { appendOutput(text) } },
            onExit = { code -> scope.launch { onExit(code) } },
        )
        session = s
        try {
            s.start(
                cmd = envManager.shellPath(),
                cwd = envManager.homeDir.absolutePath,
                env = envManager.buildEnv(),
            )
            _state.update { it.copy(running = true) }
        } catch (t: Throwable) {
            completed.add("[failed to start: ${t.message}]")
            publish(running = false)
        }
    }

    fun send(text: String) {
        val s = session ?: return
        if (!_state.value.running) return
        s.write(text)
    }

    fun sendLine(text: String) {
        send(text + "\n")
    }

    fun stop() {
        session?.close()
        session = null
        _state.update { it.copy(running = false) }
    }

    fun clear() {
        completed.clear()
        tail.clear()
        publish(_state.value.running)
    }

    fun bootstrap() {
        scope.launch {
            appendSystem("[bootstrap] installing basic prefix...")
            val result = bootstrapManager.ensureBasicBootstrap()
            result.onSuccess { appendSystem("[bootstrap] $it") }
                .onFailure { appendSystem("[bootstrap] failed: ${it.message}") }
        }
    }

    fun installBootstrap(url: String) {
        val trimmed = url.trim()
        if (trimmed.isBlank()) return
        scope.launch {
            appendSystem("[bootstrap] downloading $trimmed")
            val result = bootstrapManager.installBootstrapZip(trimmed)
            result.onSuccess { appendSystem("[bootstrap] $it") }
                .onFailure { appendSystem("[bootstrap] failed: ${it.message}") }
        }
    }

    fun installDeb(url: String) {
        val trimmed = url.trim()
        if (trimmed.isBlank()) return
        scope.launch {
            appendSystem("[pkg] installing $trimmed")
            val result = debInstaller.installFromUrl(trimmed)
            result.onSuccess { appendSystem("[pkg] $it") }
                .onFailure { appendSystem("[pkg] failed: ${it.message}") }
        }
    }

    fun installTermuxPackage(name: String) {
        val pkg = name.trim()
        if (pkg.isBlank()) return
        scope.launch {
            appendSystem("[pkg] resolving $pkg from Termux repo...")
            val abi = android.os.Build.SUPPORTED_ABIS.firstOrNull() ?: "arm64-v8a"
            val urlResult = termuxRepo.findPackageUrl(pkg, abi)
            urlResult.onFailure { appendSystem("[pkg] resolve failed: ${it.message}") }
                .onSuccess { url ->
                    appendSystem("[pkg] downloading $url")
                    val result = debInstaller.installFromUrl(url)
                    result.onSuccess { appendSystem("[pkg] $it") }
                        .onFailure { appendSystem("[pkg] failed: ${it.message}") }
                }
        }
    }

    fun resize(rows: Int, cols: Int) {
        session?.resize(rows, cols)
    }

    private fun appendOutput(text: String) {
        val clean = stripAnsi(text).replace("\r", "")
        tail.append(clean)
        val parts = tail.toString().split('\n')
        completed.addAll(parts.dropLast(1))
        tail.clear()
        tail.append(parts.last())
        publish(_state.value.running)
    }

    private fun onExit(code: Int) {
        session = null
        if (tail.isNotEmpty()) {
            completed.add(tail.toString())
            tail.clear()
        }
        completed.add("[process exited: $code]")
        publish(running = false)
    }

    private fun appendSystem(line: String) {
        completed.add(line)
        publish(_state.value.running)
    }

    private fun publish(running: Boolean = _state.value.running) {
        val snapshot = buildList {
            addAll(completed.takeLast(800))
            if (tail.isNotEmpty()) add(tail.toString())
        }
        _state.value = TerminalState(lines = snapshot, running = running)
    }

    private fun stripAnsi(text: String): String {
        // Keep SGR color/style sequences, strip other terminal control sequences.
        return text.replace(Regex("\u001B\\[[;?0-9]*[ -/]*[A-LN-Za-ln-z~]"), "")
    }
}
