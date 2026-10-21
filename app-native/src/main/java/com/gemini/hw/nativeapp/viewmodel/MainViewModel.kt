package com.gemini.hw.nativeapp.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.gemini.hw.core.audio.AudioRecorder
import com.gemini.hw.core.auth.AuthRepository
import com.gemini.hw.core.model.*
import com.gemini.hw.core.network.GeminiWebClient
import com.gemini.hw.core.repository.GemsAndSparkRepository
import com.gemini.hw.nativeapp.GeminiApp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as GeminiApp
    private val authRepository: AuthRepository = app.authRepository
    private val client: GeminiWebClient = app.geminiWebClient
    private val audioRecorder: AudioRecorder = AudioRecorder(application)
    val gemsAndSparkRepo: GemsAndSparkRepository = app.gemsAndSparkRepository

    val isLoggedIn: StateFlow<Boolean> = authRepository.isLoggedIn

    val gems: StateFlow<List<Gem>> = gemsAndSparkRepo.gems
    val sparkTasks: StateFlow<List<SparkTask>> = gemsAndSparkRepo.sparkTasks
    val scheduledItems: StateFlow<List<ScheduledItem>> = gemsAndSparkRepo.scheduledItems

    private val _conversations = MutableStateFlow<List<Conversation>>(emptyList())
    val conversations: StateFlow<List<Conversation>> = _conversations.asStateFlow()

    private val _currentConversationId = MutableStateFlow<String?>(null)
    val currentConversationId: StateFlow<String?> = _currentConversationId.asStateFlow()

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _models = MutableStateFlow<List<GeminiModel>>(GeminiModel.FALLBACK_MODELS)
    val models: StateFlow<List<GeminiModel>> = _models.asStateFlow()

    private val _selectedModel = MutableStateFlow<GeminiModel>(GeminiModel.FALLBACK_MODELS.first())
    val selectedModel: StateFlow<GeminiModel> = _selectedModel.asStateFlow()

    private val _isRecordingVoice = MutableStateFlow(false)
    val isRecordingVoice: StateFlow<Boolean> = _isRecordingVoice.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _activeCanvas = MutableStateFlow<CanvasDocument?>(null)
    val activeCanvas: StateFlow<CanvasDocument?> = _activeCanvas.asStateFlow()

    init {
        if (authRepository.hasValidSession()) {
            loadInitialData()
        }
    }

    fun loadInitialData() {
        viewModelScope.launch {
            _isLoading.value = true
            // Dynamic model list fetch from gemini.google.com
            val modelResult = client.refreshTokensAndFetchModels()
            if (modelResult.isSuccess) {
                val fetchedModels = modelResult.getOrThrow()
                _models.value = fetchedModels
                _selectedModel.value = fetchedModels.firstOrNull { it.isDefault } ?: fetchedModels.first()
            }

            // Sync conversations
            val convoResult = client.getConversations()
            if (convoResult.isSuccess) {
                _conversations.value = convoResult.getOrThrow()
            }
            _isLoading.value = false
        }
    }

    fun selectConversation(conversationId: String?) {
        _currentConversationId.value = conversationId
        _messages.value = emptyList()
    }

    fun startNewChat() {
        _currentConversationId.value = null
        _messages.value = emptyList()
    }

    fun selectModel(model: GeminiModel) {
        _selectedModel.value = model
    }

    fun sendMessage(text: String) {
        if (text.isBlank()) return
        val userMsgId = "msg_u_" + System.currentTimeMillis()
        val userMsg = ChatMessage(
            id = userMsgId,
            conversationId = _currentConversationId.value ?: "new",
            role = MessageRole.USER,
            text = text.trim()
        )

        _messages.value = _messages.value + userMsg
        dispatchToGemini(text, null)
    }

    private fun dispatchToGemini(prompt: String, audioBytes: ByteArray?) {
        viewModelScope.launch {
            _isLoading.value = true
            val convoId = _currentConversationId.value
            val currentModel = _selectedModel.value

            client.sendMessageStream(
                conversationId = convoId,
                prompt = prompt,
                modelId = currentModel.id,
                audioBytes = audioBytes
            ).collect { responseMsg ->
                val list = _messages.value.toMutableList()
                val existingIndex = list.indexOfFirst { it.id == responseMsg.id }
                if (existingIndex >= 0) {
                    list[existingIndex] = responseMsg
                } else {
                    list.add(responseMsg)
                }
                _messages.value = list
                if (_currentConversationId.value == null) {
                    _currentConversationId.value = responseMsg.conversationId
                }
            }
            _isLoading.value = false
        }
    }

    fun startVoiceRecording() {
        if (_isRecordingVoice.value) return
        _isRecordingVoice.value = true
        viewModelScope.launch {
            try {
                val audioBytes = audioRecorder.recordAudio()
                _isRecordingVoice.value = false
                if (audioBytes.isNotEmpty()) {
                    val userMsg = ChatMessage(
                        id = "msg_audio_" + System.currentTimeMillis(),
                        conversationId = _currentConversationId.value ?: "new",
                        role = MessageRole.USER,
                        text = "🎤 [Голосовое сообщение]"
                    )
                    _messages.value = _messages.value + userMsg
                    dispatchToGemini("Ответь на аудиозапись", audioBytes)
                }
            } catch (e: Exception) {
                _isRecordingVoice.value = false
            }
        }
    }

    fun stopVoiceRecording() {
        audioRecorder.stop()
        _isRecordingVoice.value = false
    }

    /**
     * Edit a previously sent message and regenerate response
     */
    fun editMessage(messageId: String, newText: String) {
        val list = _messages.value.toMutableList()
        val index = list.indexOfFirst { it.id == messageId }
        if (index >= 0) {
            val edited = list[index].copy(text = newText)
            val subList = list.subList(0, index + 1).toMutableList()
            subList[index] = edited
            _messages.value = subList
            dispatchToGemini(newText, null)
        }
    }

    /**
     * Branch dialogue into a new chat starting from this message
     */
    fun createBranchInNewChat(message: ChatMessage) {
        viewModelScope.launch {
            val convoId = _currentConversationId.value ?: return@launch
            val res = client.createBranchInNewChat(convoId, message.id)
            if (res.isSuccess) {
                val branchedConvo = res.getOrThrow()
                _conversations.value = listOf(branchedConvo) + _conversations.value
                _currentConversationId.value = branchedConvo.id
                _messages.value = listOf(message.copy(conversationId = branchedConvo.id))
            }
        }
    }

    fun openCanvas(document: CanvasDocument) {
        _activeCanvas.value = document
    }

    fun closeCanvas() {
        _activeCanvas.value = null
    }

    fun logout() {
        authRepository.logout()
        _messages.value = emptyList()
        _conversations.value = emptyList()
        _currentConversationId.value = null
    }
}
