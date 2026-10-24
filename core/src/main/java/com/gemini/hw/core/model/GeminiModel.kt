package com.gemini.hw.core.model

import kotlinx.serialization.Serializable

@Serializable
data class GeminiModel(
    val id: String,
    val displayName: String,
    val description: String,
    val isDefault: Boolean = false,
    val isThinking: Boolean = false,
    val isLiveVoiceSupported: Boolean = true,
    val badge: String? = null,
    val shortName: String = displayName
) {
    companion object {
        // Modern 2026 flagship Gemini models matching gemini.google.com
        val MODEL_FLASH_3_8 = GeminiModel(
            id = "gemini-3.8-flash",
            displayName = "3.8 Flash",
            shortName = "Flash",
            description = "All-around help",
            isDefault = true,
            badge = "Flash"
        )

        val MODEL_FLASH_LITE_3_5 = GeminiModel(
            id = "gemini-3.5-flash-lite",
            displayName = "3.5 Flash-Lite",
            shortName = "Lite",
            description = "Самые быстрые ответы",
            badge = "Lite"
        )

        val MODEL_PRO_3_1 = GeminiModel(
            id = "gemini-3.1-pro",
            displayName = "3.1 Pro",
            shortName = "Pro",
            description = "Расширенные возможности рассуждений",
            badge = "Pro"
        )

        val MODEL_ARGON_4 = GeminiModel(
            id = "gemini-4-argon",
            displayName = "4 Argon",
            shortName = "Argon",
            description = "Сложные задачи",
            badge = "Argon"
        )

        val CURRENT_MODELS = listOf(
            MODEL_FLASH_3_8,
            MODEL_FLASH_LITE_3_5,
            MODEL_PRO_3_1,
            MODEL_ARGON_4
        )

        val FALLBACK_MODELS = CURRENT_MODELS

        fun createDynamic(id: String): GeminiModel {
            val cleanId = id.trim()
            val lower = cleanId.lowercase()

            return when {
                lower.contains("3.8") || lower == "gemini-3.8-flash" || lower == "flash" -> MODEL_FLASH_3_8
                lower.contains("3.5") || lower.contains("flash-lite") -> MODEL_FLASH_LITE_3_5
                lower.contains("3.1") || (lower.contains("pro") && !lower.contains("1.5")) -> MODEL_PRO_3_1
                lower.contains("argon") || lower.contains("gemini-4") -> MODEL_ARGON_4
                else -> {
                    val isThinking = lower.contains("thinking")
                    val isPro = lower.contains("pro") || lower.contains("advanced")
                    val isPreview = lower.contains("preview")
                    val isFlash = lower.contains("flash")

                    val badge = when {
                        isThinking -> "Рассуждения"
                        isPro -> "Pro"
                        isPreview -> "Preview"
                        isFlash -> "Flash"
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

                    GeminiModel(
                        id = cleanId,
                        displayName = displayName,
                        shortName = displayName.replace("Gemini ", ""),
                        description = when {
                            isThinking -> "Модель с глубокими пошаговыми рассуждениями"
                            isPro -> "Расширенные возможности рассуждений"
                            isPreview -> "Предварительная версия модели"
                            isFlash -> "Быстрые ответы для повседневных задач"
                            else -> "Динамически загруженная модель Gemini"
                        },
                        isDefault = false,
                        isThinking = isThinking,
                        isLiveVoiceSupported = true,
                        badge = badge
                    )
                }
            }
        }
    }
}
