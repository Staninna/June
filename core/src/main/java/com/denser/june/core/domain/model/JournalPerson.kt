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

    /** Identity used to recognise the same person across entries. */
    val identityKey: String
        get() = contactLookupKey?.let { "contact:$it" } ?: "name:${name.trim().lowercase()}"

    fun isSamePerson(other: JournalPerson): Boolean = identityKey == other.identityKey
}

/** Collapses duplicates (same contact or same name) keeping the first, i.e. most recent, occurrence. */
fun List<JournalPerson>.distinctPeople(): List<JournalPerson> = distinctBy { it.identityKey }
