package com.denser.june.core.data.repository

import com.denser.june.core.data.database.journal.JournalEntity
import com.denser.june.core.data.database.journal.JournalTagCrossRef
import com.denser.june.core.domain.model.Journal
import com.denser.june.core.domain.model.JournalPerson
import com.denser.june.core.sync.fakes.FakeJournalDao
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class JournalTagRepositoryTest {

    private lateinit var fakeDao: FakeJournalDao
    private lateinit var repository: JournalRepositoryImpl

    @Before
    fun setUp() {
        fakeDao = FakeJournalDao()
        repository = JournalRepositoryImpl(journalDao = fakeDao)
    }

    private fun addJournalWithTags(
        id: String,
        tags: List<String>,
        isLocked: Boolean = false,
        deletedAt: Long? = null,
        updatedAt: Long = 1000L
    ): JournalEntity {
        val entity = JournalEntity(
            id = id,
            title = "Title $id",
            content = "Content $id",
            dateTime = 1000L,
            tags = tags,
            isLocked = isLocked,
            deletedAt = deletedAt,
            updatedAt = updatedAt
        )
        fakeDao.journals[id] = entity
        tags.forEach { tag ->
            fakeDao.tags.add(tag)
            fakeDao.crossRefs.add(JournalTagCrossRef(id, tag))
        }
        return entity
    }

    @Test
    fun renameTag_mergesTagsWhenTargetTagAlreadyExists() = runTest {
        addJournalWithTags("j1", listOf("trip"))
        addJournalWithTags("j2", listOf("travel"))
        addJournalWithTags("j3", listOf("trip", "travel"))

        repository.renameTag("trip", "travel")

        assertTrue(fakeDao.tags.contains("travel"))
        assertFalse(fakeDao.tags.contains("trip"))

        assertEquals(listOf("travel"), fakeDao.journals["j1"]?.tags)
        assertEquals(listOf("travel"), fakeDao.journals["j2"]?.tags)
        assertEquals(listOf("travel"), fakeDao.journals["j3"]?.tags)

        val j3Refs = fakeDao.crossRefs.filter { it.id == "j3" }
        assertEquals(1, j3Refs.size)
        assertEquals("travel", j3Refs.first().tagName)

        val tripRefs = fakeDao.crossRefs.filter { it.tagName == "trip" }
        assertTrue(tripRefs.isEmpty())
    }

    @Test
    fun renameTag_updatesLockedAndTrashJournals() = runTest {
        addJournalWithTags("j_locked", listOf("trip"), isLocked = true)
        addJournalWithTags("j_trash", listOf("trip"), deletedAt = 5000L)

        repository.renameTag("trip", "travel")

        assertEquals(listOf("travel"), fakeDao.journals["j_locked"]?.tags)
        assertEquals(listOf("travel"), fakeDao.journals["j_trash"]?.tags)

        val lockedRef = fakeDao.crossRefs.firstOrNull { it.id == "j_locked" }
        val trashRef = fakeDao.crossRefs.firstOrNull { it.id == "j_trash" }

        assertEquals("travel", lockedRef?.tagName)
        assertEquals("travel", trashRef?.tagName)
    }

    @Test
    fun renameTag_doesNotBumpTimestampOnUnchangedJournals() = runTest {
        val initialTimestamp = 1000L
        addJournalWithTags("j_trip", listOf("trip"), updatedAt = initialTimestamp)
        addJournalWithTags("j_travel", listOf("travel"), updatedAt = initialTimestamp)

        repository.renameTag("trip", "travel")

        assertTrue((fakeDao.journals["j_trip"]?.updatedAt ?: 0L) > initialTimestamp)
        assertEquals(initialTimestamp, fakeDao.journals["j_travel"]?.updatedAt)
    }

    @Test
    fun renameTag_handlesSimpleRenameWhenTargetDoesNotExist() = runTest {
        addJournalWithTags("j1", listOf("nature"))

        repository.renameTag("nature", "wildlife")

        assertTrue(fakeDao.tags.contains("wildlife"))
        assertFalse(fakeDao.tags.contains("nature"))
        assertEquals(listOf("wildlife"), fakeDao.journals["j1"]?.tags)

        val j1Ref = fakeDao.crossRefs.firstOrNull { it.id == "j1" }
        assertEquals("wildlife", j1Ref?.tagName)
    }

    @Test
    fun renameTag_noOpOnSameNameOrBlank() = runTest {
        addJournalWithTags("j1", listOf("trip"))

        repository.renameTag("trip", "trip")
        repository.renameTag("trip", "   ")
        repository.renameTag("", "travel")

        assertTrue(fakeDao.tags.contains("trip"))
        assertEquals(listOf("trip"), fakeDao.journals["j1"]?.tags)
    }

    @Test
    fun deleteTag_removesTagFromAllJournalsAndCrossRefs() = runTest {
        addJournalWithTags("j_normal", listOf("trip", "other"))
        addJournalWithTags("j_locked", listOf("trip"), isLocked = true)
        addJournalWithTags("j_trash", listOf("trip"), deletedAt = 5000L)

        repository.deleteTag("trip")

        assertFalse(fakeDao.tags.contains("trip"))
        val remainingTripRefs = fakeDao.crossRefs.filter { it.tagName == "trip" }
        assertTrue(remainingTripRefs.isEmpty())

        assertEquals(listOf("other"), fakeDao.journals["j_normal"]?.tags)
        assertEquals(emptyList<String>(), fakeDao.journals["j_locked"]?.tags)
        assertEquals(emptyList<String>(), fakeDao.journals["j_trash"]?.tags)
    }

    // --- People are labels too -------------------------------------------------------------

    private fun journal(id: String, tags: List<String> = emptyList(), people: List<JournalPerson> = emptyList()) =
        Journal(id = id, title = "t", content = "c", tags = tags, people = people, createdAt = 1L, updatedAt = 1L, dateTime = 1L)

    @Test
    fun insertJournal_movesPeopleTagsToPeopleAndFilesThemUnderTheirId() = runTest {
        repository.insertJournal(journal("j1", tags = listOf("work", "@Anna")))

        val stored = fakeDao.journals["j1"]!!
        assertEquals(listOf("work"), stored.tags)
        val anna = stored.people.single()
        assertEquals("Anna", anna.name)
        assertFalse(anna.isUnresolved)
        assertEquals(setOf("work", "@${anna.id}"), fakeDao.crossRefs.filter { it.id == "j1" }.map { it.tagName }.toSet())
    }

    @Test
    fun updateJournal_keepsNamesakesApart() = runTest {
        val brother = JournalPerson(id = "b1", name = "Bart", note = "Brother")
        val colleague = JournalPerson(id = "b2", name = "Bart", note = "Work")
        repository.insertJournal(journal("j1"))
        repository.updateJournal(journal("j1", people = listOf(brother, colleague)))

        assertEquals(listOf(brother, colleague), fakeDao.journals["j1"]!!.people)
        assertEquals(setOf("@b1", "@b2"), fakeDao.crossRefs.filter { it.id == "j1" }.map { it.tagName }.toSet())
    }

    @Test
    fun updatePerson_changesThatPersonOnEveryEntryAndInTheText() = runTest {
        val bart = JournalPerson(id = "b1", name = "Bart")
        val otherBart = JournalPerson(id = "b2", name = "Bart")
        repository.insertJournal(journal("j1", people = listOf(bart)).copy(content = "With [@Bart](person:b1) and [@Bart](person:b2)."))
        repository.insertJournal(journal("j2", people = listOf(bart, otherBart)))
        repository.insertJournal(journal("j3", people = listOf(otherBart)))

        val updated = bart.copy(name = "Bart V", note = "Brother", contactLookupKey = "key")
        repository.updatePerson(updated)

        assertEquals(listOf(updated), fakeDao.journals["j1"]!!.people)
        assertEquals("With [@Bart V](person:b1) and [@Bart](person:b2).", fakeDao.journals["j1"]!!.content)
        assertEquals(listOf(updated, otherBart), fakeDao.journals["j2"]!!.people)
        assertEquals(listOf(otherBart), fakeDao.journals["j3"]!!.people)
        assertEquals(setOf("j1", "j2"), fakeDao.crossRefs.filter { it.tagName == "@b1" }.map { it.id }.toSet())
    }

    @Test
    fun removePerson_takesOnlyThatPersonOffEveryEntry() = runTest {
        val bart = JournalPerson(id = "b1", name = "Bart")
        val otherBart = JournalPerson(id = "b2", name = "Bart")
        repository.insertJournal(journal("j1", tags = listOf("work"), people = listOf(bart, otherBart)))

        repository.removePerson("b1")

        assertEquals(listOf(otherBart), fakeDao.journals["j1"]!!.people)
        assertEquals(setOf("work", "@b2"), fakeDao.crossRefs.map { it.tagName }.toSet())
        assertFalse(fakeDao.tags.contains("@b1"))
    }

    @Test
    fun renameTag_toAPersonLabelMakesOnePersonOfIt() = runTest {
        repository.insertJournal(journal("j1", tags = listOf("Anna", "work")))
        repository.insertJournal(journal("j2", tags = listOf("Anna")))

        repository.renameTag("Anna", "@Anna")

        val anna = fakeDao.journals["j1"]!!.people.single()
        assertEquals("Anna", anna.name)
        assertEquals(listOf(anna), fakeDao.journals["j2"]!!.people)
        assertEquals(listOf("work"), fakeDao.journals["j1"]!!.tags)
        assertEquals(setOf("j1", "j2"), fakeDao.crossRefs.filter { it.tagName == anna.key }.map { it.id }.toSet())
        assertTrue(fakeDao.crossRefs.none { it.tagName == "@Anna" || it.tagName == "Anna" })
    }
}
