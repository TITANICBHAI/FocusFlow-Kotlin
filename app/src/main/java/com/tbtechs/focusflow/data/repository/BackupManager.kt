package com.tbtechs.focusflow.data.repository

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.tbtechs.focusflow.data.backup.BackupV1Exporter
import com.tbtechs.focusflow.data.backup.BackupJsonPreflight
import com.tbtechs.focusflow.data.backup.BackupV1ParseResult
import com.tbtechs.focusflow.data.backup.BackupV1Parser
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.json.JSONArray
import org.json.JSONObject
import java.text.DateFormat
import java.time.Clock
import java.time.Instant
import java.util.Date

/**
 * Kotlin/SAF port of backupService.ts.
 *
 * Settings and task payloads remain raw JSON objects on purpose. This prevents
 * a native migration from dropping fields that were added by the React Native
 * app or by a newer app version. The FocusFlowBackupV1 envelope and its field
 * names are part of the user's existing file format.
 */
class BackupManager(
    context: Context,
    private val dataSource: BackupDataSource,
    private val clock: Clock = Clock.systemDefaultZone(),
) {
    private val appContext = context.applicationContext

    suspend fun buildBackupJson(
        settings: JSONObject,
        appVersion: String? = null,
    ): String {
        val tasks = dataSource.getAllTasks().map { task ->
            Json.parseToJsonElement(task.toString()) as? JsonObject
                ?: error("Task export record must be a JSON object.")
        }
        val portableSettings = Json.parseToJsonElement(settings.toString()) as? JsonObject
            ?: error("Export settings must be a JSON object.")
        val now = clock.instant()
        return BackupV1Exporter.buildBackupJson(
            settings = portableSettings,
            tasks = tasks,
            exportedAt = now,
            exportedAtHuman = DateFormat.getDateTimeInstance().format(Date.from(now)),
            appVersion = appVersion,
        )
    }

    /**
     * Builds the ACTION_CREATE_DOCUMENT request. The ActivityResult launcher
     * owns displaying it; this manager only owns the SAF contract and writing.
     */
    fun createExportDocumentIntent(now: Instant = clock.instant()): Intent =
        Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = BACKUP_MIME_TYPE
            putExtra(Intent.EXTRA_TITLE, exportFileName(now))
        }

    /**
     * Writes an already-built backup to the URI returned by
     * ACTION_CREATE_DOCUMENT. The URI may be backed by Downloads, Drive, or
     * another document provider.
     */
    suspend fun exportBackup(
        settings: JSONObject,
        appVersion: String? = null,
        destinationUri: Uri,
    ): BackupFileResult {
        return try {
            val json = buildBackupJson(settings, appVersion)
            appContext.contentResolver.openOutputStream(destinationUri)?.use { output ->
                output.write(json.toByteArray(Charsets.UTF_8))
                output.flush()
            } ?: return BackupFileResult.failure("Could not open destination URI for writing.")
            BackupFileResult.success(destinationUri)
        } catch (error: Exception) {
            BackupFileResult.failure(error.toString())
        }
    }

    fun createImportDocumentIntent(): Intent =
        Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
        }

    suspend fun pickAndImportBackup(
        sourceUri: Uri,
        callbacks: RestoreCallbacks,
        validateBeforeRestore: suspend (BackupEnvelope) -> RestoreResult? = { null },
    ): RestoreResult {
        return try {
            val text = readBackupText(sourceUri)
                ?: return RestoreResult.Error("Could not read the selected backup file.")
            when (val parsed = parseBackupJson(text)) {
                is BackupParseResult.Error -> return parsed.toRestoreError()
                is BackupParseResult.Success -> {
                    validateBeforeRestore(parsed.envelope)?.let { return it }
                }
            }
            restoreFromJson(text, callbacks)
        } catch (error: Exception) {
            RestoreResult.Error("Could not open file picker result: $error")
        }
    }

    suspend fun inspectBackup(sourceUri: Uri): BackupParseResult =
        try {
            readBackupText(sourceUri)?.let(::parseBackupJson)
                ?: BackupParseResult.Error("Could not read the selected backup file.")
        } catch (error: Exception) {
            BackupParseResult.Error("Could not open the selected backup file: $error")
        }

    private fun readBackupText(sourceUri: Uri): String? =
        appContext.contentResolver.openInputStream(sourceUri)
            ?.use { input -> BackupJsonPreflight.readUtf8Bounded(input) }

    fun parseBackupJson(text: String): BackupParseResult {
        return when (val result = BackupV1Parser.parse(text)) {
            is BackupV1ParseResult.Error -> BackupParseResult.Error(result.message)
            is BackupV1ParseResult.Success -> {
                val v1 = result.backup.envelope
                val tasks = JSONArray().apply {
                    v1.tasks.forEach { put(JSONObject(it.wire.toString())) }
                }
                BackupParseResult.Success(
                    BackupEnvelope(
                        raw = JSONObject(v1.raw.toString()),
                        settings = JSONObject(v1.settings.toString()),
                        tasks = tasks,
                        warnings = result.backup.warnings,
                        invalidTaskCount = result.backup.invalidTaskCount,
                    ),
                )
            }
        }
    }

    fun buildRestoreCallbacks(
        inputs: RestoreCallbackInputs,
        replaceTasks: Boolean = false,
        restoreSettings: Boolean = true,
        restoreTasks: Boolean = true,
    ): RestoreCallbacks = RestoreCallbacks(
        updateSettings = inputs.updateSettings,
        addTask = inputs.addTask,
        scheduleTasks = inputs.scheduleTasks,
        deleteTask = inputs.deleteTask,
        refreshTasks = inputs.refreshTasks,
        replaceTasks = replaceTasks,
        currentTasks = inputs.currentTasks,
        currentSettings = inputs.currentSettings,
        currentFocusSessionActive = inputs.currentFocusSessionActive,
        restoreSettings = restoreSettings,
        restoreTasks = restoreTasks,
    )

    suspend fun restoreFromJson(
        text: String,
        callbacks: RestoreCallbacks,
    ): RestoreResult {
        val parsed = parseBackupJson(text)
        if (parsed !is BackupParseResult.Success) return parsed.toRestoreError()
        val envelope = parsed.envelope

        if (callbacks.replaceTasks) {
            val databaseSessionActive = dataSource.hasActiveFocusSession()
            if (callbacks.currentFocusSessionActive || databaseSessionActive) {
                return RestoreResult.Error(
                    "Cannot replace tasks while a Focus Session is running. " +
                        "Stop the current session, then try importing the backup again.",
                )
            }
        }

        val summary = ImportSummary(
            tasksSkipped = envelope.invalidTaskCount,
            warnings = envelope.warnings.toMutableList(),
        )

        try {
            if (callbacks.restoreSettings) {
                callbacks.updateSettings(envelope.settings)
                summary.settings = true
            }
        } catch (error: Exception) {
            summary.warnings += "Settings could not be restored: $error"
        }

        if (!callbacks.restoreTasks) {
            runCatching { callbacks.refreshTasks() }
            return RestoreResult.Success(summary)
        }

        val existingTasks = if (callbacks.restoreTasks) {
            runCatching { dataSource.getAllTasks() }
                .getOrElse { callbacks.currentTasks }
        } else {
            emptyList()
        }

        if (callbacks.restoreTasks && callbacks.replaceTasks) {
            existingTasks.forEach { task ->
                runCatching { callbacks.deleteTask(task.optString("id")) }
            }
        }

        val existingIds = if (!callbacks.restoreTasks || callbacks.replaceTasks) {
            emptySet()
        } else {
            existingTasks.mapNotNull { it.optString("id").takeIf(String::isNotBlank) }.toSet()
        }
        val importedIds = existingIds.toMutableSet()
        val tasksToSchedule = mutableListOf<JSONObject>()

        for (index in 0 until envelope.tasks.length()) {
            val rawTask = envelope.tasks.opt(index)
            if (rawTask !is JSONObject || rawTask.optString("id").isBlank()) {
                summary.tasksSkipped++
                continue
            }

            val taskId = rawTask.optString("id")
            if (!importedIds.add(taskId)) {
                summary.tasksSkipped++
                continue
            }

            try {
                val imported = copyJson(rawTask)
                val isPastScheduledTask =
                    imported.optString("status") == "scheduled" &&
                        parseInstantOrNull(imported.optString("endTime"))?.toEpochMilli()
                            ?.let { it < clock.millis() } == true
                if (isPastScheduledTask) {
                    imported.put("status", "skipped")
                    imported.put("updatedAt", formatIso(clock.instant()))
                }

                callbacks.addTask(imported, true)
                if (imported.optString("status") == "scheduled") {
                    tasksToSchedule += imported
                }
                summary.tasksImported++
            } catch (error: Exception) {
                summary.tasksSkipped++
                summary.warnings +=
                    "Task \"${rawTask.optString("title", taskId)}\" failed: $error"
            }
        }

        if (tasksToSchedule.isNotEmpty()) {
            try {
                callbacks.scheduleTasks(tasksToSchedule)
            } catch (error: Exception) {
                summary.warnings +=
                    "Some imported task reminders could not be scheduled: $error"
            }
        }

        runCatching { callbacks.refreshTasks() }
        return RestoreResult.Success(summary)
    }

    private fun copyJson(value: JSONObject): JSONObject = JSONObject(value.toString())

    private fun parseInstantOrNull(value: String): Instant? =
        runCatching { Instant.parse(value) }
            .getOrElse {
                runCatching { java.time.OffsetDateTime.parse(value).toInstant() }.getOrNull()
            }

    private fun formatIso(value: Instant): String =
        java.time.format.DateTimeFormatterBuilder()
            .appendInstant(3)
            .toFormatter()
            .format(value)

    private fun exportFileName(now: Instant): String {
        val stamp = formatIso(now)
            .replace(":", "-")
            .replace(".", "-")
            .take(19)
        return "focusflow-$stamp$BACKUP_FILE_EXT"
    }

    companion object {
        const val BACKUP_ENVELOPE_KIND = BackupV1Exporter.ENVELOPE_KIND
        const val BACKUP_FILE_EXT = ".focusflow"
        const val BACKUP_MIME_TYPE = "application/octet-stream"
    }
}

interface BackupDataSource {
    suspend fun getAllTasks(): List<JSONObject>
    suspend fun hasActiveFocusSession(): Boolean
}

data class BackupFileResult(
    val ok: Boolean,
    val uri: Uri? = null,
    val error: String? = null,
) {
    companion object {
        fun success(uri: Uri) = BackupFileResult(ok = true, uri = uri)
        fun failure(error: String) = BackupFileResult(ok = false, error = error)
    }
}

data class BackupEnvelope(
    val raw: JSONObject,
    val settings: JSONObject,
    val tasks: JSONArray,
    val warnings: List<String> = emptyList(),
    val invalidTaskCount: Int = 0,
)

sealed class BackupParseResult {
    data class Success(val envelope: BackupEnvelope) : BackupParseResult()
    data class Error(val message: String) : BackupParseResult()

    fun toRestoreError(): RestoreResult.Error = when (this) {
        is Success -> error("A successful parse cannot be converted to an error.")
        is Error -> RestoreResult.Error(message)
    }
}

data class ImportSummary(
    var settings: Boolean = false,
    var tasksImported: Int = 0,
    var tasksSkipped: Int = 0,
    val warnings: MutableList<String> = mutableListOf(),
)

sealed class RestoreResult {
    data class Success(val summary: ImportSummary) : RestoreResult()
    data class Error(
        val message: String,
        val requiresPin: Boolean = false,
    ) : RestoreResult()
}

data class RestoreCallbackInputs(
    val updateSettings: suspend (JSONObject) -> Unit,
    val addTask: suspend (JSONObject, skipAlarms: Boolean) -> Unit,
    val scheduleTasks: suspend (List<JSONObject>) -> Unit,
    val deleteTask: suspend (String) -> Unit,
    val refreshTasks: suspend () -> Unit,
    val currentTasks: List<JSONObject>,
    val currentSettings: JSONObject,
    val currentFocusSessionActive: Boolean = false,
)

data class RestoreCallbacks(
    val updateSettings: suspend (JSONObject) -> Unit,
    val addTask: suspend (JSONObject, skipAlarms: Boolean) -> Unit,
    val scheduleTasks: suspend (List<JSONObject>) -> Unit,
    val deleteTask: suspend (String) -> Unit,
    val refreshTasks: suspend () -> Unit,
    val replaceTasks: Boolean,
    val currentTasks: List<JSONObject>,
    val currentSettings: JSONObject,
    val currentFocusSessionActive: Boolean,
    val restoreSettings: Boolean = true,
    val restoreTasks: Boolean = true,
)
