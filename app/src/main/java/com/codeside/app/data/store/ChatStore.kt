package com.codeside.app.data.store

import android.content.Context
import com.codeside.app.data.model.ChatMessage
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

class ChatStore(context: Context) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val file = File(context.filesDir, "chat_history.json")

    fun load(): List<ChatMessage> {
        if (!file.exists()) return emptyList()
        return runCatching { json.decodeFromString<List<ChatMessage>>(file.readText()) }.getOrElse { emptyList() }
    }

    fun save(messages: List<ChatMessage>) {
        runCatching {
            file.writeText(json.encodeToString(messages.filter { !it.isStreaming }))
        }
    }

    fun clear() {
        runCatching { file.delete() }
    }
}
