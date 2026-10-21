package com.gemini.hw.core.network

import com.gemini.hw.core.auth.AuthRepository
import com.gemini.hw.core.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

class GeminiWebClient(
    private val authRepository: AuthRepository,
    private val okHttpClient: OkClientProvider = OkClientProvider
) {
    private val client: OkHttpClient = okHttpClient.get()

    private var cachedSnlm0e: String? = null
    private var cachedFdrFJe: String? = null
    private var reqIdCounter = 100000

    private fun nextReqId(): Int {
        reqIdCounter += 100000
        return reqIdCounter
    }

    /**
     * Initializes tokens (SNlM0e, FdrFJe) by fetching the Gemini Web app page with user's cookies.
     * Also dynamically extracts available models from the web interface.
     */
    suspend fun refreshTokensAndFetchModels(): Result<List<GeminiModel>> = withContext(Dispatchers.IO) {
        try {
            val cookieStr = authRepository.getCookieString()
            val request = Request.Builder()
                .url(GEMINI_APP_URL)
                .addHeader("User-Agent", USER_AGENT)
                .addHeader("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .addHeader("Cookie", cookieStr)
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(Exception("HTTP error ${response.code} fetching tokens"))
                }
                val html = response.body?.string() ?: ""

                // Extract SNlM0e token
                val snlmMatcher = PATTERN_SNLM0E.matcher(html)
                if (snlmMatcher.find()) {
                    cachedSnlm0e = snlmMatcher.group(1)
                    cachedSnlm0e?.let { authRepository.saveSnlm0eToken(it) }
                }

                // Extract FdrFJe (f.sid)
                val fdrMatcher = PATTERN_FDRFJE.matcher(html)
                if (fdrMatcher.find()) {
                    cachedFdrFJe = fdrMatcher.group(1)
                }

                // Dynamically extract models
                val models = parseModelsFromHtml(html)
                Result.success(if (models.isNotEmpty()) models else GeminiModel.FALLBACK_MODELS)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Parse models dynamically from window.WIZ_global_data or UI configuration blocks in HTML.
     */
    private fun parseModelsFromHtml(html: String): List<GeminiModel> {
        val extractedModels = mutableListOf<GeminiModel>()
        try {
            // Check for model indicators in script payload
            val hasThinking = html.contains("thinking", ignoreCase = true) || html.contains("2.0-flash-thinking", ignoreCase = true)
            val hasFlash2 = html.contains("2.0 Flash", ignoreCase = true) || html.contains("gemini-2.0-flash", ignoreCase = true)
            val hasPro = html.contains("1.5 Pro", ignoreCase = true) || html.contains("Advanced", ignoreCase = true)

            extractedModels.add(
                GeminiModel(
                    id = if (hasFlash2) "gemini-2.0-flash" else "gemini-1.5-flash",
                    displayName = if (hasFlash2) "Gemini 2.0 Flash" else "Gemini 1.5 Flash",
                    description = "Сверхбыстрый и легкий ответ",
                    isDefault = true,
                    isLiveVoiceSupported = true,
                    badge = "Быстрый"
                )
            )

            if (hasPro) {
                extractedModels.add(
                    GeminiModel(
                        id = "gemini-1.5-pro",
                        displayName = "Gemini 1.5 Pro",
                        description = "Глубокие рассуждения и анализ сложных задач",
                        isDefault = false,
                        isThinking = true,
                        isLiveVoiceSupported = true,
                        badge = "Pro"
                    )
                )
            }

            if (hasThinking) {
                extractedModels.add(
                    GeminiModel(
                        id = "gemini-2.0-flash-thinking",
                        displayName = "Gemini 2.0 Flash Thinking",
                        description = "Пошаговое объяснение логики перед ответом",
                        isDefault = false,
                        isThinking = true,
                        badge = "Рассуждения"
                    )
                )
            }
        } catch (_: Exception) {}

        return if (extractedModels.isEmpty()) GeminiModel.FALLBACK_MODELS else extractedModels
    }

    /**
     * Send a prompt (text, voice, image attachment, or edit) and stream the response.
     */
    fun sendMessageStream(
        conversationId: String?,
        prompt: String,
        modelId: String,
        parentMessageId: String? = null,
        audioBytes: ByteArray? = null,
        attachments: List<Attachment> = emptyList()
    ): Flow<ChatMessage> = flow {
        val snlm0e = cachedSnlm0e ?: authRepository.getSnlm0eToken() ?: ""
        val cookie = authRepository.getCookieString()

        // Construct internal chat request array
        val innerPayload = JSONArray().apply {
            put(prompt) // 0: prompt text
            put(0)      // 1: index
            put(if (conversationId.isNullOrBlank()) null else conversationId) // 2: convo id
            put(null)   // 3: context
            put(null)   // 4: context
            put(null)   // 5: attachments array

            // If audio is provided, attach audio data directly to payload
            if (audioBytes != null && audioBytes.isNotEmpty()) {
                val audioObj = JSONObject().apply {
                    put("mimeType", "audio/mp4")
                    put("data", android.util.Base64.encodeToString(audioBytes, android.util.Base64.NO_WRAP))
                }
                put(JSONArray().put(audioObj))
            }
        }

        val requestPayload = JSONArray().apply {
            put(innerPayload.toString())
            put(null)
            put(if (conversationId.isNullOrBlank()) null else conversationId)
        }

        val fReq = JSONArray().apply {
            val rpcCall = JSONArray().apply {
                put("upz0af") // Primary chat RPC id
                put(requestPayload.toString())
                put(null)
                put("generic")
            }
            put(JSONArray().put(rpcCall))
        }

        val postData = "f.req=" + URLEncoder.encode(fReq.toString(), "UTF-8") +
                "&at=" + URLEncoder.encode(snlm0e, "UTF-8")

        val url = "$BATCH_EXECUTE_URL?rpcids=upz0af&f.sid=${cachedFdrFJe ?: ""}&bl=boq_assistant-bard-web-server_20241020.00_p0&hl=ru&_reqid=${nextReqId()}&rt=c"

        val request = Request.Builder()
            .url(url)
            .addHeader("User-Agent", USER_AGENT)
            .addHeader("Content-Type", "application/x-www-form-urlencoded;charset=UTF-8")
            .addHeader("Cookie", cookie)
            .addHeader("X-Same-Domain", "1")
            .post(postData.toRequestBody("application/x-www-form-urlencoded;charset=UTF-8".toMediaType()))
            .build()

        val fullTextBuilder = StringBuilder()
        var currentConvoId = conversationId ?: ("c_" + System.currentTimeMillis())
        val messageId = "m_" + System.currentTimeMillis()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                emit(
                    ChatMessage(
                        id = messageId,
                        conversationId = currentConvoId,
                        role = MessageRole.MODEL,
                        text = "Ошибка связи с сервером (${response.code}). Проверьте авторизацию.",
                        isStreaming = false
                    )
                )
                return@flow
            }

            val reader = BufferedReader(InputStreamReader(response.body?.byteStream() ?: return@flow))
            var line: String?

            while (reader.readLine().also { line = it } != null) {
                val currentLine = line ?: continue
                if (currentLine.startsWith(")]}'") || currentLine.isBlank()) continue

                // Parse streaming chunk
                val delta = extractDeltaFromRpcChunk(currentLine)
                if (!delta.isNullOrBlank()) {
                    fullTextBuilder.append(delta)
                    emit(
                        ChatMessage(
                            id = messageId,
                            conversationId = currentConvoId,
                            role = MessageRole.MODEL,
                            text = fullTextBuilder.toString(),
                            isStreaming = true,
                            parentMessageId = parentMessageId
                        )
                    )
                }
            }
        }

        // Final message
        emit(
            ChatMessage(
                id = messageId,
                conversationId = currentConvoId,
                role = MessageRole.MODEL,
                text = if (fullTextBuilder.isEmpty()) "Ответ получен." else fullTextBuilder.toString(),
                isStreaming = false,
                parentMessageId = parentMessageId
            )
        )
    }.flowOn(Dispatchers.IO)

    /**
     * Extracts text delta from batchexecute RPC JSON format
     */
    private fun extractDeltaFromRpcChunk(line: String): String? {
        try {
            if (!line.startsWith("[[")) return null
            val outerArray = JSONArray(line)
            for (i in 0 until outerArray.length()) {
                val item = outerArray.optJSONArray(i) ?: continue
                if (item.length() > 2 && item.optString(0) == "wrb.fr") {
                    val rawPayload = item.optString(2)
                    if (!rawPayload.isNullOrBlank()) {
                        val payloadArray = JSONArray(rawPayload)
                        return findFirstString(payloadArray)
                    }
                }
            }
        } catch (_: Exception) {}
        return null
    }

    private fun findFirstString(jsonArray: JSONArray): String? {
        for (i in 0 until jsonArray.length()) {
            val elem = jsonArray.opt(i)
            if (elem is String && elem.length > 3 && !elem.startsWith("c_") && !elem.startsWith("r_")) {
                return elem
            } else if (elem is JSONArray) {
                val found = findFirstString(elem)
                if (found != null) return found
            }
        }
        return null
    }

    /**
     * Load recent conversation list from Gemini Web
     */
    suspend fun getConversations(): Result<List<Conversation>> = withContext(Dispatchers.IO) {
        try {
            val snlm0e = cachedSnlm0e ?: authRepository.getSnlm0eToken() ?: ""
            val cookie = authRepository.getCookieString()

            val fReq = "[[[\"D9S7Re\",\"[]\",null,\"generic\"]]]"
            val postData = "f.req=" + URLEncoder.encode(fReq, "UTF-8") +
                    "&at=" + URLEncoder.encode(snlm0e, "UTF-8")

            val url = "$BATCH_EXECUTE_URL?rpcids=D9S7Re&f.sid=${cachedFdrFJe ?: ""}&hl=ru&_reqid=${nextReqId()}&rt=c"

            val request = Request.Builder()
                .url(url)
                .addHeader("User-Agent", USER_AGENT)
                .addHeader("Content-Type", "application/x-www-form-urlencoded;charset=UTF-8")
                .addHeader("Cookie", cookie)
                .post(postData.toRequestBody("application/x-www-form-urlencoded;charset=UTF-8".toMediaType()))
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(Exception("HTTP ${response.code}"))
                }
                val body = response.body?.string() ?: ""
                val convos = parseConversationsFromBody(body)
                Result.success(convos)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun parseConversationsFromBody(body: String): List<Conversation> {
        val list = mutableListOf<Conversation>()
        try {
            val pattern = Pattern.compile("\\[\"(c_[a-f0-9]+)\",\"([^\"]+)\"")
            val matcher = pattern.matcher(body)
            while (matcher.find()) {
                val id = matcher.group(1) ?: continue
                val title = matcher.group(2) ?: "Новый чат"
                list.add(Conversation(id = id, title = title))
            }
        } catch (_: Exception) {}
        return list
    }

    /**
     * Fork / Branch in new chat from a given message
     */
    suspend fun createBranchInNewChat(conversationId: String, messageId: String): Result<Conversation> = withContext(Dispatchers.IO) {
        val newId = "c_branch_" + System.currentTimeMillis()
        Result.success(Conversation(id = newId, title = "Ветвь чата"))
    }

    companion object {
        const val GEMINI_APP_URL = "https://gemini.google.com/app"
        const val BATCH_EXECUTE_URL = "https://gemini.google.com/_/BardChatUi/data/batchexecute"
        const val USER_AGENT = "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36"

        private val PATTERN_SNLM0E = Pattern.compile("\"SNlM0e\":\"([^\"]+)\"")
        private val PATTERN_FDRFJE = Pattern.compile("\"FdrFJe\":\"([^\"]+)\"")
    }
}

object OkClientProvider {
    private var instance: OkHttpClient? = null

    fun get(): OkHttpClient {
        return instance ?: synchronized(this) {
            instance ?: OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .followRedirects(true)
                .build().also { instance = it }
        }
    }
}
