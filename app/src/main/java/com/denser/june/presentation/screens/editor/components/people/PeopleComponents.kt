package com.denser.june.presentation.screens.editor.components.people

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.denser.june.core.R
import com.denser.june.core.domain.model.JournalPerson
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue

// -------------------------------------------------------------------------------------------------
// Avatar
// -------------------------------------------------------------------------------------------------

private data class AvatarStyle(val container: Color, val content: Color, val shape: Shape)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun avatarStyleFor(person: JournalPerson): AvatarStyle {
    val c = MaterialTheme.colorScheme
    val palettes = listOf(
        c.primaryContainer to c.onPrimaryContainer,
        c.secondaryContainer to c.onSecondaryContainer,
        c.tertiaryContainer to c.onTertiaryContainer,
        c.primary to c.onPrimary,
        c.tertiary to c.onTertiary,
        c.inversePrimary to c.onSurface,
    )
    val shapes = remember {
        listOf(
            MaterialShapes.Cookie9Sided,
            MaterialShapes.Clover4Leaf,
            MaterialShapes.Sunny,
            MaterialShapes.Cookie6Sided,
            MaterialShapes.Flower,
            MaterialShapes.Pill,
            MaterialShapes.Circle,
            MaterialShapes.Gem,
        )
    }
    val hash = person.identityKey.hashCode().absoluteValue
    val (bg, fg) = palettes[hash % palettes.size]
    val shape = shapes[(hash / palettes.size) % shapes.size].toShape()
    return AvatarStyle(bg, fg, shape)
}

/** A playful, deterministic avatar: same person => same shape and colour, everywhere. */
@Composable
fun PersonAvatar(
    person: JournalPerson,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
) {
    val style = avatarStyleFor(person)
    Box(
        modifier = modifier
            .size(size)
            .clip(style.shape)
            .background(style.container),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = person.initials,
            color = style.content,
            fontWeight = FontWeight.SemiBold,
            fontSize = (size.value * 0.36f).sp,
            maxLines = 1
        )
    }
}

/** Overlapping avatars, e.g. for compact summaries. */
@Composable
fun PeopleAvatarStack(
    people: List<JournalPerson>,
    modifier: Modifier = Modifier,
    size: Dp = 28.dp,
    max: Int = 4,
) {
    val shown = people.take(max)
    Row(modifier = modifier) {
        shown.forEachIndexed { i, person ->
            PersonAvatar(
                person = person,
                size = size,
                modifier = Modifier
                    .offset(x = -(size * 0.3f) * i)
                    .border(2.dp, MaterialTheme.colorScheme.surface, CircleShape)
            )
        }
        if (people.size > max) {
            Box(
                modifier = Modifier
                    .offset(x = -(size * 0.3f) * shown.size)
                    .size(size)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "+${people.size - max}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// -------------------------------------------------------------------------------------------------
// Editor strip
// -------------------------------------------------------------------------------------------------

/** "With  (A) Anna · Sister   (T) Tom   (+)" — shown under the metadata bar when people are added. */
@Composable
fun EditorPeopleRow(
    people: List<JournalPerson>,
    readOnly: Boolean,
    onPersonClick: (JournalPerson) -> Unit,
    onAddClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = people.isNotEmpty(),
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier
    ) {
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            item(key = "label") {
                Text(
                    text = stringResource(R.string.people_with),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(end = 2.dp)
                )
            }
            items(people, key = { it.id }) { person ->
                PersonChip(
                    person = person,
                    onClick = { if (!readOnly) onPersonClick(person) },
                    modifier = Modifier.animateItem()
                )
            }
            if (!readOnly) {
                item(key = "add") {
                    Surface(
                        onClick = onAddClick,
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                painter = painterResource(R.drawable.add_24px),
                                contentDescription = stringResource(R.string.people_add),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PersonChip(
    person: JournalPerson,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier.heightIn(min = 36.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 3.dp, end = 12.dp, top = 3.dp, bottom = 3.dp)
        ) {
            PersonAvatar(person, size = 30.dp)
            Spacer(Modifier.width(8.dp))
            Column {
                Text(
                    text = person.name,
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 140.dp)
                )
                if (!person.note.isNullOrBlank()) {
                    Text(
                        text = person.note.orEmpty(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.widthIn(max = 140.dp)
                    )
                }
            }
            if (person.contactLookupKey != null) {
                Spacer(Modifier.width(6.dp))
                Icon(
                    painter = painterResource(R.drawable.person_24px_fill),
                    contentDescription = stringResource(R.string.people_linked_contact),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}

// -------------------------------------------------------------------------------------------------
// Contacts helpers (no READ_CONTACTS permission: the system picker grants access to one contact)
// -------------------------------------------------------------------------------------------------

private data class PickedContact(val name: String, val lookupKey: String?)

private fun readPickedContact(context: Context, uri: Uri): PickedContact? = runCatching {
    context.contentResolver.query(
        uri,
        arrayOf(ContactsContract.Contacts.DISPLAY_NAME_PRIMARY, ContactsContract.Contacts.LOOKUP_KEY),
        null, null, null
    )?.use { c ->
        if (!c.moveToFirst()) return@use null
        val name = c.getString(0)?.trim().orEmpty()
        if (name.isEmpty()) null else PickedContact(name, c.getString(1))
    }
}.getOrNull()

fun openContact(context: Context, lookupKey: String): Boolean = try {
    val uri = Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_LOOKUP_URI, lookupKey)
    context.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    true
} catch (_: ActivityNotFoundException) {
    false
} catch (_: SecurityException) {
    false
}

@Composable
private fun rememberContactPicker(onPicked: (PickedContact) -> Unit): () -> Unit {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.PickContact()) { uri ->
        uri?.let { readPickedContact(context, it) }?.let(onPicked)
    }
    return {
        try {
            launcher.launch(null)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(context, R.string.people_contact_unavailable, Toast.LENGTH_SHORT).show()
        }
    }
}

// -------------------------------------------------------------------------------------------------
// Add-people sheet
// -------------------------------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
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
    var query by remember { mutableStateOf("") }
    val trimmed = query.trim()

    val pickContact = rememberContactPicker { picked ->
        val existing = known.firstOrNull { it.contactLookupKey != null && it.contactLookupKey == picked.lookupKey }
        onAdd(existing ?: JournalPerson(name = picked.name, contactLookupKey = picked.lookupKey))
    }

    val recent = remember(known, selected, trimmed) {
        known.filter { k -> selected.none { it.isSamePerson(k) } }
            .filter { trimmed.isEmpty() || it.name.contains(trimmed, ignoreCase = true) || it.note.orEmpty().contains(trimmed, ignoreCase = true) }
            .take(30)
    }
    val exactExists = trimmed.isNotEmpty() &&
            (known + selected).any { it.name.equals(trimmed, ignoreCase = true) }

    fun addTyped() {
        if (trimmed.isEmpty()) return
        val match = known.firstOrNull { it.name.equals(trimmed, ignoreCase = true) }
        onAdd(match ?: JournalPerson(name = trimmed))
        query = ""
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .navigationBarsPadding(),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            item(key = "header") {
                Row(
                    modifier = Modifier.padding(horizontal = 24.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier
                            .size(48.dp)
                            .clip(MaterialShapes.Cookie6Sided.toShape())
                            .background(MaterialTheme.colorScheme.tertiaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painterResource(R.drawable.groups_24px_fill),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    }
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            stringResource(R.string.people_sheet_title),
                            style = MaterialTheme.typography.headlineSmall
                        )
                        Text(
                            stringResource(R.string.people_sheet_subtitle),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(Modifier.height(20.dp))
            }

            item(key = "input") {
                Column(Modifier.padding(horizontal = 16.dp)) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it.take(60) },
                        placeholder = { Text(stringResource(R.string.people_search_hint)) },
                        leadingIcon = { Icon(painterResource(R.drawable.person_24px), null) },
                        trailingIcon = {
                            AnimatedVisibility(
                                visible = trimmed.isNotEmpty(),
                                enter = scaleIn() + fadeIn(),
                                exit = scaleOut() + fadeOut()
                            ) {
                                IconButton(onClick = ::addTyped) {
                                    Icon(
                                        painterResource(R.drawable.add_circle_24px_fill),
                                        contentDescription = stringResource(R.string.people_add_name, trimmed),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
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
                    Spacer(Modifier.height(10.dp))
                    FilledTonalButton(
                        onClick = pickContact,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                    ) {
                        Icon(
                            painterResource(R.drawable.person_24px),
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.people_from_contacts))
                    }
                }
            }

            if (selected.isNotEmpty()) {
                item(key = "selected") {
                    SheetSectionLabel(stringResource(R.string.people_in_entry))
                    FlowRow(
                        modifier = Modifier
                            .padding(horizontal = 16.dp)
                            .animateContentSize(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        selected.forEach { person ->
                            SelectedPersonPill(
                                person = person,
                                onClick = { onPersonClick(person) },
                                onRemove = { onRemove(person) }
                            )
                        }
                    }
                }
            }

            if (trimmed.isNotEmpty() && !exactExists) {
                item(key = "add-typed") {
                    Spacer(Modifier.height(8.dp))
                    PersonRow(
                        person = JournalPerson(id = "preview", name = trimmed),
                        title = stringResource(R.string.people_add_name, trimmed),
                        subtitle = null,
                        trailingIcon = R.drawable.add_24px,
                        onClick = ::addTyped
                    )
                }
            }

            item(key = "recent-label") {
                SheetSectionLabel(stringResource(R.string.people_recent))
            }

            if (recent.isEmpty()) {
                item(key = "recent-empty") {
                    Text(
                        text = if (trimmed.isNotEmpty()) stringResource(R.string.people_no_match, trimmed)
                        else stringResource(R.string.people_empty_recent),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
                    )
                }
            }

            items(recent, key = { "recent-" + it.identityKey }) { person ->
                PersonRow(
                    person = person,
                    title = person.name,
                    subtitle = person.note,
                    linked = person.contactLookupKey != null,
                    trailingIcon = R.drawable.add_24px,
                    onClick = {
                        onAdd(person)
                        query = ""
                    },
                    modifier = Modifier.animateItem()
                )
            }
        }
    }
}

@Composable
private fun SheetSectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 20.dp, bottom = 8.dp)
    )
}

@Composable
private fun SelectedPersonPill(
    person: JournalPerson,
    onClick: () -> Unit,
    onRemove: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .clickable(role = Role.Button, onClick = onClick)
                    .padding(start = 4.dp, top = 4.dp, bottom = 4.dp, end = 4.dp)
            ) {
                PersonAvatar(person, size = 28.dp)
                Spacer(Modifier.width(8.dp))
                Text(
                    person.name,
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 160.dp)
                )
            }
            IconButton(onClick = onRemove, modifier = Modifier.size(36.dp)) {
                Icon(
                    painterResource(R.drawable.close_24px),
                    contentDescription = stringResource(R.string.people_remove),
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
private fun PersonRow(
    person: JournalPerson,
    title: String,
    subtitle: String?,
    trailingIcon: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    linked: Boolean = false,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 10.dp)
    ) {
        PersonAvatar(person, size = 44.dp)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
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
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Icon(
            painterResource(trailingIcon),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

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
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var name by remember(person.id) { mutableStateOf(person.name) }
    var note by remember(person.id) { mutableStateOf(person.note.orEmpty()) }
    var lookupKey by remember(person.id) { mutableStateOf(person.contactLookupKey) }

    val draft = person.copy(
        name = name.trim().ifEmpty { person.name },
        note = note.trim().ifEmpty { null },
        contactLookupKey = lookupKey
    )

    val pickContact = rememberContactPicker { picked ->
        lookupKey = picked.lookupKey
        if (name.isBlank()) name = picked.name
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

    ModalBottomSheet(
        onDismissRequest = { if (draft != person) onSave(draft); onDismiss() },
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp)
                .padding(bottom = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            PersonAvatar(draft, size = 88.dp)
            Spacer(Modifier.height(12.dp))
            Text(
                draft.name,
                style = MaterialTheme.typography.headlineSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            AnimatedVisibility(visible = draft.note != null) {
                Text(
                    draft.note.orEmpty(),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(20.dp))

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
                shape = RoundedCornerShape(16.dp),
                colors = fieldColors,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(10.dp))
            TextField(
                value = note,
                onValueChange = { note = it.take(80) },
                label = { Text(stringResource(R.string.people_note)) },
                placeholder = { Text(stringResource(R.string.people_note_placeholder)) },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                colors = fieldColors,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                quickNotes.forEach { q ->
                    FilterChip(
                        selected = note.equals(q, ignoreCase = true),
                        onClick = { note = if (note.equals(q, ignoreCase = true)) "" else q },
                        label = { Text(q) },
                        shape = RoundedCornerShape(10.dp)
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            Spacer(Modifier.height(8.dp))

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
                    Text(stringResource(R.string.people_remove), color = MaterialTheme.colorScheme.error)
                }
                Spacer(Modifier.weight(1f))
                FilledTonalButton(onClick = { close(save = true) }) {
                    Text(stringResource(R.string.action_done))
                }
            }
        }
    }
}

@Composable
private fun ContactLinkRow(linked: Boolean, onClick: () -> Unit, onUnlink: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = if (linked) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
        else MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 12.dp)
        ) {
            Icon(
                painterResource(if (linked) R.drawable.person_24px_fill else R.drawable.person_24px),
                contentDescription = null,
                tint = if (linked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(if (linked) R.string.people_linked_contact else R.string.people_link_contact),
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    stringResource(if (linked) R.string.people_linked_contact_desc else R.string.people_link_contact_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (linked) {
                TextButton(onClick = onUnlink) { Text(stringResource(R.string.people_unlink)) }
            } else {
                Icon(
                    painterResource(R.drawable.chevron_right_24px),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(end = 8.dp)
                )
            }
        }
    }
}
