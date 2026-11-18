package com.denser.june.presentation.screens.editor.components.people

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.denser.june.core.R
import com.denser.june.core.domain.model.JournalPerson
import com.denser.june.core.domain.model.PersonIcon
import com.denser.june.presentation.components.JuneConnectedItem
import com.denser.june.presentation.components.JuneConnectedRowItem

// -------------------------------------------------------------------------------------------------
// Add-people sheet (same anatomy as the editor's AddItemSheet: handle, bold title, connected rows)
// -------------------------------------------------------------------------------------------------

@Composable
internal fun SheetHandle() {
    Box(
        modifier = Modifier
            .padding(top = 24.dp)
            .width(36.dp)
            .height(4.dp)
            .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f), CircleShape)
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PeoplePickerSheet(
    selected: List<JournalPerson>,
    known: List<JournalPerson>,
    onAdd: (JournalPerson) -> Unit,
    onRemove: (JournalPerson) -> Unit,
    onPersonClick: (JournalPerson) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val haptics = LocalHapticFeedback.current
    var query by remember { mutableStateOf("") }
    val trimmed = query.trim()

    val everyone = remember(selected, known) { (selected + known).distinctBy { it.id } }
    val pickerContext = LocalContext.current

    val pickContact = rememberContactPicker { picked ->
        // The same contact is the same person. Otherwise the one person with that name who has
        // no contact yet gets it. Anything else is someone new: namesakes are different people.
        val sameName = everyone.filter { it.hasName(picked.name) }
        val person = everyone.firstOrNull { it.contactLookupKey != null && it.contactLookupKey == picked.lookupKey }
            ?: sameName.singleOrNull()?.takeIf { it.contactLookupKey == null }?.copy(contactLookupKey = picked.lookupKey)
            ?: PersonIcon.distinctFrom(JournalPerson(name = picked.name, contactLookupKey = picked.lookupKey), sameName)
        haptics.performHapticFeedback(HapticFeedbackType.Confirm)
        // who they are comes from the contact, when June may read it and nothing was written yet
        val context = person.note ?: picked.lookupKey?.let { contactContext(pickerContext, it) }
        onAdd(person.copy(note = context))
    }

    fun matches(p: JournalPerson) = trimmed.isEmpty() ||
            p.name.contains(trimmed, ignoreCase = true) ||
            p.note.orEmpty().contains(trimmed, ignoreCase = true)

    val inEntry = remember(selected, trimmed) { selected.filter(::matches) }
    val recent = remember(known, selected, trimmed) {
        known.filter { k -> selected.none { it.isSamePerson(k) } }.filter(::matches)
            .sortedBy { trimmed.isNotEmpty() && !it.name.trim().startsWith(trimmed, ignoreCase = true) }
            .take(30)
    }
    val namesakes = remember(everyone, trimmed) { if (trimmed.isEmpty()) emptyList() else everyone.filter { it.hasName(trimmed) } }

    fun addNew() {
        if (trimmed.isEmpty()) return
        // a second Bart gets an icon of their own, so the two can be told apart at a glance
        val person = PersonIcon.distinctFrom(JournalPerson(name = trimmed), namesakes)
        haptics.performHapticFeedback(HapticFeedbackType.Confirm)
        onAdd(person)
        query = ""
        // a second Bart needs something to tell them apart: go straight to their details
        if (namesakes.isNotEmpty()) onPersonClick(person)
    }

    /** Done on the keyboard: the person with that name when there is one to add, else someone new. */
    fun addTyped() {
        if (trimmed.isEmpty()) return
        val existing = namesakes.firstOrNull { n -> selected.none { it.isSamePerson(n) } }
        if (existing != null) {
            haptics.performHapticFeedback(HapticFeedbackType.Confirm)
            onAdd(existing)
            query = ""
        } else if (namesakes.isEmpty()) {
            addNew()
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        dragHandle = null
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            item(key = "header") {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    SheetHandle()
                    Text(
                        text = stringResource(R.string.people_sheet_title),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 16.dp)
                    )
                    Spacer(Modifier.height(20.dp))
                    TextField(
                        value = query,
                        onValueChange = { query = it.take(60) },
                        placeholder = { Text(stringResource(R.string.people_search_hint)) },
                        leadingIcon = {
                            Icon(
                                painterResource(R.drawable.search_24px),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        trailingIcon = {
                            AnimatedVisibility(
                                visible = trimmed.isNotEmpty(),
                                enter = scaleIn() + fadeIn(),
                                exit = scaleOut() + fadeOut()
                            ) {
                                IconButton(onClick = { query = "" }) {
                                    Icon(
                                        painterResource(R.drawable.close_24px),
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        },
                        singleLine = true,
                        shape = CircleShape,
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Words,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(onDone = { addTyped() }),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(12.dp))
                }
            }

            // Quick actions: add what you typed, or pick from the address book.
            val showAddTyped = trimmed.isNotEmpty() && namesakes.isEmpty()
            val actionCount = if (showAddTyped) 2 else 1
            if (showAddTyped) {
                item(key = "add-typed") {
                    PersonListRow(
                        person = JournalPerson(id = "preview", name = trimmed),
                        title = stringResource(R.string.people_add_name, trimmed),
                        subtitle = stringResource(R.string.people_add_new_desc),
                        index = 0,
                        count = actionCount,
                        trailing = {
                            Icon(
                                painterResource(R.drawable.add_circle_24px_fill),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        },
                        onClick = ::addNew,
                        modifier = Modifier.animateItem()
                    )
                }
            }
            item(key = "contacts") {
                JuneConnectedItem(
                    title = stringResource(R.string.people_from_contacts),
                    leadingContent = {
                        Box(
                            Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.secondaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                painterResource(R.drawable.person_24px),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    },
                    trailingContent = {
                        Icon(
                            painterResource(R.drawable.chevron_right_24px),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    index = actionCount - 1,
                    count = actionCount,
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
                    onClick = pickContact,
                    modifier = Modifier
                        .padding(top = if (showAddTyped) 4.dp else 0.dp)
                        .animateItem()
                )
            }

            if (inEntry.isNotEmpty()) {
                item(key = "in-entry-label") {
                    SheetSectionLabel(
                        stringResource(R.string.people_in_entry),
                        trailing = "${selected.size}",
                        modifier = Modifier.animateItem()
                    )
                }
                itemsIndexed(inEntry, key = { _, p -> "in-" + p.id }) { index, person ->
                    PersonListRow(
                        person = person,
                        title = person.name,
                        subtitle = person.note,
                        index = index,
                        count = inEntry.size,
                        selected = true,
                        linked = person.contactLookupKey != null,
                        trailing = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(onClick = { onPersonClick(person) }) {
                                    Icon(
                                        painterResource(R.drawable.edit_24px),
                                        contentDescription = stringResource(R.string.people_edit),
                                        tint = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                }
                                Icon(
                                    painterResource(R.drawable.check_circle_24px_fill),
                                    contentDescription = stringResource(R.string.people_selected),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        },
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.ToggleOff)
                            onRemove(person)
                        },
                        modifier = Modifier
                            .padding(bottom = if (index == inEntry.lastIndex) 0.dp else 4.dp)
                            .animateItem()
                    )
                }
            }

            if (recent.isNotEmpty()) {
                item(key = "recent-label") {
                    SheetSectionLabel(stringResource(R.string.people_recent), modifier = Modifier.animateItem())
                }
            }

            itemsIndexed(recent, key = { _, p -> "recent-" + p.id }) { index, person ->
                PersonListRow(
                    person = person,
                    title = person.name,
                    subtitle = person.note,
                    index = index,
                    count = recent.size,
                    linked = person.contactLookupKey != null,
                    trailing = {
                        Icon(
                            painterResource(R.drawable.add_circle_24px),
                            contentDescription = stringResource(R.string.people_add),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    },
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.ToggleOn)
                        onAdd(person)
                        query = ""
                    },
                    modifier = Modifier
                        .padding(bottom = if (index == recent.lastIndex) 0.dp else 4.dp)
                        .animateItem()
                )
            }

            // Someone with this name exists already, and this is not them: another Bart.
            if (namesakes.isNotEmpty()) {
                item(key = "add-another") {
                    PersonListRow(
                        person = JournalPerson(id = "preview", name = trimmed),
                        title = stringResource(R.string.people_add_another, trimmed),
                        subtitle = stringResource(R.string.people_add_another_desc),
                        index = 0,
                        count = 1,
                        trailing = {
                            Icon(
                                painterResource(R.drawable.add_circle_24px),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        },
                        onClick = ::addNew,
                        modifier = Modifier
                            .padding(top = 16.dp)
                            .animateItem()
                    )
                }
            }
        }
    }
}

@Composable
private fun SheetSectionLabel(text: String, modifier: Modifier = Modifier, trailing: String? = null) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 12.dp, end = 12.dp, top = 24.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f)
        )
        if (trailing != null) {
            Text(
                text = trailing,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun PersonListRow(
    person: JournalPerson,
    title: String,
    subtitle: String?,
    index: Int,
    count: Int,
    onClick: () -> Unit,
    trailing: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    linked: Boolean = false,
) {
    JuneConnectedRowItem(
        leadingContent = { PersonAvatar(person, size = 40.dp) },
        trailingContent = trailing,
        index = index,
        count = count,
        selected = selected,
        containerColor = if (selected) MaterialTheme.colorScheme.secondaryContainer
        else MaterialTheme.colorScheme.surfaceContainerHigh,
        contentPadding = PaddingValues(start = 16.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
        onClick = onClick,
        modifier = modifier
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (selected) MaterialTheme.colorScheme.onSecondaryContainer
                    else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (linked) {
                    Spacer(Modifier.width(6.dp))
                    Icon(
                        painterResource(R.drawable.person_24px_fill),
                        contentDescription = stringResource(R.string.people_linked_contact),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
            if (!subtitle.isNullOrBlank()) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (selected) MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
