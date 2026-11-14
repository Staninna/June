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
import com.denser.june.core.domain.model.PersonLabels
import com.denser.june.core.domain.model.PersonIcon
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.material3.ColorScheme
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.foundation.combinedClickable
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import android.Manifest
import android.content.pm.PackageManager
import androidx.compose.runtime.LaunchedEffect
import androidx.core.content.ContextCompat
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import com.denser.june.presentation.components.JuneConnectedItem
import com.denser.june.presentation.components.JuneConnectedRowItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.absoluteValue

// -------------------------------------------------------------------------------------------------
// Avatar
// -------------------------------------------------------------------------------------------------

/**
 * Everyone in the journal as they are now, by id. An entry keeps its own copy of the people on
 * it; showing a person through this makes them look the same on every entry and every screen:
 * the name, labels, colour and shape set in one place.
 */
val LocalPeople = compositionLocalOf<Map<String, JournalPerson>> { emptyMap() }

/** This person as the journal knows them now, or as given when it does not (yet). */
@Composable
fun JournalPerson.asKnown(): JournalPerson = LocalPeople.current[id] ?: this

/**
 * The colours a person can have: background and what is drawn on it. They are the app's colours,
 * fixed at the top of the app ([ProvidePersonColors]). An entry can have a colour theme of its
 * own, and a person must not change colour because of the entry they happen to be shown on.
 */
@Composable
fun personColors(): List<Pair<Color, Color>> = LocalPersonColors.current ?: personColorsOf(MaterialTheme.colorScheme)

private val LocalPersonColors = staticCompositionLocalOf<List<Pair<Color, Color>>?> { null }

private fun personColorsOf(c: ColorScheme): List<Pair<Color, Color>> =
    listOf(
        c.primaryContainer to c.onPrimaryContainer,
        c.secondaryContainer to c.onSecondaryContainer,
        c.tertiaryContainer to c.onTertiaryContainer,
        c.primary to c.onPrimary,
        c.tertiary to c.onTertiary,
        c.inversePrimary to c.onSurface,
    )

/** Takes the person colours from the theme in force here, the app's, for everything inside. */
@Composable
fun ProvidePersonColors(content: @Composable () -> Unit) {
    val colors = personColorsOf(MaterialTheme.colorScheme)
    CompositionLocalProvider(LocalPersonColors provides colors, content = content)
}

/** The shapes a person's icon can have. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun personShapes() = remember {
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

private data class AvatarStyle(val container: Color, val content: Color, val shape: Shape)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun avatarStyleFor(person: JournalPerson): AvatarStyle {
    val palettes = personColors()
    val shapes = personShapes()
    // One rule for everyone, everywhere: PersonIcon. What was picked, or else what follows from who they are.
    val (bg, fg) = palettes[PersonIcon.colorOf(person).mod(palettes.size)]
    val shape = shapes[PersonIcon.shapeOf(person).mod(shapes.size)].toShape()
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
        // too small to read: the shape and colour say who it is
        if (size >= 18.dp) {
            Text(
                text = person.initials,
                color = style.content,
                fontWeight = FontWeight.SemiBold,
                fontSize = (size.value * 0.36f).sp,
                maxLines = 1
            )
        }
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
                person.mainLabel?.let { mainLabel ->
                    Text(
                        text = mainLabel,
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
    /** Someone else has the same name: show the context too ("Bart · Work"). */
    showContext: Boolean = false,
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
                text = if (showContext) person.displayName else person.name,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 180.dp)
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

/**
 * Whether June may read contacts. It never needs to: picking and opening a contact work without.
 * With it, a linked contact's organisation or nickname becomes the person's labels.
 */
object ContactsAccess {
    private var asked by mutableStateOf(0)

    fun isGranted(context: Context): Boolean {
        asked // read, so everything showing contact details updates once access is given
        return ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED
    }

    /** Call after the permission dialog was answered. */
    fun answered() {
        asked++
    }
}

/**
 * What the contact itself says about who this is: "Acme · Engineer" from the organisation, or
 * else the nickname. Null without access to contacts, or when the contact has neither.
 */
fun contactContext(context: Context, lookupKey: String): String? {
    if (!ContactsAccess.isGranted(context)) return null
    return runCatching {
        val org = ContactsContract.CommonDataKinds.Organization.CONTENT_ITEM_TYPE
        val nick = ContactsContract.CommonDataKinds.Nickname.CONTENT_ITEM_TYPE
        var company: String? = null
        var title: String? = null
        var nickname: String? = null
        context.contentResolver.query(
            ContactsContract.Data.CONTENT_URI,
            arrayOf(
                ContactsContract.Data.MIMETYPE,
                ContactsContract.CommonDataKinds.Organization.COMPANY,
                ContactsContract.CommonDataKinds.Organization.TITLE,
            ),
            "${ContactsContract.Data.LOOKUP_KEY} = ? AND ${ContactsContract.Data.MIMETYPE} IN (?, ?)",
            arrayOf(lookupKey, org, nick),
            null
        )?.use { c ->
            while (c.moveToNext()) {
                when (c.getString(0)) {
                    org -> {
                        company = company ?: c.getString(1)?.trim()?.takeIf { it.isNotEmpty() }
                        title = title ?: c.getString(2)?.trim()?.takeIf { it.isNotEmpty() }
                    }
                    // a nickname is stored in the same column an organisation keeps its company in
                    nick -> nickname = nickname ?: c.getString(1)?.trim()?.takeIf { it.isNotEmpty() }
                }
            }
        }
        listOfNotNull(company, title).joinToString(" · ").ifEmpty { nickname }?.take(80)
    }.getOrNull()
}

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
