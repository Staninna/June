package com.denser.june.presentation.screens.editor.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.denser.june.core.R
import com.denser.june.presentation.screens.editor.CleanupIndicator

/**
 * The sparkle button. While a cleanup of this entry runs behind the minimized sheet it carries the
 * progress as a ring; when the rewrite is ready it turns into a filled check. Tapping it always
 * brings the sheet back.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun CleanupButton(indicator: CleanupIndicator, enabled: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val ready = indicator == CleanupIndicator.Ready
    val container by
        animateColorAsState(
            when (indicator) {
                CleanupIndicator.Ready -> scheme.primary
                CleanupIndicator.Problem -> scheme.errorContainer
                else -> scheme.surfaceContainerLow
            },
            label = "cleanupContainer",
        )
    val content by
        animateColorAsState(
            when (indicator) {
                CleanupIndicator.Ready -> scheme.onPrimary
                CleanupIndicator.Problem -> scheme.onErrorContainer
                CleanupIndicator.BusyElsewhere -> scheme.onSurfaceVariant.copy(alpha = 0.6f)
                else -> scheme.primary
            },
            label = "cleanupContent",
        )
    val description =
        when (indicator) {
            is CleanupIndicator.Working ->
                indicator.progress.fraction?.let { stringResource(R.string.cleanup_button_working_percent, (it * 100).toInt()) }
                    ?: stringResource(R.string.cleanup_button_working)
            CleanupIndicator.Ready -> stringResource(R.string.cleanup_button_ready)
            CleanupIndicator.Problem -> stringResource(R.string.cleanup_button_problem)
            CleanupIndicator.BusyElsewhere -> stringResource(R.string.cleanup_busy_elsewhere)
            CleanupIndicator.Idle -> stringResource(R.string.clean_up_dictation)
        }
    Box(contentAlignment = Alignment.Center) {
        FilledIconButton(
            onClick = onClick,
            enabled = enabled,
            colors = IconButtonDefaults.filledIconButtonColors(containerColor = container, contentColor = content),
            modifier = Modifier.semantics { contentDescription = description },
        ) {
            AnimatedContent(ready, transitionSpec = { (scaleIn() + fadeIn()) togetherWith (scaleOut() + fadeOut()) }, label = "cleanupIcon") { done ->
                Icon(
                    painterResource(if (done) R.drawable.check_24px else R.drawable.auto_awesome_24px),
                    contentDescription = null,
                )
            }
        }
        if (indicator is CleanupIndicator.Working) {
            val fraction = indicator.progress.fraction
            if (fraction != null) {
                val animated by animateFloatAsState(fraction, label = "cleanupRing")
                CircularProgressIndicator(
                    progress = { animated },
                    modifier = Modifier.size(44.dp),
                    strokeWidth = 2.5.dp,
                    trackColor = scheme.primary.copy(alpha = 0.15f),
                )
            } else {
                CircularProgressIndicator(modifier = Modifier.size(44.dp), strokeWidth = 2.5.dp)
            }
        }
    }
}
