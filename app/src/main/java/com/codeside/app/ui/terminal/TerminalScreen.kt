package com.codeside.app.ui.terminal

import android.os.Build
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.codeside.app.CodeSideApplication
import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalSessionClient
import com.termux.view.TerminalView
import com.termux.view.TerminalViewClient

@Composable
fun TerminalScreen(onOpenDrawer: () -> Unit = {}, vm: TerminalViewModel = viewModel()) {
    if (Build.VERSION.SDK_INT >= 24) {
        TermuxTerminal(onOpenDrawer = onOpenDrawer, vm = vm)
    } else {
        LegacyTerminal(onOpenDrawer = onOpenDrawer, vm = vm)
    }
}

@Composable
private fun TermuxTerminal(onOpenDrawer: () -> Unit = {}, vm: TerminalViewModel = viewModel()) {
    val termState by vm.uiState.collectAsStateWithLifecycle()
    var showInstall by remember { mutableStateOf(false) }
    var bootstrapUrl by remember { mutableStateOf("") }
    var session by remember { mutableStateOf<TerminalSession?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var terminalView by remember { mutableStateOf<TerminalView?>(null) }
    val context = LocalContext.current

    fun startSession(view: TerminalView) {
        try {
            session?.finishIfRunning()
            val app = context.applicationContext as CodeSideApplication
            val env = com.codeside.app.terminal.TerminalEnvironment(app)
            env.ensureDirectories()
            com.codeside.app.util.AppLog.log("Terminal", "startSession shell=${env.shellPath()} prefix=${env.prefixDir}")
            val client = object : TerminalSessionClient {
                override fun onTextChanged(changedSession: TerminalSession) { view.onScreenUpdated() }
                override fun onTitleChanged(changedSession: TerminalSession) {}
                override fun onSessionFinished(finishedSession: TerminalSession) {
                    view.post { session = null }
                }
                override fun onCopyTextToClipboard(session: TerminalSession, text: String?) {}
                override fun onPasteTextFromClipboard(session: TerminalSession) {}
                override fun onBell(session: TerminalSession) {}
                override fun onColorsChanged(session: TerminalSession) {}
                override fun onTerminalCursorStateChange(state: Boolean) {}
                override fun getTerminalCursorStyle(): Int? = null
                override fun logError(tag: String?, message: String?) {}
                override fun logWarn(tag: String?, message: String?) {}
                override fun logInfo(tag: String?, message: String?) {}
                override fun logDebug(tag: String?, message: String?) {}
                override fun logVerbose(tag: String?, message: String?) {}
                override fun logStackTraceWithMessage(tag: String?, message: String?, e: Exception?) {}
                override fun logStackTrace(tag: String?, e: Exception?) {}
            }
            val s = TerminalSession(
                env.shellPath(),
                env.homeDir.absolutePath,
                arrayOf<String>(),
                env.buildEnv(),
                2000,
                client,
            )
            view.attachSession(s)
            session = s
            error = null
            view.requestFocus()
            com.codeside.app.util.AppLog.log("Terminal", "session started")
        } catch (t: Throwable) {
            error = t.message ?: "Failed to start terminal"
            com.codeside.app.util.AppLog.logThrowable("Terminal", "startSession failed", t)
        }
    }

    if (showInstall) {
        AlertDialog(
            onDismissRequest = { showInstall = false },
            title = { Text("Install") },
            text = {
                OutlinedTextField(
                    value = bootstrapUrl,
                    onValueChange = { bootstrapUrl = it },
                    placeholder = { Text("URL or Termux package name") },
                    singleLine = true,
                )
            },
            confirmButton = {
                Row {
                    TextButton(onClick = { vm.installBootstrap(bootstrapUrl); showInstall = false }) { Text("ZIP") }
                    TextButton(onClick = { vm.installDeb(bootstrapUrl); showInstall = false }) { Text("DEB") }
                    TextButton(onClick = { vm.installTermuxPackage(bootstrapUrl); showInstall = false }) { Text("Repo") }
                }
            },
            dismissButton = { TextButton(onClick = { showInstall = false }) { Text("Cancel") } },
        )
    }

    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onOpenDrawer) { Icon(Icons.Default.Menu, contentDescription = "Menu") }
                Text("Terminal", style = MaterialTheme.typography.titleMedium)
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                TextButton(onClick = { if (bootstrapUrl.isBlank()) bootstrapUrl = vm.currentBootstrapUrl(); showInstall = true }) { Text("Install") }
                TextButton(onClick = { vm.bootstrap() }) { Text("Bootstrap") }
                TextButton(onClick = { terminalView?.let { startSession(it) } }) { Text(if (session == null) "Start" else "Restart") }
                TextButton(onClick = { session?.finishIfRunning(); session = null }) { Text("Stop") }
            }
        }

        if (error != null) {
            Text(
                "Terminal error: $error",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            )
        }

        val sysLines = termState.lines.takeLast(4)
        if (sysLines.isNotEmpty()) {
            androidx.compose.material3.Card(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp),
            ) {
                Column(Modifier.padding(8.dp)) {
                    Text("System log", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    sysLines.forEach { line ->
                        Text(line, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        Box(modifier = Modifier.weight(1f).fillMaxWidth().background(MaterialTheme.colorScheme.surface)) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    TerminalView(ctx, null).apply {
                        setTerminalViewClient(object : TerminalViewClient {
                            override fun onScale(scale: Float): Float = scale
                            override fun onSingleTapUp(e: MotionEvent) {
                                requestFocus()
                                (ctx.getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as? android.view.inputmethod.InputMethodManager)
                                    ?.showSoftInput(this@apply, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
                            }
                            override fun shouldBackButtonBeMappedToEscape() = false
                            override fun shouldEnforceCharBasedInput() = false
                            override fun shouldUseCtrlSpaceWorkaround() = false
                            override fun isTerminalViewSelected() = true
                            override fun copyModeChanged(copyMode: Boolean) {}
                            override fun onKeyDown(keyCode: Int, e: KeyEvent, session: TerminalSession?) = false
                            override fun onKeyUp(keyCode: Int, e: KeyEvent) = false
                            override fun onLongPress(event: MotionEvent) = false
                            override fun readControlKey() = false
                            override fun readAltKey() = false
                            override fun readShiftKey() = false
                            override fun readFnKey() = false
                            override fun onCodePoint(codePoint: Int, ctrlDown: Boolean, session: TerminalSession?) = false
                            override fun onEmulatorSet() {}
                            override fun logError(tag: String?, message: String?) {}
                            override fun logWarn(tag: String?, message: String?) {}
                            override fun logInfo(tag: String?, message: String?) {}
                            override fun logDebug(tag: String?, message: String?) {}
                            override fun logVerbose(tag: String?, message: String?) {}
                            override fun logStackTraceWithMessage(tag: String?, message: String?, e: Exception?) {}
                            override fun logStackTrace(tag: String?, e: Exception?) {}
                        })
                        isFocusable = true
                        isFocusableInTouchMode = true
                        terminalView = this
                    }
                },
                update = { view -> terminalView = view },
            )
            if (session == null) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        "Terminal not started",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        "Press Start to launch the in-app terminal. If it fails, the error will appear above.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose { session?.finishIfRunning() }
    }
}

@Composable
private fun LegacyTerminal(onOpenDrawer: () -> Unit = {}, vm: TerminalViewModel = viewModel()) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    var showInstall by remember { mutableStateOf(false) }
    var bootstrapUrl by remember { mutableStateOf("") }

    LaunchedEffect(state.lines.size) {
        if (state.lines.isNotEmpty()) listState.animateScrollToItem(state.lines.lastIndex)
    }

    if (showInstall) {
        AlertDialog(
            onDismissRequest = { showInstall = false },
            title = { Text("Install") },
            text = {
                OutlinedTextField(
                    value = bootstrapUrl,
                    onValueChange = { bootstrapUrl = it },
                    placeholder = { Text("URL or Termux package name") },
                    singleLine = true,
                )
            },
            confirmButton = {
                Row {
                    TextButton(onClick = { vm.installBootstrap(bootstrapUrl); showInstall = false }) { Text("ZIP") }
                    TextButton(onClick = { vm.installDeb(bootstrapUrl); showInstall = false }) { Text("DEB") }
                    TextButton(onClick = { vm.installTermuxPackage(bootstrapUrl); showInstall = false }) { Text("Repo") }
                }
            },
            dismissButton = { TextButton(onClick = { showInstall = false }) { Text("Cancel") } },
        )
    }

    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onOpenDrawer) { Icon(Icons.Default.Menu, contentDescription = "Menu") }
                Text("Terminal", style = MaterialTheme.typography.titleMedium)
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                TextButton(onClick = { if (bootstrapUrl.isBlank()) bootstrapUrl = vm.currentBootstrapUrl(); showInstall = true }) { Text("Install") }
                TextButton(onClick = { vm.bootstrap() }) { Text("Bootstrap") }
                TextButton(onClick = { vm.clear() }) { Text("Clear") }
                TextButton(onClick = { if (state.running) vm.stop() else vm.start() }) { Text(if (state.running) "Stop" else "Start") }
            }
        }
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 8.dp),
        ) {
            items(state.lines) { line ->
                Text(
                    text = ansiToAnnotatedString(line, MaterialTheme.colorScheme.onBackground),
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                )
            }
        }
        Row(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
            OutlinedTextField(
                value = state.input,
                onValueChange = vm::onInputChange,
                modifier = Modifier.weight(1f),
                placeholder = { Text("Command") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { vm.sendInput() }),
            )
            TextButton(onClick = { vm.sendInput() }, enabled = state.running) { Text("Send") }
        }
    }
}
