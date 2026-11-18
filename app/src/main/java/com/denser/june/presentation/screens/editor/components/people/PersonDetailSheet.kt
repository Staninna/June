package com.denser.june.presentation.screens.editor.components.people

import android.Manifest
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.denser.june.core.R
import com.denser.june.core.domain.model.JournalPerson
import com.denser.june.core.domain.model.PersonIcon
import com.denser.june.core.domain.model.PersonLabels
import com.denser.june.presentation.components.JuneConnectedItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// -------------------------------------------------------------------------------------------------
// Person detail sheet
// -------------------------------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PersonDetailSheet(
    person: JournalPerson,
    onSave: (JournalPerson) -> Unit,
    onRemove: (JournalPerson) -> Unit,
    onDismiss: () -> Unit,
    /** What the destructive button says: leaving this entry, or leaving every entry. */
    removeLabel: String = stringResource(R.string.people_remove),
    /** Everyone else, to notice when the name is shared and context is needed to tell them apart. */
    everyone: List<JournalPerson> = emptyList(),
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var name by remember(person.id) { mutableStateOf(person.name) }
    var note by remember(person.id) { mutableStateOf(person.note.orEmpty()) }
    var lookupKey by remember(person.id) { mutableStateOf(person.contactLookupKey) }
    var color by remember(person.id) { mutableStateOf(person.color) }
    var shape by remember(person.id) { mutableStateOf(person.shape) }

    // A new name or contact must not change the icon people know them by.
    val draft = PersonIcon.keepLook(
        person,
        person.copy(
            name = name.trim().ifEmpty { person.name },
            note = note.trim().ifEmpty { null },
            contactLookupKey = lookupKey,
            color = color,
            shape = shape
        )
    )

    val pickContact = rememberContactPicker { picked ->
        lookupKey = picked.lookupKey
        if (name.isBlank()) name = picked.name
    }
    val hasNamesake = everyone.any { it.id != person.id && it.hasName(draft.name) }

    val contactsGranted = ContactsAccess.isGranted(context)
    val askForContacts = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        ContactsAccess.answered()
    }
    // Context comes from the contact when there is one to read and nothing was written yet.
    LaunchedEffect(lookupKey, contactsGranted) {
        val key = lookupKey ?: return@LaunchedEffect
        if (note.isBlank()) {
            val fromContact = withContext(Dispatchers.IO) { contactContext(context, key) }
            if (fromContact != null && note.isBlank()) note = fromContact
        }
    }

    fun close(save: Boolean) {
        scope.launch { sheetState.hide() }.invokeOnCompletion {
            if (save && draft != person) onSave(draft)
            onDismiss()
        }
    }

    val quickNotes = listOf(
        R.string.people_note_family,
        R.string.people_note_friend,
        R.string.people_note_partner,
        R.string.people_note_colleague,
        R.string.people_note_new,
    ).map { stringResource(it) }
    // Their own labels, then the ones other people have, then the standard ones.
    val labelChoices = remember(everyone, person.id, quickNotes) {
        PersonLabels.suggestions(person.note, everyone.filterNot { it.id == person.id }.distinctBy { it.id }, quickNotes)
    }
    val labels = PersonLabels.of(note)
    val haptics = LocalHapticFeedback.current
    var addingLabel by remember { mutableStateOf(false) }
    var newLabel by remember { mutableStateOf("") }
    val newLabelFocus = remember { FocusRequester() }
    fun addNewLabel() {
        if (newLabel.isNotBlank() && labels.none { it.equals(newLabel.trim(), ignoreCase = true) }) {
            note = PersonLabels.toggle(note, newLabel)
        }
        newLabel = ""
        addingLabel = false
    }

    ModalBottomSheet(
        onDismissRequest = { if (draft != person) onSave(draft); onDismiss() },
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        dragHandle = null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            SheetHandle()
            Spacer(Modifier.height(20.dp))
            PersonAvatar(draft, size = 88.dp)
            Spacer(Modifier.height(12.dp))
            Text(
                draft.name,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            AnimatedVisibility(visible = draft.mainLabel != null) {
                Text(
                    draft.mainLabel.orEmpty(),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(12.dp))
            PersonColorDots(picked = color, onPick = { color = it })
            PersonShapeDots(person = draft, picked = shape, onPick = { shape = it })
            Spacer(Modifier.height(16.dp))

            val fieldColors = TextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
            )
            TextField(
                value = name,
                onValueChange = { name = it.take(60) },
                label = { Text(stringResource(R.string.people_name)) },
                singleLine = true,
                shape = RoundedCornerShape(20.dp),
                colors = fieldColors,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(14.dp))
            Text(
                text = if (hasNamesake && labels.isEmpty()) stringResource(R.string.people_namesake_hint, draft.name)
                else stringResource(R.string.people_note),
                style = MaterialTheme.typography.labelMedium,
                color = if (hasNamesake && labels.isEmpty()) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 8.dp, bottom = 6.dp)
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp)
            ) {
                // The labels this person has come first, the main one in front and filled.
                // Tap puts a label on or off; hold one to make it the main label.
                (labels + labelChoices).distinctBy { it.lowercase() }.take(labels.size + 8).forEach { label ->
                    val on = labels.any { it.equals(label, ignoreCase = true) }
                    PersonLabelChip(
                        label = label,
                        on = on,
                        main = on && labels.first().equals(label, ignoreCase = true),
                        onToggle = { note = PersonLabels.toggle(note, label) },
                        onMakeMain = {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            note = PersonLabels.makeMain(note, label)
                        }
                    )
                }
                if (!addingLabel) {
                    PersonLabelChip(
                        label = stringResource(R.string.people_label_new),
                        on = false,
                        main = false,
                        leadingAdd = true,
                        onToggle = { addingLabel = true },
                        onMakeMain = null
                    )
                }
            }
            AnimatedVisibility(visible = addingLabel) {
                LaunchedEffect(Unit) { newLabelFocus.requestFocus() }
                TextField(
                    value = newLabel,
                    onValueChange = { newLabel = it.replace("·", "").take(PersonLabels.MAX_LENGTH) },
                    placeholder = { Text(stringResource(R.string.people_label_new_hint)) },
                    singleLine = true,
                    shape = RoundedCornerShape(20.dp),
                    colors = fieldColors,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(onDone = { addNewLabel() }),
                    trailingIcon = {
                        IconButton(onClick = ::addNewLabel) {
                            Icon(
                                painterResource(if (newLabel.isBlank()) R.drawable.close_24px else R.drawable.check_24px),
                                contentDescription = stringResource(
                                    if (newLabel.isBlank()) R.string.action_cancel else R.string.people_label_add
                                )
                            )
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                        .focusRequester(newLabelFocus)
                )
            }

            Spacer(Modifier.height(16.dp))

            val linked = lookupKey
            ContactLinkRow(
                linked = linked != null,
                onClick = {
                    if (linked != null) {
                        if (!openContact(context, linked)) {
                            Toast.makeText(context, R.string.people_contact_unavailable, Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        pickContact()
                    }
                },
                onUnlink = { lookupKey = null }
            )
            if (linked != null && !contactsGranted) {
                TextButton(
                    onClick = { askForContacts.launch(Manifest.permission.READ_CONTACTS) },
                    modifier = Modifier.align(Alignment.Start)
                ) {
                    Text(stringResource(R.string.people_use_contact_details))
                }
            }

            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = {
                    scope.launch { sheetState.hide() }.invokeOnCompletion {
                        onRemove(person)
                        onDismiss()
                    }
                }) {
                    Icon(
                        painterResource(R.drawable.delete_24px),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(removeLabel, color = MaterialTheme.colorScheme.error)
                }
                Spacer(Modifier.weight(1f))
                FilledTonalButton(onClick = { close(save = true) }) {
                    Text(stringResource(R.string.action_done))
                }
            }
        }
    }
}

/**
 * The colour of a person, as a row of small dots. Tapping the picked one again gives the choice
 * back to June.
 */
@Composable
private fun PersonColorDots(picked: Int?, onPick: (Int?) -> Unit) {
    val colors = personColors()
    val ring = MaterialTheme.colorScheme.onSurface
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.selectableGroup()
    ) {
        colors.forEachIndexed { index, (background, _) ->
            val isPicked = picked == index
            val label = stringResource(R.string.people_color_n, index + 1)
            // a small dot in a touch target of its own
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .selectable(
                        selected = isPicked,
                        role = Role.RadioButton,
                        onClick = { onPick(if (isPicked) null else index) }
                    )
                    .semantics { contentDescription = label }
            ) {
                Box(
                    Modifier
                        .size(if (isPicked) 26.dp else 20.dp)
                        .then(if (isPicked) Modifier.border(2.dp, ring, CircleShape).padding(4.dp) else Modifier)
                        .clip(CircleShape)
                        .background(background)
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
                )
            }
        }
    }
}

/**
 * The shape of a person's icon, as a row of small shapes in their colour. Tapping the picked one
 * again gives the choice back to June.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun PersonShapeDots(person: JournalPerson, picked: Int?, onPick: (Int?) -> Unit) {
    val shapes = personShapes()
    val fill = avatarStyleFor(person).container
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.selectableGroup()
    ) {
        shapes.forEachIndexed { index, shape ->
            val isPicked = picked == index
            val label = stringResource(R.string.people_shape_n, index + 1)
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(if (isPicked) MaterialTheme.colorScheme.surfaceContainerHighest else Color.Transparent)
                    .selectable(
                        selected = isPicked,
                        role = Role.RadioButton,
                        onClick = { onPick(if (isPicked) null else index) }
                    )
                    .semantics { contentDescription = label }
            ) {
                Box(
                    Modifier
                        .size(22.dp)
                        .clip(shape.toShape())
                        .background(if (isPicked) fill else MaterialTheme.colorScheme.outline)
                )
            }
        }
    }
}

/**
 * One of a person's labels, small. Filled when it is the main label, tinted when it is on.
 * Tap puts it on or off; holding it makes it the main label.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PersonLabelChip(
    label: String,
    on: Boolean,
    main: Boolean,
    onToggle: () -> Unit,
    onMakeMain: (() -> Unit)?,
    leadingAdd: Boolean = false,
) {
    val scheme = MaterialTheme.colorScheme
    val container = when {
        main -> scheme.primary
        on -> scheme.secondaryContainer
        else -> Color.Transparent
    }
    val content = when {
        main -> scheme.onPrimary
        on -> scheme.onSecondaryContainer
        else -> scheme.onSurfaceVariant
    }
    val makeMainLabel = stringResource(R.string.people_label_make_main)
    val mainState = stringResource(R.string.people_label_main)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .heightIn(min = 32.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(container)
            .then(
                if (on) Modifier
                else Modifier.border(1.dp, scheme.outlineVariant, RoundedCornerShape(10.dp))
            )
            .combinedClickable(
                role = Role.Checkbox,
                onClick = onToggle,
                onLongClick = onMakeMain,
                onLongClickLabel = if (onMakeMain != null) makeMainLabel else null
            )
            .semantics {
                if (!leadingAdd) selected = on
                if (main) stateDescription = mainState
            }
            .padding(horizontal = 12.dp)
    ) {
        if (leadingAdd) {
            Icon(painterResource(R.drawable.add_24px), contentDescription = null, tint = content, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = content,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 200.dp)
        )
    }
}

@Composable
private fun ContactLinkRow(linked: Boolean, onClick: () -> Unit, onUnlink: () -> Unit) {
    JuneConnectedItem(
        title = stringResource(if (linked) R.string.people_linked_contact else R.string.people_link_contact),
        leadingContent = {
            Icon(
                painterResource(if (linked) R.drawable.person_24px_fill else R.drawable.person_24px),
                contentDescription = null,
                tint = if (linked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
            )
        },
        trailingContent = {
            if (linked) {
                TextButton(onClick = onUnlink) { Text(stringResource(R.string.people_unlink)) }
            } else {
                Icon(
                    painterResource(R.drawable.chevron_right_24px),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        shape = RoundedCornerShape(28.dp),
        containerColor = if (linked) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
        else MaterialTheme.colorScheme.surfaceContainerHigh,
        contentPadding = PaddingValues(start = 20.dp, end = 12.dp, top = 14.dp, bottom = 14.dp),
        onClick = onClick
    )
}
