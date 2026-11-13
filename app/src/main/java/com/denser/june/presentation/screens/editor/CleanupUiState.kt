package com.denser.june.presentation.screens.editor

import com.denser.june.core.localai.DictationCleanup
import com.denser.june.core.localai.TextDiff

/** State of the "Clean up dictation" flow. The entry is only changed by [EditorVM.acceptCleanup]. */
sealed interface CleanupUiState {
    data object Hidden : CleanupUiState

    /** [startedAt] is wall-clock time, for the elapsed counter. */
    data class Working(
        val progress: DictationCleanup.Progress = DictationCleanup.Progress(),
        val startedAt: Long = System.currentTimeMillis(),
    ) : CleanupUiState

    data class Review(
        val original: String,
        val rewrite: String,
        val segments: List<TextDiff.Segment>,
        val changes: List<String>,
    ) : CleanupUiState

    data class Problem(val kind: Kind, val message: String? = null) : CleanupUiState

    enum class Kind { ServerUnavailable, NotPaired, NoModel, Busy, TooLong, Empty, Stopped, Error }
}

/** What the sparkle button in the editor shows. */
sealed interface CleanupIndicator {
    data object Idle : CleanupIndicator

    /** A run for this entry is going on behind the minimized sheet. */
    data class Working(val progress: DictationCleanup.Progress) : CleanupIndicator

    /** The rewrite for this entry is waiting to be reviewed. */
    data object Ready : CleanupIndicator

    data object Problem : CleanupIndicator

    /** A run for another entry is going on. */
    data object BusyElsewhere : CleanupIndicator
}
