package com.tbtechs.focusflow.data.restore

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withTimeout

internal const val DEFAULT_RESTORE_RECOVERY_TIMEOUT_MILLIS = 120_000L

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
    private val recoveryTimeoutMillis: Long = DEFAULT_RESTORE_RECOVERY_TIMEOUT_MILLIS,
) {
    init {
        require(recoveryTimeoutMillis > 0) {
            "Restore recovery timeout must be greater than zero."
        }
    }

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
        return try {
            withTimeout(recoveryTimeoutMillis) {
                runAlreadyClosedGateInternal(interrupted)
            }
        } catch (_: TimeoutCancellationException) {
            blockForRecovery(
                "Restore is taking longer than expected. Retry or discard it to continue.",
            )
        } catch (cancelled: CancellationException) {
            markBlockedAfterCancellation()
            throw cancelled
        } catch (_: Exception) {
            blockForRecovery(
                "Restore recovery encountered an unexpected error. Retry or discard it to continue.",
            )
        }
    }

    private suspend fun runAlreadyClosedGateInternal(interrupted: Boolean): RecoveryRunResult {
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
        var pendingImportCleared = false
        var terminalResultPersisted = false
        while (true) {
            try {
                if (!pendingImportCleared) {
                    // The journal is authoritative whenever both durable files
                    // exist. Clear the staged preview before any restore phase.
                    pendingImportStore.delete()
                    pendingImportCleared = true
                }
                when (journal.phase) {
                    RestorePhase.PLANNED -> {
                        actions.applyTasks(journal.plan())
                        journal = advance(journal, RestorePhase.TASKS_APPLIED)
                    }
                    RestorePhase.TASKS_APPLIED -> {
                        actions.applySettings(journal.plan())
                        journal = advance(journal, RestorePhase.SETTINGS_APPLIED)
                    }
                    RestorePhase.SETTINGS_APPLIED -> {
                        actions.reconcile(journal.plan())
                        journal = advance(journal, RestorePhase.RECONCILED)
                    }
                    RestorePhase.RECONCILED -> {
                        if (!terminalResultPersisted) {
                            actions.persistLastResult(journal.plan(), interrupted)
                            terminalResultPersisted = true
                        }
                        journalStore.deleteJournal()
                        gate.reopen()
                        _state.value = RestoreUiState.Completed(
                            counts = journal.counts,
                            afterInterruption = interrupted,
                        )
                        return RecoveryRunResult.Completed(journal, interrupted)
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                failuresThisRun += 1
                journal = journal.copy(attempts = journal.attempts + 1)
                try {
                    journalStore.write(journal)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    // Keep the original journal; the retry window still bounds this run.
                }
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
        return try {
            retryInternal()
        } catch (cancelled: CancellationException) {
            markBlockedAfterCancellation()
            throw cancelled
        } catch (_: Exception) {
            blockForRecovery(
                "Restore recovery encountered an unexpected error. Retry or discard it to continue.",
            )
        }
    }

    private suspend fun retryInternal(): RecoveryRunResult {
        if (!gate.beginRetry()) {
            return RecoveryRunResult.Blocked(
                "Restore recovery is not waiting for a retry.",
                unreadableJournal = journalStore.hasQuarantine(),
            )
        }
        if (journalStore.hasQuarantine()) {
            try {
                journalStore.restoreQuarantineForRetry()
            } catch (cancelled: CancellationException) {
                throw cancelled
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
            try {
                journalStore.write(read.journal.copy(attempts = 0))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // The previous attempt count is still safe; recovery remains bounded.
            }
        }
        return runAlreadyClosedGate(interrupted = true)
    }

    suspend fun discard(): Result<Unit> {
        if (gate.state.value != RestoreGate.State.RECOVERY_BLOCKED) {
            return Result.failure(IllegalStateException("Discard is available only when recovery is blocked."))
        }

        // Keep the gate closed unless both durable restore artifacts are gone.
        try {
            journalStore.deleteJournal()
            journalStore.deleteQuarantine()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            return Result.failure(error)
        }

        // Once the user's discard choice is durable, repair derived state with
        // bounded retries but do not lock the app again if that repair fails.
        _state.value = RestoreUiState.Running(interrupted = true)
        var reconcileFailure: Exception? = null
        try {
            var attempt = 0
            var reconciled = false
            while (attempt < maxAttempts && !reconciled) {
                attempt += 1
                try {
                    actions.reconcileCurrentState()
                    reconcileFailure = null
                    reconciled = true
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    reconcileFailure = error
                    if (attempt < maxAttempts) delay(retryDelayMillis)
                }
            }
        } finally {
            _state.value = RestoreUiState.Idle
            gate.reopen()
        }
        return reconcileFailure?.let { Result.failure(it) } ?: Result.success(Unit)
    }

    private suspend fun advance(
        current: RestoreJournal,
        phase: RestorePhase,
    ): RestoreJournal {
        val next = current.copy(phase = phase)
        journalStore.write(next)
        return next
    }

    private suspend fun blockUnreadableJournal(message: String): RecoveryRunResult.Blocked {
        gate.markRecoveryBlocked()
        val userMessage = "The restore record could not be read. Retry may not succeed."
        _state.value = RestoreUiState.Blocked(userMessage, unreadableJournal = true)
        try {
            journalStore.quarantine()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // Keep the gate blocked even if quarantine cannot be persisted.
        }
        return RecoveryRunResult.Blocked(userMessage, unreadableJournal = true)
    }

    private fun markBlockedAfterCancellation() {
        if (gate.state.value == RestoreGate.State.OPEN) {
            _state.value = RestoreUiState.Idle
            return
        }
        val unreadable = (_state.value as? RestoreUiState.Blocked)?.unreadableJournal ?: false
        gate.markRecoveryBlocked()
        _state.value = RestoreUiState.Blocked(
            message = "Restore recovery was interrupted. Retry or discard it to continue.",
            unreadableJournal = unreadable,
        )
    }

    private fun blockForRecovery(message: String): RecoveryRunResult {
        if (gate.state.value == RestoreGate.State.OPEN) {
            _state.value = RestoreUiState.Idle
            return RecoveryRunResult.NoJournal(
                pendingImportRemains = runCatching { pendingImportStore.exists() }.getOrDefault(false),
            )
        }

        val unreadable = runCatching { journalStore.hasQuarantine() }.getOrDefault(false)
        gate.markRecoveryBlocked()
        _state.value = RestoreUiState.Blocked(
            message = message,
            unreadableJournal = unreadable,
        )
        return RecoveryRunResult.Blocked(message, unreadable)
    }
}