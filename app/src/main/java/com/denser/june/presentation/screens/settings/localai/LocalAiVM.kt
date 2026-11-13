package com.denser.june.presentation.screens.settings.localai

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.denser.june.core.domain.repository.JournalRepository
import com.denser.june.core.localai.DictationPrompt
import com.denser.june.core.localai.LocalAiClient
import com.denser.june.core.localai.LocalAiContract
import com.denser.june.core.localai.LocalAiPreferences
import com.denser.june.core.localai.LocalAiResult
import com.denser.june.core.localai.LocalAiSettings
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Connection state shown in Settings → Local AI. */
sealed interface LocalAiStatus {
    data object Checking : LocalAiStatus
    data object NotInstalled : LocalAiStatus
    data object NotRunning : LocalAiStatus
    data object NotPaired : LocalAiStatus
    data object Revoked : LocalAiStatus
    data object NoModel : LocalAiStatus
    data class Ready(val model: String?) : LocalAiStatus
}

class LocalAiVM(
    private val context: Context,
    private val prefs: LocalAiPreferences,
    private val client: LocalAiClient,
    journalRepo: JournalRepository,
) : ViewModel() {
    val settings: StateFlow<LocalAiSettings> = prefs.settings

    val people: StateFlow<List<String>> =
        journalRepo.getUniqueTags()
            .map { DictationPrompt.peopleFromTags(it) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _status = MutableStateFlow<LocalAiStatus>(LocalAiStatus.Checking)
    val status: StateFlow<LocalAiStatus> = _status.asStateFlow()
    private var checkJob: Job? = null

    val serverInstalled: Boolean
        get() = serverLaunchIntent(context, prefs.current.serverPackage) != null

    fun refresh() {
        checkJob?.cancel()
        _status.value = LocalAiStatus.Checking
        checkJob =
            viewModelScope.launch {
                val s = prefs.current
                if (!serverInstalled) {
                    _status.value = LocalAiStatus.NotInstalled
                    return@launch
                }
                val baseUrl = s.baseUrl ?: DEFAULT_BASE_URL
                val health = client.health(baseUrl)
                if (health !is LocalAiResult.Ok) {
                    _status.value = LocalAiStatus.NotRunning
                    return@launch
                }
                val token = s.token
                if (token.isNullOrBlank() || s.baseUrl == null) {
                    _status.value = LocalAiStatus.NotPaired
                    return@launch
                }
                _status.value =
                    when (val r = client.activeModel(baseUrl, token)) {
                        is LocalAiResult.Ok ->
                            if (r.value == null || health.value.status == "no_model") LocalAiStatus.NoModel
                            else LocalAiStatus.Ready(r.value)
                        LocalAiResult.Unauthorized -> LocalAiStatus.Revoked
                        LocalAiResult.NoModel -> LocalAiStatus.NoModel
                        LocalAiResult.Unreachable -> LocalAiStatus.NotRunning
                        else -> LocalAiStatus.Ready(health.value.model)
                    }
            }
    }

    fun pairIntent(): Intent =
        Intent(LocalAiContract.ACTION_PAIR)
            .setPackage(prefs.current.serverPackage)
            .putExtra(LocalAiContract.EXTRA_CLIENT_NAME, "June")

    /** Handles the pairing result (docs/LOCAL_LLM_API.md §3.3). Returns true when paired. */
    fun onPairResult(resultCode: Int, data: Intent?): Boolean {
        val token = data?.getStringExtra(LocalAiContract.EXTRA_TOKEN)
        val baseUrl = data?.getStringExtra(LocalAiContract.EXTRA_BASE_URL)
        if (resultCode != Activity.RESULT_OK || token.isNullOrBlank() || baseUrl == null ||
            !LocalAiContract.isLoopbackBaseUrl(baseUrl)
        ) {
            refresh()
            return false
        }
        prefs.savePairing(token, baseUrl, prefs.current.serverPackage)
        prefs.setEnabled(true)
        refresh()
        return true
    }

    fun setEnabled(enabled: Boolean) = prefs.setEnabled(enabled)

    fun unpair() {
        prefs.clearPairing()
        refresh()
    }

    fun saveSystemPrompt(prompt: String) = prefs.setSystemPrompt(prompt)

    fun restoreDefaults() = prefs.restoreDefaults()

    companion object {
        const val DEFAULT_BASE_URL = "http://127.0.0.1:8080"

        fun serverLaunchIntent(context: Context, pkg: String = LocalAiContract.SERVER_PACKAGE): Intent? =
            context.packageManager.getLaunchIntentForPackage(pkg)
    }
}
