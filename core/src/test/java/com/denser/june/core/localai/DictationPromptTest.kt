package com.denser.june.core.localai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DictationPromptTest {
    @Test
    fun `people come from People tags only`() {
        val tags = listOf("@Noor", "#work", "Home", "@Emma", "@emma", "@ ")
        assertEquals(listOf("Emma", "emma", "Noor"), DictationPrompt.peopleFromTags(tags))
    }

    @Test
    fun `people list reads naturally`() {
        assertEquals("Emma", DictationPrompt.peopleList(listOf("Emma")))
        assertEquals("Emma and Noor", DictationPrompt.peopleList(listOf("Emma", "Noor")))
        assertEquals("Emma, Noor and Sam", DictationPrompt.peopleList(listOf("Emma", "Noor", "Sam")))
        assertTrue(DictationPrompt.peopleList(emptyList()).isNotBlank())
    }

    @Test
    fun `default prompt substitutes people and keeps the rules`() {
        val p = DictationPrompt.buildSystemPrompt(DictationPrompt.DEFAULT_SYSTEM_PROMPT, listOf("Emma", "Noor"))
        assertTrue(p.contains("6. My friends are Emma and Noor. Write their names exactly like [@Emma](person:Emma)."))
        assertFalse(p.contains("{{PEOPLE}}"))
        assertTrue(p.startsWith("You rewrite my voice-dictated notes into a clean journal entry for my journal app."))
        assertTrue(p.endsWith("Write times like 15:45."))
        assertEquals(7, Regex("""(?m)^\d\. """).findAll(p).count())
    }
}
