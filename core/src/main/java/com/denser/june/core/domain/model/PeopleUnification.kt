package com.denser.june.core.domain.model

import java.util.UUID

/**
 * One-time clean-up of the people in a journal that was written before a person had one identity.
 *
 * Back then every entry kept its own copy of a person, recognised by contact or else by name, and
 * people also existed as `@name` tags. The same person could therefore sit under several ids, and
 * carry a note or a contact link on one entry but not on the next.
 *
 * [unify] gives each person a single id and the most complete details, on every entry:
 * - people with the same linked contact are one person;
 * - people without a contact are one person per name;
 * - someone without a contact is the linked person of that name when there is exactly one
 *   (the link was simply not made on that entry). With several linked namesakes there is no
 *   telling which one was meant, so they stay a person of their own;
 * - a `@name` tag is the person of that name, found the same way, or else a new person.
 *
 * Nobody is merged beyond that: four Barts with four contacts stay four people.
 */
object PeopleUnification {

    /** The tags and people of one entry. [entries] are passed newest first. */
    data class Entry(val id: String, val tags: List<String>, val people: List<JournalPerson>)

    fun unify(entries: List<Entry>, newId: () -> String = { UUID.randomUUID().toString() }): List<Entry> {
        // 1. One person per contact and one per unlinked name; the newest entry decides spelling,
        //    older entries fill in what is missing.
        val byContact = LinkedHashMap<String, JournalPerson>()
        val byName = LinkedHashMap<String, JournalPerson>()
        fun JournalPerson.filledFrom(other: JournalPerson) =
            copy(note = note?.takeIf { it.isNotBlank() } ?: other.note?.takeIf { it.isNotBlank() })

        for (entry in entries) {
            for (person in entry.people) {
                val name = person.name.trim()
                if (name.isEmpty()) continue
                val contact = person.contactLookupKey
                if (contact != null) {
                    byContact[contact] = byContact[contact]?.filledFrom(person) ?: person.copy(name = name)
                } else {
                    val key = name.lowercase()
                    byName[key] = byName[key]?.filledFrom(person) ?: person.copy(name = name)
                }
            }
        }

        // 2. An unlinked name that exactly one linked person has is that person.
        val linkedByName = byContact.values.groupBy { it.name.lowercase() }
        fun linkedNamesake(nameKey: String): JournalPerson? = linkedByName[nameKey]?.singleOrNull()
        for ((nameKey, unlinked) in byName.toMap()) {
            val linked = linkedNamesake(nameKey) ?: continue
            byContact[linked.contactLookupKey!!] = linked.filledFrom(unlinked)
            byName.remove(nameKey)
        }

        // Two people must never share an id, whatever the old data did.
        val seenIds = HashSet<String>()
        fun unique(person: JournalPerson) = if (seenIds.add(person.id)) person else person.copy(id = newId()).also { seenIds += it.id }
        for (key in byContact.keys.toList()) byContact[key] = unique(byContact.getValue(key))
        for (key in byName.keys.toList()) byName[key] = unique(byName.getValue(key))

        fun personFor(name: String, contact: String?): JournalPerson {
            val nameKey = name.trim().lowercase()
            if (contact != null) return byContact.getValue(contact)
            return byName[nameKey]
                ?: byContact.values.singleOrNull { it.name.lowercase() == nameKey }
                ?: unique(JournalPerson(id = newId(), name = name.trim())).also { byName[nameKey] = it }
        }

        // 3. Rewrite every entry with those people, and move `@name` tags over.
        return entries.map { entry ->
            val people = entry.people
                .filter { it.name.isNotBlank() }
                .map { personFor(it.name, it.contactLookupKey) }
                .toMutableList()
            val tags = mutableListOf<String>()
            for (raw in entry.tags) {
                val tag = raw.trim()
                if (!JournalPerson.isPersonLabel(tag)) {
                    if (tag.isNotEmpty()) tags += tag
                    continue
                }
                val name = JournalPerson.nameFromLabel(tag)
                if (name.isEmpty()) continue
                // someone on this entry with that name is who the tag meant
                if (people.none { it.hasName(name) }) people += personFor(name, null)
            }
            Entry(entry.id, tags.distinct(), people.distinctPeople())
        }
    }
}
