package com.denser.june.core.localai

/** Runs one "Clean up dictation" request: prompt with People tags, call, validate. */
class DictationCleanup(
    private val client: LocalAiClient,
    private val prefs: LocalAiPreferences,
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

        data class Error(val message: String) : Outcome
    }

    suspend fun run(body: String, people: List<String>): Outcome {
        val s = prefs.current
        val token = s.token
        val baseUrl = s.baseUrl
        if (token.isNullOrBlank() || baseUrl == null) return Outcome.NotPaired
        val prompt = DictationPrompt.buildSystemPrompt(s.systemPromptTemplate, people)
        return when (val r = client.cleanUp(baseUrl, token, prompt, body)) {
            is LocalAiResult.Ok -> {
                val result = OutputValidator.validate(r.value, people)
                if (result.text.isBlank()) Outcome.EmptyResult else Outcome.Rewrite(body, result.text, result.changes)
            }
            LocalAiResult.Unreachable -> Outcome.ServerUnavailable
            LocalAiResult.Unauthorized -> Outcome.NotPaired
            LocalAiResult.NoModel -> Outcome.NoModel
            LocalAiResult.Busy -> Outcome.Busy
            LocalAiResult.TooLong -> Outcome.TooLong
            is LocalAiResult.Failed -> Outcome.Error(r.message)
        }
    }
}
