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
    private fun shown(segments: List<TextDiff.Segment>) =
        segments.joinToString("") {
            when (it.op) {
                Op.Equal -> it.text
                Op.Added -> "{+${it.text}}"
                Op.Removed -> if (it.quiet) "" else "{-${it.oldText}}"
            }
        }

    private fun readable(old: String, new: String) = TextDiff.readable(TextDiff.diff(old, new))

    @Test
    fun `readable segments still rebuild both texts`() {
        val old = "found it in Emma's bag Thank you.\n---\nSunday. ate a kapsalon at around round 11 no round 22 30"
        val new = "found it in [@Emma](person:e1)'s bag.\n\nSunday. Ate a kapsalon at around 22:30."
        val d = readable(old, new)
        assertEquals(new, rebuild(d, Op.Added))
        assertEquals(old, d.filter { it.op != Op.Added }.joinToString("") { it.oldText })
    }

    @Test
    fun `a change inside a run of characters takes the whole run`() {
        assertEquals("at {-22 30}{+22:30}.", shown(readable("at 22 30.", "at 22:30.")))
    }

    @Test
    fun `changes with only white space between them read as one`() {
        assertEquals("at around {-round 11 no round 22 30}{+22:30}", shown(readable("at around round 11 no round 22 30", "at around 22:30")))
    }

    @Test
    fun `a name that only became a link shows as the link alone`() {
        assertEquals("with {+[@Emma](person:e1)} and", shown(readable("with Emma and", "with [@Emma](person:e1) and")))
        assertEquals("in {+[@Emma](person:e1)}'s bag", shown(readable("in Emma's bag", "in [@Emma](person:e1)'s bag")))
        assertEquals("in {+@Emma}'s bag", shown(readable("in Emma's bag", "in @Emma's bag")))
        // as the entry shows it: the name gains an @
        assertEquals("with {+@Emma} and", shown(readable("with Emma and", "with @Emma and")))
    }

    @Test
    fun `removed punctuation and words are not glued to their neighbours`() {
        assertEquals("bag{- Thank you}.", shown(readable("bag Thank you.", "bag.")))
        assertEquals("{-ate}{+Ate} a", shown(readable("ate a", "Ate a")))
    }

    @Test
    fun `the change count is what a person would see`() {
        val d = readable("with Emma and Joris. ate a kapsalon at 22 30", "with [@Emma](person:e1) and Joris. Ate a kapsalon at 22:30.")
        assertEquals(3, TextDiff.changeCount(d))
        assertEquals(0, TextDiff.changeCount(readable("Same.", "Same.")))
    }
}
