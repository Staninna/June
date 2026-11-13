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
    fun `a person is their id, so namesakes are different people`() {
        val brother = JournalPerson(id = "1", name = "Bart", note = "Brother")
        val colleague = JournalPerson(id = "2", name = "Bart", note = "Work")
        assertFalse(brother.isSamePerson(colleague))
        assertTrue(brother.isSamePerson(brother.copy(name = "Bartholomew")))
        assertEquals(listOf(brother, colleague), listOf(brother, colleague, brother.copy(note = "x")).distinctPeople())
        assertEquals(setOf("bart"), listOf(brother, colleague, JournalPerson(name = "Emma")).sharedNames())
    }

    @Test
    fun `a person is written with an at sign and filed under their id`() {
        val anna = JournalPerson(id = "a1", name = " Anna van Dijk ", note = "Sister")
        assertEquals("@Anna van Dijk", anna.label)
        assertEquals("@a1", anna.key)
        assertEquals("Anna van Dijk · Sister", anna.displayName)
        assertEquals("[@Anna van Dijk](person:a1)", anna.mentionCode())
    }

    @Test
    fun `adding someone who is already there only brings a new contact link`() {
        val tom = JournalPerson(id = "t", name = "Tom", note = "Friend")
        assertEquals(listOf(tom), listOf(tom).withPerson(tom.copy(note = null)))
        assertEquals(listOf(tom.copy(contactLookupKey = "key")), listOf(tom).withPerson(tom.copy(contactLookupKey = "key")))
        // another Tom is another person
        assertEquals(2, listOf(tom).withPerson(JournalPerson(id = "t2", name = "Tom")).size)
    }

    @Test
    fun `people tags become people and leave the tags`() {
        val unified = unifyPeople(listOf("work", "@Anna", "#idea", "@"), emptyList())
        assertEquals(listOf("work", "#idea"), unified.tags)
        assertEquals(listOf("Anna"), unified.people.map { it.name })
        assertTrue(unified.people.single().isUnresolved)
    }

    @Test
    fun `a people tag does not duplicate a person already on the entry`() {
        val anna = JournalPerson(id = "p", name = "Anna", note = "Sister", contactLookupKey = "k")
        val unified = unifyPeople(listOf("@anna"), listOf(anna))
        assertEquals(listOf(anna), unified.people)
        assertTrue(unified.tags.isEmpty())
    }

    @Test
    fun `a people tag is the most recent known person with that name`() {
        val recent = JournalPerson(id = "k", name = "Anna", note = "Sister", contactLookupKey = "key")
        val older = JournalPerson(id = "o", name = "Anna")
        assertEquals(listOf(recent), unifyPeople(listOf("@anna"), emptyList(), known = listOf(recent, older)).people)
    }

    @Test
    fun `someone known by name is the person with that name or one new person`() {
        val known = listOf(JournalPerson(id = "k", name = "Anna", note = "Sister"))
        val assigned = mutableMapOf<String, JournalPerson>()
        val first = listOf(JournalPerson.named("anna"), JournalPerson.named("Tom")).resolvePeople(known, assigned)
        val second = listOf(JournalPerson.named("tom")).resolvePeople(known, assigned)
        assertEquals(known.single(), first[0])
        assertFalse(first[1].isUnresolved)
        // the same name in a later entry of the same import is the same new person
        assertEquals(first[1], second.single())
    }

    @Test
    fun `unifying an entry without people tags changes nothing`() {
        val entry = journal.copy(tags = listOf("work"), people = listOf(JournalPerson(id = "p", name = "Tom")))
        assertTrue(entry === entry.withUnifiedPeople())
        assertEquals(listOf("work", "@p"), entry.labelKeys)
    }

    @Test
    fun `mentions follow a rename, but only those of that person`() {
        val bart = JournalPerson(id = "b1", name = "Bartholomew")
        val text = "With [@Bart](person:b1) and [@Bart](person:b2)."
        assertEquals("With [@Bartholomew](person:b1) and [@Bart](person:b2).", renameMentions(text, bart))
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
        val second = JournalPerson(id = "1", name = "tom", note = "older")
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
