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

class GeminiClient(
    private val http: OkHttpClient = OpenAiCompatibleClient.defaultHttp(),
) : ProviderClient {
    private val json = Json { ignoreUnknownKeys = true }

    override fun streamChat(
        profile: ProviderProfile,
        messages: List<ChatMessage>,
        onEvent: (ProviderEvent) -> Unit,
    ): Cancellable {
        val base = profile.baseUrl.trimEnd('/')
        val model = profile.model.trim()
        val path = profile.chatPath.ifBlank { "/v1beta/models/{model}:streamGenerateContent?alt=sse" }
            .replace("{model}", model)
        val url = if (path.startsWith("http")) path else base + (if (path.startsWith("/")) path else "/$path")

        val system = messages.filter { it.role == ChatRole.SYSTEM }.joinToString("\n\n") { it.content }.ifBlank { null }
        val body = buildJsonObject {
            system?.let {
                put("systemInstruction", buildJsonObject {
                    put("parts", buildJsonArray { add(buildJsonObject { put("text", it) }) })
                })
            }
            put("contents", buildJsonArray {
                messages.filter { it.role == ChatRole.USER || it.role == ChatRole.ASSISTANT }.forEach { msg ->
                    add(buildJsonObject {
                        put("role", if (msg.role == ChatRole.USER) "user" else "model")
                        put("parts", buildJsonArray { add(buildJsonObject { put("text", msg.content) }) })
                    })
                }
            })
        }

        val req = Request.Builder()
            .url(url)
            .post(json.encodeToString(JsonObject.serializer(), body).toRequestBody("application/json".toMediaType()))
            .header("x-goog-api-key", profile.apiKey)
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
                        while (!source.exhausted()) {
                            val line = source.readUtf8Line() ?: break
                            if (!line.startsWith("data:")) continue
                            val data = line.removePrefix("data:").trim()
                            if (data.isBlank()) continue
                            handle(data, onEvent)
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

    private fun handle(data: String, onEvent: (ProviderEvent) -> Unit) {
        val root = runCatching { json.parseToJsonElement(data).jsonObject }.getOrNull() ?: return
        val candidates = root["candidates"] as? JsonArray ?: return
        val content = candidates.firstOrNull()?.jsonObject?.get("content")?.jsonObject ?: return
        val parts = content["parts"] as? JsonArray ?: return
        parts.forEach { part ->
            val text = part.jsonObject["text"]?.jsonPrimitive?.contentOrNull
            if (!text.isNullOrEmpty()) onEvent(ProviderEvent.TextDelta(text))
        }
    }
}
