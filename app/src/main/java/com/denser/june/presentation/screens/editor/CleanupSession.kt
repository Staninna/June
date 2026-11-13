package com.denser.june.presentation.screens.editor

import com.denser.june.core.localai.CleanupPerson
import com.denser.june.core.localai.DictationCleanup
import com.denser.june.core.localai.TextDiff
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The one "Clean up dictation" run of the app.
 *
 * It lives outside the editor screen on purpose: the user can minimize the sheet, keep writing,
 * leave the entry or walk around June, and the run goes on. Whoever shows it (the sheet in the
 * editor, the sparkle button, the pill on other screens) reads [snapshot].
 */
class CleanupSession(private val cleanup: DictationCleanup) {

    data class Snapshot(
        /** The entry the run belongs to: its journal id, or a temporary key for an unsaved entry. */
        val entryKey: String? = null,
        /** True when [entryKey] is a journal id, so the entry can be opened from anywhere. */
        val canOpenEntry: Boolean = false,
        val state: CleanupUiState = CleanupUiState.Hidden,
        /** The sheet is put away; the run shows as a button or pill. */
        val minimized: Boolean = false,
        /** The key of the entry whose editor is on screen right now, if any. */
        val visibleEditorKey: String? = null,
    ) {
        val isActive: Boolean
            get() = state !is CleanupUiState.Hidden

        /** The run has something to show, and the user is not looking at its entry. */
        val showsPill: Boolean
            get() = isActive && state !is CleanupUiState.Problem && visibleEditorKey != entryKey
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var job: Job? = null
    private val _snapshot = MutableStateFlow(Snapshot())
    val snapshot: StateFlow<Snapshot> = _snapshot.asStateFlow()

    /** Starts a run for [entryKey]; a run that is still going is replaced. */
    fun start(entryKey: String, canOpenEntry: Boolean, body: String, people: suspend () -> List<CleanupPerson>) {
        job?.cancel()
        val working = CleanupUiState.Working()
        _snapshot.update { it.copy(entryKey = entryKey, canOpenEntry = canOpenEntry, state = working, minimized = false) }
        job =
            scope.launch {
                val outcome =
                    cleanup.run(body, people()) { progress ->
                        _snapshot.update { s ->
                            if (s.state is CleanupUiState.Working) s.copy(state = working.copy(progress = progress)) else s
                        }
                    }
                val next =
                    when (outcome) {
                        is DictationCleanup.Outcome.Rewrite ->
                            CleanupUiState.Review(
                                original = outcome.original,
                                rewrite = outcome.rewrite,
                                segments = withContext(Dispatchers.Default) { TextDiff.diff(outcome.original, outcome.rewrite) },
                                changes = outcome.changes,
                            )
                        DictationCleanup.Outcome.ServerUnavailable -> CleanupUiState.Problem(CleanupUiState.Kind.ServerUnavailable)
                        DictationCleanup.Outcome.NotPaired -> CleanupUiState.Problem(CleanupUiState.Kind.NotPaired)
                        DictationCleanup.Outcome.NoModel -> CleanupUiState.Problem(CleanupUiState.Kind.NoModel)
                        DictationCleanup.Outcome.Busy -> CleanupUiState.Problem(CleanupUiState.Kind.Busy)
                        DictationCleanup.Outcome.TooLong -> CleanupUiState.Problem(CleanupUiState.Kind.TooLong)
                        DictationCleanup.Outcome.EmptyResult -> CleanupUiState.Problem(CleanupUiState.Kind.Empty)
                        DictationCleanup.Outcome.Stopped -> CleanupUiState.Problem(CleanupUiState.Kind.Stopped)
                        is DictationCleanup.Outcome.Error -> CleanupUiState.Problem(CleanupUiState.Kind.Error, outcome.message)
                    }
                _snapshot.update { it.copy(state = next) }
            }
    }

    /** Stops the run (also on the server) and forgets it. */
    fun cancel() {
        job?.cancel()
        job = null
        _snapshot.update { it.copy(entryKey = null, canOpenEntry = false, state = CleanupUiState.Hidden, minimized = false) }
    }

    /** The result was used or dismissed. */
    fun clear() = cancel()

    fun minimize() = _snapshot.update { if (it.isActive) it.copy(minimized = true) else it }

    fun expand() = _snapshot.update { if (it.isActive) it.copy(minimized = false) else it }

    /** An unsaved entry got its journal id. */
    fun rebind(oldKey: String, journalId: String) =
        _snapshot.update {
            it.copy(
                entryKey = if (it.entryKey == oldKey) journalId else it.entryKey,
                canOpenEntry = it.canOpenEntry || it.entryKey == oldKey,
                visibleEditorKey = if (it.visibleEditorKey == oldKey) journalId else it.visibleEditorKey,
            )
        }

    /** The editor for [key] is on screen. */
    fun editorShown(key: String) = _snapshot.update { it.copy(visibleEditorKey = key) }

    /** The editor for [key] went away: the run carries on, minimized. */
    fun editorGone(key: String) =
        _snapshot.update {
            if (it.visibleEditorKey != key) it
            else it.copy(visibleEditorKey = null, minimized = it.minimized || it.entryKey == key)
        }
}
