package com.gemini.hw.core.model

import kotlinx.serialization.Serializable

@Serializable
data class GeminiModel(
    val id: String,
    val displayName: String,
    val description: String,
    val isDefault: Boolean = false,
    val isThinking: Boolean = false,
    val isLiveVoiceSupported: Boolean = false,
    val badge: String? = null
) {
    companion object {
        val FALLBACK_MODELS = listOf(
            GeminiModel(
                id = "gemini-2.0-flash",
                displayName = "Gemini 2.0 Flash",
                description = "Быстрая и универсальная модель нового поколения",
                isDefault = true,
                isLiveVoiceSupported = true,
                badge = "Быстрый"
            ),
            GeminiModel(
                id = "gemini-1.5-pro",
                displayName = "Gemini 1.5 Pro",
                description = "Мощная модель для сложных рассуждений и кода",
                isDefault = false,
                isThinking = true,
                isLiveVoiceSupported = true,
                badge = "Рассуждения"
            ),
            GeminiModel(
                id = "gemini-2.0-flash-thinking",
                displayName = "Gemini 2.0 Thinking",
                description = "Пошаговый процесс рассуждений перед ответом",
                isDefault = false,
                isThinking = true,
                badge = "Эксперимент"
            )
        )
    }
}
