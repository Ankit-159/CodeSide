package com.codeside.app.data.store

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.codeside.app.data.model.ProviderProfile
import com.codeside.app.data.model.ProviderType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class SettingsState(
    val profiles: List<ProviderProfile> = emptyList(),
    val activeProfileId: String? = null,
    val bootstrapUrl: String = "",
    val allowedTools: List<String> = emptyList(),
    val grantedFolders: List<String> = emptyList(),
    val reduceMotion: Boolean = android.os.Build.VERSION.SDK_INT <= 23,
    val autoGenerateChatTitles: Boolean = true,
)

class SettingsStore(context: Context) {
    private val appContext = context.applicationContext
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; prettyPrint = true }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val writeMutex = Mutex()
    private val initialState = defaultState()
    private val _state = MutableStateFlow(initialState)
    val state: StateFlow<SettingsState> = _state.asStateFlow()

    @Volatile
    private var prefs: SharedPreferences? = null

    init {
        scope.launch {
            val p = createPrefs(appContext)
            val loaded = load(p)
            val toPersist: SettingsState? = synchronized(this@SettingsStore) {
                prefs = p
                if (_state.value == initialState) {
                    _state.value = loaded
                    null
                } else {
                    _state.value
                }
            }
            if (toPersist != null) {
                writeMutex.withLock {
                    p.edit().putString(KEY_STATE, json.encodeToString(_state.value)).apply()
                }
            }
        }
    }

    private fun createPrefs(context: Context): SharedPreferences {
        return try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            EncryptedSharedPreferences.create(
                context,
                "codeside_secure_settings",
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
        } catch (_: Throwable) {
            // API 21-22 fallback: app-private prefs. Keys are still inside app sandbox.
            context.getSharedPreferences("codeside_settings_fallback", Context.MODE_PRIVATE)
        }
    }

    private fun load(p: SharedPreferences): SettingsState {
        val raw = p.getString(KEY_STATE, null) ?: return defaultState()
        return try {
            val decoded = json.decodeFromString<SettingsState>(raw)
            if (decoded.profiles.isEmpty()) defaultState() else decoded
        } catch (_: Throwable) {
            defaultState()
        }
    }

    private fun defaultState(): SettingsState {
        val openAi = ProviderProfile.preset(ProviderType.OPENAI)
        return SettingsState(profiles = listOf(openAi), activeProfileId = openAi.id)
    }

    fun activeProfile(): ProviderProfile? {
        val s = _state.value
        return s.profiles.firstOrNull { it.id == s.activeProfileId } ?: s.profiles.firstOrNull()
    }

    fun saveProfile(profile: ProviderProfile) {
        val normalized = profile.normalized()
        val current = _state.value
        val existing = current.profiles.any { it.id == normalized.id }
        val profiles = if (existing) {
            current.profiles.map { if (it.id == normalized.id) normalized else it }
        } else {
            current.profiles + normalized
        }
        val activeId = current.activeProfileId ?: normalized.id
        persist(current.copy(profiles = profiles, activeProfileId = activeId))
    }

    fun deleteProfile(id: String) {
        val current = _state.value
        val profiles = current.profiles.filterNot { it.id == id }
        val activeId = if (current.activeProfileId == id) profiles.firstOrNull()?.id else current.activeProfileId
        persist(current.copy(profiles = profiles, activeProfileId = activeId))
    }

    fun setActiveProfile(id: String) {
        val current = _state.value
        if (current.profiles.any { it.id == id }) {
            persist(current.copy(activeProfileId = id))
        }
    }

    fun setBootstrapUrl(url: String) {
        persist(_state.value.copy(bootstrapUrl = url.trim()))
    }

    fun setReduceMotion(enabled: Boolean) {
        persist(_state.value.copy(reduceMotion = enabled))
    }

    fun setAutoGenerateChatTitles(enabled: Boolean) {
        persist(_state.value.copy(autoGenerateChatTitles = enabled))
    }

    fun allowTool(name: String) {
        val current = _state.value
        if (name.isBlank() || current.allowedTools.contains(name)) return
        persist(current.copy(allowedTools = current.allowedTools + name))
    }

    fun removeAllowedTool(name: String) {
        val current = _state.value
        persist(current.copy(allowedTools = current.allowedTools.filterNot { it == name }))
    }

    fun addGrantedFolder(uri: String) {
        val current = _state.value
        if (uri.isBlank() || current.grantedFolders.contains(uri)) return
        persist(current.copy(grantedFolders = current.grantedFolders + uri))
    }

    fun removeGrantedFolder(uri: String) {
        val current = _state.value
        persist(current.copy(grantedFolders = current.grantedFolders.filterNot { it == uri }))
    }

    fun replaceAll(state: SettingsState) {
        persist(state)
    }

    private fun persist(state: SettingsState) {
        _state.value = state
        val p = prefs ?: return
        scope.launch {
            writeMutex.withLock {
                p.edit().putString(KEY_STATE, json.encodeToString(_state.value)).apply()
            }
        }
    }

    companion object {
        private const val KEY_STATE = "settings_state"
    }
}
