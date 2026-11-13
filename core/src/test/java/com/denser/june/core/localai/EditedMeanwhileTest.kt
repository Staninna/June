package com.denser.june.core.localai

import org.junit.Assert.assertEquals
import org.junit.Test

class EditedMeanwhileTest {
    private val original = "so yeah i went to the gym\n\nthen home"

    @Test
    fun `an untouched entry was not edited`() {
        assertEquals(EditedMeanwhile.No, EditedMeanwhile.of(original, original))
        assertEquals(EditedMeanwhile.No, EditedMeanwhile.of(original, "$original\n\n  "))
    }

    @Test
    fun `a new paragraph after the text is kept as its own paragraph`() {
        val edits = EditedMeanwhile.of(original, "$original\n\nAnd I called mum.")
        assertEquals(EditedMeanwhile.Added("And I called mum.", sameParagraph = false), edits)
        assertEquals("I went to the gym.\n\nThen home.\n\nAnd I called mum.", (edits as EditedMeanwhile.Added).keep("I went to the gym.\n\nThen home.\n"))
    }

    @Test
    fun `text that carries on in the last paragraph stays in it`() {
        val edits = EditedMeanwhile.of(original, "$original and slept early")
        assertEquals(EditedMeanwhile.Added("and slept early", sameParagraph = true), edits)
        assertEquals("Then home. and slept early", (edits as EditedMeanwhile.Added).keep("Then home."))
    }

    @Test
    fun `changing the text that is being cleaned up cannot be kept`() {
        assertEquals(EditedMeanwhile.Changed, EditedMeanwhile.of(original, original.replace("gym", "pool")))
        assertEquals(EditedMeanwhile.Changed, EditedMeanwhile.of(original, "Intro. $original"))
        assertEquals(EditedMeanwhile.Changed, EditedMeanwhile.of(original, "so yeah"))
    }
}
