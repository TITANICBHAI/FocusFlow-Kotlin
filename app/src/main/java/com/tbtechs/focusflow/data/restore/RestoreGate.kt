package com.tbtechs.focusflow.data.restore

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

/**
 * Process-wide admission gate for writes that can race a durable restore.
 *
 * A writer owns one permit for its whole operation. Nested repository writes in
 * that operation inherit the permit, so a restore cannot close the gate between
 * (for example) a Room task update and its alarm side effects.
 */
class RestoreGate(
    initialState: State = State.OPEN,
    private val writerDrainTimeoutMillis: Long = DEFAULT_WRITER_DRAIN_TIMEOUT_MILLIS,
) {
    enum class State {
        OPEN,
        RECOVERING,
        RESTORING,
        RECOVERY_BLOCKED,
    }

    private class WriterPermit(val gate: RestoreGate) :
        AbstractCoroutineContextElement(Key) {
        companion object Key : CoroutineContext.Key<WriterPermit>
    }

    private val monitor = Any()
    private var activeWriters = 0
    private var openSignal =
        if (initialState == State.OPEN) completedSignal() else CompletableDeferred()
    private var drainedSignal: CompletableDeferred<Unit>? = null

    private val _state = MutableStateFlow(initialState)
    val state: StateFlow<State> = _state.asStateFlow()

    init {
        require(writerDrainTimeoutMillis > 0) {
            "Writer drain timeout must be greater than zero."
        }
    }

    suspend fun <T> write(owner: String, block: suspend () -> T): T {
        if (currentCoroutineContext()[WriterPermit]?.gate === this) return block()

        var acquired = false
        while (!acquired) {
            val waitForOpen: Deferred<Unit>? = synchronized(monitor) {
                if (_state.value == State.OPEN) {
                    activeWriters += 1
                    null
                } else {
                    openSignal
                }
            }
            if (waitForOpen == null) {
                acquired = true
            } else {
                waitForOpen.await()
            }
        }

        try {
            return withContext(WriterPermit(this)) { block() }
        } finally {
            synchronized(monitor) {
                activeWriters -= 1
                check(activeWriters >= 0) { "RestoreGate writer count underflow for $owner." }
                if (activeWriters == 0) drainedSignal?.complete(Unit)
            }
        }
    }

    /**
     * Closes admission first, then waits for writers that already own permits
     * to finish. A second restore receives false rather than queueing.
     */
    suspend fun tryBeginRestore(): Boolean = closeAndDrain(State.RESTORING)

    /** Startup recovery closes admission before any repository work can begin. */
    suspend fun beginRecovery(): Boolean = closeAndDrain(State.RECOVERING)

    /** Retry is permitted only from the explicit blocked-recovery screen. */
    fun beginRetry(): Boolean = synchronized(monitor) {
        if (_state.value != State.RECOVERY_BLOCKED) return false
        _state.value = State.RECOVERING
        true
    }

    private suspend fun closeAndDrain(next: State): Boolean {
        val drained = synchronized(monitor) {
            if (_state.value != State.OPEN) return false
            _state.value = next
            openSignal = CompletableDeferred()
            if (activeWriters == 0) {
                null
            } else {
                CompletableDeferred<Unit>().also { drainedSignal = it }
            }
        }
        if (drained != null) {
            val finished = try {
                withTimeoutOrNull(writerDrainTimeoutMillis) {
                    drained.await()
                    true
                } ?: false
            } catch (cancelled: CancellationException) {
                reopenAfterFailedDrain(next)
                throw cancelled
            }
            if (!finished) {
                reopenAfterFailedDrain(next)
                return false
            }
        }
        return true
    }

    private fun reopenAfterFailedDrain(expectedState: State) {
        val signal = synchronized(monitor) {
            if (_state.value != expectedState) {
                null
            } else {
                _state.value = State.OPEN
                drainedSignal = null
                openSignal
            }
        }
        signal?.complete(Unit)
    }

    fun markRecoveryBlocked() {
        synchronized(monitor) {
            check(_state.value != State.OPEN) { "Cannot block recovery while the gate is open." }
            _state.value = State.RECOVERY_BLOCKED
        }
    }

    /** Only restore admission, recovery, retry, or explicit discard calls this. */
    fun reopen() {
        val signal = synchronized(monitor) {
            if (_state.value == State.OPEN) return
            _state.value = State.OPEN
            drainedSignal = null
            openSignal
        }
        signal.complete(Unit)
    }

    private fun completedSignal() = CompletableDeferred(Unit)

    private companion object {
        const val DEFAULT_WRITER_DRAIN_TIMEOUT_MILLIS = 30_000L
    }
}