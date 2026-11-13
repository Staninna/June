package com.denser.june.core.localai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.net.ConnectException
import java.net.Proxy
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resumeWithException

/** Outcome of a call to the local server, mapped from the contract's error codes (§4). */
sealed interface LocalAiResult<out T> {
    data class Ok<T>(val value: T) : LocalAiResult<T>

    /** Connection refused: the server app is not installed, not started, or Network is off. */
    data object Unreachable : LocalAiResult<Nothing>

    /** The server accepted the connection but the answer did not arrive in time (or the link broke). */
    data object TimedOut : LocalAiResult<Nothing>

    /** The server does not know this job (any more): it was restarted, or the result expired. */
    data object NotFound : LocalAiResult<Nothing>

    data object Unauthorized : LocalAiResult<Nothing>

    data object NoModel : LocalAiResult<Nothing>

    data object Busy : LocalAiResult<Nothing>

    data object TooLong : LocalAiResult<Nothing>

    data class Failed(val message: String) : LocalAiResult<Nothing>
}

/** What the server answered to a cleanup request. */
sealed interface LocalAiSubmission {
    /** Contract v2: the server works on it in the background; poll [LocalAiClient.job]. */
    data class Accepted(val job: LocalAiJob) : LocalAiSubmission

    /** Contract v1 servers answer with the finished completion on the same connection. */
    data class Finished(val completion: LocalAiCompletion) : LocalAiSubmission
}

data class LocalAiCompletion(val text: String, val finishReason: String?)

fun LocalAiChatResponse.toCompletion(): LocalAiCompletion =
    choices.firstOrNull().let { LocalAiCompletion(it?.message?.content.orEmpty(), it?.finishReason) }

/**
 * Minimal HTTP client for the local server. It refuses any base URL that is not 127.0.0.1 /
 * localhost and ignores system proxies, so it can never reach the network.
 *
 * Generation runs as a background job on the server (contract v2): every call here is short, so no
 * timeout depends on how long the model needs, and nothing is lost when June is in the background.
 */
class LocalAiClient(
    private val http: OkHttpClient =
        OkHttpClient.Builder()
            .proxy(Proxy.NO_PROXY)
            .connectTimeout(3, TimeUnit.SECONDS)
            .readTimeout(POLL_WAIT_SECONDS + 15, TimeUnit.SECONDS)
            .callTimeout(POLL_WAIT_SECONDS + 20, TimeUnit.SECONDS)
            .retryOnConnectionFailure(false)
            .build(),
) {
    private val json = LocalAiContract.json
    private val jsonType = "application/json".toMediaType()

    /** Only a contract v1 server keeps the POST open while it generates; give that one a long time. */
    private val submitHttp: OkHttpClient =
        http.newBuilder().readTimeout(30, TimeUnit.MINUTES).callTimeout(31, TimeUnit.MINUTES).build()

    suspend fun health(baseUrl: String): LocalAiResult<LocalAiHealth> =
        execute(http, baseUrl, Request.Builder().url(baseUrl.trimEnd('/') + "/health").get().build()) { body ->
            json.decodeFromString<LocalAiHealth>(body)
        }

    /** Checks the token with an authenticated call; returns the active model id (or null). */
    suspend fun activeModel(baseUrl: String, token: String): LocalAiResult<String?> =
        execute(
            http,
            baseUrl,
            Request.Builder().url(baseUrl.trimEnd('/') + "/v1/models").header("Authorization", "Bearer $token").get().build(),
        ) { body ->
            val data = json.parseToJsonElement(body).let { it as? kotlinx.serialization.json.JsonObject }?.get("data")
            (data as? kotlinx.serialization.json.JsonArray)?.firstOrNull()
                ?.let { (it as? kotlinx.serialization.json.JsonObject)?.get("id") }
                ?.let { (it as? JsonPrimitive)?.content }
        }

    /**
     * Sends the dictated [notes] with the [systemPrompt] as a background job. [maxTokens] bounds the
     * answer, so a model that does not stop cannot keep the phone busy for the whole context window.
     */
    suspend fun submitCleanup(
        baseUrl: String,
        token: String,
        systemPrompt: String,
        notes: String,
        maxTokens: Int?,
    ): LocalAiResult<LocalAiSubmission> {
        val payload =
            LocalAiChatRequest(
                messages = listOf(LocalAiMessage("system", systemPrompt), LocalAiMessage("user", notes)),
                maxTokens = maxTokens,
                stream = false,
                chatTemplateKwargs = mapOf("enable_thinking" to JsonPrimitive(false)),
                background = true,
            )
        val request =
            Request.Builder()
                .url(baseUrl.trimEnd('/') + "/v1/chat/completions")
                .header("Authorization", "Bearer $token")
                .post(json.encodeToString(payload).toRequestBody(jsonType))
                .build()
        return execute(submitHttp, baseUrl, request) { body -> parseSubmission(body) }
    }

    internal fun parseSubmission(body: String): LocalAiSubmission {
        val obj = json.parseToJsonElement(body) as? kotlinx.serialization.json.JsonObject
        val kind = (obj?.get("object") as? JsonPrimitive)?.content
        return if (kind == LocalAiJob.OBJECT) {
            LocalAiSubmission.Accepted(json.decodeFromString<LocalAiJob>(body))
        } else {
            LocalAiSubmission.Finished(json.decodeFromString<LocalAiChatResponse>(body).toCompletion())
        }
    }

    /** The job's state. The server answers when the job has ended, or after [waitSeconds]. */
    suspend fun job(baseUrl: String, token: String, id: String, waitSeconds: Int = POLL_WAIT_SECONDS.toInt()): LocalAiResult<LocalAiJob> =
        execute(
            http,
            baseUrl,
            Request.Builder()
                .url(baseUrl.trimEnd('/') + "/v1/jobs/$id?wait=$waitSeconds")
                .header("Authorization", "Bearer $token")
                .get()
                .build(),
        ) { body -> json.decodeFromString<LocalAiJob>(body) }

    /** Stops a job. Best effort: the result does not matter to the caller. */
    suspend fun cancelJob(baseUrl: String, token: String, id: String): LocalAiResult<Unit> =
        execute(
            http,
            baseUrl,
            Request.Builder().url(baseUrl.trimEnd('/') + "/v1/jobs/$id").header("Authorization", "Bearer $token").delete().build(),
        ) {}

    private suspend fun <T> execute(
        http: OkHttpClient,
        baseUrl: String,
        request: Request,
        parse: (String) -> T,
    ): LocalAiResult<T> {
        if (!LocalAiContract.isLoopbackBaseUrl(baseUrl)) return LocalAiResult.Failed("Not a local address: $baseUrl")
        return withContext(Dispatchers.IO) {
            try {
                http.newCall(request).await().use { response ->
                    val body = response.body?.string().orEmpty()
                    if (response.isSuccessful) {
                        LocalAiResult.Ok(parse(body))
                    } else {
                        mapError(response.code, body)
                    }
                }
            } catch (e: ConnectException) {
                LocalAiResult.Unreachable
            } catch (e: IOException) {
                LocalAiResult.TimedOut
            } catch (e: kotlinx.serialization.SerializationException) {
                LocalAiResult.Failed("Unexpected response from the server")
            } catch (e: IllegalArgumentException) {
                LocalAiResult.Failed(e.message ?: "Invalid request")
            }
        }
    }

    private fun mapError(status: Int, body: String): LocalAiResult<Nothing> {
        val error = runCatching { json.decodeFromString<LocalAiErrorResponse>(body).error }.getOrNull()
        return when (error?.code) {
            "unauthorized" -> LocalAiResult.Unauthorized
            "model_not_loaded" -> LocalAiResult.NoModel
            "server_busy" -> LocalAiResult.Busy
            "context_length_exceeded" -> LocalAiResult.TooLong
            "not_found" -> LocalAiResult.NotFound
            else ->
                when (status) {
                    401 -> LocalAiResult.Unauthorized
                    429 -> LocalAiResult.Busy
                    503 -> LocalAiResult.NoModel
                    404 -> LocalAiResult.NotFound
                    else -> LocalAiResult.Failed(error?.message?.ifBlank { null } ?: "Server error $status")
                }
        }
    }

    companion object {
        /** How long one status call may wait on the server for the job to end. */
        const val POLL_WAIT_SECONDS = 2L
    }
}

private suspend fun Call.await(): Response =
    suspendCancellableCoroutine { cont ->
        cont.invokeOnCancellation { cancel() }
        enqueue(
            object : Callback {
                override fun onResponse(call: Call, response: Response) = cont.resume(response) { _, _, _ -> response.close() }

                override fun onFailure(call: Call, e: IOException) {
                    if (!cont.isCancelled) cont.resumeWithException(e)
                }
            }
        )
    }
