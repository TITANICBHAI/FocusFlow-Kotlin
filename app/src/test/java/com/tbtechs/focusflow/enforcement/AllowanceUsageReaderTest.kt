package com.tbtechs.focusflow.enforcement

import com.tbtechs.focusflow.analytics.UsageEventRead
import java.time.ZoneId
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class AllowanceUsageReaderTest {
    @Test
    fun unknownRevokedTimedOutAndFailedReadsKeepLastKnownAllowance() = runBlocking {
        val original = """{"pkg":{"mode":"time_budget","date":"2026-10-08","usedMs":1200,"confirmedUsedMs":1000,"estimatedExtraMs":200,"confirmedAtMs":1000}}"""
        val store = MemoryStore(original)
        val ledger = AllowanceLedger(store)
        val failures: List<suspend (Long, Long) -> UsageEventRead> = listOf(
            { _, _ ->
                UsageEventRead.Unknown(UsageEventRead.Unknown.Reason.EVENTS_UNAVAILABLE)
            },
            { _, _ -> throw SecurityException("usage access revoked") },
            { _, _ ->
                delay(50)
                UsageEventRead.Unknown(UsageEventRead.Unknown.Reason.EVENTS_UNAVAILABLE)
            },
            { _, _ -> throw IllegalStateException("slow or unavailable source") },
        )

        failures.forEach { readEvents ->
            val reader = AllowanceUsageReader(
                readEvents = readEvents,
                packageName = "com.example.focusflow",
                ledger = ledger,
                zoneId = ZoneId.of("UTC"),
                onReconciled = {},
                readTimeoutMs = 5L,
            )

            val outcome = reader.readAndReconcile(
                targets = listOf(
                    AllowanceUsageTarget("pkg", AllowanceLedger.MODE_TIME_BUDGET),
                ),
                queryStartMs = 1L,
                nowMs = 2L,
                activePackage = null,
            )

            assertFalse(outcome.read is UsageEventRead.Available)
            assertEquals(1_200L, ledger.usage("pkg").usedMs)
            assertEquals(1_000L, ledger.usage("pkg").confirmedUsedMs)
            assertEquals(200L, ledger.usage("pkg").estimatedExtraMs)
            assertEquals(original, store.value)
            assertEquals(0, store.writes)
        }
    }

    private class MemoryStore(var value: String?) : AllowanceLedgerStore {
        var writes = 0

        override fun readUsageJson(): String? = value

        override fun writeUsageJson(value: String) {
            this.value = value
            writes += 1
        }
    }
}
