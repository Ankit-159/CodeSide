package com.codeside.app

import android.app.Application
import com.codeside.app.data.store.SettingsStore
import com.codeside.app.terminal.TerminalManager
import com.codeside.app.util.AppLog

class CodeSideApplication : Application() {
    lateinit var settingsStore: SettingsStore
        private set
    lateinit var terminalManager: TerminalManager
        private set

    override fun onCreate() {
        super.onCreate()
        AppLog.init(this)
        AppLog.log("App", "onCreate")
        settingsStore = SettingsStore(this)
        terminalManager = TerminalManager(this)
    }
}
