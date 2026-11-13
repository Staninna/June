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
import java.net.Proxy
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resumeWithException

/** Outcome of a call to the local server, mapped from the contract's error codes (§4). */
sealed interface LocalAiResult<out T> {
    data class Ok<T>(val value: T) : LocalAiResult<T>

    /** Connection refused: the server app is not installed, not started, or Network is off. */
    data object Unreachable : LocalAiResult<Nothing>

    data object Unauthorized : LocalAiResult<Nothing>

    data object NoModel : LocalAiResult<Nothing>

    data object Busy : LocalAiResult<Nothing>

    data object TooLong : LocalAiResult<Nothing>

    data class Failed(val message: String) : LocalAiResult<Nothing>
}

/**
 * Minimal HTTP client for the local server. It refuses any base URL that is not 127.0.0.1 /
 * localhost and ignores system proxies, so it can never reach the network.
 */
class LocalAiClient(
    private val http: OkHttpClient =
        OkHttpClient.Builder()
            .proxy(Proxy.NO_PROXY)
            .connectTimeout(3, TimeUnit.SECONDS)
            // a 4B model on a phone can take a couple of minutes for a long entry
            .readTimeout(5, TimeUnit.MINUTES)
            .callTimeout(6, TimeUnit.MINUTES)
            .retryOnConnectionFailure(false)
            .build(),
) {
    private val json = LocalAiContract.json
    private val jsonType = "application/json".toMediaType()

    suspend fun health(baseUrl: String): LocalAiResult<LocalAiHealth> =
        execute(baseUrl, Request.Builder().url(baseUrl.trimEnd('/') + "/health").get().build()) { body ->
            json.decodeFromString<LocalAiHealth>(body)
        }

    /** Checks the token with an authenticated call; returns the active model id (or null). */
    suspend fun activeModel(baseUrl: String, token: String): LocalAiResult<String?> =
        execute(
            baseUrl,
            Request.Builder().url(baseUrl.trimEnd('/') + "/v1/models").header("Authorization", "Bearer $token").get().build(),
        ) { body ->
            val data = json.parseToJsonElement(body).let { it as? kotlinx.serialization.json.JsonObject }?.get("data")
            (data as? kotlinx.serialization.json.JsonArray)?.firstOrNull()
                ?.let { (it as? kotlinx.serialization.json.JsonObject)?.get("id") }
                ?.let { (it as? JsonPrimitive)?.content }
        }

    /** Sends the dictated [notes] with the [systemPrompt] and returns the raw rewrite. */
    suspend fun cleanUp(baseUrl: String, token: String, systemPrompt: String, notes: String): LocalAiResult<String> {
        val payload =
            LocalAiChatRequest(
                messages = listOf(LocalAiMessage("system", systemPrompt), LocalAiMessage("user", notes)),
                stream = false,
                chatTemplateKwargs = mapOf("enable_thinking" to JsonPrimitive(false)),
            )
        val request =
            Request.Builder()
                .url(baseUrl.trimEnd('/') + "/v1/chat/completions")
                .header("Authorization", "Bearer $token")
                .post(json.encodeToString(payload).toRequestBody(jsonType))
                .build()
        return execute(baseUrl, request) { body ->
            json.decodeFromString<LocalAiChatResponse>(body).choices.firstOrNull()?.message?.content.orEmpty()
        }
    }

    private suspend fun <T> execute(baseUrl: String, request: Request, parse: (String) -> T): LocalAiResult<T> {
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
            } catch (e: IOException) {
                LocalAiResult.Unreachable
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
            else ->
                when (status) {
                    401 -> LocalAiResult.Unauthorized
                    429 -> LocalAiResult.Busy
                    503 -> LocalAiResult.NoModel
                    else -> LocalAiResult.Failed(error?.message?.ifBlank { null } ?: "Server error $status")
                }
        }
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
