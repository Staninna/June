package com.denser.june.presentation.screens.editor.components.people

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.denser.hyphen.model.TriggerState
import com.denser.hyphen.state.HyphenTextState
import com.denser.june.core.R
import com.denser.june.core.domain.model.JournalPerson
import com.denser.june.core.domain.model.nameAmong
import com.denser.june.core.domain.model.sharedNames
import com.denser.june.presentation.screens.editor.components.people.PersonAvatar
import com.denser.june.presentation.utils.TagUiUtils

/**
 * Who to mention after typing `@`. People who share a name show their context ("Bart · Work"),
 * and the mention remembers which person was picked, not just the name.
 */
@Composable
fun PersonMentionChips(
    trigger: TriggerState,
    people: List<JournalPerson>,
    hyphenState: HyphenTextState,
    onPersonSelect: (JournalPerson) -> Unit,
) {
    val query = trigger.query.trim()
    val sharedNames = remember(people) { people.sharedNames() }
    val matches = remember(people, query) {
        people.filter { query.isBlank() || it.name.contains(query, ignoreCase = true) }
            // names that start with what was typed come first
            .sortedBy { !it.name.trim().startsWith(query, ignoreCase = true) }
            .take(20)
    }
    val showCreate = query.isNotBlank() && people.none { it.hasName(query) }

    fun mention(person: JournalPerson) {
        onPersonSelect(person)
        hyphenState.completeMention(id = person.id, display = person.name.trim(), trigger = trigger)
    }

    Row(
        modifier = Modifier
            .fillMaxHeight()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        matches.forEach { person ->
            FilterChip(
                selected = false,
                onClick = { mention(person) },
                leadingIcon = { PersonAvatar(person, size = 22.dp) },
                label = {
                    Text(
                        person.nameAmong(sharedNames),
                        fontSize = 13.sp,
                    )
                },
                shape = RoundedCornerShape(8.dp),
            )
        }

        if (matches.isEmpty() && !showCreate) {
            Text(
                text = stringResource(R.string.people_search_hint),
                style = TextStyle(
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                ),
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }

        if (showCreate) {
            SuggestionChip(
                onClick = { mention(JournalPerson(name = query)) },
                label = { Text("@$query", fontSize = 13.sp, fontWeight = FontWeight.SemiBold) },
                colors = TagUiUtils.getTagSuggestionChipColors("@$query"),
                border = null,
                shape = RoundedCornerShape(8.dp),
            )
        }
    }
}
