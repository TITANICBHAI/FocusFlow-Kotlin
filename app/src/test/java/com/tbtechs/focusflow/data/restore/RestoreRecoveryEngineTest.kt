package com.tbtechs.focusflow.data.restore

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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

    @Test
    fun unknownJournalVersionIsQuarantinedAndKeepsGateClosed() = runTest {
        val store = FakeJournalStore(
            readResult = RestoreJournalRead.UnknownVersion(99),
        )
        val gate = RestoreGate(RestoreGate.State.RECOVERING)
        val engine = engine(gate, store, CountingActions())

        val result = engine.runAlreadyClosedGate(interrupted = true)

        assertTrue(result is RecoveryRunResult.Blocked)
        assertTrue((result as RecoveryRunResult.Blocked).unreadableJournal)
        assertTrue(store.hasQuarantine())
        assertEquals(RestoreGate.State.RECOVERY_BLOCKED, gate.state.value)
    }

    @Test
    fun noJournalLeavesPendingImportForConfirmationAndOpensGate() = runTest {
        val pending = FakePendingStore(present = true)
        val gate = RestoreGate(RestoreGate.State.RECOVERING)
        val engine = engine(gate, FakeJournalStore(), CountingActions(), pending)

        val result = engine.runAlreadyClosedGate(interrupted = true)

        assertEquals(RecoveryRunResult.NoJournal(pendingImportRemains = true), result)
        assertTrue(pending.present)
        assertEquals(RestoreGate.State.OPEN, gate.state.value)
    }

    @Test
    fun pendingImportIsRemovedBeforeAnyJournalPhaseRuns() = runTest {
        val events = mutableListOf<String>()
        val pending = FakePendingStore(present = true, events = events, failDeletes = 1)
        val store = FakeJournalStore(journal = journal(RestorePhase.PLANNED))
        val actions = CountingActions(events = events)
        val gate = RestoreGate(RestoreGate.State.RECOVERING)
        val engine = engine(gate, store, actions, pending)

        val result = engine.runAlreadyClosedGate(interrupted = true)

        assertTrue(result is RecoveryRunResult.Completed)
        assertFalse(pending.present)
        assertTrue(pending.deleteCalls >= 2)
        assertEquals(listOf("pending-delete", "pending-delete", "tasks"), events.take(3))
    }

    @Test
    fun partialSettingsCommitIsReappliedAsAnAbsolutePlan() = runTest {
        val settingsPlan = JsonObject(
            mapOf(
                "darkMode" to JsonPrimitive(true),
                "launcherTheme" to JsonPrimitive("classic"),
            ),
        )
        val store = FakeJournalStore(
            journal = journal(RestorePhase.TASKS_APPLIED, settingsPlan),
        )
        val actions = CountingActions(failSettingsAfterPartialCalls = 1)
        val gate = RestoreGate(RestoreGate.State.RECOVERING)
        val engine = engine(gate, store, actions)

        val result = engine.runAlreadyClosedGate(interrupted = true)

        assertTrue(result is RecoveryRunResult.Completed)
        assertEquals(2, actions.settingsCalls)
        assertEquals(settingsPlan, JsonObject(actions.persistedSettings))
        assertEquals(RestoreGate.State.OPEN, gate.state.value)
    }

    @Test
    fun reconcileFailureRetriesFromSettingsApplied() = runTest {
        val store = FakeJournalStore(journal = journal(RestorePhase.SETTINGS_APPLIED))
        val actions = CountingActions(failReconcileCalls = 1)
        val gate = RestoreGate(RestoreGate.State.RECOVERING)
        val engine = engine(gate, store, actions)

        val result = engine.runAlreadyClosedGate(interrupted = true)

        assertTrue(result is RecoveryRunResult.Completed)
        assertEquals(2, actions.reconcileCalls)
        assertEquals(RestoreGate.State.OPEN, gate.state.value)
    }

    @Test
    fun terminalResultAndJournalDeletionAreSafeToRetry() = runTest {
        val store = FakeJournalStore(
            journal = journal(RestorePhase.RECONCILED),
            failDeleteJournalCalls = 1,
        )
        val actions = CountingActions(failPersistCalls = 1)
        val gate = RestoreGate(RestoreGate.State.RECOVERING)
        val engine = engine(gate, store, actions)

        val result = engine.runAlreadyClosedGate(interrupted = true)

        assertTrue(result is RecoveryRunResult.Completed)
        assertEquals(2, actions.persistCalls)
        assertEquals(2, store.deleteJournalCalls)
        assertFalse(store.hasJournal())
        assertEquals(RestoreGate.State.OPEN, gate.state.value)
    }

    @Test
    fun discardReconcilesAndRetryKeepsGateClosedIfRepairFails() = runTest {
        val store = FakeJournalStore(journal = journal(RestorePhase.PLANNED))
        val actions = CountingActions(
            failTaskCalls = 3,
            failCurrentReconcileCalls = 1,
        )
        val gate = RestoreGate(RestoreGate.State.RECOVERING)
        val engine = engine(gate, store, actions)

        assertTrue(engine.runAlreadyClosedGate(interrupted = true) is RecoveryRunResult.Blocked)
        val discard = engine.discard()

        assertTrue(discard.isFailure)
        assertFalse(store.hasJournal())
        assertEquals(RestoreGate.State.RECOVERY_BLOCKED, gate.state.value)

        val retry = engine.retry()

        assertTrue(retry is RecoveryRunResult.NoJournal)
        assertEquals(2, actions.currentReconcileCalls)
        assertEquals(RestoreGate.State.OPEN, gate.state.value)
    }

    @Test
    fun failedPhaseTransitionKeepsOldDurablePhaseUntilReplay() = runTest {
        val oldJournal = journal(RestorePhase.PLANNED)
        val store = FakeJournalStore(
            journal = oldJournal,
            failNextPhaseTransition = true,
        )
        val gate = RestoreGate(RestoreGate.State.RECOVERING)
        val engine = engine(gate, store, CountingActions())

        val result = engine.runAlreadyClosedGate(interrupted = true)

        assertTrue(result is RecoveryRunResult.Completed)
        assertNull(store.journal)
        assertEquals(2, store.writeCountFor(RestorePhase.TASKS_APPLIED))
    }

    private fun engine(
        gate: RestoreGate,
        store: FakeJournalStore,
        actions: CountingActions,
        pending: FakePendingStore = FakePendingStore(),
    ) = RestoreRecoveryEngine(
        gate = gate,
        journalStore = store,
        pendingImportStore = pending,
        actions = actions,
        retryDelayMillis = 0,
    )

    private fun journal(
        phase: RestorePhase,
        settingsPlan: JsonObject = JsonObject(emptyMap()),
    ) = RestoreJournal(
        sessionId = "test-session",
        mode = RestoreMode.MERGE,
        planNowMillis = 1L,
        phase = phase,
        attempts = 0,
        deleteAllExisting = false,
        preDeleteTaskIds = emptyList(),
        tasksToInsert = emptyList(),
        settingsPlan = settingsPlan,
        userGreyoutWindows = JsonArray(emptyList()),
        recurringBlockSchedules = JsonArray(emptyList()),
        warningCodes = emptyList(),
        counts = RestoreCounts(),
    )

    private class CountingActions(
        var failTaskCalls: Int = 0,
        var failSettingsAfterPartialCalls: Int = 0,
        var failReconcileCalls: Int = 0,
        var failPersistCalls: Int = 0,
        var failCurrentReconcileCalls: Int = 0,
        private val events: MutableList<String>? = null,
    ) : RestorePhaseActions {
        var taskCalls = 0
        var settingsCalls = 0
        var reconcileCalls = 0
        var persistCalls = 0
        var currentReconcileCalls = 0
        val persistedSettings = linkedMapOf<String, kotlinx.serialization.json.JsonElement>()

        override suspend fun applyTasks(plan: RestorePlan) {
            taskCalls++
            events?.add("tasks")
            if (failTaskCalls > 0) {
                failTaskCalls--
                error("injected task phase failure")
            }
        }

        override suspend fun applySettings(plan: RestorePlan) {
            settingsCalls++
            if (failSettingsAfterPartialCalls > 0) {
                plan.settingsPlan.entries.firstOrNull()?.let {
                    persistedSettings[it.key] = it.value
                }
                failSettingsAfterPartialCalls--
                error("injected partial settings commit")
            }
            persistedSettings.putAll(plan.settingsPlan)
        }

        override suspend fun reconcile(plan: RestorePlan) {
            reconcileCalls++
            if (failReconcileCalls > 0) {
                failReconcileCalls--
                error("injected reconcile failure")
            }
        }

        override suspend fun reconcileCurrentState() {
            currentReconcileCalls++
            if (failCurrentReconcileCalls > 0) {
                failCurrentReconcileCalls--
                error("injected current-state reconciliation failure")
            }
        }

        override suspend fun persistLastResult(plan: RestorePlan, afterInterruption: Boolean) {
            persistCalls++
            if (failPersistCalls > 0) {
                failPersistCalls--
                error("injected result persistence failure")
            }
        }
    }

    private class FakePendingStore : PendingImportStore {
        var present = true
        var failDeletes = 0
        var deleteCalls = 0
        var events: MutableList<String>? = null

        constructor(
            present: Boolean = true,
            events: MutableList<String>? = null,
            failDeletes: Int = 0,
        ) : this() {
            this.present = present
            this.events = events
            this.failDeletes = failDeletes
        }

        override fun exists() = present
        override suspend fun read() = PendingImportRead.Missing
        override suspend fun write(record: PendingImportRecord) {
            present = true
        }
        override suspend fun delete() {
            deleteCalls++
            events?.add("pending-delete")
            if (failDeletes > 0) {
                failDeletes--
                error("injected pending-import delete failure")
            }
            present = false
        }
    }

    private class FakeJournalStore(
        var journal: RestoreJournal? = null,
        private var readResult: RestoreJournalRead? = null,
        var failNextPhaseTransition: Boolean = false,
        var failDeleteJournalCalls: Int = 0,
    ) : RestoreJournalStore {
        private var quarantined = false
        private val writesByPhase = mutableMapOf<RestorePhase, Int>()
        var deleteJournalCalls = 0
            private set
        var writeCount = 0
            private set

        override fun hasJournal() = journal != null
        override fun hasQuarantine() = quarantined

        override suspend fun read(): RestoreJournalRead =
            readResult ?: journal?.let(RestoreJournalRead::Value) ?: RestoreJournalRead.Missing

        override suspend fun write(journal: RestoreJournal) {
            writeCount++
            writesByPhase[journal.phase] = (writesByPhase[journal.phase] ?: 0) + 1
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
            deleteJournalCalls++
            if (failDeleteJournalCalls > 0) {
                failDeleteJournalCalls--
                error("injected journal delete failure")
            }
            journal = null
        }

        override suspend fun deleteQuarantine() {
            quarantined = false
        }

        fun writeCountFor(phase: RestorePhase) = writesByPhase[phase] ?: 0
    }
}