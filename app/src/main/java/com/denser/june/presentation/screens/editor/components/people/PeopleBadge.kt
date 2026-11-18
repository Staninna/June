package com.denser.june.presentation.screens.editor.components.people

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.denser.june.core.domain.model.JournalPerson

/**
 * Who is on the entry, as their own icons: the shape and colour they have everywhere else, so
 * an entry with someone on it is recognised at a glance. One person shows with their first name.
 */
@Composable
fun PeopleBadge(people: List<JournalPerson>, modifier: Modifier = Modifier) {
    if (people.isEmpty()) return
    // as the journal knows them now, not as copied onto this entry
    val known = people.map { it.asKnown() }
    val shown = known.take(MAX_PEOPLE_ICONS)
    val names = known.joinToString(", ") { it.name }
    Surface(
        modifier = modifier.semantics(mergeDescendants = true) { contentDescription = names },
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        shape = CircleShape
    ) {
        Row(
            modifier = Modifier.padding(start = 3.dp, end = 6.dp, top = 1.5.dp, bottom = 1.5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            shown.forEach { person -> PersonAvatar(person, size = 14.dp) }
            val label = when {
                known.size == 1 -> known.first().name.trim().substringBefore(' ').take(12)
                known.size > shown.size -> "+${known.size - shown.size}"
                else -> null
            }
            if (label != null) {
                Spacer(Modifier.width(2.dp))
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clearAndSetSemantics {}
                )
            }
        }
    }
}

private const val MAX_PEOPLE_ICONS = 3
