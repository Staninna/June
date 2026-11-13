package com.denser.june.presentation.screens.editor.components.dialogs
import com.denser.june.presentation.components.JuneCloseButton

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.denser.june.core.R
import com.denser.june.core.domain.model.JournalPerson
import com.denser.june.core.domain.model.distinctPeople
import com.denser.june.core.domain.model.enums.TagCategory
import com.denser.june.core.domain.model.unifyPeople
import com.denser.june.core.domain.model.resolvePeople
import com.denser.june.core.domain.model.sharedNames
import com.denser.june.core.domain.model.withPerson
import com.denser.june.presentation.screens.editor.components.people.PeoplePickerSheet
import com.denser.june.presentation.screens.editor.components.people.PersonChip
import com.denser.june.presentation.screens.editor.components.people.PersonDetailSheet
import com.denser.june.presentation.screens.editor.components.people.PersonSuggestionChip
import androidx.compose.ui.text.input.KeyboardCapitalization
import com.denser.june.presentation.components.JuneAppBarType
import com.denser.june.presentation.components.JuneDialog
import com.denser.june.presentation.components.JuneFloatingAction
import com.denser.june.presentation.components.JuneFloatingActionBar
import com.denser.june.presentation.components.JuneFullScreenDialog
import com.denser.june.presentation.components.JuneTopAppBar
import com.denser.june.presentation.utils.TagUtils
import com.denser.june.presentation.utils.UiUtils


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JournalTagsDialog(
    tags: List<String>,
    people: List<JournalPerson>,
    knownPeople: List<JournalPerson>,
    suggestions: List<String>,
    onSave: (List<String>, List<JournalPerson>) -> Unit,
    onSearchTags: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var localTags by remember { mutableStateOf(tags) }
    var localPeople by remember { mutableStateOf(people) }
    val hasChanges = localTags != tags || localPeople != people

    var showPeoplePicker by remember { mutableStateOf(false) }
    var editingPersonId by remember { mutableStateOf<String?>(null) }

    fun addPerson(person: JournalPerson) {
        localPeople = localPeople.withPerson(person)
    }

    /** Typed or suggested labels: `@name` adds a person, anything else a tag. */
    fun addLabel(label: String) {
        val unified = unifyPeople(localTags + label.trim(), localPeople, knownPeople)
        localTags = unified.tags
        localPeople = unified.people.resolvePeople(knownPeople)
    }

    var tagInput by remember { mutableStateOf("") }
    var showInfoDialog by remember { mutableStateOf(false) }
    var showExitDialog by remember { mutableStateOf(false) }

    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current

    /** Tags are looked up in the journal; people are suggested from [knownPeople] right here. */
    fun search(input: String) = onSearchTags(if (input.startsWith("@")) "" else input)

    val sharedNames = remember(knownPeople, localPeople) { (knownPeople + localPeople).distinctPeople().sharedNames() }
    val personMatches = remember(tagInput, knownPeople, localPeople) {
        if (!tagInput.startsWith("@")) emptyList()
        else {
            val query = tagInput.drop(1).trim()
            knownPeople.filter { k -> localPeople.none { it.isSamePerson(k) } }
                .filter { query.isEmpty() || it.name.contains(query, ignoreCase = true) || it.note.orEmpty().contains(query, ignoreCase = true) }
                .sortedBy { !it.name.trim().startsWith(query, ignoreCase = true) }
                .take(12)
        }
    }

    fun onInsertPrefix(prefix: String) {
        tagInput = prefix
        search(prefix)
        focusManager.clearFocus()
        focusRequester.requestFocus()
    }

    fun handleDismiss() {
        if (hasChanges) {
            showExitDialog = true
        } else {
            onDismiss()
        }
    }

    if (showInfoDialog) {
        TagInfoDialog(onDismiss = { showInfoDialog = false })
    }

    if (showPeoplePicker) {
        PeoplePickerSheet(
            selected = localPeople,
            known = knownPeople,
            onAdd = ::addPerson,
            onRemove = { person -> localPeople = localPeople.filterNot { it.id == person.id } },
            onPersonClick = {
                showPeoplePicker = false
                editingPersonId = it.id
            },
            onDismiss = { showPeoplePicker = false }
        )
    }

    localPeople.firstOrNull { it.id == editingPersonId }?.let { person ->
        PersonDetailSheet(
            person = person,
            onSave = { updated ->
                localPeople = localPeople.map { if (it.id == updated.id) updated else it }.distinctPeople()
            },
            onRemove = { removed -> localPeople = localPeople.filterNot { it.id == removed.id } },
            onDismiss = { editingPersonId = null },
            everyone = knownPeople + localPeople
        )
    }

    if (showExitDialog) {
        JuneDialog(
            onDismissRequest = { showExitDialog = false },
            title = stringResource(R.string.save_changes_title),
            icon = R.drawable.file_save_24px,
            confirmButton = {
                Button(onClick = {
                    showExitDialog = false
                    onSave(localTags, localPeople)
                    onDismiss()
                }) { Text(stringResource(R.string.action_save)) }
            },
            dismissButton = {
                OutlinedButton(onClick = {
                    showExitDialog = false
                    onDismiss()
                }) { Text(stringResource(R.string.action_discard)) }
            },
            text = { Text(stringResource(R.string.unsaved_tags_message)) }
        )
    }

    JuneFullScreenDialog(
        onDismissRequest = ::handleDismiss,
    ) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = MaterialTheme.colorScheme.surface,
            topBar = {
                JuneTopAppBar(
                    type = JuneAppBarType.CenterAligned,
                    title = {
                        Text(
                            text = "Manage Tags",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    },
                    navigationIcon = {
                    JuneCloseButton(onClick = ::handleDismiss)
                },
                    actions = {
                        IconButton(onClick = { showInfoDialog = true }) {
                            Icon(
                                painter = painterResource(R.drawable.info_24px),
                                contentDescription = "Tag Help",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                )
            },
            floatingActionButtonPosition = FabPosition.Center,
            floatingActionButton = {
                JuneFloatingActionBar {
                    JuneFloatingAction(
                        onClick = {
                            if (hasChanges) {
                                localTags = tags
                                localPeople = people
                            }
                        },
                        label = stringResource(R.string.action_reset),
                        icon = { Icon(painterResource(R.drawable.replay_24px), null) },
                        enabled = hasChanges,
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    JuneFloatingAction(
                        onClick = {
                            if (hasChanges) {
                                onSave(localTags, localPeople)
                            }
                            onDismiss()
                        },
                        label = stringResource(R.string.action_done),
                        icon = {
                            Icon(painterResource(R.drawable.check_24px), null)
                        }
                    )
                }
            },
            bottomBar = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    TagInputArea(
                        tagInput = tagInput,
                        onInputChange = {
                            tagInput = it
                            search(it)
                        },
                        suggestions = suggestions,
                        people = personMatches,
                        sharedNames = sharedNames,
                        onAddPerson = { person ->
                            addPerson(person)
                            tagInput = ""
                            search("")
                        },
                        onAddTag = { newTag ->
                            if (newTag.isNotBlank()) addLabel(newTag)
                            tagInput = ""
                            search("")
                        },
                        onInsertPrefix = ::onInsertPrefix,
                        focusRequester = focusRequester
                    )
                }
            }
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Spacer(modifier = Modifier.height(8.dp))
                TagCategory.entries.forEach { category ->
                    val spec = TagUtils.getCategoryUiSpec(category)
                    if (category == TagCategory.People) {
                        val recent = remember(knownPeople, localPeople) {
                            knownPeople.filter { k -> localPeople.none { it.isSamePerson(k) } }.take(8)
                        }
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
                                focusManager.clearFocus()
                                showPeoplePicker = true
                            },
                            body = if (localPeople.isEmpty() && recent.isEmpty()) null else {
                                {
                                    PeopleSectionBody(
                                        people = localPeople,
                                        recent = recent,
                                        sharedNames = sharedNames,
                                        emptyMessage = stringResource(spec.emptyMessageRes),
                                        chipColor = spec.containerColor,
                                        onPersonClick = {
                                            focusManager.clearFocus()
                                            editingPersonId = it.id
                                        },
                                        onRemove = { person ->
                                            localPeople = localPeople.filterNot { it.id == person.id }
                                        },
                                        onAddRecent = ::addPerson
                                    )
                                }
                            }
                        )
                        return@forEach
                    }
                    val categoryTags = TagUtils.filterTagsByCategory(localTags, category)
                    TagSectionCard(
                        title = category.label,
                        prefix = category.prefix,
                        iconRes = spec.iconRes,
                        tags = categoryTags,
                        onRemove = { tagToRemove ->
                            localTags = localTags - tagToRemove
                        },
                        emptyMessage = stringResource(spec.emptyMessageRes),
                        tintColor = spec.color,
                        containerColor = spec.containerColor,
                        onAddClick = {
                            val prefix = category.prefix ?: ""
                            tagInput = prefix
                            search(prefix)
                            focusManager.clearFocus()
                            focusRequester.requestFocus()
                        }
                    )
                }
                Spacer(modifier = Modifier.height(120.dp))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun TagSectionCard(
    title: String,
    prefix: String?,
    iconRes: Int,
    tags: List<String>,
    onRemove: (String) -> Unit,
    emptyMessage: String,
    tintColor: Color,
    containerColor: Color,
    onAddClick: () -> Unit,
    /** Replaces the chip area, for sections whose items are more than a word (people). */
    body: (@Composable () -> Unit)? = null
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = MaterialShapes.Cookie4Sided.toShape(),
                    color = containerColor,
                    modifier = Modifier.size(44.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            painter = painterResource(iconRes),
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = tintColor
                        )
                    }
                }
                Spacer(Modifier.width(12.dp))
                if (prefix != null) {
                    Text(
                        text = prefix,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        color = tintColor
                    )
                    Spacer(Modifier.width(8.dp))
                }

                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.weight(1f))
                val plusColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                val backgroundColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.12f)
                FilledIconButton(
                    onClick = onAddClick,
                    modifier = Modifier.size(36.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = backgroundColor,
                        contentColor = plusColor
                    )
                ) {
                    Icon(
                        painter = painterResource(R.drawable.add_2_24px),
                        contentDescription = "Add $title tag",
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(Modifier.width(4.dp))
            }
            Spacer(modifier = Modifier.height(16.dp))
            if (body != null) {
                body()
            } else if (tags.isEmpty()) {
                Text(
                    text = emptyMessage,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
                )
            } else {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    tags.forEach { tag ->
                        InputChip(
                            selected = true,
                            onClick = { onRemove(tag) },
                            label = { Text(tag) },
                            trailingIcon = {
                                Icon(
                                    painter = painterResource(R.drawable.close_24px),
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            shape = RoundedCornerShape(12.dp),
                            colors = TagUtils.getTagInputChipColors(tag),
                            border = null
                        )
                    }
                }
            }
        }
    }
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
                        showContext = person.name.trim().lowercase() in sharedNames,
                        onClick = { onAddRecent(person) },
                        modifier = Modifier.animateItem()
                    )
                }
            }
        }
    }
}

@Composable
fun TagInputArea(
    tagInput: String,
    onInputChange: (String) -> Unit,
    suggestions: List<String>,
    onAddTag: (String) -> Unit,
    onInsertPrefix: (String) -> Unit,
    focusRequester: FocusRequester,
    /** People matching an `@name` being typed. */
    people: List<JournalPerson> = emptyList(),
    sharedNames: Set<String> = emptySet(),
    onAddPerson: (JournalPerson) -> Unit = {}
) {
    var textFieldValue by remember {
        mutableStateOf(TextFieldValue(text = tagInput, selection = TextRange(tagInput.length)))
    }

    SideEffect {
        if (tagInput != textFieldValue.text) {
            textFieldValue = TextFieldValue(
                text = tagInput,
                selection = TextRange(tagInput.length)
            )
        }
    }

    val isPersonInput = tagInput.startsWith("@")
    val charLimit = if (isPersonInput) TagUtils.PERSON_CHARACTER_LIMIT else TagUtils.TAG_CHARACTER_LIMIT

    val isInputValid = remember(tagInput) {
        val trimmed = tagInput.trim()
        trimmed.isNotBlank() && trimmed != "@" && trimmed != "#" && (!trimmed.startsWith("#") || !trimmed.contains(" "))
    }

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        tonalElevation = 6.dp,
        shadowElevation = 4.dp
    ) {
        Column(
            modifier = Modifier
                .navigationBarsPadding()
                .imePadding()
        ) {
            val showSuggestions = suggestions.isNotEmpty() && tagInput.isNotBlank() && !isPersonInput

            if (isPersonInput && people.isNotEmpty()) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(people, key = { it.id }) { person ->
                        PersonSuggestionChip(
                            person = person,
                            showContext = person.name.trim().lowercase() in sharedNames,
                            onClick = { onAddPerson(person) }
                        )
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
            } else if (showSuggestions) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(suggestions) { suggestion ->
                        SuggestionChip(
                            onClick = { onAddTag(suggestion) },
                            label = { Text(suggestion) },
                            colors = SuggestionChipDefaults.suggestionChipColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                            ),
                            shape = CircleShape
                        )
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
            } else if (tagInput.isBlank()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TagCategory.entries.filter { it.prefix != null }.forEach { category ->
                        val spec = TagUtils.getCategoryUiSpec(category)
                        SuggestionChip(
                            onClick = {
                                onInsertPrefix(category.prefix!!)
                                focusRequester.requestFocus()
                            },
                            label = { Text(category.singularLabel) },
                            icon = {
                                Text(
                                    category.prefix!!,
                                    fontWeight = FontWeight.Bold,
                                    color = spec.color
                                )
                            },
                            colors = SuggestionChipDefaults.suggestionChipColors(
                                containerColor = spec.containerColor,
                                labelColor = spec.color
                            ),
                            border = null,
                            shape = CircleShape
                        )
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                TextField(
                    value = textFieldValue,
                    onValueChange = { newValue ->
                        val text = newValue.text
                        val isPerson = text.startsWith("@")
                        val isTopic = text.startsWith("#")
                        val limit = if (isPerson) TagUtils.PERSON_CHARACTER_LIMIT else TagUtils.TAG_CHARACTER_LIMIT

                        // People are names, so they may have spaces, dots and apostrophes.
                        val isValidChars = when {
                            isPerson -> text.drop(1).all { it.isLetterOrDigit() || it in "_-.' " }
                            isTopic -> text.drop(1).all { it.isLetterOrDigit() || it == '_' || it == '-' }
                            else -> text.all { it.isLetterOrDigit() || it == '_' || it == '-' || it == ' ' }
                        }

                        if (isValidChars && text.length <= limit) {
                            textFieldValue = newValue
                            onInputChange(newValue.text)
                        }
                    },
                    placeholder = {
                        Text(
                            if (isPersonInput) stringResource(R.string.people_search_hint) else "Add tag...",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    },
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(focusRequester),
                    shape = RoundedCornerShape(12.dp),
                    colors = UiUtils.getTransparentTextFieldColors().copy(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    ),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        imeAction = ImeAction.Done,
                        capitalization = if (isPersonInput) KeyboardCapitalization.Words else KeyboardCapitalization.None
                    ),
                    keyboardActions = KeyboardActions(onDone = {
                        if (isInputValid) onAddTag(tagInput)
                    }),
                    trailingIcon = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(end = 8.dp)
                        ) {
                            if (tagInput.isNotEmpty()) {
                                Text(
                                    text = "${tagInput.length}/$charLimit",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (tagInput.length == charLimit) MaterialTheme.colorScheme.error
                                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                    modifier = Modifier.padding(end = 8.dp)
                                )
                            }

                            FilledIconButton(
                                onClick = { onAddTag(tagInput) },
                                enabled = isInputValid,
                                modifier = Modifier.size(40.dp),
                                colors = IconButtonDefaults.filledIconButtonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary,
                                    disabledContainerColor = MaterialTheme.colorScheme.onSurface.copy(
                                        alpha = 0.12f
                                    ),
                                    disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(
                                        alpha = 0.38f
                                    )
                                )
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.arrow_upward_24px),
                                    contentDescription = "Add Tag",
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                )
            }
        }
    }
}
