package com.denser.june.core.localai

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.concurrent.thread

/**
 * The cleanup flow against a small local HTTP server that behaves like the SmolChat server
 * (docs/LOCAL_LLM_API.md, contract v2): background job, status calls, and the things that go wrong.
 */
class DictationCleanupTest {

    /** Answers each request with what [handler] returns: status line code and JSON body. */
    private class TinyServer(private val handler: (method: String, path: String, body: String) -> Pair<Int, String>) {
        private val socket = ServerSocket(0)
        val baseUrl = "http://127.0.0.1:${socket.localPort}"
        val requests = CopyOnWriteArrayList<String>()

        init {
            thread(isDaemon = true) {
                while (!socket.isClosed) {
                    val client = runCatching { socket.accept() }.getOrNull() ?: break
                    thread(isDaemon = true) { runCatching { serve(client) } }
                }
            }
        }

        private fun serve(client: Socket) =
            client.use {
                val input = it.getInputStream().buffered()
                while (true) {
                    val head = StringBuilder()
                    while (!head.endsWith("\r\n\r\n")) {
                        val b = input.read()
                        if (b < 0) return
                        head.append(b.toChar())
                    }
                    val lines = head.lines()
                    val (method, path) = lines.first().split(" ")
                    val length =
                        lines.firstOrNull { l -> l.startsWith("Content-Length", ignoreCase = true) }
                            ?.substringAfter(":")?.trim()?.toInt() ?: 0
                    val body = String(ByteArray(length).also { buf -> var n = 0; while (n < length) n += input.read(buf, n, length - n) })
                    requests += "$method $path"
                    val (status, json) = handler(method, path, body)
                    val bytes = json.toByteArray()
                    it.getOutputStream().write(
                        ("HTTP/1.1 $status X\r\nContent-Type: application/json\r\nContent-Length: ${bytes.size}\r\n\r\n").toByteArray() + bytes
                    )
                    it.getOutputStream().flush()
                }
            }

        fun close() = socket.close()
    }

    private class Store(override var current: LocalAiSettings) : LocalAiStore {
        override fun setPendingJob(id: String?, fingerprint: String?) {
            current = current.copy(pendingJobId = id, pendingJobFingerprint = fingerprint.takeIf { id != null })
        }
    }

    private var server: TinyServer? = null

    @After
    fun tearDown() {
        server?.close()
    }

    private fun store(server: TinyServer) =
        Store(LocalAiSettings(enabled = true, token = "sk-local-test", baseUrl = server.baseUrl))

    private fun job(status: String, extra: String = "") =
        """{"id":"chatcmpl-1","object":"chat.completion.job","status":"$status","created":1$extra}"""

    private fun completion(text: String, finish: String = "stop") =
        """{"id":"chatcmpl-1","object":"chat.completion","created":1,"model":"m","choices":[{"index":0,"message":{"role":"assistant","content":"$text"},"finish_reason":"$finish"}],"usage":{"prompt_tokens":5,"completion_tokens":4,"total_tokens":9}}"""

    private val body = "so uh good morning world today i went to the the city with Emma"

    @Test
    fun `follows a background job through its phases to the rewrite`() = runBlocking {
        var polls = 0
        val s =
            TinyServer { method, path, requestBody ->
                when {
                    method == "POST" -> {
                        assertTrue(requestBody.contains("\"background\":true"))
                        assertTrue(requestBody.contains("\"max_tokens\":${DictationCleanup.maxTokens(body)}"))
                        assertTrue("a request id is sent", Regex("\"request_id\":\"[0-9a-f-]{36}\"").containsMatchIn(requestBody))
                        202 to job("queued", ""","queue_position":1""")
                    }
                    path.startsWith("/v1/jobs/") ->
                        when (++polls) {
                            1 -> 200 to job("loading_model")
                            2 -> 200 to job("processing_prompt", ""","progress":{"prompt_tokens":900,"prompt_tokens_processed":450}""")
                            3 -> 200 to job("generating", ""","progress":{"prompt_tokens":900,"prompt_tokens_processed":900,"completion_tokens":7,"tokens_per_second":6.5}""")
                            else -> 200 to job("completed", ""","response":${completion("Good morning, world! Today I went to the city with Emma.")}""")
                        }
                    else -> 404 to "{}"
                }
            }
                .also { server = it }
        val store = store(s)
        val seen = mutableListOf<DictationCleanup.Progress>()
        val outcome = DictationCleanup(LocalAiClient(), store).run(body, listOf("Emma")) { seen += it }

        val rewrite = outcome as DictationCleanup.Outcome.Rewrite
        assertEquals("Good morning, world! Today I went to the city with [@Emma](person:Emma).", rewrite.rewrite)
        assertEquals(
            listOf(
                DictationCleanup.Phase.Sending,
                DictationCleanup.Phase.Queued,
                DictationCleanup.Phase.LoadingModel,
                DictationCleanup.Phase.Reading,
                DictationCleanup.Phase.Writing,
            ),
            seen.map { it.phase },
        )
        assertEquals(0.5f, seen[3].fraction)
        assertEquals(6.5, seen[4].tokensPerSecond!!, 0.0)
        assertNull("the finished job is forgotten", store.current.pendingJobId)
    }

    @Test
    fun `a contract v1 server that answers with the completion still works`() = runBlocking {
        val s = TinyServer { _, _, _ -> 200 to completion("Good morning, world!") }.also { server = it }
        val outcome = DictationCleanup(LocalAiClient(), store(s)).run(body, emptyList())
        assertEquals("Good morning, world!", (outcome as DictationCleanup.Outcome.Rewrite).rewrite)
    }

    @Test
    fun `the same entry leads back to the same generation after June went away`() = runBlocking {
        val posts = CopyOnWriteArrayList<String>()
        val s =
            TinyServer { method, _, requestBody ->
                if (method == "POST") posts += requestBody
                // the server knows the id: it answers with the job that is already finished
                200 to job("completed", ""","response":${completion("Good morning, world!")}""")
            }
                .also { server = it }
        val store = store(s)
        val prompt = DictationPrompt.buildSystemPrompt(store.current.systemPromptTemplate, emptyList(), body)
        store.setPendingJob("11111111-2222-3333-4444-555555555555", DictationCleanup.fingerprint(prompt, body))

        val outcome = DictationCleanup(LocalAiClient(), store).run(body, emptyList())
        assertEquals("Good morning, world!", (outcome as DictationCleanup.Outcome.Rewrite).rewrite)
        assertEquals(1, posts.size)
        assertTrue("the stored id is used again", posts[0].contains("\"request_id\":\"11111111-2222-3333-4444-555555555555\""))
        assertTrue("the result is acknowledged", s.requests.contains("DELETE /v1/jobs/11111111-2222-3333-4444-555555555555"))
        assertNull(store.current.pendingJobId)
    }

    @Test
    fun `another entry gets a new id, and a job the server lost is started again under the same id`() = runBlocking {
        val posts = CopyOnWriteArrayList<String>()
        var gets = 0
        val s =
            TinyServer { method, _, requestBody ->
                when (method) {
                    "POST" -> {
                        posts += requestBody
                        202 to job("queued")
                    }
                    "GET" ->
                        if (++gets == 1) 404 to """{"error":{"message":"Unknown job","type":"invalid_request_error","code":"not_found"}}"""
                        else 200 to job("completed", ""","response":${completion("New.")}""")
                    else -> 200 to job("completed")
                }
            }
                .also { server = it }
        val store = store(s)
        store.setPendingJob("99999999-2222-3333-4444-555555555555", "the fingerprint of some other entry")

        val outcome = DictationCleanup(LocalAiClient(), store).run(body, emptyList())
        assertEquals("New.", (outcome as DictationCleanup.Outcome.Rewrite).rewrite)
        assertTrue("the old id belongs to another text", posts.none { it.contains("99999999") })
        assertEquals("posted again after the server lost the job", 2, posts.size)
        val ids = posts.map { Regex("\"request_id\":\"([0-9a-f-]{36})\"").find(it)!!.groupValues[1] }
        assertEquals(ids[0], ids[1])
    }

    @Test
    fun `a server that is not running keeps the id, so the next try continues the same generation`() = runBlocking {
        val port = ServerSocket(0).use { it.localPort }
        val store = Store(LocalAiSettings(enabled = true, token = "sk-local-test", baseUrl = "http://127.0.0.1:$port"))
        assertEquals(DictationCleanup.Outcome.ServerUnavailable, DictationCleanup(LocalAiClient(), store).run(body, emptyList()))
        val id = store.current.pendingJobId
        assertTrue(id != null)

        // the server is back (on the address June knows) and still has the job
        val posts = CopyOnWriteArrayList<String>()
        val s =
            TinyServer { method, _, requestBody ->
                if (method == "POST") posts += requestBody
                200 to job("completed", ""","response":${completion("Back.")}""")
            }
                .also { server = it }
        store.current = store.current.copy(baseUrl = s.baseUrl)
        val outcome = DictationCleanup(LocalAiClient(), store).run(body, emptyList())
        assertEquals("Back.", (outcome as DictationCleanup.Outcome.Rewrite).rewrite)
        assertTrue(posts.single().contains("\"request_id\":\"$id\""))
    }

    @Test
    fun `cancelling stops the job on the server`() = runBlocking {
        val s =
            TinyServer { method, _, _ ->
                when (method) {
                    "POST" -> 202 to job("queued")
                    "DELETE" -> 200 to job("cancelled")
                    else -> {
                        Thread.sleep(150)
                        200 to job("generating")
                    }
                }
            }
                .also { server = it }
        val store = store(s)
        val running = CoroutineScope(Dispatchers.Default).async { DictationCleanup(LocalAiClient(), store).run(body, emptyList()) }
        withTimeout(5_000) { while (s.requests.none { it.startsWith("GET") }) delay(10) }
        running.cancelAndJoin()
        withTimeout(5_000) { while (s.requests.none { it.startsWith("DELETE /v1/jobs/") }) delay(10) }
        assertNull(store.current.pendingJobId)
    }

    @Test
    fun `server-side problems map to the right outcome`() = runBlocking {
        fun failing(code: String) = job("failed", ""","error":{"message":"m","type":"t","code":"$code"}""")
        val cases =
            listOf(
                failing("context_length_exceeded") to DictationCleanup.Outcome.TooLong,
                failing("model_not_loaded") to DictationCleanup.Outcome.NoModel,
                job("cancelled") to DictationCleanup.Outcome.Stopped,
            )
        for ((answer, expected) in cases) {
            val s = TinyServer { method, _, _ -> if (method == "POST") 202 to job("queued") else 200 to answer }
            try {
                val store = store(s)
                assertEquals(expected, DictationCleanup(LocalAiClient(), store).run(body, emptyList()))
                assertNull(store.current.pendingJobId)
            } finally {
                s.close()
            }
        }
    }

    @Test
    fun `a full queue, a revoked token and a stopped server are reported as such`() = runBlocking {
        val busy = TinyServer { _, _, _ -> 429 to """{"error":{"message":"busy","type":"server_error","code":"server_busy"}}""" }
        assertEquals(DictationCleanup.Outcome.Busy, DictationCleanup(LocalAiClient(), store(busy)).run(body, emptyList()))
        busy.close()

        val revoked = TinyServer { _, _, _ -> 401 to """{"error":{"message":"no","type":"authentication_error","code":"unauthorized"}}""" }
        assertEquals(DictationCleanup.Outcome.NotPaired, DictationCleanup(LocalAiClient(), store(revoked)).run(body, emptyList()))
        revoked.close()

        // nothing listens on this port
        val port = ServerSocket(0).use { it.localPort }
        val gone = Store(LocalAiSettings(enabled = true, token = "sk-local-test", baseUrl = "http://127.0.0.1:$port"))
        assertEquals(DictationCleanup.Outcome.ServerUnavailable, DictationCleanup(LocalAiClient(), gone).run(body, emptyList()))

        assertEquals(DictationCleanup.Outcome.NotPaired, DictationCleanup(LocalAiClient(), Store(LocalAiSettings())).run(body, emptyList()))
    }

    @Test
    fun `too long with the safety bound is tried once more without it`() = runBlocking {
        val bodies = CopyOnWriteArrayList<String>()
        val s =
            TinyServer { method, _, requestBody ->
                if (method == "POST") {
                    bodies += requestBody
                    if (requestBody.contains("max_tokens")) {
                        400 to """{"error":{"message":"too long","type":"invalid_request_error","code":"context_length_exceeded"}}"""
                    } else {
                        202 to job("queued")
                    }
                } else {
                    200 to job("completed", ""","response":${completion("Cut", finish = "length")}""")
                }
            }
                .also { server = it }
        val outcome = DictationCleanup(LocalAiClient(), store(s)).run(body, emptyList()) as DictationCleanup.Outcome.Rewrite
        assertEquals(2, bodies.size)
        assertFalse(bodies[1].contains("max_tokens"))
        assertTrue("a cut-off answer is flagged in the review", outcome.changes.contains(DictationCleanup.CHANGE_CUT_OFF))
    }

    @Test
    fun `the token bound follows the length of the entry`() {
        assertEquals(256, DictationCleanup.maxTokens("x"))
        assertEquals(256 + 800, DictationCleanup.maxTokens("x".repeat(1200)))
        assertEquals(8192, DictationCleanup.maxTokens("x".repeat(100_000)))
        assertEquals(400, DictationCleanup.expectedTokens("x".repeat(1200)))
    }

    @Test
    fun `the model sees simple tags and the review gets June's codes back`() = runBlocking {
        val sent = CopyOnWriteArrayList<String>()
        val s =
            TinyServer { method, _, requestBody ->
                if (method == "POST") {
                    sent += requestBody
                    202 to job("queued")
                } else {
                    200 to job("completed", ""","progress":{"prompt_tokens":600},"response":${completion("Out with @Emma.\\n\\n#carnaval")}""")
                }
            }
                .also { server = it }
        val entry = "so uh out with [@Emma](person:Emma)\n\n[#carnaval](topic:carnaval) "
        val outcome = DictationCleanup(LocalAiClient(), store(s)).run(entry, listOf("Emma")) as DictationCleanup.Outcome.Rewrite
        assertFalse("no link codes go to the model", sent.single().contains("person:") || sent.single().contains("topic:"))
        assertTrue(sent.single().contains("@Emma") && sent.single().contains("#carnaval"))
        assertTrue("the topic rule is only there because the entry has a tag", sent.single().contains("topic tags"))
        assertEquals("Out with [@Emma](person:Emma).\n\n[#carnaval](topic:carnaval)", outcome.rewrite)
        assertEquals(entry, outcome.original)
    }

    @Test
    fun `the write bar is measured against the entry's share of the prompt`() = runBlocking {
        var polls = 0
        val s =
            TinyServer { method, _, _ ->
                when {
                    method == "POST" -> 202 to job("queued")
                    method == "GET" && ++polls == 1 ->
                        200 to job("generating", ""","progress":{"prompt_tokens":1000,"prompt_tokens_processed":1000,"completion_tokens":20}""")
                    else -> 200 to job("completed", ""","response":${completion("Done.")}""")
                }
            }
                .also { server = it }
        val store = store(s)
        val seen = mutableListOf<DictationCleanup.Progress>()
        DictationCleanup(LocalAiClient(), store).run(body, emptyList()) { seen += it }
        val writing = seen.single { it.phase == DictationCleanup.Phase.Writing }
        val prompt = DictationPrompt.buildSystemPrompt(store.current.systemPromptTemplate, emptyList(), body)
        val expected = (1000.0 * body.length / (prompt.length + body.length)).toInt()
        assertEquals(expected, writing.expectedTokens)
        assertEquals(20f / expected, writing.fraction!!, 0.001f)
    }
}
