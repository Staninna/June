package com.denser.june.core.localai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OutputValidatorTest {
    private val people = listOf("Emma", "Noor", "Sam")

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
        assertTrue(matches.all { it.groupValues[3] in people })
        assertFalse(out.contains("**"))
    }
}
