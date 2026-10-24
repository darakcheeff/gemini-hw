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

    @Test
    fun testGemsParsing() {
        val authRepo = MockAuthRepository()
        val client = GeminiWebClient(authRepo)

        val sampleBody = """[["wrb.fr","CNgdBe","[null,null,[[\"productivity-helper\",[\"Productivity planner\",\"Stay on top of your work.\"]]]]"]]"""
        val gems = client.parseGemsFromBody(sampleBody)

        assertEquals(1, gems.size)
        assertEquals("productivity-helper", gems[0].id)
        assertEquals("Productivity planner", gems[0].name)
        assertEquals("Stay on top of your work.", gems[0].description)
    }

    @Test
    fun testNotebooksParsing() {
        val authRepo = MockAuthRepository()
        val client = GeminiWebClient(authRepo)

        val sampleBody = """[["wrb.fr","wXbhsf","[[[\"Research Notebook\",[\"doc1\",\"doc2\"],\"nb_123\"]]]"]]"""
        val notebooks = client.parseNotebooksFromBody(sampleBody)

        assertEquals(1, notebooks.size)
        assertEquals("nb_123", notebooks[0].id)
        assertEquals("Research Notebook", notebooks[0].title)
        assertEquals("Источников: 2", notebooks[0].content)
    }

    @Test
    fun testNotebooksGraphQLParsing() {
        val authRepo = MockAuthRepository()
        val client = GeminiWebClient(authRepo)

        val sampleGraphQL = """)]}'
[{
  "data": {
    "viewer": {
      "rpcs": {
        "listNotebooks": {
          "notebooks": [{
            "id": "notebooks/nb_1",
            "displayName": "китай 2026",
            "createTime": "2026-09-29T14:20:39.208434Z",
            "sourceCount": 2.0
          }, {
            "id": "notebooks/nb_2",
            "displayName": "taobao идеи",
            "createTime": "2026-09-22T13:44:18.090680Z",
            "sourceCount": 0.0
          }]
        }
      }
    }
  }
}]"""
        val notebooks = client.parseNotebooksFromGraphQL(sampleGraphQL)

        assertEquals(2, notebooks.size)
        assertEquals("notebooks/nb_1", notebooks[0].id)
        assertEquals("китай 2026", notebooks[0].title)
        assertEquals("Источников: 2", notebooks[0].content)
        assertEquals("notebooks/nb_2", notebooks[1].id)
        assertEquals("taobao идеи", notebooks[1].title)
        assertEquals("", notebooks[1].content)
    }

    @Test
    fun testMessagesParsing() {
        val authRepo = MockAuthRepository()
        val client = GeminiWebClient(authRepo)

        val sampleBody = """[["wrb.fr","hNvQHb","[[[[\"t_1\",\"turn_1\"],null,[\"Привет Gemini\"],[[[\"rc_1\",[\"Привет! Чем помочь?\"]]]]]]]"]]"""
        val messages = client.parseMessagesFromBody(sampleBody, "c_test")

        assertEquals(2, messages.size)
        assertEquals("Привет Gemini", messages[0].text)
        assertEquals(com.gemini.hw.core.model.MessageRole.USER, messages[0].role)
        assertEquals("Привет! Чем помочь?", messages[1].text)
        assertEquals(com.gemini.hw.core.model.MessageRole.MODEL, messages[1].role)
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
