package com.denser.june.core.domain.model

import com.denser.june.core.domain.markdown.MarkdownEngine
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class JournalPersonTest {

    private val journal = Journal(
        id = "j1",
        title = "Dinner",
        content = "Lovely evening",
        createdAt = 1L,
        updatedAt = 2L,
        dateTime = 3L,
    )

    @Test
    fun `initials use first and last word`() {
        assertEquals("AD", JournalPerson(name = "Anna van Dijk").initials)
        assertEquals("T", JournalPerson(name = "  tom ").initials)
        assertEquals("?", JournalPerson(name = " ").initials)
    }

    @Test
    fun `same contact is the same person even if renamed`() {
        val a = JournalPerson(name = "Anna", contactLookupKey = "k1")
        val b = JournalPerson(name = "Anna V.", contactLookupKey = "k1")
        assertTrue(a.isSamePerson(b))
    }

    @Test
    fun `unlinked people match on case-insensitive name`() {
        assertTrue(JournalPerson(name = "Tom").isSamePerson(JournalPerson(name = " tom ")))
        assertFalse(JournalPerson(name = "Tom").isSamePerson(JournalPerson(name = "Tom", contactLookupKey = "x")))
    }

    @Test
    fun `distinctPeople keeps first occurrence`() {
        val first = JournalPerson(id = "1", name = "Tom", note = "newest")
        val second = JournalPerson(id = "2", name = "tom", note = "older")
        assertEquals(listOf(first), listOf(first, second).distinctPeople())
    }

    @Test
    fun `empty people list is omitted so existing content hashes are unchanged`() {
        val json = Json { encodeDefaults = true; explicitNulls = true }
        val encoded = json.encodeToString(Journal.serializer(), journal)
        assertFalse(encoded.contains("people"))
    }

    @Test
    fun `adding people changes the content hash and round-trips`() {
        val withPeople = journal.copy(people = listOf(JournalPerson(id = "p", name = "Anna", note = "Sister")))
        assertNotEquals(journal.computeContentHash(), withPeople.computeContentHash())
        val json = Json { ignoreUnknownKeys = true }
        val decoded = json.decodeFromString(Journal.serializer(), json.encodeToString(Journal.serializer(), withPeople))
        assertEquals(withPeople.people, decoded.people)
        assertFalse(journal.isContentEqualTo(withPeople))
    }

    @Test
    fun `old payloads without people decode to empty list`() {
        val legacy = """{"id":"j1","title":"t","content":"c","createdAt":1,"updatedAt":null,"dateTime":3}"""
        val decoded = Json { ignoreUnknownKeys = true }.decodeFromString(Journal.serializer(), legacy)
        assertTrue(decoded.people.isEmpty())
    }

    @Test
    fun `markdown export lists people and import restores names`() {
        val withPeople = journal.copy(people = listOf(JournalPerson(name = "Anna"), JournalPerson(name = "Tom")))
        val md = MarkdownEngine.toMarkdown(withPeople)
        assertTrue(md.contains("Anna, Tom"))
        val parsed = MarkdownEngine.fromMarkdown(md)
        assertEquals(listOf("Anna", "Tom"), parsed.people.map { it.name })
    }
}
