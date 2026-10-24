package com.gemini.hw.core

import com.gemini.hw.core.auth.AuthRepository
import com.gemini.hw.core.network.GeminiWebClient
import org.junit.Assert.*
import org.junit.Test

class GeminiWebClientTest {

    @Test
    fun testTokenExtractionFromHtml() {
        val sampleHtml = """
            <!doctype html><html><head>
            <script>window.WIZ_global_data = {
                "SNlM0e":"mock_snlm0e_token_12345",
                "FdrFJe":"-1012832322517013405",
                "cfb2h":"boq_gemini-web-uiserver_20261020.01_p0"
            };</script>
            </head><body></body></html>
        """.trimIndent()

        val authRepo = MockAuthRepository()
        val client = GeminiWebClient(authRepo)

        client.extractTokensFromHtml(sampleHtml)

        assertEquals("mock_snlm0e_token_12345", client.cachedSnlm0e)
        assertEquals("-1012832322517013405", client.cachedFdrFJe)
        assertEquals("boq_gemini-web-uiserver_20261020.01_p0", client.cachedBuildLabel)
    }

    @Test
    fun testDynamicModelDetection() {
        val authRepo = MockAuthRepository()
        val client = GeminiWebClient(authRepo)

        val htmlWithNewModels = """
            <script>window.WIZ_global_data = {
                "45702952":"[[\"gemini-2.0-flash\",\"gemini-2.5-flash\",\"gemini-3-flash-preview\"]]"
            };</script>
            <div>Models: "gemini-1.5-pro", "gemini-2.0-flash-thinking"</div>
        """.trimIndent()
        val models = client.parseModelsFromHtml(htmlWithNewModels)

        assertTrue(models.any { it.id == "gemini-2.0-flash" })
        assertTrue(models.any { it.id == "gemini-1.5-pro" })
        assertTrue(models.any { it.id == "gemini-2.0-flash-thinking" })
        assertTrue(models.any { it.id == "gemini-2.5-flash" })
        assertTrue(models.any { it.id == "gemini-3-flash-preview" })
    }

    @Test
    fun testDeltaExtractionFromRpcChunk() {
        val authRepo = MockAuthRepository()
        val client = GeminiWebClient(authRepo)

        val chunk = """[["wrb.fr","upz0af","[\"Привет! Я готов помочь вам на устройстве Huawei.\"]"]]"""
        val delta = client.extractDeltaFromRpcChunk(chunk)

        assertEquals("Привет! Я готов помочь вам на устройстве Huawei.", delta)
    }

    @Test
    fun testConversationsParsing() {
        val authRepo = MockAuthRepository()
        val client = GeminiWebClient(authRepo)

        val sampleBody = """[null,[["c_1a2b3c4d","Чат по разработке"],["c_9e8f7a6b","Написание статьи"]]]"""
        val convos = client.parseConversationsFromBody(sampleBody)

        assertEquals(2, convos.size)
        assertEquals("c_1a2b3c4d", convos[0].id)
        assertEquals("Чат по разработке", convos[0].title)
        assertEquals("c_9e8f7a6b", convos[1].id)
        assertEquals("Написание статьи", convos[1].title)
    }
}

class MockAuthRepository : AuthRepository(null) {
    private var token: String? = null

    override fun saveSnlm0eToken(token: String) {
        this.token = token
    }

    override fun getSnlm0eToken(): String? = token

    override fun getCookieString(): String = "__Secure-1PSID=test_cookie"
}
