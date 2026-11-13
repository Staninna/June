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
    fun `known people merge tags and entry people`() {
        assertEquals(
            listOf("Emma", "Noor", "Sam de Vries"),
            DictationPrompt.knownPeople(listOf("@Emma", "#work"), listOf("emma", "Sam de Vries", " ", "Noor")),
        )
    }

    @Test
    fun `people list reads naturally`() {
        assertEquals("Emma", DictationPrompt.peopleList(listOf("Emma")))
        assertEquals("Emma and Noor", DictationPrompt.peopleList(listOf("Emma", "Noor")))
        assertEquals("Emma, Noor and Sam", DictationPrompt.peopleList(listOf("Emma", "Noor", "Sam")))
        assertTrue(DictationPrompt.peopleList(emptyList()).isNotBlank())
    }

    @Test
    fun `the full prompt has every rule and the people last`() {
        val p = DictationPrompt.buildSystemPrompt(DictationPrompt.DEFAULT_SYSTEM_PROMPT, listOf("Emma", "Noor"))
        assertTrue(p.startsWith("You rewrite my voice-dictated notes into a clean journal entry for my journal app."))
        assertFalse(p.contains("{{PEOPLE}}"))
        assertEquals(9, Regex("""(?m)^\d\. """).findAll(p).count())
        assertTrue(p.lines().last().startsWith("9. Names with @ in front"))
        assertTrue(p.contains("My friends are Emma and Noor."))
    }

    @Test
    fun `an entry only gets the rules it needs`() {
        val plain = DictationPrompt.buildSystemPrompt(DictationPrompt.DEFAULT_SYSTEM_PROMPT, listOf("Emma"), "so uh today i went out")
        assertEquals(7, Regex("""(?m)^\d\. """).findAll(plain).count())
        assertFalse(plain.contains("topic tags"))
        assertFalse(plain.contains("~~"))

        val rich = DictationPrompt.buildSystemPrompt(DictationPrompt.DEFAULT_SYSTEM_PROMPT, listOf("Emma"), "I *really* went out #carnaval")
        assertEquals(9, Regex("""(?m)^\d\. """).findAll(rich).count())
        assertTrue(rich.contains("7. Keep the marks"))
        assertTrue(rich.contains("8. Words that start with #"))

        // everything before the people is the same text, so the server can reuse it
        val other = DictationPrompt.buildSystemPrompt(DictationPrompt.DEFAULT_SYSTEM_PROMPT, listOf("Noor", "Sam"), "another plain entry")
        assertEquals(plain.substringBefore("My friends are"), other.substringBefore("My friends are"))
    }

    @Test
    fun `a prompt the user wrote is only filled in`() {
        val mine = "Rewrite this. Friends: {{PEOPLE}}."
        assertEquals("Rewrite this. Friends: Emma.", DictationPrompt.buildSystemPrompt(mine, listOf("Emma"), "text with #tag and *marks*"))
    }

    @Test
    fun `link codes become simple tags for the model`() {
        val s =
            DictationPrompt.simplify(
                "Out with [@Emma](person:Emma) and [@Sam de Vries](person:Sam de Vries).\n\n[#awake](topic:awake) [#zininin](topic:zininin) "
            )
        assertEquals("Out with @Emma and @Sam de Vries.\n\n#awake #zininin ", s.text)
        assertEquals(mapOf("#awake" to "awake", "#zininin" to "zininin"), s.topics)
        assertEquals("no codes here", DictationPrompt.simplify("no codes here").text)
    }

    @Test
    fun `a long people list is narrowed to the ones the entry names`() {
        val many = (1..80).map { "Person$it" } + listOf("Emma", "Krijn", "Zoë")
        assertEquals(many.distinct().size, DictationPrompt.relevantPeople(many, "anything", limit = 100).size)
        val picked = DictationPrompt.relevantPeople(many, "went out with ema and krijn", limit = 10)
        assertEquals(10, picked.size)
        assertTrue("an exact and a misheard name are both kept", picked.containsAll(listOf("Emma", "Krijn")))
    }
}
