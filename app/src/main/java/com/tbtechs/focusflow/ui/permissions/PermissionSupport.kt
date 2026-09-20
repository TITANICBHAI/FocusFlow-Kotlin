package com.tbtechs.focusflow.ui.permissions

import android.Manifest
import android.app.AppOpsManager
import android.app.NotificationManager
import android.app.admin.DevicePolicyManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.VpnService
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import com.tbtechs.focusflow.data.repository.LauncherController
import com.tbtechs.focusflow.data.repository.AlarmRepository
import com.tbtechs.focusflow.data.repository.UsageStatsRepository

enum class PermissionId {
    ACCESSIBILITY, USAGE, BATTERY, NOTIFICATIONS, DEVICE_ADMIN, OVERLAY, MEDIA, VPN, EXACT_ALARMS, LAUNCHER,
}

enum class PermissionStatus { GRANTED, DENIED, UNKNOWN }

data class PermissionDefinition(
    val id: PermissionId,
    val title: String,
    val description: String,
    val whyNeeded: String,
    val brokenWithout: List<String>,
    val optional: Boolean,
    val actionLabel: String,
)

val permissionDefinitions = listOf(
    PermissionDefinition(PermissionId.ACCESSIBILITY, "Accessibility Service", "Reads the foreground app name only. Data stays on your device.", "Redirects blocked apps before they open.", listOf("App blocking will not work", "Blocked apps can open during focus", "Blocks can be bypassed"), false, "Open Accessibility Settings"),
    PermissionDefinition(PermissionId.USAGE, "Usage Access", "Detects which app is in the foreground.", "Needed to detect blocked apps.", listOf("Foreground app detection fails", "Blocking can silently fail", "Stats may be inaccurate"), false, "Open Usage Access Settings"),
    PermissionDefinition(PermissionId.NOTIFICATIONS, "Notifications", "Shows reminders and the focus notification.", "Keeps reminders and the service notification visible.", listOf("No task reminders", "The focus notification disappears", "Blocking may stop"), false, "Open Notification Settings"),
    PermissionDefinition(PermissionId.BATTERY, "Battery Optimization", "Helps FocusFlow keep running in the background.", "Some phones stop background services.", listOf("Blocking can stop when the screen turns off", "Focus sessions may stop"), true, "Disable Battery Optimization"),
    PermissionDefinition(PermissionId.OVERLAY, "Appear on Top", "Shows the block screen over other apps.", "Prevents blocked apps from flashing first.", listOf("The block screen may open inside FocusFlow", "A blocked app may flash"), true, "Allow Display Over Other Apps"),
    PermissionDefinition(PermissionId.MEDIA, "Media & Files", "Lets you choose a block-screen image.", "The default image works without it.", listOf("Custom block-screen image is unavailable"), true, "Allow Photos and Media"),
    PermissionDefinition(PermissionId.VPN, "VPN Network Blocking", "Blocks internet for selected apps.", "Android requires one-time consent.", listOf("Selected apps keep internet access", "Network blocking cannot start"), true, "Grant VPN Permission"),
    PermissionDefinition(PermissionId.EXACT_ALARMS, "Exact Alarms", "Fires task reminders on schedule.", "Without it, Android may delay reminders.", listOf("Task reminders may be delayed"), true, "Allow Alarms & Reminders"),
    PermissionDefinition(PermissionId.DEVICE_ADMIN, "Device Admin", "Adds resistance to force-stop controls.", "Some phones expose force-stop paths.", listOf("Stopping FocusFlow may be easier"), true, "Activate Device Admin"),
    PermissionDefinition(PermissionId.LAUNCHER, "Home Launcher", "Intercepts blocked launches before they open.", "Stops blocked apps from flashing.", listOf("Blocked apps may flash first", "Launcher filtering is unavailable"), true, "Set as Home Launcher"),
)

suspend fun checkPermission(context: Context, id: PermissionId): PermissionStatus = try {
    when (id) {
        PermissionId.ACCESSIBILITY -> if (UsageStatsRepository(context).hasAccessibilityPermission()) PermissionStatus.GRANTED else PermissionStatus.DENIED
        PermissionId.USAGE -> if (UsageStatsRepository(context).hasPermission()) PermissionStatus.GRANTED else PermissionStatus.DENIED
        PermissionId.BATTERY -> if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M ||
            (context.getSystemService(Context.POWER_SERVICE) as PowerManager).isIgnoringBatteryOptimizations(context.packageName)
        ) PermissionStatus.GRANTED else PermissionStatus.DENIED
        PermissionId.NOTIFICATIONS -> if (NotificationManagerCompat.from(context).areNotificationsEnabled()) PermissionStatus.GRANTED else PermissionStatus.DENIED
        PermissionId.OVERLAY -> if (LauncherController(context).hasOverlayPermission()) PermissionStatus.GRANTED else PermissionStatus.DENIED
        PermissionId.MEDIA -> {
            val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Manifest.permission.READ_MEDIA_IMAGES else Manifest.permission.READ_EXTERNAL_STORAGE
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M || context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED) PermissionStatus.GRANTED else PermissionStatus.DENIED
        }
        PermissionId.VPN -> if (VpnService.prepare(context) == null) PermissionStatus.GRANTED else PermissionStatus.DENIED
        PermissionId.EXACT_ALARMS -> if (AlarmRepository(context).canScheduleExactAlarms()) PermissionStatus.GRANTED else PermissionStatus.DENIED
        PermissionId.DEVICE_ADMIN -> {
            val manager = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            val receiver = android.content.ComponentName(context, com.tbtechs.focusflow.enforcement.receivers.FocusDayDeviceAdminReceiver::class.java)
            if (manager.isAdminActive(receiver)) PermissionStatus.GRANTED else PermissionStatus.DENIED
        }
        PermissionId.LAUNCHER -> {
            val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
            val resolved = context.packageManager.resolveActivity(home, 0)
            if (resolved?.activityInfo?.packageName == context.packageName) PermissionStatus.GRANTED else PermissionStatus.DENIED
        }
    }
} catch (_: Exception) {
    PermissionStatus.UNKNOWN
}

suspend fun openPermissionSettings(context: Context, id: PermissionId) {
    when (id) {
        PermissionId.ACCESSIBILITY -> UsageStatsRepository(context).openAccessibilitySettings()
        PermissionId.USAGE -> UsageStatsRepository(context).openUsageAccessSettings()
        PermissionId.BATTERY -> UsageStatsRepository(context).openBatteryOptimizationSettings()
        PermissionId.DEVICE_ADMIN -> UsageStatsRepository(context).openDeviceAdminSettings()
        PermissionId.OVERLAY -> LauncherController(context).requestOverlayPermission()
        PermissionId.LAUNCHER -> context.startActivity(Intent(Settings.ACTION_HOME_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        PermissionId.NOTIFICATIONS -> context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
        PermissionId.EXACT_ALARMS -> AlarmRepository(context).requestExactAlarmPermission()
        PermissionId.MEDIA, PermissionId.VPN -> Unit
    }
}
