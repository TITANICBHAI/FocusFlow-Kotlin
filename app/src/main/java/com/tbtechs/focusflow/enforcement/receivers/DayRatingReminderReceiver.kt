package com.tbtechs.focusflow.enforcement.receivers

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.tbtechs.focusflow.R
import com.tbtechs.focusflow.enforcement.DayRatingNotificationScheduler
import com.tbtechs.focusflow.enforcement.LauncherActivity
import com.tbtechs.focusflow.notifications.NotificationChannels

/** Posts the daily self-rating prompt and re-arms the next reminder. */
class DayRatingReminderReceiver : BroadcastReceiver() {
    companion object {
        const val ACTION = "com.tbtechs.focusflow.alarm.DAY_RATING_REMIND"
        private const val NOTIFICATION_ID = 8812
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION) return
        val wakeLock = (context.getSystemService(Context.POWER_SERVICE) as? PowerManager)
            ?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "FocusFlow:DayRatingReminder")
            ?.also { it.setReferenceCounted(false); it.acquire(8_000L) }
        try {
            val tapIntent = Intent(context, LauncherActivity::class.java).apply {
                action = LauncherActivity.ACTION_OPEN_DAY_RATING
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                0,
                tapIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val notification = NotificationCompat.Builder(
                context,
                NotificationChannels.DAY_RATING,
            )
                .setSmallIcon(R.drawable.focusflow_icon)
                .setContentTitle("How was today?")
                .setContentText("Rate your day — one tap.")
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .build()
            (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .notify(NOTIFICATION_ID, notification)
            DayRatingNotificationScheduler.scheduleNext(context)
        } finally {
            if (wakeLock?.isHeld == true) runCatching { wakeLock.release() }
        }
    }
}