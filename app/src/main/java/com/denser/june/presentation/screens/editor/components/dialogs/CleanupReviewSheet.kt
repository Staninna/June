package com.denser.june.presentation.screens.editor.components.dialogs

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
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
import com.denser.june.core.R
import com.denser.june.core.localai.TextDiff
import com.denser.june.presentation.components.JuneFloatingAction
import com.denser.june.presentation.components.JuneFloatingActionBar
import com.denser.june.presentation.components.JuneSelectableTabChip
import com.denser.june.presentation.screens.editor.CleanupUiState

private enum class ReviewTab { Changes, Original, Rewrite }

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
    onOpenServer: (() -> Unit)?,
    onOpenSettings: () -> Unit,
) {
    if (state is CleanupUiState.Hidden) return
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val maxHeight = (LocalConfiguration.current.screenHeightDp * 0.62f).dp

    ModalBottomSheet(
        sheetState = sheetState,
        onDismissRequest = onCancel,
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
                    CleanupUiState.Working -> WorkingContent(onCancel)
                    is CleanupUiState.Problem -> ProblemContent(s, onRetry, onCancel, onOpenServer, onOpenSettings)
                    is CleanupUiState.Review -> ReviewContent(s, maxHeight, onAccept, onRetry, onCancel)
                    CleanupUiState.Hidden -> Spacer(Modifier.height(1.dp))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun WorkingContent(onCancel: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ContainedLoadingIndicator(Modifier.size(72.dp))
        Spacer(Modifier.height(20.dp))
        Text(
            stringResource(R.string.cleanup_working),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.cleanup_working_desc),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(28.dp))
        JuneFloatingActionBar {
            JuneFloatingAction(
                onClick = onCancel,
                label = stringResource(R.string.action_cancel),
                icon = { Icon(painterResource(R.drawable.close_24px), contentDescription = null) },
                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
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
    onAccept: () -> Unit,
    onRetry: () -> Unit,
    onCancel: () -> Unit,
) {
    var tab by rememberSaveable { mutableStateOf(ReviewTab.Changes) }
    val scheme = MaterialTheme.colorScheme
    val added = SpanStyle(background = scheme.primaryContainer, color = scheme.onPrimaryContainer)
    val removed =
        SpanStyle(
            background = scheme.errorContainer.copy(alpha = 0.7f),
            color = scheme.onErrorContainer,
            textDecoration = TextDecoration.LineThrough,
        )
    val mention = SpanStyle(color = scheme.primary, fontWeight = FontWeight.Medium)

    val text: AnnotatedString =
        remember(state, tab, scheme) {
            buildAnnotatedString {
                for (seg in state.segments) {
                    val style =
                        when (seg.op) {
                            TextDiff.Op.Equal -> null
                            TextDiff.Op.Added -> if (tab == ReviewTab.Original) continue else added
                            TextDiff.Op.Removed -> if (tab == ReviewTab.Rewrite) continue else removed
                        }
                    if (style != null) withStyle(style) { appendWithMentions(seg.text, mention) }
                    else appendWithMentions(seg.text, mention)
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
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(R.string.cleanup_review_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        ) {
            ReviewTab.entries.forEach { t ->
                JuneSelectableTabChip(
                    item = t,
                    label =
                        stringResource(
                            when (t) {
                                ReviewTab.Changes -> R.string.cleanup_tab_changes
                                ReviewTab.Original -> R.string.cleanup_tab_original
                                ReviewTab.Rewrite -> R.string.cleanup_tab_rewrite
                            }
                        ),
                    isSelected = tab == t,
                    onSelect = { tab = it },
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
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.verticalScroll(rememberScrollState()).padding(20.dp),
                )
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
                onClick = onAccept,
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

