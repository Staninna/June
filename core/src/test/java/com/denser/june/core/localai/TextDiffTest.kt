package com.denser.june.core.localai

import com.denser.june.core.localai.TextDiff.Op
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TextDiffTest {
    private fun rebuild(segments: List<TextDiff.Segment>, keep: Op) =
        segments.filter { it.op == Op.Equal || it.op == keep }.joinToString("") { it.text }

    @Test
    fun `segments rebuild both texts`() {
        val old = "so uh i went to albert hine with [@Emma](person:Emma)"
        val new = "So I went to Albert Heijn with [@Emma](person:Emma)."
        val d = TextDiff.diff(old, new)
        assertEquals(old, rebuild(d, Op.Removed).replace(Regex("\\s+"), " "))
        assertEquals(new, rebuild(d, Op.Added))
    }

    @Test
    fun `mentions stay whole tokens`() {
        assertTrue(TextDiff.tokenize("hi [@Emma](person:Emma)!").contains("[@Emma](person:Emma)"))
    }

    @Test
    fun `identical text has no changes`() {
        assertTrue(TextDiff.diff("Same text.", "Same text.").all { it.op == Op.Equal })
    }

    @Test
    fun `huge inputs fall back without crashing`() {
        val d = TextDiff.diff("a ".repeat(3000), "b ".repeat(3000), maxCells = 1000)
        assertEquals(listOf(Op.Removed, Op.Added), d.map { it.op })
    }

    @Test
    fun `a mention that reads the same is not a change when only its id differs`() {
        val segments = TextDiff.diff("with [@Emma](person:Emma) and Krijn", "with [@Emma](person:7f3a-id) and Krijn")
        assertEquals(listOf(TextDiff.Op.Equal), segments.map { it.op })
        // the new text is what is kept
        assertEquals("with [@Emma](person:7f3a-id) and Krijn", segments.joinToString("") { it.text })
        // another name is a change
        assertTrue(TextDiff.diff("[@Emma](person:a)", "[@Noor](person:a)").any { it.op != TextDiff.Op.Equal })
    }
}
