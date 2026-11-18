package com.denser.june.presentation.screens.editor.components.people

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.denser.june.core.domain.model.JournalPerson
import com.denser.june.core.domain.model.PersonIcon
import com.denser.june.core.domain.repository.JournalRepository
import org.koin.compose.koinInject

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

/**
 * Everyone in the journal for [LocalPeople], and the person colours of the theme in force here,
 * for everything inside: a person looks the same wherever they are shown.
 */
@Composable
fun ProvideJournalPeople(content: @Composable () -> Unit) {
    val journalRepository = koinInject<JournalRepository>()
    val knownPeople by remember(journalRepository) { journalRepository.getKnownPeople() }
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val people = remember(knownPeople) { knownPeople.associateBy { it.id } }
    CompositionLocalProvider(LocalPeople provides people) { ProvidePersonColors(content) }
}

/** The shapes a person's icon can have. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun personShapes() = remember {
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

internal data class AvatarStyle(val container: Color, val content: Color, val shape: Shape)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun avatarStyleFor(person: JournalPerson): AvatarStyle {
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
