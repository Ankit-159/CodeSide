package com.codeside.app.terminal

import android.content.Context
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.zip.ZipInputStream

class BootstrapManager(private val context: Context) {
    private val env = TerminalEnvironment(context)
    private val http = OkHttpClient()

    suspend fun ensureBasicBootstrap(): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            env.ensureDirectories()
            val prefixBin = env.binDir
            val shTarget = File(prefixBin, "sh")
            if (!shTarget.exists()) {
                val systemSh = File("/system/bin/sh")
                if (systemSh.exists()) {
                    systemSh.copyTo(shTarget, overwrite = true)
                    shTarget.setExecutable(true, false)
                    shTarget.setReadable(true, false)
                } else {
                    throw IOException("/system/bin/sh not found")
                }
            }

            val systemTools = listOf("ls", "cat", "echo", "pwd", "id", "ps", "grep", "sed", "awk", "cp", "mv", "rm", "mkdir", "chmod", "ln", "uname", "env")
            for (tool in systemTools) {
                val target = File(prefixBin, tool)
                if (target.exists()) continue
                val src = File("/system/bin/$tool")
                if (src.exists()) {
                    try {
                        android.system.Os.symlink(src.absolutePath, target.absolutePath)
                    } catch (_: Throwable) {
                        try {
                            src.copyTo(target, overwrite = false)
                            target.setExecutable(true, false)
                        } catch (_: Throwable) {
                        }
                    }
                }
            }

            "Bootstrap ready: ${env.bootstrapStatus()}"
        }
    }

    suspend fun installBootstrapZip(url: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            env.ensureDirectories()
            val tmpZip = File(context.cacheDir, "bootstrap.zip")
            downloadTo(url, tmpZip)
            extractZip(tmpZip, env.prefixDir)
            applySymlinksFile(env.prefixDir)
            makeBinExecutable(env.binDir)
            tmpZip.delete()
            "Bootstrap installed from $url"
        }
    }

    private fun downloadTo(url: String, out: File) {
        val req = Request.Builder().url(url).build()
        http.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("HTTP ${resp.code} downloading bootstrap")
            val body = resp.body ?: throw IOException("Empty bootstrap response")
            FileOutputStream(out).use { fos ->
                body.byteStream().copyTo(fos)
            }
        }
    }

    private fun extractZip(zip: File, destDir: File) {
        ZipInputStream(zip.inputStream().buffered()).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                val outFile = safeResolve(destDir, entry.name)
                if (entry.isDirectory) {
                    outFile.mkdirs()
                } else {
                    outFile.parentFile?.mkdirs()
                    FileOutputStream(outFile).use { fos -> zis.copyTo(fos) }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
    }

    private fun applySymlinksFile(prefix: File) {
        val symlinks = File(prefix, "SYMLINKS.txt")
        if (!symlinks.exists()) return
        symlinks.readLines().forEach { line ->
            val trimmed = line.trim()
            if (trimmed.isEmpty()) return@forEach
            val parts = when {
                trimmed.contains("←") -> trimmed.split("←", limit = 2)
                trimmed.contains("\t") -> trimmed.split("\t", limit = 2)
                else -> trimmed.split(Regex("\\s+"), limit = 2)
            }
            if (parts.size != 2) return@forEach
            val left = parts[0].trim()
            val right = parts[1].trim()
            if (left.isEmpty() || right.isEmpty()) return@forEach
            // Termux SYMLINKS.txt is usually: path←target
            val linkPath = safeResolve(prefix, left)
            val target = right
            try { if (linkPath.exists()) linkPath.delete() } catch (_: Throwable) {}
            linkPath.parentFile?.mkdirs()
            try {
                android.system.Os.symlink(target, linkPath.absolutePath)
            } catch (_: Throwable) {
            }
        }
        symlinks.delete()
    }

    private fun makeBinExecutable(bin: File) {
        if (!bin.exists()) return
        bin.listFiles()?.forEach { f ->
            if (f.isFile) f.setExecutable(true, false)
        }
    }

    private fun safeResolve(base: File, name: String): File {
        val target = File(base, name).canonicalFile
        val baseCanon = base.canonicalFile
        if (!target.path.startsWith(baseCanon.path)) {
            throw IOException("Unsafe zip entry: $name")
        }
        return target
    }

    companion object {
        fun defaultBootstrapUrl(): String {
            val abi = Build.SUPPORTED_ABIS.firstOrNull() ?: Build.CPU_ABI ?: "armeabi-v7a"
            val arch = when {
                abi.contains("arm64") || abi.contains("aarch64") -> "aarch64"
                abi.contains("armeabi") || abi == "arm" -> "arm"
                abi.contains("x86_64") -> "x86_64"
                abi.contains("x86") || abi.contains("i686") -> "i686"
                else -> "arm"
            }
            return "https://github.com/termux/termux-packages/releases/latest/download/bootstrap-$arch.zip"
        }
    }
}
