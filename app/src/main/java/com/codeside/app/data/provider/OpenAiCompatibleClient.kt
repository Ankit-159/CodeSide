package com.codeside.app.data.provider

import com.codeside.app.data.model.ChatMessage
import com.codeside.app.data.model.ChatRole
import com.codeside.app.data.model.ProviderProfile
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
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
                            handleData(data, onEvent)
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
                messages.filter { it.role != ChatRole.TOOL }.forEach { msg ->
                    add(buildJsonObject {
                        put("role", when (msg.role) {
                            ChatRole.USER -> "user"
                            ChatRole.ASSISTANT -> "assistant"
                            ChatRole.SYSTEM -> "system"
                            ChatRole.TOOL -> "tool"
                        })
                        put("content", msg.content)
                    })
                }
            })
            extra.forEach { (k, v) -> put(k, v) }
        }
    }

    private fun handleData(data: String, onEvent: (ProviderEvent) -> Unit) {
        val root = runCatching { json.parseToJsonElement(data).jsonObject }.getOrNull() ?: return
        val choices = root["choices"] as? JsonArray ?: return
        val delta = choices.firstOrNull()?.jsonObject?.get("delta")?.jsonObject ?: return
        delta["content"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotEmpty() }?.let {
            onEvent(ProviderEvent.TextDelta(it))
        }
        val reasoning = delta["reasoning_content"]?.jsonPrimitive?.contentOrNull
            ?: delta["thinking"]?.jsonPrimitive?.contentOrNull
        reasoning?.takeIf { it.isNotEmpty() }?.let { onEvent(ProviderEvent.ThinkingDelta(it)) }
    }

    private fun parseExtraHeaders(raw: String): Map<String, String> = parseStringMap(raw)
    private fun parseExtraBody(raw: String): Map<String, JsonElement> {
        val obj = runCatching { json.parseToJsonElement(raw).jsonObject }.getOrNull() ?: return emptyMap()
        return obj
    }

    private fun parseStringMap(raw: String): Map<String, String> {
        val obj = runCatching { json.parseToJsonElement(raw).jsonObject }.getOrNull() ?: return emptyMap()
        return obj.mapNotNull { (k, v) ->
            val s = (v as? JsonPrimitive)?.contentOrNull ?: return@mapNotNull null
            k to s
        }.toMap()
    }

    companion object {
        fun defaultHttp(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }
}
