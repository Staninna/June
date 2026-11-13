package com.denser.june.core.domain.reminder

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.LocalTime

class ReminderSlotTest {

    // Friday 13 Nov 2026, 12:00
    private val now = LocalDateTime.of(2026, 11, 13, 12, 0)

    @Test
    fun `fires later today when time has not passed`() {
        val slot = ReminderSlot(time = "21:00")
        assertEquals(now.toLocalDate().atTime(21, 0), slot.nextTrigger(now))
    }

    @Test
    fun `rolls to next selected day when time already passed`() {
        val slot = ReminderSlot(time = "08:00").withDays(setOf(DayOfWeek.MONDAY))
        assertEquals(LocalDateTime.of(2026, 11, 16, 8, 0), slot.nextTrigger(now))
    }

    @Test
    fun `same weekday next week when only today selected and passed`() {
        val slot = ReminderSlot(time = "08:00").withDays(setOf(DayOfWeek.FRIDAY))
        assertEquals(LocalDateTime.of(2026, 11, 20, 8, 0), slot.nextTrigger(now))
    }

    @Test
    fun `no days means no trigger`() {
        assertNull(ReminderSlot(time = "08:00").withDays(emptySet()).nextTrigger(now))
    }

    @Test
    fun `next upcoming picks the soonest enabled slot`() {
        val morning = ReminderSlot(id = "m", time = "08:00")
        val evening = ReminderSlot(id = "e", time = "21:00")
        val lunch = ReminderSlot(id = "l", time = "13:00", enabled = false)
        val next = listOf(morning, evening, lunch).nextUpcoming(now)
        assertEquals("e", next?.first?.id)
    }

    @Test
    fun `withTime strips seconds`() {
        assertEquals("07:45", ReminderSlot(time = "00:00").withTime(LocalTime.of(7, 45, 30)).time)
    }
}
