package com.denser.june.core.domain.reminder

interface ReminderScheduler {
    /** Schedules (or re-schedules) the next alarm for a single slot. */
    fun schedule(slot: ReminderSlot)

    /** Cancels the pending alarm for a single slot. */
    fun cancel(slotId: String)

    /**
     * Makes the alarm state match [slots] exactly: every enabled slot is scheduled,
     * everything else that was previously scheduled (including removed slots) is cancelled.
     */
    fun sync(masterEnabled: Boolean, slots: List<ReminderSlot>)

    /** Whether the OS currently lets us fire alarms at the exact minute. */
    fun canScheduleExact(): Boolean
}
