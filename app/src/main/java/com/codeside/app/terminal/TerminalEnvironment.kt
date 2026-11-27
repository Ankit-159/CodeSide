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
        listOf(prefixDir, homeDir, tmpDir, binDir, File(prefixDir, "lib"), File(prefixDir, "etc"), File(prefixDir, "etc/profile.d")).forEach {
            it.mkdirs()
        }
        val envFile = File(homeDir, ".codeside_env")
        runCatching { envFile.writeText("export PS1='\\W \\$ '\n") }
        runCatching {
            File(prefixDir, "etc/profile.d/codeside-prompt.sh").writeText("export PS1='\\W \\$ '\n")
        }
        runCatching {
            val bashrc = File(homeDir, ".bashrc")
            if (!bashrc.exists()) bashrc.writeText("export PS1='\\W \\$ '\n")
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
        val ldPreload = File(nativeLibDir, "libcodeside-exec.so")
        val envFile = File(homeDir, ".codeside_env")
        val env = mutableListOf(
            "HOME=${homeDir.absolutePath}",
            "PREFIX=${prefixDir.absolutePath}",
            "TMPDIR=${tmpDir.absolutePath}",
            "PATH=${pathParts.joinToString(":")}",
            "LD_LIBRARY_PATH=${File(prefixDir, "lib").absolutePath}",
            "LANG=en_US.UTF-8",
            "TERM=xterm-256color",
            "COLORTERM=truecolor",
            "ENV=${envFile.absolutePath}",
            "PS1=\\W \\$ ",
            "ANDROID_ROOT=/system",
            "ANDROID_DATA=/data",
            "CODESIDE_NATIVE_LIB_DIR=$nativeLibDir",
            "CODESIDE_APP_ID=${context.packageName}",
            "CODESIDE_ANDROID_SDK=${Build.VERSION.SDK_INT}",
        )
        if (ldPreload.exists()) {
            env += "LD_PRELOAD=${ldPreload.absolutePath}"
        }
        return env.toTypedArray()
    }

    fun bootstrapStatus(): String {
        return when {
            File(binDir, "sh").canExecute() -> "prefix shell installed"
            File(binDir, "busybox").canExecute() -> "busybox installed"
            else -> "system shell fallback (/system/bin/sh)"
        }
    }
}
