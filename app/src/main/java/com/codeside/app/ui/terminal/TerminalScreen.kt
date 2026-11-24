package com.codeside.app.ui.terminal

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun TerminalScreen(vm: TerminalViewModel = viewModel()) {
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
                    TextButton(onClick = {
                        vm.installBootstrap(bootstrapUrl)
                        showInstall = false
                    }) { Text("ZIP") }
                    TextButton(onClick = {
                        vm.installDeb(bootstrapUrl)
                        showInstall = false
                    }) { Text("DEB") }
                    TextButton(onClick = {
                        vm.installTermuxPackage(bootstrapUrl)
                        showInstall = false
                    }) { Text("Repo") }
                }
            },
            dismissButton = {
                TextButton(onClick = { showInstall = false }) { Text("Cancel") }
            },
        )
    }

    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Row(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
            Text("Terminal", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            TextButton(onClick = { showInstall = true }) { Text("Zip") }
            TextButton(onClick = { vm.bootstrap() }) { Text("Bootstrap") }
            TextButton(onClick = { vm.clear() }) { Text("Clear") }
            TextButton(onClick = { if (state.running) vm.stop() else vm.start() }) {
                Text(if (state.running) "Stop" else "Start")
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
