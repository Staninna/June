package com.denser.june.core.domain.model

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import java.util.UUID

/**
 * Someone who was part of a journal entry.
 *
 * A person is one thing across the whole journal, known by [id]. Two people can share a name
 * (four Barts are four people); [note] and the linked contact tell them apart. Every entry a
 * person is on carries a copy with the same [id], and an edit is written to all of them.
 *
 * People live inside the journal. When picked from the phone's address book, [contactLookupKey]
 * keeps a link back to that contact: the system picker hands over a single contact and only its
 * lookup key is remembered.
 */
@Serializable
data class JournalPerson(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    /** Free-form context such as "Sister", "Met at climbing" or "Team lead". */
    val note: String? = null,
    /** ContactsContract lookup key, when linked to a device contact. */
    val contactLookupKey: String? = null,
    /**
     * The colour picked for this person, as a place in the app's set of person colours, or null
     * to let June choose. Left out when null, so entries written before this read and compare
     * the same.
     */
    @OptIn(ExperimentalSerializationApi::class)
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val color: Int? = null,
    /** The shape picked for this person's icon, as a place in the app's set of shapes, or null. */
    @OptIn(ExperimentalSerializationApi::class)
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val shape: Int? = null,
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
     * What a person's icon follows from when no colour or shape was picked: their contact, or
     * else their name. It is what icons have always followed from, so they look as they did.
     */
    val iconSeed: String
        get() = contactLookupKey?.let { "contact:$it" } ?: "name:${name.trim().lowercase()}"

    /** What makes this person this person: the id. Never the name. */
    val identityKey: String
        get() = id

    /** The person as a label in the index of everything entries are filed under: `@<id>`. */
    val key: String
        get() = PERSON_PREFIX + id

    /** How the person is written in text: `@Anna`. */
    val label: String
        get() = PERSON_PREFIX + name.trim()

    /** The one label that says who this is where there is little room: the first of [note]'s. */
    val mainLabel: String?
        get() = PersonLabels.main(note)

    /** Name with the label that tells namesakes apart: "Bart · Work". */
    val displayName: String
        get() = mainLabel?.let { "${name.trim()} · $it" } ?: name.trim()

    /**
     * Known by name only, not yet matched to a person: from a `@name` tag or an imported file.
     * Such a person is matched to someone with that name, or becomes a new person, before it is
     * stored ([resolvePeople]).
     */
    val isUnresolved: Boolean
        get() = id.startsWith(UNRESOLVED_PREFIX)

    fun isSamePerson(other: JournalPerson): Boolean = id == other.id

    fun hasName(other: String): Boolean = name.trim().equals(other.trim(), ignoreCase = true)

    companion object {
        const val PERSON_PREFIX = "@"
        private const val UNRESOLVED_PREFIX = "name:"

        fun isPersonLabel(label: String): Boolean = label.startsWith(PERSON_PREFIX)

        /** `@Anna` -> `Anna`, and `@<id>` -> `<id>`. */
        fun nameFromLabel(label: String): String = label.removePrefix(PERSON_PREFIX).trim()

        /** Someone known by name only. See [isUnresolved]. */
        fun named(name: String): JournalPerson =
            JournalPerson(id = UNRESOLVED_PREFIX + name.trim().lowercase(), name = name.trim())
    }
}

/** Collapses duplicates of the same person, keeping the first, i.e. most recent, occurrence. */
fun List<JournalPerson>.distinctPeople(): List<JournalPerson> = distinctBy { it.id }

/** The names more than one of these people have, lowercased. They need their context shown. */
fun List<JournalPerson>.sharedNames(): Set<String> =
    groupingBy { it.name.trim().lowercase() }.eachCount().filterValues { it > 1 }.keys

/**
 * Adds [person] to the people on an entry. Someone who is already there is not added twice, but
 * a contact link that comes with [person] (they were just picked from the address book) is kept.
 */
fun List<JournalPerson>.withPerson(person: JournalPerson): List<JournalPerson> {
    if (none { it.isSamePerson(person) }) return this + person
    val lookupKey = person.contactLookupKey ?: return this
    return map { if (it.isSamePerson(person)) it.copy(contactLookupKey = lookupKey) else it }
}

/**
 * Gives everyone who is only known by name an identity. A name [known] already has is that
 * person (the most recent one, when several share the name). Any other name becomes a new person,
 * and the same name is the same new person throughout [assigned], which callers share across the
 * entries of one import.
 */
fun List<JournalPerson>.resolvePeople(
    known: List<JournalPerson>,
    assigned: MutableMap<String, JournalPerson> = mutableMapOf(),
): List<JournalPerson> =
    map { person ->
        if (!person.isUnresolved) return@map person
        known.firstOrNull { it.hasName(person.name) }
            ?: assigned.getOrPut(person.name.trim().lowercase()) { person.copy(id = UUID.randomUUID().toString()) }
    }.distinctPeople()

/** An entry's spaces and topics, with its people moved out of the tags. */
data class EntryLabels(val tags: List<String>, val people: List<JournalPerson>) {
    /** Everything the entry is filed under in the index: tags, then each person as `@<id>`. */
    val keys: List<String> get() = (tags + people.map { it.key }).distinct()
}

/**
 * People used to exist twice: as `@name` tags and as the people on an entry. This makes them one.
 * Every `@name` tag leaves the tags and becomes a person: the one already on the entry with that
 * name, else the most recent of [known] with it, else someone known by name only, who gets an
 * identity when the entry is stored. Blank names and duplicates go.
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
        if (name.isEmpty() || merged.any { it.hasName(name) }) continue
        merged += known.firstOrNull { it.hasName(name) } ?: JournalPerson.named(name)
    }
    return EntryLabels(plainTags.distinct(), merged.distinctPeople())
}

/** The entry with any `@name` tags folded into its people. */
fun Journal.withUnifiedPeople(): Journal {
    val unified = unifyPeople(tags, people)
    return if (unified.tags == tags && unified.people == people) this
    else copy(tags = unified.tags, people = unified.people)
}

/** Everything the entry is filed under in the index: its tags plus each person as `@<id>`. */
val Journal.labelKeys: List<String>
    get() = (tags + people.map { it.key }).distinct()

/** `[@Bart](person:<id>)`: a mention of this person in an entry's text. */
fun JournalPerson.mentionCode(): String = "[${label}](person:$id)"

private val MENTION_OF = Regex("""\[@?[^\[\]]*\]\(\s*person\s*:\s*([^()\n]+?)\s*\)""")

/** Rewrites the mentions of [person] in [content] so they show the person's current name. */
fun renameMentions(content: String, person: JournalPerson): String =
    MENTION_OF.replace(content) { m -> if (m.groupValues[1] == person.id) person.mentionCode() else m.value }

/**
 * A person's context is a handful of short labels: "Friend", "Climbing", "Work". The first one is
 * the main label: the only one shown on small chips, so they stay small. The others are there in
 * a person's details and wherever there is room.
 *
 * They are kept in [JournalPerson.note], separated by [PersonLabels.SEPARATOR], so older notes
 * and synced copies read the same: a note without the separator is simply one label.
 */
object PersonLabels {
    const val SEPARATOR = " · "
    const val MAX_LENGTH = 24

    /** The labels in [note], in order, without blanks or repeats. */
    fun of(note: String?): List<String> =
        note.orEmpty().split("·").map { it.trim() }.filter { it.isNotEmpty() }.distinctBy { it.lowercase() }

    fun join(labels: List<String>): String = labels.joinToString(SEPARATOR)

    /** The main label: the first one. */
    fun main(note: String?): String? = of(note).firstOrNull()

    /** [note] with [label] as its main label, added if it was not on it. */
    fun makeMain(note: String?, label: String): String {
        val clean = label.replace("·", " ").trim()
        if (clean.isEmpty()) return note.orEmpty()
        val labels = of(note)
        // a label that is on already keeps the spelling it has
        val main = labels.firstOrNull { it.equals(clean, ignoreCase = true) } ?: clean
        return join(listOf(main) + labels.filterNot { it.equals(clean, ignoreCase = true) })
    }

    /** [note] with [label] added when it is not on it, and taken off when it is. */
    fun toggle(note: String?, label: String): String {
        val clean = label.replace("·", " ").trim()
        if (clean.isEmpty()) return note.orEmpty()
        val labels = of(note)
        return join(if (labels.any { it.equals(clean, ignoreCase = true) }) labels.filterNot { it.equals(clean, ignoreCase = true) } else labels + clean)
    }

    /**
     * The labels to offer for a person: the ones they have, the ones other [people] have (most
     * used first), then the [defaults]. A label made for one person is there for the next.
     */
    fun suggestions(note: String?, people: List<JournalPerson>, defaults: List<String>, limit: Int = 24): List<String> {
        val used = people.flatMap { of(it.note) }.filter { it.length <= MAX_LENGTH }
        val byUse = used.groupingBy { it.lowercase() }.eachCount()
        val others = used.distinctBy { it.lowercase() }.sortedByDescending { byUse[it.lowercase()] }
        return (of(note) + others + defaults).distinctBy { it.lowercase() }.take(maxOf(limit, of(note).size))
    }
}

/**
 * How a person's icon looks: one of [COLORS] colours and one of [SHAPES] shapes. What the user
 * picked wins. Without a pick the look follows from [JournalPerson.iconSeed], so the same person
 * looks the same on every screen and every day. Nothing about it is random.
 */
object PersonIcon {
    const val COLORS = 6
    const val SHAPES = 8

    private fun seed(person: JournalPerson): Int = person.iconSeed.hashCode().let { if (it == Int.MIN_VALUE) 0 else kotlin.math.abs(it) }

    fun colorOf(person: JournalPerson): Int = (person.color ?: seed(person)).mod(COLORS)

    fun shapeOf(person: JournalPerson): Int = (person.shape ?: (seed(person) / COLORS)).mod(SHAPES)

    /**
     * [after] with the look [before] had. Renaming someone or linking a contact changes what an
     * unpicked look follows from; the icon people know them by must not change with it.
     */
    fun keepLook(before: JournalPerson, after: JournalPerson): JournalPerson =
        if (before.iconSeed == after.iconSeed) after
        else after.copy(color = after.color ?: colorOf(before), shape = after.shape ?: shapeOf(before))

    /**
     * [person] with a look none of [others] has. Namesakes without a contact would otherwise look
     * exactly alike, and telling them apart is the whole point. Someone who already looks
     * different is returned as they are.
     */
    fun distinctFrom(person: JournalPerson, others: List<JournalPerson>): JournalPerson {
        val taken = others.filterNot { it.id == person.id }.map { colorOf(it) to shapeOf(it) }.toSet()
        if ((colorOf(person) to shapeOf(person)) !in taken) return person
        val color = colorOf(person)
        val shape = shapeOf(person)
        // another colour first: it is what the eye picks up; then another shape as well
        for (step in 1 until COLORS * SHAPES) {
            val c = (color + step).mod(COLORS)
            val s = (shape + step / COLORS).mod(SHAPES)
            if ((c to s) !in taken) return person.copy(color = c, shape = s)
        }
        return person
    }
}
