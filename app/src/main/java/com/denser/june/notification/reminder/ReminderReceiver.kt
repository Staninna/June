package com.denser.june.notification.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.denser.june.core.data.reminder.ReminderSchedulerImpl
import com.denser.june.core.domain.preferences.JournalPreferences
import com.denser.june.core.domain.reminder.ReminderScheduler
import com.denser.june.notification.NotificationsHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class ReminderReceiver : BroadcastReceiver(), KoinComponent {

    private val journalPreferences: JournalPreferences by inject()
    private val reminderScheduler: ReminderScheduler by inject()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        scope.launch {
            try {
                handle(context, intent)
            } finally {
                pending.finish()
            }
        }
    }

    private suspend fun handle(context: Context, intent: Intent) {
        val masterEnabled = journalPreferences.isReminderEnabled().first()
        val slots = journalPreferences.reminderSlots().first()

        when (intent.action) {
            ReminderSchedulerImpl.ACTION_SHOW_REMINDER -> {
                val slotId = intent.getStringExtra(ReminderSchedulerImpl.EXTRA_SLOT_ID)
                val slot = slots.firstOrNull { it.id == slotId }
                    // Alarms set by older builds carry no slot id.
                    ?: slots.firstOrNull().takeIf { slotId == null }
                if (masterEnabled && slot != null && slot.enabled) {
                    NotificationsHelper(context).showReminderNotification(slot)
                }
                reminderScheduler.sync(masterEnabled, slots)
            }
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED -> reminderScheduler.sync(masterEnabled, slots)
        }
    }
}
