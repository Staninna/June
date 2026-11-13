package com.denser.june.core.domain.model

import kotlinx.serialization.Serializable
import java.util.UUID

/**
 * Someone who was part of a journal entry.
 *
 * People live entirely on-device inside the journal. When picked from the phone's address book,
 * [contactLookupKey] keeps a link back to that contact (no contacts permission is needed for this:
 * the system picker hands over a single contact and we only remember its lookup key).
 */
@Serializable
data class JournalPerson(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    /** Free-form context such as "Sister", "Met at climbing" or "Team lead". */
    val note: String? = null,
    /** ContactsContract lookup key, when linked to a device contact. */
    val contactLookupKey: String? = null,
) {
    val initials: String
        get() = name.trim()
            .split(Regex("\\s+"))
            .filter { it.isNotEmpty() }
            .let { parts ->
                when {
                    parts.isEmpty() -> "?"
                    parts.size == 1 -> parts[0].take(1)
                    else -> parts.first().take(1) + parts.last().take(1)
                }
            }
            .uppercase()

    /**
     * Identity used to recognise the same person across entries: the name, ignoring case.
     * A person is one thing everywhere in the journal, so a contact link is a property of the
     * person and not a second identity.
     */
    val identityKey: String
        get() = name.trim().lowercase()

    /** How this person appears among an entry's labels and in the tag index: `@Anna`. */
    val label: String
        get() = PERSON_PREFIX + name.trim()

    fun isSamePerson(other: JournalPerson): Boolean = identityKey == other.identityKey

    companion object {
        const val PERSON_PREFIX = "@"

        fun isPersonLabel(label: String): Boolean = label.startsWith(PERSON_PREFIX)

        /** `@Anna` -> `Anna`. */
        fun nameFromLabel(label: String): String = label.removePrefix(PERSON_PREFIX).trim()
    }
}

/** Collapses duplicates (same name) keeping the first, i.e. most recent, occurrence. */
fun List<JournalPerson>.distinctPeople(): List<JournalPerson> = distinctBy { it.identityKey }

/**
 * Adds [person] to the people on an entry. Someone who is already there is not added twice, but
 * a contact link that comes with [person] (they were just picked from the address book) is kept.
 */
fun List<JournalPerson>.withPerson(person: JournalPerson): List<JournalPerson> {
    if (none { it.isSamePerson(person) }) return this + person
    val lookupKey = person.contactLookupKey ?: return this
    return map { if (it.isSamePerson(person)) it.copy(contactLookupKey = lookupKey) else it }
}

/** An entry's spaces and topics, with its people moved out of the tags. */
data class EntryLabels(val tags: List<String>, val people: List<JournalPerson>) {
    /** Everything the entry is filed under: tags first, then each person as `@Name`. */
    val all: List<String> get() = (tags + people.map { it.label }).distinct()
}

/**
 * People used to exist twice: as `@name` tags and as the people on an entry. This makes them one.
 * Every `@name` tag becomes a person (taken from [known] when that person already exists, so the
 * context note and contact link come along) and leaves the tags. Blank names and duplicates go.
 */
fun unifyPeople(
    tags: List<String>,
    people: List<JournalPerson>,
    known: List<JournalPerson> = emptyList(),
): EntryLabels {
    val merged = people.filter { it.name.isNotBlank() }.toMutableList()
    val plainTags = mutableListOf<String>()
    for (raw in tags) {
        val tag = raw.trim()
        if (!JournalPerson.isPersonLabel(tag)) {
            if (tag.isNotEmpty()) plainTags += tag
            continue
        }
        val name = JournalPerson.nameFromLabel(tag)
        if (name.isEmpty()) continue
        val candidate = JournalPerson(name = name)
        if (merged.none { it.isSamePerson(candidate) }) {
            merged += known.firstOrNull { it.isSamePerson(candidate) } ?: candidate
        }
    }
    return EntryLabels(plainTags.distinct(), merged.distinctPeople())
}

/**
 * Renames the label [old] to [new] on one entry, wherever it lives. A label can change kind on
 * the way: `@Anna` -> `#anna` turns a person into a topic, `work` -> `@Work` a space into a person.
 */
fun relabel(tags: List<String>, people: List<JournalPerson>, old: String, new: String): EntryLabels {
    val newIsPerson = JournalPerson.isPersonLabel(new)
    val newTags = tags.map { if (it == old) new else it }.toMutableList()
    val newPeople = mutableListOf<JournalPerson>()
    for (person in people) {
        when {
            person.label != old -> newPeople += person
            newIsPerson -> newPeople += person.copy(name = JournalPerson.nameFromLabel(new))
            else -> newTags += new
        }
    }
    // An existing person with the target name wins over one created from a bare tag.
    return unifyPeople(newTags, newPeople)
}

/** The entry with any `@name` tags folded into its people. */
fun Journal.withUnifiedPeople(): Journal {
    val unified = unifyPeople(tags, people)
    return if (unified.tags == tags && unified.people == people) this
    else copy(tags = unified.tags, people = unified.people)
}

/** Everything the entry is filed under: its tags plus each person as `@Name`. */
val Journal.labels: List<String>
    get() = (tags + people.map { it.label }).distinct()
