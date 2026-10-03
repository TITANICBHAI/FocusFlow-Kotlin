package com.tbtechs.focusflow.data.repository

/**
 * Device-local enforcement/runtime state is never portable, even in older
 * backup files that included these keys.
 */
object BackupSettingsPolicy {
    val neverApplyImportKeys: Set<String> = setOf(
        "standaloneBlockPackages",
        "standaloneBlockUntil",
        "standaloneVpnPackages",
        "autoCopiedAlwaysOnPackages",
        "alwaysOnEnforcementEnabled",
        "focusModeEnabled",
        "pomodoroEnabled",
        "notificationsEnabled",
        "weeklyReportEnabled",
        "launcherEnabled",
        "aversionDimmerEnabled",
        "aversionVibrateEnabled",
        "aversionSoundEnabled",
        "systemGuardEnabled",
        "blockInstallActionsEnabled",
        "blockYoutubeShortsEnabled",
        "blockInstagramReelsEnabled",
        "vpnBlockEnabled",
        "autoCopyToAlwaysOn",
        "vpnSelfHealEnabled",
        "pinProtectionEnabled",
    )

    fun mayApplyImportKey(key: String): Boolean = key !in neverApplyImportKeys

    fun importableKeys(keys: Iterable<String>): Set<String> =
        keys.filterTo(linkedSetOf()) { mayApplyImportKey(it) }
}