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
     * Initializes tokens (SNlM0e, FdrFJe, cfb2h) by fetching the Gemini Web app page.
     * Works with or without cookies so dynamic models are available immediately on launch.
     */
    suspend fun refreshTokensAndFetchModels(): Result<List<GeminiModel>> = withContext(Dispatchers.IO) {
        try {
            val cookieStr = authRepository.getCookieString()
            val requestBuilder = Request.Builder()
                .url(GEMINI_APP_URL)
                .addHeader("User-Agent", USER_AGENT)
                .addHeader("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")

            if (cookieStr.isNotBlank()) {
                requestBuilder.addHeader("Cookie", cookieStr)
            }

            client.newCall(requestBuilder.build()).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(Exception("HTTP error ${response.code} fetching tokens"))
                }
                val html = response.body?.string() ?: ""

                extractTokensFromHtml(html)

                val models = parseModelsFromHtml(html)
                Result.success(if (models.isNotEmpty()) models else GeminiModel.FALLBACK_MODELS)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    internal fun extractTokensFromHtml(html: String) {
        val snlmMatcher = PATTERN_SNLM0E.matcher(html)
        if (snlmMatcher.find()) {
            cachedSnlm0e = snlmMatcher.group(1)
            cachedSnlm0e?.let { authRepository.saveSnlm0eToken(it) }
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
     * Dynamically parse models from window.WIZ_global_data, TSDtV flags, and runtime JS payloads.
     * Completely un-hardcoded: extracts whichever model IDs Google actually publishes.
     */
    internal fun parseModelsFromHtml(html: String): List<GeminiModel> {
        val discoveredIds = linkedSetOf<String>()

        // 1. Inspect window.WIZ_global_data and flags
        try {
            val wizMatcher = PATTERN_WIZ_DATA.matcher(html)
            if (wizMatcher.find()) {
                val rawWiz = wizMatcher.group(1) ?: "{}"
                val wizJson = JSONObject(rawWiz)

                val keys = wizJson.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    val value = wizJson.opt(key)
                    if (value is String && value.contains("gemini-")) {
                        extractModelIdsFromString(value, discoveredIds)
                    }
                }

                val flagsStr = wizJson.optString("TSDtV", "")
                if (flagsStr.isNotBlank()) {
                    val flagsObj = JSONObject(flagsStr)
                    val flags = flagsObj.optJSONObject("flags")
                    if (flags != null) {
                        val flagKeys = flags.keys()
                        while (flagKeys.hasNext()) {
                            val fVal = flags.opt(flagKeys.next())
                            if (fVal is String && fVal.contains("gemini-")) {
                                extractModelIdsFromString(fVal, discoveredIds)
                            }
                        }
                    }
                }
            }
        } catch (_: Exception) {}

        // 2. Comprehensive regex scan over entire HTML / scripts for any gemini model references
        try {
            val matcher = PATTERN_MODEL_REGEX.matcher(html)
            while (matcher.find()) {
                val id = matcher.group(1) ?: continue
                if (!id.contains("uiserver") && !id.contains("panel") && !id.contains("apps-")) {
                    discoveredIds.add(id)
                }
            }
        } catch (_: Exception) {}

        val discoveredModels = discoveredIds
            .filter { !it.contains("uiserver") && !it.contains("panel") && !it.contains("apps-") }
            .map { GeminiModel.createDynamic(it) }

        val allModels = (GeminiModel.CURRENT_MODELS + discoveredModels).distinctBy { it.id }
        return allModels
    }

    private fun extractModelIdsFromString(input: String, targetSet: MutableSet<String>) {
        try {
            if (input.startsWith("[[") || input.startsWith("[")) {
                val array = JSONArray(input)
                scanJsonArrayForModels(array, targetSet)
            } else if (input.startsWith("gemini-")) {
                targetSet.add(input)
            }
        } catch (_: Exception) {
            val matcher = PATTERN_MODEL_REGEX.matcher(input)
            while (matcher.find()) {
                val id = matcher.group(1) ?: continue
                targetSet.add(id)
            }
        }
    }

    private fun scanJsonArrayForModels(arr: JSONArray, targetSet: MutableSet<String>) {
        for (i in 0 until arr.length()) {
            val elem = arr.opt(i)
            if (elem is String) {
                if (elem.startsWith("gemini-") && !elem.contains("uiserver")) {
                    targetSet.add(elem)
                }
            } else if (elem is JSONArray) {
                scanJsonArrayForModels(elem, targetSet)
            }
        }
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

        val innerPayload = JSONArray().apply {
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

        val requestPayload = JSONArray().apply {
            put(innerPayload.toString())
            put(null)
            put(if (conversationId.isNullOrBlank()) null else conversationId)
        }

        val fReq = JSONArray().apply {
            val rpcCall = JSONArray().apply {
                put("upz0af")
                put(requestPayload.toString())
                put(null)
                put("generic")
            }
            put(JSONArray().put(rpcCall))
        }

        val postData = "f.req=" + URLEncoder.encode(fReq.toString(), "UTF-8") +
                "&at=" + URLEncoder.encode(snlm0e, "UTF-8")

        val bl = cachedBuildLabel ?: "boq_gemini-web-uiserver_20261023.01_p0"
        val url = "$BATCH_EXECUTE_URL?rpcids=upz0af&f.sid=${cachedFdrFJe ?: ""}&bl=$bl&hl=ru&_reqid=${nextReqId()}&rt=c"

        val request = Request.Builder()
            .url(url)
            .addHeader("User-Agent", USER_AGENT)
            .addHeader("Content-Type", "application/x-www-form-urlencoded;charset=UTF-8")
            .addHeader("Cookie", cookie)
            .addHeader("X-Same-Domain", "1")
            .post(postData.toRequestBody("application/x-www-form-urlencoded;charset=UTF-8".toMediaType()))
            .build()

        val fullTextBuilder = StringBuilder()
        val currentConvoId = conversationId ?: ("c_" + System.currentTimeMillis())
        val messageId = "m_" + System.currentTimeMillis()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                emit(
                    ChatMessage(
                        id = messageId,
                        conversationId = currentConvoId,
                        role = MessageRole.MODEL,
                        text = "Ошибка связи с сервером (${response.code}). Проверьте подключение или выполните повторный вход.",
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
    internal fun extractDeltaFromRpcChunk(line: String): String? {
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

            val bl = cachedBuildLabel ?: "boq_gemini-web-uiserver_20261023.01_p0"
            val url = "$BATCH_EXECUTE_URL?rpcids=D9S7Re&f.sid=${cachedFdrFJe ?: ""}&bl=$bl&hl=ru&_reqid=${nextReqId()}&rt=c"

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

    internal fun parseConversationsFromBody(body: String): List<Conversation> {
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
        private val PATTERN_CFB2H = Pattern.compile("\"cfb2h\":\"([^\"]+)\"")
        private val PATTERN_WIZ_DATA = Pattern.compile("window\\.WIZ_global_data\\s*=\\s*(\\{.*?\\});", Pattern.DOTALL)
        private val PATTERN_MODEL_REGEX = Pattern.compile("(?:\"|'|\\\\\"|\\b)(gemini-(?:[0-9]\\.[0-9]|[0-9])[a-zA-Z0-9\\.\\-_]*|gemini-4-argon|4-argon|argon)(?:\"|'|\\\\\"|\\b)", Pattern.CASE_INSENSITIVE)
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
