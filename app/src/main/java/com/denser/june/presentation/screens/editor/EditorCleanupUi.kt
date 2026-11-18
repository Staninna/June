package com.denser.june.presentation.screens.editor

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.denser.june.core.R
import com.denser.june.presentation.screens.editor.components.dialogs.CleanupReviewSheet
import com.denser.june.presentation.screens.settings.localai.LocalAiVM

/**
 * The snackbars "Clean up dictation" shows in the editor: the rewrite is ready (with Review, which
 * calls [onReview]), a note when the user writes during a run, and Undo after a rewrite went in.
 */
@Composable
fun EditorCleanupSnackbars(cleanup: EditorCleanup, snackbar: SnackbarHostState, onReview: () -> Unit) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current

    LaunchedEffect(cleanup) {
        // the rewrite arrived while the sheet was put away: a nudge, not an interruption
        cleanup.ready.collect {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            val result = snackbar.showSnackbar(
                message = context.getString(R.string.cleanup_ready),
                actionLabel = context.getString(R.string.cleanup_review_action),
                withDismissAction = true,
                duration = SnackbarDuration.Long
            )
            if (result == SnackbarResult.ActionPerformed) onReview()
        }
    }

    LaunchedEffect(cleanup) {
        cleanup.notice.collect { message ->
            snackbar.showSnackbar(message = context.getString(message), withDismissAction = true)
        }
    }

    LaunchedEffect(cleanup) {
        cleanup.applied.collect {
            val result = snackbar.showSnackbar(
                message = context.getString(R.string.cleanup_applied),
                actionLabel = context.getString(R.string.cleanup_undo),
                withDismissAction = true,
                duration = SnackbarDuration.Long
            )
            if (result == SnackbarResult.ActionPerformed) cleanup.undo()
        }
    }
}

/** The review sheet of this entry's cleanup. [content] is the entry's body, to see what was written meanwhile. */
@Composable
fun EditorCleanupSheet(
    cleanup: EditorCleanup,
    content: String,
    fontFamily: FontFamily?,
    onOpenSettings: () -> Unit,
) {
    val context = LocalContext.current
    val state by cleanup.state.collectAsStateWithLifecycle()
    val formatted by cleanup.isReviewFormatted.collectAsStateWithLifecycle()
    CleanupReviewSheet(
        state = state,
        onAccept = cleanup::accept,
        onRetry = cleanup::retry,
        onCancel = cleanup::cancel,
        onMinimize = cleanup::minimize,
        edits = remember(state, content) { cleanup.editedMeanwhile() },
        onOpenServer = LocalAiVM.serverLaunchIntent(context)?.let { intent -> { context.startActivity(intent) } },
        onOpenSettings = {
            cleanup.cancel()
            onOpenSettings()
        },
        onPickPerson = cleanup::pickPerson,
        formatted = formatted,
        onFormattedChange = cleanup::setReviewFormatted,
        fontFamily = fontFamily,
    )
}
