package com.codeside.app.data.provider

import com.codeside.app.data.model.ProviderProfile
import com.codeside.app.data.model.ProviderType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object ModelFetcher {
    private val client = OkHttpClient.Builder()
        .callTimeout(20, TimeUnit.SECONDS)
        .build()

    suspend fun fetchModels(profile: ProviderProfile): Result<List<String>> = withContext(Dispatchers.IO) {
        runCatching {
            val p = profile.normalized()
            val req = when (p.type) {
                ProviderType.GOOGLE -> buildGeminiRequest(p)
                ProviderType.ANTHROPIC -> buildAnthropicRequest(p)
                else -> buildOpenAiRequest(p)
            }
            client.newCall(req).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) error("HTTP ${resp.code}: ${body.take(200)}")
                parseModels(p.type, body)
            }
        }
    }

    private fun buildOpenAiRequest(p: ProviderProfile): Request {
        val base = p.baseUrl.trimEnd('/')
        val url = "$base/models"
        val b = Request.Builder().url(url).get()
        if (p.apiKey.isNotBlank()) b.header(p.authHeader.ifBlank { "Authorization" }, "${p.authPrefix}${p.apiKey}")
        return b.build()
    }

    private fun buildAnthropicRequest(p: ProviderProfile): Request {
        val base = p.baseUrl.trimEnd('/')
        val url = "$base/models"
        val b = Request.Builder().url(url).get()
        if (p.apiKey.isNotBlank()) b.header("x-api-key", p.apiKey)
        b.header("anthropic-version", "2023-06-01")
        return b.build()
    }

    private fun buildGeminiRequest(p: ProviderProfile): Request {
        val base = p.baseUrl.trimEnd('/')
        val key = p.apiKey.trim()
        val url = if (key.isNotBlank()) "$base/models?key=$key" else "$base/models"
        return Request.Builder().url(url).get().build()
    }

    private fun parseModels(type: ProviderType, body: String): List<String> {
        val root = runCatching { JSONObject(body) }.getOrNull() ?: return emptyList()
        val arr: JSONArray = when {
            root.has("data") -> root.optJSONArray("data") ?: JSONArray()
            root.has("models") -> root.optJSONArray("models") ?: JSONArray()
            else -> JSONArray()
        }
        val out = linkedSetOf<String>()
        for (i in 0 until arr.length()) {
            val item = arr.opt(i)
            val id = when (item) {
                is JSONObject -> item.optString("id").ifBlank { item.optString("name").ifBlank { item.optString("model") } }
                is String -> item
                else -> ""
            }
            val clean = id.removePrefix("models/").trim()
            if (clean.isNotBlank()) out += clean
        }
        return out.toList().sorted()
    }
}
