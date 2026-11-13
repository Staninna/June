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
    fun `a person is recognised by name whatever the case or contact link`() {
        assertTrue(JournalPerson(name = "Tom").isSamePerson(JournalPerson(name = " tom ")))
        assertTrue(JournalPerson(name = "Tom").isSamePerson(JournalPerson(name = "Tom", contactLookupKey = "x")))
        assertFalse(JournalPerson(name = "Tom").isSamePerson(JournalPerson(name = "Tim")))
    }

    @Test
    fun `a person is labelled with an at sign`() {
        assertEquals("@Anna van Dijk", JournalPerson(name = " Anna van Dijk ").label)
        assertEquals("Anna van Dijk", JournalPerson.nameFromLabel("@Anna van Dijk"))
    }

    @Test
    fun `adding someone who is already there only brings a new contact link`() {
        val tom = JournalPerson(id = "t", name = "Tom", note = "Friend")
        assertEquals(listOf(tom), listOf(tom).withPerson(JournalPerson(name = "tom")))
        assertEquals(
            listOf(tom.copy(contactLookupKey = "key")),
            listOf(tom).withPerson(JournalPerson(name = "Tom", contactLookupKey = "key"))
        )
        assertEquals(2, listOf(tom).withPerson(JournalPerson(name = "Anna")).size)
    }

    @Test
    fun `people tags become people and leave the tags`() {
        val unified = unifyPeople(listOf("work", "@Anna", "#idea", "@"), emptyList())
        assertEquals(listOf("work", "#idea"), unified.tags)
        assertEquals(listOf("Anna"), unified.people.map { it.name })
        assertEquals(listOf("work", "#idea", "@Anna"), unified.all)
    }

    @Test
    fun `a people tag does not duplicate a person already on the entry`() {
        val anna = JournalPerson(id = "p", name = "Anna", note = "Sister", contactLookupKey = "k")
        val unified = unifyPeople(listOf("@anna"), listOf(anna))
        assertEquals(listOf(anna), unified.people)
        assertTrue(unified.tags.isEmpty())
    }

    @Test
    fun `a people tag picks up the known person with note and contact`() {
        val known = JournalPerson(id = "k", name = "Anna", note = "Sister", contactLookupKey = "key")
        val unified = unifyPeople(listOf("@anna"), emptyList(), known = listOf(known))
        assertEquals(listOf(known), unified.people)
    }

    @Test
    fun `unifying an entry without people tags changes nothing`() {
        val entry = journal.copy(tags = listOf("work"), people = listOf(JournalPerson(id = "p", name = "Tom")))
        assertTrue(entry === entry.withUnifiedPeople())
        assertEquals(listOf("work", "@Tom"), entry.labels)
    }

    @Test
    fun `relabel renames a person and keeps their details`() {
        val anna = JournalPerson(id = "p", name = "Anna", note = "Sister")
        val result = relabel(listOf("work"), listOf(anna), "@Anna", "@Anna V")
        assertEquals(listOf(anna.copy(name = "Anna V")), result.people)
        assertEquals(listOf("work"), result.tags)
    }

    @Test
    fun `relabel merges into a person who is already there`() {
        val anna = JournalPerson(id = "a", name = "Anna")
        val ann = JournalPerson(id = "b", name = "Ann")
        val result = relabel(emptyList(), listOf(anna, ann), "@Ann", "@Anna")
        assertEquals(listOf(anna), result.people)
    }

    @Test
    fun `relabel can turn a person into a topic and a tag into a person`() {
        val toTopic = relabel(emptyList(), listOf(JournalPerson(name = "Anna")), "@Anna", "#anna")
        assertEquals(listOf("#anna"), toTopic.tags)
        assertTrue(toTopic.people.isEmpty())

        val toPerson = relabel(listOf("Anna", "work"), emptyList(), "Anna", "@Anna")
        assertEquals(listOf("work"), toPerson.tags)
        assertEquals(listOf("Anna"), toPerson.people.map { it.name })
    }

    @Test
    fun `markdown import turns people tags into people`() {
        val md = "---\ntags:\n  - work\n  - \"@Anna\"\n---\n\nHello"
        val parsed = MarkdownEngine.fromMarkdown(md)
        assertEquals(listOf("work"), parsed.tags)
        assertEquals(listOf("Anna"), parsed.people.map { it.name })
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
