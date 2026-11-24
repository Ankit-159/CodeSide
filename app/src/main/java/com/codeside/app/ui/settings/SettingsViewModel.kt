package com.codeside.app.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.codeside.app.CodeSideApplication
import com.codeside.app.data.model.ProviderProfile
import com.codeside.app.data.model.ProviderType
import com.codeside.app.data.store.SettingsState
import kotlinx.coroutines.flow.StateFlow

class SettingsViewModel(app: Application) : AndroidViewModel(app) {
    private val store = (app as CodeSideApplication).settingsStore
    val state: StateFlow<SettingsState> = store.state

    fun addPreset(type: ProviderType) {
        store.saveProfile(ProviderProfile.preset(type))
    }

    fun save(profile: ProviderProfile) {
        store.saveProfile(profile)
    }

    fun delete(id: String) {
        store.deleteProfile(id)
    }

    fun setActive(id: String) {
        store.setActiveProfile(id)
    }

    fun setBootstrapUrl(url: String) {
        store.setBootstrapUrl(url)
    }

    fun removeAllowedTool(name: String) {
        store.removeAllowedTool(name)
    }
}
