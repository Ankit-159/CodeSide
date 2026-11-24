package com.codeside.app.terminal

import android.content.Context
import android.os.Build
import java.io.File

class TerminalEnvironment(private val context: Context) {
    val filesDir: File = context.filesDir
    val prefixDir: File = File(filesDir, "usr")
    val homeDir: File = File(filesDir, "home")
    val tmpDir: File = File(filesDir, "tmp")
    val binDir: File = File(prefixDir, "bin")

    fun ensureDirectories() {
        listOf(prefixDir, homeDir, tmpDir, binDir, File(prefixDir, "lib"), File(prefixDir, "etc")).forEach {
            it.mkdirs()
        }
    }

    fun isBootstrapInstalled(): Boolean {
        val sh = File(binDir, "sh")
        val busybox = File(binDir, "busybox")
        return sh.canExecute() || busybox.canExecute() || File("/system/bin/sh").canExecute()
    }

    fun shellPath(): String {
        val candidates = listOf(
            File(binDir, "sh"),
            File(binDir, "busybox"),
            File("/system/bin/sh"),
        )
        return candidates.firstOrNull { it.exists() }?.absolutePath ?: "/system/bin/sh"
    }

    fun buildEnv(): Array<String> {
        val pathParts = mutableListOf<String>()
        if (binDir.exists()) pathParts += binDir.absolutePath
        pathParts += "/system/bin"
        pathParts += "/system/xbin"
        val nativeLibDir = context.applicationInfo.nativeLibraryDir
        return arrayOf(
            "HOME=${homeDir.absolutePath}",
            "PREFIX=${prefixDir.absolutePath}",
            "TMPDIR=${tmpDir.absolutePath}",
            "PATH=${pathParts.joinToString(":")}",
            "LANG=en_US.UTF-8",
            "TERM=xterm-256color",
            "ANDROID_ROOT=/system",
            "ANDROID_DATA=/data",
            "CODESIDE_NATIVE_LIB_DIR=$nativeLibDir",
            "CODESIDE_APP_ID=${context.packageName}",
            "CODESIDE_ANDROID_SDK=${Build.VERSION.SDK_INT}",
        )
    }

    fun bootstrapStatus(): String {
        return when {
            File(binDir, "sh").canExecute() -> "prefix shell installed"
            File(binDir, "busybox").canExecute() -> "busybox installed"
            else -> "system shell fallback (/system/bin/sh)"
        }
    }
}
