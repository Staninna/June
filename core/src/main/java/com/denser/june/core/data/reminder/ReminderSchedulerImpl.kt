package com.denser.june.core.data.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.edit
import androidx.core.net.toUri
import com.denser.june.core.domain.reminder.ReminderScheduler
import com.denser.june.core.domain.reminder.ReminderSlot
import java.time.ZoneId

class ReminderSchedulerImpl(
    private val context: Context
) : ReminderScheduler {

    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    /** Tracks which slot ids currently have a pending alarm, so removed slots can be cancelled. */
    private val bookkeeping by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    override fun schedule(slot: ReminderSlot) {
        val next = slot.takeIf { it.enabled }?.nextTrigger()
        if (next == null) {
            cancel(slot.id)
            return
        }
        val triggerAt = next.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val pendingIntent = pendingIntentFor(slot.id)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
        } else {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
        }
        updateScheduledIds { it + slot.id }
    }

    override fun cancel(slotId: String) {
        alarmManager.cancel(pendingIntentFor(slotId))
        updateScheduledIds { it - slotId }
    }

    override fun sync(masterEnabled: Boolean, slots: List<ReminderSlot>) {
        cancelLegacyAlarm()
        val wanted = if (masterEnabled) slots.filter { it.enabled && it.days.isNotEmpty() } else emptyList()
        val wantedIds = wanted.map { it.id }.toSet()
        scheduledIds().filterNot { it in wantedIds }.forEach(::cancel)
        wanted.forEach(::schedule)
    }

    override fun canScheduleExact(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()

    private fun pendingIntentFor(slotId: String): PendingIntent {
        val intent = Intent(ACTION_SHOW_REMINDER).apply {
            // Explicit component: the data URI below would otherwise need a matching <data> filter.
            setClassName(context.packageName, RECEIVER_CLASS)
            // Distinct data URI => distinct PendingIntent per slot.
            data = "june://reminder/$slotId".toUri()
            putExtra(EXTRA_SLOT_ID, slotId)
        }
        return PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /** Pre-2.2 builds scheduled a single alarm without a data URI. */
    private fun cancelLegacyAlarm() {
        val legacy = Intent(ACTION_SHOW_REMINDER).apply { setPackage(context.packageName) }
        val pi = PendingIntent.getBroadcast(
            context,
            LEGACY_REQUEST_CODE,
            legacy,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (pi != null) {
            alarmManager.cancel(pi)
            pi.cancel()
        }
    }

    private fun scheduledIds(): Set<String> =
        bookkeeping.getStringSet(KEY_SCHEDULED_IDS, emptySet()).orEmpty().toSet()

    private fun updateScheduledIds(transform: (Set<String>) -> Set<String>) {
        bookkeeping.edit { putStringSet(KEY_SCHEDULED_IDS, transform(scheduledIds())) }
    }

    companion object {
        const val ACTION_SHOW_REMINDER = "com.denser.june.ACTION_SHOW_REMINDER"
        const val EXTRA_SLOT_ID = "reminder_slot_id"
        private const val RECEIVER_CLASS = "com.denser.june.notification.reminder.ReminderReceiver"
        private const val REQUEST_CODE = 1002
        private const val LEGACY_REQUEST_CODE = 1001
        private const val PREFS_NAME = "june_reminder_alarms"
        private const val KEY_SCHEDULED_IDS = "scheduled_ids"
    }
}
