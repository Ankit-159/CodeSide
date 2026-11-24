package com.codeside.app

import android.app.Application
import com.codeside.app.data.store.SettingsStore
import com.codeside.app.terminal.TerminalManager

class CodeSideApplication : Application() {
    lateinit var settingsStore: SettingsStore
        private set
    lateinit var terminalManager: TerminalManager
        private set

    override fun onCreate() {
        super.onCreate()
        settingsStore = SettingsStore(this)
        terminalManager = TerminalManager(this)
    }
}
