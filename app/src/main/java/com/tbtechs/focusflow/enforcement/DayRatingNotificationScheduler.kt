package com.tbtechs.focusflow.enforcement

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import com.tbtechs.focusflow.enforcement.receivers.DayRatingReminderReceiver
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/** Schedules the daily day-rating reminder without requiring an exact alarm. */
object DayRatingNotificationScheduler {
    private const val TAG = "DayRatingScheduler"
    private const val PREFS = "focusday_prefs"
    private const val REQUEST = 8813

    fun scheduleNext(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val hour = prefs.getInt("sleep_time_hour", 22)
        val minute = prefs.getInt("sleep_time_minute", 0)
        val zone = ZoneId.systemDefault()
        val now = System.currentTimeMillis()
        var targetMs = LocalDate.now().atTime(LocalTime.of(hour, minute))
            .minusMinutes(30).atZone(zone).toInstant().toEpochMilli()
        if (targetMs <= now) {
            targetMs = LocalDate.now().plusDays(1).atTime(LocalTime.of(hour, minute))
                .minusMinutes(30).atZone(zone).toInstant().toEpochMilli()
        }
        (context.getSystemService(Context.ALARM_SERVICE) as AlarmManager).setInexactRepeating(
            AlarmManager.RTC,
            targetMs,
            AlarmManager.INTERVAL_DAY,
            buildPendingIntent(context),
        )
        prefs.edit().putBoolean("day_rating_alarm_set", true).apply()
        Log.d(TAG, "Scheduled for $targetMs")
    }

    fun ensureScheduled(context: Context) {
        if (!context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getBoolean("day_rating_alarm_set", false)
        ) {
            scheduleNext(context)
        }
    }

    fun cancel(context: Context) {
        (context.getSystemService(Context.ALARM_SERVICE) as AlarmManager)
            .cancel(buildPendingIntent(context))
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean("day_rating_alarm_set", false).apply()
    }

    private fun buildPendingIntent(context: Context): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            REQUEST,
            Intent(context, DayRatingReminderReceiver::class.java).apply {
                action = DayRatingReminderReceiver.ACTION
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
}