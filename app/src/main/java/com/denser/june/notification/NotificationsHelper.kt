package com.denser.june.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.denser.june.MainActivity
import com.denser.june.core.R
import com.denser.june.core.domain.reminder.ReminderSlot

class NotificationsHelper(private val context: Context) {

    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.reminders),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = context.getString(R.string.reminders_desc)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun showReminderNotification(slot: ReminderSlot? = null) {
        createNotificationChannel()

        val hour = slot?.localTime?.hour ?: java.time.LocalTime.now().hour
        val morningOptions = listOf(
            context.getString(R.string.reminder_morning_title_1) to context.getString(R.string.reminder_morning_msg_1),
            context.getString(R.string.reminder_morning_title_2) to context.getString(R.string.reminder_morning_msg_2),
            context.getString(R.string.reminder_morning_title_3) to context.getString(R.string.reminder_morning_msg_3),
        )
        val middayOptions = listOf(
            context.getString(R.string.reminder_midday_title_1) to context.getString(R.string.reminder_midday_msg_1),
            context.getString(R.string.reminder_midday_title_2) to context.getString(R.string.reminder_midday_msg_2),
            context.getString(R.string.reminder_midday_title_3) to context.getString(R.string.reminder_midday_msg_3),
        )
        val eveningOptions = listOf(
            context.getString(R.string.reminder_title_1) to context.getString(R.string.reminder_msg_1),
            context.getString(R.string.reminder_title_2) to context.getString(R.string.reminder_msg_2),
            context.getString(R.string.reminder_title_3) to context.getString(R.string.reminder_msg_3),
            context.getString(R.string.reminder_title_4) to context.getString(R.string.reminder_msg_4),
            context.getString(R.string.reminder_title_5) to context.getString(R.string.reminder_msg_5),
            context.getString(R.string.reminder_title_6) to context.getString(R.string.reminder_msg_6),
            context.getString(R.string.reminder_title_7) to context.getString(R.string.reminder_msg_7)
        )

        val notificationOptions = when (hour) {
            in 4..10 -> morningOptions
            in 11..16 -> middayOptions
            else -> eveningOptions
        }
        val (randomTitle, message) = notificationOptions.random()
        // A user-chosen label ("Gratitude break") reads better as the headline.
        val title = slot?.label?.takeIf { it.isNotBlank() } ?: randomTitle

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("OPEN_NEW_NOTE", true)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_notification)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    companion object {
        private const val CHANNEL_ID = "reminder_channel"
        private const val NOTIFICATION_ID = 1
    }
}