package com.codeside.app.data.store

import android.content.Context
import com.codeside.app.data.model.ChatMessage
import com.codeside.app.data.model.ChatSession
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

@Serializable
data class ChatSessionMeta(
    val id: String,
    val title: String,
    val createdAt: Long,
    val updatedAt: Long,
    val messageCount: Int = 0,
    val preview: String = "",
)

class ChatStore(context: Context) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val root = File(context.filesDir, "chat_sessions").apply { mkdirs() }
    private val indexFile = File(root, "index.json")
    private val legacyFile = File(context.filesDir, "chat_history.json")

    fun loadSessions(): List<ChatSessionMeta> {
        migrateLegacyIfNeeded()
        val metas = readIndex().sortedByDescending { it.updatedAt }
        if (metas.isNotEmpty()) return metas
        val created = createSession()
        return listOf(created.toMeta())
    }

    fun loadSession(id: String): ChatSession? {
        val file = sessionFile(id)
        if (!file.exists()) return null
        return runCatching { json.decodeFromString<ChatSession>(file.readText()) }.getOrNull()
    }

    fun createSession(title: String = "New chat"): ChatSession {
        val now = System.currentTimeMillis()
        val session = ChatSession(
            id = UUID.randomUUID().toString(),
            title = title,
            createdAt = now,
            updatedAt = now,
            messages = emptyList(),
        )
        saveSession(session)
        return session
    }

    fun saveSession(session: ChatSession) {
        val clean = session.copy(messages = session.messages.filter { !it.isStreaming })
        runCatching { sessionFile(clean.id).writeText(json.encodeToString(clean)) }
        upsertIndex(clean.toMeta())
    }

    fun deleteSession(id: String) {
        runCatching { sessionFile(id).delete() }
        val next = readIndex().filterNot { it.id == id }
        writeIndex(next)
    }

    fun renameSession(id: String, title: String) {
        val session = loadSession(id) ?: return
        saveSession(session.copy(title = title.ifBlank { "New chat" }, updatedAt = System.currentTimeMillis()))
    }

    fun load(): List<ChatMessage> {
        val sessions = loadSessions()
        val first = sessions.firstOrNull() ?: return emptyList()
        return loadSession(first.id)?.messages.orEmpty()
    }

    fun save(messages: List<ChatMessage>) {
        val sessions = loadSessions()
        val id = sessions.firstOrNull()?.id ?: createSession().id
        val existing = loadSession(id)
        val title = existing?.title?.takeIf { it != "New chat" } ?: deriveTitle(messages)
        val now = System.currentTimeMillis()
        saveSession(
            ChatSession(
                id = id,
                title = title,
                createdAt = existing?.createdAt ?: now,
                updatedAt = now,
                messages = messages,
            )
        )
    }

    fun clear() {
        val sessions = loadSessions()
        val id = sessions.firstOrNull()?.id ?: return
        deleteSession(id)
        if (loadSessions().isEmpty()) createSession()
    }

    private fun sessionFile(id: String): File = File(root, "$id.json")

    private fun readIndex(): List<ChatSessionMeta> {
        if (!indexFile.exists()) return rebuildIndex()
        return runCatching { json.decodeFromString<List<ChatSessionMeta>>(indexFile.readText()) }
            .getOrElse { rebuildIndex() }
    }

    private fun writeIndex(metas: List<ChatSessionMeta>) {
        runCatching { indexFile.writeText(json.encodeToString(metas.sortedByDescending { it.updatedAt })) }
    }

    private fun upsertIndex(meta: ChatSessionMeta) {
        val next = readIndex().filterNot { it.id == meta.id } + meta
        writeIndex(next)
    }

    private fun rebuildIndex(): List<ChatSessionMeta> {
        val metas = root.listFiles()
            ?.filter { it.isFile && it.extension == "json" && it.name != "index.json" }
            ?.mapNotNull { f -> runCatching { json.decodeFromString<ChatSession>(f.readText()).toMeta() }.getOrNull() }
            ?.sortedByDescending { it.updatedAt }
            .orEmpty()
        if (metas.isNotEmpty()) writeIndex(metas)
        return metas
    }

    private fun migrateLegacyIfNeeded() {
        if (!legacyFile.exists()) return
        val existing = readIndex()
        if (existing.isNotEmpty()) {
            runCatching { legacyFile.delete() }
            return
        }
        val messages = runCatching { json.decodeFromString<List<ChatMessage>>(legacyFile.readText()) }.getOrElse { emptyList() }
        if (messages.isNotEmpty()) {
            val now = System.currentTimeMillis()
            saveSession(
                ChatSession(
                    id = UUID.randomUUID().toString(),
                    title = deriveTitle(messages),
                    createdAt = now,
                    updatedAt = now,
                    messages = messages,
                )
            )
        }
        runCatching { legacyFile.delete() }
    }

    private fun deriveTitle(messages: List<ChatMessage>): String {
        val firstUser = messages.firstOrNull { it.role.name == "USER" }?.content?.trim().orEmpty()
        if (firstUser.isBlank()) return "New chat"
        return firstUser.replace("\n", " ").take(48).ifBlank { "New chat" }
    }

    private fun ChatSession.toMeta(): ChatSessionMeta {
        val preview = messages.lastOrNull { it.content.isNotBlank() }?.content?.replace("\n", " ")?.take(80).orEmpty()
        return ChatSessionMeta(
            id = id,
            title = title,
            createdAt = createdAt,
            updatedAt = updatedAt,
            messageCount = messages.count { !it.isStreaming },
            preview = preview,
        )
    }
}
