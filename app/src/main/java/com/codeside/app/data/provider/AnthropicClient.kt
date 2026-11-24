package com.codeside.app.data.provider

import com.codeside.app.data.model.ChatMessage
import com.codeside.app.data.model.ChatRole
import com.codeside.app.data.model.ProviderProfile
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException

class AnthropicClient(
    private val http: OkHttpClient = OpenAiCompatibleClient.defaultHttp(),
) : ProviderClient {
    private val json = Json { ignoreUnknownKeys = true }

    override fun streamChat(
        profile: ProviderProfile,
        messages: List<ChatMessage>,
        onEvent: (ProviderEvent) -> Unit,
    ): Cancellable {
        val base = profile.baseUrl.trimEnd('/')
        val path = profile.chatPath.ifBlank { "/v1/messages" }
        val url = if (path.startsWith("http")) path else base + (if (path.startsWith("/")) path else "/$path")

        val system = messages.filter { it.role == ChatRole.SYSTEM }.joinToString("\n\n") { it.content }.ifBlank { null }
        val body = buildJsonObject {
            put("model", profile.model)
            put("max_tokens", 4096)
            put("stream", true)
            system?.let { put("system", it) }
            put("tools", defaultTools())
            put("messages", buildJsonArray {
                messages.filter { it.role != ChatRole.SYSTEM }.forEach { msg ->
                    add(buildJsonObject {
                        when (msg.role) {
                            ChatRole.TOOL -> {
                                put("role", "user")
                                put("content", buildJsonArray {
                                    add(buildJsonObject {
                                        put("type", "tool_result")
                                        put("tool_use_id", msg.toolCallId ?: "")
                                        put("content", msg.content)
                                    })
                                })
                            }
                            ChatRole.ASSISTANT -> {
                                put("role", "assistant")
                                if (msg.toolCalls.isNotEmpty()) {
                                    put("content", buildJsonArray {
                                        if (msg.content.isNotBlank()) add(buildJsonObject { put("type", "text"); put("text", msg.content) })
                                        msg.toolCalls.forEach { tc ->
                                            add(buildJsonObject {
                                                put("type", "tool_use")
                                                put("id", tc.id)
                                                put("name", tc.name)
                                                put("input", runCatching { json.parseToJsonElement(tc.arguments) }.getOrElse { buildJsonObject {} })
                                            })
                                        }
                                    })
                                } else {
                                    put("content", msg.content)
                                }
                            }
                            else -> {
                                put("role", "user")
                                put("content", msg.content)
                            }
                        }
                    })
                }
            })
        }

        val req = Request.Builder()
            .url(url)
            .post(json.encodeToString(JsonObject.serializer(), body).toRequestBody("application/json".toMediaType()))
            .header("x-api-key", profile.apiKey)
            .header("anthropic-version", "2023-06-01")
            .header("accept", "text/event-stream")
            .build()

        val toolBuilders = mutableMapOf<Int, ToolBuilder>()
        val call = http.newCall(req)
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (!call.isCanceled()) onEvent(ProviderEvent.Error(e.message ?: "Network error", e))
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (!it.isSuccessful) {
                        onEvent(ProviderEvent.Error(it.body?.string()?.take(2000) ?: "HTTP ${it.code}"))
                        return
                    }
                    val source = it.body?.source() ?: return onEvent(ProviderEvent.Error("Empty response body"))
                    try {
                        var currentEvent: String? = null
                        while (!source.exhausted()) {
                            val line = source.readUtf8Line() ?: break
                            when {
                                line.startsWith("event:") -> currentEvent = line.removePrefix("event:").trim()
                                line.startsWith("data:") -> {
                                    val data = line.removePrefix("data:").trim()
                                    handle(currentEvent, data, onEvent, toolBuilders)
                                }
                            }
                        }
                        if (!call.isCanceled()) onEvent(ProviderEvent.Done)
                    } catch (e: Exception) {
                        if (!call.isCanceled()) onEvent(ProviderEvent.Error(e.message ?: "Stream error", e))
                    }
                }
            }
        })
        return Cancellable { call.cancel() }
    }

    private fun handle(event: String?, data: String, onEvent: (ProviderEvent) -> Unit, toolBuilders: MutableMap<Int, ToolBuilder>) {
        if (data.isBlank()) return
        val root = runCatching { json.parseToJsonElement(data).jsonObject }.getOrNull() ?: return
        when (event) {
            "content_block_start" -> {
                val idx = root["index"]?.jsonPrimitive?.intOrNull ?: 0
                val block = root["content_block"]?.jsonObject ?: return
                if (block["type"]?.jsonPrimitive?.contentOrNull == "tool_use") {
                    toolBuilders[idx] = ToolBuilder(
                        id = block["id"]?.jsonPrimitive?.contentOrNull ?: "",
                        name = block["name"]?.jsonPrimitive?.contentOrNull ?: "",
                    )
                }
            }
            "content_block_delta" -> {
                val idx = root["index"]?.jsonPrimitive?.intOrNull ?: 0
                val delta = root["delta"]?.jsonObject ?: return
                when (delta["type"]?.jsonPrimitive?.contentOrNull) {
                    "text_delta" -> delta["text"]?.jsonPrimitive?.contentOrNull?.let { onEvent(ProviderEvent.TextDelta(it)) }
                    "thinking_delta" -> delta["thinking"]?.jsonPrimitive?.contentOrNull?.let { onEvent(ProviderEvent.ThinkingDelta(it)) }
                    "input_json_delta" -> delta["partial_json"]?.jsonPrimitive?.contentOrNull?.let { toolBuilders[idx]?.args?.append(it) }
                }
            }
            "content_block_stop" -> {
                val idx = root["index"]?.jsonPrimitive?.intOrNull ?: 0
                val b = toolBuilders.remove(idx) ?: return
                onEvent(ProviderEvent.ToolCall(b.id, b.name, b.args.toString().ifBlank { "{}" }))
            }
            "error" -> {
                val msg = root["error"]?.jsonObject?.get("message")?.jsonPrimitive?.contentOrNull ?: data
                onEvent(ProviderEvent.Error(msg))
            }
        }
    }

    private fun defaultTools(): JsonArray = buildJsonArray {
        fun tool(name: String, desc: String, props: JsonObject, required: List<String> = emptyList()) {
            add(buildJsonObject {
                put("name", name)
                put("description", desc)
                put("input_schema", buildJsonObject {
                    put("type", "object")
                    put("properties", props)
                    put("required", buildJsonArray { required.forEach { add(kotlinx.serialization.json.JsonPrimitive(it)) } })
                })
            })
        }
        tool("run_command", "Run a shell command in the app terminal environment", buildJsonObject {
            put("command", buildJsonObject { put("type", "string"); put("description", "Shell command to run") })
        }, listOf("command"))
        tool("read_file", "Read a file from the workspace", buildJsonObject { put("path", buildJsonObject { put("type", "string") }) }, listOf("path"))
        tool("write_file", "Write content to a file in the workspace", buildJsonObject {
            put("path", buildJsonObject { put("type", "string") })
            put("content", buildJsonObject { put("type", "string") })
        }, listOf("path", "content"))
        tool("edit_file", "Replace exact text in a workspace file", buildJsonObject {
            put("path", buildJsonObject { put("type", "string") })
            put("oldString", buildJsonObject { put("type", "string") })
            put("newString", buildJsonObject { put("type", "string") })
        }, listOf("path", "oldString", "newString"))
        tool("list_dir", "List files in a workspace directory", buildJsonObject { put("path", buildJsonObject { put("type", "string") }) })
        tool("search", "Search text in workspace files", buildJsonObject {
            put("query", buildJsonObject { put("type", "string") })
            put("path", buildJsonObject { put("type", "string") })
        }, listOf("query"))
        tool("list_granted_folders", "List SAF folder URIs granted by the user", buildJsonObject {})
        tool("read_granted_uri", "Read content from a granted SAF URI", buildJsonObject {
            put("uri", buildJsonObject { put("type", "string") })
        }, listOf("uri"))
    }

    private class ToolBuilder(val id: String, val name: String, val args: StringBuilder = StringBuilder())
}
