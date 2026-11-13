package com.denser.june.core.localai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DictationPromptTest {
    @Test
    fun `names are listed once, however many people share them`() {
        val people = listOf(
            CleanupPerson("1", "Noor"), CleanupPerson("2", "Bart", "Brother"), CleanupPerson("3", "bart", "Work"), CleanupPerson("4", " "),
        )
        assertEquals(listOf("Bart", "Noor"), DictationPrompt.names(people))
    }

    @Test
    fun `the rule about numbered names is only sent when the entry needs it`() {
        val plain = DictationPrompt.buildSystemPrompt(DictationPrompt.DEFAULT_SYSTEM_PROMPT, listOf("Bart"), "Met @Bart.")
        val numbered = DictationPrompt.buildSystemPrompt(DictationPrompt.DEFAULT_SYSTEM_PROMPT, listOf("Bart", "Bart2"), "@Bart met @Bart2.", numberedNames = true)
        assertFalse(plain.contains("Bart2"))
        assertFalse(DictationPrompt.DEFAULT_SYSTEM_PROMPT.contains("share a name"))
        assertTrue(numbered.contains("share a name"))
        assertTrue(numbered.contains("Bart and Bart2"))
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
        assertEquals(11, Regex("""(?m)^\d+\. """).findAll(p).count())
        assertTrue(p.lines().last().startsWith("11. Names with @ in front"))
        // every format of June's editor is named
        for (mark in listOf("**bold**", "*italic*", "__underline__", "~~strikethrough~~", "==highlight==", "`code`", "- [ ]", "1.", ">", "#", "---", "```", "|", "[text](address)", "![name](file)")) {
            assertTrue("missing $mark", p.contains(mark))
        }
        assertTrue(p.contains("The people I know are Emma and Noor."))
    }

    @Test
    fun `an entry only gets the rules it needs`() {
        val plain = DictationPrompt.buildSystemPrompt(DictationPrompt.DEFAULT_SYSTEM_PROMPT, listOf("Emma"), "so uh today i went out")
        assertEquals(7, Regex("""(?m)^\d+\. """).findAll(plain).count())
        assertFalse(plain.contains("topic tags"))
        assertFalse(plain.contains("formatting marks"))
        assertFalse(plain.contains("layout"))

        val rich = DictationPrompt.buildSystemPrompt(DictationPrompt.DEFAULT_SYSTEM_PROMPT, listOf("Emma"), "I *really* went out #carnaval")
        assertEquals(9, Regex("""(?m)^\d+\. """).findAll(rich).count())
        assertTrue(rich.contains("7. My notes use formatting marks: *italic*. Keep every mark"))
        assertTrue(rich.contains("8. Words that start with #"))

        // everything before the people is the same text, so the server can reuse it
        val other = DictationPrompt.buildSystemPrompt(DictationPrompt.DEFAULT_SYSTEM_PROMPT, listOf("Noor", "Sam"), "another plain entry")
        assertEquals(plain.substringBefore("The people I know are"), other.substringBefore("The people I know are"))
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

    @Test
    fun `the markup an entry uses is recognised, and nothing else`() {
        val f =
            DictationPrompt.featuresOf(
                """
                # Monday
                - milk
                - [x] call __mum__
                1. first
                > a quote with ==highlight== and `code`
                ---
                see [the site](https://example.org) and ![pic](a.jpg) #tag
                """.trimIndent()
            )
        assertEquals(listOf("__underline__", "==highlight==", "`code`"), f.inlineMarks)
        assertEquals(5, f.blocks.size)
        assertTrue(f.links)
        assertTrue(f.topics)

        val plain = DictationPrompt.featuresOf("It cost 3 * 4 euros, #1 on my list. Snake_case_word and 2 - 1.")
        assertTrue(plain.inlineMarks.isEmpty())
        assertTrue(plain.blocks.isEmpty())
        assertFalse(plain.links)

        // a bullet written with * and a divider are layout, not italics
        val bullets = DictationPrompt.featuresOf("* one\n* two\n\n***")
        assertTrue(bullets.inlineMarks.isEmpty())
        assertEquals(2, bullets.blocks.size)
    }
}
