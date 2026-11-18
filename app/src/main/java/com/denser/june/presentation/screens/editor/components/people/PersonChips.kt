package com.denser.june.presentation.screens.editor.components.people

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.denser.june.core.R
import com.denser.june.core.domain.model.JournalPerson

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
