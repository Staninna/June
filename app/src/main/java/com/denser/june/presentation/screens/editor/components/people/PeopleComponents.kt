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
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.denser.june.core.R
import com.denser.june.core.domain.model.JournalPerson
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import com.denser.june.presentation.components.JuneConnectedItem
import com.denser.june.presentation.components.JuneConnectedRowItem
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
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
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

/**
 * A person as a chip: avatar, name and context. The same chip is used wherever people show up as
 * labels. With [onRemove] it gets a close button; tapping the chip itself opens the person.
 */
@Composable
fun PersonChip(
    person: JournalPerson,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
    onRemove: (() -> Unit)? = null,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = color,
        modifier = modifier.heightIn(min = 36.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(
                start = 3.dp,
                end = if (onRemove != null) 2.dp else 12.dp,
                top = 3.dp,
                bottom = 3.dp
            )
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
            if (onRemove != null) {
                IconButton(onClick = onRemove, modifier = Modifier.size(32.dp)) {
                    Icon(
                        painter = painterResource(R.drawable.close_24px),
                        contentDescription = stringResource(R.string.people_remove_name, person.name),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

/** A known person offered for one-tap adding. */
@Composable
fun PersonSuggestionChip(
    person: JournalPerson,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = Color.Transparent,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier.heightIn(min = 32.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 3.dp, end = 10.dp, top = 3.dp, bottom = 3.dp)
        ) {
            PersonAvatar(person, size = 26.dp)
            Spacer(Modifier.width(6.dp))
            Text(
                text = person.name,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 120.dp)
            )
            Spacer(Modifier.width(4.dp))
            Icon(
                painter = painterResource(R.drawable.add_24px),
                contentDescription = stringResource(R.string.people_add_name, person.name),
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp)
            )
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
// Add-people sheet (same anatomy as the editor's AddItemSheet: handle, bold title, connected rows)
// -------------------------------------------------------------------------------------------------

@Composable
private fun SheetHandle() {
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

    val pickContact = rememberContactPicker { picked ->
        val everyone = selected + known
        val existing = everyone.firstOrNull { it.contactLookupKey != null && it.contactLookupKey == picked.lookupKey }
            ?: everyone.firstOrNull { it.name.trim().equals(picked.name, ignoreCase = true) }
                ?.copy(contactLookupKey = picked.lookupKey)
        haptics.performHapticFeedback(HapticFeedbackType.Confirm)
        onAdd(existing ?: JournalPerson(name = picked.name, contactLookupKey = picked.lookupKey))
    }

    fun matches(p: JournalPerson) = trimmed.isEmpty() ||
            p.name.contains(trimmed, ignoreCase = true) ||
            p.note.orEmpty().contains(trimmed, ignoreCase = true)

    val inEntry = remember(selected, trimmed) { selected.filter(::matches) }
    val recent = remember(known, selected, trimmed) {
        known.filter { k -> selected.none { it.isSamePerson(k) } }.filter(::matches).take(30)
    }
    val exactExists = trimmed.isNotEmpty() &&
            (known + selected).any { it.name.equals(trimmed, ignoreCase = true) }

    fun addTyped() {
        if (trimmed.isEmpty()) return
        val match = known.firstOrNull { it.name.equals(trimmed, ignoreCase = true) }
        haptics.performHapticFeedback(HapticFeedbackType.Confirm)
        onAdd(match ?: JournalPerson(name = trimmed))
        query = ""
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
                        modifier = Modifier.padding(top = 16.dp, bottom = 4.dp)
                    )
                    Text(
                        text = stringResource(R.string.people_sheet_subtitle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 24.dp)
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
            val showAddTyped = trimmed.isNotEmpty() && !exactExists
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
                        onClick = ::addTyped,
                        modifier = Modifier.animateItem()
                    )
                }
            }
            item(key = "contacts") {
                JuneConnectedItem(
                    title = stringResource(R.string.people_from_contacts),
                    subtitle = stringResource(R.string.people_contacts_desc),
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
                        subtitle = personSubtitle(person),
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

            item(key = "recent-label") {
                SheetSectionLabel(stringResource(R.string.people_recent), modifier = Modifier.animateItem())
            }

            if (recent.isEmpty()) {
                item(key = "recent-empty") {
                    Text(
                        text = if (trimmed.isNotEmpty()) stringResource(R.string.people_no_match, trimmed)
                        else stringResource(R.string.people_empty_recent),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 4.dp)
                            .animateItem()
                    )
                }
            }

            itemsIndexed(recent, key = { _, p -> "recent-" + p.identityKey }) { index, person ->
                PersonListRow(
                    person = person,
                    title = person.name,
                    subtitle = personSubtitle(person),
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
        }
    }
}

@Composable
private fun personSubtitle(person: JournalPerson): String? =
    person.note?.takeIf { it.isNotBlank() }
        ?: if (person.contactLookupKey != null) stringResource(R.string.people_from_contacts_label) else null

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
            AnimatedVisibility(visible = draft.note != null) {
                Text(
                    draft.note.orEmpty(),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                stringResource(R.string.people_everywhere_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 6.dp, start = 24.dp, end = 24.dp)
            )
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
                shape = RoundedCornerShape(20.dp),
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
                shape = RoundedCornerShape(20.dp),
                colors = fieldColors,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp)
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

@Composable
private fun ContactLinkRow(linked: Boolean, onClick: () -> Unit, onUnlink: () -> Unit) {
    JuneConnectedItem(
        title = stringResource(if (linked) R.string.people_linked_contact else R.string.people_link_contact),
        subtitle = stringResource(if (linked) R.string.people_linked_contact_desc else R.string.people_link_contact_desc),
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
