package com.denser.june.core.data.repository

import com.denser.june.core.data.database.converters.JournalTypeConverters
import com.denser.june.core.data.database.journal.JournalDao
import com.denser.june.core.data.database.journal.JournalEntity
import com.denser.june.core.domain.model.Journal
import com.denser.june.core.domain.model.JournalPerson
import com.denser.june.core.domain.model.distinctPeople
import com.denser.june.core.domain.model.renameMentions
import com.denser.june.core.domain.model.resolvePeople
import com.denser.june.core.domain.model.unifyPeople
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * The people side of the journal store (the fork's people model).
 *
 * People live inside entries, and each person is also filed in the label index as `@<id>`
 * ([com.denser.june.core.domain.model.labelKeys]). [JournalRepositoryImpl] calls in here at the
 * few points where storing an entry has to know about people, so that file stays close to
 * upstream's. Nothing in here opens a transaction: callers run the writes inside theirs.
 */
internal class JournalPeopleStore(private val journalDao: JournalDao) {

    private val converters = JournalTypeConverters()

    /** Everyone on unlocked entries, most recently seen first, each person once. */
    fun knownPeople(): Flow<List<JournalPerson>> =
        journalDao.getAllPeopleJson().map { rows ->
            rows.asSequence()
                .flatMap { converters.toPeopleList(it).asSequence() }
                .filter { it.name.isNotBlank() }
                .toList()
                .distinctPeople()
        }

    /**
     * Makes the people on [journals] storable: `@name` tags become people, and anyone known by
     * name only (from such a tag or an imported file) is matched to the person with that name or
     * becomes a new one. A name new to the journal is one new person across the whole batch, and
     * the journal's people are read once per batch, not once per entry.
     *
     * An entry that still had `@name` tags (a backup, or a copy synced from an older version) is
     * stored as edited now, so sync uploads the new form instead of putting the old one back.
     */
    suspend fun readyForStorage(journals: List<Journal>): List<Journal> {
        val newPeople = mutableMapOf<String, JournalPerson>()
        var known: List<JournalPerson>? = null
        suspend fun known() = known ?: knownPeople().first().also { known = it }

        return journals.map { journal ->
            val unified = unifyPeople(journal.tags, journal.people)
            val hadPeopleTags = unified.tags != journal.tags
            val people =
                if (unified.people.any { it.isUnresolved }) unified.people.resolvePeople(known(), newPeople)
                else unified.people
            if (!hadPeopleTags && people == journal.people) journal
            else journal.copy(
                tags = unified.tags,
                people = people,
                updatedAt = if (hadPeopleTags) System.currentTimeMillis() else journal.updatedAt,
            )
        }
    }

    suspend fun readyForStorage(journal: Journal): Journal = readyForStorage(listOf(journal)).single()

    /**
     * The entries in [affected] with tag [old] renamed to [new], or left out when nothing
     * changed. Renaming a tag to `@name` turns it into a person: the one with that name, or a new
     * one, the same new one on every entry.
     */
    suspend fun renameTag(affected: List<JournalEntity>, old: String, new: String, timestamp: Long): List<JournalEntity> {
        val known = if (JournalPerson.isPersonLabel(new)) knownPeople().first() else emptyList()
        val newPeople = mutableMapOf<String, JournalPerson>()
        return affected.mapNotNull { entity ->
            val renamed = entity.tags.map { if (it == old) new else it }.distinct()
            val unified = unifyPeople(renamed, entity.people, known)
            val people = unified.people.resolvePeople(known, newPeople)
            if (unified.tags == entity.tags && people == entity.people) null
            else entity.copy(tags = unified.tags, people = people, updatedAt = timestamp)
        }
    }

    /**
     * Writes [updated] to every entry the person is on; mentions in the text follow a new name.
     * Returns the entries to store, empty when there is nothing to change. A person is never
     * stored without a name, so a nameless [updated] changes nothing.
     */
    suspend fun updatePerson(updated: JournalPerson, timestamp: Long): List<JournalEntity> {
        val name = updated.name.trim()
        if (name.isEmpty()) return emptyList()
        val person = updated.copy(name = name, note = updated.note?.trim()?.takeIf { it.isNotEmpty() })
        return journalDao.getAllJournalsByTagNameSync(person.key).mapNotNull { entity ->
            val people = entity.people.map { if (it.isSamePerson(person)) person else it }
            val content = renameMentions(entity.content, person)
            if (people == entity.people && content == entity.content) null
            else entity.copy(people = people, content = content, updatedAt = timestamp)
        }
    }

    /**
     * Takes the person with [personId] off every entry and out of the label index. Returns the
     * entries to store. Mentions in the text stay as they are written.
     */
    suspend fun removePerson(personId: String, timestamp: Long): List<JournalEntity> {
        val key = JournalPerson.PERSON_PREFIX + personId
        val updated = journalDao.getAllJournalsByTagNameSync(key).mapNotNull { entity ->
            val people = entity.people.filterNot { it.id == personId }
            if (people == entity.people) null else entity.copy(people = people, updatedAt = timestamp)
        }
        journalDao.deleteTagCrossRefs(key)
        journalDao.deleteTag(key)
        return updated
    }
}
