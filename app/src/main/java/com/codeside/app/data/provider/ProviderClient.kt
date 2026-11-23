package com.codeside.app.data.provider

import com.codeside.app.data.model.ChatMessage
import com.codeside.app.data.model.ProviderProfile

sealed class ProviderEvent {
    data class TextDelta(val text: String) : ProviderEvent()
    data class ThinkingDelta(val text: String) : ProviderEvent()
    data class Error(val message: String, val throwable: Throwable? = null) : ProviderEvent()
    data object Done : ProviderEvent()
}

interface ProviderClient {
    fun streamChat(
        profile: ProviderProfile,
        messages: List<ChatMessage>,
        onEvent: (ProviderEvent) -> Unit,
    ): Cancellable
}

fun interface Cancellable {
    fun cancel()
}
