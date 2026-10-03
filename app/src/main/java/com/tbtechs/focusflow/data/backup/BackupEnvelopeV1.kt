package com.tbtechs.focusflow.data.backup

import com.tbtechs.focusflow.data.model.CanonicalTimestamp
import com.tbtechs.focusflow.data.model.Reminder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.longOrNull
import java.io.InputStream
import java.time.Instant
import java.time.OffsetDateTime

data class BackupTaskV1(
    val id: String,
    val title: String,
    val description: String,
    val startTime: String,
    val endTime: String,
    val durationMinutes: Int,
    val status: String,
    val priority: String,
    val tags: List<String>,
    val reminders: List<Reminder>,
    val color: String,
    val focusMode: Boolean,
    /** Null preserves either an absent or explicit JSON null wire value. */
    val focusAllowedPackages: List<String>?,
    val focusAllowedPackagesPresent: Boolean,
    /** Normalized object; unknown future fields remain available to the importer. */
    val wire: JsonObject,
)

data class BackupEnvelopeV1(
    val kind: String,
    val version: Int,
    val exportedAt: String?,
    val appVersion: String?,
    val platform: JsonObject?,
    /** Only validated APPLY settings are retained here. */
    val settings: JsonObject,
    val tasks: List<BackupTaskV1>,
    val presetSections: JsonArray?,
    val summary: JsonObject?,
    val raw: JsonObject,
)

data class ParsedBackupV1(
    val envelope: BackupEnvelopeV1,
    val warnings: List<String>,
    val invalidTaskCount: Int,
)

sealed class BackupV1ParseResult {
    data class Success(val backup: ParsedBackupV1) : BackupV1ParseResult()
    data class Error(val message: String) : BackupV1ParseResult()
}

/**
 * Bounded V1 envelope and task validation. The JSON syntax preflight runs
 * before Json.parseToJsonElement so duplicate keys and structural limits are
 * enforced before a tree is allocated.
 */
object BackupV1Parser {
    const val ENVELOPE_KIND = "FocusFlowBackupV1"
    private val json = Json { isLenient = false }

    fun parse(input: InputStream, now: Instant = Instant.now()): BackupV1ParseResult =
        try {
            parse(BackupJsonPreflight.readUtf8Bounded(input), now)
        } catch (error: BackupJsonFormatException) {
            BackupV1ParseResult.Error(error.message ?: "Backup exceeds a format limit.")
        } catch (_: Exception) {
            BackupV1ParseResult.Error("Backup could not be read.")
        }

    fun parse(text: String, now: Instant = Instant.now()): BackupV1ParseResult {
        return try {
            val normalizedText = BackupJsonPreflight.validateAndStripBom(text)
            val root = try {
                json.parseToJsonElement(normalizedText) as? JsonObject
            } catch (_: Exception) {
                null
            } ?: return BackupV1ParseResult.Error("Backup must be a valid JSON object.")

            val kind = root["kind"]?.stringValue()
            if (kind != ENVELOPE_KIND) {
                return BackupV1ParseResult.Error(
                    "Unsupported format (expected \"$ENVELOPE_KIND\").",
                )
            }
            val version = if (root.containsKey("version")) {
                root["version"]?.intValue()
                    ?: return BackupV1ParseResult.Error("Backup version must be the integer 1.")
            } else {
                1
            }
            if (version != 1) {
                return BackupV1ParseResult.Error(
                    "Unsupported backup version $version. This app can import version 1 files only.",
                )
            }
            val rawSettings = root["settings"] as? JsonObject
                ?: return BackupV1ParseResult.Error(
                    "Backup is missing settings — the file may be corrupted.",
                )
            val rawTasks = root["tasks"] as? JsonArray
                ?: return BackupV1ParseResult.Error("Backup is missing task data.")
            if (rawTasks.size > BackupJsonLimits.MAX_TASKS) {
                return BackupV1ParseResult.Error("Backup exceeds the maximum task count.")
            }

            ensureUniqueTaskIds(rawTasks)

            val normalizedSettings = TsSettingsAdapter.normalizeForImport(rawSettings)
            normalizedSettings.fatalError?.let { return BackupV1ParseResult.Error(it) }

            val taskWarnings = mutableListOf<String>()
            var invalidTasks = 0
            val validTasks = rawTasks.mapNotNull { rawTask ->
                val task = (rawTask as? JsonObject)?.let {
                    normalizeTask(it, now, taskWarnings)
                }
                if (task == null) invalidTasks++
                task
            }

            val envelope = BackupEnvelopeV1(
                kind = kind,
                version = version,
                exportedAt = root["exportedAt"]?.stringValue(),
                appVersion = root["appVersion"]?.stringValue(),
                platform = root["platform"] as? JsonObject,
                settings = normalizedSettings.settings,
                tasks = validTasks,
                presetSections = root["presetSections"] as? JsonArray,
                summary = root["summary"] as? JsonObject,
                raw = root,
            )
            val warnings = buildList {
                addAll(normalizedSettings.warnings)
                addAll(taskWarnings)
                if (invalidTasks > 0) {
                    add("Tasks: skipped $invalidTasks invalid task record(s).")
                }
            }
            BackupV1ParseResult.Success(
                ParsedBackupV1(envelope, warnings, invalidTasks),
            )
        } catch (error: BackupJsonFormatException) {
            BackupV1ParseResult.Error(error.message ?: "Backup exceeds a format limit.")
        } catch (_: Exception) {
            BackupV1ParseResult.Error("Backup could not be parsed.")
        }
    }

    /** Serializes exactly the validated, normalized V1 content for durable confirmation. */
    fun normalizedJson(backup: ParsedBackupV1): String {
        val envelope = backup.envelope
        val normalized = envelope.raw.toMutableMap().apply {
            put("settings", envelope.settings)
            put("tasks", JsonArray(envelope.tasks.map(BackupTaskV1::wire)))
        }
        return JsonObject(normalized).toString()
    }

    private fun ensureUniqueTaskIds(tasks: JsonArray) {
        val ids = HashSet<String>()
        tasks.forEach { value ->
            val id = (value as? JsonObject)?.get("id")?.stringValue() ?: return@forEach
            if (!ids.add(id)) {
                throw BackupJsonFormatException(
                    "Backup contains duplicate task IDs and cannot be imported safely.",
                )
            }
        }
    }

    private fun normalizeTask(
        source: JsonObject,
        now: Instant,
        warnings: MutableList<String>,
    ): BackupTaskV1? {
        val id = source["id"]?.stringValue()
            ?.takeIf { it.isNotEmpty() && it.length <= BackupJsonLimits.MAX_ID_CHARS && !it.hasControlChars() }
            ?: return null
        val title = source["title"]?.stringValue()
            ?.takeIf { it.isNotBlank() && it.length <= BackupJsonLimits.MAX_TITLE_CHARS }
            ?: return null
        val description = when (val raw = source["description"]) {
            null, JsonNull -> ""
            else -> raw.stringValue()?.takeIf { it.length <= BackupJsonLimits.MAX_STRING_CHARS }
                ?: return null
        }

        val startInstant = source["startTime"]?.stringValue()?.parseIsoInstant() ?: return null
        val endInstant = source["endTime"]?.stringValue()?.parseIsoInstant() ?: return null
        val createdInstant = source["createdAt"]?.stringValue()?.parseIsoInstant() ?: return null
        val updatedInstant = source["updatedAt"]?.stringValue()?.parseIsoInstant() ?: return null
        if (endInstant < startInstant) return null

        val duration = source["durationMinutes"]?.intValue()
            ?.takeIf { it in 0..100_000 }
            ?: return null
        val status = source["status"]?.stringValue()
            ?.takeIf { it in setOf("scheduled", "active", "completed", "skipped", "overdue") }
            ?: return null
        val priority = source["priority"]?.stringValue()
            ?.takeIf { it in setOf("low", "medium", "high", "critical") }
            ?: return null

        val tagsValue = source["tags"]
        val tags = when (tagsValue) {
            null -> emptyList()
            is JsonArray -> {
                if (tagsValue.size > BackupJsonLimits.MAX_TAGS_PER_TASK) {
                    throw BackupJsonFormatException("A task exceeds the maximum tag count.")
                }
                val parsedTags = tagsValue.map { it.stringValue() ?: return null }
                if (parsedTags.any { it.length > BackupJsonLimits.MAX_TAG_CHARS }) return null
                parsedTags
            }
            else -> return null
        }

        val remindersValue = source["reminders"]
        if (remindersValue != null && remindersValue !is JsonArray) {
            warnings += "Task reminders: invalid field was replaced with an empty list."
        }
        val remindersArray = remindersValue as? JsonArray ?: JsonArray(emptyList())
        if (remindersArray.size > BackupJsonLimits.MAX_REMINDERS_PER_TASK) {
            throw BackupJsonFormatException("A task exceeds the maximum reminder count.")
        }
        var invalidReminderCount = 0
        val reminders = remindersArray.mapNotNull { value ->
            decodeReminder(value, id).also { if (it == null) invalidReminderCount++ }
        }
        if (invalidReminderCount > 0) {
            warnings += "Task reminders: dropped $invalidReminderCount invalid item(s)."
        }

        val color = when {
            !source.containsKey("color") -> "#6366f1"
            else -> source["color"]?.stringValue()
                ?.takeIf { it.length <= 32 }
                ?: return null
        }
        val focusMode = when {
            !source.containsKey("focusMode") -> false
            else -> source["focusMode"]?.booleanValue() ?: return null
        }
        val packagesPresent = source.containsKey("focusAllowedPackages")
        val packagesElement = source["focusAllowedPackages"]
        val focusAllowedPackages = when {
            !packagesPresent || packagesElement == JsonNull -> null
            packagesElement !is JsonArray -> return null
            packagesElement.size > BackupJsonLimits.MAX_PACKAGES_PER_LIST ->
                throw BackupJsonFormatException("A task exceeds the maximum package-list size.")
            else -> {
                var dropped = 0
                val validPackages = packagesElement.mapNotNull { value ->
                    val packageName = value.stringValue()
                    if (packageName != null &&
                        packageName.length <= BackupJsonLimits.MAX_PACKAGE_CHARS &&
                        packageName.matches(PACKAGE_REGEX)
                    ) {
                        packageName
                    } else {
                        dropped++
                        null
                    }
                }
                if (dropped > 0) {
                    warnings += "Task focus packages: dropped $dropped invalid item(s)."
                }
                validPackages
            }
        }

        val normalized = source.toMutableMap()
        normalized["id"] = JsonPrimitive(id)
        normalized["title"] = JsonPrimitive(title)
        normalized["description"] = JsonPrimitive(description)
        normalized["startTime"] = JsonPrimitive(CanonicalTimestamp.format(startInstant))
        normalized["endTime"] = JsonPrimitive(CanonicalTimestamp.format(endInstant))
        normalized["createdAt"] = JsonPrimitive(CanonicalTimestamp.format(createdInstant))
        normalized["updatedAt"] = JsonPrimitive(CanonicalTimestamp.format(updatedInstant))
        normalized["durationMinutes"] = JsonPrimitive(duration)
        normalized["status"] = JsonPrimitive(status)
        normalized["priority"] = JsonPrimitive(priority)
        normalized["tags"] = JsonArray(tags.map(::JsonPrimitive))
        normalized["reminders"] = JsonArray(remindersArray.mapNotNull { raw ->
            normalizeReminder(raw, id)
        })
        normalized["color"] = JsonPrimitive(color)
        normalized["focusMode"] = JsonPrimitive(focusMode)
        if (packagesPresent && packagesElement == JsonNull) {
            normalized["focusAllowedPackages"] = JsonNull
        } else if (packagesPresent) {
            normalized["focusAllowedPackages"] =
                JsonArray(focusAllowedPackages.orEmpty().map(::JsonPrimitive))
        } else {
            normalized.remove("focusAllowedPackages")
        }

        return BackupTaskV1(
            id = id,
            title = title,
            description = description,
            startTime = CanonicalTimestamp.format(startInstant),
            endTime = CanonicalTimestamp.format(endInstant),
            durationMinutes = duration,
            status = status,
            priority = priority,
            tags = tags,
            reminders = reminders,
            color = color,
            focusMode = focusMode,
            focusAllowedPackages = focusAllowedPackages,
            focusAllowedPackagesPresent = packagesPresent,
            wire = JsonObject(normalized),
        )
    }

    private fun decodeReminder(value: JsonElement, taskId: String): Reminder? {
        val item = value as? JsonObject ?: return null
        val id = item["id"]?.stringValue()
            ?.takeIf { it.isNotBlank() && it.length <= BackupJsonLimits.MAX_ID_CHARS }
            ?: return null
        val storedTaskId = item["taskId"]?.stringValue()
            ?.takeIf { it.isNotBlank() && it.length <= BackupJsonLimits.MAX_ID_CHARS }
            ?: return null
        val offset = item["offsetMinutes"]?.intValue() ?: return null
        val type = item["type"]?.stringValue()
            ?.takeIf { it in setOf("pre-start", "at-start", "post-start") }
            ?: return null
        val notificationId = when (val raw = item["notifId"]) {
            null, JsonNull -> null
            else -> raw.stringValue()?.takeIf { it.length <= BackupJsonLimits.MAX_ID_CHARS }
                ?: return null
        }
        return Reminder(id, storedTaskId, offset, type, notificationId)
    }

    private fun normalizeReminder(value: JsonElement, taskId: String): JsonElement? {
        val reminder = decodeReminder(value, taskId) ?: return null
        val original = value as JsonObject
        val normalized = original.toMutableMap()
        normalized["id"] = JsonPrimitive(reminder.id)
        normalized["taskId"] = JsonPrimitive(reminder.taskId)
        normalized["offsetMinutes"] = JsonPrimitive(reminder.offsetMinutes)
        normalized["type"] = JsonPrimitive(reminder.type)
        if (reminder.notifId == null) normalized.remove("notifId")
        else normalized["notifId"] = JsonPrimitive(reminder.notifId)
        return JsonObject(normalized)
    }

    private fun String.parseIsoInstant(): Instant? =
        runCatching { OffsetDateTime.parse(this).toInstant() }.getOrNull()

    private fun String.hasControlChars(): Boolean = any { it.code < 0x20 || it.code == 0x7f }

    private fun JsonElement.stringValue(): String? {
        val primitive = this as? JsonPrimitive ?: return null
        return primitive.content.takeIf { primitive.isString }
    }

    private fun JsonElement.intValue(): Int? {
        val primitive = this as? JsonPrimitive ?: return null
        if (primitive.isString) return null
        return primitive.intOrNull
    }

    private fun JsonElement.booleanValue(): Boolean? {
        val primitive = this as? JsonPrimitive ?: return null
        if (primitive.isString) return null
        return primitive.booleanOrNull
    }

    private fun JsonElement.longValue(): Long? {
        val primitive = this as? JsonPrimitive ?: return null
        if (primitive.isString) return null
        return primitive.longOrNull
    }

    private val PACKAGE_REGEX = Regex("^[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+$")
}
