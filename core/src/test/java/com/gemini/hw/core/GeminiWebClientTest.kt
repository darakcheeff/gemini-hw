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
                "thykhd":"AFWLbD12345mocktoken",
                "FdrFJe":"-1012832322517013405",
                "cfb2h":"boq_gemini-web-uiserver_20261020.01_p0"
            };</script>
            </head><body></body></html>
        """.trimIndent()

        val authRepo = MockAuthRepository()
        val client = GeminiWebClient(authRepo)

        client.extractTokensFromHtml(sampleHtml)

        assertEquals("AFWLbD12345mocktoken", client.cachedSnlm0e)
        assertEquals("-1012832322517013405", client.cachedFdrFJe)
        assertEquals("boq_gemini-web-uiserver_20261020.01_p0", client.cachedBuildLabel)
    }

    @Test
    fun testRpcModelParsing() {
        val authRepo = MockAuthRepository()
        val client = GeminiWebClient(authRepo)

        val rpcLine = """[["wrb.fr","otAQ7b","[6,null,false,null,null,null,false,null,null,null,null,null,false,false,1016,[[\"cf41b0e0dd7d53e5\",\"Flash-Lite\",\"Самые быстрые ответы\",[2,140],1,null,[],false,\"\",1,\"Flash-Lite\",\"3.5 Flash-Lite\",\"Самые быстрые ответы\",null,null,false,\"\",6,null,\"3.5 Flash-Lite\"]]]"]]"""
        val models = client.parseModelsFromRpcResponse(rpcLine)

        assertEquals(1, models.size)
        assertEquals("cf41b0e0dd7d53e5", models[0].id)
        assertEquals("3.5 Flash-Lite", models[0].displayName)
        assertEquals("Самые быстрые ответы", models[0].description)
    }

    @Test
    fun testConversationsParsing() {
        val authRepo = MockAuthRepository()
        val client = GeminiWebClient(authRepo)

        val sampleBody = """[["wrb.fr","MaZiqc","[[[\"c_1a2b3c4d\",\"Чат по разработке\"],[\"c_9e8f7a6b\",\"Написание статьи\"]]]"]]"""
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

    override fun hasValidSession(): Boolean = true

    override fun getCookieString(): String = "__Secure-1PSID=test_cookie"
}
