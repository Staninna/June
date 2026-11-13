package com.denser.june.presentation.screens.home.tags

import androidx.compose.animation.*
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.denser.june.core.R
import com.denser.june.core.domain.model.enums.TagCategory
import com.denser.june.core.domain.model.Journal
import com.denser.june.core.domain.model.JournalPerson
import com.denser.june.core.domain.model.sharedNames
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.text.style.TextOverflow
import com.denser.june.presentation.screens.editor.components.people.PersonAvatar
import com.denser.june.presentation.screens.editor.components.people.PersonDetailSheet
import com.denser.june.presentation.screens.editor.components.people.openContact
import com.denser.june.presentation.components.JuneDropdownMenu
import com.denser.june.presentation.components.JuneDropdownMenuItem
import com.denser.june.core.domain.model.enums.TimeFormat
import com.denser.june.core.utils.toLocalDate
import com.denser.june.presentation.components.journal.DayJournalGroupData
import com.denser.june.presentation.components.journal.ExportJournalSheet
import com.denser.june.presentation.components.JunePlaceholderPage
import com.denser.june.presentation.navigation.AppNavigator
import com.denser.june.presentation.navigation.Route
import com.denser.june.presentation.screens.home.components.DayJournalGroup
import com.denser.june.presentation.components.journal.JournalCard
import com.denser.june.presentation.components.journal.JournalOptionsSheet
import com.denser.june.presentation.screens.home.tags.components.DeleteTagDialog
import com.denser.june.presentation.screens.home.tags.components.FilterBottomSheet
import com.denser.june.presentation.screens.home.tags.components.FilterFab
import com.denser.june.presentation.screens.home.tags.components.RenameTagDialog
import com.denser.june.presentation.utils.TagUtils
import com.denser.june.presentation.utils.UiUtils
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun TagsPage() {
    val viewModel: TagsVM = koinViewModel()
    val timeFormat by viewModel.timeFormat.collectAsStateWithLifecycle()
    val is24Hour = timeFormat == TimeFormat.TWENTY_FOUR_HOUR

    val allTags by viewModel.allUniqueTags.collectAsStateWithLifecycle()
    val selectedCategory by viewModel.selectedCategory.collectAsStateWithLifecycle()

    val primaryTags by viewModel.primaryTags.collectAsStateWithLifecycle()
    val journals by viewModel.journals.collectAsStateWithLifecycle()

    val selectedPrimaryTag by viewModel.selectedPrimaryTag.collectAsStateWithLifecycle()
    val tagCounts by viewModel.tagCounts.collectAsStateWithLifecycle()
    val availableFilters by viewModel.availableFilters.collectAsStateWithLifecycle()
    val selectedFilters by viewModel.selectedFilters.collectAsStateWithLifecycle()
    val knownPeople by viewModel.knownPeople.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // A person label (`@<id>`) stands for a person: look up who.
    val peopleByKey = remember(knownPeople) { knownPeople.associateBy { it.key } }
    val sharedNames = remember(knownPeople) { knownPeople.sharedNames() }
    /** What a label is called on screen: a person by name, with context when the name is shared. */
    fun labelText(label: String): String {
        val person = peopleByKey[label] ?: return TagUtils.getCleanTagName(label)
        return if (person.name.trim().lowercase() in sharedNames) person.displayName else person.name.trim()
    }
    val isPeopleTab = selectedCategory == TagCategory.People
    val selectedPerson = selectedPrimaryTag?.takeIf { isPeopleTab }?.let { peopleByKey[it] }
    var showPersonSheet by remember { mutableStateOf(false) }

    val navigator = koinInject<AppNavigator>()

    val dayGroups = remember(journals) {
        journals?.groupBy { it.dateTime.toLocalDate() }
            ?.map { (date, journalsOnDay) -> DayJournalGroupData(date, journalsOnDay) }
    }

    var showRenameDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var showFilterSheet by remember { mutableStateOf(false) }

    var selectedJournalForOptions by remember { mutableStateOf<Journal?>(null) }
    val scope = rememberCoroutineScope()

    val filterSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val hasAvailableFilters = availableFilters.isNotEmpty() || selectedFilters.isNotEmpty()
    val activeFilterCount = selectedFilters.size
    val currentCategorySpec = TagUtils.getCategoryUiSpec(selectedCategory)

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            PrimaryTabRow(
                selectedTabIndex = selectedCategory.ordinal,
                containerColor = MaterialTheme.colorScheme.surface,
                divider = {}
            ) {
                TagCategory.entries.forEach { category ->
                    val spec = TagUtils.getCategoryUiSpec(category)
                    val isSelected = selectedCategory == category

                    Tab(
                        modifier = Modifier.clip(RoundedCornerShape(20.dp)),
                        selected = isSelected,
                        onClick = { viewModel.selectCategory(category) },
                        icon = {
                            Icon(
                                painter = painterResource(if (isSelected) spec.filledIconRes else spec.iconRes),
                                contentDescription = category.label,
                                modifier = Modifier.size(22.dp)
                            )
                        },
                        text = {
                            Text(
                                text = category.label,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        selectedContentColor = MaterialTheme.colorScheme.primary,
                        unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (primaryTags == null) {
                JunePlaceholderPage(
                    modifier = Modifier.weight(1f),
                    isLoading = true
                )
            } else if (primaryTags!!.isNotEmpty()) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(primaryTags!!, key = { it }) { fullTag ->
                        val isSelected = selectedPrimaryTag == fullTag
                        val count = tagCounts[fullTag] ?: 0

                        FilterChip(
                            selected = isSelected,
                            onClick = { viewModel.selectPrimaryTag(fullTag) },
                            modifier = Modifier.height(40.dp),
                            shape = RoundedCornerShape(16.dp),
                            leadingIcon = peopleByKey[fullTag]?.let { person ->
                                { PersonAvatar(person, size = 26.dp) }
                            },
                            label = {
                                Text(
                                    text = if (isPeopleTab) labelText(fullTag) else fullTag,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal
                                )
                            },
                            trailingIcon = {
                                if (isSelected) {
                                    Box {
                                        IconButton(
                                            onClick = { showMenu = true },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(
                                                painter = painterResource(R.drawable.more_vert_24px),
                                                contentDescription = "Options",
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }

                                        JuneDropdownMenu(
                                            expanded = showMenu,
                                            onDismissRequest = { showMenu = false },
                                        ) {
                                            JuneDropdownMenuItem(
                                                text = {
                                                    Text(
                                                        text = stringResource(
                                                            if (isPeopleTab) R.string.people_edit else R.string.rename
                                                        ),
                                                        style = MaterialTheme.typography.bodyLarge
                                                    )
                                                },
                                                leadingIcon = {
                                                    Icon(
                                                        painter = painterResource(R.drawable.edit_24px),
                                                        contentDescription = null,
                                                        modifier = Modifier.size(20.dp)
                                                    )
                                                },
                                                onClick = {
                                                    showMenu = false
                                                    if (isPeopleTab) showPersonSheet = true else showRenameDialog = true
                                                }
                                            )
                                            JuneDropdownMenuItem(
                                                text = {
                                                    Text(
                                                        text = stringResource(R.string.action_delete),
                                                        style = MaterialTheme.typography.bodyLarge,
                                                        color = MaterialTheme.colorScheme.error
                                                    )
                                                },
                                                leadingIcon = {
                                                    Icon(
                                                        painter = painterResource(R.drawable.delete_24px),
                                                        contentDescription = null,
                                                        modifier = Modifier.size(20.dp),
                                                        tint = MaterialTheme.colorScheme.error
                                                    )
                                                },
                                                onClick = {
                                                    showMenu = false
                                                    showDeleteDialog = true
                                                }
                                            )
                                        }
                                    }
                                } else if (count > 0) {
                                    Surface(
                                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                                        shape = CircleShape,
                                        modifier = Modifier
                                            .height(24.dp)
                                            .defaultMinSize(minWidth = 24.dp)
                                    ) {
                                        Box(
                                            contentAlignment = Alignment.Center,
                                            modifier = Modifier.padding(horizontal = 8.dp)
                                        ) {
                                            Text(
                                                text = count.toString(),
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = currentCategorySpec.containerColor,
                                selectedLabelColor = currentCategorySpec.color,
                                selectedTrailingIconColor = currentCategorySpec.color,
                                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                                labelColor = MaterialTheme.colorScheme.onSurface
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = isSelected,
                                borderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                                borderWidth = 1.dp
                            )
                        )
                    }
                }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    contentPadding = PaddingValues(top = 8.dp, bottom = 100.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    selectedPerson?.let { person ->
                        item(key = "person_header") {
                            PersonHeader(
                                person = person,
                                entryCount = tagCounts[selectedPrimaryTag] ?: 0,
                                onEdit = { showPersonSheet = true },
                                onOpenContact = { lookupKey ->
                                    if (!openContact(context, lookupKey)) {
                                        Toast.makeText(context, R.string.people_contact_unavailable, Toast.LENGTH_SHORT).show()
                                    }
                                },
                                modifier = Modifier
                                    .padding(horizontal = 16.dp)
                                    .animateItem()
                            )
                        }
                    }
                    if (journals == null) {
                        item {
                            JunePlaceholderPage(
                                modifier = Modifier.fillParentMaxHeight(0.8f),
                                isLoading = true
                            )
                        }
                    } else if (journals!!.isEmpty()) {
                        item {
                            JunePlaceholderPage(
                                modifier = Modifier.fillParentMaxHeight(0.8f),
                                icon = R.drawable.auto_stories_off_24px,
                                title = stringResource(R.string.no_entries),
                                subtitle = stringResource(R.string.no_entries_filter_desc)
                            )
                        }
                    } else {
                        dayGroups?.forEach { dayGroup ->
                            if (dayGroup.journals.size > 1) {
                                item(key = "day_group_${dayGroup.date}") {
                                    DayJournalGroup(
                                        date = dayGroup.date,
                                        journals = dayGroup.journals,
                                        is24Hour = is24Hour,
                                        onToggleBookmark = { id -> viewModel.toggleBookmark(id) },
                                        onJournalClick = { journal ->
                                            navigator.navigateTo(Route.Editor(journal.id), isSingleTop = true)
                                        },
                                        onLongClick = { journal -> selectedJournalForOptions = journal },
                                        modifier = Modifier
                                            .padding(horizontal = 16.dp)
                                            .animateItem()
                                    )
                                }
                            } else {
                                val singleJournal = dayGroup.journals.first()
                                item(key = "single_journal_${singleJournal.id}") {
                                    JournalCard(
                                        journal = singleJournal,
                                        is24Hour = is24Hour,
                                        showDate = true,
                                        onToggleBookmark = { viewModel.toggleBookmark(singleJournal.id) },
                                        onJournalClick = {
                                            navigator.navigateTo(Route.Editor(singleJournal.id), isSingleTop = true)
                                        },
                                        onLongClick = { selectedJournalForOptions = singleJournal },
                                        modifier = Modifier
                                            .padding(horizontal = 16.dp)
                                            .animateItem()
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                JunePlaceholderPage(
                    modifier = Modifier.weight(1f),
                    icon = currentCategorySpec.iconRes,
                    title = stringResource(R.string.no_tag_category_yet, selectedCategory.label.lowercase()),
                    subtitle = stringResource(R.string.tag_category_empty_desc, selectedCategory.singularLabel.lowercase())
                )
            }
        }

        AnimatedVisibility(
            visible = hasAvailableFilters,
            enter = scaleIn(spring()) + fadeIn(),
            exit = scaleOut(spring()) + fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = UiUtils.BOTTOM_BAR_PADDING + 24.dp)
        ) {
            FilterFab(
                activeCount = activeFilterCount,
                onClick = { showFilterSheet = true }
            )
        }

        if (showFilterSheet) {
            FilterBottomSheet(
                sheetState = filterSheetState,
                availableFilters = availableFilters,
                labelText = ::labelText,
                selectedFilters = selectedFilters,
                onToggle = { viewModel.toggleFilter(it) },
                onClearAll = { viewModel.clearFilters() },
                onDismiss = { showFilterSheet = false }
            )
        }

        val currentJournalForOptions = remember(selectedJournalForOptions, journals) {
            val id = selectedJournalForOptions?.id ?: return@remember null
            (journals ?: emptyList()).find { it.id == id }
        }

        var journalToExport by remember { mutableStateOf<Journal?>(null) }

        ExportJournalSheet(
            journal = journalToExport,
            onDismiss = { journalToExport = null }
        )

        JournalOptionsSheet(
            journal = currentJournalForOptions,
            is24Hour = is24Hour,
            onDismiss = { selectedJournalForOptions = null },
            onToggleBookmark = {
                currentJournalForOptions?.id?.let { viewModel.toggleBookmark(it) }
            },
            onToggleLock = {
                currentJournalForOptions?.id?.let { viewModel.toggleLock(it) }
            },
            onExportMarkdown = {
                journalToExport = currentJournalForOptions
                selectedJournalForOptions = null
            },
            onDeleteOrRestore = {
                currentJournalForOptions?.let {
                    if (it.isDeleted) {
                        viewModel.restoreJournal(it.id)
                    } else {
                        viewModel.softDelete(it.id)
                    }
                }
            }
        )

        if (showPersonSheet) {
            val person = selectedPerson
            if (person == null) {
                showPersonSheet = false
            } else {
                PersonDetailSheet(
                    person = person,
                    onSave = viewModel::updatePerson,
                    onRemove = { showDeleteDialog = true },
                    onDismiss = { showPersonSheet = false },
                    removeLabel = stringResource(R.string.people_remove_everywhere),
                    everyone = knownPeople
                )
            }
        }

        if (selectedPrimaryTag != null && allTags != null) {
            if (showRenameDialog) {
                RenameTagDialog(
                    currentTagName = selectedPrimaryTag!!,
                    category = selectedCategory,
                    existingTags = allTags!!,
                    onDismiss = { showRenameDialog = false },
                    onRename = { newName ->
                        viewModel.renameCurrentTag(newName)
                        showRenameDialog = false
                    }
                )
            }

            if (showDeleteDialog) {
                DeleteTagDialog(
                    tagName = selectedPerson?.displayName ?: selectedPrimaryTag!!,
                    isPerson = selectedPerson != null,
                    onDismiss = { showDeleteDialog = false },
                    onConfirm = {
                        viewModel.deleteCurrentTag()
                        showDeleteDialog = false
                    }
                )
            }
        }
    }
}

/** Who the selected person is: avatar, context, how many entries, and a way to their contact. */
@Composable
private fun PersonHeader(
    person: JournalPerson,
    entryCount: Int,
    onEdit: () -> Unit,
    onOpenContact: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onEdit,
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 14.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PersonAvatar(person, size = 52.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = person.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val entries = pluralStringResource(R.plurals.people_entry_count, entryCount, entryCount)
                Text(
                    text = person.note?.takeIf { it.isNotBlank() }?.let { "$it • $entries" } ?: entries,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            person.contactLookupKey?.let { lookupKey ->
                FilledTonalIconButton(onClick = { onOpenContact(lookupKey) }) {
                    Icon(
                        painter = painterResource(R.drawable.person_24px_fill),
                        contentDescription = stringResource(R.string.people_open_contact),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            IconButton(onClick = onEdit) {
                Icon(
                    painter = painterResource(R.drawable.edit_24px),
                    contentDescription = stringResource(R.string.people_edit),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
