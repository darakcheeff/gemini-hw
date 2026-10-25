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

    private val _conversationNotebookMap = java.util.concurrent.ConcurrentHashMap<String, String>()
    private val _customConvoTitles = java.util.concurrent.ConcurrentHashMap<String, String>()

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

    private val _isCanvasMode = MutableStateFlow(false)
    val isCanvasMode: StateFlow<Boolean> = _isCanvasMode.asStateFlow()

    private val _pendingAttachments = MutableStateFlow<List<Attachment>>(emptyList())
    val pendingAttachments: StateFlow<List<Attachment>> = _pendingAttachments.asStateFlow()

    fun toggleCanvasMode() {
        _isCanvasMode.value = !_isCanvasMode.value
    }

    fun setCanvasMode(enabled: Boolean) {
        _isCanvasMode.value = enabled
    }

    fun addAttachment(attachment: Attachment) {
        _pendingAttachments.value = _pendingAttachments.value + attachment
    }

    fun removeAttachment(attachmentId: String) {
        _pendingAttachments.value = _pendingAttachments.value.filter { it.id != attachmentId }
    }

    fun clearAttachments() {
        _pendingAttachments.value = emptyList()
    }

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
        if (title.isBlank()) return
        val trimmed = title.trim()
        viewModelScope.launch {
            _isLoading.value = true
            val res = client.createNotebook(trimmed)
            val id = if (res.isSuccess) res.getOrThrow() else "nb_" + System.currentTimeMillis()
            gemsAndSparkRepo.addNotebook(trimmed, content, id)
            refreshNotebooks()
            _isLoading.value = false
        }
    }

    fun removeNotebook(id: String) {
        gemsAndSparkRepo.removeNotebook(id)
        if (_currentNotebook.value?.id == id) {
            _currentNotebook.value = null
        }
        viewModelScope.launch {
            client.deleteNotebook(id)
            refreshNotebooks()
        }
    }

    fun selectGem(gem: Gem) {
        startNewChat()
        sendMessage("Ты действуешь как бот '${gem.name}'. ${gem.description}. Начнем диалог.")
    }

    fun refreshConversations() {
        viewModelScope.launch {
            val convoResult = client.getConversations()
            if (convoResult.isSuccess) {
                val serverList = convoResult.getOrThrow()
                _conversations.value = serverList.map { serverConvo ->
                    val mappedNb = _conversationNotebookMap[serverConvo.id] ?: serverConvo.notebookId
                    val customTitle = _customConvoTitles[serverConvo.id]
                    serverConvo.copy(
                        notebookId = mappedNb,
                        title = if (!customTitle.isNullOrBlank()) customTitle else serverConvo.title
                    )
                }
            }
        }
    }

    fun openNotebook(notebook: Notebook) {
        _currentNotebook.value = notebook
        _currentConversationId.value = null
        _messages.value = emptyList()
        loadNotebookConversations(notebook.id)
    }

    fun closeNotebook() {
        _currentNotebook.value = null
        _currentConversationId.value = null
        _messages.value = emptyList()
    }

    fun openNotebookChat(notebook: Notebook, conversationId: String) {
        _currentNotebook.value = notebook
        _currentConversationId.value = conversationId
        _messages.value = emptyList()
        viewModelScope.launch {
            _isLoading.value = true
            val msgResult = client.getConversationMessages(conversationId)
            if (msgResult.isSuccess) {
                _messages.value = msgResult.getOrThrow()
            }
            _isLoading.value = false
        }
    }

    fun closeNotebookChat() {
        _currentConversationId.value = null
        _messages.value = emptyList()
    }

    private val _notebookConversations = MutableStateFlow<Map<String, List<Conversation>>>(emptyMap())
    val notebookConversations: StateFlow<Map<String, List<Conversation>>> = _notebookConversations.asStateFlow()

    private val _isRefreshingNotebook = MutableStateFlow(false)
    val isRefreshingNotebook: StateFlow<Boolean> = _isRefreshingNotebook.asStateFlow()

    fun loadNotebookConversations(notebookId: String) {
        val targetId = if (notebookId.startsWith("notebooks/")) notebookId else "notebooks/$notebookId"
        val rawId = targetId.removePrefix("notebooks/")
        viewModelScope.launch {
            _isRefreshingNotebook.value = true
            val localConvos = _conversations.value.filter {
                it.notebookId == targetId || it.notebookId == rawId ||
                _conversationNotebookMap[it.id] == targetId || _conversationNotebookMap[it.id] == rawId
            }
            val existing = _notebookConversations.value[targetId] ?: _notebookConversations.value[rawId] ?: emptyList()
            val combined = (localConvos + existing).distinctBy { it.id }
            _notebookConversations.value = _notebookConversations.value + (targetId to combined) + (rawId to combined)

            try {
                val res = client.getNotebookConversations(targetId)
                if (res.isSuccess) {
                    val serverList = res.getOrThrow()
                    val merged = (serverList + localConvos).distinctBy { it.id }
                    _notebookConversations.value = _notebookConversations.value + (targetId to merged) + (rawId to merged)
                }
            } catch (_: Exception) {}
            _isRefreshingNotebook.value = false
        }
    }

    fun getNotebookConversations(notebookId: String): List<Conversation> {
        val targetId = if (notebookId.startsWith("notebooks/")) notebookId else "notebooks/$notebookId"
        val rawId = targetId.removePrefix("notebooks/")
        val loaded = _notebookConversations.value[targetId] ?: _notebookConversations.value[rawId]
        if (loaded != null && loaded.isNotEmpty()) return loaded
        val local = _conversations.value.filter {
            it.notebookId == targetId || it.notebookId == rawId ||
            _conversationNotebookMap[it.id] == targetId || _conversationNotebookMap[it.id] == rawId
        }
        if (local.isNotEmpty()) {
            _notebookConversations.value = _notebookConversations.value + (targetId to local) + (rawId to local)
            return local
        }
        loadNotebookConversations(targetId)
        return emptyList()
    }

    fun renameNotebook(notebookId: String, newTitle: String) {
        val trimmed = newTitle.trim()
        if (trimmed.isBlank()) return
        gemsAndSparkRepo.renameNotebook(notebookId, trimmed)
        if (_currentNotebook.value?.id == notebookId) {
            _currentNotebook.value = _currentNotebook.value?.copy(title = trimmed)
        }
        viewModelScope.launch {
            try { client.renameNotebook(notebookId, trimmed) } catch (_: Exception) {}
            refreshNotebooks()
        }
    }

    fun deleteNotebook(notebookId: String) {
        val targetNorm = if (notebookId.startsWith("notebooks/")) notebookId else "notebooks/$notebookId"
        val rawId = targetNorm.removePrefix("notebooks/")
        gemsAndSparkRepo.removeNotebook(notebookId)
        gemsAndSparkRepo.removeNotebook(targetNorm)
        gemsAndSparkRepo.removeNotebook(rawId)
        if (_currentNotebook.value?.id == notebookId || _currentNotebook.value?.id == targetNorm || _currentNotebook.value?.id == rawId) {
            _currentNotebook.value = null
        }
        _notebookConversations.value = _notebookConversations.value.filterKeys {
            it != notebookId && it != targetNorm && it != rawId
        }
        viewModelScope.launch {
            try { client.deleteNotebook(notebookId) } catch (_: Exception) {}
            refreshNotebooks()
        }
    }

    fun moveConversationToNotebook(conversationId: String, targetNotebookId: String) {
        val targetNorm = if (targetNotebookId.startsWith("notebooks/")) targetNotebookId else "notebooks/$targetNotebookId"
        val rawId = targetNorm.removePrefix("notebooks/")
        _conversationNotebookMap[conversationId] = targetNorm
        var movedConvo: Conversation? = null
        _conversations.value = _conversations.value.map {
            if (it.id == conversationId) {
                val updated = it.copy(notebookId = targetNorm)
                movedConvo = updated
                updated
            } else it
        }
        if (movedConvo != null) {
            val cur = _notebookConversations.value[targetNorm] ?: _notebookConversations.value[rawId] ?: emptyList()
            val updatedList = (listOf(movedConvo!!) + cur).distinctBy { it.id }
            _notebookConversations.value = _notebookConversations.value + (targetNorm to updatedList) + (rawId to updatedList)
        }
        viewModelScope.launch {
            try { client.addConversationToNotebook(conversationId, targetNorm) } catch (_: Exception) {}
            loadNotebookConversations(targetNorm)
        }
    }

    fun removeConversationFromNotebook(conversationId: String, notebookId: String) {
        val targetNorm = if (notebookId.startsWith("notebooks/")) notebookId else "notebooks/$notebookId"
        val rawId = targetNorm.removePrefix("notebooks/")
        _conversationNotebookMap.remove(conversationId)
        _conversations.value = _conversations.value.map {
            if (it.id == conversationId) it.copy(notebookId = null) else it
        }
        _notebookConversations.value = _notebookConversations.value.mapValues { (k, list) ->
            if (k == targetNorm || k == rawId) list.filter { it.id != conversationId } else list
        }
        viewModelScope.launch {
            try { client.removeConversationFromNotebook(conversationId, targetNorm) } catch (_: Exception) {}
            loadNotebookConversations(targetNorm)
        }
    }

    fun renameConversation(conversationId: String, newTitle: String) {
        if (newTitle.isBlank()) return
        val trimmed = newTitle.trim()
        _customConvoTitles[conversationId] = trimmed
        _conversations.value = _conversations.value.map {
            if (it.id == conversationId) it.copy(title = trimmed) else it
        }
        _notebookConversations.value = _notebookConversations.value.mapValues { (_, list) ->
            list.map { if (it.id == conversationId) it.copy(title = trimmed) else it }
        }
        viewModelScope.launch {
            try { client.renameConversation(conversationId, trimmed) } catch (_: Exception) {}
            refreshConversations()
        }
    }

    fun deleteConversation(conversationId: String) {
        _conversationNotebookMap.remove(conversationId)
        _customConvoTitles.remove(conversationId)
        _conversations.value = _conversations.value.filter { it.id != conversationId }
        _notebookConversations.value = _notebookConversations.value.mapValues { (_, list) ->
            list.filter { it.id != conversationId }
        }
        if (_currentConversationId.value == conversationId) {
            _currentConversationId.value = null
            _messages.value = emptyList()
        }
        viewModelScope.launch {
            try { client.deleteConversation(conversationId) } catch (_: Exception) {}
            refreshConversations()
        }
    }

    fun startChatInNotebook(notebook: Notebook, prompt: String) {
        if (prompt.isBlank()) return
        _currentNotebook.value = notebook
        _currentConversationId.value = null
        _messages.value = emptyList()
        sendMessage(prompt, targetNotebookId = notebook.id)
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

    fun sendMessage(
        text: String,
        attachments: List<Attachment> = emptyList(),
        targetNotebookId: String? = null
    ) {
        if (text.isBlank() && attachments.isEmpty()) return
        val userMsgId = "msg_u_" + System.currentTimeMillis()
        val userMsg = ChatMessage(
            id = userMsgId,
            conversationId = _currentConversationId.value ?: "new",
            role = MessageRole.USER,
            text = text.trim(),
            attachments = attachments
        )

        _messages.value = _messages.value + userMsg
        dispatchToGemini(text, attachments, targetNotebookId)
    }

    private fun dispatchToGemini(
        prompt: String,
        attachments: List<Attachment> = emptyList(),
        targetNotebookId: String? = null
    ) {
        viewModelScope.launch {
            _isLoading.value = true
            val convoId = _currentConversationId.value
            val currentModel = _selectedModel.value
            val modelId = currentModel?.id ?: GeminiModel.MODEL_FLASH_3_8.id
            val currentNotebookId = targetNotebookId
                ?: _currentNotebook.value?.id
                ?: _conversations.value.firstOrNull { it.id == convoId }?.notebookId

            try {
                // Upload local attachments if any
                val readyAttachments = attachments.map { att ->
                    if (att.url.isNullOrBlank() && !att.localUri.isNullOrBlank()) {
                        try {
                            val uri = android.net.Uri.parse(att.localUri)
                            val bytes = getApplication<Application>().contentResolver.openInputStream(uri)?.use { it.readBytes() }
                            if (bytes != null && bytes.isNotEmpty()) {
                                val uploadRes = client.uploadFile(bytes, att.name, att.mimeType)
                                if (uploadRes.isSuccess) {
                                    att.copy(url = uploadRes.getOrThrow())
                                } else {
                                    att
                                }
                            } else {
                                att
                            }
                        } catch (e: Exception) {
                            android.util.Log.e("MainViewModel", "Failed to upload attachment ${att.name}: ${e.message}")
                            att
                        }
                    } else {
                        att
                    }
                }

                var promptToSend = prompt
                if (_isCanvasMode.value) {
                    promptToSend = "[Режим Canvas включен. Сформируй ответ в виде подробного структурированного документа или кода с блоками]\n$prompt"
                }

                var lastResponseText = ""
                client.sendMessageStream(
                    conversationId = convoId,
                    prompt = promptToSend,
                    modelId = modelId,
                    attachments = readyAttachments,
                    notebookId = currentNotebookId
                ).collect { responseMsg ->
                    lastResponseText = responseMsg.text
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
                        if (currentNotebookId != null) {
                            viewModelScope.launch {
                                client.addConversationToNotebook(responseMsg.conversationId, currentNotebookId)
                                refreshConversations()
                            }
                        }
                    }
                }

                // If message contains a Canvas document, or Canvas Mode is enabled and model returned code or structured document, pop up Canvas automatically!
                val lastMsg = _messages.value.lastOrNull { it.role == MessageRole.MODEL }
                if (lastMsg?.canvas != null) {
                    _activeCanvas.value = lastMsg.canvas
                } else if (_isCanvasMode.value && lastResponseText.isNotBlank()) {
                    val codeMatch = Regex("""```([a-zA-Z0-9_\-+]*)\n([\s\S]*?)```""").find(lastResponseText)
                    if (codeMatch != null) {
                        val lang = codeMatch.groupValues[1]
                        val code = codeMatch.groupValues[2]
                        _activeCanvas.value = CanvasDocument(
                            id = "canvas_${System.currentTimeMillis()}",
                            title = "Документ Canvas (${lang.ifBlank { "код" }})",
                            content = code,
                            language = lang,
                            version = 1
                        )
                    } else if (lastResponseText.length > 200) {
                        _activeCanvas.value = CanvasDocument(
                            id = "canvas_${System.currentTimeMillis()}",
                            title = "Документ Canvas",
                            content = lastResponseText,
                            language = "markdown",
                            version = 1
                        )
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
                    val att = Attachment(
                        id = "att_voice_" + System.currentTimeMillis(),
                        name = "voice_recording.m4a",
                        mimeType = "audio/mp4"
                    )
                    dispatchToGemini("Ответь на голосовое сообщение", listOf(att))
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

    val isTranscribing = MutableStateFlow(false)

    fun transcribeVoiceAudio(file: java.io.File, onResult: (String) -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            isTranscribing.value = true
            try {
                val bytes = file.readBytes()
                val result = client.transcribeAudio(bytes, "audio/mp4")
                isTranscribing.value = false
                if (result.isSuccess) {
                    onResult(result.getOrNull() ?: "")
                } else {
                    onError(result.exceptionOrNull()?.message ?: "Ошибка транскрибации")
                }
            } catch (e: Exception) {
                isTranscribing.value = false
                onError(e.message ?: "Ошибка")
            } finally {
                file.delete()
            }
        }
    }

    fun sendLiveAudioMessage(
        audioFile: java.io.File,
        prompt: String = "Отвечай кратко, емко и разговорно, как собеседник в реальном времени.",
        onStreamingChunk: (String) -> Unit,
        onDone: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            val userMsg = ChatMessage(
                id = "live_u_" + System.currentTimeMillis(),
                conversationId = _currentConversationId.value ?: "new",
                role = MessageRole.USER,
                text = "🎤 [Голосовой разговор]"
            )
            _messages.value = _messages.value + userMsg

            try {
                val bytes = audioFile.readBytes()
                val uploadRes = client.uploadFile(bytes, "live_voice.m4a", "audio/mp4")
                if (uploadRes.isFailure) {
                    onError(uploadRes.exceptionOrNull()?.message ?: "Не удалось отправить аудио")
                    return@launch
                }
                val ref = uploadRes.getOrNull() ?: run {
                    onError("Пустой ответ от сервера")
                    return@launch
                }
                val att = Attachment(
                    id = java.util.UUID.randomUUID().toString(),
                    name = "live_voice.m4a",
                    mimeType = "audio/mp4",
                    url = ref,
                    sizeBytes = bytes.size.toLong()
                )

                var fullResponse = ""
                client.sendMessageStream(
                    conversationId = _currentConversationId.value,
                    prompt = prompt,
                    modelId = _selectedModel.value?.id ?: "default",
                    attachments = listOf(att),
                    notebookId = _currentNotebook.value?.id
                ).collect { chunk ->
                    if (chunk.conversationId.isNotBlank() && _currentConversationId.value != chunk.conversationId) {
                        _currentConversationId.value = chunk.conversationId
                    }
                    if (chunk.text.isNotBlank()) {
                        fullResponse = chunk.text
                        onStreamingChunk(fullResponse)
                    }
                }
                val modelMsg = ChatMessage(
                    id = "live_m_" + System.currentTimeMillis(),
                    conversationId = _currentConversationId.value ?: "new",
                    role = MessageRole.MODEL,
                    text = fullResponse
                )
                _messages.value = _messages.value + modelMsg
                val finalConvoId = _currentConversationId.value
                if (!finalConvoId.isNullOrBlank()) {
                    try {
                        val historyRes = client.getConversationMessages(finalConvoId)
                        if (historyRes.isSuccess && historyRes.getOrThrow().isNotEmpty()) {
                            _messages.value = historyRes.getOrThrow()
                        }
                    } catch (_: Exception) {}
                }
                refreshConversations()
                onDone(fullResponse)
            } catch (e: Exception) {
                onError(e.message ?: "Ошибка")
            } finally {
                audioFile.delete()
            }
        }
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
            dispatchToGemini(newText, emptyList())
        }
    }

    /**
     * Branch dialogue into a new chat starting from this message, preserving preceding history.
     */
    fun createBranchInNewChat(message: ChatMessage) {
        val currentList = _messages.value
        val targetIndex = currentList.indexOfFirst { it.id == message.id }
        val historyToCopy = if (targetIndex >= 0) {
            currentList.subList(0, targetIndex + 1)
        } else {
            listOf(message)
        }

        viewModelScope.launch {
            val convoId = _currentConversationId.value ?: return@launch
            val originalTitle = _conversations.value.firstOrNull { it.id == convoId }?.title ?: "Диалог"
            val res = client.createBranchInNewChat(convoId, message.id)
            if (res.isSuccess) {
                val returnedConvo = res.getOrThrow()
                val branchTitle = if (returnedConvo.title.isNotBlank() && returnedConvo.title != "Ветвь чата") {
                    returnedConvo.title
                } else {
                    "$originalTitle (ветвь)"
                }
                val branchedConvo = returnedConvo.copy(title = branchTitle)
                _conversations.value = listOf(branchedConvo) + _conversations.value
                _currentConversationId.value = branchedConvo.id
                _messages.value = historyToCopy.map { it.copy(conversationId = branchedConvo.id) }
                refreshConversations()
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
