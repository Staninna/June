package com.denser.june.presentation.screens.editor.components.dialogs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.denser.june.core.R
import com.denser.june.core.domain.model.EntryLabels
import com.denser.june.core.domain.model.JournalPerson
import com.denser.june.core.domain.model.sharesName
import com.denser.june.core.domain.model.distinctPeople
import com.denser.june.core.domain.model.enums.TagCategory
import com.denser.june.core.domain.model.resolvePeople
import com.denser.june.core.domain.model.sharedNames
import com.denser.june.core.domain.model.unifyPeople
import com.denser.june.core.domain.model.withPerson
import com.denser.june.presentation.screens.editor.components.people.PeoplePickerSheet
import com.denser.june.presentation.screens.editor.components.people.PersonChip
import com.denser.june.presentation.screens.editor.components.people.PersonDetailSheet
import com.denser.june.presentation.screens.editor.components.people.PersonSuggestionChip
import com.denser.june.presentation.utils.TagUiUtils

/**
 * The people being edited in the tags dialog, next to its tags: who is on the entry, the people
 * picker and the person being looked at. Kept here so upstream's [JournalTagsDialog] only holds
 * one of these and asks it what to show.
 */
@Stable
class PeopleLabelsState(private val initial: List<JournalPerson>) {
    /** Everyone in the journal; follows the dialog's input, the edits above stay. */
    var known: List<JournalPerson> = emptyList()
        internal set

    var people by mutableStateOf(initial)
        private set
    var showPicker by mutableStateOf(false)
    private var editingId by mutableStateOf<String?>(null)

    val hasChanges: Boolean get() = people != initial
    val editing: JournalPerson? get() = people.firstOrNull { it.id == editingId }

    /** Names more than one of the people around share; those people show their main label. */
    val sharedNames: Set<String> get() = (known + people).distinctPeople().sharedNames()

    /** People from other entries, to add with one tap. */
    val recent: List<JournalPerson> get() = known.filter { k -> people.none { it.isSamePerson(k) } }.take(8)

    fun add(person: JournalPerson) { people = people.withPerson(person) }
    fun remove(person: JournalPerson) { people = people.filterNot { it.id == person.id } }
    fun update(person: JournalPerson) { people = people.map { if (it.id == person.id) person else it }.distinctPeople() }
    fun reset() { people = initial }
    fun edit(person: JournalPerson) { editingId = person.id }
    fun stopEditing() { editingId = null }

    /** [tags] with [label] added: `@name` adds a person and leaves the tags as they are. */
    fun addLabel(tags: List<String>, label: String): List<String> {
        val unified: EntryLabels = unifyPeople(tags + label.trim(), people, known)
        people = unified.people.resolvePeople(known)
        return unified.tags
    }

    /** Known people matching an `@name` being typed, best first. Nothing for other input. */
    fun matches(input: String): List<JournalPerson> {
        if (!input.startsWith(JournalPerson.PERSON_PREFIX)) return emptyList()
        val query = input.drop(1).trim()
        return known.filter { k -> people.none { it.isSamePerson(k) } }
            .filter { query.isEmpty() || it.name.contains(query, ignoreCase = true) || it.note.orEmpty().contains(query, ignoreCase = true) }
            .sortedBy { !it.name.trim().startsWith(query, ignoreCase = true) }
            .take(12)
    }
}

@Composable
fun rememberPeopleLabelsState(people: List<JournalPerson>, known: List<JournalPerson>): PeopleLabelsState =
    remember { PeopleLabelsState(people) }.also { it.known = known }

/** The people picker and the sheet of the person being looked at, when open. */
@Composable
fun PeopleLabelSheets(state: PeopleLabelsState) {
    if (state.showPicker) {
        PeoplePickerSheet(
            selected = state.people,
            known = state.known,
            onAdd = state::add,
            onRemove = state::remove,
            onPersonClick = {
                state.showPicker = false
                state.edit(it)
            },
            onDismiss = { state.showPicker = false }
        )
    }
    state.editing?.let { person ->
        PersonDetailSheet(
            person = person,
            onSave = state::update,
            onRemove = state::remove,
            onDismiss = state::stopEditing,
            everyone = state.known + state.people
        )
    }
}

/** The People section of the tags dialog: the people on the entry, then people to add. */
@Composable
fun PeopleTagSection(state: PeopleLabelsState, onBeforeOpen: () -> Unit) {
    val category = TagCategory.People
    val spec = TagUiUtils.getCategoryUiSpec(category)
    val recent = state.recent
    TagSectionCard(
        title = category.label,
        prefix = category.prefix,
        iconRes = spec.iconRes,
        tags = emptyList(),
        onRemove = {},
        emptyMessage = stringResource(spec.emptyMessageRes),
        tintColor = spec.color,
        containerColor = spec.containerColor,
        onAddClick = {
            onBeforeOpen()
            state.showPicker = true
        },
        body = if (state.people.isEmpty() && recent.isEmpty()) null else {
            {
                PeopleSectionBody(
                    people = state.people,
                    recent = recent,
                    sharedNames = state.sharedNames,
                    emptyMessage = stringResource(spec.emptyMessageRes),
                    chipColor = spec.containerColor,
                    onPersonClick = {
                        onBeforeOpen()
                        state.edit(it)
                    },
                    onRemove = state::remove,
                    onAddRecent = state::add
                )
            }
        }
    )
}

/** The people on the entry as chips, then people from other entries to add with one tap. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PeopleSectionBody(
    people: List<JournalPerson>,
    recent: List<JournalPerson>,
    sharedNames: Set<String>,
    emptyMessage: String,
    chipColor: Color,
    onPersonClick: (JournalPerson) -> Unit,
    onRemove: (JournalPerson) -> Unit,
    onAddRecent: (JournalPerson) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (people.isEmpty()) {
            Text(
                text = emptyMessage,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.padding(start = 4.dp)
            )
        } else {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                people.forEach { person ->
                    key(person.id) {
                        PersonChip(
                            person = person,
                            color = chipColor,
                            onClick = { onPersonClick(person) },
                            onRemove = { onRemove(person) }
                        )
                    }
                }
            }
        }
        if (recent.isNotEmpty()) {
            Text(
                text = stringResource(R.string.people_recent),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp)
            )
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(recent, key = { it.id }) { person ->
                    PersonSuggestionChip(
                        person = person,
                        showContext = person.sharesName(sharedNames),
                        onClick = { onAddRecent(person) },
                        modifier = Modifier.animateItem()
                    )
                }
            }
        }
    }
}
