package com.tbtechs.focusflow.data.backup

import com.tbtechs.focusflow.data.model.CanonicalTimestamp
import com.tbtechs.focusflow.data.repository.BackupSettingsPolicy
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import java.time.Instant
import java.time.OffsetDateTime

/**
 * Pure V1 wire-envelope serializer shared by the SAF export path and golden
 * compatibility tests.
 */
object BackupV1Exporter {
    private val encoder = Json { prettyPrint = true }
    private val taskTimestampFields = listOf(
        "startTime",
        "endTime",
        "createdAt",
        "updatedAt",
    )
    private val neverExportKeys = BackupSettingsPolicy.neverApplyImportKeys + setOf(
        "onboardingComplete",
        "privacyAccepted",
        "launcherWallpaperUri",
        "overlayWallpaper",
    )

    fun buildBackupJson(
        settings: JsonObject,
        tasks: List<JsonObject>,
        exportedAt: Instant,
        exportedAtHuman: String,
        appVersion: String? = null,
    ): String {
        val portableSettings = portableSettings(settings)
        val canonicalTasks = JsonArray(tasks.map(::canonicalizeTask))
        val greyoutWindows = portableSettings.array("greyoutSchedule")
        val allowances = portableSettings.array("dailyAllowanceEntries")
        val blockedWords = portableSettings.array("blockedWords")

        val envelope = linkedMapOf<String, JsonElement>(
            "kind" to JsonPrimitive(ENVELOPE_KIND),
            "version" to JsonPrimitive(1),
            "exportedAt" to JsonPrimitive(CanonicalTimestamp.format(exportedAt)),
            "exportedAtHuman" to JsonPrimitive(exportedAtHuman),
            "platform" to JsonObject(mapOf("os" to JsonPrimitive("android"))),
            "settings" to portableSettings,
            "tasks" to canonicalTasks,
            "presetSections" to buildPresetSections(portableSettings),
            "summary" to JsonObject(
                linkedMapOf(
                    "taskCount" to JsonPrimitive(canonicalTasks.size),
                    "blockedWordCount" to JsonPrimitive(blockedWords.size),
                    "greyoutWindowCount" to JsonPrimitive(greyoutWindows.size),
                    "dailyAllowanceCount" to JsonPrimitive(allowances.size),
                ),
            ),
        )
        appVersion?.let { envelope["appVersion"] = JsonPrimitive(it) }
        return encoder.encodeToString(JsonObject.serializer(), JsonObject(envelope))
    }

    private fun portableSettings(settings: JsonObject): JsonObject {
        val values = settings.toMutableMap()
        neverExportKeys.forEach(values::remove)
        // This portable setting is explicit in V1, including when false.
        values["focusMirrorVpnEnabled"] =
            (settings["focusMirrorVpnEnabled"] as? JsonPrimitive)
                ?.booleanOrNull
                ?.let(::JsonPrimitive)
                ?: JsonPrimitive(false)
        return JsonObject(values)
    }

    private fun canonicalizeTask(task: JsonObject): JsonObject {
        val fields = task.toMutableMap()
        val tags = when (val rawTags = task["tags"]) {
            null -> JsonArray(emptyList())
            is JsonArray -> rawTags
            else -> error("Task export contains invalid tags.")
        }
        if (tags.size > BackupJsonLimits.MAX_TAGS_PER_TASK) {
            error("Task export exceeds the maximum tag count.")
        }
        tags.forEach { value ->
            val tag = (value as? JsonPrimitive)
                ?.takeIf { it.isString }
                ?.content
                ?: error("Task export contains a non-string tag.")
            if (tag.length > BackupJsonLimits.MAX_TAG_CHARS) {
                error("Task export contains a tag exceeding the character limit.")
            }
        }
        fields["tags"] = tags
        taskTimestampFields.forEach { field ->
            val raw = (task[field] as? JsonPrimitive)
                ?.takeIf { it.isString }
                ?.content
                ?: error("Task export contains an invalid $field timestamp.")
            val instant = runCatching { Instant.parse(raw) }
                .recoverCatching { OffsetDateTime.parse(raw).toInstant() }
                .getOrElse { error("Task export contains an invalid $field timestamp.") }
            fields[field] = JsonPrimitive(CanonicalTimestamp.format(instant))
        }
        // Do not inspect, derive from, or rewrite reminders; they are task data.
        return JsonObject(fields)
    }

    private fun buildPresetSections(settings: JsonObject): JsonArray {
        val allowedApps = settings.array("allowedInFocus")
        val standaloneApps = settings.array("standaloneBlockPackages")
        val standaloneVpnApps = settings.array("standaloneVpnPackages")
        val alwaysOnApps = settings.array("alwaysOnPackages")
        val alwaysOnVpnApps = settings.array("alwaysOnVpnPackages")
        val allowances = settings.array("dailyAllowanceEntries")
        val keywords = settings.array("blockedWords")
        val schedules = settings.array("greyoutSchedule")
        val blockPresets = settings.array("blockPresets")
        val allowedAppPresets = settings.array("allowedAppPresets")

        return JsonArray(
            listOf(
                section(
                    id = "focus-mode",
                    name = "Focus Mode",
                    configured = allowedApps.isNotEmpty() || allowedAppPresets.isNotEmpty(),
                    fields = linkedMapOf(
                        "appPackages" to allowedApps,
                        "itemCount" to JsonPrimitive(allowedAppPresets.size),
                        "details" to JsonObject(mapOf("allowedAppPresets" to allowedAppPresets)),
                    ),
                ),
                section(
                    id = "standalone-block",
                    name = "Standalone Block",
                    configured = standaloneApps.isNotEmpty() || standaloneVpnApps.isNotEmpty(),
                    fields = linkedMapOf(
                        "appPackages" to standaloneApps,
                        "vpnPackages" to standaloneVpnApps,
                        "details" to JsonObject(
                            mapOf("runtimeState" to JsonPrimitive("local-only")),
                        ),
                    ),
                ),
                section(
                    id = "always-on",
                    name = "Always-On Blocking",
                    configured = alwaysOnApps.isNotEmpty() || alwaysOnVpnApps.isNotEmpty(),
                    fields = linkedMapOf(
                        "appPackages" to alwaysOnApps,
                        "vpnPackages" to alwaysOnVpnApps,
                    ),
                ),
                section(
                    id = "daily-allowance",
                    name = "Daily Allowance",
                    configured = allowances.isNotEmpty(),
                    fields = linkedMapOf(
                        "itemCount" to JsonPrimitive(allowances.size),
                        "details" to JsonObject(mapOf("entries" to allowances)),
                    ),
                ),
                section(
                    id = "keyword-blocker",
                    name = "Keyword Blocker",
                    configured = keywords.isNotEmpty(),
                    fields = linkedMapOf(
                        "itemCount" to JsonPrimitive(keywords.size),
                        "details" to JsonObject(mapOf("keywords" to keywords)),
                    ),
                ),
                section(
                    id = "block-schedules",
                    name = "Block Schedules",
                    configured = schedules.isNotEmpty(),
                    fields = linkedMapOf(
                        "itemCount" to JsonPrimitive(schedules.size),
                        "details" to JsonObject(mapOf("windows" to schedules)),
                    ),
                ),
                section(
                    id = "defense",
                    name = "Defense",
                    configured = blockPresets.isNotEmpty(),
                    fields = linkedMapOf(
                        "itemCount" to JsonPrimitive(blockPresets.size),
                        "details" to JsonObject(
                            linkedMapOf(
                                "blockPresets" to blockPresets,
                                "overlayQuotes" to settings.array("overlayQuotes"),
                                "overlayWallpaper" to JsonPrimitive(""),
                            ),
                        ),
                    ),
                ),
            ),
        )
    }

    private fun section(
        id: String,
        name: String,
        configured: Boolean,
        fields: LinkedHashMap<String, JsonElement>,
    ): JsonObject = JsonObject(
        linkedMapOf<String, JsonElement>(
            "id" to JsonPrimitive(id),
            "name" to JsonPrimitive(name),
            "configured" to JsonPrimitive(configured),
        ).apply { putAll(fields) },
    )

    private fun JsonObject.array(key: String): JsonArray =
        this[key] as? JsonArray ?: JsonArray(emptyList())

    const val ENVELOPE_KIND = "FocusFlowBackupV1"
}