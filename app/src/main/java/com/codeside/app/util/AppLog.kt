package com.codeside.app.util

import android.content.Context
import android.os.Build
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

object AppLog {
    private val initialized = AtomicBoolean(false)
    private lateinit var logFile: File
    private lateinit var crashFile: File
    private val lock = Any()
    private val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)

    fun init(context: Context) {
        if (!initialized.compareAndSet(false, true)) return
        val dir = context.getExternalFilesDir(null) ?: context.filesDir
        dir.mkdirs()
        logFile = File(dir, "codeside-log.txt")
        crashFile = File(dir, "codeside-crash.txt")
        log("AppLog", "init sdk=${Build.VERSION.SDK_INT} abi=${Build.SUPPORTED_ABIS.joinToString()} device=${Build.MANUFACTURER} ${Build.MODEL}")
        installCrashHandler()
    }

    fun log(tag: String, msg: String) {
        val line = "${fmt.format(Date())} [$tag] $msg\n"
        synchronized(lock) {
            runCatching {
                if (::logFile.isInitialized) logFile.appendText(line)
            }
        }
    }

    fun logThrowable(tag: String, msg: String, t: Throwable) {
        val sw = StringWriter()
        t.printStackTrace(PrintWriter(sw))
        log(tag, "$msg\n$sw")
    }

    fun getLogText(): String = synchronized(lock) {
        runCatching { if (::logFile.isInitialized && logFile.exists()) logFile.readText() else "" }.getOrDefault("")
    }

    fun getLogFilePath(): String = if (::logFile.isInitialized) logFile.absolutePath else ""

    fun hasCrash(): Boolean = ::crashFile.isInitialized && crashFile.exists() && crashFile.length() > 0

    fun readCrash(): String = synchronized(lock) {
        runCatching { if (hasCrash()) crashFile.readText() else "" }.getOrDefault("")
    }

    fun clearCrash() {
        synchronized(lock) {
            runCatching { if (::crashFile.isInitialized && crashFile.exists()) crashFile.delete() }
        }
    }

    private fun installCrashHandler() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            val sw = StringWriter()
            throwable.printStackTrace(PrintWriter(sw))
            val text = buildString {
                append("Time: ${fmt.format(Date())}\n")
                append("Thread: ${thread.name}\n")
                append("Device: ${Build.MANUFACTURER} ${Build.MODEL} SDK=${Build.VERSION.SDK_INT}\n")
                append("ABI: ${Build.SUPPORTED_ABIS.joinToString()}\n\n")
                append(sw.toString())
            }
            synchronized(lock) {
                runCatching {
                    if (::crashFile.isInitialized) crashFile.writeText(text)
                    if (::logFile.isInitialized) logFile.appendText("${fmt.format(Date())} [CRASH] ${throwable.message}\n$sw\n")
                }
            }
            previous?.uncaughtException(thread, throwable)
        }
    }
}
