package com.denser.june.core.localai

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class LocalAiSettings(
    /** Off by default: nothing is sent anywhere until the user turns it on. */
    val enabled: Boolean = false,
    val token: String? = null,
    val baseUrl: String? = null,
    val serverPackage: String = LocalAiContract.SERVER_PACKAGE,
    /** null = use [DictationPrompt.DEFAULT_SYSTEM_PROMPT]. */
    val customSystemPrompt: String? = null,
) {
    val isPaired: Boolean
        get() = !token.isNullOrBlank() && baseUrl != null

    val systemPromptTemplate: String
        get() = customSystemPrompt ?: DictationPrompt.DEFAULT_SYSTEM_PROMPT
}

/**
 * Stored in its own SharedPreferences file ("local_ai"), which is excluded from backups so the
 * pairing token never leaves the device.
 */
class LocalAiPreferences(context: Context) {
    private val prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
    private val _settings = MutableStateFlow(read())
    val settings: StateFlow<LocalAiSettings> = _settings.asStateFlow()

    val current: LocalAiSettings
        get() = _settings.value

    fun setEnabled(enabled: Boolean) = update { it.copy(enabled = enabled) }

    fun savePairing(token: String, baseUrl: String, serverPackage: String) =
        update { it.copy(token = token, baseUrl = baseUrl.trimEnd('/'), serverPackage = serverPackage) }

    fun clearPairing() = update { it.copy(token = null, baseUrl = null) }

    /** Saving the default text (or blank) stores null, so future prompt updates still apply. */
    fun setSystemPrompt(prompt: String) =
        update {
            val p = prompt.trim()
            it.copy(customSystemPrompt = p.takeIf { v -> v.isNotEmpty() && v != DictationPrompt.DEFAULT_SYSTEM_PROMPT })
        }

    fun restoreDefaults() = update { it.copy(customSystemPrompt = null) }

    private fun update(transform: (LocalAiSettings) -> LocalAiSettings) {
        val next = transform(_settings.value)
        prefs.edit {
            putBoolean(KEY_ENABLED, next.enabled)
            putString(KEY_TOKEN, next.token)
            putString(KEY_BASE_URL, next.baseUrl)
            putString(KEY_SERVER_PACKAGE, next.serverPackage)
            putString(KEY_PROMPT, next.customSystemPrompt)
        }
        _settings.value = next
    }

    private fun read() =
        LocalAiSettings(
            enabled = prefs.getBoolean(KEY_ENABLED, false),
            token = prefs.getString(KEY_TOKEN, null),
            baseUrl = prefs.getString(KEY_BASE_URL, null),
            serverPackage = prefs.getString(KEY_SERVER_PACKAGE, null) ?: LocalAiContract.SERVER_PACKAGE,
            customSystemPrompt = prefs.getString(KEY_PROMPT, null),
        )

    companion object {
        const val FILE = "local_ai"
        private const val KEY_ENABLED = "enabled"
        private const val KEY_TOKEN = "token"
        private const val KEY_BASE_URL = "base_url"
        private const val KEY_SERVER_PACKAGE = "server_package"
        private const val KEY_PROMPT = "system_prompt"
    }
}
