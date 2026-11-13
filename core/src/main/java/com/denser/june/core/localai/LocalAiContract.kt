package com.denser.june.core.localai

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

/**
 * Client side of docs/LOCAL_LLM_API.md (contract v2). The server is the SmolChat fork
 * ("SmolChat Server"); keep this file in sync with the contract in both repositories.
 */
object LocalAiContract {
    const val API_VERSION = 2
    const val SERVER_PACKAGE = "io.shubham0204.smollmandroid"
    const val ACTION_PAIR = "$SERVER_PACKAGE.action.PAIR"
    const val EXTRA_CLIENT_NAME = "client_name"
    const val EXTRA_TOKEN = "token"
    const val EXTRA_BASE_URL = "base_url"
    const val EXTRA_API_VERSION = "api_version"

    val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        encodeDefaults = true
    }

    /** Only loopback base URLs are accepted: June never talks to anything but 127.0.0.1. */
    fun isLoopbackBaseUrl(url: String): Boolean =
        Regex("""^http://(127\.0\.0\.1|localhost):\d{1,5}/?$""").matches(url.trim())
}

@Serializable
data class LocalAiMessage(val role: String, val content: String)

@Serializable
data class LocalAiChatRequest(
    val model: String? = null,
    val messages: List<LocalAiMessage>,
    val temperature: Float? = null,
    @SerialName("top_p") val topP: Float? = null,
    @SerialName("top_k") val topK: Int? = null,
    @SerialName("min_p") val minP: Float? = null,
    @SerialName("presence_penalty") val presencePenalty: Float? = null,
    @SerialName("max_tokens") val maxTokens: Int? = null,
    val stream: Boolean = false,
    @SerialName("chat_template_kwargs") val chatTemplateKwargs: Map<String, JsonElement>? = null,
    /** v2: answer `202` with a job and keep generating without this connection (§2.4). */
    val background: Boolean = false,
    /**
     * v2: June's own id for this request (a UUID). Sending the same id again returns the job that
     * already exists, and the job can be asked for by this id.
     */
    @SerialName("request_id") val requestId: String? = null,
)

@Serializable
data class LocalAiResponseMessage(val role: String = "assistant", val content: String = "")

@Serializable
data class LocalAiChoice(
    val index: Int = 0,
    val message: LocalAiResponseMessage,
    @SerialName("finish_reason") val finishReason: String? = null,
)

@Serializable
data class LocalAiUsage(
    @SerialName("prompt_tokens") val promptTokens: Int = 0,
    @SerialName("completion_tokens") val completionTokens: Int = 0,
    @SerialName("total_tokens") val totalTokens: Int = 0,
)

@Serializable
data class LocalAiChatResponse(
    val id: String = "",
    val model: String = "",
    val choices: List<LocalAiChoice> = emptyList(),
    val usage: LocalAiUsage? = null,
)

@Serializable
data class LocalAiJobProgress(
    @SerialName("prompt_tokens") val promptTokens: Int = 0,
    @SerialName("prompt_tokens_processed") val promptTokensProcessed: Int = 0,
    @SerialName("prompt_tokens_cached") val promptTokensCached: Int = 0,
    @SerialName("completion_tokens") val completionTokens: Int = 0,
    @SerialName("max_tokens") val maxTokens: Int? = null,
    @SerialName("tokens_per_second") val tokensPerSecond: Double? = null,
    @SerialName("elapsed_ms") val elapsedMs: Long = 0,
)

/** A background generation (§2.4). [response] is set once [status] is `completed`. */
@Serializable
data class LocalAiJob(
    val id: String,
    @SerialName("object") val obj: String = OBJECT,
    @SerialName("request_id") val requestId: String? = null,
    val status: String = STATUS_QUEUED,
    @SerialName("queue_position") val queuePosition: Int? = null,
    val progress: LocalAiJobProgress = LocalAiJobProgress(),
    val response: LocalAiChatResponse? = null,
    val error: LocalAiErrorBody? = null,
) {
    val isFinished: Boolean
        get() = status == STATUS_COMPLETED || status == STATUS_FAILED || status == STATUS_CANCELLED

    companion object {
        const val OBJECT = "chat.completion.job"
        const val STATUS_QUEUED = "queued"
        const val STATUS_LOADING_MODEL = "loading_model"
        const val STATUS_PROCESSING_PROMPT = "processing_prompt"
        const val STATUS_GENERATING = "generating"
        const val STATUS_COMPLETED = "completed"
        const val STATUS_FAILED = "failed"
        const val STATUS_CANCELLED = "cancelled"
    }
}

@Serializable
data class LocalAiHealth(
    val status: String,
    @SerialName("api_version") val apiVersion: Int = 1,
    val model: String? = null,
    @SerialName("queue_length") val queueLength: Int = 0,
)

@Serializable
data class LocalAiErrorBody(val message: String = "", val type: String = "", val code: String = "")

@Serializable
data class LocalAiErrorResponse(val error: LocalAiErrorBody)
