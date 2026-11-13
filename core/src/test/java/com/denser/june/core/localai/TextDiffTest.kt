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
}
