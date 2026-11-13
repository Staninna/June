package com.denser.june.core.localai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OutputValidatorTest {
    private val names = listOf("Emma", "Noor", "Sam")
    private val people = named("Emma", "Noor", "Sam")

    /** People whose id is their name, like mentions written before people had ids. */
    private fun named(vararg names: String) = names.map { CleanupPerson(id = it, name = it) }

    private fun v(text: String) = OutputValidator.validate(text, people, linkPlainNames = false).text

    @Test
    fun `plain known names are linked when enabled`() {
        val out = OutputValidator.validate("I met Emma, Samantha and Noor. Then [@Sam](person:Sam) left.", people).text
        assertEquals("I met [@Emma](person:Emma), Samantha and [@Noor](person:Noor). Then [@Sam](person:Sam) left.", out)
    }

    @Test
    fun `real model output from Qwen3_5 gets codes`() {
        val raw = "I wanted to go to Albert Heijn with Emma, and we went around 22:30. I smoked a joint with Noor."
        assertEquals(
            "I wanted to go to Albert Heijn with [@Emma](person:Emma), and we went around 22:30. I smoked a joint with [@Noor](person:Noor).",
            OutputValidator.validate(raw, people).text,
        )
    }

    @Test
    fun `clean entry passes through unchanged`() {
        val text = "Today I went to Albert Heijn with [@Emma](person:Emma) around 22:30.\n\nWe bought beer."
        assertEquals(text, v(text))
    }

    @Test
    fun `strips intro line and trailing commentary`() {
        val raw =
            """
            Here is the cleaned-up journal entry:

            Today I went to the market.

            Note: I removed the filler words.
            """.trimIndent()
        assertEquals("Today I went to the market.", v(raw))
    }

    @Test
    fun `strips sure-preamble, title and separator commentary`() {
        val raw = "Sure! Here's your entry:\n# Monday\nI slept in.\n\n---\n\nLet me know if you want changes."
        assertEquals("I slept in.", v(raw))
    }

    @Test
    fun `strips code fences and wrapping quotes`() {
        assertEquals("I went out.", v("```\nI went out.\n```"))
        assertEquals("I went out.", v("\"I went out.\""))
    }

    @Test
    fun `keeps a real first paragraph that merely starts with Today`() {
        val raw = "Today I woke up late.\n\nThen I had coffee."
        assertEquals(raw, v(raw))
    }

    @Test
    fun `unknown person mention becomes plain text`() {
        assertEquals("I met Bob and [@Emma](person:Emma).", v("I met [@Bob](person:Bob) and [@Emma](person:Emma)."))
    }

    @Test
    fun `mention ids are matched case-insensitively and normalised`() {
        assertEquals("With [@Noor](person:Noor).", v("With [@noor](person:NOOR)."))
        assertEquals("With [@Emma](person:Emma).", v("With [@Em](person:Emma)."))
    }

    @Test
    fun `broken nested code collapses into one clean code`() {
        assertEquals("I saw [@Emma](person:Emma) today.", v("I saw [[@Emm](person:Emma)a](person:Emma) today."))
    }

    @Test
    fun `bare at-names become codes for known people and plain text otherwise`() {
        assertEquals("Called [@Sam](person:Sam) and Lisa.", v("Called @Sam and @Lisa."))
    }

    @Test
    fun `removes invisible characters and non-breaking spaces`() {
        val raw = "﻿I went​ out‎ at 15:45."
        assertEquals("I went out at 15:45.", v(raw))
    }

    @Test
    fun `removes stray square brackets that would break Hyphen`() {
        assertEquals("I quietly told [@Emma](person:Emma) hi.", v("I [quietly] told [@Emma](person:Emma) hi."))
        assertEquals("[@Emma](person:Emma) said wow.", v("[@Emma](person:Emma) said [wow]."))
    }

    @Test
    fun `removes emphasis that wraps a mention`() {
        assertEquals("I hugged [@Emma](person:Emma).", v("I hugged **[@Emma](person:Emma)**."))
        assertEquals("I hugged [@Emma](person:Emma) and Sam.", v("I hugged *[@Emma](person:Emma) and Sam*."))
        assertEquals("Party with [@Noor](person:Noor)!", v("Party with __[@Noor](person:Noor)__!"))
        // emphasis that does not touch a mention is left alone
        assertEquals("It was **great** with [@Sam](person:Sam).", v("It was **great** with [@Sam](person:Sam)."))
    }

    @Test
    fun `removes leftover think blocks`() {
        assertEquals("I ran.", v("<think>\nok\n</think>\n\nI ran."))
    }

    @Test
    fun `collapses extra blank lines`() {
        assertEquals("A.\n\nB.", v("A.\n\n\n\nB.   "))
    }

    @Test
    fun `reports what it changed`() {
        val r = OutputValidator.validate("Here is the entry:\n\nI met [@Bob](person:Bob).", people)
        assertTrue(r.changes.any { it.contains("intro") })
        assertTrue(r.changes.any { it.contains("unknown mention") })
        assertFalse(r.text.contains("Bob]"))
    }

    @Test
    fun `output always parses as clean Hyphen mentions`() {
        // Hyphen's mention regex; every match must be a known person and nothing may be swallowed
        val hyphen = Regex("""\[(.+?)\]\((person|topic):(.+?)\)""")
        val messy = "[x] **[@Emma](person:Emma)** met [[@Sa](person:Sam)m](person:Sam), [@Zed](person:Zed) and ​@Noor."
        val out = v(messy)
        val matches = hyphen.findAll(out).toList()
        assertEquals(listOf("@Emma", "@Sam", "@Noor"), matches.map { it.groupValues[1] })
        assertTrue(matches.all { it.groupValues[3] in names })
        assertFalse(out.contains("**"))
    }

    @Test
    fun `topic tags survive, written as codes or as plain tags`() {
        val topics = mapOf("#awake" to "awake", "#zininin" to "zininin")
        val r = OutputValidator.validate("Good morning.\n\n#awake #zininin", named("Emma"), topics = topics)
        assertEquals("Good morning.\n\n[#awake](topic:awake) [#zininin](topic:zininin)", r.text)
        assertTrue(r.changes.none { it.contains("square brackets") })

        val kept = OutputValidator.validate("Good morning.\n\n[#awake](topic:awake) [#zininin](topic:zininin)", emptyList())
        assertEquals("Good morning.\n\n[#awake](topic:awake) [#zininin](topic:zininin)", kept.text)

        // a tag that was not in the entry stays a plain word, and a longer word is not cut
        val other = OutputValidator.validate("#awakening and #other", emptyList(), topics = topics)
        assertEquals("#awakening and #other", other.text)
    }

    @Test
    fun `simple at-tags come back as codes, also names of several words`() {
        val r = OutputValidator.validate("Out with @Emma and @Sam de Vries, later @Nobody.", named("Emma", "Sam de Vries"))
        assertEquals("Out with [@Emma](person:Emma) and [@Sam de Vries](person:Sam de Vries), later Nobody.", r.text)
    }

    @Test
    fun `an entry goes to the model and comes back with its markup intact`() {
        val entry = "Movie with [@Emma](person:Emma) *tonight*.\n\n[#carnaval](topic:carnaval) "
        val s = DictationPrompt.simplify(entry)
        // a model that returns the notes unchanged
        val r = OutputValidator.validate(s.text, named("Emma"), topics = s.topics)
        assertEquals(entry.trim(), r.text)
        assertTrue(r.changes.isEmpty())
    }

    @Test
    fun `every format of the editor passes through untouched`() {
        val entry =
            """
            # Monday

            **Bold**, *italic*, __underline__, ~~gone~~, ==marked== and `code`.

            - milk
              - oat milk
            - [ ] call mum
            - [x] pay rent
            1. first
            2. second

            > a quote

            ---

            See [the site](https://example.org/a_b) and ![pic](photos/a.jpg) and ![[b.png]].

            | a | b |
            |---|---|
            | 1 | 2 |
            """.trimIndent()
        val r = OutputValidator.validate(entry, named("Emma"), original = entry)
        assertEquals(entry, r.text)
        assertTrue("changed: ${r.changes}", r.changes.isEmpty())
    }

    @Test
    fun `a heading the model invented is still removed`() {
        val r = OutputValidator.validate("# My Day\n\nI went out.\n\n---\n\nNote: I fixed the punctuation.", emptyList(), original = "so i went out")
        assertEquals("I went out.", r.text)
    }

    @Test
    fun `a bold link keeps its emphasis, a bold mention loses it`() {
        val r = OutputValidator.validate("Read **[this](https://example.org)** with **[@Emma](person:Emma)**.", named("Emma"))
        assertEquals("Read **[this](https://example.org)** with [@Emma](person:Emma).", r.text)
    }

    // --- several people with the same name ---------------------------------------------------

    private val brother = CleanupPerson(id = "b1", name = "Bart", note = "Brother")
    private val colleague = CleanupPerson(id = "b2", name = "Bart", note = "Work")
    private val barts = listOf(brother, colleague, CleanupPerson(id = "e1", name = "Emma"))

    @Test
    fun `a mention from the notes stays the same person`() {
        val s = DictationPrompt.simplify("Lunch with [@Bart](person:b2) and [@Emma](person:e1).")
        assertEquals("Lunch with @Bart and @Emma.", s.text)
        val r = OutputValidator.validate("Had lunch with @Bart and Emma.", barts, mentions = s.mentions)
        assertEquals("Had lunch with [@Bart](person:b2) and [@Emma](person:e1).", r.text)
        assertTrue(r.changes.none { it.contains("could not tell") })
    }

    @Test
    fun `two namesakes in one entry are kept apart by a number`() {
        val s = DictationPrompt.simplify("[@Bart](person:b1) met [@Bart](person:b2), then [@Bart](person:b1) left.")
        assertEquals("@Bart met @Bart2, then @Bart left.", s.text)
        assertTrue(s.hasNumberedNames)
        assertEquals(listOf("Bart", "Bart2"), s.names)
        val r = OutputValidator.validate("@Bart met @Bart2. Then @Bart left.", barts, mentions = s.mentions)
        assertEquals("[@Bart](person:b1) met [@Bart](person:b2). Then [@Bart](person:b1) left.", r.text)
    }

    @Test
    fun `a new name several people share goes to the one whose context is said nearby`() {
        val r = OutputValidator.validate("At work I talked to Bart about the release.", barts)
        assertEquals("At work I talked to [@Bart](person:b2) about the release.", r.text)
        assertTrue(r.changes.none { it.contains("could not tell") })
    }

    @Test
    fun `otherwise the namesake on the entry is meant`() {
        val onEntry = listOf(brother.copy(onEntry = true), colleague)
        val r = OutputValidator.validate("Bart came over.", onEntry)
        assertEquals("[@Bart](person:b1) came over.", r.text)
    }

    @Test
    fun `a guess between namesakes is reported`() {
        val r = OutputValidator.validate("Bart came over.", barts)
        assertEquals("[@Bart](person:b1) came over.", r.text)
        assertTrue(r.changes.any { it.contains("could not tell which Bart") })
    }

    @Test
    fun `an old mention that carries a name gets the id of that person`() {
        val s = DictationPrompt.simplify("Coffee with [@Emma](person:Emma).")
        val r = OutputValidator.validate("Coffee with @Emma.", barts, mentions = s.mentions)
        assertEquals("Coffee with [@Emma](person:e1).", r.text)
    }
}
