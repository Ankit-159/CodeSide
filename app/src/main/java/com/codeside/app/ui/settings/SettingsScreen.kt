package com.codeside.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import com.codeside.app.data.model.ProviderProfile
import com.codeside.app.data.model.ProviderType
import com.codeside.app.terminal.BootstrapManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onOpenDrawer: () -> Unit = {}, vm: SettingsViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<ProviderProfile?>(null) }
    val defaultBootstrapUrl = remember { BootstrapManager.defaultBootstrapUrl() }
    var bootstrapUrl by remember(state.bootstrapUrl, defaultBootstrapUrl) {
        mutableStateOf(state.bootstrapUrl.ifBlank { defaultBootstrapUrl })
    }
    var bootstrapSaveStatus by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onOpenDrawer) {
                        Icon(Icons.Default.Menu, contentDescription = "Menu")
                    }
                },
                windowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
            )
        },
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { Spacer(Modifier.height(4.dp)) }

            item {
                SectionCard("API Profiles") {
                    Text("Add a provider preset, then edit endpoint/model/key as needed.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(onClick = { vm.addPreset(ProviderType.OPENAI) }, modifier = Modifier.weight(1f)) { Text("OpenAI") }
                        OutlinedButton(onClick = { vm.addPreset(ProviderType.ANTHROPIC) }, modifier = Modifier.weight(1f)) { Text("Anthropic") }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(onClick = { vm.addPreset(ProviderType.GOOGLE) }, modifier = Modifier.weight(1f)) { Text("Google") }
                        OutlinedButton(onClick = { vm.addPreset(ProviderType.CUSTOM) }, modifier = Modifier.weight(1f)) { Text("Custom") }
                    }
                }
            }

            items(state.profiles, key = { it.id }) { profile ->
                val active = state.activeProfileId == profile.id
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                        else MaterialTheme.colorScheme.surfaceVariant
                    ),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(profile.name.ifBlank { profile.type.name }, style = MaterialTheme.typography.titleSmall)
                        Text("${profile.type} · ${profile.model}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(profile.baseUrl, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = { vm.setActive(profile.id) }) { Text(if (active) "Active" else "Set active") }
                            TextButton(onClick = { editing = profile }) { Text("Edit") }
                            TextButton(onClick = { vm.delete(profile.id) }) { Text("Delete") }
                        }
                    }
                }
            }

            item {
                SectionCard("Terminal Bootstrap") {
                    Text("Default bootstrap ZIP URL. Used when installing bootstrap from the terminal menu.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedTextField(
                        value = bootstrapUrl,
                        onValueChange = { bootstrapUrl = it; bootstrapSaveStatus = null },
                        label = { Text("Bootstrap ZIP URL") },
                        placeholder = { Text(defaultBootstrapUrl) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { vm.setBootstrapUrl(bootstrapUrl); bootstrapSaveStatus = "Saved" }) { Text("Save URL") }
                        OutlinedButton(onClick = { bootstrapUrl = defaultBootstrapUrl; bootstrapSaveStatus = null }) { Text("Use default") }
                    }
                    if (bootstrapSaveStatus != null) {
                        Text(bootstrapSaveStatus!!, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }

            item {
                SectionCard("Performance") {
                    Text("Auto-detected defaults are applied for your Android version. Reduce motion helps on older/slower devices.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(Modifier.weight(1f)) {
                            Text("Reduce motion", style = MaterialTheme.typography.bodyMedium)
                            Text("Disable tab transitions and heavy animations", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked = state.reduceMotion, onCheckedChange = vm::setReduceMotion)
                    }
                }
            }

            item {
                val clipboard = LocalClipboardManager.current
                SectionCard("Diagnostics") {
                    Text("Log file is saved in app storage. Use Copy log to share crashes/issues.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(com.codeside.app.util.AppLog.getLogFilePath(), style = MaterialTheme.typography.bodySmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { clipboard.setText(AnnotatedString(com.codeside.app.util.AppLog.getLogText().take(20000))) }) { Text("Copy log") }
                        OutlinedButton(onClick = { com.codeside.app.util.AppLog.clearCrash() }) { Text("Clear crash") }
                    }
                }
            }

            item {
                SectionCard("Allowed Tools") {
                    Text("Tools you marked “Always allow” for agent actions.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (state.allowedTools.isEmpty()) {
                        Text("No always-allowed tools.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        state.allowedTools.forEach { tool ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(tool, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                                TextButton(onClick = { vm.removeAllowedTool(tool) }) { Text("Remove") }
                            }
                        }
                    }
                }
            }

            item {
                val context = LocalContext.current
                val version = runCatching { com.codeside.app.BuildConfig.VERSION_NAME }.getOrDefault("unknown")
                val installedOn = runCatching {
                    val pm = context.packageManager
                    val info = pm.getPackageInfo(context.packageName, 0)
                    java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.US).format(java.util.Date(info.firstInstallTime))
                }.getOrDefault("unknown")
                SectionCard("About") {
                    Text("CodeSide — Your AI. Your Code. Your Space.", style = MaterialTheme.typography.bodyMedium)
                    Text("Version: $version", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Installed on: $installedOn", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("GitHub: https://github.com/Ankit-159/CodeSide", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                }
            }

            if (editing != null) {
                item {
                    ProfileEditor(
                        initial = editing!!,
                        onCancel = { editing = null },
                        onSave = {
                            vm.save(it)
                            editing = null
                        },
                    )
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProfileEditor(
    initial: ProviderProfile,
    onCancel: () -> Unit,
    onSave: (ProviderProfile) -> Unit,
) {
    var profile by remember(initial.id) { mutableStateOf(initial) }
    var typeExpanded by remember { mutableStateOf(false) }
    var models by remember(initial.id) { mutableStateOf<List<String>>(emptyList()) }
    var loadingModels by remember { mutableStateOf(false) }
    var modelsError by remember { mutableStateOf<String?>(null) }
    var modelsExpanded by remember { mutableStateOf(false) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Edit profile", style = MaterialTheme.typography.titleMedium)

            OutlinedTextField(value = profile.name, onValueChange = { profile = profile.copy(name = it) }, label = { Text("Name") }, modifier = Modifier.fillMaxWidth())

            Column {
                OutlinedButton(onClick = { typeExpanded = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("Type: ${profile.type.name}")
                }
                DropdownMenu(expanded = typeExpanded, onDismissRequest = { typeExpanded = false }) {
                    ProviderType.entries.forEach { type ->
                        DropdownMenuItem(
                            text = { Text(type.name) },
                            onClick = {
                                profile = profile.copy(type = type)
                                typeExpanded = false
                            },
                        )
                    }
                }
            }

            OutlinedTextField(value = profile.baseUrl, onValueChange = { profile = profile.copy(baseUrl = it) }, label = { Text("Base URL") }, modifier = Modifier.fillMaxWidth())
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                OutlinedTextField(value = profile.model, onValueChange = { profile = profile.copy(model = it) }, label = { Text("Model") }, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = {
                        loadingModels = true; modelsError = null
                        scope.launch {
                            val r = com.codeside.app.data.provider.ModelFetcher.fetchModels(profile)
                            loadingModels = false
                            r.onSuccess { models = it; if (it.isNotEmpty()) modelsExpanded = true }
                                .onFailure { modelsError = it.message }
                        }
                    }, enabled = !loadingModels) { Text(if (loadingModels) "Fetching..." else "Fetch models") }
                    if (models.isNotEmpty()) {
                        Box {
                            OutlinedButton(onClick = { modelsExpanded = true }) { Text("Pick (${models.size})") }
                            DropdownMenu(expanded = modelsExpanded, onDismissRequest = { modelsExpanded = false }) {
                                models.take(80).forEach { m ->
                                    DropdownMenuItem(text = { Text(m) }, onClick = { profile = profile.copy(model = m); modelsExpanded = false })
                                }
                            }
                        }
                    }
                }
                if (modelsError != null) Text(modelsError!!, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
            OutlinedTextField(value = profile.chatPath, onValueChange = { profile = profile.copy(chatPath = it) }, label = { Text("Chat path") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(
                value = profile.apiKey,
                onValueChange = { profile = profile.copy(apiKey = it) },
                label = { Text("API key") },
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(value = profile.authHeader, onValueChange = { profile = profile.copy(authHeader = it) }, label = { Text("Auth header") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = profile.authPrefix, onValueChange = { profile = profile.copy(authPrefix = it) }, label = { Text("Auth prefix") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = profile.extraHeadersJson, onValueChange = { profile = profile.copy(extraHeadersJson = it) }, label = { Text("Extra headers JSON") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
            OutlinedTextField(value = profile.extraBodyJson, onValueChange = { profile = profile.copy(extraBodyJson = it) }, label = { Text("Extra body JSON") }, modifier = Modifier.fillMaxWidth(), minLines = 2)

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { onSave(profile) }) { Text("Save") }
                OutlinedButton(onClick = onCancel) { Text("Cancel") }
            }
        }
    }
}
