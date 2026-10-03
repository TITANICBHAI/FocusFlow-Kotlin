package com.tbtechs.focusflow.ui.backup

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.tbtechs.focusflow.data.backup.BackupV1ParseResult
import com.tbtechs.focusflow.data.backup.ParsedBackupV1
import com.tbtechs.focusflow.data.backup.TsSettingsAdapter
import com.tbtechs.focusflow.data.model.AppSettings
import com.tbtechs.focusflow.data.repository.BackupDataSource
import com.tbtechs.focusflow.data.repository.BackupFileResult
import com.tbtechs.focusflow.data.repository.BackupManager
import com.tbtechs.focusflow.data.repository.BackupEnvelope
import com.tbtechs.focusflow.data.repository.BackupParseResult
import com.tbtechs.focusflow.data.repository.FocusSessionRepository
import com.tbtechs.focusflow.data.repository.RestoreResult
import com.tbtechs.focusflow.data.repository.SettingsRepository
import com.tbtechs.focusflow.data.repository.SetupPersistenceManager
import com.tbtechs.focusflow.data.repository.TaskRepository
import com.tbtechs.focusflow.data.repository.VpnRepository
import com.tbtechs.focusflow.data.restore.PendingBackupResult
import com.tbtechs.focusflow.data.restore.RestoreAdmissionResult
import com.tbtechs.focusflow.data.restore.RestoreCoordinator
import com.tbtechs.focusflow.data.restore.RestoreMode
import com.tbtechs.focusflow.ui.SettingsViewModel
import org.json.JSONObject
import org.json.JSONArray

/**
 * Activity-hosted bridge for the SAF backup contract.
 *
 * The Activity owns document launchers; this class owns the portable JSON
 * mapping and repository callbacks so Settings/Profile do not each implement a
 * slightly different restore path.
 */
class BackupCoordinator(
    context: Context,
    private val taskRepository: TaskRepository,
    private val focusSessionRepository: FocusSessionRepository,
    private val settingsRepository: SettingsRepository,
    private val settingsViewModel: SettingsViewModel,
    private val restoreCoordinator: RestoreCoordinator,
    restoreGate: com.tbtechs.focusflow.data.restore.RestoreGate,
) {
    private val appContext = context.applicationContext
    private val vpnRepository = VpnRepository(appContext, restoreGate)
    private val manager = BackupManager(
        context = context,
        dataSource = object : BackupDataSource {
            override suspend fun getAllTasks(): List<JSONObject> =
                taskRepository.getAllTasks().map(taskRepository::taskToBackupJson)

            override suspend fun hasActiveFocusSession(): Boolean =
                focusSessionRepository.getActiveFocusSession()?.isActive == true
        },
    )

    fun createExportIntent() = manager.createExportDocumentIntent()

    fun createImportIntent() = manager.createImportDocumentIntent()

    fun restorePendingAvailable(): Boolean = restoreCoordinator.hasPendingImport()

    suspend fun export(settings: AppSettings, destination: Uri): BackupFileResult =
        manager.exportBackup(
            settings = settings.toBackupJson(),
            destinationUri = destination,
        )

    suspend fun stageImport(source: Uri): Result<Unit> {
        return when (val parsed = manager.readBackupV1(source)) {
            is BackupV1ParseResult.Error -> Result.failure(IllegalArgumentException(parsed.message))
            is BackupV1ParseResult.Success -> restoreCoordinator.stage(
                parsed.backup,
                displayName = displayName(source),
            )
        }
    }

    suspend fun inspectPending(): BackupParseResult {
        return when (val loaded = restoreCoordinator.loadPending()) {
            PendingBackupResult.Missing ->
                BackupParseResult.Error("There is no saved import to confirm.")
            is PendingBackupResult.Error -> BackupParseResult.Error(loaded.message)
            is PendingBackupResult.Ready -> BackupParseResult.Success(
                loaded.backup.toBackupEnvelope(),
            )
        }
    }

    suspend fun preview(
        replaceTasks: Boolean,
        restoreSettings: Boolean,
        restoreTasks: Boolean,
    ) = restoreCoordinator.preview(
        mode = if (replaceTasks) RestoreMode.REPLACE else RestoreMode.MERGE,
        restoreSettings = restoreSettings,
        restoreTasks = restoreTasks,
    )

    suspend fun cancelPendingImport(): Result<Unit> = restoreCoordinator.cancelPending()

    suspend fun requiresDefensePin(restoreSettings: Boolean): Boolean {
        if (!restoreSettings) return false
        val parsed = inspectPending()
        if (parsed !is BackupParseResult.Success) return false
        return requiresDefensePin(parsed.envelope)
    }

    suspend fun importPending(
        replaceTasks: Boolean,
        currentFocusActive: Boolean,
        restoreSettings: Boolean = true,
        restoreTasks: Boolean = true,
        defensePin: String? = null,
    ): RestoreResult {
        val loaded = restoreCoordinator.loadPending()
        if (loaded !is PendingBackupResult.Ready) {
            val message = (loaded as? PendingBackupResult.Error)?.message
                ?: "The saved import is no longer available."
            return RestoreResult.Error(message)
        }
        val admission = restoreCoordinator.begin(
            mode = if (replaceTasks) RestoreMode.REPLACE else RestoreMode.MERGE,
            restoreSettings = restoreSettings,
            restoreTasks = restoreTasks,
            runtimeFocusActive = currentFocusActive,
            verifyPin = { parsed ->
                !restoreSettings ||
                    !requiresDefensePin(parsed.toBackupEnvelope()) ||
                    settingsViewModel.verifyPin(defensePin.orEmpty())
            },
        )
        return when (admission) {
            RestoreAdmissionResult.Busy ->
                RestoreResult.Error("A restore is already running.")
            RestoreAdmissionResult.ActiveFocusSession ->
                RestoreResult.Error(
                    "Cannot replace tasks while a Focus Session is running. Stop the session and try again.",
                )
            RestoreAdmissionResult.PinRequired ->
                RestoreResult.Error("Incorrect Defense PIN. No data was changed.", requiresPin = true)
            is RestoreAdmissionResult.IdConflict ->
                RestoreResult.Error(
                    "${admission.tasks.size} tasks already exist with different content. Use Replace to overwrite.",
                )
            is RestoreAdmissionResult.FailedBeforeJournal ->
                RestoreResult.Error(admission.message)
            is RestoreAdmissionResult.RecoveryBlocked ->
                RestoreResult.Error(admission.message)
            is RestoreAdmissionResult.Finished ->
                RestoreResult.Success(
                    com.tbtechs.focusflow.data.repository.ImportSummary(
                        settings = restoreSettings,
                        tasksImported = admission.counts.tasksInserted,
                        tasksSkipped = admission.counts.invalidTasks +
                            admission.counts.identicalDuplicates,
                        warnings = loaded.backup.record.warnings.toMutableList().apply {
                            if (admission.counts.downgradedToSkipped > 0) {
                                add("${admission.counts.downgradedToSkipped} past tasks were marked skipped.")
                            }
                        },
                    ),
                )
        }
    }

    private suspend fun requiresDefensePin(
        envelope: com.tbtechs.focusflow.data.repository.BackupEnvelope,
    ): Boolean {
        val currentSettings = settingsViewModel.settings.value
        if (!currentSettings.pinProtectionEnabled) return false
        val currentVpnPackages = runCatching {
            vpnRepository.getNetworkBlockSettings().packages
        }.getOrElse {
            // If the import contains this protection list but local state cannot
            // be read, fail closed and require the local Defense PIN.
            if (envelope.settings.has("alwaysOnVpnPackages")) return true
            emptyList()
        }
        val (current, imported) = ImportProtectionPolicy.fromSettings(
            current = currentSettings,
            currentAlwaysOnVpnPackages = currentVpnPackages,
            imported = envelope.settings,
        )
        return ImportProtectionPolicy.requiresPin(current, imported)
    }

    private fun AppSettings.toBackupJson(): JSONObject = JSONObject(
        TsSettingsAdapter.toWireSettings(
            settings = this,
            userProfileJson = settingsRepository.getString("user_profile"),
            protectionMode = settingsRepository.getString(SetupPersistenceManager.KEY_PROTECTION_MODE),
        ).toString(),
    )

    private fun PendingBackupResult.Ready.toBackupEnvelope(): BackupEnvelope =
        backup.toBackupEnvelope()

    private fun com.tbtechs.focusflow.data.restore.PendingBackup.toBackupEnvelope(): BackupEnvelope {
        val envelope = parsed.envelope
        return BackupEnvelope(
            raw = JSONObject(envelope.raw.toString()),
            settings = JSONObject(envelope.settings.toString()),
            tasks = JSONArray().apply {
                envelope.tasks.forEach { put(JSONObject(it.wire.toString())) }
            },
            warnings = record.warnings,
            invalidTaskCount = record.invalidTaskCount,
        )
    }

    private fun ParsedBackupV1.toBackupEnvelope(): BackupEnvelope {
        val envelope = this.envelope
        return BackupEnvelope(
            raw = JSONObject(envelope.raw.toString()),
            settings = JSONObject(envelope.settings.toString()),
            tasks = JSONArray().apply {
                envelope.tasks.forEach { put(JSONObject(it.wire.toString())) }
            },
            warnings = warnings,
            invalidTaskCount = invalidTaskCount,
        )
    }

    private fun displayName(uri: Uri): String {
        return runCatching {
            appContext.contentResolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME),
                null,
                null,
                null,
            )?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
        }.getOrNull()?.takeIf(String::isNotBlank) ?: "FocusFlow backup"
    }
}
