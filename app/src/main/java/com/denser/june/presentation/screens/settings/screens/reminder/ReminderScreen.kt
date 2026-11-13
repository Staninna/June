package com.denser.june.presentation.screens.settings.screens.reminder

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.denser.june.core.R
import com.denser.june.core.domain.model.enums.TimeFormat
import com.denser.june.core.domain.reminder.ReminderSlot
import com.denser.june.core.utils.combineDateAndTime
import com.denser.june.core.utils.toLocalTime
import com.denser.june.notification.NotificationsHelper
import com.denser.june.presentation.components.JuneAppBarType
import com.denser.june.presentation.components.JuneBackButton
import com.denser.june.presentation.components.JuneConnectedItem
import com.denser.june.presentation.components.JuneConnectedList
import com.denser.june.presentation.components.JuneDateTimePicker
import com.denser.june.presentation.components.JuneDateTimePickerMode
import com.denser.june.presentation.components.JuneDialog
import com.denser.june.presentation.components.JuneTextField
import com.denser.june.presentation.components.JuneTopAppBar
import com.denser.june.presentation.navigation.AppNavigator
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.WeekFields

private sealed interface TimePickerTarget {
    data class New(val label: String? = null) : TimePickerTarget
    data class Existing(val slot: ReminderSlot) : TimePickerTarget
}

private data class MomentPreset(val time: LocalTime, val labelRes: Int, val iconRes: Int)

private val presets = listOf(
    MomentPreset(LocalTime.of(8, 0), R.string.reminder_preset_morning, R.drawable.light_mode_24px),
    MomentPreset(LocalTime.of(13, 0), R.string.reminder_preset_midday, R.drawable.routine_24px),
    MomentPreset(LocalTime.of(21, 30), R.string.reminder_preset_evening, R.drawable.partly_cloudy_night_24px),
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ReminderScreen() {
    val viewModel: ReminderVM = koinViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val navigator = koinInject<AppNavigator>()
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val is24Hour = state.timeFormat == TimeFormat.TWENTY_FOUR_HOUR

    var pickerTarget by remember { mutableStateOf<TimePickerTarget?>(null) }
    var renameTarget by remember { mutableStateOf<ReminderSlot?>(null) }
    var pendingAfterPermission by remember { mutableStateOf<(() -> Unit)?>(null) }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refreshPermissions() }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) pendingAfterPermission?.invoke()
        pendingAfterPermission = null
    }

    fun withNotificationPermission(action: () -> Unit) {
        val needsAsk = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED
        if (needsAsk) {
            pendingAfterPermission = action
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            action()
        }
    }

    val removedMessage = stringResource(R.string.reminder_moment_removed)
    val undoLabel = stringResource(R.string.reminder_undo)
    val testSentMessage = stringResource(R.string.reminder_test_sent)

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            JuneTopAppBar(
                type = JuneAppBarType.Large,
                title = { Text(stringResource(R.string.reminders)) },
                scrollBehavior = scrollBehavior,
                navigationIcon = { JuneBackButton(onClick = { navigator.navigateBack() }) }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = padding.calculateTopPadding()),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                bottom = 32.dp + padding.calculateBottomPadding()
            ),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            item(key = "intro") {
                Text(
                    text = stringResource(R.string.reminder_screen_desc),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 8.dp, end = 8.dp, top = 4.dp, bottom = 20.dp)
                )
            }

            item(key = "hero") {
                ReminderHeroCard(
                    enabled = state.isEnabled,
                    next = state.next,
                    is24Hour = is24Hour,
                    onToggle = { on ->
                        haptics.performHapticFeedback(
                            if (on) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff
                        )
                        if (on) withNotificationPermission { viewModel.toggleReminder(true) }
                        else viewModel.toggleReminder(false)
                    }
                )
            }

            if (state.isEnabled && !state.canScheduleExact) {
                item(key = "exact") {
                    ExactAlarmBanner(
                        onClick = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                context.startActivity(
                                    Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                                        .setData("package:${context.packageName}".toUri())
                                )
                            }
                        },
                        modifier = Modifier
                            .padding(top = 8.dp)
                            .animateItem()
                    )
                }
            }

            if (state.slots.isNotEmpty()) {
                item(key = "moments-header") {
                    SectionHeader(
                        title = stringResource(R.string.reminder_moments),
                        trailing = stringResource(
                            R.string.reminder_moments_count,
                            if (state.isEnabled) state.activeCount else 0,
                            state.slots.size
                        ),
                        modifier = Modifier.animateItem()
                    )
                }
            }

            val nextId = state.next?.first?.id
            itemsIndexed(state.slots, key = { _, slot -> slot.id }) { index, slot ->
                ReminderMomentCard(
                    slot = slot,
                    shape = connectedShape(index, state.slots.size),
                    isNext = slot.id == nextId,
                    masterEnabled = state.isEnabled,
                    is24Hour = is24Hour,
                    onToggle = {
                        haptics.performHapticFeedback(
                            if (it) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff
                        )
                        viewModel.setSlotEnabled(slot.id, it)
                    },
                    onTimeClick = { pickerTarget = TimePickerTarget.Existing(slot) },
                    onLabelClick = { renameTarget = slot },
                    onDayToggle = { day ->
                        haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                        viewModel.toggleDay(slot.id, day)
                    },
                    onRemove = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        viewModel.removeSlot(slot.id)
                        scope.launch {
                            snackbarHostState.currentSnackbarData?.dismiss()
                            val result = snackbarHostState.showSnackbar(
                                message = removedMessage,
                                actionLabel = undoLabel,
                                duration = SnackbarDuration.Long
                            )
                            if (result == SnackbarResult.ActionPerformed) viewModel.restoreSlot(slot)
                        }
                    },
                    modifier = Modifier
                        .padding(bottom = if (index == state.slots.lastIndex) 0.dp else 2.dp)
                        .animateItem()
                )
            }

            item(key = "add-header") {
                SectionHeader(
                    title = stringResource(R.string.reminder_add_moment),
                    trailing = null,
                    modifier = Modifier.animateItem()
                )
            }

            item(key = "add") {
                AddMomentSection(
                    existing = state.slots,
                    canAddMore = state.canAddMore,
                    is24Hour = is24Hour,
                    onPreset = { preset, label ->
                        haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                        withNotificationPermission { viewModel.addSlot(preset.time, label) }
                    },
                    onCustom = { pickerTarget = TimePickerTarget.New() },
                    modifier = Modifier.animateItem()
                )
            }

            if (state.isEnabled && state.slots.isNotEmpty()) {
                item(key = "test") {
                    Column(Modifier.animateItem()) {
                        Spacer(Modifier.height(24.dp))
                        JuneConnectedItem(
                            title = stringResource(R.string.reminder_test),
                            subtitle = stringResource(R.string.reminder_test_desc),
                            leadingContent = {
                                Icon(
                                    painter = painterResource(R.drawable.notifications_24px),
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.secondary
                                )
                            },
                            shape = connectedShape(0, 1),
                            onClick = {
                                withNotificationPermission {
                                    NotificationsHelper(context).showReminderNotification(
                                        state.next?.first ?: state.slots.first()
                                    )
                                    scope.launch {
                                        snackbarHostState.currentSnackbarData?.dismiss()
                                        snackbarHostState.showSnackbar(testSentMessage)
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    pickerTarget?.let { target ->
        val initial = when (target) {
            is TimePickerTarget.Existing -> target.slot.localTime
            is TimePickerTarget.New -> LocalTime.now().plusHours(1).withMinute(0)
        }
        JuneDateTimePicker(
            initialDateTimeMillis = remember(target) { combineDateAndTime(LocalDate.now(), initial) },
            mode = JuneDateTimePickerMode.TIME_ONLY,
            is24Hour = is24Hour,
            onDateTimeSelected = { millis ->
                val time = millis.toLocalTime()
                when (target) {
                    is TimePickerTarget.Existing -> viewModel.updateSlotTime(target.slot.id, time)
                    is TimePickerTarget.New -> withNotificationPermission {
                        viewModel.addSlot(time, target.label)
                    }
                }
                pickerTarget = null
            },
            onDismiss = { pickerTarget = null }
        )
    }

    renameTarget?.let { slot ->
        RenameMomentDialog(
            initial = slot.label.orEmpty(),
            onDismiss = { renameTarget = null },
            onConfirm = {
                viewModel.updateSlotLabel(slot.id, it)
                renameTarget = null
            }
        )
    }
}

// ---------------------------------------------------------------------------------------------
// Hero
// ---------------------------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ReminderHeroCard(
    enabled: Boolean,
    next: Pair<ReminderSlot, LocalDateTime>?,
    is24Hour: Boolean,
    onToggle: (Boolean) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val container by animateColorAsState(
        if (enabled) colors.primaryContainer else colors.surfaceContainerHigh, label = "heroContainer"
    )
    val content by animateColorAsState(
        if (enabled) colors.onPrimaryContainer else colors.onSurface, label = "heroContent"
    )
    val badge by animateColorAsState(
        if (enabled) colors.primary else colors.surfaceVariant, label = "heroBadge"
    )
    val badgeIcon by animateColorAsState(
        if (enabled) colors.onPrimary else colors.onSurfaceVariant, label = "heroBadgeIcon"
    )
    val rotation by animateFloatAsState(if (enabled) 0f else -30f, label = "heroRotation")

    Surface(
        onClick = { onToggle(!enabled) },
        shape = RoundedCornerShape(28.dp),
        color = container,
        contentColor = content,
        modifier = Modifier
            .fillMaxWidth()
            .semantics { stateDescription = if (enabled) "On" else "Off" }
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(MaterialShapes.Cookie9Sided.toShape())
                    .background(badge),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(
                        if (enabled) R.drawable.notifications_24px else R.drawable.notifications_off_24px
                    ),
                    contentDescription = null,
                    tint = badgeIcon,
                    modifier = Modifier
                        .size(28.dp)
                        .graphicsLayer { rotationZ = rotation }
                )
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f).animateContentSize()) {
                Text(
                    text = stringResource(if (enabled) R.string.reminder_hero_on else R.string.reminder_hero_off),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = when {
                        !enabled -> stringResource(R.string.reminder_hero_off_desc)
                        next == null -> stringResource(R.string.reminder_next_none)
                        else -> nextLabel(next.second, is24Hour)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = content.copy(alpha = 0.8f)
                )
            }
            Spacer(Modifier.width(12.dp))
            Switch(checked = enabled, onCheckedChange = onToggle)
        }
    }
}

@Composable
private fun nextLabel(at: LocalDateTime, is24Hour: Boolean): String {
    val time = formatTime(at.toLocalTime(), is24Hour).let { "${it.first}${it.second?.let { s -> " $s" } ?: ""}" }
    val today = LocalDate.now()
    return when (at.toLocalDate()) {
        today -> stringResource(R.string.reminder_next_today, time)
        today.plusDays(1) -> stringResource(R.string.reminder_next_tomorrow, time)
        else -> stringResource(
            R.string.reminder_next_day,
            at.dayOfWeek.getDisplayName(TextStyle.FULL, LocalLocale.current.platformLocale),
            time
        )
    }
}

@Composable
private fun ExactAlarmBanner(onClick: () -> Unit, modifier: Modifier = Modifier) {
    JuneConnectedItem(
        title = stringResource(R.string.reminder_exact_title),
        subtitle = stringResource(R.string.reminder_exact_desc),
        leadingContent = {
            Icon(
                painterResource(R.drawable.more_time_24px),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onTertiaryContainer
            )
        },
        trailingContent = {
            Icon(
                painterResource(R.drawable.chevron_right_24px),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onTertiaryContainer
            )
        },
        shape = connectedShape(0, 1),
        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
        onClick = onClick,
        modifier = modifier
    )
}

/** Matches [com.denser.june.presentation.screens.settings.components.SettingSection]'s header. */
@Composable
private fun SectionHeader(title: String, trailing: String?, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 12.dp, end = 12.dp, top = 24.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
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

/** Same corner rhythm as the app's connected lists: 28dp outside, 8dp between items. */
private fun connectedShape(index: Int, count: Int): RoundedCornerShape = when {
    count <= 1 -> RoundedCornerShape(28.dp)
    index == 0 -> RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp, bottomStart = 8.dp, bottomEnd = 8.dp)
    index == count - 1 -> RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp, bottomStart = 28.dp, bottomEnd = 28.dp)
    else -> RoundedCornerShape(8.dp)
}

// ---------------------------------------------------------------------------------------------
// Moment card
// ---------------------------------------------------------------------------------------------

@Composable
private fun ReminderMomentCard(
    slot: ReminderSlot,
    shape: RoundedCornerShape,
    isNext: Boolean,
    masterEnabled: Boolean,
    is24Hour: Boolean,
    onToggle: (Boolean) -> Unit,
    onTimeClick: () -> Unit,
    onLabelClick: () -> Unit,
    onDayToggle: (DayOfWeek) -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val active = masterEnabled && slot.enabled
    val contentAlpha by animateFloatAsState(if (active) 1f else 0.55f, label = "momentAlpha")
    val (timeText, amPm) = formatTime(slot.localTime, is24Hour)
    val editTimeLabel = stringResource(R.string.reminder_edit_time)

    Surface(
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(start = 20.dp, end = 12.dp, top = 16.dp, bottom = 8.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(
                    Modifier
                        .weight(1f)
                        .alpha(contentAlpha)
                ) {
                    Row(
                        verticalAlignment = Alignment.Bottom,
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .clickable(role = Role.Button, onClickLabel = editTimeLabel, onClick = onTimeClick)
                            .padding(horizontal = 4.dp)
                    ) {
                        Text(
                            text = timeText,
                            style = MaterialTheme.typography.displayMedium,
                            fontWeight = FontWeight.Normal,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (amPm != null) {
                            Text(
                                text = amPm,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = 6.dp, bottom = 8.dp)
                            )
                        }
                    }
                    AnimatedVisibility(
                        visible = isNext && active,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically()
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.tertiaryContainer,
                            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.reminder_up_next),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(role = Role.Button, onClick = onLabelClick)
                            .padding(horizontal = 4.dp, vertical = 4.dp)
                    ) {
                        Icon(
                            painter = painterResource(timeOfDayIcon(slot.localTime)),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = slot.label ?: stringResource(R.string.reminder_untitled),
                            style = MaterialTheme.typography.titleSmall,
                            color = if (slot.label != null) MaterialTheme.colorScheme.onSurface
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        Spacer(Modifier.width(6.dp))
                        Icon(
                            painter = painterResource(R.drawable.edit_24px),
                            contentDescription = stringResource(R.string.reminder_name_moment),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
                Switch(
                    checked = slot.enabled,
                    onCheckedChange = onToggle,
                    enabled = masterEnabled,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            Spacer(Modifier.height(14.dp))
            DayCircles(
                selected = slot.days,
                enabled = masterEnabled,
                onToggle = onDayToggle,
                modifier = Modifier
                    .padding(end = 8.dp)
                    .alpha(contentAlpha)
            )
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (slot.days.isEmpty()) stringResource(R.string.reminder_no_days)
                    else repeatSummary(slot.days),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (slot.days.isEmpty()) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 4.dp)
                )
                IconButton(onClick = onRemove) {
                    Icon(
                        painter = painterResource(R.drawable.delete_24px),
                        contentDescription = stringResource(R.string.reminder_remove_moment),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun DayCircles(
    selected: Set<DayOfWeek>,
    enabled: Boolean,
    onToggle: (DayOfWeek) -> Unit,
    modifier: Modifier = Modifier,
) {
    val locale = LocalLocale.current.platformLocale
    val firstDay = remember(locale) { WeekFields.of(locale).firstDayOfWeek }
    val ordered = remember(firstDay) { (0L until 7L).map { firstDay.plus(it) } }

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        ordered.forEach { day ->
            val isOn = day in selected
            val bg by animateColorAsState(
                if (isOn) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest,
                label = "dayBg"
            )
            val fg by animateColorAsState(
                if (isOn) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                label = "dayFg"
            )
            val fullName = day.getDisplayName(TextStyle.FULL, locale)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .widthIn(max = 48.dp)
                    .aspectRatio(1f)
                    .clip(CircleShape)
                    .background(bg)
                    .clickable(enabled = enabled, role = Role.Checkbox) { onToggle(day) }
                    .semantics {
                        contentDescription = fullName
                        stateDescription = if (isOn) "Selected" else "Not selected"
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = day.getDisplayName(TextStyle.NARROW, locale),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (isOn) FontWeight.Bold else FontWeight.Medium,
                    color = fg
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Add card
// ---------------------------------------------------------------------------------------------

@Composable
private fun AddMomentSection(
    existing: List<ReminderSlot>,
    canAddMore: Boolean,
    is24Hour: Boolean,
    onPreset: (MomentPreset, String) -> Unit,
    onCustom: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val existingTimes = existing.map { it.localTime }.toSet()
    val available = presets.filter { it.time !in existingTimes }
    val count = if (canAddMore) available.size + 1 else 1

    JuneConnectedList(modifier = modifier.fillMaxWidth().animateContentSize()) {
        if (!canAddMore) {
            JuneConnectedItem(
                title = stringResource(R.string.reminder_limit_reached, ReminderSlot.MAX_SLOTS),
                leadingContent = {
                    Icon(
                        painterResource(R.drawable.info_24px),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary
                    )
                },
                shape = connectedShape(0, 1)
            )
            return@JuneConnectedList
        }
        available.forEachIndexed { index, preset ->
            val label = stringResource(preset.labelRes)
            val (t, amPm) = formatTime(preset.time, is24Hour)
            JuneConnectedItem(
                title = label,
                subtitle = "$t${amPm?.let { " $it" } ?: ""} · ${stringResource(R.string.repeat_daily)}",
                leadingContent = {
                    Icon(
                        painterResource(preset.iconRes),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary
                    )
                },
                trailingContent = {
                    Icon(
                        painterResource(R.drawable.add_circle_24px),
                        contentDescription = stringResource(R.string.reminder_add_moment),
                        tint = MaterialTheme.colorScheme.primary
                    )
                },
                index = index,
                count = count,
                onClick = { onPreset(preset, label) }
            )
        }
        JuneConnectedItem(
            title = stringResource(R.string.reminder_custom_time),
            subtitle = stringResource(R.string.reminder_add_moment_desc),
            leadingContent = {
                Icon(
                    painterResource(R.drawable.more_time_24px),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary
                )
            },
            trailingContent = {
                Icon(
                    painterResource(R.drawable.chevron_right_24px),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            index = count - 1,
            count = count,
            onClick = onCustom
        )
    }
}

@Composable
private fun RenameMomentDialog(
    initial: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var text by remember { mutableStateOf(initial) }
    val focusRequester = remember { FocusRequester() }
    JuneDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.reminder_name_moment),
        icon = R.drawable.edit_24px,
        text = {
            Column {
                JuneTextField(
                    value = text,
                    onValueChange = { if (it.length <= 40) text = it },
                    label = "",
                    placeholder = stringResource(R.string.reminder_name_placeholder),
                    modifier = Modifier.focusRequester(focusRequester)
                )
                LaunchedEffect(Unit) { focusRequester.requestFocus() }
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.reminder_name_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text) }) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        }
    )
}

// ---------------------------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------------------------

@Composable
private fun formatTime(time: LocalTime, is24Hour: Boolean): Pair<String, String?> {
    val locale = LocalLocale.current.platformLocale
    return remember(time, is24Hour, locale) {
        if (is24Hour) {
            time.format(DateTimeFormatter.ofPattern("HH:mm", locale)) to null
        } else {
            time.format(DateTimeFormatter.ofPattern("h:mm", locale)) to
                    time.format(DateTimeFormatter.ofPattern("a", locale))
        }
    }
}

private fun timeOfDayIcon(time: LocalTime): Int = when (time.hour) {
    in 4..10 -> R.drawable.light_mode_24px
    in 11..16 -> R.drawable.routine_24px
    else -> R.drawable.partly_cloudy_night_24px
}

@Composable
private fun repeatSummary(days: Set<DayOfWeek>): String {
    val weekend = setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)
    return when {
        days.size == 7 -> stringResource(R.string.repeat_daily)
        days == DayOfWeek.entries.toSet() - weekend -> stringResource(R.string.repeat_weekdays)
        days == weekend -> stringResource(R.string.repeat_weekends)
        else -> {
            val locale = LocalLocale.current.platformLocale
            days.sortedBy { it.value }.joinToString(", ") { it.getDisplayName(TextStyle.SHORT, locale) }
        }
    }
}
