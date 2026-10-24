package com.gemini.hw.core.model

import kotlinx.serialization.Serializable

@Serializable
data class Conversation(
    val id: String,
    val title: String,
    val updatedAt: Long = System.currentTimeMillis(),
    val snippet: String = "",
    val modelId: String? = null,
    val isPinned: Boolean = false,
    val notebookId: String? = null,
    val dateText: String? = null
)

enum class MessageRole {
    USER,
    MODEL,
    SYSTEM
}

@Serializable
data class Attachment(
    val id: String,
    val name: String,
    val mimeType: String,
    val url: String? = null,
    val localUri: String? = null,
    val sizeBytes: Long = 0,
    val isGenerated: Boolean = false,
    val isVideo: Boolean = false
)

@Serializable
data class CanvasDocument(
    val id: String,
    val title: String,
    val content: String,
    val language: String? = null,
    val version: Int = 1,
    val updatedAt: Long = System.currentTimeMillis()
)

@Serializable
data class ChatMessage(
    val id: String,
    val conversationId: String,
    val role: MessageRole,
    val text: String,
    val timestamp: Long = System.currentTimeMillis(),
    val thoughts: String? = null,
    val attachments: List<Attachment> = emptyList(),
    val canvas: CanvasDocument? = null,
    val isStreaming: Boolean = false,
    val parentMessageId: String? = null,
    val alternateCandidateCount: Int = 1,
    val currentCandidateIndex: Int = 0
)

@Serializable
data class Gem(
    val id: String,
    val name: String,
    val description: String,
    val iconName: String = "gem",
    val systemInstructions: String = "",
    val isCustom: Boolean = false
)

@Serializable
data class Notebook(
    val id: String,
    val title: String,
    val content: String = "",
    val sources: List<String> = emptyList(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Serializable
data class ScheduledItem(
    val id: String,
    val title: String,
    val prompt: String,
    val scheduledTimeEpochMillis: Long,
    val isRecurring: Boolean = false,
    val recurrencePattern: String? = null,
    val isEnabled: Boolean = true
)

@Serializable
data class SparkTask(
    val id: String,
    val title: String,
    val description: String,
    val status: SparkTaskStatus = SparkTaskStatus.PENDING,
    val steps: List<SparkStep> = emptyList(),
    val createdAt: Long = System.currentTimeMillis()
)

enum class SparkTaskStatus {
    PENDING,
    IN_PROGRESS,
    COMPLETED,
    FAILED
}

@Serializable
data class SparkStep(
    val id: String,
    val title: String,
    val detail: String,
    val isCompleted: Boolean = false
)
