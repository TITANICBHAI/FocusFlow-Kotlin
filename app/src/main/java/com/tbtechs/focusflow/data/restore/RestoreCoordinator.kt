package com.tbtechs.focusflow.data.restore

import com.tbtechs.focusflow.data.backup.BackupV1ParseResult
import com.tbtechs.focusflow.data.backup.BackupV1Parser
import com.tbtechs.focusflow.data.backup.ParsedBackupV1
import com.tbtechs.focusflow.data.repository.FocusSessionRepository
import com.tbtechs.focusflow.data.repository.TaskRepository
import java.util.UUID
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class PendingBackup(
    val record: PendingImportRecord,
    val parsed: ParsedBackupV1,
)

sealed interface PendingBackupResult {
    data object Missing : PendingBackupResult
    data class Ready(val backup: PendingBackup) : PendingBackupResult
    data class Error(val message: String) : PendingBackupResult
}

data class RestorePreview(
    val tasksInFile: Int,
    val newTasks: Int,
    val identicalDuplicates: Int,
    val totalConflicts: Int,
    val conflictingTasks: List<RestoreConflict>,
    val invalidTasks: Int,
    val pastScheduledToSkipped: Int,
    val settingsSectionsPresent: Int,
    val externalResourcesUnresolved: Int,
    val warnings: List<String>,
) {
    val hasConflicts: Boolean get() = conflictingTasks.isNotEmpty()
}

sealed interface RestoreAdmissionResult {
    data object Busy : RestoreAdmissionResult
    data object ActiveFocusSession : RestoreAdmissionResult
    data object PinRequired : RestoreAdmissionResult
    data class IdConflict(val tasks: List<RestoreConflict>) : RestoreAdmissionResult
    data class FailedBeforeJournal(val message: String) : RestoreAdmissionResult
    data class Finished(val counts: RestoreCounts, val interrupted: Boolean) : RestoreAdmissionResult
    data class RecoveryBlocked(val message: String, val unreadable: Boolean) : RestoreAdmissionResult
}

/**
 * Owns PendingImport admission and restore planning. The journal engine is
 * launched in the application scope, so Activity/ViewModel cancellation never
 * cancels an admitted restore.
 */
class RestoreCoordinator(
    private val gate: RestoreGate,
    private val pendingStore: PendingImportStore,
    private val journalStore: RestoreJournalStore,
    private val taskRepository: TaskRepository,
    private val focusSessionRepository: FocusSessionRepository,
    private val recoveryEngine: RestoreRecoveryEngine,
    private val applicationScope: CoroutineScope,
    private val isRuntimeFocusActive: suspend () -> Boolean = { false },
) {
    private val admissionMutex = Mutex()
    val state = recoveryEngine.state

    fun hasPendingImport(): Boolean =
        gate.state.value == RestoreGate.State.OPEN &&
            !journalStore.hasJournal() &&
            !journalStore.hasQuarantine() &&
            pendingStore.exists()

    suspend fun stage(parsed: ParsedBackupV1, displayName: String): Result<Unit> =
        admissionMutex.withLock {
            if (
                gate.state.value != RestoreGate.State.OPEN ||
                journalStore.hasJournal() ||
                journalStore.hasQuarantine()
            ) {
                return@withLock Result.failure(IllegalStateException("A restore is already running."))
            }
            runCatching {
                pendingStore.write(
                    PendingImportRecord(
                        displayName = displayName.take(256).ifBlank { "FocusFlow backup" },
                        normalizedBackupJson = BackupV1Parser.normalizedJson(parsed),
                        warnings = parsed.warnings,
                        invalidTaskCount = parsed.invalidTaskCount,
                    ),
                )
            }
        }

    suspend fun loadPending(): PendingBackupResult {
        return when (val stored = pendingStore.read()) {
            PendingImportRead.Missing -> PendingBackupResult.Missing
            is PendingImportRead.Error -> PendingBackupResult.Error(stored.message)
            is PendingImportRead.Value -> when (
                val parsed = BackupV1Parser.parse(stored.record.normalizedBackupJson)
            ) {
                is BackupV1ParseResult.Error -> PendingBackupResult.Error(parsed.message)
                is BackupV1ParseResult.Success -> PendingBackupResult.Ready(
                    PendingBackup(
                        record = stored.record,
                        parsed = parsed.backup.copy(
                            warnings = stored.record.warnings,
                            invalidTaskCount = stored.record.invalidTaskCount,
                        ),
                    ),
                )
            }
        }
    }

    suspend fun cancelPending(): Result<Unit> = admissionMutex.withLock {
        if (
            gate.state.value != RestoreGate.State.OPEN ||
            journalStore.hasJournal() ||
            journalStore.hasQuarantine()
        ) {
            return@withLock Result.failure(IllegalStateException("The active restore cannot be cancelled here."))
        }
        runCatching { pendingStore.delete() }
    }

    suspend fun preview(
        mode: RestoreMode,
        restoreSettings: Boolean,
        restoreTasks: Boolean,
    ): Result<RestorePreview> {
        val loaded = loadPending()
        if (loaded !is PendingBackupResult.Ready) {
            val message = (loaded as? PendingBackupResult.Error)?.message
                ?: "There is no saved import to preview."
            return Result.failure(IllegalStateException(message))
        }
        val backup = loaded.backup.parsed
        val local = taskRepository.getAllTasks()
        val allConflicts = if (mode == RestoreMode.MERGE && restoreTasks) {
            RestorePlanBuilder.conflicts(backup.envelope, local)
        } else {
            emptyList()
        }
        val conflicts = allConflicts.take(20)
        val localIds = local.mapTo(mutableSetOf()) { it.id }
        val duplicates = if (mode == RestoreMode.MERGE && restoreTasks) {
            backup.envelope.tasks.count { it.id in localIds } - allConflicts.size
        } else {
            0
        }.coerceAtLeast(0)
        val newTasks = if (!restoreTasks) 0 else when (mode) {
            RestoreMode.REPLACE -> backup.envelope.tasks.size
            RestoreMode.MERGE -> (backup.envelope.tasks.size - duplicates - conflicts.size).coerceAtLeast(0)
        }
        val now = System.currentTimeMillis()
        val past = if (!restoreTasks) 0 else backup.envelope.tasks.count { task ->
            (mode == RestoreMode.REPLACE || task.id !in localIds) &&
                (task.status == "scheduled" || task.status == "active") &&
                runCatching { java.time.Instant.parse(task.endTime).toEpochMilli() <= now }
                    .getOrDefault(false)
        }
        val rawSettings = backup.envelope.raw["settings"] as? kotlinx.serialization.json.JsonObject
        val unresolved = listOf("launcherWallpaperUri", "overlayWallpaper")
            .count { key -> rawSettings?.get(key)?.let { it !is kotlinx.serialization.json.JsonNull } == true }
        return Result.success(
            RestorePreview(
                tasksInFile = backup.envelope.tasks.size + backup.invalidTaskCount,
                newTasks = newTasks,
                identicalDuplicates = duplicates,
                totalConflicts = allConflicts.size,
                conflictingTasks = conflicts,
                invalidTasks = backup.invalidTaskCount,
                pastScheduledToSkipped = past,
                settingsSectionsPresent = if (restoreSettings) backup.envelope.settings.size else 0,
                externalResourcesUnresolved = unresolved,
                warnings = backup.warnings,
            ),
        )
    }

    suspend fun begin(
        mode: RestoreMode,
        restoreSettings: Boolean,
        restoreTasks: Boolean,
        runtimeFocusActive: Boolean,
        verifyPin: suspend (ParsedBackupV1) -> Boolean,
    ): RestoreAdmissionResult {
        if (!restoreSettings && !restoreTasks) {
            return RestoreAdmissionResult.FailedBeforeJournal("Select at least one section to import.")
        }

        lateinit var completion: CompletableDeferred<RecoveryRunResult>
        val journal = admissionMutex.withLock {
            if (!gate.tryBeginRestore()) return@withLock null

            try {
                val loaded = when (val pending = loadPending()) {
                    is PendingBackupResult.Ready -> pending.backup
                    is PendingBackupResult.Error -> {
                        gate.reopen()
                        return@withLock RestoreAdmissionResult.FailedBeforeJournal(pending.message)
                    }
                    PendingBackupResult.Missing -> {
                        gate.reopen()
                        return@withLock RestoreAdmissionResult.FailedBeforeJournal(
                            "The saved import is no longer available.",
                        )
                    }
                }

                val activeSession = runtimeFocusActive ||
                    isRuntimeFocusActive() ||
                    focusSessionRepository.getActiveFocusSession()?.isActive == true
                if (mode == RestoreMode.REPLACE && restoreTasks && activeSession) {
                    gate.reopen()
                    return@withLock RestoreAdmissionResult.ActiveFocusSession
                }
                if (restoreSettings && !verifyPin(loaded.parsed)) {
                    gate.reopen()
                    return@withLock RestoreAdmissionResult.PinRequired
                }

                val localTasks = taskRepository.getAllTasks()
                val backup = loaded.parsed
                val warnings = buildList {
                    if (backup.warnings.isNotEmpty()) add("BACKUP_VALIDATION_WARNINGS")
                    if (backup.invalidTaskCount > 0) add("INVALID_TASKS_SKIPPED")
                }
                val rawSettings =
                    backup.envelope.raw["settings"] as? kotlinx.serialization.json.JsonObject
                val unresolved = listOf("launcherWallpaperUri", "overlayWallpaper")
                    .count { key ->
                        rawSettings?.get(key)?.let {
                            it !is kotlinx.serialization.json.JsonNull
                        } == true
                    }
                val planResult = RestorePlanBuilder.build(
                    backup = backup.envelope,
                    localTasks = localTasks,
                    mode = mode,
                    planNowMillis = System.currentTimeMillis(),
                    restoreSettings = restoreSettings,
                    restoreTasks = restoreTasks,
                    activeSession = activeSession,
                    warningCodes = warnings,
                    invalidTaskCount = backup.invalidTaskCount,
                    unresolvedExternalResources = unresolved,
                )
                when (planResult) {
                    is RestorePlanResult.Failure -> {
                        gate.reopen()
                        return@withLock when (val failure = planResult.failure) {
                            is RestorePlanFailure.IdConflict ->
                                RestoreAdmissionResult.IdConflict(failure.conflictingTasks.take(20))
                            RestorePlanFailure.ActiveSession ->
                                RestoreAdmissionResult.ActiveFocusSession
                        }
                    }
                    is RestorePlanResult.Success -> {
                        val next = RestoreJournal.fromPlan(
                            sessionId = UUID.randomUUID().toString(),
                            plan = planResult.plan,
                        )
                        journalStore.write(next)
                        runCatching { pendingStore.delete() }
                        next
                    }
                }
            } catch (error: Exception) {
                if (gate.state.value != RestoreGate.State.OPEN) gate.reopen()
                return@withLock RestoreAdmissionResult.FailedBeforeJournal(
                    error.message ?: "Restore could not be prepared.",
                )
            }
        }

        when (journal) {
            null -> return RestoreAdmissionResult.Busy
            is RestoreAdmissionResult -> return journal
            is RestoreJournal -> Unit
        }

        completion = CompletableDeferred()
        applicationScope.launch(Dispatchers.IO) {
            completion.complete(recoveryEngine.runAlreadyClosedGate(interrupted = false))
        }
        return when (val result = completion.await()) {
            is RecoveryRunResult.Completed ->
                RestoreAdmissionResult.Finished(result.journal.counts, result.afterInterruption)
            is RecoveryRunResult.Blocked ->
                RestoreAdmissionResult.RecoveryBlocked(result.message, result.unreadableJournal)
            is RecoveryRunResult.NoJournal ->
                RestoreAdmissionResult.FailedBeforeJournal("The restore journal disappeared.")
        }
    }

    fun startStartupRecovery() {
        when (gate.state.value) {
            RestoreGate.State.RECOVERING -> applicationScope.launch(Dispatchers.IO) {
                recoveryEngine.runAlreadyClosedGate(interrupted = true)
            }
            RestoreGate.State.RECOVERY_BLOCKED -> recoveryEngine.reflectBlockedStartup(
                journalStore.hasQuarantine(),
            )
            else -> Unit
        }
    }

    suspend fun retryRecovery(): RecoveryRunResult = recoveryEngine.retry()

    suspend fun discardRecovery(): Result<Unit> = recoveryEngine.discard()
}