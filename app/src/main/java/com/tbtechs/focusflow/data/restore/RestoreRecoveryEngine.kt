package com.tbtechs.focusflow.data.restore

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed interface RestoreUiState {
    data object Idle : RestoreUiState
    data class Running(val interrupted: Boolean) : RestoreUiState
    data class Blocked(
        val message: String,
        val unreadableJournal: Boolean,
    ) : RestoreUiState
    data class Completed(
        val counts: RestoreCounts,
        val afterInterruption: Boolean,
    ) : RestoreUiState
}

interface RestorePhaseActions {
    suspend fun applyTasks(plan: RestorePlan)
    suspend fun applySettings(plan: RestorePlan)
    suspend fun reconcile(plan: RestorePlan)
    suspend fun reconcileCurrentState()
    suspend fun persistLastResult(plan: RestorePlan, afterInterruption: Boolean)
}

sealed interface RecoveryRunResult {
    data class Completed(val journal: RestoreJournal, val afterInterruption: Boolean) :
        RecoveryRunResult
    data class Blocked(val message: String, val unreadableJournal: Boolean) : RecoveryRunResult
    data class NoJournal(val pendingImportRemains: Boolean) : RecoveryRunResult
}

/**
 * Idempotent journal phase runner. It never opens the gate after a post-journal
 * failure; only a completed replay or explicit user discard can do that.
 */
class RestoreRecoveryEngine(
    private val gate: RestoreGate,
    private val journalStore: RestoreJournalStore,
    private val pendingImportStore: PendingImportStore,
    private val actions: RestorePhaseActions,
    private val maxAttempts: Int = 3,
    private val retryDelayMillis: Long = 250,
) {
    private val _state = MutableStateFlow<RestoreUiState>(RestoreUiState.Idle)
    val state: StateFlow<RestoreUiState> = _state.asStateFlow()

    fun reflectBlockedStartup(unreadableJournal: Boolean) {
        _state.value = RestoreUiState.Blocked(
            message = if (unreadableJournal) {
                "The restore record could not be read. Retry may not succeed."
            } else {
                "Restore could not be completed. The app is locked until you retry or discard it."
            },
            unreadableJournal = unreadableJournal,
        )
    }

    suspend fun runAlreadyClosedGate(interrupted: Boolean): RecoveryRunResult {
        _state.value = RestoreUiState.Running(interrupted)
        var journal = when (val read = journalStore.read()) {
            RestoreJournalRead.Missing -> {
                if (journalStore.hasQuarantine()) {
                    gate.markRecoveryBlocked()
                    val message = "The restore record could not be read. Retry may not succeed."
                    _state.value = RestoreUiState.Blocked(message, unreadableJournal = true)
                    return RecoveryRunResult.Blocked(message, unreadableJournal = true)
                }
                gate.reopen()
                _state.value = RestoreUiState.Idle
                return RecoveryRunResult.NoJournal(pendingImportStore.exists())
            }
            is RestoreJournalRead.Value -> read.journal
            is RestoreJournalRead.Corrupt -> return blockUnreadableJournal(read.message)
            is RestoreJournalRead.UnknownVersion -> return blockUnreadableJournal(
                "The restore journal version is unsupported.",
            )
        }

        var failuresThisRun = 0
        while (true) {
            try {
                when (journal.phase) {
                    RestorePhase.PLANNED -> {
                        actions.applyTasks(journal.plan())
                        journal = journal.copy(phase = RestorePhase.TASKS_APPLIED)
                        journalStore.write(journal)
                    }
                    RestorePhase.TASKS_APPLIED -> {
                        actions.applySettings(journal.plan())
                        journal = journal.copy(phase = RestorePhase.SETTINGS_APPLIED)
                        journalStore.write(journal)
                    }
                    RestorePhase.SETTINGS_APPLIED -> {
                        actions.reconcile(journal.plan())
                        journal = journal.copy(phase = RestorePhase.RECONCILED)
                        journalStore.write(journal)
                    }
                    RestorePhase.RECONCILED -> {
                        actions.persistLastResult(journal.plan(), interrupted)
                        pendingImportStore.delete()
                        journalStore.deleteJournal()
                        gate.reopen()
                        _state.value = RestoreUiState.Completed(
                            counts = journal.counts,
                            afterInterruption = interrupted,
                        )
                        return RecoveryRunResult.Completed(journal, interrupted)
                    }
                }
            } catch (_: Exception) {
                failuresThisRun += 1
                journal = journal.copy(attempts = journal.attempts + 1)
                runCatching { journalStore.write(journal) }
                if (failuresThisRun >= maxAttempts) {
                    gate.markRecoveryBlocked()
                    val message = "Restore could not be completed. The app is locked until you retry or discard it."
                    _state.value = RestoreUiState.Blocked(message, unreadableJournal = false)
                    return RecoveryRunResult.Blocked(message, unreadableJournal = false)
                }
                delay(retryDelayMillis)
            }
        }
    }

    suspend fun retry(): RecoveryRunResult {
        if (!gate.beginRetry()) {
            return RecoveryRunResult.Blocked(
                "Restore recovery is not waiting for a retry.",
                unreadableJournal = journalStore.hasQuarantine(),
            )
        }
        if (journalStore.hasQuarantine()) {
            try {
                journalStore.restoreQuarantineForRetry()
            } catch (_: Exception) {
                gate.markRecoveryBlocked()
                val message = "The restore record could not be read. Retry may not succeed."
                _state.value = RestoreUiState.Blocked(message, unreadableJournal = true)
                return RecoveryRunResult.Blocked(message, unreadableJournal = true)
            }
        }
        val read = journalStore.read()
        if (read is RestoreJournalRead.Value) {
            // A user retry starts a fresh three-attempt window.
            runCatching { journalStore.write(read.journal.copy(attempts = 0)) }
        }
        return runAlreadyClosedGate(interrupted = true)
    }

    suspend fun discard(): Result<Unit> {
        if (gate.state.value != RestoreGate.State.RECOVERY_BLOCKED) {
            return Result.failure(IllegalStateException("Discard is available only when recovery is blocked."))
        }
        return runCatching {
            journalStore.deleteJournal()
            journalStore.deleteQuarantine()
            var reconciliationFailure: Exception? = null
            try {
                actions.reconcileCurrentState()
            } catch (error: Exception) {
                reconciliationFailure = error
            }
            _state.value = RestoreUiState.Idle
            gate.reopen()
            reconciliationFailure?.let { throw it }
        }
    }

    private suspend fun blockUnreadableJournal(message: String): RecoveryRunResult.Blocked {
        runCatching { journalStore.quarantine() }
        gate.markRecoveryBlocked()
        val userMessage = "The restore record could not be read. Retry may not succeed."
        _state.value = RestoreUiState.Blocked(userMessage, unreadableJournal = true)
        return RecoveryRunResult.Blocked(userMessage, unreadableJournal = true)
    }
}