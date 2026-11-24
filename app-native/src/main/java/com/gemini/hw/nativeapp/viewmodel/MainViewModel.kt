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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
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
            if (res.isSuccess) {
                val id = res.getOrThrow()
                gemsAndSparkRepo.addNotebook(trimmed, content, id)
                if (content.isNotBlank()) {
                    gemsAndSparkRepo.addSourceToNotebook(id, content)
                }
                refreshNotebooks()
            } else {
                android.widget.Toast.makeText(
                    getApplication(),
                    res.exceptionOrNull()?.message ?: "Не удалось создать блокнот",
                    android.widget.Toast.LENGTH_SHORT
                ).show()
            }
            _isLoading.value = false
        }
    }

    fun addSourceToCurrentNotebook(sourceText: String) {
        val nb = _currentNotebook.value ?: return
        val trimmed = sourceText.trim()
        if (trimmed.isBlank()) return
        gemsAndSparkRepo.addSourceToNotebook(nb.id, trimmed)
        val updatedSources = (nb.sources + trimmed).distinct()
        _currentNotebook.value = nb.copy(
            sources = updatedSources,
            content = "Источников: ${updatedSources.size}"
        )
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
                _conversations.value = convoResult.getOrThrow()
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
        _currentNotebook.value?.let { loadNotebookConversations(it.id) }
    }

    private val _notebookConversations = MutableStateFlow<Map<String, List<Conversation>>>(emptyMap())
    val notebookConversations: StateFlow<Map<String, List<Conversation>>> = _notebookConversations.asStateFlow()

    private val _recentlyAttachedNotebookConvos = java.util.concurrent.ConcurrentHashMap<String, List<Conversation>>()

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
                    val serverIds = serverList.map { it.id }.toSet()
                    val pending = (_recentlyAttachedNotebookConvos[targetId] ?: emptyList())
                        .filter { it.id !in serverIds }
                    if (pending.isEmpty()) {
                        _recentlyAttachedNotebookConvos.remove(targetId)
                    } else {
                        _recentlyAttachedNotebookConvos[targetId] = pending
                    }
                    val finalList = (pending + serverList).distinctBy { it.id }
                    _notebookConversations.value = _notebookConversations.value + (targetId to finalList) + (rawId to finalList)
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
            refreshConversations()
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
        _recentlyAttachedNotebookConvos[targetNorm] = (_recentlyAttachedNotebookConvos[targetNorm] ?: emptyList())
            .filter { it.id != conversationId }
        viewModelScope.launch {
            try { client.removeConversationFromNotebook(conversationId, targetNorm) } catch (_: Exception) {}
            refreshConversations()
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
        _recentlyAttachedNotebookConvos.keys.forEach { k ->
            _recentlyAttachedNotebookConvos[k] = (_recentlyAttachedNotebookConvos[k] ?: emptyList())
                .filter { it.id != conversationId }
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

    fun reviseCanvasDocument(
        document: CanvasDocument,
        displayInstruction: String,
        fullPromptForServer: String
    ) {
        if (fullPromptForServer.isBlank()) return
        val userMsg = ChatMessage(
            id = "msg_u_" + System.currentTimeMillis(),
            conversationId = _currentConversationId.value ?: "new",
            role = MessageRole.USER,
            text = displayInstruction.trim()
        )
        _messages.value = _messages.value + userMsg
        dispatchToGemini(fullPromptForServer, emptyList(), null)
    }

    private var generationJob: Job? = null

    fun stopGeneration() {
        generationJob?.cancel()
        generationJob = null
        _isLoading.value = false
        _messages.value = _messages.value.map { msg ->
            if (msg.isStreaming) msg.copy(isStreaming = false) else msg
        }
    }

    private fun dispatchToGemini(
        prompt: String,
        attachments: List<Attachment> = emptyList(),
        targetNotebookId: String? = null,
        parentMessageId: String? = null
    ) {
        generationJob?.cancel()
        generationJob = viewModelScope.launch {
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
                }.toMutableList()

                if (currentNotebookId != null) {
                    val nbSources = gemsAndSparkRepo.getSourcesForNotebook(currentNotebookId)
                    if (nbSources.isNotEmpty()) {
                        val notesBytes = nbSources.joinToString("\n\n---\n\n").toByteArray(Charsets.UTF_8)
                        val uploadNotes = client.uploadFile(notesBytes, "notebook_notes.txt", "text/plain")
                        if (uploadNotes.isSuccess) {
                            readyAttachments.add(
                                Attachment(
                                    id = "nb_notes_${System.currentTimeMillis()}",
                                    name = "notebook_notes.txt",
                                    mimeType = "text/plain",
                                    url = uploadNotes.getOrThrow(),
                                    sizeBytes = notesBytes.size.toLong()
                                )
                            )
                        }
                    }
                }

                var lastResponseText = ""
                client.sendMessageStream(
                    conversationId = convoId,
                    prompt = prompt,
                    modelId = modelId,
                    parentMessageId = parentMessageId,
                    attachments = readyAttachments,
                    notebookId = currentNotebookId,
                    thinkingLevel = _thinkingLevel.value,
                    isCanvasMode = _isCanvasMode.value
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
                    if (_currentConversationId.value == null && responseMsg.conversationId.startsWith("c_")) {
                        _currentConversationId.value = responseMsg.conversationId
                        if (currentNotebookId != null) {
                            val targetNorm = if (currentNotebookId.startsWith("notebooks/")) currentNotebookId else "notebooks/$currentNotebookId"
                            val rawId = targetNorm.removePrefix("notebooks/")
                            _conversationNotebookMap[responseMsg.conversationId] = targetNorm
                            val newConvo = Conversation(
                                id = responseMsg.conversationId,
                                title = prompt.take(30),
                                notebookId = targetNorm,
                                dateText = "Сегодня"
                            )
                            val existingList = _notebookConversations.value[targetNorm] ?: _notebookConversations.value[rawId] ?: emptyList()
                            val updatedList = (listOf(newConvo) + existingList).distinctBy { it.id }
                            _notebookConversations.value = _notebookConversations.value + (targetNorm to updatedList) + (rawId to updatedList)
                            val prevPending = _recentlyAttachedNotebookConvos[targetNorm] ?: emptyList()
                            _recentlyAttachedNotebookConvos[targetNorm] = (listOf(newConvo) + prevPending).distinctBy { it.id }
                        }
                    }
                }

                // Pop up Canvas only when server returned an actual Canvas document or Canvas Mode returned a code block
                val lastMsg = _messages.value.lastOrNull { it.role == MessageRole.MODEL }
                if (lastMsg?.canvas != null) {
                    _activeCanvas.value = lastMsg.canvas
                } else if (_isCanvasMode.value) {
                    val activeCid = _currentConversationId.value
                    var resolvedCanvas: CanvasDocument? = null
                    if (!activeCid.isNullOrBlank() && activeCid.startsWith("c_")) {
                        val hRes = client.getConversationMessages(activeCid)
                        val hMsgs = hRes.getOrNull()
                        val hCanvas = hMsgs?.lastOrNull { it.role == MessageRole.MODEL }?.canvas
                        if (hCanvas != null) {
                            _messages.value = hMsgs
                            resolvedCanvas = hCanvas
                            _activeCanvas.value = hCanvas
                        }
                    }
                    if (resolvedCanvas == null && lastResponseText.isNotBlank()) {
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
                        }
                    }
                }

                // Sync conversation list or attach to notebook if new conversation was started
                if (convoId == null && _currentConversationId.value != null) {
                    val finalConvoId = _currentConversationId.value!!
                    if (currentNotebookId != null) {
                        val targetNb = currentNotebookId
                        viewModelScope.launch {
                            kotlinx.coroutines.delay(600)
                            val addRes = client.addConversationToNotebook(finalConvoId, targetNb)
                            if (!addRes.isSuccess) {
                                kotlinx.coroutines.delay(1000)
                                client.addConversationToNotebook(finalConvoId, targetNb)
                            }
                            loadNotebookConversations(targetNb)
                            refreshConversations()
                        }
                    } else {
                        val convoResult = client.getConversations()
                        if (convoResult.isSuccess) {
                            _conversations.value = convoResult.getOrThrow()
                        }
                    }
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
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

    val isTranscribing = MutableStateFlow(false)
    private val liveUserTranscriptions = mutableListOf<String>()

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
            val userMsgId = "live_u_" + System.currentTimeMillis()
            val userMsg = ChatMessage(
                id = userMsgId,
                conversationId = _currentConversationId.value ?: "new",
                role = MessageRole.USER,
                text = "🎤 [Голосовой разговор]"
            )
            _messages.value = _messages.value + userMsg

            try {
                val bytes = audioFile.readBytes()
                if (bytes.size < 3000) {
                    _messages.value = _messages.value.filterNot { it.id == userMsgId }
                    onError("Запись слишком короткая")
                    return@launch
                }
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

                // Transcribe user's voice turn in parallel using the already uploaded attachment
                var localTranscript = ""
                val transcribeJob = viewModelScope.launch {
                    val tRes = client.transcribeAttachment(att)
                    val tText = tRes.getOrNull()?.trim().orEmpty()
                    val lower = tText.lowercase()
                    val looksLikeRefusal = lower.contains("не прикреплена") ||
                        lower.contains("прикрепите файл") ||
                        lower.contains("no audio")
                    if (tText.isNotBlank() && !looksLikeRefusal) {
                        localTranscript = tText
                        _messages.value = _messages.value.map { m ->
                            if (m.id == userMsgId) m.copy(text = tText) else m
                        }
                    }
                }

                var fullResponse = ""
                var modelRespId: String? = null
                var modelChoiceId: String? = null
                client.sendMessageStream(
                    conversationId = _currentConversationId.value,
                    prompt = prompt,
                    modelId = _selectedModel.value?.id ?: GeminiModel.MODEL_FLASH_3_8.id,
                    attachments = listOf(att),
                    notebookId = _currentNotebook.value?.id,
                    thinkingLevel = _thinkingLevel.value
                ).collect { chunk ->
                    if (chunk.conversationId.startsWith("c_") && _currentConversationId.value != chunk.conversationId) {
                        _currentConversationId.value = chunk.conversationId
                    }
                    if (!chunk.responseId.isNullOrBlank()) modelRespId = chunk.responseId
                    if (!chunk.choiceId.isNullOrBlank()) modelChoiceId = chunk.choiceId
                    if (chunk.text.isNotBlank()) {
                        fullResponse = chunk.text
                        onStreamingChunk(fullResponse)
                    }
                }

                transcribeJob.join()
                if (localTranscript.isNotBlank()) {
                    liveUserTranscriptions.add(localTranscript)
                }

                val modelMsg = ChatMessage(
                    id = "live_m_" + System.currentTimeMillis(),
                    conversationId = _currentConversationId.value ?: "new",
                    role = MessageRole.MODEL,
                    text = fullResponse,
                    responseId = modelRespId,
                    choiceId = modelChoiceId
                )
                _messages.value = _messages.value + modelMsg
                val finalConvoId = _currentConversationId.value
                if (!finalConvoId.isNullOrBlank() && finalConvoId.startsWith("c_")) {
                    try {
                        val historyRes = client.getConversationMessages(finalConvoId)
                        if (historyRes.isSuccess && historyRes.getOrThrow().isNotEmpty()) {
                            var userTurnIdx = 0
                            _messages.value = historyRes.getOrThrow().map { msg ->
                                if (msg.role == MessageRole.USER) {
                                    val fallbackText = liveUserTranscriptions.getOrNull(userTurnIdx)
                                    userTurnIdx++
                                    if (msg.text == "🎤 [Голосовой разговор]" && !fallbackText.isNullOrBlank()) {
                                        msg.copy(text = fallbackText)
                                    } else msg
                                } else msg
                            }
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
     * Edit a previously sent message and regenerate response from parent server turn context
     */
    fun editMessage(messageId: String, newText: String) {
        val list = _messages.value.toMutableList()
        val index = list.indexOfFirst { it.id == messageId }
        if (index >= 0) {
            val prevModelMsg = list.take(index).lastOrNull { it.role == MessageRole.MODEL }
            val parentCtx = if (!prevModelMsg?.responseId.isNullOrBlank()) {
                "${prevModelMsg?.responseId}|${prevModelMsg?.choiceId.orEmpty()}"
            } else null
            val edited = list[index].copy(text = newText)
            val subList = list.subList(0, index + 1).toMutableList()
            subList[index] = edited
            _messages.value = subList
            dispatchToGemini(newText, emptyList(), parentMessageId = parentCtx)
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
            } else {
                android.widget.Toast.makeText(
                    getApplication(),
                    res.exceptionOrNull()?.message ?: "Не удалось создать ветвь чата",
                    android.widget.Toast.LENGTH_SHORT
                ).show()
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
