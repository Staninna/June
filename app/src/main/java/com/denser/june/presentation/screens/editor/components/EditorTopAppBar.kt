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
import androidx.compose.runtime.getValue
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.denser.june.presentation.screens.editor.CleanupIndicator
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.denser.june.core.R
import com.denser.june.presentation.components.JuneCloseButton
import com.denser.june.presentation.components.JuneMoreOptionsButton
import com.denser.june.presentation.components.JuneTopAppBar
import com.denser.june.presentation.plus.LocalIsPlus
import com.denser.june.presentation.utils.UiUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorTopAppBar(
    modifier: Modifier = Modifier,
    isFontMode: Boolean,
    isColorMode: Boolean = false,
    showAddItemSheet: Boolean,
    showSaveButton: Boolean,
    isLoading: Boolean,
    onCloseClick: () -> Unit,
    onFontModeToggle: () -> Unit,
    onColorModeToggle: () -> Unit = {},
    onAddItemClick: () -> Unit,
    onSaveClick: () -> Unit,
    onOptionsClick: () -> Unit,
    isFontDirty: Boolean = false,
    onResetFont: () -> Unit = {},
    isThemeDirty: Boolean = false,
    onResetTheme: () -> Unit = {},
    isPlusStylingActive: Boolean = false,
    showCleanupAction: Boolean = false,
    onCleanupClick: () -> Unit = {},
    cleanupIndicator: CleanupIndicator = CleanupIndicator.Idle,
) {
    val isPlus = LocalIsPlus.current

    JuneTopAppBar(
        modifier = modifier,
        title = {},
        navigationIcon = {
            if (isFontMode || isColorMode) {
                JuneCloseButton(onClick = onCloseClick)
            } else {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    JuneCloseButton(onClick = onCloseClick)

                    FilledIconButton(
                        onClick = onAddItemClick,
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
                        )
                    ) {
                        Icon(
                            painterResource(if (showAddItemSheet) R.drawable.add_circle_24px_fill else R.drawable.add_circle_24px),
                            stringResource(R.string.add_attachment)
                        )
                    }

                    FilledIconButton(
                        onClick = onFontModeToggle,
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
                        )
                    ) {
                        Icon(
                            painterResource(R.drawable.brand_family_24px),
                            stringResource(R.string.journal_font)
                        )
                    }

                    FilledIconButton(
                        onClick = onColorModeToggle,
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
                        )
                    ) {
                        Icon(
                            painterResource(R.drawable.format_paint_24px),
                            stringResource(R.string.journal_theme)
                        )
                    }
                }
            }
        },
        actions = {
            if (isFontMode) {
                if (isFontDirty) {
                    FilledIconButton(
                        onClick = onResetFont,
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
                        )
                    ) {
                        Icon(
                            painterResource(R.drawable.replay_24px),
                            stringResource(R.string.reset_font)
                        )
                    }
                }
                FilledIconButton(
                    onClick = onCloseClick,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Icon(
                        painterResource(R.drawable.check_24px),
                        stringResource(R.string.action_done)
                    )
                }
            } else if (isColorMode) {
                if (isThemeDirty) {
                    FilledIconButton(
                        onClick = onResetTheme,
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
                        )
                    ) {
                        Icon(
                            painterResource(R.drawable.replay_24px),
                            stringResource(R.string.reset_theme)
                        )
                    }
                }
                FilledIconButton(
                    onClick = onCloseClick,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Icon(
                        painterResource(R.drawable.check_24px),
                        stringResource(R.string.action_done)
                    )
                }
            } else {
                if (showCleanupAction || cleanupIndicator != CleanupIndicator.Idle) {
                    CleanupButton(cleanupIndicator, enabled = !isLoading, onClick = onCleanupClick)
                }
                if (showSaveButton) {
                    Button(
                        enabled = !isLoading,
                        onClick = onSaveClick,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        modifier = Modifier.height(38.dp)
                    ) {
                        if (isPlusStylingActive && !isPlus) {
                            Icon(
                                painter = painterResource(UiUtils.PLUS_ICON_RES),
                                contentDescription = "Plus",
                                modifier = Modifier
                                    .size(20.dp)
                                    .padding(end = 4.dp)
                            )
                        }
                        Text(
                            text = stringResource(R.string.action_save),
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                    Spacer(Modifier.width(4.dp))
                }

                Box {
                    JuneMoreOptionsButton(onClick = onOptionsClick)
                }
            }
        }
    )
}

/**
 * The sparkle button. While a cleanup of this entry runs behind the minimized sheet it carries the
 * progress as a ring; when the rewrite is ready it turns into a filled check. Tapping it always
 * brings the sheet back.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun CleanupButton(indicator: CleanupIndicator, enabled: Boolean, onClick: () -> Unit) {
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
