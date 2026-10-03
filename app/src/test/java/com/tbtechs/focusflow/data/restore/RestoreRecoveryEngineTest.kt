package com.tbtechs.focusflow.data.restore

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RestoreRecoveryEngineTest {

    @Test
    fun resumesFromEveryDurablePhaseBoundary() = runTest {
        for (phase in RestorePhase.values()) {
            val store = FakeJournalStore(journal = journal(phase))
            val actions = CountingActions()
            val gate = RestoreGate(RestoreGate.State.RECOVERING)
            val engine = engine(gate, store, actions)

            val result = engine.runAlreadyClosedGate(interrupted = true)

            assertTrue("Expected completion from $phase", result is RecoveryRunResult.Completed)
            assertEquals(if (phase == RestorePhase.PLANNED) 1 else 0, actions.taskCalls)
            assertEquals(
                if (phase.ordinal <= RestorePhase.TASKS_APPLIED.ordinal) 1 else 0,
                actions.settingsCalls,
            )
            assertEquals(
                if (phase.ordinal <= RestorePhase.SETTINGS_APPLIED.ordinal) 1 else 0,
                actions.reconcileCalls,
            )
            assertEquals(1, actions.persistCalls)
            assertEquals(RestoreGate.State.OPEN, gate.state.value)
            assertFalse(store.hasJournal())
        }
    }

    @Test
    fun failedPhaseRetainsJournalAndGateUntilExplicitRetryCompletes() = runTest {
        val store = FakeJournalStore(journal = journal(RestorePhase.PLANNED))
        val actions = CountingActions(failTaskCalls = 3)
        val gate = RestoreGate(RestoreGate.State.RECOVERING)
        val engine = engine(gate, store, actions)

        val firstRun = engine.runAlreadyClosedGate(interrupted = true)

        assertTrue(firstRun is RecoveryRunResult.Blocked)
        assertEquals(RestoreGate.State.RECOVERY_BLOCKED, gate.state.value)
        assertTrue(store.hasJournal())
        assertEquals(RestorePhase.PLANNED, store.journal?.phase)

        actions.failTaskCalls = 0
        val retry = engine.retry()

        assertTrue(retry is RecoveryRunResult.Completed)
        assertEquals(RestoreGate.State.OPEN, gate.state.value)
        assertFalse(store.hasJournal())
    }

    @Test
    fun transitionWriteFailureReplaysIdempotentTaskTransaction() = runTest {
        val store = FakeJournalStore(
            journal = journal(RestorePhase.PLANNED),
            failNextPhaseTransition = true,
        )
        val actions = CountingActions()
        val gate = RestoreGate(RestoreGate.State.RECOVERING)
        val engine = engine(gate, store, actions)

        val result = engine.runAlreadyClosedGate(interrupted = false)

        assertTrue(result is RecoveryRunResult.Completed)
        assertEquals(2, actions.taskCalls)
        assertEquals(RestoreGate.State.OPEN, gate.state.value)
    }

    @Test
    fun corruptJournalIsQuarantinedAndKeepsGateClosed() = runTest {
        val store = FakeJournalStore(readResult = RestoreJournalRead.Corrupt("bad json"))
        val gate = RestoreGate(RestoreGate.State.RECOVERING)
        val engine = engine(gate, store, CountingActions())

        val result = engine.runAlreadyClosedGate(interrupted = true)

        assertTrue(result is RecoveryRunResult.Blocked)
        assertTrue((result as RecoveryRunResult.Blocked).unreadableJournal)
        assertTrue(store.hasQuarantine())
        assertEquals(RestoreGate.State.RECOVERY_BLOCKED, gate.state.value)
    }

    private fun engine(
        gate: RestoreGate,
        store: FakeJournalStore,
        actions: CountingActions,
    ) = RestoreRecoveryEngine(
        gate = gate,
        journalStore = store,
        pendingImportStore = FakePendingStore(),
        actions = actions,
        retryDelayMillis = 0,
    )

    private fun journal(phase: RestorePhase) = RestoreJournal(
        sessionId = "test-session",
        mode = RestoreMode.MERGE,
        planNowMillis = 1L,
        phase = phase,
        attempts = 0,
        deleteAllExisting = false,
        preDeleteTaskIds = emptyList(),
        tasksToInsert = emptyList(),
        settingsPlan = JsonObject(emptyMap()),
        userGreyoutWindows = JsonArray(emptyList()),
        recurringBlockSchedules = JsonArray(emptyList()),
        warningCodes = emptyList(),
        counts = RestoreCounts(),
    )

    private class CountingActions(
        var failTaskCalls: Int = 0,
    ) : RestorePhaseActions {
        var taskCalls = 0
        var settingsCalls = 0
        var reconcileCalls = 0
        var persistCalls = 0

        override suspend fun applyTasks(plan: RestorePlan) {
            taskCalls++
            if (failTaskCalls > 0) {
                failTaskCalls--
                error("injected task phase failure")
            }
        }

        override suspend fun applySettings(plan: RestorePlan) {
            settingsCalls++
        }

        override suspend fun reconcile(plan: RestorePlan) {
            reconcileCalls++
        }

        override suspend fun reconcileCurrentState() = Unit

        override suspend fun persistLastResult(plan: RestorePlan, afterInterruption: Boolean) {
            persistCalls++
        }
    }

    private class FakePendingStore : PendingImportStore {
        var present = true
        override fun exists() = present
        override suspend fun read() = PendingImportRead.Missing
        override suspend fun write(record: PendingImportRecord) {
            present = true
        }
        override suspend fun delete() {
            present = false
        }
    }

    private class FakeJournalStore(
        var journal: RestoreJournal? = null,
        private var readResult: RestoreJournalRead? = null,
        var failNextPhaseTransition: Boolean = false,
    ) : RestoreJournalStore {
        private var quarantined = false

        override fun hasJournal() = journal != null
        override fun hasQuarantine() = quarantined

        override suspend fun read(): RestoreJournalRead =
            readResult ?: journal?.let(RestoreJournalRead::Value) ?: RestoreJournalRead.Missing

        override suspend fun write(journal: RestoreJournal) {
            if (failNextPhaseTransition && journal.phase != RestorePhase.PLANNED) {
                failNextPhaseTransition = false
                error("injected journal transition failure")
            }
            this.journal = journal
            readResult = null
        }

        override suspend fun quarantine() {
            quarantined = true
            journal = null
        }

        override suspend fun restoreQuarantineForRetry() {
            quarantined = false
            journal = null
        }

        override suspend fun deleteJournal() {
            journal = null
        }

        override suspend fun deleteQuarantine() {
            quarantined = false
        }
    }
}