package com.tbtechs.focusflow.enforcement

import java.time.LocalDate
import java.time.ZoneId
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

class AllowanceUsageAccumulatorTest {
    private val zone = ZoneId.of("UTC")
    private val target = AllowanceUsageTarget("pkg", AllowanceLedger.MODE_TIME_BUDGET)
    private val day = LocalDate.parse("2026-10-08")

    @Test
    fun staleUsageAccumulatesAcrossSeparateForegroundSessions() {
        val ledger = AllowanceLedger(MemoryStore())
        val accumulator = AllowanceUsageAccumulator(ledger, zone)
        val firstStart = day.atTime(12, 0).atZone(zone).toInstant().toEpochMilli()

        accumulator.start(firstStart, firstStart)
        accumulator.finish(target, firstStart + 10 * MINUTE_MS)
        accumulator.start(firstStart + 20 * MINUTE_MS, firstStart)
        accumulator.finish(target, firstStart + 30 * MINUTE_MS)

        assertEquals(20 * MINUTE_MS, ledger.usage("pkg").estimatedExtraMs)
        assertEquals(20 * MINUTE_MS, ledger.usage("pkg").usedMs)
    }

    @Test
    fun staleUsageIsClippedAtMidnightAndStoredUnderEachLocalDate() {
        val store = MemoryStore()
        val ledger = AllowanceLedger(store)
        val accumulator = AllowanceUsageAccumulator(ledger, zone)
        val start = day.atTime(23, 50).atZone(zone).toInstant().toEpochMilli()
        val end = day.plusDays(1).atTime(0, 20).atZone(zone).toInstant().toEpochMilli()

        accumulator.start(start, start)
        accumulator.checkpoint(target, end)

        val writes = store.history.map { JSONObject(it).getJSONObject("pkg") }
        assertEquals(2, writes.size)
        assertEquals("2026-10-08", writes[0].getString("date"))
        assertEquals(10 * MINUTE_MS, writes[0].getLong("usedMs"))
        assertEquals("2026-10-09", writes[1].getString("date"))
        assertEquals(20 * MINUTE_MS, writes[1].getLong("usedMs"))
    }

    @Test
    fun repeatedStaleCheckpointsCannotExceedFourHoursForOneSession() {
        val ledger = AllowanceLedger(MemoryStore())
        val accumulator = AllowanceUsageAccumulator(ledger, zone)
        val start = day.atTime(12, 0).atZone(zone).toInstant().toEpochMilli()

        accumulator.start(start, start)
        accumulator.checkpoint(target, start + 3 * HOUR_MS)
        accumulator.checkpoint(target, start + 6 * HOUR_MS)

        assertEquals(4 * HOUR_MS, ledger.usage("pkg").usedMs)
    }

    @Test
    fun successfulReconciliationKeepsConfirmedUsageMonotonicAndResetsEstimate() {
        val ledger = AllowanceLedger(MemoryStore())
        val start = day.atTime(12, 0).atZone(zone).toInstant().toEpochMilli()

        ledger.reconcileTimeUsage(
            packageName = "pkg",
            mode = AllowanceLedger.MODE_TIME_BUDGET,
            today = "2026-10-08",
            windowStartMs = 0L,
            usedMs = 20 * MINUTE_MS,
            atMs = start,
        )
        ledger.addEstimatedTimeUsage(
            packageName = "pkg",
            mode = AllowanceLedger.MODE_TIME_BUDGET,
            today = "2026-10-08",
            windowStartMs = 0L,
            deltaMs = 5 * MINUTE_MS,
        )
        ledger.reconcileTimeUsage(
            packageName = "pkg",
            mode = AllowanceLedger.MODE_TIME_BUDGET,
            today = "2026-10-08",
            windowStartMs = 0L,
            usedMs = 10 * MINUTE_MS,
            atMs = start + HOUR_MS,
        )

        val usage = ledger.usage("pkg")
        assertEquals(20 * MINUTE_MS, usage.confirmedUsedMs)
        assertEquals(0L, usage.estimatedExtraMs)
        assertEquals(20 * MINUTE_MS, usage.usedMs)
    }

    private class MemoryStore : AllowanceLedgerStore {
        private var current: String? = null
        val history = mutableListOf<String>()

        override fun readUsageJson(): String? = current

        override fun writeUsageJson(value: String) {
            current = value
            history += value
        }
    }

    private companion object {
        const val MINUTE_MS = 60_000L
        const val HOUR_MS = 60 * MINUTE_MS
    }
}
