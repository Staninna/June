package com.denser.june.core.localai

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.security.MessageDigest
import java.util.UUID

/**
 * Runs one "Clean up dictation" request: prompt with People tags, call, validate.
 *
 * The server generates in a background job, and this class only asks for its state in short calls.
 * So there is no timeout that depends on how long the model needs, and the job carries on while
 * June is in the background or the screen is off, for minutes or for hours.
 *
 * Every cleanup gets a request id (a UUID) that June stores before it sends anything. The server
 * treats that id as the identity of the generation: sending it again returns the same job, and the
 * job is kept on the server's disk with a checkpoint. So the same text always leads back to the
 * same generation, whether the answer got lost, June was killed, or the server itself restarted.
 */
class DictationCleanup(
    private val client: LocalAiClient,
    private val prefs: LocalAiStore,
) {
    sealed interface Outcome {
        data class Rewrite(val original: String, val rewrite: String, val changes: List<String>) : Outcome

        /** The server could not be reached (not installed / not running / Network permission off). */
        data object ServerUnavailable : Outcome

        data object NotPaired : Outcome

        data object NoModel : Outcome

        data object Busy : Outcome

        data object TooLong : Outcome

        data object EmptyResult : Outcome

        /** The request was stopped in the server app, or the server was stopped. */
        data object Stopped : Outcome

        data class Error(val message: String) : Outcome
    }

    enum class Phase { Sending, Queued, LoadingModel, Reading, Writing }

    /** What the server is doing right now, for the progress sheet. */
    data class Progress(
        val phase: Phase = Phase.Sending,
        val queuePosition: Int? = null,
        val promptTokens: Int = 0,
        val promptTokensProcessed: Int = 0,
        val completionTokens: Int = 0,
        /** Roughly how many tokens the rewrite will have (the entry's own length). */
        val expectedTokens: Int = 0,
        val tokensPerSecond: Double? = null,
    ) {
        /** 0..1 within the current phase, or null when it cannot be known. */
        val fraction: Float?
            get() =
                when (phase) {
                    Phase.Reading -> if (promptTokens > 0) promptTokensProcessed.toFloat() / promptTokens else null
                    // the estimate is rough, so never claim to be done before the server says so
                    Phase.Writing ->
                        if (expectedTokens > 0) (completionTokens.toFloat() / expectedTokens).coerceAtMost(0.97f) else null
                    else -> null
                }
    }

    suspend fun run(body: String, people: List<String>, onProgress: (Progress) -> Unit = {}): Outcome {
        val s = prefs.current
        val token = s.token
        val baseUrl = s.baseUrl
        if (token.isNullOrBlank() || baseUrl == null) return Outcome.NotPaired
        val prompt = DictationPrompt.buildSystemPrompt(s.systemPromptTemplate, people)
        val fingerprint = fingerprint(prompt, body)
        val expected = expectedTokens(body)

        // June's own name for this cleanup. It is stored before anything is sent, so whatever
        // happens next (answer lost, June frozen or killed, server restarted) the same text always
        // leads back to the same generation.
        val requestId =
            s.pendingJobId?.takeIf { s.pendingJobFingerprint == fingerprint }
                ?: newRequestId().also { prefs.setPendingJob(it, fingerprint) }
        var onServer = false
        try {
            var bound: Int? = maxTokens(body)
            var restarts = 0
            while (true) {
                onProgress(Progress(Phase.Sending, expectedTokens = expected))
                val submission =
                    when (val submitted = submit(baseUrl, token, prompt, body, bound, requestId)) {
                        is LocalAiResult.Ok -> submitted.value
                        LocalAiResult.TooLong ->
                            if (bound != null) {
                                // the safety bound may be what does not fit: use the room that is left
                                bound = null
                                continue
                            } else {
                                return finish(Ended.Problem(Outcome.TooLong), body, people, baseUrl, token, requestId)
                            }
                        else -> return finish(mapFailure(submitted), body, people, baseUrl, token, requestId)
                    }
                when (submission) {
                    is LocalAiSubmission.Finished ->
                        return finish(Ended.Text(submission.completion), body, people, baseUrl, token, null)
                    is LocalAiSubmission.Accepted -> {
                        onServer = true
                        when (val followed = follow(baseUrl, token, requestId, expected, onProgress, submission.job)) {
                            is Followed.Ended -> return finish(followed.outcome, body, people, baseUrl, token, requestId)
                            // the server lost the job (its data was cleared): the same id starts it again
                            Followed.Lost ->
                                if (++restarts > MAX_RESTARTS) {
                                    return finish(Ended.Problem(Outcome.Stopped), body, people, baseUrl, token, requestId)
                                }
                        }
                    }
                }
            }
        } catch (e: CancellationException) {
            // the user pressed Cancel or left the editor: nobody wants the result any more
            withContext(NonCancellable) {
                prefs.setPendingJob(null, null)
                if (onServer) withTimeoutOrNull(CANCEL_TIMEOUT_MS) { client.cancelJob(baseUrl, token, requestId) }
            }
            throw e
        }
    }

    /** Posting is safe to repeat (same request id), so a lost answer is simply asked for again. */
    private suspend fun submit(
        baseUrl: String,
        token: String,
        prompt: String,
        body: String,
        maxTokens: Int?,
        requestId: String,
    ): LocalAiResult<LocalAiSubmission> {
        var attempt = 0
        while (true) {
            val r = client.submitCleanup(baseUrl, token, prompt, body, maxTokens, requestId)
            if (r != LocalAiResult.TimedOut || ++attempt >= MAX_SUBMIT_ATTEMPTS) return r
            delay(RETRY_DELAY_MS)
        }
    }

    private sealed interface Ended {
        data class Text(val completion: LocalAiCompletion) : Ended

        /** [keepJob]: the job may still be running, so the next attempt should look for it again. */
        data class Problem(val outcome: Outcome, val keepJob: Boolean = false) : Ended
    }

    private sealed interface Followed {
        /** The server does not know the job any more. */
        data object Lost : Followed

        data class Ended(val outcome: DictationCleanup.Ended) : Followed
    }

    /** Asks for the job's state until it has ended. Short hiccups are ridden out. */
    private suspend fun follow(
        baseUrl: String,
        token: String,
        id: String,
        expected: Int,
        onProgress: (Progress) -> Unit,
        first: LocalAiJob? = null,
    ): Followed {
        first?.let { onProgress(progressOf(it, expected)) }
        var refused = 0
        var timeouts = 0
        while (true) {
            when (val r = client.job(baseUrl, token, id)) {
                is LocalAiResult.Ok -> {
                    refused = 0
                    timeouts = 0
                    val job = r.value
                    when (job.status) {
                        LocalAiJob.STATUS_COMPLETED ->
                            return Followed.Ended(
                                job.response?.let { Ended.Text(it.toCompletion()) } ?: Ended.Problem(Outcome.EmptyResult)
                            )
                        LocalAiJob.STATUS_FAILED -> return Followed.Ended(Ended.Problem(mapJobError(job.error)))
                        LocalAiJob.STATUS_CANCELLED -> return Followed.Ended(Ended.Problem(Outcome.Stopped))
                        else -> onProgress(progressOf(job, expected))
                    }
                }
                LocalAiResult.NotFound -> return Followed.Lost
                LocalAiResult.Unauthorized -> return Followed.Ended(Ended.Problem(Outcome.NotPaired))
                LocalAiResult.Unreachable -> {
                    // Nothing listens on the port: the server app was stopped or killed. The job is
                    // on its disk and continues when it runs again, so the id is kept.
                    if (++refused >= MAX_REFUSED) {
                        return Followed.Ended(Ended.Problem(Outcome.ServerUnavailable, keepJob = true))
                    }
                    delay(RETRY_DELAY_MS)
                }
                else -> {
                    // a slow or broken answer says nothing about the job; it keeps running
                    if (++timeouts >= MAX_TIMEOUTS) {
                        return Followed.Ended(Ended.Problem(Outcome.ServerUnavailable, keepJob = true))
                    }
                    delay(RETRY_DELAY_MS)
                }
            }
        }
    }

    /**
     * Ends the run. Once June has the answer (or a final "no"), the server may forget the job:
     * [ackId] is deleted there so the text does not stay on its disk.
     */
    private suspend fun finish(
        ended: Ended,
        body: String,
        people: List<String>,
        baseUrl: String,
        token: String,
        ackId: String?,
    ): Outcome {
        val keep = ended is Ended.Problem && ended.keepJob
        if (!keep) {
            prefs.setPendingJob(null, null)
            if (ackId != null && (ended is Ended.Text || (ended as Ended.Problem).outcome.endedOnServer)) {
                withContext(NonCancellable) { withTimeoutOrNull(CANCEL_TIMEOUT_MS) { client.cancelJob(baseUrl, token, ackId) } }
            }
        }
        return when (ended) {
            is Ended.Problem -> ended.outcome
            is Ended.Text -> {
                val result = OutputValidator.validate(ended.completion.text, people)
                val changes =
                    if (ended.completion.finishReason == FINISH_LENGTH) result.changes + CHANGE_CUT_OFF else result.changes
                if (result.text.isBlank()) Outcome.EmptyResult else Outcome.Rewrite(body, result.text, changes)
            }
        }
    }

    /** True for outcomes that came from a finished job, which then still has a row on the server. */
    private val Outcome.endedOnServer: Boolean
        get() = this is Outcome.Error || this == Outcome.TooLong || this == Outcome.NoModel || this == Outcome.Stopped || this == Outcome.EmptyResult

    private fun mapFailure(result: LocalAiResult<*>): Ended =
        Ended.Problem(
            keepJob = result == LocalAiResult.Unreachable || result == LocalAiResult.TimedOut,
            outcome =
            when (result) {
                LocalAiResult.Unreachable, LocalAiResult.TimedOut -> Outcome.ServerUnavailable
                LocalAiResult.Unauthorized -> Outcome.NotPaired
                LocalAiResult.NoModel -> Outcome.NoModel
                LocalAiResult.Busy -> Outcome.Busy
                LocalAiResult.TooLong -> Outcome.TooLong
                LocalAiResult.NotFound -> Outcome.Error("The server does not support this request")
                is LocalAiResult.Failed -> Outcome.Error(result.message)
                is LocalAiResult.Ok -> Outcome.Error("Unexpected response from the server")
            }
        )

    private fun mapJobError(error: LocalAiErrorBody?): Outcome =
        when (error?.code) {
            "unauthorized" -> Outcome.NotPaired
            "model_not_loaded" -> Outcome.NoModel
            "server_busy" -> Outcome.Busy
            "context_length_exceeded" -> Outcome.TooLong
            "cancelled" -> Outcome.Stopped
            else -> Outcome.Error(error?.message?.ifBlank { null } ?: "The model could not finish")
        }

    companion object {
        const val FINISH_LENGTH = "length"

        /** Shown in the review when the model ran into the token limit. */
        const val CHANGE_CUT_OFF = "may be cut off at the end"

        // about 15 seconds of "nothing listens": long enough for Android to restart the server
        private const val MAX_REFUSED = 10
        private const val MAX_TIMEOUTS = 20
        private const val MAX_SUBMIT_ATTEMPTS = 5
        private const val MAX_RESTARTS = 2
        private const val RETRY_DELAY_MS = 1_500L
        private const val CANCEL_TIMEOUT_MS = 4_000L

        /** A rewrite is about as long as the notes; a token is roughly three characters. */
        fun expectedTokens(body: String): Int = (body.length / 3).coerceAtLeast(16)

        /**
         * Upper bound for the answer: about twice the expected length plus room for short entries.
         * It only stops a model that will not stop by itself.
         */
        fun maxTokens(body: String): Int = (body.length * 2 / 3 + 256).coerceIn(256, 8192)

        fun newRequestId(): String = UUID.randomUUID().toString()

        fun fingerprint(systemPrompt: String, body: String): String =
            MessageDigest.getInstance("SHA-256")
                .digest((systemPrompt + "\u0000" + body).toByteArray())
                .joinToString("") { "%02x".format(it) }

        fun progressOf(job: LocalAiJob, expectedTokens: Int): Progress =
            Progress(
                phase =
                    when (job.status) {
                        LocalAiJob.STATUS_QUEUED -> Phase.Queued
                        LocalAiJob.STATUS_LOADING_MODEL -> Phase.LoadingModel
                        LocalAiJob.STATUS_PROCESSING_PROMPT -> Phase.Reading
                        else -> Phase.Writing
                    },
                queuePosition = job.queuePosition,
                promptTokens = job.progress.promptTokens,
                promptTokensProcessed = job.progress.promptTokensProcessed,
                completionTokens = job.progress.completionTokens,
                expectedTokens = expectedTokens,
                tokensPerSecond = job.progress.tokensPerSecond,
            )
    }
}
