package com.codeside.app.data.provider

import com.codeside.app.data.model.ChatMessage
import com.codeside.app.data.model.ChatRole
import com.codeside.app.data.model.ProviderProfile
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
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
import java.util.concurrent.TimeUnit

class OpenAiCompatibleClient(
    private val http: OkHttpClient = defaultHttp(),
) : ProviderClient {

    private val json = Json { ignoreUnknownKeys = true }

    override fun streamChat(
        profile: ProviderProfile,
        messages: List<ChatMessage>,
        onEvent: (ProviderEvent) -> Unit,
    ): Cancellable {
        val url = buildUrl(profile)
        val body = buildBody(profile, messages)
        val reqBody = json.encodeToString(JsonObject.serializer(), body)
            .toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url(url)
            .post(reqBody)
            .header(profile.authHeader.ifBlank { "Authorization" }, profile.authPrefix + profile.apiKey)
            .header("Accept", "text/event-stream")
            .apply {
                parseExtraHeaders(profile.extraHeadersJson).forEach { (k, v) -> header(k, v) }
            }
            .build()

        val toolCalls = mutableMapOf<Int, ToolCallBuilder>()
        val call = http.newCall(request)
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (!call.isCanceled()) onEvent(ProviderEvent.Error(e.message ?: "Network error", e))
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (!it.isSuccessful) {
                        val err = it.body?.string()?.take(2000) ?: "HTTP ${it.code}"
                        onEvent(ProviderEvent.Error(err))
                        return
                    }
                    val source = it.body?.source() ?: run {
                        onEvent(ProviderEvent.Error("Empty response body"))
                        return
                    }
                    try {
                        while (!source.exhausted()) {
                            val line = source.readUtf8Line() ?: break
                            if (!line.startsWith("data:")) continue
                            val data = line.removePrefix("data:").trim()
                            if (data == "[DONE]") break
                            handleData(data, onEvent, toolCalls)
                        }
                        toolCalls.values.forEach { b -> onEvent(ProviderEvent.ToolCall(b.id, b.name, b.arguments.toString())) }
                        if (!call.isCanceled()) onEvent(ProviderEvent.Done)
                    } catch (e: Exception) {
                        if (!call.isCanceled()) onEvent(ProviderEvent.Error(e.message ?: "Stream error", e))
                    }
                }
            }
        })
        return Cancellable { call.cancel() }
    }

    private fun buildUrl(profile: ProviderProfile): String {
        val base = profile.baseUrl.trimEnd('/')
        val path = profile.chatPath.ifBlank { "/chat/completions" }
        return if (path.startsWith("http")) path else base + (if (path.startsWith("/")) path else "/$path")
    }

    private fun buildBody(profile: ProviderProfile, messages: List<ChatMessage>): JsonObject {
        val extra = parseExtraBody(profile.extraBodyJson)
        return buildJsonObject {
            put("model", profile.model)
            put("stream", true)
            put("messages", buildJsonArray {
                messages.forEach { msg ->
                    add(buildJsonObject {
                        when (msg.role) {
                            ChatRole.TOOL -> {
                                put("role", "tool")
                                put("tool_call_id", msg.toolCallId ?: "")
                                put("content", msg.content)
                            }
                            ChatRole.ASSISTANT -> {
                                put("role", "assistant")
                                if (msg.content.isNotBlank()) put("content", msg.content) else put("content", kotlinx.serialization.json.JsonNull)
                                if (msg.toolCalls.isNotEmpty()) {
                                    put("tool_calls", buildJsonArray {
                                        msg.toolCalls.forEach { tc ->
                                            add(buildJsonObject {
                                                put("id", tc.id)
                                                put("type", "function")
                                                put("function", buildJsonObject {
                                                    put("name", tc.name)
                                                    put("arguments", tc.arguments)
                                                })
                                            })
                                        }
                                    })
                                }
                            }
                            else -> {
                                put("role", when (msg.role) {
                                    ChatRole.USER -> "user"
                                    ChatRole.SYSTEM -> "system"
                                    else -> "user"
                                })
                                put("content", msg.content)
                            }
                        }
                    })
                }
            })
            put("tools", defaultTools())
            extra.forEach { (k, v) -> put(k, v) }
        }
    }

    private fun handleData(data: String, onEvent: (ProviderEvent) -> Unit, toolCalls: MutableMap<Int, ToolCallBuilder>) {
        val root = runCatching { json.parseToJsonElement(data).jsonObject }.getOrNull() ?: return
        val choices = root["choices"] as? JsonArray ?: return
        val choice = choices.firstOrNull()?.jsonObject ?: return
        val delta = choice["delta"]?.jsonObject ?: return

        delta["content"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotEmpty() }?.let {
            onEvent(ProviderEvent.TextDelta(it))
        }
        val reasoning = delta["reasoning_content"]?.jsonPrimitive?.contentOrNull
            ?: delta["thinking"]?.jsonPrimitive?.contentOrNull
        reasoning?.takeIf { it.isNotEmpty() }?.let { onEvent(ProviderEvent.ThinkingDelta(it)) }

        val tc = delta["tool_calls"] as? JsonArray ?: return
        tc.forEach { el ->
            val obj = el.jsonObject
            val idx = obj["index"]?.jsonPrimitive?.intOrNull ?: 0
            val b = toolCalls.getOrPut(idx) { ToolCallBuilder() }
            obj["id"]?.jsonPrimitive?.contentOrNull?.let { b.id = it }
            val fn = obj["function"]?.jsonObject
            fn?.get("name")?.jsonPrimitive?.contentOrNull?.let { b.name = it }
            fn?.get("arguments")?.jsonPrimitive?.contentOrNull?.let { b.arguments.append(it) }
        }
    }

    private fun defaultTools(): JsonArray = buildJsonArray {
        fun tool(name: String, desc: String, props: JsonObject, required: List<String> = emptyList()) {
            add(buildJsonObject {
                put("type", "function")
                put("function", buildJsonObject {
                    put("name", name)
                    put("description", desc)
                    put("parameters", buildJsonObject {
                        put("type", "object")
                        put("properties", props)
                        put("required", buildJsonArray { required.forEach { add(kotlinx.serialization.json.JsonPrimitive(it)) } })
                    })
                })
            })
        }
        tool("run_command", "Run a shell command in the app terminal environment", buildJsonObject {
            put("command", buildJsonObject { put("type", "string"); put("description", "Shell command to run") })
        }, listOf("command"))
        tool("read_file", "Read a file from the workspace", buildJsonObject {
            put("path", buildJsonObject { put("type", "string") })
        }, listOf("path"))
        tool("write_file", "Write content to a file in the workspace", buildJsonObject {
            put("path", buildJsonObject { put("type", "string") })
            put("content", buildJsonObject { put("type", "string") })
        }, listOf("path", "content"))
        tool("edit_file", "Replace exact text in a workspace file", buildJsonObject {
            put("path", buildJsonObject { put("type", "string") })
            put("oldString", buildJsonObject { put("type", "string") })
            put("newString", buildJsonObject { put("type", "string") })
        }, listOf("path", "oldString", "newString"))
        tool("list_dir", "List files in a workspace directory", buildJsonObject {
            put("path", buildJsonObject { put("type", "string") })
        })
        tool("search", "Search text in workspace files", buildJsonObject {
            put("query", buildJsonObject { put("type", "string") })
            put("path", buildJsonObject { put("type", "string") })
        }, listOf("query"))
        tool("list_granted_folders", "List SAF folder URIs granted by the user", buildJsonObject {})
        tool("read_granted_uri", "Read content from a granted SAF URI", buildJsonObject {
            put("uri", buildJsonObject { put("type", "string") })
        }, listOf("uri"))
    }

    private fun parseExtraHeaders(raw: String): Map<String, String> = parseStringMap(raw)
    private fun parseExtraBody(raw: String): Map<String, JsonElement> {
        val obj = runCatching { json.parseToJsonElement(raw).jsonObject }.getOrNull() ?: return emptyMap()
        return obj
    }

    private fun parseStringMap(raw: String): Map<String, String> {
        val obj = runCatching { json.parseToJsonElement(raw).jsonObject }.getOrNull() ?: return emptyMap()
        return obj.mapNotNull { (k, v) ->
            val s = (v as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull ?: return@mapNotNull null
            k to s
        }.toMap()
    }

    private class ToolCallBuilder {
        var id: String = ""
        var name: String = ""
        val arguments: StringBuilder = StringBuilder()
    }

    companion object {
        fun defaultHttp(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }
}
