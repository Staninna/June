package com.denser.june.presentation.screens.editor.components.dialogs

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.runtime.produceState
import androidx.compose.ui.semantics.clearAndSetSemantics
import com.denser.june.core.localai.DictationCleanup
import com.denser.june.core.localai.EditedMeanwhile
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.ui.text.style.TextOverflow
import com.denser.june.presentation.components.JuneDialog
import kotlinx.coroutines.delay
import com.denser.june.core.R
import com.denser.june.core.localai.TextDiff
import com.denser.june.presentation.components.JuneFloatingAction
import com.denser.june.presentation.components.JuneFloatingActionBar
import com.denser.june.presentation.components.JuneSelectableTabChip
import com.denser.june.presentation.screens.editor.CleanupUiState
import com.denser.june.presentation.screens.editor.components.RenderedReview
import com.denser.june.presentation.screens.editor.components.ReviewView
import com.denser.june.presentation.screens.editor.components.appendReview
import com.denser.june.presentation.screens.editor.components.juneEntryStyleConfig
import androidx.compose.material3.FilledTonalIconToggleButton
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontFamily
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val MENTION = Regex("""\[@([^\]]*)]\(person:[^)]*\)""")

/**
 * Bottom sheet for "Clean up dictation": progress, a friendly problem state, or a review of the
 * original against the rewrite. Only [onAccept] changes the entry.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun CleanupReviewSheet(
    state: CleanupUiState,
    onAccept: () -> Unit,
    onRetry: () -> Unit,
    onCancel: () -> Unit,
    onMinimize: () -> Unit,
    /** What was written in the entry since the cleanup started. */
    edits: EditedMeanwhile,
    onOpenServer: (() -> Unit)?,
    onOpenSettings: () -> Unit,
    /** Show the texts as in the entry (true) or as Markdown (false). */
    formatted: Boolean = true,
    onFormattedChange: (Boolean) -> Unit = {},
    /** The font of the entry, so the review reads like the entry will. */
    fontFamily: FontFamily? = null,
) {
    if (state is CleanupUiState.Hidden) return
    // Swiping the sheet down, a tap next to it or Back only puts it away: the cleanup carries on
    // (or the rewrite keeps waiting) and shows on the sparkle button. Only the buttons end it.
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val maxHeight = (LocalConfiguration.current.screenHeightDp * 0.62f).dp

    ModalBottomSheet(
        sheetState = sheetState,
        onDismissRequest = { if (state is CleanupUiState.Problem) onCancel() else onMinimize() },
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        dragHandle = null,
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 16.dp).navigationBarsPadding(),
        ) {
            Box(
                Modifier.padding(top = 16.dp).align(Alignment.CenterHorizontally).width(36.dp).height(4.dp)
                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f), CircleShape)
            )
            AnimatedContent(
                targetState = state,
                contentKey = { it::class },
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "cleanup",
            ) { s ->
                when (s) {
                    is CleanupUiState.Working -> WorkingContent(s, onCancel, onMinimize)
                    is CleanupUiState.Problem -> ProblemContent(s, onRetry, onCancel, onOpenServer, onOpenSettings)
                    is CleanupUiState.Review ->
                        ReviewContent(s, maxHeight, edits, onAccept, onRetry, onCancel, formatted, onFormattedChange, fontFamily)
                    CleanupUiState.Hidden -> Spacer(Modifier.height(1.dp))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun WorkingContent(state: CleanupUiState.Working, onCancel: () -> Unit, onMinimize: () -> Unit) {
    val progress = state.progress
    val now by
        produceState(System.currentTimeMillis(), state.startedAt) {
            while (true) {
                value = System.currentTimeMillis()
                delay(1_000)
            }
        }
    val title =
        when (progress.phase) {
            DictationCleanup.Phase.Sending -> stringResource(R.string.cleanup_working)
            DictationCleanup.Phase.Queued -> stringResource(R.string.cleanup_phase_queued)
            DictationCleanup.Phase.LoadingModel -> stringResource(R.string.cleanup_phase_loading)
            DictationCleanup.Phase.Reading -> stringResource(R.string.cleanup_phase_reading)
            DictationCleanup.Phase.Writing -> stringResource(R.string.cleanup_phase_writing)
        }
    val detail =
        when (progress.phase) {
            DictationCleanup.Phase.Queued ->
                progress.queuePosition?.let { stringResource(R.string.cleanup_detail_queue, it) }
            DictationCleanup.Phase.Reading ->
                progress.fraction?.let { stringResource(R.string.cleanup_detail_percent, (it * 100).toInt()) }
            DictationCleanup.Phase.Writing ->
                progress.tokensPerSecond?.let {
                    stringResource(R.string.cleanup_detail_writing_speed, progress.completionTokens, it)
                } ?: stringResource(R.string.cleanup_detail_writing, progress.completionTokens)
            else -> null
        }
    val elapsed = ((now - state.startedAt) / 1000).coerceAtLeast(0)
    val elapsedText = "%d:%02d".format(elapsed / 60, elapsed % 60)
    val steps =
        listOf(
            DictationCleanup.Phase.Queued to R.string.cleanup_step_queue,
            DictationCleanup.Phase.LoadingModel to R.string.cleanup_step_load,
            DictationCleanup.Phase.Reading to R.string.cleanup_step_read,
            DictationCleanup.Phase.Writing to R.string.cleanup_step_write,
        )
    val current = steps.indexOfFirst { it.first == progress.phase }

    Column(
        Modifier.fillMaxWidth().padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ContainedLoadingIndicator(Modifier.size(72.dp))
        Spacer(Modifier.height(20.dp))
        Column(
            Modifier.semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AnimatedContent(title, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "phase") { text ->
                Text(text, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            }
            Spacer(Modifier.height(4.dp))
            Text(
                listOfNotNull(detail, elapsedText).joinToString(" · "),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        Spacer(Modifier.height(20.dp))

        // Queue, Load, Read, Write: where the request is on the server
        Row(Modifier.fillMaxWidth().clearAndSetSemantics {}, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            steps.forEachIndexed { i, (_, label) ->
                val color by
                    animateColorAsState(
                        when {
                            current < 0 -> MaterialTheme.colorScheme.surfaceContainerHighest
                            i < current -> MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
                            i == current -> MaterialTheme.colorScheme.primary
                            else -> MaterialTheme.colorScheme.surfaceContainerHighest
                        },
                        label = "step",
                    )
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.fillMaxWidth().height(6.dp).background(color, CircleShape))
                    Spacer(Modifier.height(6.dp))
                    Text(
                        stringResource(label),
                        style = MaterialTheme.typography.labelMedium,
                        color =
                            if (i == current) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        val fraction = progress.fraction
        if (fraction != null) {
            val animated by animateFloatAsState(fraction, label = "progress")
            LinearWavyProgressIndicator(progress = { animated }, modifier = Modifier.fillMaxWidth())
        } else {
            LinearWavyProgressIndicator(Modifier.fillMaxWidth())
        }
        Spacer(Modifier.height(28.dp))
        JuneFloatingActionBar {
            JuneFloatingAction(
                onClick = onCancel,
                label = stringResource(R.string.action_cancel),
                icon = { Icon(painterResource(R.drawable.close_24px), contentDescription = null) },
                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            JuneFloatingAction(
                onClick = onMinimize,
                label = stringResource(R.string.cleanup_minimize),
                icon = { Icon(painterResource(R.drawable.keyboard_arrow_down_24px), contentDescription = null) },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ProblemContent(
    state: CleanupUiState.Problem,
    onRetry: () -> Unit,
    onCancel: () -> Unit,
    onOpenServer: (() -> Unit)?,
    onOpenSettings: () -> Unit,
) {
    val (title, body) =
        when (state.kind) {
            CleanupUiState.Kind.ServerUnavailable -> R.string.cleanup_unavailable_title to stringResource(R.string.cleanup_unavailable_desc)
            CleanupUiState.Kind.NotPaired -> R.string.cleanup_not_paired_title to stringResource(R.string.cleanup_not_paired_desc)
            CleanupUiState.Kind.NoModel -> R.string.cleanup_no_model_title to stringResource(R.string.cleanup_no_model_desc)
            CleanupUiState.Kind.Busy -> R.string.cleanup_busy_title to stringResource(R.string.cleanup_busy_desc)
            CleanupUiState.Kind.TooLong -> R.string.cleanup_too_long_title to stringResource(R.string.cleanup_too_long_desc)
            CleanupUiState.Kind.Empty -> R.string.cleanup_empty_title to stringResource(R.string.cleanup_empty_desc)
            CleanupUiState.Kind.Stopped -> R.string.cleanup_stopped_title to stringResource(R.string.cleanup_stopped_desc)
            CleanupUiState.Kind.Error -> R.string.cleanup_error_title to (state.message ?: "")
        }
    val wantsServer = state.kind == CleanupUiState.Kind.ServerUnavailable || state.kind == CleanupUiState.Kind.NoModel
    Column(
        Modifier.fillMaxWidth().padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Surface(
            shape = MaterialShapes.Cookie9Sided.toShape(),
            color = MaterialTheme.colorScheme.secondaryContainer,
            modifier = Modifier.size(72.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    painterResource(R.drawable.auto_awesome_24px),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(32.dp),
                )
            }
        }
        Spacer(Modifier.height(20.dp))
        Text(
            stringResource(title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { heading(); liveRegion = LiveRegionMode.Polite },
        )
        if (body.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(
                body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        Spacer(Modifier.height(28.dp))
        JuneFloatingActionBar {
            JuneFloatingAction(
                onClick = onCancel,
                label = stringResource(R.string.action_cancel),
                icon = { Icon(painterResource(R.drawable.close_24px), contentDescription = null) },
                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            when {
                wantsServer && onOpenServer != null ->
                    JuneFloatingAction(
                        onClick = onOpenServer,
                        label = stringResource(R.string.local_ai_open_server),
                        icon = { Icon(painterResource(R.drawable.open_in_new_24px), contentDescription = null) },
                    )
                state.kind == CleanupUiState.Kind.NotPaired ->
                    JuneFloatingAction(
                        onClick = onOpenSettings,
                        label = stringResource(R.string.cleanup_open_settings),
                        icon = { Icon(painterResource(R.drawable.auto_awesome_24px), contentDescription = null) },
                    )
                else ->
                    JuneFloatingAction(
                        onClick = onRetry,
                        label = stringResource(R.string.cleanup_retry),
                        icon = { Icon(painterResource(R.drawable.replay_24px), contentDescription = null) },
                    )
            }
        }
    }
}

@Composable
private fun ReviewContent(
    state: CleanupUiState.Review,
    maxHeight: androidx.compose.ui.unit.Dp,
    edits: EditedMeanwhile,
    onAccept: () -> Unit,
    onRetry: () -> Unit,
    onCancel: () -> Unit,
    formatted: Boolean,
    onFormattedChange: (Boolean) -> Unit,
    fontFamily: FontFamily?,
) {
    var tab by rememberSaveable { mutableStateOf(ReviewView.Changes) }
    var confirmReplace by remember { mutableStateOf(false) }

    // The earlier text was changed after it was sent: accepting would undo those changes. Ask.
    if (confirmReplace) {
        JuneDialog(
            onDismissRequest = { confirmReplace = false },
            title = stringResource(R.string.cleanup_replace_title),
            icon = R.drawable.edit_24px,
            text = { Text(stringResource(R.string.cleanup_replace_desc)) },
            confirmButton = {
                Button(onClick = {
                    confirmReplace = false
                    onRetry()
                }) { Text(stringResource(R.string.cleanup_again)) }
            },
            dismissButton = {
                OutlinedButton(onClick = {
                    confirmReplace = false
                    onAccept()
                }) { Text(stringResource(R.string.cleanup_replace)) }
            },
        )
    }
    val scheme = MaterialTheme.colorScheme
    val added = SpanStyle(background = scheme.primaryContainer, color = scheme.onPrimaryContainer)
    val removed =
        SpanStyle(
            background = scheme.errorContainer.copy(alpha = 0.7f),
            color = scheme.onErrorContainer,
            textDecoration = TextDecoration.LineThrough,
        )
    val mention = SpanStyle(color = scheme.primary, fontWeight = FontWeight.Medium)
    val entryStyles = juneEntryStyleConfig()

    // Parsing and comparing a long entry takes a moment: not on the frame that opens the sheet.
    val review by
        produceState<RenderedReview?>(null, state.original, state.rewrite) {
            value = withContext(Dispatchers.Default) { RenderedReview.of(state.original, state.rewrite) }
        }
    val shown = review.takeIf { formatted }

    val text: AnnotatedString =
        remember(state, tab, scheme, shown, entryStyles) {
            buildAnnotatedString {
                if (shown != null) {
                    appendReview(shown, tab, entryStyles, added, removed)
                } else {
                    for (seg in state.segments) {
                        val style =
                            when (seg.op) {
                                TextDiff.Op.Equal -> null
                                TextDiff.Op.Added -> if (tab == ReviewView.Original) continue else added.takeIf { tab == ReviewView.Changes }
                                TextDiff.Op.Removed -> if (tab == ReviewView.Rewrite) continue else removed.takeIf { tab == ReviewView.Changes }
                            }
                        if (style != null) withStyle(style) { appendWithMentions(seg.text, mention) }
                        else appendWithMentions(seg.text, mention)
                    }
                }
            }
        }

    Column(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                stringResource(R.string.cleanup_review_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.semantics { heading() },
            )
            if (edits == EditedMeanwhile.Changed) {
                Spacer(Modifier.height(12.dp))
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = scheme.errorContainer,
                    contentColor = scheme.onErrorContainer,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                ) {
                    Text(
                        stringResource(R.string.cleanup_changed),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    )
                }
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Row(
                Modifier.weight(1f).horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ReviewView.entries.forEach { t ->
                    JuneSelectableTabChip(
                        item = t,
                        label =
                            stringResource(
                                when (t) {
                                    ReviewView.Changes -> R.string.cleanup_tab_changes
                                    ReviewView.Original -> R.string.cleanup_tab_original
                                    ReviewView.Rewrite -> R.string.cleanup_tab_rewrite
                                }
                            ),
                        isSelected = tab == t,
                        onSelect = { tab = it },
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            // As in the entry, or the Markdown behind it. The icon shows what a tap switches to.
            val switchLabel = stringResource(if (formatted) R.string.cleanup_view_markdown else R.string.cleanup_view_formatted)
            FilledTonalIconToggleButton(
                checked = !formatted,
                onCheckedChange = { onFormattedChange(!it) },
                modifier = Modifier.semantics { stateDescription = switchLabel },
            ) {
                Icon(
                    painterResource(if (formatted) R.drawable.markdown_24px else R.drawable.article_24px),
                    contentDescription = switchLabel,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = scheme.surfaceContainer,
            modifier = Modifier.fillMaxWidth().heightIn(max = maxHeight),
        ) {
            SelectionContainer {
                Text(
                    text,
                    style =
                        if (formatted) {
                            // the entry's own font and text colour, as the editor uses them
                            MaterialTheme.typography.bodyLarge.copy(
                                fontFamily = fontFamily ?: MaterialTheme.typography.bodyLarge.fontFamily,
                                color = scheme.onSurface.copy(alpha = 0.8f),
                            )
                        } else {
                            MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace)
                        },
                    modifier = Modifier.verticalScroll(rememberScrollState()).padding(20.dp),
                )
            }
        }
        if (edits is EditedMeanwhile.Added) {
            // Written while the cleanup ran: not in the rewrite, and not lost. It goes after it.
            Spacer(Modifier.height(8.dp))
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = scheme.tertiaryContainer.copy(alpha = 0.6f),
                contentColor = scheme.onTertiaryContainer,
                modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) {},
            ) {
                Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
                    Text(stringResource(R.string.cleanup_kept), style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.height(2.dp))
                    Text(
                        edits.text,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        if (state.changes.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text(
                state.changes.joinToString(" · ").replaceFirstChar { it.uppercase() },
                style = MaterialTheme.typography.labelMedium,
                color = scheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 8.dp),
            )
        }
        Spacer(Modifier.height(20.dp))
        JuneFloatingActionBar(Modifier.align(Alignment.CenterHorizontally)) {
            JuneFloatingAction(
                onClick = onCancel,
                label = stringResource(R.string.action_cancel),
                icon = { Icon(painterResource(R.drawable.close_24px), contentDescription = null) },
                containerColor = scheme.surfaceContainerHighest,
                contentColor = scheme.onSurfaceVariant,
            )
            JuneFloatingAction(
                onClick = onRetry,
                label = stringResource(R.string.cleanup_retry),
                icon = { Icon(painterResource(R.drawable.replay_24px), contentDescription = null) },
                containerColor = scheme.secondaryContainer,
                contentColor = scheme.onSecondaryContainer,
            )
            JuneFloatingAction(
                onClick = { if (edits == EditedMeanwhile.Changed) confirmReplace = true else onAccept() },
                label = stringResource(R.string.cleanup_accept),
                icon = { Icon(painterResource(R.drawable.check_24px), contentDescription = null) },
            )
        }
    }
}

/** Shows `[@Emma](person:Emma)` as a styled "@Emma", like the editor does. */
private fun AnnotatedString.Builder.appendWithMentions(text: String, mentionStyle: SpanStyle) {
    var last = 0
    for (m in MENTION.findAll(text)) {
        append(text.substring(last, m.range.first))
        withStyle(mentionStyle) { append("@" + m.groupValues[1].removePrefix("@")) }
        last = m.range.last + 1
    }
    append(text.substring(last))
}

