package com.denser.june.core.domain.reminder

import kotlinx.serialization.Serializable
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.UUID

/**
 * A single journaling "moment": one time of day, repeated on a set of weekdays.
 * Users can have several of these (e.g. a morning intention + an evening reflection).
 */
@Serializable
data class ReminderSlot(
    val id: String = UUID.randomUUID().toString(),
    /** ISO-8601 local time, e.g. "21:14". */
    val time: String,
    /** ISO day-of-week values (1 = Monday … 7 = Sunday). */
    val dayValues: List<Int> = DayOfWeek.entries.map { it.value },
    val enabled: Boolean = true,
    val label: String? = null,
) {
    val localTime: LocalTime
        get() = runCatching { LocalTime.parse(time) }.getOrDefault(LocalTime.of(21, 0))

    val days: Set<DayOfWeek>
        get() = dayValues.mapNotNull { v -> DayOfWeek.entries.firstOrNull { it.value == v } }.toSet()

    fun withDays(days: Set<DayOfWeek>): ReminderSlot =
        copy(dayValues = days.map { it.value }.sorted())

    fun withTime(time: LocalTime): ReminderSlot =
        copy(time = time.withSecond(0).withNano(0).toString())

    /** Next moment this slot fires strictly after [now], or null if it has no days. */
    fun nextTrigger(now: LocalDateTime = LocalDateTime.now()): LocalDateTime? {
        val activeDays = days
        if (activeDays.isEmpty()) return null
        val today: LocalDate = now.toLocalDate()
        for (offset in 0..7) {
            val date = today.plusDays(offset.toLong())
            if (date.dayOfWeek !in activeDays) continue
            val candidate = date.atTime(localTime)
            if (candidate.isAfter(now)) return candidate
        }
        return null
    }

    companion object {
        const val MAX_SLOTS = 6
        const val LEGACY_ID = "legacy"
    }
}

/** The soonest upcoming trigger across all enabled slots. */
fun List<ReminderSlot>.nextUpcoming(
    now: LocalDateTime = LocalDateTime.now()
): Pair<ReminderSlot, LocalDateTime>? =
    asSequence()
        .filter { it.enabled }
        .mapNotNull { slot -> slot.nextTrigger(now)?.let { slot to it } }
        .minByOrNull { it.second }
