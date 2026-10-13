package com.tbtechs.focusflow.ui.backup

import com.tbtechs.focusflow.data.model.AppSettings
import org.json.JSONArray
import org.json.JSONObject

internal data class ProtectedSettingsSnapshot(
    val alwaysOnPackages: Set<String>,
    val alwaysOnVpnPackages: Set<String>,
    val blockedWords: Set<String>,
    val recurringBlockSchedules: Set<String>,
    val dailyAllowanceEntries: Set<String>,
    val focusMirrorVpnEnabled: Boolean,
)

internal object ImportProtectionPolicy {
    fun requiresPin(
        current: ProtectedSettingsSnapshot,
        imported: ProtectedSettingsSnapshot,
    ): Boolean =
        current.alwaysOnPackages.any { it !in imported.alwaysOnPackages } ||
            current.alwaysOnVpnPackages.any { it !in imported.alwaysOnVpnPackages } ||
            current.blockedWords.any { it !in imported.blockedWords } ||
            current.recurringBlockSchedules.any { it !in imported.recurringBlockSchedules } ||
            current.dailyAllowanceEntries.any { it !in imported.dailyAllowanceEntries } ||
            (current.focusMirrorVpnEnabled && !imported.focusMirrorVpnEnabled)

    fun fromSettings(
        current: AppSettings,
        currentAlwaysOnVpnPackages: Collection<String>,
        imported: JSONObject,
    ): Pair<ProtectedSettingsSnapshot, ProtectedSettingsSnapshot> {
        val local = ProtectedSettingsSnapshot(
            alwaysOnPackages = current.alwaysBlockPackages.toSet(),
            alwaysOnVpnPackages = currentAlwaysOnVpnPackages.toSet(),
            blockedWords = current.blockedWords.toSet(),
            recurringBlockSchedules = current.recurringBlockSchedules
                .mapIndexed { index, schedule -> schedule.id.ifBlank { "imported-$index" } }
                .toSet(),
            dailyAllowanceEntries = current.dailyAllowanceConfigJson
                .asAllowanceIdentities(),
            focusMirrorVpnEnabled = current.focusMirrorVpnEnabled,
        )

        val importedSchedules = imported.optJSONArray("greyoutSchedule")
            ?: imported.optJSONArray("recurringBlockSchedules")
        val importedAllowances = imported.optJSONArray("dailyAllowanceEntries")

        val portable = local.copy(
            alwaysOnPackages = imported.optJSONArray("alwaysOnPackages")
                ?.stringValues()
                ?.toSet()
                ?: local.alwaysOnPackages,
            alwaysOnVpnPackages = imported.optJSONArray("alwaysOnVpnPackages")
                ?.stringValues()
                ?.toSet()
                ?: local.alwaysOnVpnPackages,
            blockedWords = imported.optJSONArray("blockedWords")
                ?.stringValues()
                ?.toSet()
                ?: local.blockedWords,
            recurringBlockSchedules = importedSchedules
                ?.scheduleIdentities()
                ?: local.recurringBlockSchedules,
            dailyAllowanceEntries = importedAllowances
                ?.allowanceIdentities()
                ?: local.dailyAllowanceEntries,
            focusMirrorVpnEnabled = if (imported.has("focusMirrorVpnEnabled")) {
                imported.optBoolean("focusMirrorVpnEnabled", local.focusMirrorVpnEnabled)
            } else {
                local.focusMirrorVpnEnabled
            },
        )

        return local to portable
    }

    private fun JSONArray.stringValues(): List<String> =
        (0 until length()).mapNotNull { index ->
            optString(index).takeIf(String::isNotBlank)
        }

    private fun JSONArray.scheduleIdentities(): Set<String> =
        (0 until length()).mapNotNull { index ->
            val schedule = optJSONObject(index) ?: return@mapNotNull null
            schedule.optString("id").ifBlank { "imported-$index" }
        }.toSet()

    private fun JSONArray.allowanceIdentities(): Set<String> =
        (0 until length()).mapNotNull { index ->
            val entry = optJSONObject(index) ?: return@mapNotNull null
            entry.optString("packageName")
                .ifBlank { entry.optString("package") }
                .ifBlank { entry.optString("id") }
                .takeIf(String::isNotBlank)
        }.toSet()

    private fun String?.asAllowanceIdentities(): Set<String> = runCatching {
        JSONArray(this ?: "[]").allowanceIdentities()
    }.getOrDefault(emptySet())
}