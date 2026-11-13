package com.denser.june.core.domain.model

import com.denser.june.core.domain.model.PeopleUnification.Entry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PeopleUnificationTest {

    private var next = 0
    private fun unify(vararg entries: Entry) = PeopleUnification.unify(entries.toList()) { "new${++next}" }

    @Test
    fun `the same name on several entries becomes one person with the fullest details`() {
        val result = unify(
            Entry("j1", emptyList(), listOf(JournalPerson(id = "a", name = "Anna"))),
            Entry("j2", emptyList(), listOf(JournalPerson(id = "b", name = "anna", note = "Sister"))),
        )
        val anna = JournalPerson(id = "a", name = "Anna", note = "Sister")
        assertEquals(listOf(anna), result[0].people)
        assertEquals(listOf(anna), result[1].people)
    }

    @Test
    fun `people tags become people and leave the tags`() {
        val result = unify(
            Entry("j1", listOf("work", "@Tom"), emptyList()),
            Entry("j2", listOf("@tom", "#idea"), listOf(JournalPerson(id = "t", name = "Tom", note = "Friend"))),
        )
        val tom = JournalPerson(id = "t", name = "Tom", note = "Friend")
        assertEquals(Entry("j1", listOf("work"), listOf(tom)), result[0])
        assertEquals(Entry("j2", listOf("#idea"), listOf(tom)), result[1])
    }

    @Test
    fun `a tag for someone nobody knows makes one new person`() {
        val result = unify(Entry("j1", listOf("@Kim"), emptyList()), Entry("j2", listOf("@kim"), emptyList()))
        assertEquals("Kim", result[0].people.single().name)
        assertEquals(result[0].people, result[1].people)
    }

    @Test
    fun `someone without a contact is the one linked person of that name`() {
        val result = unify(
            Entry("j1", emptyList(), listOf(JournalPerson(id = "x", name = "Bart", note = "Brother"))),
            Entry("j2", listOf("@Bart"), listOf(JournalPerson(id = "y", name = "Bart", contactLookupKey = "c1"))),
        )
        val bart = JournalPerson(id = "y", name = "Bart", note = "Brother", contactLookupKey = "c1")
        assertEquals(listOf(bart), result[0].people)
        assertEquals(listOf(bart), result[1].people)
    }

    @Test
    fun `four Barts with their own contacts stay four people`() {
        val barts = (1..4).map { JournalPerson(id = "b$it", name = "Bart", contactLookupKey = "c$it") }
        val unlinked = JournalPerson(id = "u", name = "Bart")
        val result = unify(
            Entry("j1", emptyList(), barts),
            Entry("j2", listOf("@Bart"), listOf(unlinked)),
        )
        assertEquals(barts, result[0].people)
        // with several linked Barts there is no telling which one was meant: a fifth person
        assertEquals(listOf(unlinked), result[1].people)
        assertTrue(result[1].tags.isEmpty())
    }

    @Test
    fun `two people never share an id`() {
        val result = unify(
            Entry("j1", emptyList(), listOf(JournalPerson(id = "same", name = "Anna"), JournalPerson(id = "same", name = "Tom"))),
        )
        assertEquals(2, result[0].people.map { it.id }.toSet().size)
        assertEquals(listOf("Anna", "Tom"), result[0].people.map { it.name })
    }
}
