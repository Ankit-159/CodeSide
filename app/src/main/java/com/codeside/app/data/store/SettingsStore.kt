package com.codeside.app.data.store

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.codeside.app.data.model.ProviderProfile
import com.codeside.app.data.model.ProviderType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class SettingsState(
    val profiles: List<ProviderProfile> = emptyList(),
    val activeProfileId: String? = null,
    val bootstrapUrl: String = "",
)

class SettingsStore(context: Context) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; prettyPrint = true }
    private val prefs: SharedPreferences = createPrefs(context.applicationContext)
    private val _state = MutableStateFlow(load())
    val state: StateFlow<SettingsState> = _state.asStateFlow()

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

    private fun load(): SettingsState {
        val raw = prefs.getString(KEY_STATE, null) ?: return defaultState()
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

    fun replaceAll(state: SettingsState) {
        persist(state)
    }

    private fun persist(state: SettingsState) {
        _state.value = state
        prefs.edit().putString(KEY_STATE, json.encodeToString(state)).apply()
    }

    companion object {
        private const val KEY_STATE = "settings_state"
    }
}
