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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
    var showMenu by remember { mutableStateOf(false) }
    var showLog by remember { mutableStateOf(false) }
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
                override fun onCopyTextToClipboard(session: TerminalSession, text: String?) {
                    if (text.isNullOrEmpty()) return
                    val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                    clipboard?.setPrimaryClip(android.content.ClipData.newPlainText("terminal", text))
                }
                override fun onPasteTextFromClipboard(session: TerminalSession) {
                    val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                    val clip = clipboard?.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString()
                    if (!clip.isNullOrEmpty()) session.write(clip)
                }
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

    if (showLog) {
        AlertDialog(
            onDismissRequest = { showLog = false },
            title = { Text("System log") },
            text = {
                androidx.compose.foundation.lazy.LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 360.dp)) {
                    items(termState.lines.takeLast(200)) { line ->
                        Text(line, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace))
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showLog = false }) { Text("Close") } },
        )
    }

    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).imePadding()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onOpenDrawer) { Icon(Icons.Default.Menu, contentDescription = "Menu") }
            Text("Terminal", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Box {
                IconButton(onClick = { showMenu = true }) { Icon(Icons.Default.MoreVert, contentDescription = "More") }
                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                    DropdownMenuItem(text = { Text(if (session == null) "Start" else "Restart") }, onClick = { showMenu = false; terminalView?.let { startSession(it) } })
                    DropdownMenuItem(text = { Text("Stop") }, onClick = { showMenu = false; session?.finishIfRunning(); session = null })
                    DropdownMenuItem(text = { Text("Bootstrap") }, onClick = { showMenu = false; vm.bootstrap() })
                    DropdownMenuItem(text = { Text("Install") }, onClick = { showMenu = false; if (bootstrapUrl.isBlank()) bootstrapUrl = vm.currentBootstrapUrl(); showInstall = true })
                    DropdownMenuItem(text = { Text("System log") }, onClick = { showMenu = false; showLog = true })
                }
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



        Box(modifier = Modifier.weight(1f).fillMaxWidth().background(MaterialTheme.colorScheme.surface)) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    val container = SafeTerminalView(ctx, null)
                    container.terminalView.apply {
                        setBackgroundColor(android.graphics.Color.parseColor("#111827"))
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
                    container
                },
                update = { view -> terminalView = view.terminalView },
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
    var showMenu by remember { mutableStateOf(false) }
    var showLog by remember { mutableStateOf(false) }

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

    if (showLog) {
        AlertDialog(
            onDismissRequest = { showLog = false },
            title = { Text("System log") },
            text = {
                androidx.compose.foundation.lazy.LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 360.dp)) {
                    items(state.lines.takeLast(200)) { line ->
                        Text(line, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace))
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showLog = false }) { Text("Close") } },
        )
    }

    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).imePadding()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onOpenDrawer) { Icon(Icons.Default.Menu, contentDescription = "Menu") }
            Text("Terminal", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Box {
                IconButton(onClick = { showMenu = true }) { Icon(Icons.Default.MoreVert, contentDescription = "More") }
                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                    DropdownMenuItem(text = { Text(if (state.running) "Stop" else "Start") }, onClick = { showMenu = false; if (state.running) vm.stop() else vm.start() })
                    DropdownMenuItem(text = { Text("Bootstrap") }, onClick = { showMenu = false; vm.bootstrap() })
                    DropdownMenuItem(text = { Text("Install") }, onClick = { showMenu = false; if (bootstrapUrl.isBlank()) bootstrapUrl = vm.currentBootstrapUrl(); showInstall = true })
                    DropdownMenuItem(text = { Text("Clear") }, onClick = { showMenu = false; vm.clear() })
                    DropdownMenuItem(text = { Text("System log") }, onClick = { showMenu = false; showLog = true })
                }
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
