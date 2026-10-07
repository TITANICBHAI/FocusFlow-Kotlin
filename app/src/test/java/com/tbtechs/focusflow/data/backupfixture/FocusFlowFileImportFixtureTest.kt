package com.tbtechs.focusflow.data.backupfixture

import com.tbtechs.focusflow.data.backup.BackupV1ParseResult
import com.tbtechs.focusflow.data.backup.BackupV1Parser
import com.tbtechs.focusflow.data.restore.PendingImportRead
import com.tbtechs.focusflow.data.restore.PendingImportRecord
import com.tbtechs.focusflow.data.restore.PendingImportStore
import com.tbtechs.focusflow.data.restore.RestoreAdmissionResult
import com.tbtechs.focusflow.data.restore.RestoreCoordinator
import com.tbtechs.focusflow.data.restore.RestoreGate
import com.tbtechs.focusflow.data.restore.RestoreJournal
import com.tbtechs.focusflow.data.restore.RestoreJournalRead
import com.tbtechs.focusflow.data.restore.RestoreJournalStore
import com.tbtechs.focusflow.data.restore.RestoreMode
import com.tbtechs.focusflow.data.restore.RestorePhaseActions
import com.tbtechs.focusflow.data.restore.RestorePlan
import com.tbtechs.focusflow.data.restore.RestoreRecoveryEngine
import java.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FocusFlowFileImportFixtureTest {
    @Test
    fun suppliedFocusFlowFileParsesPreviewsAndRunsThroughRestorePipeline() = runTest {
        val resource = requireNotNull(
            javaClass.classLoader?.getResourceAsStream(
                "backup/precollege-prep-plan.focusflow",
            ),
        )
        val parsed = resource.use {
            when (
                val result = BackupV1Parser.parse(
                    it,
                    Instant.parse("2026-10-07T12:00:00Z"),
                )
            ) {
                is BackupV1ParseResult.Success -> result.backup
                is BackupV1ParseResult.Error -> error(result.message)
            }
        }
        assertEquals(136, parsed.envelope.tasks.size)
        assertEquals(0, parsed.invalidTaskCount)

        val pendingStore = MemoryPendingImportStore()
        val journalStore = MemoryRestoreJournalStore()
        val actions = RecordingRestoreActions()
        val gate = RestoreGate()
        val recoveryEngine = RestoreRecoveryEngine(
            gate = gate,
            journalStore = journalStore,
            pendingImportStore = pendingStore,
            actions = actions,
            retryDelayMillis = 0,
        )
        val coordinator = RestoreCoordinator(
            gate = gate,
            pendingStore = pendingStore,
            journalStore = journalStore,
            readLocalTasks = { emptyList() },
            hasActiveFocusSession = { false },
            recoveryEngine = recoveryEngine,
            applicationScope = this,
        )

        coordinator.stage(parsed, "precollege-prep-plan.focusflow").getOrThrow()
        assertTrue(coordinator.hasPendingImport())

        val preview = coordinator.preview(
            mode = RestoreMode.MERGE,
            restoreSettings = true,
            restoreTasks = true,
        ).getOrThrow()
        assertEquals(136, preview.tasksInFile)
        assertEquals(136, preview.newTasks)
        assertEquals(0, preview.totalConflicts)
        assertEquals(0, preview.invalidTasks)

        val result = coordinator.begin(
            mode = RestoreMode.MERGE,
            restoreSettings = true,
            restoreTasks = true,
            runtimeFocusActive = false,
            verifyPin = { true },
        )

        assertTrue(result is RestoreAdmissionResult.Finished)
        assertEquals(136, (result as RestoreAdmissionResult.Finished).counts.tasksInserted)
        assertEquals(136, actions.importedTaskCount)
        assertTrue(actions.settingsApplied)
        assertFalse(pendingStore.exists())
    }

    private class MemoryPendingImportStore : PendingImportStore {
        private var record: PendingImportRecord? = null

        override fun exists(): Boolean = record != null

        override suspend fun read(): PendingImportRead =
            record?.let(PendingImportRead::Value) ?: PendingImportRead.Missing

        override suspend fun write(record: PendingImportRecord) {
            this.record = record
        }

        override suspend fun delete() {
            record = null
        }
    }

    private class MemoryRestoreJournalStore : RestoreJournalStore {
        private var journal: RestoreJournal? = null

        override fun hasJournal(): Boolean = journal != null
        override fun hasQuarantine(): Boolean = false

        override suspend fun read(): RestoreJournalRead =
            journal?.let(RestoreJournalRead::Value) ?: RestoreJournalRead.Missing

        override suspend fun write(journal: RestoreJournal) {
            this.journal = journal
        }

        override suspend fun quarantine() = Unit
        override suspend fun restoreQuarantineForRetry() = Unit
        override suspend fun deleteJournal() {
            journal = null
        }
        override suspend fun deleteQuarantine() = Unit
    }

    private class RecordingRestoreActions : RestorePhaseActions {
        var importedTaskCount = 0
            private set
        var settingsApplied = false
            private set

        override suspend fun applyTasks(plan: RestorePlan) {
            importedTaskCount = plan.tasksToInsert.size
        }

        override suspend fun applySettings(plan: RestorePlan) {
            settingsApplied = true
        }

        override suspend fun reconcile(plan: RestorePlan) = Unit
        override suspend fun reconcileCurrentState() = Unit
        override suspend fun persistLastResult(plan: RestorePlan, afterInterruption: Boolean) = Unit
    }
}
