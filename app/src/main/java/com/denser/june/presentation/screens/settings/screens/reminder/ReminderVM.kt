package com.denser.june.presentation.screens.settings.screens.reminder

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.denser.june.core.domain.model.enums.TimeFormat
import com.denser.june.core.domain.preferences.JournalPreferences
import com.denser.june.core.domain.reminder.ReminderScheduler
import com.denser.june.core.domain.reminder.ReminderSlot
import com.denser.june.core.domain.reminder.nextUpcoming
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.LocalTime

data class ReminderSettingsState(
    val isEnabled: Boolean = false,
    val slots: List<ReminderSlot> = emptyList(),
    val timeFormat: TimeFormat = TimeFormat.TWELVE_HOUR,
    val canScheduleExact: Boolean = true,
    val isLoaded: Boolean = false,
) {
    val next: Pair<ReminderSlot, LocalDateTime>?
        get() = if (isEnabled) slots.nextUpcoming() else null
    val activeCount: Int get() = slots.count { it.enabled && it.days.isNotEmpty() }
    val canAddMore: Boolean get() = slots.size < ReminderSlot.MAX_SLOTS
}

class ReminderVM(
    private val journalPreferences: JournalPreferences,
    private val reminderScheduler: ReminderScheduler
) : ViewModel() {

    private val exactAllowed = MutableStateFlow(reminderScheduler.canScheduleExact())

    val state = combine(
        journalPreferences.isReminderEnabled(),
        journalPreferences.reminderSlots(),
        journalPreferences.timeFormat(),
        exactAllowed
    ) { enabled, slots, timeFormat, exact ->
        ReminderSettingsState(
            isEnabled = enabled,
            slots = slots.sortedBy { it.localTime },
            timeFormat = timeFormat,
            canScheduleExact = exact,
            isLoaded = true,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ReminderSettingsState()
    )

    /** Called when returning to the screen (e.g. from the exact-alarm settings page). */
    fun refreshPermissions() {
        exactAllowed.value = reminderScheduler.canScheduleExact()
        viewModelScope.launch { resync() }
    }

    fun toggleReminder(enabled: Boolean) {
        viewModelScope.launch {
            journalPreferences.setReminderEnabled(enabled)
            // First time on with nothing configured: seed a sensible evening moment.
            val slots = currentSlots()
            if (enabled && slots.isEmpty()) {
                writeSlots(listOf(ReminderSlot(time = "21:00")))
            } else {
                resync()
            }
        }
    }

    fun addSlot(time: LocalTime, label: String? = null) {
        viewModelScope.launch {
            val slots = currentSlots()
            if (slots.size >= ReminderSlot.MAX_SLOTS) return@launch
            val slot = ReminderSlot(time = "00:00", label = label).withTime(time)
            if (!journalPreferences.isReminderEnabled().first()) {
                journalPreferences.setReminderEnabled(true)
            }
            writeSlots(slots + slot)
        }
    }

    fun setSlotEnabled(id: String, enabled: Boolean) = updateSlot(id) { it.copy(enabled = enabled) }

    fun updateSlotTime(id: String, time: LocalTime) = updateSlot(id) { it.withTime(time) }

    fun updateSlotLabel(id: String, label: String) =
        updateSlot(id) { it.copy(label = label.trim().ifBlank { null }) }

    fun toggleDay(id: String, day: DayOfWeek) = updateSlot(id) { slot ->
        val days = slot.days
        slot.withDays(if (day in days) days - day else days + day)
    }

    fun setDays(id: String, days: Set<DayOfWeek>) = updateSlot(id) { it.withDays(days) }

    fun removeSlot(id: String) {
        viewModelScope.launch {
            writeSlots(currentSlots().filterNot { it.id == id })
        }
    }

    fun restoreSlot(slot: ReminderSlot) {
        viewModelScope.launch {
            val slots = currentSlots()
            if (slots.none { it.id == slot.id } && slots.size < ReminderSlot.MAX_SLOTS) {
                writeSlots(slots + slot)
            }
        }
    }

    private fun updateSlot(id: String, transform: (ReminderSlot) -> ReminderSlot) {
        viewModelScope.launch {
            writeSlots(currentSlots().map { if (it.id == id) transform(it) else it })
        }
    }

    private suspend fun currentSlots(): List<ReminderSlot> = journalPreferences.reminderSlots().first()

    private suspend fun writeSlots(slots: List<ReminderSlot>) {
        journalPreferences.setReminderSlots(slots)
        // Keep legacy keys roughly in step so a downgrade still has something sensible.
        slots.firstOrNull()?.let {
            journalPreferences.setReminderTime(it.time)
            journalPreferences.setReminderDays(it.days)
        }
        resync()
    }

    private suspend fun resync() {
        reminderScheduler.sync(
            masterEnabled = journalPreferences.isReminderEnabled().first(),
            slots = currentSlots()
        )
    }
}
