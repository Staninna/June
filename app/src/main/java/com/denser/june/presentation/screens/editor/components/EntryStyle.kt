package com.denser.june.presentation.screens.editor.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.sp
import com.denser.hyphen.ui.style.BlockquoteStyle
import com.denser.hyphen.ui.style.HyphenStyleConfig
import com.denser.hyphen.ui.style.ListItemStyle

/**
 * How June styles the body of an entry: bold, headings, lists, quotes, mentions and so on.
 * Shared by the editor and by everything that shows an entry the way the editor does.
 */
@Composable
fun juneEntryStyleConfig(): HyphenStyleConfig =
    HyphenStyleConfig(
        boldStyle = SpanStyle(
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        ),
        italicStyle = SpanStyle(
            fontStyle = FontStyle.Italic,
            color = MaterialTheme.colorScheme.primary
        ),
        strikethroughStyle = SpanStyle(
            textDecoration = TextDecoration.LineThrough,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        ),
        highlightStyle = SpanStyle(
            background = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f),
            color = MaterialTheme.colorScheme.onSecondaryContainer
        ),
        inlineCodeStyle = SpanStyle(
            background = MaterialTheme.colorScheme.surfaceVariant,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontFamily = FontFamily.Monospace,
            fontSize = 14.sp
        ),
        blockquoteSpanStyle = SpanStyle(
            fontStyle = FontStyle.Italic,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        ),
        blockquoteStyle = BlockquoteStyle(
            backgroundColor = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.6f),
            borderColor = MaterialTheme.colorScheme.primary,
        ),
        h1Style = SpanStyle(fontSize = 28.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface),
        h2Style = SpanStyle(fontSize = 24.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface),
        h3Style = SpanStyle(fontSize = 22.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface),
        h4Style = SpanStyle(fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface),
        h5Style = SpanStyle(fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface),
        h6Style = SpanStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant, letterSpacing = 0.5.sp),
        bulletListStyle = ListItemStyle(
            prefixStyle = SpanStyle(
                color = MaterialTheme.colorScheme.primary
            )
        ),
        orderedListStyle = ListItemStyle(
            prefixStyle = SpanStyle(
                color = MaterialTheme.colorScheme.primary
            )
        ),
        linkStyle = SpanStyle(
            color = MaterialTheme.colorScheme.primary,
            textDecoration = TextDecoration.Underline,
        ),
        mentionStyles = mapOf(
            "person" to SpanStyle(
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Medium,
            ),
            "topic" to SpanStyle(
                color = MaterialTheme.colorScheme.tertiary,
                fontWeight = FontWeight.Medium,
            )
        )
    )
