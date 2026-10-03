package com.tbtechs.focusflow.data.restore

import com.tbtechs.focusflow.data.backup.BackupV1ParseResult
import com.tbtechs.focusflow.data.backup.BackupV1Parser
import com.tbtechs.focusflow.data.backup.ParsedBackupV1
import com.tbtechs.focusflow.data.model.Reminder
import com.tbtechs.focusflow.data.model.Task
import java.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RestoreCoordinatorTest {

    @Test
    fun pendingImportSurvivesRecreationLatestFileWinsAndCancelDeletesIt() = runTest {
        val first = fixture(this)
        first.coordinator.stage(backup("first"), "first.json").getOrThrow()
        assertTrue(first.coordinator.hasPendingImport())
        assertEquals("first.json", first.pending.record?.displayName)

        // A new coordinator/gate represents a process restart over the same durable file.
        val reopened = fixture(this, pending = first.pending)
        val restored = reopened.coordinator.loadPending() as PendingBackupResult.Ready
        assertEquals("first", restored.backup.parsed.envelope.tasks.single().id)

        reopened.coordinator.stage(backup("latest"), "latest.json").getOrThrow()
        val latest = reopened.coordinator.loadPending() as PendingBackupResult.Ready
        assertEquals("latest", latest.backup.parsed.envelope.tasks.single().id)
        assertEquals("latest.json", latest.backup.record.displayName)

        reopened.coordinator.cancelPending().getOrThrow()
        assertFalse(reopened.pending.exists())
        assertFalse(reopened.coordinator.hasPendingImport())
    }

    @Test
    fun sameIdMergeDuplicateIgnoresReminderDifferencesAndKeepsLocalReminderData() {
        val local = backup(
            id = "same-id",
            title = "Same task",
            remindersJson = """[{"id":"local-reminder","taskId":"same-id","offsetMinutes":-10,"type":"pre-start","notifId":"local-notification"}]""",
        ).envelope.tasks.single().toLocalTask()
        val imported = backup(
            id = "same-id",
            title = "Same task",
            remindersJson = """[{"id":"backup-reminder","taskId":"same-id","offsetMinutes":5,"type":"post-start"}]""",
        )

        val result = RestorePlanBuilder.build(
            backup = imported.envelope,
            localTasks = listOf(local),
            mode = RestoreMode.MERGE,
            planNowMillis = Instant.parse("2026-10-03T12:00:00Z").toEpochMilli(),
        )

        assertTrue(result is RestorePlanResult.Success)
        val plan = (result as RestorePlanResult.Success).plan
        assertEquals(1, plan.counts.identicalDuplicates)
        assertTrue(plan.tasksToInsert.isEmpty())
        assertEquals("local-reminder", local.reminders.single().id)
        assertEquals("local-notification", local.reminders.single().notifId)
    }

    @Test
    fun divergentSameIdIsRejectedBeforeJournalOrMutation() = runTest {
        val local = backup("same-id", title = "Local title").envelope.tasks.single().toLocalTask()
        val state = fixture(this, localTasks = listOf(local))
        state.coordinator.stage(backup("same-id", title = "Imported title"), "backup.json").getOrThrow()

        val result = state.coordinator.begin(
            mode = RestoreMode.MERGE,
            restoreSettings = true,
            restoreTasks = true,
            runtimeFocusActive = false,
            verifyPin = { true },
        )

        assertTrue(result is RestoreAdmissionResult.IdConflict)
        assertEquals(0, state.journal.writeCount)
        assertEquals(0, state.actions.taskCalls)
        assertEquals(0, state.actions.settingsCalls)
        assertEquals(RestoreGate.State.OPEN, state.gate.state.value)
        assertTrue(state.coordinator.hasPendingImport())
    }

    @Test
    fun failedPendingRewriteLeavesPreviousValidatedImportAvailable() = runTest {
        val state = fixture(this)
        state.coordinator.stage(backup("previous"), "previous.json").getOrThrow()
        val previous = state.pending.record
        state.pending.failWrites = 1

        val replacement = state.coordinator.stage(backup("replacement"), "replacement.json")

        assertTrue(replacement.isFailure)
        assertEquals(previous, state.pending.record)
        assertTrue(state.coordinator.hasPendingImport())
    }

    @Test
    fun journalWriteFailureReopensGateWithoutApplyingAnyPhase() = runTest {
        val state = fixture(this)
        state.coordinator.stage(backup("only"), "only.json").getOrThrow()
        state.journal.failWrites = 1

        val result = state.coordinator.begin(
            mode = RestoreMode.MERGE,
            restoreSettings = true,
            restoreTasks = true,
            runtimeFocusActive = false,
            verifyPin = { true },
        )

        assertTrue(result is RestoreAdmissionResult.FailedBeforeJournal)
        assertEquals(RestoreGate.State.OPEN, state.gate.state.value)
        assertTrue(state.pending.exists())
        assertEquals(0, state.actions.taskCalls)
        assertEquals(0, state.actions.settingsCalls)
    }

    @Test
    fun secondImportDoesNotReplacePendingFileWhileRestoreGateIsClosed() = runTest {
        val gate = RestoreGate(RestoreGate.State.RECOVERING)
        val pending = FakePendingStore()
        val first = backup("first")
        pending.write(
            PendingImportRecord(
                displayName = "first.json",
                normalizedBackupJson = BackupV1Parser.normalizedJson(first),
                warnings = first.warnings,
                invalidTaskCount = first.invalidTaskCount,
            ),
        )
        val state = fixture(this, gate = gate, pending = pending)
        val previous = pending.record

        val result = state.coordinator.stage(backup("second"), "second.json")
        val begin = state.coordinator.begin(
            mode = RestoreMode.MERGE,
            restoreSettings = true,
            restoreTasks = true,
            runtimeFocusActive = false,
            verifyPin = { true },
        )

        assertTrue(result.isFailure)
        assertEquals(RestoreAdmissionResult.Busy, begin)
        assertEquals(previous, pending.record)
    }

    private fun fixture(
        scope: CoroutineScope,
        gate: RestoreGate = RestoreGate(),
        pending: FakePendingStore = FakePendingStore(),
        journal: FakeJournalStore = FakeJournalStore(),
        localTasks: List<Task> = emptyList(),
    ): Fixture {
        val actions = RecordingActions()
        val engine = RestoreRecoveryEngine(
            gate = gate,
            journalStore = journal,
            pendingImportStore = pending,
            actions = actions,
            retryDelayMillis = 0,
        )
        val coordinator = RestoreCoordinator(
            gate = gate,
            pendingStore = pending,
            journalStore = journal,
            readLocalTasks = { localTasks },
            hasActiveFocusSession = { false },
            recoveryEngine = engine,
            applicationScope = scope,
        )
        return Fixture(coordinator, gate, pending, journal, actions)
    }

    private fun backup(
        id: String,
        title: String = "Task",
        remindersJson: String = "[]",
    ): ParsedBackupV1 {
        val text = """
            {
              "kind":"${BackupV1Parser.ENVELOPE_KIND}",
              "version":1,
              "settings":{},
              "tasks":[{
                "id":"$id",
                "title":"$title",
                "description":"",
                "startTime":"2026-10-03T10:00:00Z",
                "endTime":"2026-10-03T10:30:00Z",
                "durationMinutes":30,
                "status":"scheduled",
                "priority":"medium",
                "tags":[],
                "reminders":$remindersJson,
                "color":"#6366f1",
                "focusMode":false,
                "createdAt":"2026-10-03T09:00:00Z",
                "updatedAt":"2026-10-03T09:00:00Z"
              }]
            }
        """.trimIndent()
        return when (val parsed = BackupV1Parser.parse(
            text,
            Instant.parse("2026-10-03T12:00:00Z"),
        )) {
            is BackupV1ParseResult.Success -> parsed.backup
            is BackupV1ParseResult.Error -> error(parsed.message)
        }
    }

    private fun com.tbtechs.focusflow.data.backup.BackupTaskV1.toLocalTask() = Task(
        id = id,
        title = title,
        description = description,
        startTime = startTime,
        endTime = endTime,
        durationMinutes = durationMinutes,
        status = status,
        priority = priority,
        tags = tags,
        reminders = reminders,
        color = color,
        focusMode = focusMode,
        focusAllowedPackages = if (focusAllowedPackagesPresent) focusAllowedPackages else null,
        createdAt = wire["createdAt"]!!.toString().trim('"'),
        updatedAt = wire["updatedAt"]!!.toString().trim('"'),
    )

    private data class Fixture(
        val coordinator: RestoreCoordinator,
        val gate: RestoreGate,
        val pending: FakePendingStore,
        val journal: FakeJournalStore,
        val actions: RecordingActions,
    )

    private class RecordingActions : RestorePhaseActions {
        var taskCalls = 0
        var settingsCalls = 0

        override suspend fun applyTasks(plan: RestorePlan) {
            taskCalls++
        }

        override suspend fun applySettings(plan: RestorePlan) {
            settingsCalls++
        }

        override suspend fun reconcile(plan: RestorePlan) = Unit
        override suspend fun reconcileCurrentState() = Unit
        override suspend fun persistLastResult(plan: RestorePlan, afterInterruption: Boolean) = Unit
    }

    private class FakePendingStore : PendingImportStore {
        var record: PendingImportRecord? = null
        var failWrites = 0

        override fun exists() = record != null
        override suspend fun read(): PendingImportRead =
            record?.let(PendingImportRead::Value) ?: PendingImportRead.Missing

        override suspend fun write(record: PendingImportRecord) {
            if (failWrites > 0) {
                failWrites--
                error("injected pending-import rewrite failure")
            }
            this.record = record
        }

        override suspend fun delete() {
            record = null
        }
    }

    private class FakeJournalStore : RestoreJournalStore {
        var journal: RestoreJournal? = null
        var writeCount = 0
        var failWrites = 0

        override fun hasJournal() = journal != null
        override fun hasQuarantine() = false
        override suspend fun read(): RestoreJournalRead =
            journal?.let(RestoreJournalRead::Value) ?: RestoreJournalRead.Missing

        override suspend fun write(journal: RestoreJournal) {
            writeCount++
            if (failWrites > 0) {
                failWrites--
                error("injected journal write failure")
            }
            this.journal = journal
        }

        override suspend fun quarantine() = Unit
        override suspend fun restoreQuarantineForRetry() = Unit
        override suspend fun deleteJournal() {
            journal = null
        }

        override suspend fun deleteQuarantine() = Unit
    }
}