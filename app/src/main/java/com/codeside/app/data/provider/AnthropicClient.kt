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
            put("messages", buildJsonArray {
                messages.filter { it.role == ChatRole.USER || it.role == ChatRole.ASSISTANT }.forEach { msg ->
                    add(buildJsonObject {
                        put("role", if (msg.role == ChatRole.USER) "user" else "assistant")
                        put("content", msg.content)
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
                                    handle(currentEvent, data, onEvent)
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

    private fun handle(event: String?, data: String, onEvent: (ProviderEvent) -> Unit) {
        if (data.isBlank()) return
        val root = runCatching { json.parseToJsonElement(data).jsonObject }.getOrNull() ?: return
        when (event) {
            "content_block_delta" -> {
                val delta = root["delta"]?.jsonObject ?: return
                when (delta["type"]?.jsonPrimitive?.contentOrNull) {
                    "text_delta" -> delta["text"]?.jsonPrimitive?.contentOrNull?.let { onEvent(ProviderEvent.TextDelta(it)) }
                    "thinking_delta" -> delta["thinking"]?.jsonPrimitive?.contentOrNull?.let { onEvent(ProviderEvent.ThinkingDelta(it)) }
                }
            }
            "error" -> {
                val msg = root["error"]?.jsonObject?.get("message")?.jsonPrimitive?.contentOrNull ?: data
                onEvent(ProviderEvent.Error(msg))
            }
        }
    }
}
