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
        fun createDynamic(id: String): GeminiModel {
            val cleanId = id.trim()
            val isThinking = cleanId.contains("thinking", ignoreCase = true)
            val isPro = cleanId.contains("pro", ignoreCase = true) || cleanId.contains("advanced", ignoreCase = true)
            val isPreview = cleanId.contains("preview", ignoreCase = true)
            val isFlash = cleanId.contains("flash", ignoreCase = true)

            val badge = when {
                isThinking -> "Рассуждения"
                isPro -> "Pro"
                isPreview -> "Preview"
                isFlash -> "Быстрый"
                else -> null
            }

            val displayName = cleanId
                .split("-")
                .joinToString(" ") { part ->
                    when (part.lowercase()) {
                        "gemini" -> "Gemini"
                        "tts" -> "TTS"
                        else -> part.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
                    }
                }

            val description = when {
                isThinking -> "Модель с глубокими пошаговыми рассуждениями"
                isPro -> "Высокопроизводительная модель для сложных аналитических задач"
                isPreview -> "Предварительная версия новой архитектуры модели"
                isFlash -> "Сверхбыстрая легковесная модель для повседневных задач"
                else -> "Динамически загруженная модель Gemini с сервера"
            }

            return GeminiModel(
                id = cleanId,
                displayName = displayName,
                description = description,
                isDefault = cleanId == "gemini-2.0-flash",
                isThinking = isThinking,
                isLiveVoiceSupported = true,
                badge = badge
            )
        }

        val FALLBACK_MODELS = listOf(
            createDynamic("gemini-2.0-flash"),
            createDynamic("gemini-2.5-flash"),
            createDynamic("gemini-3-flash-preview"),
            createDynamic("gemini-1.5-pro"),
            createDynamic("gemini-2.0-flash-thinking")
        )
    }
}
