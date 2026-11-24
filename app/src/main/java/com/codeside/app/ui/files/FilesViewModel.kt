package com.codeside.app.ui.files

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.codeside.app.agent.Workspace
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

data class FilesUiState(
    val files: List<String> = emptyList(),
    val status: String? = null,
)

class FilesViewModel(app: Application) : AndroidViewModel(app) {
    private val workspace = Workspace(File(app.applicationContext.filesDir, "workspace"))
    private val _uiState = MutableStateFlow(FilesUiState())
    val uiState: StateFlow<FilesUiState> = _uiState.asStateFlow()

    init { refresh() }

    fun refresh() {
        viewModelScope.launch(Dispatchers.IO) {
            val root = workspace.root
            root.mkdirs()
            val files = root.walkTopDown()
                .filter { it.isFile }
                .map { workspace.relative(it) }
                .sorted()
                .toList()
            _uiState.value = FilesUiState(files = files)
        }
    }

    fun importUri(uri: Uri, name: String?) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val resolver = getApplication<Application>().contentResolver
                val targetName = (name?.takeIf { it.isNotBlank() } ?: "import-${System.currentTimeMillis()}").replace("/", "_")
                val target = workspace.resolve(targetName)
                resolver.openInputStream(uri).use { input ->
                    requireNotNull(input) { "Could not open selected file" }
                    target.outputStream().use { out -> input.copyTo(out) }
                }
                refresh()
                _uiState.value = _uiState.value.copy(status = "Imported $targetName")
            }.onFailure {
                _uiState.value = _uiState.value.copy(status = "Import failed: ${it.message}")
            }
        }
    }

    fun clearStatus() { _uiState.value = _uiState.value.copy(status = null) }
}
