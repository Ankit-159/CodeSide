package com.codeside.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.codeside.app.data.model.ProviderProfile
import com.codeside.app.data.model.ProviderType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: SettingsViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<ProviderProfile?>(null) }
    var bootstrapUrl by remember(state.bootstrapUrl) { mutableStateOf(state.bootstrapUrl) }

    Scaffold(topBar = { TopAppBar(title = { Text("Settings") }) }) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text("API Profiles", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(onClick = { vm.addPreset(ProviderType.OPENAI) }) { Text("OpenAI") }
                    OutlinedButton(onClick = { vm.addPreset(ProviderType.ANTHROPIC) }) { Text("Anthropic") }
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(onClick = { vm.addPreset(ProviderType.GOOGLE) }) { Text("Google") }
                    OutlinedButton(onClick = { vm.addPreset(ProviderType.CUSTOM) }) { Text("Custom") }
                }
            }

            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Terminal bootstrap", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Default download link for bootstrap ZIP (you can change it).",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        OutlinedTextField(
                            value = bootstrapUrl,
                            onValueChange = { bootstrapUrl = it },
                            label = { Text("Bootstrap ZIP URL") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                        )
                        Button(onClick = { vm.setBootstrapUrl(bootstrapUrl) }) { Text("Save URL") }
                    }
                }
            }

            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Allowed tools", style = MaterialTheme.typography.titleMedium)
                        if (state.allowedTools.isEmpty()) {
                            Text("No always-allowed tools.", style = MaterialTheme.typography.bodySmall)
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
                        Text("${profile.type} · ${profile.model}", style = MaterialTheme.typography.bodySmall)
                        Text(profile.baseUrl, style = MaterialTheme.typography.bodySmall)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = { vm.setActive(profile.id) }) { Text(if (active) "Active" else "Set active") }
                            TextButton(onClick = { editing = profile }) { Text("Edit") }
                            TextButton(onClick = { vm.delete(profile.id) }) { Text("Delete") }
                        }
                    }
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProfileEditor(
    initial: ProviderProfile,
    onCancel: () -> Unit,
    onSave: (ProviderProfile) -> Unit,
) {
    var profile by remember(initial.id) { mutableStateOf(initial) }
    var typeExpanded by remember { mutableStateOf(false) }

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
            OutlinedTextField(value = profile.model, onValueChange = { profile = profile.copy(model = it) }, label = { Text("Model") }, modifier = Modifier.fillMaxWidth())
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
