package com.codeside.app

import android.app.Application
import com.codeside.app.data.store.SettingsStore

class CodeSideApplication : Application() {
    lateinit var settingsStore: SettingsStore
        private set

    override fun onCreate() {
        super.onCreate()
        settingsStore = SettingsStore(this)
    }
}
