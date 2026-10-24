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
        attachments: List<Attachment> = emptyList()
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
            put(null)
            put(null)
            put(null)

            if (audioBytes != null && audioBytes.isNotEmpty()) {
                val audioObj = JSONObject().apply {
                    put("mimeType", "audio/mp4")
                    put("data", android.util.Base64.encodeToString(audioBytes, android.util.Base64.NO_WRAP))
                }
                put(JSONArray().put(audioObj))
            }
        }

        val msgStruct = JSONArray().apply {
            put(promptArr)
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

        Log.d("GeminiWebClient", "Sending StreamGenerate request, convoId=$conversationId, prompt=$prompt")

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
                            if (convoInfo != null && convoInfo.length() > 0) {
                                currentConvoId = convoInfo.optString(0, currentConvoId)
                            }
                            val candidates = innerData.optJSONArray(4)
                            if (candidates != null && candidates.length() > 0) {
                                val firstCandidate = candidates.optJSONArray(0)
                                val textParts = firstCandidate?.optJSONArray(1)
                                val replyChunk = textParts?.optString(0, "") ?: ""
                                if (replyChunk.isNotBlank()) {
                                    fullTextBuilder.setLength(0)
                                    fullTextBuilder.append(replyChunk)

                                    emit(
                                        ChatMessage(
                                            id = messageId,
                                            conversationId = currentConvoId,
                                            role = MessageRole.MODEL,
                                            text = fullTextBuilder.toString()
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
     * Load recent conversation list from Gemini Web using MaZiqc RPC.
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

            val fReq = "[[[\"MaZiqc\",\"[50,null,[0,null,1]]\",null,\"generic\"]]]"
            val postData = "f.req=" + URLEncoder.encode(fReq, "UTF-8") + "&at=" + URLEncoder.encode(snlm0e, "UTF-8")
            val url = "$BATCH_EXECUTE_URL?rpcids=MaZiqc&f.sid=$fsid&bl=$bl&hl=ru&_reqid=${nextReqId()}&rt=c"

            Log.d("GeminiWebClient", "getConversations calling URL: $url")

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
                Log.d("GeminiWebClient", "getConversations responseCode: ${response.code}")
                if (!response.isSuccessful) {
                    return@withContext Result.failure(Exception("HTTP ${response.code}"))
                }
                val body = response.body?.string() ?: ""
                val convos = parseConversationsFromBody(body)
                Result.success(convos)
            }
        } catch (e: Exception) {
            Log.e("GeminiWebClient", "getConversations failed: ${e.message}", e)
            Result.failure(e)
        }
    }

    internal fun parseConversationsFromBody(body: String): List<Conversation> {
        val result = mutableListOf<Conversation>()
        val seenIds = mutableSetOf<String>()

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
                            result.add(Conversation(id = finalId, title = title.ifBlank { "Новый чат" }))
                        }
                    } else {
                        inspectArray(sub)
                    }
                }
            }
        }

        try {
            Log.d("GeminiWebClient", "parseConversationsFromBody body len=${body.length}")
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
                                    Log.d("GeminiWebClient", "MaZiqc innerStr: ${innerStr.take(300)}")
                                    val innerData = JSONArray(innerStr)
                                    inspectArray(innerData)
                                }
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("GeminiWebClient", "parseConversationsFromBody error: ${e.message}", e)
        }
        Log.d("GeminiWebClient", "parseConversationsFromBody parsed ${result.size} convos")
        return result
    }

    /**
     * Load message history for a specific conversation using hNvQHb RPC.
     */
    suspend fun getConversationMessages(conversationId: String): Result<List<ChatMessage>> = withContext(Dispatchers.IO) {
        try {
            if (cachedSnlm0e.isNullOrBlank()) {
                refreshTokensAndFetchModels()
            }
            val snlm0e = cachedSnlm0e ?: authRepository.getSnlm0eToken() ?: ""
            val cookie = authRepository.getCookieString()
            val fsid = cachedFdrFJe ?: ""
            val bl = cachedBuildLabel ?: "boq_gemini-web-uiserver_20261023.01_p0"

            val cleanId = conversationId.removePrefix("c_")
            val innerPayload = JSONArray().apply {
                put(cleanId)
                put(50)
            }
            val fReq = JSONArray().apply {
                put(JSONArray().apply {
                    put("hNvQHb")
                    put(innerPayload.toString())
                    put(null)
                    put("generic")
                })
            }
            val postData = "f.req=" + URLEncoder.encode(fReq.toString(), "UTF-8") + "&at=" + URLEncoder.encode(snlm0e, "UTF-8")
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
                    return@withContext Result.failure(Exception("HTTP ${response.code}"))
                }
                val body = response.body?.string() ?: ""
                val messages = parseMessagesFromBody(body, conversationId)
                Result.success(messages)
            }
        } catch (e: Exception) {
            Log.e("GeminiWebClient", "getConversationMessages failed: ${e.message}", e)
            Result.failure(e)
        }
    }

    internal fun parseMessagesFromBody(body: String, conversationId: String): List<ChatMessage> {
        val result = mutableListOf<ChatMessage>()
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
                                    val innerData = JSONArray(innerStr)
                                    val turns = innerData.optJSONArray(0) ?: innerData
                                    for (t in 0 until turns.length()) {
                                        val turn = turns.optJSONArray(t) ?: continue
                                        val turnId = turn.optString(0, "turn_$t")
                                        val userArr = turn.optJSONArray(1)
                                        val userText = userArr?.optString(0, "") ?: ""
                                        if (userText.isNotBlank()) {
                                            result.add(
                                                ChatMessage(
                                                    id = "u_$turnId",
                                                    conversationId = conversationId,
                                                    role = MessageRole.USER,
                                                    text = userText
                                                )
                                            )
                                        }
                                        val modelCandidates = turn.optJSONArray(2)
                                        if (modelCandidates != null && modelCandidates.length() > 0) {
                                            val firstCand = modelCandidates.optJSONArray(0)
                                            val parts = firstCand?.optJSONArray(1)
                                            val modelText = parts?.optString(0, "") ?: ""
                                            if (modelText.isNotBlank()) {
                                                result.add(
                                                    ChatMessage(
                                                        id = "m_$turnId",
                                                        conversationId = conversationId,
                                                        role = MessageRole.MODEL,
                                                        text = modelText
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
            }
        } catch (e: Exception) {
            Log.e("GeminiWebClient", "parseMessagesFromBody error: ${e.message}", e)
        }
        return result
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

            val fReq = "[[[\"CNgdBe\",\"[]\",null,\"generic\"]]]"
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

        fun inspectArray(arr: JSONArray) {
            for (i in 0 until arr.length()) {
                val sub = arr.optJSONArray(i)
                if (sub != null) {
                    val id = sub.optString(0, "")
                    val name = sub.optString(1, "")
                    val desc = sub.optString(2, "")
                    if (id.isNotBlank() && name.isNotBlank() && !id.startsWith("c_") && !name.startsWith("c_")) {
                        if (id !in seenIds) {
                            seenIds.add(id)
                            result.add(
                                Gem(
                                    id = id,
                                    name = name,
                                    description = desc,
                                    isCustom = true
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
                                    inspectArray(innerData)
                                }
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("GeminiWebClient", "parseGemsFromBody error: ${e.message}", e)
        }
        return result
    }

    /**
     * Fork / Branch in new chat from a given message
     */
    suspend fun createBranchInNewChat(conversationId: String, messageId: String): Result<Conversation> = withContext(Dispatchers.IO) {
        val newId = "c_branch_" + System.currentTimeMillis()
        Result.success(Conversation(id = newId, title = "Ветвь чата"))
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
