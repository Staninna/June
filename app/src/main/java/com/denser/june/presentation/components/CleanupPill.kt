package com.denser.june.presentation.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.denser.june.core.R
import com.denser.june.core.localai.DictationCleanup
import com.denser.june.presentation.screens.editor.CleanupSession
import com.denser.june.presentation.screens.editor.CleanupUiState

/**
 * A cleanup that is still running, or a rewrite that waits for review, while the user is somewhere
 * else in June. It floats above the bottom of every screen; a tap opens the entry and the sheet.
 */
@Composable
fun CleanupPill(snapshot: CleanupSession.Snapshot, onClick: () -> Unit, modifier: Modifier = Modifier) {
    AnimatedVisibility(
        visible = snapshot.showsPill,
        enter = slideInVertically { it } + fadeIn(),
        exit = slideOutVertically { it } + fadeOut(),
        modifier = modifier.navigationBarsPadding().imePadding().padding(bottom = 92.dp),
    ) {
        val scheme = MaterialTheme.colorScheme
        val ready = snapshot.state is CleanupUiState.Review
        Surface(
            onClick = onClick,
            enabled = snapshot.canOpenEntry,
            shape = CircleShape,
            color = if (ready) scheme.primary else scheme.surfaceContainerHigh,
            contentColor = if (ready) scheme.onPrimary else scheme.onSurface,
            shadowElevation = 8.dp,
            tonalElevation = 6.dp,
        ) {
            Row(
                Modifier.padding(start = 8.dp, end = 14.dp, top = 8.dp, bottom = 8.dp)
                    .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(28.dp), contentAlignment = Alignment.Center) {
                    val progress = (snapshot.state as? CleanupUiState.Working)?.progress
                    if (progress != null) {
                        val fraction = progress.fraction
                        if (fraction != null) {
                            val animated by animateFloatAsState(fraction, label = "pillRing")
                            CircularProgressIndicator(
                                progress = { animated },
                                modifier = Modifier.size(28.dp),
                                strokeWidth = 2.5.dp,
                                trackColor = scheme.primary.copy(alpha = 0.15f),
                            )
                        } else {
                            CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 2.5.dp)
                        }
                    }
                    Icon(
                        painterResource(if (ready) R.drawable.check_24px else R.drawable.auto_awesome_24px),
                        contentDescription = null,
                        modifier = Modifier.size(if (ready) 22.dp else 14.dp),
                    )
                }
                Spacer(Modifier.width(8.dp))
                AnimatedContent(pillTitle(snapshot.state), transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "pillTitle") { title ->
                    Text(title, style = MaterialTheme.typography.labelLarge, maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun pillTitle(state: CleanupUiState): String =
    when (state) {
        is CleanupUiState.Review -> stringResource(R.string.cleanup_ready)
        is CleanupUiState.Working -> {
            val p = state.progress
            val percent = p.fraction?.let { " " + stringResource(R.string.cleanup_detail_percent, (it * 100).toInt()) }.orEmpty()
            when (p.phase) {
                DictationCleanup.Phase.Sending -> stringResource(R.string.cleanup_pill_starting)
                DictationCleanup.Phase.Queued -> stringResource(R.string.cleanup_pill_queued)
                DictationCleanup.Phase.LoadingModel -> stringResource(R.string.cleanup_pill_loading)
                DictationCleanup.Phase.Reading -> stringResource(R.string.cleanup_pill_reading) + percent
                DictationCleanup.Phase.Writing -> stringResource(R.string.cleanup_pill_writing) + percent
            }
        }
        else -> ""
    }
