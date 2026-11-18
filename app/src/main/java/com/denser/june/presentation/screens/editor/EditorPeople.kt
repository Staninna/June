package com.denser.june.presentation.screens.editor

import com.denser.june.core.domain.model.JournalPerson
import com.denser.june.core.domain.model.resolvePeople
import com.denser.june.core.domain.model.unifyPeople
import com.denser.june.core.domain.model.withPerson

/*
 * The people on the entry being edited, as plain steps on [EditorState]. EditorVM (upstream's
 * file) only calls these; writing a changed person to every entry stays in the view model.
 */

/** The entry with [tags]; any `@name` among them becomes a person on it. */
internal fun EditorState.withTags(tags: List<String>): EditorState = withLabels(tags, people)

/** The entry with the tags and people edited together in the tags dialog. */
internal fun EditorState.withLabels(tags: List<String>, people: List<JournalPerson>): EditorState {
    val unified = unifyPeople(tags, people, knownPeople)
    return copy(tags = unified.tags, people = unified.people.resolvePeople(knownPeople))
}

internal fun EditorState.withPersonAdded(person: JournalPerson): EditorState = copy(people = people.withPerson(person))

internal fun EditorState.withPersonChanged(person: JournalPerson): EditorState =
    copy(people = people.map { if (it.id == person.id) person else it })

internal fun EditorState.withoutPerson(id: String): EditorState = copy(people = people.filterNot { it.id == id })

/**
 * The entry once the journal's people are known. A new entry started from a person (its route
 * carried `@<id>` or `@name`) gets that person now.
 */
internal fun EditorState.withKnownPeople(known: List<JournalPerson>): EditorState {
    val started = pendingPeople.mapNotNull { label ->
        known.firstOrNull { it.key == label }
            ?: JournalPerson.nameFromLabel(label).takeIf { it.isNotEmpty() }?.let(JournalPerson::named)
    }
    val people = started.fold(people) { all, person -> all.withPerson(person) }.resolvePeople(known)
    return copy(knownPeople = known, people = people, pendingPeople = emptyList())
}

/** The person as the journal knew them before an edit: on this entry, or on any other. */
internal fun EditorState.personWithId(id: String): JournalPerson? =
    people.firstOrNull { it.id == id } ?: knownPeople.firstOrNull { it.id == id }
