package com.tbtechs.focusflow.notifications

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.tbtechs.focusflow.MainActivity
import com.tbtechs.focusflow.R
import com.tbtechs.focusflow.data.model.Task
import com.tbtechs.focusflow.enforcement.AppBlockerAccessibilityService
import com.tbtechs.focusflow.enforcement.receivers.NotificationActionReceiver
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Maintains one ongoing status card for the task whose scheduled interval is
 * currently active, when Focus Mode is not already showing that task.
 *
 * This notification does not start Focus Mode, app blocking, or the VPN.
 */
object LiveTaskStatusNotificationPublisher {
    private const val NOTIFICATION_TAG = "live-task-status"
    private const val NOTIFICATION_ID = 1003
    private const val PREF_FOCUS_ACTIVE = "focus_active"
    private const val REQUEST_OPEN = 3000
    private const val REQUEST_COMPLETE = 3001
    private const val REQUEST_EXTEND_15 = 3002
    private const val REQUEST_EXTEND_30 = 3003
    private const val REQUEST_SKIP = 3004
    private val terminalStatuses = setOf("completed", "skipped", "overdue")

    fun sync(context: Context, tasks: List<Task>, nowMs: Long = System.currentTimeMillis()) {
        val appContext = context.applicationContext
        NotificationChannels.createAll(appContext)
        val manager = NotificationManagerCompat.from(appContext)
        val focusPrefs = appContext.getSharedPreferences(
            AppBlockerAccessibilityService.PREFS_NAME,
            Context.MODE_PRIVATE,
        )

        // ForegroundTaskService already owns the task-status notification while
        // focus enforcement is active. Avoid displaying a duplicate status card.
        if (focusPrefs.getBoolean(PREF_FOCUS_ACTIVE, false)) {
            manager.cancel(NOTIFICATION_TAG, NOTIFICATION_ID)
            return
        }

        val currentTask = tasks.asSequence()
            .filter { it.status !in terminalStatuses }
            .mapNotNull { task ->
                val startMs = parseEpochMillis(task.startTime) ?: return@mapNotNull null
                val endMs = parseEpochMillis(task.endTime) ?: return@mapNotNull null
                if (startMs > nowMs || endMs <= nowMs || endMs <= startMs) {
                    return@mapNotNull null
                }
                LiveTask(task, startMs, endMs)
            }
            .maxWithOrNull(compareBy<LiveTask> { it.startMs }.thenBy { it.task.id })

        if (currentTask == null || !manager.areNotificationsEnabled()) {
            manager.cancel(NOTIFICATION_TAG, NOTIFICATION_ID)
            return
        }

        runCatching {
            manager.notify(
                NOTIFICATION_TAG,
                NOTIFICATION_ID,
                buildNotification(appContext, currentTask, nowMs),
            )
        }
    }

    fun cancel(context: Context) {
        NotificationManagerCompat.from(context.applicationContext)
            .cancel(NOTIFICATION_TAG, NOTIFICATION_ID)
    }

    private fun buildNotification(
        context: Context,
        liveTask: LiveTask,
        nowMs: Long,
    ) = NotificationCompat.Builder(context, NotificationChannels.LIVE_TASK_STATUS)
        .setSmallIcon(R.mipmap.ic_launcher)
        .setContentTitle("In progress · ${liveTask.task.title}")
        .setContentText("Ends at ${formatEndTime(liveTask.endMs)}")
        .setSubText("Live task status")
        .setCategory(NotificationCompat.CATEGORY_PROGRESS)
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .setWhen(liveTask.endMs - nowMs + SystemClock.elapsedRealtime())
        .setUsesChronometer(true)
        .setChronometerCountDown(true)
        .setShowWhen(true)
        .setTimeoutAfter((liveTask.endMs - nowMs).coerceAtLeast(1_000L))
        .setContentIntent(openTaskPendingIntent(context, liveTask.task.id))
        .addAction(
            0,
            "✓ Done",
            actionPendingIntent(
                context,
                liveTask.task.id,
                NotificationActionReceiver.ACTION_COMPLETE,
                REQUEST_COMPLETE,
            ),
        )
        .addAction(
            0,
            "+15m",
            actionPendingIntent(
                context,
                liveTask.task.id,
                NotificationActionReceiver.ACTION_EXTEND,
                REQUEST_EXTEND_15,
                minutes = 15,
            ),
        )
        .addAction(
            0,
            "+30m",
            actionPendingIntent(
                context,
                liveTask.task.id,
                NotificationActionReceiver.ACTION_EXTEND,
                REQUEST_EXTEND_30,
                minutes = 30,
            ),
        )
        .addAction(
            0,
            "Skip",
            actionPendingIntent(
                context,
                liveTask.task.id,
                NotificationActionReceiver.ACTION_SKIP,
                REQUEST_SKIP,
            ),
        )
        .build()

    private fun openTaskPendingIntent(context: Context, taskId: String): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_MAIN
            addCategory(Intent.CATEGORY_LAUNCHER)
            data = Uri.parse("focusflow-internal://live-task/${Uri.encode(taskId)}")
            putExtra(NotificationActionReceiver.EXTRA_TASK_ID, taskId)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        return PendingIntent.getActivity(
            context,
            REQUEST_OPEN,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun actionPendingIntent(
        context: Context,
        taskId: String,
        action: String,
        requestCode: Int,
        minutes: Int? = null,
    ): PendingIntent {
        val intent = Intent(context, NotificationActionReceiver::class.java).apply {
            this.action = action
            data = Uri.parse(
                "focusflow-internal://live-task/${Uri.encode(taskId)}/${Uri.encode(action)}",
            )
            putExtra(NotificationActionReceiver.EXTRA_TASK_ID, taskId)
            minutes?.let { putExtra(NotificationActionReceiver.EXTRA_MINUTES, it) }
        }
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun parseEpochMillis(value: String): Long? =
        runCatching { Instant.parse(value).toEpochMilli() }
            .recoverCatching { OffsetDateTime.parse(value).toInstant().toEpochMilli() }
            .getOrNull()

    private fun formatEndTime(endMs: Long): String =
        Instant.ofEpochMilli(endMs)
            .atZone(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault()))

    private data class LiveTask(
        val task: Task,
        val startMs: Long,
        val endMs: Long,
    )
}