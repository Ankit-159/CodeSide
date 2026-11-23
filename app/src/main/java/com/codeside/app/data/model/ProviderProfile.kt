package com.codeside.app.data.model

import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
enum class ProviderType {
    OPENAI,
    ANTHROPIC,
    GOOGLE,
    CUSTOM,
}

@Serializable
data class ProviderProfile(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val type: ProviderType = ProviderType.OPENAI,
    val baseUrl: String = "",
    val apiKey: String = "",
    val model: String = "",
    val chatPath: String = "",
    val authHeader: String = "Authorization",
    val authPrefix: String = "Bearer ",
    val extraHeadersJson: String = "{}",
    val extraBodyJson: String = "{}",
) {
    fun normalized(): ProviderProfile = copy(
        baseUrl = baseUrl.trim().trimEnd('/'),
        apiKey = apiKey.trim(),
        model = model.trim(),
        chatPath = chatPath.trim(),
        authHeader = authHeader.trim().ifBlank { "Authorization" },
    )

    companion object {
        fun preset(type: ProviderType): ProviderProfile = when (type) {
            ProviderType.OPENAI -> ProviderProfile(
                name = "OpenAI",
                type = ProviderType.OPENAI,
                baseUrl = "https://api.openai.com/v1",
                model = "gpt-4.1-mini",
                chatPath = "/chat/completions",
            )
            ProviderType.ANTHROPIC -> ProviderProfile(
                name = "Anthropic",
                type = ProviderType.ANTHROPIC,
                baseUrl = "https://api.anthropic.com",
                model = "claude-sonnet-4-5",
                chatPath = "/v1/messages",
                authHeader = "x-api-key",
                authPrefix = "",
            )
            ProviderType.GOOGLE -> ProviderProfile(
                name = "Google",
                type = ProviderType.GOOGLE,
                baseUrl = "https://generativelanguage.googleapis.com",
                model = "gemini-2.5-flash",
                chatPath = "/v1beta/models/{model}:streamGenerateContent?alt=sse",
            )
            ProviderType.CUSTOM -> ProviderProfile(
                name = "Custom",
                type = ProviderType.CUSTOM,
                baseUrl = "https://",
                model = "",
                chatPath = "/v1/chat/completions",
            )
        }
    }
}
