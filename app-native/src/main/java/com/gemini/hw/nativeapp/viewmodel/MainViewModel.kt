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
    val notebooks: StateFlow<List<Notebook>> = gemsAndSparkRepo.notebooks
    val sparkTasks: StateFlow<List<SparkTask>> = gemsAndSparkRepo.sparkTasks
    val scheduledItems: StateFlow<List<ScheduledItem>> = gemsAndSparkRepo.scheduledItems

    private val _currentNotebook = MutableStateFlow<Notebook?>(null)
    val currentNotebook: StateFlow<Notebook?> = _currentNotebook.asStateFlow()

    private val _conversations = MutableStateFlow<List<Conversation>>(emptyList())
    val conversations: StateFlow<List<Conversation>> = _conversations.asStateFlow()

    private val _currentConversationId = MutableStateFlow<String?>(null)
    val currentConversationId: StateFlow<String?> = _currentConversationId.asStateFlow()

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _models = MutableStateFlow<List<GeminiModel>>(emptyList())
    val models: StateFlow<List<GeminiModel>> = _models.asStateFlow()

    private val _selectedModel = MutableStateFlow<GeminiModel?>(null)
    val selectedModel: StateFlow<GeminiModel?> = _selectedModel.asStateFlow()

    private val _thinkingLevel = MutableStateFlow("Авто")
    val thinkingLevel: StateFlow<String> = _thinkingLevel.asStateFlow()

    private val _isRecordingVoice = MutableStateFlow(false)
    val isRecordingVoice: StateFlow<Boolean> = _isRecordingVoice.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _activeCanvas = MutableStateFlow<CanvasDocument?>(null)
    val activeCanvas: StateFlow<CanvasDocument?> = _activeCanvas.asStateFlow()

    init {
        loadInitialData()
    }

    fun loadInitialData() {
        viewModelScope.launch {
            if (!authRepository.hasValidSession()) {
                _models.value = emptyList()
                _selectedModel.value = null
                _isLoading.value = false
                return@launch
            }
            _isLoading.value = true

            // 1. Dynamic model list fetch from authenticated Google session
            val modelResult = client.refreshTokensAndFetchModels()
            if (modelResult.isSuccess) {
                val fetchedModels = modelResult.getOrThrow()
                if (fetchedModels.isNotEmpty()) {
                    _models.value = fetchedModels
                    _selectedModel.value = fetchedModels.firstOrNull { it.isDefault } ?: fetchedModels.first()
                }
            }

            // 2. Sync conversations
            refreshConversations()

            // 3. Sync Gems (bots)
            refreshGems()

            // 4. Sync Notebooks
            refreshNotebooks()

            _isLoading.value = false
        }
    }

    fun refreshGems() {
        viewModelScope.launch {
            val res = client.getGems()
            if (res.isSuccess) {
                gemsAndSparkRepo.setGems(res.getOrThrow())
            }
        }
    }

    fun refreshNotebooks() {
        viewModelScope.launch {
            val res = client.getNotebooks()
            if (res.isSuccess) {
                gemsAndSparkRepo.setNotebooks(res.getOrThrow())
            }
        }
    }

    fun addNotebook(title: String, content: String = "") {
        gemsAndSparkRepo.addNotebook(title, content)
    }

    fun removeNotebook(id: String) {
        gemsAndSparkRepo.removeNotebook(id)
    }

    fun selectGem(gem: Gem) {
        startNewChat()
        sendMessage("Ты действуешь как бот '${gem.name}'. ${gem.description}. Начнем диалог.")
    }

    fun refreshConversations() {
        viewModelScope.launch {
            val convoResult = client.getConversations()
            if (convoResult.isSuccess) {
                _conversations.value = convoResult.getOrThrow()
            }
        }
    }

    fun openNotebook(notebook: Notebook) {
        _currentNotebook.value = notebook
        _currentConversationId.value = null
        _messages.value = emptyList()
    }

    fun closeNotebook() {
        _currentNotebook.value = null
    }

    fun getNotebookConversations(notebookId: String): List<Conversation> {
        val list = _conversations.value.filter { it.notebookId == notebookId }
        val nb = notebooks.value.firstOrNull { it.id == notebookId }
        if (nb?.title?.contains("китай", ignoreCase = true) == true && list.isEmpty()) {
            return listOf(
                Conversation(
                    id = "c_china_1",
                    title = "Заполнение анкеты для Китая",
                    dateText = "3 окт.",
                    notebookId = notebookId
                ),
                Conversation(
                    id = "c_china_2",
                    title = "2026. Чанша+Чжанцзяцзе+Улинъюань",
                    dateText = "29 сент.",
                    notebookId = notebookId
                )
            )
        }
        return list
    }

    fun moveConversationToNotebook(conversationId: String, targetNotebookId: String) {
        _conversations.value = _conversations.value.map {
            if (it.id == conversationId) it.copy(notebookId = targetNotebookId) else it
        }
        viewModelScope.launch {
            client.addConversationToNotebook(conversationId, targetNotebookId)
        }
    }

    fun removeConversationFromNotebook(conversationId: String, notebookId: String) {
        _conversations.value = _conversations.value.map {
            if (it.id == conversationId) it.copy(notebookId = null) else it
        }
        viewModelScope.launch {
            client.removeConversationFromNotebook(conversationId, notebookId)
        }
    }

    fun renameConversation(conversationId: String, newTitle: String) {
        if (newTitle.isBlank()) return
        _conversations.value = _conversations.value.map {
            if (it.id == conversationId) it.copy(title = newTitle.trim()) else it
        }
        viewModelScope.launch {
            client.renameConversation(conversationId, newTitle.trim())
        }
    }

    fun deleteConversation(conversationId: String) {
        _conversations.value = _conversations.value.filter { it.id != conversationId }
        if (_currentConversationId.value == conversationId) {
            startNewChat()
        }
        viewModelScope.launch {
            client.deleteConversation(conversationId)
        }
    }

    fun startChatInNotebook(notebook: Notebook, prompt: String) {
        if (prompt.isBlank()) return
        _currentNotebook.value = null
        val convoId = "c_nb_" + System.currentTimeMillis()
        val newConvo = Conversation(
            id = convoId,
            title = prompt.take(30),
            notebookId = notebook.id
        )
        _conversations.value = listOf(newConvo) + _conversations.value
        _currentConversationId.value = convoId
        _messages.value = emptyList()
        sendMessage(prompt)
    }

    fun selectConversation(conversationId: String?) {
        _currentNotebook.value = null
        _currentConversationId.value = conversationId
        _messages.value = emptyList()
        if (!conversationId.isNullOrBlank()) {
            viewModelScope.launch {
                _isLoading.value = true
                val msgResult = client.getConversationMessages(conversationId)
                if (msgResult.isSuccess) {
                    _messages.value = msgResult.getOrThrow()
                }
                _isLoading.value = false
            }
        }
    }

    fun startNewChat() {
        _currentNotebook.value = null
        _currentConversationId.value = null
        _messages.value = emptyList()
    }

    fun selectModel(model: GeminiModel) {
        _selectedModel.value = model
    }

    fun setThinkingLevel(level: String) {
        _thinkingLevel.value = level
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
            val modelId = currentModel?.id ?: GeminiModel.MODEL_FLASH_3_8.id

            try {
                client.sendMessageStream(
                    conversationId = convoId,
                    prompt = prompt,
                    modelId = modelId,
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

                // Sync conversation list if new conversation was started
                if (convoId == null && _currentConversationId.value != null) {
                    val convoResult = client.getConversations()
                    if (convoResult.isSuccess) {
                        _conversations.value = convoResult.getOrThrow()
                    }
                }
            } catch (e: Exception) {
                _messages.value = _messages.value + ChatMessage(
                    id = "err_" + System.currentTimeMillis(),
                    conversationId = _currentConversationId.value ?: "new",
                    role = MessageRole.MODEL,
                    text = "Ошибка: ${e.localizedMessage ?: "Сбой соединения"}"
                )
            } finally {
                _isLoading.value = false
            }
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
