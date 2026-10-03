package com.tbtechs.focusflow.ui.backup

import android.content.Context
import android.net.Uri
import com.tbtechs.focusflow.data.backup.TsSettingsAdapter
import com.tbtechs.focusflow.data.model.AppSettings
import com.tbtechs.focusflow.data.repository.BackupDataSource
import com.tbtechs.focusflow.data.repository.BackupFileResult
import com.tbtechs.focusflow.data.repository.BackupManager
import com.tbtechs.focusflow.data.repository.BackupParseResult
import com.tbtechs.focusflow.data.repository.FocusSessionRepository
import com.tbtechs.focusflow.data.repository.RestoreCallbackInputs
import com.tbtechs.focusflow.data.repository.RestoreResult
import com.tbtechs.focusflow.data.repository.SetupPersistenceManager
import com.tbtechs.focusflow.data.repository.SettingsRepository
import com.tbtechs.focusflow.data.repository.TaskRepository
import com.tbtechs.focusflow.data.repository.VpnRepository
import com.tbtechs.focusflow.ui.SettingsViewModel
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.json.JSONObject

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
) {
    private val vpnRepository = VpnRepository(context.applicationContext)
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

    suspend fun export(settings: AppSettings, destination: Uri): BackupFileResult =
        manager.exportBackup(
            settings = settings.toBackupJson(),
            destinationUri = destination,
        )

    suspend fun import(
        source: Uri,
        replaceTasks: Boolean,
        currentSettings: AppSettings,
        currentFocusActive: Boolean,
        restoreSettings: Boolean = true,
        restoreTasks: Boolean = true,
        defensePin: String? = null,
    ): RestoreResult {
        val currentTasks = taskRepository.getAllTasks()
        val callbacks = RestoreCallbackInputs(
            updateSettings = { imported ->
                val importedSettings = Json.parseToJsonElement(imported.toString()) as? JsonObject
                    ?: error("Validated backup settings are not a JSON object.")
                val applied = TsSettingsAdapter.applyToSettings(
                    current = currentSettings,
                    input = importedSettings,
                    currentUserProfileJson = settingsRepository.getString("user_profile"),
                )
                settingsViewModel.updateSettings(applied.settings)
                applied.userProfileJson?.let {
                    settingsRepository.putString("user_profile", it)
                }
                applied.protectionMode?.let {
                    settingsRepository.putString(SetupPersistenceManager.KEY_PROTECTION_MODE, it)
                }
            },
            addTask = { rawTask, _ ->
                val task = taskRepository.taskFromBackupJson(rawTask)
                    ?: error("Backup task ${rawTask.optString("id")} is invalid.")
                taskRepository.insertTask(task)
            },
            scheduleTasks = { /* Imported reminders remain persisted with their tasks. */ },
            deleteTask = taskRepository::deleteTask,
            refreshTasks = { /* Room's observeAllTasks flow refreshes automatically. */ },
            currentTasks = currentTasks.map(taskRepository::taskToBackupJson),
            currentSettings = currentSettings.toBackupJson(),
            currentFocusSessionActive = currentFocusActive,
        )

        return manager.pickAndImportBackup(
            sourceUri = source,
            callbacks = manager.buildRestoreCallbacks(
                inputs = callbacks,
                replaceTasks = replaceTasks && restoreTasks,
                restoreSettings = restoreSettings,
                restoreTasks = restoreTasks,
            ),
            validateBeforeRestore = { envelope ->
                if (
                    restoreSettings &&
                    requiresDefensePin(envelope) &&
                    !settingsViewModel.verifyPin(defensePin.orEmpty())
                ) {
                    RestoreResult.Error(
                        message = "Incorrect Defense PIN. No data was changed.",
                        requiresPin = true,
                    )
                } else {
                    null
                }
            },
        )
    }

    suspend fun inspect(source: Uri): BackupParseResult = manager.inspectBackup(source)

    suspend fun requiresDefensePin(
        source: Uri,
        restoreSettings: Boolean,
    ): Boolean {
        if (!restoreSettings) return false
        val inspected = manager.inspectBackup(source)
        if (inspected !is BackupParseResult.Success) return false
        return requiresDefensePin(inspected.envelope)
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
}
