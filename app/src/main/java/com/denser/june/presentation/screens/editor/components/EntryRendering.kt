package com.denser.june.presentation.screens.editor.components

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import com.denser.hyphen.model.MarkupStyle
import com.denser.hyphen.model.MarkupStyleRange
import com.denser.hyphen.model.TriggerConfig
import com.denser.hyphen.state.HyphenTextState
import com.denser.hyphen.ui.style.HyphenStyleConfig
import com.denser.june.core.localai.TextDiff

/**
 * An entry the way June shows it: the text without Markdown marks, and which style applies where.
 * It comes from the editor's own parser, so what is shown is what the editor would show.
 */
class RenderedEntry(val text: String, val spans: List<MarkupStyleRange>)

private val CHECKBOX_MARK = Regex("""^[-*][ \u00A0]\[([ xX])\][ \u00A0]""")
private val BULLET_MARK = Regex("""^[-*][ \u00A0]""")

/** Parses [markdown] like the editor does. Checkboxes and bullets become the signs the editor draws. */
fun renderEntry(markdown: String): RenderedEntry {
    val state = HyphenTextState(
        initialText = markdown,
        initialTriggerConfigs = listOf(
            TriggerConfig(trigger = "@", scheme = "person"),
            TriggerConfig(trigger = "#", scheme = "topic")
        )
    )
    val source = state.text
    val spans = state.spans

    // "- [ ] " -> "\u2610 ", "- [x] " -> "\u2611 ", "- " -> "\u2022 "
    data class Swap(val start: Int, val length: Int, val with: String)
    val swaps = spans.mapNotNull { span ->
        if (span.start !in source.indices) return@mapNotNull null
        val line = source.substring(span.start, span.end.coerceIn(span.start, source.length))
        when (span.style) {
            MarkupStyle.CheckboxUnchecked, MarkupStyle.CheckboxChecked ->
                CHECKBOX_MARK.find(line)?.let {
                    Swap(span.start, it.value.length, if (span.style == MarkupStyle.CheckboxChecked) "\u2611 " else "\u2610 ")
                }
            MarkupStyle.BulletList -> BULLET_MARK.find(line)?.let { Swap(span.start, it.value.length, "\u2022 ") }
            else -> null
        }
    }.sortedBy { it.start }.distinctBy { it.start }
    if (swaps.isEmpty()) return RenderedEntry(source, spans)

    val text = StringBuilder()
    var last = 0
    for (swap in swaps) {
        if (swap.start < last) continue
        text.append(source, last, swap.start).append(swap.with)
        last = swap.start + swap.length
    }
    text.append(source, last, source.length)

    /** Where a place in the parsed text ended up after the swaps. */
    fun moved(index: Int): Int {
        var shift = 0
        for (swap in swaps) {
            if (index >= swap.start + swap.length) shift += swap.with.length - swap.length
            else if (index > swap.start) return swap.start + shift + minOf(index - swap.start, swap.with.length)
        }
        return index + shift
    }
    return RenderedEntry(text.toString(), spans.map { MarkupStyleRange(it.style, moved(it.start), moved(it.end)) })
}

private fun HyphenStyleConfig.styleOf(style: MarkupStyle): SpanStyle? =
    when (style) {
        MarkupStyle.Bold -> boldStyle
        MarkupStyle.Italic -> italicStyle
        MarkupStyle.Underline -> underlineStyle
        MarkupStyle.Strikethrough -> strikethroughStyle
        MarkupStyle.Highlight -> highlightStyle
        MarkupStyle.InlineCode -> inlineCodeStyle
        MarkupStyle.Blockquote -> blockquoteSpanStyle
        MarkupStyle.H1 -> h1Style
        MarkupStyle.H2 -> h2Style
        MarkupStyle.H3 -> h3Style
        MarkupStyle.H4 -> h4Style
        MarkupStyle.H5 -> h5Style
        MarkupStyle.H6 -> h6Style
        is MarkupStyle.Link -> linkStyle
        is MarkupStyle.Mention -> mentionStyles[style.scheme] ?: mentionStyle
        else -> null
    }

/**
 * Appends the part of [entry] from [from] to [to], styled like the editor. [overlay] goes on top
 * of the whole part, e.g. to mark it as added or removed.
 */
fun AnnotatedString.Builder.appendRendered(
    entry: RenderedEntry,
    from: Int,
    to: Int,
    styles: HyphenStyleConfig,
    overlay: SpanStyle? = null,
) {
    val start = from.coerceIn(0, entry.text.length)
    val end = to.coerceIn(start, entry.text.length)
    if (start == end) return
    val offset = length - start
    append(entry.text, start, end)

    fun style(style: SpanStyle, spanStart: Int, spanEnd: Int) {
        val a = maxOf(spanStart, start)
        val b = minOf(spanEnd, end)
        if (a < b) addStyle(style, a + offset, b + offset)
    }
    for (span in entry.spans) {
        if (span.end <= start || span.start >= end) continue
        styles.styleOf(span.style)?.let { style(it, span.start, span.end) }
        // the sign in front of a list line, and a ticked line, look like they do in the editor
        val signEnd = entry.text.indexOf(' ', span.start).takeIf { it in span.start until span.end }?.plus(1) ?: span.start
        val sign = when (span.style) {
            MarkupStyle.OrderedList -> styles.orderedListStyle.prefixStyle
            MarkupStyle.BulletList, MarkupStyle.CheckboxUnchecked, MarkupStyle.CheckboxChecked -> styles.bulletListStyle.prefixStyle
            else -> null
        }
        sign?.let { style(it, span.start, signEnd) }
        when (span.style) {
            MarkupStyle.CheckboxUnchecked -> styles.checkboxUncheckedStyle?.let { style(it, signEnd, span.end) }
            MarkupStyle.CheckboxChecked -> styles.checkboxCheckedStyle?.let { style(it, signEnd, span.end) }
            else -> Unit
        }
    }
    if (overlay != null) addStyle(overlay, start + offset, end + offset)
}

/** Which text a review shows: the rewrite with the changes marked, or one of the two texts. */
enum class ReviewView { Changes, Original, Rewrite }

/** The original and the rewrite as June shows them, and what changed between them in that form. */
class RenderedReview(val original: RenderedEntry, val rewrite: RenderedEntry, val segments: List<TextDiff.Segment>) {
    companion object {
        fun of(original: String, rewrite: String): RenderedReview {
            val before = renderEntry(original)
            val after = renderEntry(rewrite)
            return RenderedReview(before, after, TextDiff.readable(TextDiff.diff(before.text, after.text)))
        }
    }
}

/**
 * Appends [review] for [view]. Each text is walked in step with the changes, so every part keeps
 * the formatting it has in its own text: a removed part looks like the original, the rest like
 * the rewrite. Only [ReviewView.Changes] marks what was [added] and [removed].
 */
fun AnnotatedString.Builder.appendReview(
    review: RenderedReview,
    view: ReviewView,
    styles: HyphenStyleConfig,
    added: SpanStyle,
    removed: SpanStyle,
) {
    var inOriginal = 0
    var inRewrite = 0
    for (seg in review.segments) {
        when (seg.op) {
            TextDiff.Op.Equal ->
                if (view == ReviewView.Original) appendRendered(review.original, inOriginal, inOriginal + seg.oldText.length, styles)
                else appendRendered(review.rewrite, inRewrite, inRewrite + seg.text.length, styles)
            // White space that came or went is laid out, never marked: a struck-through blank
            // reads as a stray dash, and a removed one has nothing to show in Changes. Neither has
            // a name that only became a link: the link shows as added, the plain name not at all.
            TextDiff.Op.Added ->
                if (view != ReviewView.Original) {
                    val mark = added.takeIf { view == ReviewView.Changes && seg.text.isNotBlank() }
                    appendRendered(review.rewrite, inRewrite, inRewrite + seg.text.length, styles, mark)
                }
            TextDiff.Op.Removed ->
                if (view == ReviewView.Original || (view == ReviewView.Changes && !seg.quiet && seg.oldText.isNotBlank())) {
                    appendRendered(review.original, inOriginal, inOriginal + seg.oldText.length, styles, removed.takeIf { view == ReviewView.Changes })
                }
        }
        if (seg.op != TextDiff.Op.Added) inOriginal += seg.oldText.length
        if (seg.op != TextDiff.Op.Removed) inRewrite += seg.text.length
    }
}
