package com.denser.june.presentation.screens.editor.components.people

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.denser.june.core.R
import com.denser.june.core.domain.model.JournalPerson
import com.denser.june.core.domain.model.nameAmong
import com.denser.june.core.domain.model.sharedNames
import com.denser.june.core.utils.TagUtils

/**
 * The people of the journal by their label in the index (`@<id>`): who a label stands for, and
 * what it is called on screen.
 */
class PeopleIndex(known: List<JournalPerson>) {
    private val byKey = known.associateBy { it.key }
    private val sharedNames = known.sharedNames()

    fun personOf(label: String?): JournalPerson? = label?.let(byKey::get)

    /** A person by name, with their main label when the name is shared; any other label as it is written. */
    fun text(label: String): String {
        val person = byKey[label] ?: return TagUtils.getCleanTagName(label)
        return person.nameAmong(sharedNames)
    }
}

/** Who the selected person is: avatar, context, how many entries, and a way to their contact. */
@Composable
fun PersonHeader(
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
