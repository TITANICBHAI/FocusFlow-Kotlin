package com.tbtechs.focusflow.data.restore

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RestoreGateTest {

    @Test
    fun restoreDrainsExistingWriterAndSuspendsLaterWriters() = runTest {
        val gate = RestoreGate()
        val writerEntered = CompletableDeferred<Unit>()
        val releaseWriter = CompletableDeferred<Unit>()
        var lateWriterRan = false

        val existingWriter = launch {
            gate.write("existing") {
                writerEntered.complete(Unit)
                releaseWriter.await()
            }
        }
        writerEntered.await()

        val restore = async { gate.tryBeginRestore() }
        runCurrent()
        assertFalse("Restore must wait for a writer that already has a permit.", restore.isCompleted)
        assertEquals(RestoreGate.State.RESTORING, gate.state.value)

        val lateWriter = async {
            gate.write("late") { lateWriterRan = true }
        }
        runCurrent()
        assertFalse(lateWriterRan)

        releaseWriter.complete(Unit)
        existingWriter.join()
        assertTrue(restore.await())
        assertFalse(lateWriterRan)

        gate.reopen()
        lateWriter.await()
        assertTrue(lateWriterRan)
        assertEquals(RestoreGate.State.OPEN, gate.state.value)
    }

    @Test
    fun secondRestoreIsRejectedWhileGateIsClosed() = runTest {
        val gate = RestoreGate(RestoreGate.State.RECOVERING)

        assertFalse(gate.tryBeginRestore())
        assertEquals(RestoreGate.State.RECOVERING, gate.state.value)
    }

    @Test
    fun restoreReopensGateWhenAnExistingWriterDoesNotDrain() = runTest {
        val gate = RestoreGate(writerDrainTimeoutMillis = 50)
        val writerEntered = CompletableDeferred<Unit>()
        val releaseWriter = CompletableDeferred<Unit>()
        val existingWriter = launch {
            gate.write("stalled-existing") {
                writerEntered.complete(Unit)
                releaseWriter.await()
            }
        }
        writerEntered.await()

        assertFalse(gate.tryBeginRestore())
        assertEquals(RestoreGate.State.OPEN, gate.state.value)

        releaseWriter.complete(Unit)
        existingWriter.join()
        assertEquals(RestoreGate.State.OPEN, gate.state.value)
    }
}