package com.denser.june.presentation.screens.editor

import com.denser.june.core.domain.model.JournalPerson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EditorPeopleTest {
    private val anna = JournalPerson(id = "a1", name = "Anna")
    private val bart = JournalPerson(id = "b1", name = "Bart", note = "Work")

    @Test
    fun `an entry started from a person gets them once the journal's people are known`() {
        val started = EditorState(pendingPeople = listOf(anna.key, "@Cleo"))

        val loaded = started.withKnownPeople(listOf(anna, bart))

        assertEquals(anna, loaded.people[0])
        val cleo = loaded.people[1]
        assertEquals("Cleo", cleo.name)
        assertFalse(cleo.isUnresolved)
        assertTrue(loaded.pendingPeople.isEmpty())
        assertEquals(listOf(anna, bart), loaded.knownPeople)
    }

    @Test
    fun `an @name tag becomes the known person of that name`() {
        val state = EditorState(knownPeople = listOf(anna)).withTags(listOf("work", "@anna"))

        assertEquals(listOf("work"), state.tags)
        assertEquals(listOf(anna), state.people)
    }

    @Test
    fun `people are added once, changed in place and removed by id`() {
        val state = EditorState().withPersonAdded(anna).withPersonAdded(anna).withPersonAdded(bart)
        assertEquals(listOf(anna, bart), state.people)

        val renamed = anna.copy(name = "Anna V")
        assertEquals(listOf(renamed, bart), state.withPersonChanged(renamed).people)
        assertEquals(listOf(bart), state.withoutPerson("a1").people)
    }

    @Test
    fun `a person before an edit is found on the entry or else in the journal`() {
        val state = EditorState(people = listOf(anna), knownPeople = listOf(anna.copy(name = "old"), bart))

        assertEquals(anna, state.personWithId("a1"))
        assertEquals(bart, state.personWithId("b1"))
        assertNull(state.personWithId("x"))
    }
}
