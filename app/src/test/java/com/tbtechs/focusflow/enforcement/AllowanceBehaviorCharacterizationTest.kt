package com.tbtechs.focusflow.enforcement

import java.nio.file.Files
import java.nio.file.Path
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins pre-ledger behavior examples while checking that Android-bound readers
 * now delegate allowance decisions to the shared JVM-testable ledger.
 */
class AllowanceBehaviorCharacterizationTest {
    private val accessibilitySource by lazy {
        source("enforcement/AppBlockerAccessibilityService.kt")
    }
    private val foregroundSource by lazy {
        source("enforcement/ForegroundTaskService.kt")
    }
    private val launcherSource by lazy {
        source("enforcement/LauncherActivity.kt")
    }
    private val ledgerSource by lazy {
        source("enforcement/AllowanceLedger.kt")
    }

    @Test
    fun intervalWindowUsesStrictExpiryAndClampsRemainingAtItsBoundaries() {
        val startMs = 1_000L
        val windowMs = 10_000L
        val limitMs = 5_000L

        assertFalse(windowExpired(startMs + windowMs - 1L, startMs, windowMs))
        assertFalse(windowExpired(startMs + windowMs, startMs, windowMs))
        assertTrue(windowExpired(startMs + windowMs + 1L, startMs, windowMs))
        assertEquals(1_000L, remaining(limitMs, 4_000L))
        assertEquals(0L, remaining(limitMs, limitMs))
        assertEquals(0L, remaining(limitMs, limitMs + 1L))

        val availability = method(
            accessibilitySource,
            "private fun isAllowanceAvailable(",
            "\n    private fun recordAllowanceOpen(",
        )
        val openRecording = method(
            accessibilitySource,
            "private fun recordAllowanceOpen(",
            "\n    private fun accumulateTimedUsage(",
        )
        val unlockRemaining = between(
            accessibilitySource,
            "val remainingMs = when (entry.mode)",
            "if (remainingMs <= 0L)",
        )
        val fallback = method(
            foregroundSource,
            "private fun isFallbackBlocked(",
            "\n    private fun handleFallbackBlock(",
        )
        val launcher = method(
            launcherSource,
            "private fun loadAllowanceCardData(",
            "\n    private fun formatRemainingMs(",
        )

        assertContains(availability, "readAllowance(pkg, entry, now).exhausted")
        assertContains(openRecording, "allowanceLedger.recordOpen(")
        assertContains(unlockRemaining, "readAllowance(pkg, entry, now).remaining")
        assertContains(fallback, "allowanceLedger.readAllowance(")
        assertContains(launcher, "allowanceLedger.readAllowance(")
        assertContains(ledgerSource, "nowMs > record.windowStartMs + windowMs")
    }

    @Test
    fun yesterdayAllowanceJsonIsTreatedAsZeroByAllDailyReaders() {
        val yesterday = JSONObject(
            """{"mode":"time_budget","date":"2026-10-07","usedMs":1200000,"count":4}""",
        )
        val today = "2026-10-08"

        assertEquals(0L, dailyValue(yesterday, today, "usedMs"))
        assertEquals(0L, dailyValue(yesterday, today, "count"))

        val availability = method(
            accessibilitySource,
            "private fun isAllowanceAvailable(",
            "\n    private fun recordAllowanceOpen(",
        )
        val unlockRemaining = between(
            accessibilitySource,
            "val remainingMs = when (entry.mode)",
            "if (remainingMs <= 0L)",
        )
        val fallback = method(
            foregroundSource,
            "private fun isFallbackBlocked(",
            "\n    private fun handleFallbackBlock(",
        )
        val launcher = method(
            launcherSource,
            "private fun loadAllowanceCardData(",
            "\n    private fun formatRemainingMs(",
        )

        assertContains(availability, "readAllowance(pkg, entry, now)")
        assertContains(unlockRemaining, "readAllowance(pkg, entry, now).remaining")
        assertContains(fallback, "allowanceLedger.readAllowance(")
        assertContains(launcher, "allowanceLedger.readAllowance(")
    }

    @Test
    fun allFourReadPathsAgreeOnRemainingAndExhaustedValues() {
        val accessibility = method(
            accessibilitySource,
            "private fun isAllowanceAvailable(",
            "\n    private fun recordAllowanceOpen(",
        )
        val unlockRemaining = between(
            accessibilitySource,
            "val remainingMs = when (entry.mode)",
            "if (remainingMs <= 0L)",
        )
        val fallback = method(
            foregroundSource,
            "private fun isFallbackBlocked(",
            "\n    private fun handleFallbackBlock(",
        )
        val launcher = method(
            launcherSource,
            "private fun loadAllowanceCardData(",
            "\n    private fun formatRemainingMs(",
        )

        assertContains(accessibility, "readAllowance(pkg, entry, now).exhausted")
        assertContains(unlockRemaining, "readAllowance(pkg, entry, now).remaining")
        assertContains(fallback, "allowanceLedger.readAllowance(")
        assertContains(accessibilitySource, "if (remainingMs <= 0L)")
        assertContains(launcher, "allowanceLedger.readAllowance(")
        assertContains(
            ledgerSource,
            "val remaining = (limit - consumed).coerceAtLeast(0L)",
        )
        assertContains(ledgerSource, "consumed >= limit")

        listOf(1L, 5L, 5L).forEach { limit ->
            assertEquals(1L, remaining(limit, limit - 1L))
            assertTrue(isExhausted(limit, limit))
            assertEquals(0L, remaining(limit, limit))
            assertTrue(isExhausted(limit, limit + 1L))
            assertEquals(0L, remaining(limit, limit + 1L))
        }
    }

    @Test
    fun existingDailyAllowanceJsonSnapshotRetainsItsOriginalValues() {
        val snapshot = JSONObject(
            """
            {
              "com.example.timer": {
                "mode": "time_budget",
                "date": "2026-10-08",
                "usedMs": 1200000
              },
              "com.example.counter": {
                "mode": "count",
                "date": "2026-10-08",
                "count": 2
              },
              "com.example.interval": {
                "mode": "interval",
                "date": "2026-10-08",
                "windowStartMs": 1791420000000,
                "usedMs": 180000
              }
            }
            """.trimIndent(),
        )

        val timeBudget = snapshot.getJSONObject("com.example.timer")
        assertEquals("time_budget", timeBudget.getString("mode"))
        assertEquals("2026-10-08", timeBudget.getString("date"))
        assertEquals(1_200_000L, timeBudget.getLong("usedMs"))
        assertFalse(timeBudget.has("confirmedUsedMs"))
        assertFalse(timeBudget.has("estimatedExtraMs"))

        val count = snapshot.getJSONObject("com.example.counter")
        assertEquals("count", count.getString("mode"))
        assertEquals(2, count.getInt("count"))
        assertFalse(count.has("confirmedCount"))
        assertFalse(count.has("estimatedExtraOpens"))

        val interval = snapshot.getJSONObject("com.example.interval")
        assertEquals("interval", interval.getString("mode"))
        assertEquals(1_791_420_000_000L, interval.getLong("windowStartMs"))
        assertEquals(180_000L, interval.getLong("usedMs"))
        assertTrue(
            ledgerSource.contains(
                "const val PREF_DAILY_ALLOWANCE_USED = \"daily_allowance_used\"",
            ),
        )
    }

    private fun dailyValue(usage: JSONObject, today: String, field: String): Long =
        if (usage.optString("date", "") == today) usage.optLong(field, 0L) else 0L

    private fun windowExpired(nowMs: Long, startMs: Long, durationMs: Long): Boolean =
        nowMs > startMs + durationMs

    private fun remaining(limit: Long, used: Long): Long = (limit - used).coerceAtLeast(0L)

    private fun isExhausted(limit: Long, used: Long): Boolean = used >= limit

    private fun method(source: String, start: String, end: String): String =
        between(source, start, end)

    private fun between(source: String, start: String, end: String): String {
        val startIndex = source.indexOf(start)
        assertTrue("Missing source marker: $start", startIndex >= 0)
        val endIndex = source.indexOf(end, startIndex + start.length)
        assertTrue("Missing source marker: $end", endIndex > startIndex)
        return source.substring(startIndex, endIndex)
    }

    private fun assertContains(source: String, expected: String) {
        assertTrue("Expected source to contain: $expected", expected in source)
    }

    private fun source(relativePath: String): String {
        val appRoot = findAppRoot(Path.of(System.getProperty("user.dir")))
        return appRoot.resolve("src/main/java/com/tbtechs/focusflow")
            .resolve(relativePath)
            .toFile()
            .readText(Charsets.UTF_8)
    }

    private fun findAppRoot(start: Path): Path {
        var current: Path? = start.toAbsolutePath().normalize()
        while (current != null) {
            if (Files.isDirectory(current.resolve("src/main/java/com/tbtechs/focusflow"))) {
                return current
            }
            if (Files.isDirectory(current.resolve("app/src/main/java/com/tbtechs/focusflow"))) {
                return current.resolve("app")
            }
            current = current.parent
        }
        throw AssertionError("Could not find the app module from ${start.toAbsolutePath()}")
    }
}
