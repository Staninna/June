package com.denser.june.presentation.screens.settings.screens.reminder

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
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
import com.denser.june.presentation.components.JuneDateTimePicker
import com.denser.june.presentation.components.JuneDateTimePickerMode
import com.denser.june.presentation.components.JuneTopAppBar
import com.denser.june.presentation.navigation.AppNavigator
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import java.time.LocalDate
import java.time.LocalTime

private sealed interface TimePickerTarget {
    data class New(val label: String? = null) : TimePickerTarget
    data class Existing(val slot: ReminderSlot) : TimePickerTarget
}

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

            // Don't flash an "off / no moments" state while preferences are still loading.
            if (!state.isLoaded) return@LazyColumn

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
                    onPreset = { time, label ->
                        haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                        withNotificationPermission { viewModel.addSlot(time, label) }
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
