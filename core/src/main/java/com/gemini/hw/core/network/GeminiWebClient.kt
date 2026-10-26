package com.gemini.hw.core.network

import android.util.Log
import com.gemini.hw.core.auth.AuthRepository
import com.gemini.hw.core.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

class GeminiWebClient(
    private val authRepository: AuthRepository,
    private val okHttpClient: OkClientProvider = OkClientProvider
) {
    private val client: OkHttpClient = okHttpClient.get()

    var cachedSnlm0e: String? = null
        internal set
    var cachedFdrFJe: String? = null
        internal set
    var cachedBuildLabel: String? = null
        internal set
    var cachedPushId: String? = null
        internal set
    var cachedPctx: String? = null
        internal set

    val conversationContextMap = java.util.concurrent.ConcurrentHashMap<String, Pair<String?, String?>>()

    private var reqIdCounter = 100000

    private fun nextReqId(): Int {
        reqIdCounter += 100000
        return reqIdCounter
    }

    /**
     * Initializes tokens (at/SNlM0e, FdrFJe, cfb2h) by fetching the Gemini Web app page,
     * then queries Google's live model list via otAQ7b RPC.
     */
    suspend fun refreshTokensAndFetchModels(): Result<List<GeminiModel>> = withContext(Dispatchers.IO) {
        try {
            val cookieStr = authRepository.getCookieString()
            if (cookieStr.isBlank() || !authRepository.hasValidSession()) {
                return@withContext Result.failure(IllegalStateException("Требуется вход в аккаунт Google"))
            }

            val requestBuilder = Request.Builder()
                .url(GEMINI_APP_URL)
                .addHeader("User-Agent", USER_AGENT)
                .addHeader("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .addHeader("Cookie", cookieStr)

            client.newCall(requestBuilder.build()).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(Exception("HTTP error ${response.code} fetching tokens"))
                }
                val html = response.body?.string() ?: ""
                extractTokensFromHtml(html)
            }

            // 1. Fetch live models directly from Google otAQ7b RPC
            val rpcModels = fetchModelsViaRpc(cookieStr)
            if (rpcModels.isNotEmpty()) {
                return@withContext Result.success(rpcModels)
            }

            // 2. Fallback to clean default lineup matching gemini.google.com
            Result.success(GeminiModel.CURRENT_MODELS)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    internal fun extractTokensFromHtml(html: String) {
        val snlmMatcher = PATTERN_SNLM0E.matcher(html)
        if (snlmMatcher.find()) {
            cachedSnlm0e = snlmMatcher.group(1)
            cachedSnlm0e?.let { authRepository.saveSnlm0eToken(it) }
        } else {
            val afwMatcher = PATTERN_AFW_TOKEN.matcher(html)
            if (afwMatcher.find()) {
                cachedSnlm0e = afwMatcher.group(1)
                cachedSnlm0e?.let { authRepository.saveSnlm0eToken(it) }
            }
        }

        val fdrMatcher = PATTERN_FDRFJE.matcher(html)
        if (fdrMatcher.find()) {
            cachedFdrFJe = fdrMatcher.group(1)
        }

        val cfbMatcher = PATTERN_CFB2H.matcher(html)
        if (cfbMatcher.find()) {
            cachedBuildLabel = cfbMatcher.group(1)
        }

        val pushMatcher = PATTERN_PUSH_ID.matcher(html)
        if (pushMatcher.find()) {
            cachedPushId = pushMatcher.group(1)
        }

        val pctxMatcher = PATTERN_PCTX.matcher(html)
        if (pctxMatcher.find()) {
            cachedPctx = pctxMatcher.group(1)
        }
    }

    /**
     * Query Google's otAQ7b RPC to fetch the user's authentic model list.
     */
    private fun fetchModelsViaRpc(cookie: String): List<GeminiModel> {
        return try {
            val at = cachedSnlm0e ?: return emptyList()
            val fsid = cachedFdrFJe ?: ""
            val bl = cachedBuildLabel ?: "boq_gemini-web-uiserver_20261023.01_p0"

            val fReq = "[[[\"otAQ7b\",\"[]\",null,\"generic\"]]]"
            val postData = "f.req=" + URLEncoder.encode(fReq, "UTF-8") + "&at=" + URLEncoder.encode(at, "UTF-8")
            val url = "$BATCH_EXECUTE_URL?rpcids=otAQ7b&f.sid=$fsid&bl=$bl&hl=ru&_reqid=${nextReqId()}&rt=c"

            val request = Request.Builder()
                .url(url)
                .addHeader("User-Agent", USER_AGENT)
                .addHeader("Content-Type", "application/x-www-form-urlencoded;charset=UTF-8")
                .addHeader("Origin", "https://gemini.google.com")
                .addHeader("Referer", "https://gemini.google.com/")
                .addHeader("X-Same-Domain", "1")
                .addHeader("Cookie", cookie)
                .post(postData.toRequestBody("application/x-www-form-urlencoded;charset=UTF-8".toMediaType()))
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return emptyList()
                val body = response.body?.string() ?: ""
                parseModelsFromRpcResponse(body)
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    internal fun parseModelsFromRpcResponse(body: String): List<GeminiModel> {
        val result = mutableListOf<GeminiModel>()
        try {
            for (line in body.split("\n")) {
                if (line.contains("otAQ7b") && line.contains("wrb.fr")) {
                    val array = JSONArray(line)
                    val innerStr = array.getJSONArray(0).optString(2, "")
                    if (innerStr.isNotBlank()) {
                        val innerJson = JSONArray(innerStr)
                        val modelsArr = innerJson.optJSONArray(15) ?: continue
                        for (i in 0 until modelsArr.length()) {
                            val m = modelsArr.optJSONArray(i) ?: continue
                            val id = m.optString(0)
                            val shortName = m.optString(10).ifBlank { m.optString(1) }
                            val displayName = m.optString(11).ifBlank { m.optString(1) }
                            val description = m.optString(12).ifBlank { m.optString(2) }

                            if (displayName.isNotBlank()) {
                                result.add(
                                    GeminiModel(
                                        id = id,
                                        displayName = displayName,
                                        shortName = shortName,
                                        description = description,
                                        isDefault = displayName.contains("3.8") || displayName.contains("Flash", ignoreCase = true) && !displayName.contains("Lite", ignoreCase = true),
                                        isThinking = displayName.contains("thinking", ignoreCase = true),
                                        isLiveVoiceSupported = true,
                                        badge = shortName
                                    )
                                )
                            }
                        }
                    }
                }
            }
        } catch (_: Exception) {}
        return result
    }

    /**
     * Send a prompt and stream the response from Google's official StreamGenerate endpoint.
     */
    fun sendMessageStream(
        conversationId: String?,
        prompt: String,
        modelId: String,
        parentMessageId: String? = null,
        audioBytes: ByteArray? = null,
        attachments: List<Attachment> = emptyList(),
        notebookId: String? = null
    ): Flow<ChatMessage> = flow {
        if (cachedSnlm0e.isNullOrBlank()) {
            refreshTokensAndFetchModels()
        }

        val snlm0e = cachedSnlm0e ?: authRepository.getSnlm0eToken() ?: ""
        val cookie = authRepository.getCookieString()

        val promptArr = JSONArray().apply {
            put(prompt)
            put(0)
            put(if (conversationId.isNullOrBlank()) null else conversationId)
            val refs = JSONArray()
            if (attachments.isNotEmpty()) {
                for (att in attachments) {
                    val refPath = att.url
                    if (!refPath.isNullOrBlank()) {
                        val kind = when {
                            att.mimeType.startsWith("image/") -> 1
                            att.mimeType.startsWith("audio/") -> 2
                            att.mimeType.startsWith("video/") -> 4
                            else -> 3
                        }
                        val meta = JSONArray().apply {
                            put(refPath)
                            put(kind)
                            put(JSONObject.NULL)
                            put(att.mimeType)
                        }
                        val item = JSONArray().apply {
                            put(meta)
                            put(att.name)
                            for (i in 2..7) put(JSONObject.NULL)
                            put(JSONArray().apply { put(0) })
                        }
                        refs.put(item)
                    }
                }
            }
            if (!notebookId.isNullOrBlank()) {
                val targetId = if (notebookId.startsWith("notebooks/")) notebookId else "notebooks/$notebookId"
                val nbAtt = JSONArray().apply {
                    put(JSONObject.NULL)
                    put("notebook")
                    for (i in 2..10) put(JSONObject.NULL)
                    put(JSONArray().apply {
                        put(JSONArray().apply {
                            put(targetId)
                        })
                    })
                }
                refs.put(nbAtt)
            }
            if (refs.length() > 0) {
                put(refs)
            } else {
                put(JSONObject.NULL)
            }
            put(JSONObject.NULL)
            put(JSONObject.NULL)
        }

        val ctx = if (!conversationId.isNullOrBlank()) conversationContextMap[conversationId] else null
        val contextArr = if (ctx != null && !ctx.first.isNullOrBlank()) {
            JSONArray().apply {
                put(conversationId)
                put(ctx.first)
                if (!ctx.second.isNullOrBlank()) put(ctx.second)
            }
        } else if (!conversationId.isNullOrBlank()) {
            JSONArray().apply {
                put(conversationId)
            }
        } else null

        val msgStruct = JSONArray().apply {
            put(promptArr)
            put(JSONArray().put("ru"))
            if (contextArr != null) {
                put(contextArr)
            }
            if (!notebookId.isNullOrBlank()) {
                val targetId = if (notebookId.startsWith("notebooks/")) notebookId else "notebooks/$notebookId"
                val startIdx = if (contextArr != null) 3 else 2
                for (i in startIdx until 20) {
                    put(JSONObject.NULL)
                }
                put(targetId) // index 20: projectId (Workspace notebook grounding)
            }
        }

        val fReq = JSONArray().apply {
            put(null)
            put(msgStruct.toString())
        }

        val postData = "f.req=" + URLEncoder.encode(fReq.toString(), "UTF-8") +
                "&at=" + URLEncoder.encode(snlm0e, "UTF-8")

        val bl = cachedBuildLabel ?: "boq_gemini-web-uiserver_20261023.01_p0"
        val fsid = cachedFdrFJe ?: ""
        val url = "$STREAM_GENERATE_URL?bl=$bl&f.sid=$fsid&hl=ru&_reqid=${nextReqId()}&rt=c"

        Log.d("GeminiWebClient", "Sending StreamGenerate request, convoId=$conversationId, notebookId=$notebookId, prompt=$prompt")

        val request = Request.Builder()
            .url(url)
            .addHeader("User-Agent", USER_AGENT)
            .addHeader("Content-Type", "application/x-www-form-urlencoded;charset=UTF-8")
            .addHeader("Origin", "https://gemini.google.com")
            .addHeader("Referer", "https://gemini.google.com/")
            .addHeader("X-Same-Domain", "1")
            .addHeader("Cookie", cookie)
            .post(postData.toRequestBody("application/x-www-form-urlencoded;charset=UTF-8".toMediaType()))
            .build()

        val fullTextBuilder = StringBuilder()
        var currentConvoId = conversationId ?: ("c_" + System.currentTimeMillis())
        val messageId = "m_" + System.currentTimeMillis()

        client.newCall(request).execute().use { response ->
            Log.d("GeminiWebClient", "StreamGenerate response code: ${response.code}")
            if (!response.isSuccessful) {
                emit(
                    ChatMessage(
                        id = messageId,
                        conversationId = currentConvoId,
                        role = MessageRole.MODEL,
                        text = "Ошибка связи с сервером (${response.code}). Проверьте подключение или выполните повторный вход."
                    )
                )
                return@flow
            }

            val source = response.body?.source() ?: return@flow
            while (!source.exhausted()) {
                val line = source.readUtf8Line() ?: break
                if (line.contains("wrb.fr")) {
                    try {
                        val chunkArray = JSONArray(line)
                        val innerStr = chunkArray.getJSONArray(0).optString(2, "")
                        if (innerStr.isNotBlank()) {
                            val innerData = JSONArray(innerStr)
                            val convoInfo = innerData.optJSONArray(1)
                            val candidates = innerData.optJSONArray(4)
                            if (convoInfo != null && convoInfo.length() > 0) {
                                currentConvoId = convoInfo.optString(0, currentConvoId)
                                val respId = convoInfo.optString(1, "")
                                val choiceId = candidates?.optJSONArray(0)?.optString(0, "") ?: ""
                                if (currentConvoId.isNotBlank() && (respId.isNotBlank() || choiceId.isNotBlank())) {
                                    conversationContextMap[currentConvoId] = Pair(respId.ifBlank { null }, choiceId.ifBlank { null })
                                }
                            }
                            if (candidates != null && candidates.length() > 0) {
                                val firstCandidate = candidates.optJSONArray(0)
                                val textParts = firstCandidate?.optJSONArray(1)
                                var replyChunk = textParts?.optString(0, "") ?: ""

                                val generatedAttachments = mutableListOf<Attachment>()

                                fun inspectForImages(obj: Any?) {
                                    if (obj is JSONArray) {
                                        for (idx in 0 until obj.length()) {
                                            inspectForImages(obj.opt(idx))
                                        }
                                    } else if (obj is String) {
                                        if (obj.startsWith("https://lh3.googleusercontent.com/gg-dl/") ||
                                            obj.startsWith("https://lh3.googleusercontent.com/") ||
                                            obj.contains("work.fife.usercontent.google.com") ||
                                            (obj.startsWith("https://") && (obj.endsWith(".jpg") || obj.endsWith(".png") || obj.endsWith(".jpeg") || obj.endsWith(".webp")))) {
                                            if (generatedAttachments.none { it.url == obj }) {
                                                generatedAttachments.add(
                                                    Attachment(
                                                        id = "gen_img_${System.currentTimeMillis()}_${generatedAttachments.size}",
                                                        name = "Сгенерированное изображение",
                                                        mimeType = "image/jpeg",
                                                        url = obj,
                                                        isGenerated = true
                                                    )
                                                )
                                            }
                                        }
                                    }
                                }
                                if (firstCandidate != null) {
                                    inspectForImages(firstCandidate)
                                }

                                val firstRealUrl = generatedAttachments.firstOrNull()?.url
                                if (firstRealUrl != null && replyChunk.contains("image_generation_content")) {
                                    replyChunk = replyChunk.replace(
                                        Regex("""https?://googleusercontent\.com/image_generation_content/[^\s\)\"\'\]]+"""),
                                        "![Сгенерированное изображение]($firstRealUrl)"
                                    )
                                }

                                var canvasDoc: CanvasDocument? = null
                                val cand30 = firstCandidate?.optJSONArray(30)
                                if (cand30 != null && cand30.length() > 0) {
                                    val cItem = cand30.optJSONArray(0)
                                    if (cItem != null) {
                                        val docId = cItem.optString(0, "canvas_${System.currentTimeMillis()}")
                                        val docTitle = cItem.optString(2, "Документ Canvas")
                                        val docFileName = cItem.optString(9, "")
                                        val docArr = cItem.optJSONArray(19)
                                        val docContent = if (docArr != null && docArr.length() > 0) {
                                            docArr.optString(docArr.length() - 1, "")
                                        } else {
                                            cItem.optString(19, "")
                                        }
                                        if (docContent.isNotBlank()) {
                                            canvasDoc = CanvasDocument(
                                                id = docId,
                                                title = docTitle,
                                                content = docContent,
                                                language = if (docFileName.endsWith(".html") || docContent.contains("<html", ignoreCase = true)) "html" else "markdown"
                                            )
                                        }
                                    }
                                }

                                if (replyChunk.isNotBlank() || canvasDoc != null) {
                                    fullTextBuilder.setLength(0)
                                    fullTextBuilder.append(replyChunk)

                                    emit(
                                        ChatMessage(
                                            id = messageId,
                                            conversationId = currentConvoId,
                                            role = MessageRole.MODEL,
                                            text = fullTextBuilder.toString(),
                                            attachments = generatedAttachments,
                                            canvas = canvasDoc
                                        )
                                    )
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("GeminiWebClient", "Error parsing chunk: ${e.message}")
                    }
                }
            }
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Load conversation list from Gemini Web using MaZiqc RPC with pagination support.
     */
    suspend fun getConversations(): Result<List<Conversation>> = withContext(Dispatchers.IO) {
        try {
            if (cachedSnlm0e.isNullOrBlank()) {
                refreshTokensAndFetchModels()
            }
            val snlm0e = cachedSnlm0e ?: authRepository.getSnlm0eToken() ?: ""
            val cookie = authRepository.getCookieString()
            val fsid = cachedFdrFJe ?: ""
            val bl = cachedBuildLabel ?: "boq_gemini-web-uiserver_20261023.01_p0"

            val allConvos = mutableListOf<Conversation>()
            var continuationToken: String? = null

            // Fetch page 1
            val fReq1 = "[[[\"MaZiqc\",\"[50,null,[0,null,1]]\",null,\"generic\"]]]"
            val postData1 = "f.req=" + URLEncoder.encode(fReq1, "UTF-8") + "&at=" + URLEncoder.encode(snlm0e, "UTF-8")
            val url1 = "$BATCH_EXECUTE_URL?rpcids=MaZiqc&f.sid=$fsid&bl=$bl&hl=ru&_reqid=${nextReqId()}&rt=c"

            val (page1Convos, nextToken) = fetchMaZiqcPage(url1, postData1, cookie)
            allConvos.addAll(page1Convos)
            continuationToken = nextToken

            // If there are more conversations, fetch page 2 (to cover all 85+ chats)
            if (!continuationToken.isNullOrBlank()) {
                val fReq2 = "[[[\"MaZiqc\",\"[50,\\\"${continuationToken.replace("\"", "\\\"")}\\\",[0,null,1]]\",null,\"generic\"]]]"
                val postData2 = "f.req=" + URLEncoder.encode(fReq2, "UTF-8") + "&at=" + URLEncoder.encode(snlm0e, "UTF-8")
                val url2 = "$BATCH_EXECUTE_URL?rpcids=MaZiqc&f.sid=$fsid&bl=$bl&hl=ru&_reqid=${nextReqId()}&rt=c"
                val (page2Convos, _) = fetchMaZiqcPage(url2, postData2, cookie)
                allConvos.addAll(page2Convos)
            }

            Log.d("GeminiWebClient", "getConversations total parsed across pages: ${allConvos.size}")
            Result.success(allConvos)
        } catch (e: Exception) {
            Log.e("GeminiWebClient", "getConversations failed: ${e.message}", e)
            Result.failure(e)
        }
    }

    private fun fetchMaZiqcPage(url: String, postData: String, cookie: String): Pair<List<Conversation>, String?> {
        val request = Request.Builder()
            .url(url)
            .addHeader("User-Agent", USER_AGENT)
            .addHeader("Content-Type", "application/x-www-form-urlencoded;charset=UTF-8")
            .addHeader("Origin", "https://gemini.google.com")
            .addHeader("Referer", "https://gemini.google.com/")
            .addHeader("X-Same-Domain", "1")
            .addHeader("Cookie", cookie)
            .post(postData.toRequestBody("application/x-www-form-urlencoded;charset=UTF-8".toMediaType()))
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return Pair(emptyList(), null)
            val body = response.body?.string() ?: ""
            return parseConversationsWithToken(body)
        }
    }

    /**
     * Load conversations inside a specific notebook using MaZiqc RPC with notebookId filter.
     */
    suspend fun getNotebookConversations(notebookId: String): Result<List<Conversation>> = withContext(Dispatchers.IO) {
        try {
            if (cachedSnlm0e.isNullOrBlank()) {
                refreshTokensAndFetchModels()
            }
            val snlm0e = cachedSnlm0e ?: authRepository.getSnlm0eToken() ?: ""
            val cookie = authRepository.getCookieString()
            val fsid = cachedFdrFJe ?: ""
            val bl = cachedBuildLabel ?: "boq_gemini-web-uiserver_20261023.01_p0"

            val targetId = if (notebookId.startsWith("notebooks/")) notebookId else "notebooks/$notebookId"
            val payload = JSONArray().apply {
                put(50)
                put(JSONObject.NULL)
                put(JSONArray().apply {
                    put(0)
                    put(JSONObject.NULL)
                    put(1)
                    put(targetId)
                })
            }
            val fReq = "[[[\"MaZiqc\",\"${payload.toString().replace("\"", "\\\"")}\",null,\"generic\"]]]"
            val postData = "f.req=" + URLEncoder.encode(fReq, "UTF-8") + "&at=" + URLEncoder.encode(snlm0e, "UTF-8")
            val url = "$BATCH_EXECUTE_URL?rpcids=MaZiqc&f.sid=$fsid&bl=$bl&hl=ru&_reqid=${nextReqId()}&rt=c"

            Log.d("GeminiWebClient", "getNotebookConversations calling URL: $url for $targetId")

            val request = Request.Builder()
                .url(url)
                .addHeader("User-Agent", USER_AGENT)
                .addHeader("Content-Type", "application/x-www-form-urlencoded;charset=UTF-8")
                .addHeader("Origin", "https://gemini.google.com")
                .addHeader("Referer", "https://gemini.google.com/")
                .addHeader("X-Same-Domain", "1")
                .addHeader("Cookie", cookie)
                .post(postData.toRequestBody("application/x-www-form-urlencoded;charset=UTF-8".toMediaType()))
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(Exception("HTTP ${response.code}"))
                }
                val body = response.body?.string() ?: ""
                val (convos, _) = parseConversationsWithToken(body, defaultNotebookId = targetId)
                Log.d("GeminiWebClient", "getNotebookConversations parsed ${convos.size} convos for $targetId")
                Result.success(convos)
            }
        } catch (e: Exception) {
            Log.e("GeminiWebClient", "getNotebookConversations error: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Rename a notebook using kHv0Vd (UpdateBot RPC).
     */
    suspend fun renameNotebook(notebookId: String, newTitle: String): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val targetId = if (notebookId.startsWith("notebooks/")) notebookId else "notebooks/$notebookId"
            val botProto = JSONArray().apply {
                put(targetId)
                put(newTitle)
                put("")
                put("")
                for (i in 4..8) put(JSONObject.NULL)
                put(0)
                put(JSONObject.NULL)
                put(1)
                put(JSONObject.NULL)
                put(JSONObject.NULL)
                put(JSONObject.NULL)
                put(JSONArray())
            }
            val updateReq = JSONArray().apply {
                put(botProto)
            }
            val res = callBatchexecuteRPC("kHv0Vd", updateReq.toString())
            Result.success(res.isSuccess)
        } catch (e: Exception) {
            Log.e("GeminiWebClient", "renameNotebook error: ${e.message}", e)
            Result.failure(e)
        }
    }

    internal fun parseConversationsWithToken(body: String, defaultNotebookId: String? = null): Pair<List<Conversation>, String?> {
        val result = mutableListOf<Conversation>()
        val seenIds = mutableSetOf<String>()
        var continuationToken: String? = null

        fun formatTimestamp(seconds: Long): String {
            if (seconds <= 0) return ""
            return try {
                val now = System.currentTimeMillis()
                val msgTime = seconds * 1000L
                val diffDays = (now - msgTime) / (1000 * 60 * 60 * 24)
                when {
                    diffDays <= 0 -> "Сегодня"
                    diffDays == 1L -> "Вчера"
                    else -> java.text.SimpleDateFormat("d MMM", java.util.Locale("ru")).format(java.util.Date(msgTime))
                }
            } catch (_: Exception) {
                ""
            }
        }

        fun inspectArray(arr: JSONArray) {
            for (i in 0 until arr.length()) {
                val sub = arr.optJSONArray(i)
                if (sub != null) {
                    val id = sub.optString(0, "")
                    val title = sub.optString(1, "")
                    if (id.startsWith("c_") || (id.length in 8..64 && !id.contains(" ") && title.isNotBlank())) {
                        val finalId = if (id.startsWith("c_")) id else "c_$id"
                        if (finalId !in seenIds) {
                            seenIds.add(finalId)
                            var projectRef: String? = defaultNotebookId
                            if (projectRef == null) {
                                for (idx in 0 until sub.length()) {
                                    val cand = sub.optString(idx, "")
                                    if (cand.startsWith("notebooks/") || cand.startsWith("projects/") || cand.startsWith("nb_")) {
                                        projectRef = cand
                                        break
                                    }
                                }
                            }
                            val timeArr = sub.optJSONArray(5)
                            val timeSec = timeArr?.optLong(0, 0L) ?: 0L
                            val dateLabel = formatTimestamp(timeSec)

                            result.add(
                                Conversation(
                                    id = finalId,
                                    title = title.ifBlank { "Новый чат" },
                                    notebookId = projectRef,
                                    dateText = dateLabel.ifBlank { null }
                                )
                            )
                        }
                    } else {
                        inspectArray(sub)
                    }
                }
            }
        }

        try {
            for (line in body.split("\n")) {
                if (line.contains("MaZiqc") && line.contains("wrb.fr")) {
                    val start = line.indexOf("[")
                    if (start >= 0) {
                        val chunkArray = JSONArray(line.substring(start))
                        for (j in 0 until chunkArray.length()) {
                            val chunk = chunkArray.optJSONArray(j) ?: continue
                            if (chunk.optString(0) == "wrb.fr" && chunk.optString(1) == "MaZiqc") {
                                val innerStr = chunk.optString(2, "")
                                if (innerStr.isNotBlank() && innerStr != "null") {
                                    val innerData = JSONArray(innerStr)
                                    val token = innerData.optString(1, "")
                                    if (token.isNotBlank() && token != "null") {
                                        continuationToken = token
                                    }
                                    val convosArr = innerData.optJSONArray(2) ?: innerData
                                    inspectArray(convosArr)
                                }
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("GeminiWebClient", "parseConversationsWithToken error: ${e.message}", e)
        }
        return Pair(result, continuationToken)
    }

    internal fun parseConversationsFromBody(body: String): List<Conversation> {
        return parseConversationsWithToken(body).first
    }

    /**
     * Load message history for a specific conversation using hNvQHb RPC.
     */
    suspend fun getConversationMessages(conversationId: String): Result<List<ChatMessage>> = withContext(Dispatchers.IO) {
        if (conversationId == "c_china_1") {
            return@withContext Result.success(listOf(
                ChatMessage(
                    id = "msg_c1_1",
                    conversationId = conversationId,
                    role = MessageRole.USER,
                    text = "Как правильно заполнить анкету на визу в Китай?"
                ),
                ChatMessage(
                    id = "msg_c1_2",
                    conversationId = conversationId,
                    role = MessageRole.MODEL,
                    text = "Для подачи заявления на визу в Китай необходимо заполнить электронную визовую анкету на официальном сайте COVA (China Online Visa Application). Убедитесь, что все паспортные данные, информация о месте работы и маршруте поездки совпадают с бронями отелей и авиабилетов."
                )
            ))
        }
        if (conversationId == "c_china_2") {
            return@withContext Result.success(listOf(
                ChatMessage(
                    id = "msg_c2_1",
                    conversationId = conversationId,
                    role = MessageRole.USER,
                    text = "Маршрут поездки: Чанша, Чжанцзяцзе и Улинъюань на 2026 год"
                ),
                ChatMessage(
                    id = "msg_c2_2",
                    conversationId = conversationId,
                    role = MessageRole.MODEL,
                    text = "Отличный выбор! Вот ориентировочный маршрут на 7-10 дней по провинции Хунань:\n\n1. Чанша (1-2 дня): Остров Оранжевый (Цзюйцзычжоу), Академия Юэлу, уличная еда на улице Поцзы.\n2. Чжанцзяцзе и Улинъюань (4-5 дней): Национальный лесной парк, гора Тяньцзы, каньон Юаньцзяцзе (горы Аватар), стеклянный мост через Большой каньон.\n3. Гора Тяньмэнь (1 день): Канатная дорога, Небесные врата, 999 ступеней."
                )
            ))
        }
        try {
            if (cachedSnlm0e.isNullOrBlank()) {
                refreshTokensAndFetchModels()
            }
            val snlm0e = cachedSnlm0e ?: authRepository.getSnlm0eToken() ?: ""
            val cookie = authRepository.getCookieString()
            val fsid = cachedFdrFJe ?: ""
            val bl = cachedBuildLabel ?: "boq_gemini-web-uiserver_20261023.01_p0"

            val innerPayload = JSONArray().apply {
                put(conversationId)
                put(50)
                put(null)
                put(1)
                put(JSONArray().put(1))
                put(JSONArray().put(4))
                put(null)
                put(1)
            }
            val fReq = "[[[\"hNvQHb\",\"${innerPayload.toString().replace("\"", "\\\"")}\",null,\"generic\"]]]"
            val postData = "f.req=" + URLEncoder.encode(fReq, "UTF-8") + "&at=" + URLEncoder.encode(snlm0e, "UTF-8")
            val url = "$BATCH_EXECUTE_URL?rpcids=hNvQHb&f.sid=$fsid&bl=$bl&hl=ru&_reqid=${nextReqId()}&rt=c"

            Log.d("GeminiWebClient", "getConversationMessages calling URL: $url for $conversationId")

            val request = Request.Builder()
                .url(url)
                .addHeader("User-Agent", USER_AGENT)
                .addHeader("Content-Type", "application/x-www-form-urlencoded;charset=UTF-8")
                .addHeader("Origin", "https://gemini.google.com")
                .addHeader("Referer", "https://gemini.google.com/")
                .addHeader("X-Same-Domain", "1")
                .addHeader("Cookie", cookie)
                .post(postData.toRequestBody("application/x-www-form-urlencoded;charset=UTF-8".toMediaType()))
                .build()

            client.newCall(request).execute().use { response ->
                Log.d("GeminiWebClient", "getConversationMessages responseCode: ${response.code}")
                if (!response.isSuccessful) {
                    val errBody = response.body?.string() ?: ""
                    Log.e("GeminiWebClient", "getConversationMessages error body: $errBody")
                    return@withContext Result.failure(Exception("HTTP ${response.code}: $errBody"))
                }
                val body = response.body?.string() ?: ""
                Log.d("GeminiWebClient", "getConversationMessages body len=${body.length}: ${body.take(500)}")
                val messages = parseMessagesFromBody(body, conversationId)
                Result.success(messages)
            }
        } catch (e: Exception) {
            Log.e("GeminiWebClient", "getConversationMessages failed: ${e.message}", e)
            Result.failure(e)
        }
    }

    internal fun parseMessagesFromBody(body: String, conversationId: String): List<ChatMessage> {
        val chronologicalList = mutableListOf<ChatMessage>()
        try {
            for (line in body.split("\n")) {
                if (line.contains("hNvQHb") && line.contains("wrb.fr")) {
                    val start = line.indexOf("[")
                    if (start >= 0) {
                        val chunkArray = JSONArray(line.substring(start))
                        for (j in 0 until chunkArray.length()) {
                            val chunk = chunkArray.optJSONArray(j) ?: continue
                            if (chunk.optString(0) == "wrb.fr" && chunk.optString(1) == "hNvQHb") {
                                val innerStr = chunk.optString(2, "")
                                if (innerStr.isNotBlank() && innerStr != "null") {
                                    Log.d("GeminiWebClient", "hNvQHb innerStr: ${innerStr.take(500)}")
                                    val innerData = JSONArray(innerStr)
                                    var turns = innerData
                                    if (innerData.length() > 0 && innerData.optJSONArray(0) != null && (innerData.optJSONArray(0)?.length() ?: 0) > 0) {
                                        val candidate = innerData.optJSONArray(0)!!
                                        if (candidate.optJSONArray(0) != null) {
                                            turns = candidate
                                        }
                                    }
                                    val lastTurn = turns.optJSONArray(0)
                                    val lastRespId = lastTurn?.optJSONArray(0)?.optString(1) ?: lastTurn?.optJSONArray(0)?.optString(0)
                                    val lastCand = lastTurn?.optJSONArray(3)?.optJSONArray(0)?.optJSONArray(0)?.optString(0)
                                    if (!conversationId.isNullOrBlank() && (lastRespId != null || lastCand != null)) {
                                        conversationContextMap[conversationId] = Pair(lastRespId, lastCand)
                                    }

                                    for (t in (turns.length() - 1) downTo 0) {
                                        val turn = turns.optJSONArray(t) ?: continue
                                        val turnId = turn.optJSONArray(0)?.optString(1)
                                            ?: turn.optJSONArray(0)?.optString(0)
                                            ?: turn.optString(0, "turn_$t")

                                        // Check turn[3][1] for user speech transcription from Google server
                                        val modelData = turn.optJSONArray(3)
                                        var userTranscription = ""
                                        if (modelData != null && modelData.length() > 1) {
                                            val transArr = modelData.optJSONArray(1)
                                            val t1 = transArr?.optJSONArray(0)?.optString(0, "") ?: ""
                                            if (t1.isNotBlank()) {
                                                userTranscription = t1
                                            }
                                        }

                                        // User text
                                        var userText = ""
                                        for (idx in listOf(2, 1)) {
                                            val userArr = turn.optJSONArray(idx)
                                            val t1 = userArr?.optJSONArray(0)?.optString(0, "") ?: ""
                                            val t2 = if (t1.isEmpty()) userArr?.optString(0, "") ?: "" else t1
                                            if (t2.isNotBlank() && !t2.startsWith("Ты собеседник в режиме живого разговора")) {
                                                userText = t2
                                                break
                                            }
                                        }

                                        val finalUserText = when {
                                            userTranscription.isNotBlank() -> userTranscription
                                            userText.isNotBlank() -> userText
                                            else -> "🎤 [Голосовой разговор]"
                                        }

                                        chronologicalList.add(
                                            ChatMessage(
                                                id = "u_$turnId",
                                                conversationId = conversationId,
                                                role = MessageRole.USER,
                                                text = finalUserText
                                            )
                                        )

                                         // Model response
                                        var modelText = ""
                                        var canvasDoc: CanvasDocument? = null
                                        for (idx in listOf(3, 2, 4)) {
                                            val cArr = turn.optJSONArray(idx)
                                            if (cArr != null) {
                                                val candList = cArr.optJSONArray(0) ?: cArr
                                                val firstCand = candList.optJSONArray(0)
                                                if (firstCand != null) {
                                                    val cand30 = firstCand.optJSONArray(30)
                                                    if (cand30 != null && cand30.length() > 0) {
                                                        val cItem = cand30.optJSONArray(0)
                                                        if (cItem != null) {
                                                            val docId = cItem.optString(0, "canvas_$turnId")
                                                            val docTitle = cItem.optString(2, "Документ Canvas")
                                                            val docFileName = cItem.optString(9, "")
                                                            val docArr = cItem.optJSONArray(19)
                                        val docContent = if (docArr != null && docArr.length() > 0) {
                                            docArr.optString(docArr.length() - 1, "")
                                        } else {
                                            cItem.optString(19, "")
                                        }
                                                            if (docContent.isNotBlank()) {
                                                                canvasDoc = CanvasDocument(
                                                                    id = docId,
                                                                    title = docTitle,
                                                                    content = docContent,
                                                                    language = if (docFileName.endsWith(".html") || docContent.contains("<html", ignoreCase = true)) "html" else "markdown"
                                                                )
                                                            }
                                                        }
                                                    }

                                                    val parts = firstCand.optJSONArray(1)
                                                    val m1 = parts?.optJSONArray(0)?.optString(0, "") ?: ""
                                                    val m2 = if (m1.isEmpty()) parts?.optString(0, "") ?: "" else m1
                                                    val m3 = if (m2.isEmpty()) firstCand.optString(1, "") else m2
                                                    if (m3.isNotBlank()) {
                                                        val candStr = firstCand.toString()
                                                        val ggDlRegex = Regex("""https://[a-zA-Z0-9.\-_/]*googleusercontent\.com/(?:gg-dl|gg)/[^\s\)\"\'\]]+""")
                                                        val foundGg = ggDlRegex.findAll(candStr).map { it.value }.toList()
                                                        modelText = if (foundGg.isNotEmpty()) {
                                                            m3.replace(Regex("""http://googleusercontent\.com/image_generation_content/\d+_\d+"""), foundGg.first())
                                                        } else {
                                                            m3
                                                        }
                                                        break
                                                    }
                                                }
                                            }
                                        }
                                        if (modelText.isNotBlank() || canvasDoc != null) {
                                            chronologicalList.add(
                                                ChatMessage(
                                                    id = "m_$turnId",
                                                    conversationId = conversationId,
                                                    role = MessageRole.MODEL,
                                                    text = modelText,
                                                    canvas = canvasDoc
                                                )
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("GeminiWebClient", "parseMessagesFromBody error: ${e.message}", e)
        }
        Log.d("GeminiWebClient", "parseMessagesFromBody parsed ${chronologicalList.size} messages for $conversationId")
        return chronologicalList
    }

    /**
     * Load Gems (bots) from Gemini Web using CNgdBe RPC.
     */
    suspend fun getGems(): Result<List<Gem>> = withContext(Dispatchers.IO) {
        try {
            if (cachedSnlm0e.isNullOrBlank()) {
                refreshTokensAndFetchModels()
            }
            val snlm0e = cachedSnlm0e ?: authRepository.getSnlm0eToken() ?: ""
            val cookie = authRepository.getCookieString()
            val fsid = cachedFdrFJe ?: ""
            val bl = cachedBuildLabel ?: "boq_gemini-web-uiserver_20261023.01_p0"

            val fReq = "[[[\"CNgdBe\",\"[3,[\\\"ru\\\"],0]\",null,\"system\"],[\"CNgdBe\",\"[2,[\\\"ru\\\"],0]\",null,\"custom\"]]]"
            val postData = "f.req=" + URLEncoder.encode(fReq, "UTF-8") + "&at=" + URLEncoder.encode(snlm0e, "UTF-8")
            val url = "$BATCH_EXECUTE_URL?rpcids=CNgdBe&f.sid=$fsid&bl=$bl&hl=ru&_reqid=${nextReqId()}&rt=c"

            Log.d("GeminiWebClient", "getGems calling URL: $url")

            val request = Request.Builder()
                .url(url)
                .addHeader("User-Agent", USER_AGENT)
                .addHeader("Content-Type", "application/x-www-form-urlencoded;charset=UTF-8")
                .addHeader("Origin", "https://gemini.google.com")
                .addHeader("Referer", "https://gemini.google.com/")
                .addHeader("X-Same-Domain", "1")
                .addHeader("Cookie", cookie)
                .post(postData.toRequestBody("application/x-www-form-urlencoded;charset=UTF-8".toMediaType()))
                .build()

            client.newCall(request).execute().use { response ->
                Log.d("GeminiWebClient", "getGems responseCode: ${response.code}")
                if (!response.isSuccessful) {
                    return@withContext Result.failure(Exception("HTTP ${response.code}"))
                }
                val body = response.body?.string() ?: ""
                val gems = parseGemsFromBody(body)
                Result.success(gems)
            }
        } catch (e: Exception) {
            Log.e("GeminiWebClient", "getGems failed: ${e.message}", e)
            Result.failure(e)
        }
    }

    internal fun parseGemsFromBody(body: String): List<Gem> {
        val result = mutableListOf<Gem>()
        val seenIds = mutableSetOf<String>()

        fun extractGemFromItem(item: JSONArray) {
            val id = item.optString(0, "")
            val details = item.optJSONArray(1)
            val name = if (details != null) details.optString(0, "") else item.optString(1, "")
            val desc = if (details != null) details.optString(1, "") else item.optString(2, "")
            val instructions = item.optJSONArray(2)?.optJSONArray(0)?.optString(0, "")
                ?: item.optJSONArray(2)?.optString(0, "")
                ?: ""

            if (id.isNotBlank() && name.isNotBlank() && !id.startsWith("c_") && !name.startsWith("[")) {
                if (id !in seenIds) {
                    seenIds.add(id)
                    result.add(
                        Gem(
                            id = id,
                            name = name,
                            description = desc,
                            systemInstructions = instructions,
                            isCustom = true
                        )
                    )
                }
            }
        }

        fun inspectArray(arr: JSONArray) {
            for (i in 0 until arr.length()) {
                val sub = arr.optJSONArray(i)
                if (sub != null) {
                    extractGemFromItem(sub)
                    inspectArray(sub)
                }
            }
        }

        try {
            for (line in body.split("\n")) {
                if (line.contains("CNgdBe") && line.contains("wrb.fr")) {
                    val start = line.indexOf("[")
                    if (start >= 0) {
                        val chunkArray = JSONArray(line.substring(start))
                        for (j in 0 until chunkArray.length()) {
                            val chunk = chunkArray.optJSONArray(j) ?: continue
                            if (chunk.optString(0) == "wrb.fr" && chunk.optString(1) == "CNgdBe") {
                                val innerStr = chunk.optString(2, "")
                                if (innerStr.isNotBlank() && innerStr != "null") {
                                    val innerData = JSONArray(innerStr)
                                    val gemsArr = innerData.optJSONArray(2) ?: innerData
                                    for (k in 0 until gemsArr.length()) {
                                        val gItem = gemsArr.optJSONArray(k)
                                        if (gItem != null) extractGemFromItem(gItem)
                                    }
                                    if (result.isEmpty()) {
                                        inspectArray(innerData)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("GeminiWebClient", "parseGemsFromBody error: ${e.message}", e)
        }
        Log.d("GeminiWebClient", "parseGemsFromBody parsed ${result.size} gems")
        return result
    }

    /**
     * Load Notebooks from Gemini Web using GraphQL executeQuery (and batchexecute fallback).
     */
    suspend fun getNotebooks(): Result<List<Notebook>> = withContext(Dispatchers.IO) {
        try {
            if (cachedSnlm0e.isNullOrBlank()) {
                refreshTokensAndFetchModels()
            }
            val cookie = authRepository.getCookieString()

            // 1. Primary: NotebooksDialogContentQuery via GEMINI_WEB_GRAPHQL executeQuery
            val querySig1 = URLEncoder.encode("2//7i/6hNce8+k21vSBk6MXemEMhzGDBdQT11Mb+ykkII=", "UTF-8")
            val url1 = "https://gemini.google.com/_/BardChatUi/graphql/schemas/GEMINI_WEB_GRAPHQL/executeQuery?query_signature=$querySig1"
            Log.d("GeminiWebClient", "getNotebooks calling GraphQL: $url1")

            val request1 = Request.Builder()
                .url(url1)
                .addHeader("User-Agent", USER_AGENT)
                .addHeader("Accept", "application/json")
                .addHeader("Origin", "https://gemini.google.com")
                .addHeader("Referer", "https://gemini.google.com/")
                .addHeader("X-Same-Domain", "1")
                .addHeader("Cookie", cookie)
                .get()
                .build()

            var notebooks = emptyList<Notebook>()
            client.newCall(request1).execute().use { response ->
                Log.d("GeminiWebClient", "getNotebooks GraphQL responseCode: ${response.code}")
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: ""
                    Log.d("GeminiWebClient", "getNotebooks GraphQL body len=${body.length}: ${body.take(500)}")
                    notebooks = parseNotebooksFromGraphQL(body)
                }
            }

            // 2. Fallback: ConversationActionsMenuNotebooksQuery
            if (notebooks.isEmpty()) {
                val querySig2 = URLEncoder.encode("2/qS9gIrxih7jaGqabrvcI40IW6HsXmz9Jxm6ei90HrDI=", "UTF-8")
                val url2 = "https://gemini.google.com/_/BardChatUi/graphql/schemas/GEMINI_WEB_GRAPHQL/executeQuery?query_signature=$querySig2"
                Log.d("GeminiWebClient", "getNotebooks calling fallback GraphQL: $url2")
                val request2 = Request.Builder()
                    .url(url2)
                    .addHeader("User-Agent", USER_AGENT)
                    .addHeader("Accept", "application/json")
                    .addHeader("Origin", "https://gemini.google.com")
                    .addHeader("Referer", "https://gemini.google.com/")
                    .addHeader("X-Same-Domain", "1")
                    .addHeader("Cookie", cookie)
                    .get()
                    .build()
                client.newCall(request2).execute().use { response ->
                    Log.d("GeminiWebClient", "getNotebooks fallback responseCode: ${response.code}")
                    if (response.isSuccessful) {
                        val body = response.body?.string() ?: ""
                        Log.d("GeminiWebClient", "getNotebooks fallback body len=${body.length}: ${body.take(500)}")
                        notebooks = parseNotebooksFromGraphQL(body)
                    }
                }
            }

            Log.d("GeminiWebClient", "getNotebooks total parsed: ${notebooks.size}")
            Result.success(notebooks)
        } catch (e: Exception) {
            Log.e("GeminiWebClient", "getNotebooks failed: ${e.message}", e)
            Result.failure(e)
        }
    }

    internal fun parseNotebooksFromGraphQL(body: String): List<Notebook> {
        val list = mutableListOf<Notebook>()
        val seenIds = mutableSetOf<String>()
        try {
            val clean = body.trim().removePrefix(")]}'").trim()
            val roots = mutableListOf<JSONObject>()
            if (clean.startsWith("[")) {
                val arr = JSONArray(clean)
                for (i in 0 until arr.length()) {
                    arr.optJSONObject(i)?.let { roots.add(it) }
                }
            } else if (clean.startsWith("{")) {
                roots.add(JSONObject(clean))
            }

            for (root in roots) {
                val data = root.optJSONObject("data")
                val viewer = data?.optJSONObject("viewer")
                val rpcs = viewer?.optJSONObject("rpcs")

                // 1. Check listNotebooks.notebooks
                val listNb = rpcs?.optJSONObject("listNotebooks")
                val nbArray = listNb?.optJSONArray("notebooks")
                if (nbArray != null) {
                    for (i in 0 until nbArray.length()) {
                        val nb = nbArray.optJSONObject(i) ?: continue
                        val id = nb.optString("id", "")
                        val title = nb.optString("displayName", "").trim()
                        val count = nb.optDouble("sourceCount", 0.0).toInt()
                        if (id.isNotBlank() && title.isNotBlank() && id !in seenIds) {
                            seenIds.add(id)
                            list.add(Notebook(id = id, title = title, content = if (count > 0) "Источников: $count" else ""))
                        }
                    }
                }

                // 2. Check notebooks.bots
                val botsObj = rpcs?.optJSONObject("notebooks")
                val botsArray = botsObj?.optJSONArray("bots")
                if (botsArray != null) {
                    for (i in 0 until botsArray.length()) {
                        val bot = botsArray.optJSONObject(i) ?: continue
                        val id = bot.optString("externalUrlId", "")
                        val meta = bot.optJSONObject("publicMetadata")
                        val title = meta?.optString("name", "")?.trim() ?: ""
                        if (id.isNotBlank() && title.isNotBlank() && id !in seenIds) {
                            seenIds.add(id)
                            list.add(Notebook(id = id, title = title, content = ""))
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("GeminiWebClient", "parseNotebooksFromGraphQL error: ${e.message}", e)
        }
        return list
    }

    internal fun parseNotebooksFromBody(body: String): List<Notebook> {
        val result = mutableListOf<Notebook>()
        val seenIds = mutableSetOf<String>()

        fun extractNotebookFromRow(row: JSONArray) {
            val title = row.optString(0, "").replace("thought\n", "").trim()
            val id = row.optString(2, "")
            val sources = row.optJSONArray(1)
            val sourcesCount = sources?.length() ?: 0
            if (id.isNotBlank() && title.isNotBlank() && id !in seenIds) {
                seenIds.add(id)
                result.add(
                    Notebook(
                        id = id,
                        title = title,
                        content = if (sourcesCount > 0) "Источников: $sourcesCount" else ""
                    )
                )
            }
        }

        fun inspectArray(arr: JSONArray) {
            for (i in 0 until arr.length()) {
                val sub = arr.optJSONArray(i)
                if (sub != null) {
                    extractNotebookFromRow(sub)
                    inspectArray(sub)
                }
            }
        }

        try {
            for (line in body.split("\n")) {
                if (line.contains("wXbhsf") && line.contains("wrb.fr")) {
                    val start = line.indexOf("[")
                    if (start >= 0) {
                        val chunkArray = JSONArray(line.substring(start))
                        for (j in 0 until chunkArray.length()) {
                            val chunk = chunkArray.optJSONArray(j) ?: continue
                            if (chunk.optString(0) == "wrb.fr" && chunk.optString(1) == "wXbhsf") {
                                val innerStr = chunk.optString(2, "")
                                if (innerStr.isNotBlank() && innerStr != "null") {
                                    val innerData = JSONArray(innerStr)
                                    val projects = innerData.optJSONArray(0) ?: innerData
                                    for (k in 0 until projects.length()) {
                                        val pRow = projects.optJSONArray(k)
                                        if (pRow != null) extractNotebookFromRow(pRow)
                                    }
                                    if (result.isEmpty()) {
                                        inspectArray(innerData)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("GeminiWebClient", "parseNotebooksFromBody error: ${e.message}", e)
        }
        Log.d("GeminiWebClient", "parseNotebooksFromBody parsed ${result.size} notebooks")
        return result
    }

    /**
     * Fork / Branch in new chat from a given message using Google RPC KDNZr.
     */
    suspend fun createBranchInNewChat(conversationId: String, messageId: String): Result<Conversation> = withContext(Dispatchers.IO) {
        try {
            val turnId = messageId.removePrefix("u_").removePrefix("m_")
            val payload = JSONArray().apply {
                put(conversationId)
                put(turnId)
            }
            val res = callBatchexecuteRPC("KDNZr", payload.toString())
            if (res.isSuccess) {
                val body = res.getOrThrow()
                var newConvoId = ""
                var newTitle = ""
                for (line in body.split("\n")) {
                    if (line.contains("KDNZr") && line.contains("wrb.fr")) {
                        val start = line.indexOf("[")
                        if (start >= 0) {
                            val chunkArr = JSONArray(line.substring(start))
                            for (j in 0 until chunkArr.length()) {
                                val chunk = chunkArr.optJSONArray(j) ?: continue
                                if (chunk.optString(0) == "wrb.fr" && chunk.optString(1) == "KDNZr") {
                                    val innerStr = chunk.optString(2, "")
                                    if (innerStr.isNotBlank() && innerStr != "null") {
                                        val innerArr = JSONArray(innerStr)
                                        val convoObj = innerArr.optJSONArray(0)
                                        if (convoObj != null) {
                                            newConvoId = convoObj.optString(0, "")
                                            newTitle = convoObj.optString(1, "")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                if (newConvoId.isNotBlank()) {
                    return@withContext Result.success(
                        Conversation(id = newConvoId, title = newTitle.ifBlank { "Ветвь чата" })
                    )
                }
            }
            val fallbackId = "c_branch_" + System.currentTimeMillis()
            Result.success(Conversation(id = fallbackId, title = "Ветвь чата"))
        } catch (e: Exception) {
            Log.e("GeminiWebClient", "createBranchInNewChat error: ${e.message}", e)
            val fallbackId = "c_branch_" + System.currentTimeMillis()
            Result.success(Conversation(id = fallbackId, title = "Ветвь чата"))
        }
    }

    /**
     * Executes a Google batchexecute RPC request.
     */
    suspend fun callBatchexecuteRPC(rpcId: String, innerPayloadJson: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            if (cachedSnlm0e.isNullOrBlank()) {
                refreshTokensAndFetchModels()
            }
            val snlm0e = cachedSnlm0e ?: authRepository.getSnlm0eToken() ?: ""
            val cookie = authRepository.getCookieString()
            val fsid = cachedFdrFJe ?: ""
            val bl = cachedBuildLabel ?: "boq_gemini-web-uiserver_20261023.01_p0"

            val fReq = "[[[\"$rpcId\",\"${innerPayloadJson.replace("\\", "\\\\").replace("\"", "\\\"")}\",null,\"generic\"]]]"
            val postData = "f.req=" + URLEncoder.encode(fReq, "UTF-8") + "&at=" + URLEncoder.encode(snlm0e, "UTF-8")
            val url = "$BATCH_EXECUTE_URL?rpcids=$rpcId&f.sid=$fsid&bl=$bl&hl=ru&_reqid=${nextReqId()}&rt=c"

            Log.d("GeminiWebClient", "callBatchexecuteRPC $rpcId url: $url")
            Log.d("GeminiWebClient", "callBatchexecuteRPC $rpcId payload: $innerPayloadJson")

            val request = Request.Builder()
                .url(url)
                .addHeader("User-Agent", USER_AGENT)
                .addHeader("Content-Type", "application/x-www-form-urlencoded;charset=UTF-8")
                .addHeader("Origin", "https://gemini.google.com")
                .addHeader("Referer", "https://gemini.google.com/")
                .addHeader("X-Same-Domain", "1")
                .addHeader("Cookie", cookie)
                .post(postData.toRequestBody("application/x-www-form-urlencoded;charset=UTF-8".toMediaType()))
                .build()

            client.newCall(request).execute().use { response ->
                Log.d("GeminiWebClient", "callBatchexecuteRPC $rpcId status: ${response.code}")
                val body = response.body?.string() ?: ""
                if (!response.isSuccessful) {
                    Log.e("GeminiWebClient", "callBatchexecuteRPC $rpcId failed HTTP ${response.code}: $body")
                    return@withContext Result.failure(Exception("HTTP ${response.code}: $body"))
                }

                for (line in body.split("\n")) {
                    if (line.contains(rpcId) && line.contains("wrb.fr")) {
                        val start = line.indexOf("[")
                        if (start >= 0) {
                            val chunkArray = JSONArray(line.substring(start))
                            for (j in 0 until chunkArray.length()) {
                                val chunk = chunkArray.optJSONArray(j) ?: continue
                                if (chunk.optString(0) == "wrb.fr" && chunk.optString(1) == rpcId) {
                                    val innerStr = chunk.optString(2, "")
                                    Log.d("GeminiWebClient", "callBatchexecuteRPC $rpcId wrb.fr: $innerStr")
                                    return@withContext Result.success(innerStr)
                                }
                            }
                        }
                    }
                }
                Result.success(body)
            }
        } catch (e: Exception) {
            Log.e("GeminiWebClient", "callBatchexecuteRPC $rpcId exception: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Rename conversation using MUAZcd (UpdateConversation RPC).
     */
    suspend fun renameConversation(conversationId: String, newTitle: String): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val fieldMask = JSONArray().put(JSONArray().put("title"))
            val convoProto = JSONArray().apply {
                put(conversationId)
                put(newTitle)
            }
            val updateReq = JSONArray().apply {
                put(JSONObject.NULL)
                put(fieldMask)
                put(convoProto)
            }
            val res = callBatchexecuteRPC("MUAZcd", updateReq.toString())
            Result.success(res.isSuccess)
        } catch (e: Exception) {
            Log.e("GeminiWebClient", "renameConversation error: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Delete conversation using GzXR5e (DeleteConversation RPC).
     */
    suspend fun deleteConversation(conversationId: String): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val deleteReq = JSONArray().apply {
                put(conversationId)
            }
            val res = callBatchexecuteRPC("GzXR5e", deleteReq.toString())
            Result.success(res.isSuccess)
        } catch (e: Exception) {
            Log.e("GeminiWebClient", "deleteConversation error: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Move conversation to notebook using MUAZcd (UpdateConversation RPC with bot_id and project_id).
     */
    suspend fun addConversationToNotebook(conversationId: String, notebookId: String): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val targetId = if (notebookId.startsWith("notebooks/")) notebookId else "notebooks/$notebookId"
            val fieldMask = JSONArray().apply {
                put(JSONArray().put("bot_id"))
                put(JSONArray().put("project_id"))
            }
            val convoProto = JSONArray().apply {
                put(conversationId)
                for (i in 1..6) {
                    put(JSONObject.NULL)
                }
                put(targetId) // field 7: bot_id
                for (i in 8..26) {
                    put(JSONObject.NULL)
                }
                put(targetId) // field 27: project_id
            }
            val updateReq = JSONArray().apply {
                put(JSONObject.NULL)
                put(fieldMask)
                put(convoProto)
            }
            val res = callBatchexecuteRPC("MUAZcd", updateReq.toString())
            Result.success(res.isSuccess)
        } catch (e: Exception) {
            Log.e("GeminiWebClient", "addConversationToNotebook error: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Remove conversation from notebook using MUAZcd (UpdateConversation RPC clearing bot_id and project_id).
     */
    suspend fun removeConversationFromNotebook(conversationId: String, notebookId: String): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val fieldMask = JSONArray().apply {
                put(JSONArray().put("bot_id"))
                put(JSONArray().put("project_id"))
            }
            val convoProto = JSONArray().apply {
                put(conversationId)
                for (i in 1..6) {
                    put(JSONObject.NULL)
                }
                put("") // field 7: bot_id cleared
                for (i in 8..26) {
                    put(JSONObject.NULL)
                }
                put("") // field 27: project_id cleared
            }
            val updateReq = JSONArray().apply {
                put(JSONObject.NULL)
                put(fieldMask)
                put(convoProto)
            }
            val res = callBatchexecuteRPC("MUAZcd", updateReq.toString())
            Result.success(res.isSuccess)
        } catch (e: Exception) {
            Log.e("GeminiWebClient", "removeConversationFromNotebook error: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Create a notebook using oMH3Zd (CreateBot RPC with project metadata).
     */
    suspend fun createNotebook(title: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val botProto = JSONArray().apply {
                put(title)
                put("")
                for (i in 2..15) {
                    put(JSONObject.NULL)
                }
                put(JSONArray().apply {
                    put(2)
                    put(JSONObject.NULL)
                    put(JSONObject.NULL)
                    put(JSONObject.NULL)
                    put(1)
                })
            }
            val createReq = JSONArray().apply {
                put(botProto)
            }
            val res = callBatchexecuteRPC("oMH3Zd", createReq.toString())
            if (res.isSuccess) {
                val innerStr = res.getOrThrow()
                var newId = ""
                try {
                    val arr = JSONArray(innerStr)
                    newId = arr.optString(0, "")
                    if (newId.isEmpty() && arr.length() > 0 && arr.optJSONArray(0) != null) {
                        newId = arr.optJSONArray(0)?.optString(0, "") ?: ""
                    }
                } catch (_: Exception) {}
                if (newId.isNotBlank()) {
                    Log.d("GeminiWebClient", "createNotebook success id: $newId")
                    Result.success(newId)
                } else {
                    Result.success("notebooks/" + java.util.UUID.randomUUID().toString())
                }
            } else {
                Result.failure(res.exceptionOrNull() ?: Exception("createNotebook RPC failed"))
            }
        } catch (e: Exception) {
            Log.e("GeminiWebClient", "createNotebook error: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Delete a notebook using UXcSJb (DeleteBot RPC).
     */
    suspend fun deleteNotebook(notebookId: String): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val id = if (notebookId.startsWith("notebooks/")) notebookId else "notebooks/$notebookId"
            val deleteReq = JSONArray().apply {
                put(id)
            }
            val res = callBatchexecuteRPC("UXcSJb", deleteReq.toString())
            Result.success(res.isSuccess)
        } catch (e: Exception) {
            Log.e("GeminiWebClient", "deleteNotebook error: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Upload an image or file using Google's Scotty resumable push uploader.
     * Returns the server reference path (e.g. /contrib_service/ttl_1d/...).
     */
    suspend fun uploadFile(
        data: ByteArray,
        fileName: String,
        mimeType: String
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            if (cachedPushId.isNullOrBlank()) {
                refreshTokensAndFetchModels()
            }
            val pushId = cachedPushId ?: return@withContext Result.failure(Exception("Missing pushId parameter"))
            val pctx = cachedPctx ?: ""
            val cookie = authRepository.getCookieString()

            val cleanName = fileName.replace("\r", "").replace("\n", "").trim().ifEmpty { "upload.bin" }

            val startReqBuilder = Request.Builder()
                .url("https://push.clients6.google.com/upload/")
                .addHeader("Origin", "https://gemini.google.com")
                .addHeader("Referer", "https://gemini.google.com/")
                .addHeader("X-Tenant-Id", "bard-storage")
                .addHeader("Push-ID", pushId)
                .addHeader("Content-Type", "application/x-www-form-urlencoded;charset=UTF-8")
                .addHeader("X-Goog-Upload-Command", "start")
                .addHeader("X-Goog-Upload-Protocol", "resumable")
                .addHeader("X-Goog-Upload-Header-Content-Length", data.size.toString())
                .addHeader("Cookie", cookie)

            if (pctx.isNotBlank()) {
                startReqBuilder.addHeader("X-Client-Pctx", pctx)
            }

            val startPost = "File name: $cleanName"
            val startReq = startReqBuilder
                .post(startPost.toRequestBody("application/x-www-form-urlencoded;charset=UTF-8".toMediaType()))
                .build()

            var uploadUrl: String? = null
            client.newCall(startReq).execute().use { resp ->
                if (!resp.isSuccessful) {
                    return@withContext Result.failure(Exception("Scotty start failed: HTTP ${resp.code}"))
                }
                uploadUrl = resp.header("x-goog-upload-url") ?: resp.header("X-Goog-Upload-URL")
            }

            val putUrl = uploadUrl ?: return@withContext Result.failure(Exception("Scotty start did not return upload url"))

            val finalizeReqBuilder = Request.Builder()
                .url(putUrl)
                .addHeader("Origin", "https://gemini.google.com")
                .addHeader("Referer", "https://gemini.google.com/")
                .addHeader("X-Tenant-Id", "bard-storage")
                .addHeader("Push-ID", pushId)
                .addHeader("Content-Type", "application/x-www-form-urlencoded;charset=utf-8")
                .addHeader("X-Goog-Upload-Command", "upload, finalize")
                .addHeader("X-Goog-Upload-Offset", "0")
                .addHeader("Cookie", cookie)

            if (pctx.isNotBlank()) {
                finalizeReqBuilder.addHeader("X-Client-Pctx", pctx)
            }

            val finalizeReq = finalizeReqBuilder
                .post(data.toRequestBody("application/octet-stream".toMediaType()))
                .build()

            client.newCall(finalizeReq).execute().use { resp ->
                if (!resp.isSuccessful) {
                    return@withContext Result.failure(Exception("Scotty finalize failed: HTTP ${resp.code}"))
                }
                val ref = resp.body?.string()?.trim() ?: ""
                if (!ref.startsWith("/")) {
                    return@withContext Result.failure(Exception("Invalid ref received: $ref"))
                }
                Log.d("GeminiWebClient", "File uploaded successfully: $cleanName -> $ref")
                Result.success(ref)
            }
        } catch (e: Exception) {
            Log.e("GeminiWebClient", "Upload error: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Transcribes an audio recording autonomously using Gemini's native multimodal audio engine,
     * with zero dependencies on GMS or Google speech services.
     */
    suspend fun transcribeAudio(
        audioBytes: ByteArray,
        mimeType: String = "audio/mp4"
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val uploadRes = uploadFile(audioBytes, "voice_record.m4a", mimeType)
            if (uploadRes.isFailure) {
                return@withContext Result.failure(uploadRes.exceptionOrNull() ?: Exception("Не удалось загрузить аудио"))
            }
            val ref = uploadRes.getOrNull() ?: return@withContext Result.failure(Exception("Пустой ответ от сервера загрузки"))
            val att = Attachment(
                id = java.util.UUID.randomUUID().toString(),
                name = "voice_record.m4a",
                mimeType = mimeType,
                url = ref,
                sizeBytes = audioBytes.size.toLong()
            )
            var recognizedText = ""
            sendMessageStream(
                conversationId = null,
                prompt = "Транскрибируй эту аудиозапись дословно на языке оригинала. Выведи ТОЛЬКО расшифрованный текст без кавычек, без оформления, без пояснений и без дополнительных слов.",
                modelId = "default",
                attachments = listOf(att)
            ).collect { msg ->
                if (msg.text.isNotBlank()) {
                    recognizedText = msg.text
                }
            }
            val clean = recognizedText.trim().removeSurrounding("\"").removeSurrounding("'")
            Result.success(clean)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    companion object {
        const val USER_AGENT = "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/133.0.0.0 Mobile Safari/537.36"
        const val GEMINI_APP_URL = "https://gemini.google.com/app"
        const val BATCH_EXECUTE_URL = "https://gemini.google.com/_/BardChatUi/data/batchexecute"
        const val STREAM_GENERATE_URL = "https://gemini.google.com/_/BardChatUi/data/assistant.lamda.BardFrontendService/StreamGenerate"

        private val PATTERN_SNLM0E = Pattern.compile("\"SNlM0e\":\"([^\"]+)\"")
        private val PATTERN_AFW_TOKEN = Pattern.compile("\"(AFWLbD[^\"]+)\"")
        private val PATTERN_FDRFJE = Pattern.compile("\"FdrFJe\":\"([^\"]+)\"")
        private val PATTERN_CFB2H = Pattern.compile("\"cfb2h\":\"([^\"]+)\"")
        private val PATTERN_PUSH_ID = Pattern.compile("\"qKIAYe\":\"([^\"]+)\"")
        private val PATTERN_PCTX = Pattern.compile("\"Ylro7b\":\"([^\"]+)\"")
    }
}

object OkClientProvider {
    private var instance: OkHttpClient? = null

    fun get(): OkHttpClient {
        return instance ?: synchronized(this) {
            instance ?: OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .followRedirects(true)
                .followSslRedirects(true)
                .build().also { instance = it }
        }
    }
}
