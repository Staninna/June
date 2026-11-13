package com.denser.june.presentation.screens.editor.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import com.denser.hyphen.ui.style.HyphenStyleConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EntryRenderingTest {
    private val bold = SpanStyle(fontWeight = FontWeight.Bold)
    private val styles = HyphenStyleConfig(boldStyle = bold)
    private val added = SpanStyle(background = Color.Green)
    private val removed = SpanStyle(background = Color.Red)

    private fun show(review: RenderedReview, view: ReviewView) =
        buildAnnotatedString { appendReview(review, view, styles, added, removed) }

    @Test
    fun `an entry is shown without its Markdown marks`() {
        val entry = renderEntry("# Day one\n\nMet **the team** with [@Bart](person:b1) about [#work](topic:work).")
        assertEquals("Day one\n\nMet the team with @Bart about #work.", entry.text)
    }

    @Test
    fun `checkboxes and bullets become signs and styles stay on the right words`() {
        val entry = renderEntry("- [ ] call **mum**\n- [x] done\n- plain")
        assertEquals("\u2610 call mum\n\u2611 done\n\u2022 plain", entry.text)
        val shown = buildAnnotatedString { appendRendered(entry, 0, entry.text.length, styles) }
        val boldRange = shown.spanStyles.single { it.item == bold }
        assertEquals("mum", shown.text.substring(boldRange.start, boldRange.end))
    }

    @Test
    fun `each view shows its own text, and only Changes marks what changed`() {
        val review = RenderedReview.of(
            original = "so yeah i went to **the gym** with [@Bart](person:b1)\n\n- [ ] buy milk",
            rewrite = "I went to **the gym** with [@Bart](person:b1).\n\n- [ ] buy milk",
        )
        val original = show(review, ReviewView.Original)
        val rewrite = show(review, ReviewView.Rewrite)
        val changes = show(review, ReviewView.Changes)

        assertEquals(review.original.text, original.text)
        assertEquals(review.rewrite.text, rewrite.text)
        assertEquals("I went to the gym with @Bart.\n\n\u2610 buy milk", rewrite.text)
        assertTrue(original.spanStyles.none { it.item == added || it.item == removed })
        assertTrue(rewrite.spanStyles.none { it.item == added || it.item == removed })

        // formatting survives in every view
        listOf(original, rewrite, changes).forEach { shown ->
            val range = shown.spanStyles.first { it.item == bold }
            assertEquals("the gym", shown.text.substring(range.start, range.end))
        }

        val removedText = changes.spanStyles.filter { it.item == removed }.joinToString("") { changes.text.substring(it.start, it.end) }
        val addedText = changes.spanStyles.filter { it.item == added }.joinToString("") { changes.text.substring(it.start, it.end) }
        assertTrue(removedText.contains("so yeah"))
        assertTrue(addedText.contains("I"))
        assertFalse(changes.text.contains("**"))
    }
}
