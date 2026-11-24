package com.codeside.app.agent

import android.content.Context
import com.codeside.app.terminal.TerminalEnvironment
import java.io.File
import java.util.concurrent.TimeUnit

data class ToolResult(val ok: Boolean, val output: String)

class AgentTools(context: Context) {
    private val appContext = context.applicationContext
    private val env = TerminalEnvironment(appContext)
    val workspace = Workspace(File(appContext.filesDir, "workspace"))

    fun listDir(path: String = "."): ToolResult = runCatching {
        val dir = workspace.resolve(path)
        if (!dir.exists()) return ToolResult(false, "Not found: $path")
        if (!dir.isDirectory) return ToolResult(false, "Not a directory: $path")
        val items = dir.listFiles()?.sortedBy { it.name }?.joinToString("\n") {
            val suffix = if (it.isDirectory) "/" else ""
            "${it.name}$suffix"
        }.orEmpty()
        ToolResult(true, items.ifBlank { "(empty)" })
    }.getOrElse { ToolResult(false, it.message ?: "list_dir failed") }

    fun readFile(path: String): ToolResult = runCatching {
        val file = workspace.resolve(path)
        if (!file.exists() || !file.isFile) return ToolResult(false, "File not found: $path")
        val text = file.readText()
        val limit = 200_000
        if (text.length > limit) ToolResult(true, text.take(limit) + "\n...[truncated]") else ToolResult(true, text)
    }.getOrElse { ToolResult(false, it.message ?: "read_file failed") }

    fun writeFile(path: String, content: String): ToolResult = runCatching {
        val file = workspace.resolve(path)
        file.parentFile?.mkdirs()
        file.writeText(content)
        ToolResult(true, "Wrote ${workspace.relative(file)}")
    }.getOrElse { ToolResult(false, it.message ?: "write_file failed") }

    fun editFile(path: String, oldString: String, newString: String): ToolResult = runCatching {
        val file = workspace.resolve(path)
        if (!file.exists() || !file.isFile) return ToolResult(false, "File not found: $path")
        val text = file.readText()
        val idx = text.indexOf(oldString)
        if (idx < 0) return ToolResult(false, "oldString not found in $path")
        val updated = text.replaceFirst(oldString, newString)
        file.writeText(updated)
        ToolResult(true, "Edited ${workspace.relative(file)}")
    }.getOrElse { ToolResult(false, it.message ?: "edit_file failed") }

    fun search(query: String, path: String = "."): ToolResult = runCatching {
        val root = workspace.resolve(path)
        if (!root.exists()) return ToolResult(false, "Not found: $path")
        val q = query.lowercase()
        val hits = mutableListOf<String>()
        root.walkTopDown().take(2000).forEach { f ->
            if (hits.size >= 100) return@forEach
            if (f.isFile) {
                val rel = workspace.relative(f)
                val text = runCatching { f.readText() }.getOrNull() ?: return@forEach
                text.lineSequence().forEachIndexed { i, line ->
                    if (hits.size >= 100) return@forEachIndexed
                    if (line.lowercase().contains(q)) hits += "$rel:${i + 1}: ${line.trim().take(200)}"
                }
            }
        }
        ToolResult(true, hits.joinToString("\n").ifBlank { "No matches" })
    }.getOrElse { ToolResult(false, it.message ?: "search failed") }

    fun runCommand(command: String, timeoutSeconds: Long = 30): ToolResult = runCatching {
        env.ensureDirectories()
        val pb = ProcessBuilder(env.shellPath(), "-c", command)
        pb.directory(env.homeDir)
        pb.redirectErrorStream(true)
        val envMap = pb.environment()
        env.buildEnv().forEach { entry ->
            val idx = entry.indexOf('=')
            if (idx > 0) envMap[entry.substring(0, idx)] = entry.substring(idx + 1)
        }
        val proc = pb.start()
        val finished = proc.waitFor(timeoutSeconds, TimeUnit.SECONDS)
        if (!finished) {
            proc.destroyForcibly()
            return ToolResult(false, "Command timed out after ${timeoutSeconds}s")
        }
        val out = proc.inputStream.bufferedReader().readText()
        val code = proc.exitValue()
        val text = if (out.isBlank()) "(no output)" else out.take(200_000)
        if (code == 0) ToolResult(true, text) else ToolResult(false, "exit $code\n$text")
    }.getOrElse { ToolResult(false, it.message ?: "run_command failed") }
}
