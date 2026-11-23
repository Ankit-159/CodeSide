package com.codeside.app.data.provider

import com.codeside.app.data.model.ProviderType

object ProviderFactory {
    private val openAi = OpenAiCompatibleClient()
    private val anthropic = AnthropicClient()
    private val gemini = GeminiClient()

    fun get(type: ProviderType): ProviderClient = when (type) {
        ProviderType.OPENAI, ProviderType.CUSTOM -> openAi
        ProviderType.ANTHROPIC -> anthropic
        ProviderType.GOOGLE -> gemini
    }
}
