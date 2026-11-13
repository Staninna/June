package com.denser.june.core.localai

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pins the client side of docs/LOCAL_LLM_API.md (contract v2). */
class LocalAiContractTest {
    private val json = LocalAiContract.json

    @Test
    fun `request serialises with the contract field names`() {
        val req =
            LocalAiChatRequest(
                messages = listOf(LocalAiMessage("system", "s"), LocalAiMessage("user", "u")),
                chatTemplateKwargs = mapOf("enable_thinking" to JsonPrimitive(false)),
            )
        val obj = json.parseToJsonElement(json.encodeToString(req)).jsonObject
        assertEquals("system", obj["messages"]!!.jsonArray[0].jsonObject["role"]!!.jsonPrimitive.content)
        assertFalse(obj["stream"]!!.jsonPrimitive.boolean)
        assertFalse(obj["chat_template_kwargs"]!!.jsonObject["enable_thinking"]!!.jsonPrimitive.boolean)
        // unset sampling values are omitted so the server defaults apply
        assertFalse(obj.containsKey("temperature"))
        assertFalse(obj.containsKey("top_p"))
    }

    @Test
    fun `parses the contract's response, health and error examples`() {
        val response =
            json.decodeFromString<LocalAiChatResponse>(
                """{"id":"chatcmpl-1","object":"chat.completion","created":1,"model":"Qwen3.5-4B-Q4_K_M",
                   "choices":[{"index":0,"message":{"role":"assistant","content":"Today I went out."},"finish_reason":"stop"}],
                   "usage":{"prompt_tokens":5,"completion_tokens":4,"total_tokens":9},
                   "timings":{"prompt_ms":1.0,"generation_ms":2.0,"tokens_per_second":3.0}}"""
            )
        assertEquals("Today I went out.", response.choices.single().message.content)
        assertEquals(9, response.usage!!.totalTokens)

        val health = json.decodeFromString<LocalAiHealth>("""{"status":"ok","api_version":1,"model":"m","queue_length":0}""")
        assertEquals("ok", health.status)

        val err = json.decodeFromString<LocalAiErrorResponse>("""{"error":{"message":"m","type":"server_error","code":"server_busy"}}""")
        assertEquals("server_busy", err.error.code)
    }

    @Test
    fun `pairing constants match the server`() {
        assertEquals("io.shubham0204.smollmandroid", LocalAiContract.SERVER_PACKAGE)
        assertEquals("io.shubham0204.smollmandroid.action.PAIR", LocalAiContract.ACTION_PAIR)
        assertEquals("client_name", LocalAiContract.EXTRA_CLIENT_NAME)
    }

    @Test
    fun `only loopback base urls are accepted`() {
        assertTrue(LocalAiContract.isLoopbackBaseUrl("http://127.0.0.1:8080"))
        assertTrue(LocalAiContract.isLoopbackBaseUrl("http://localhost:9000/"))
        assertFalse(LocalAiContract.isLoopbackBaseUrl("http://192.168.1.2:8080"))
        assertFalse(LocalAiContract.isLoopbackBaseUrl("https://example.com"))
        assertFalse(LocalAiContract.isLoopbackBaseUrl("http://127.0.0.1.evil.com:8080"))
    }
}
