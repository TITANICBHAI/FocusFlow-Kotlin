package com.tbtechs.focusflow.analytics

import com.tbtechs.focusflow.analytics.detection.UsageDetectorWindow
import com.tbtechs.focusflow.analytics.detection.UsageDetectorWindows
import com.tbtechs.focusflow.data.local.entity.AppSessionEntity
import com.tbtechs.focusflow.data.local.entity.UsagePipelineStateEntity
import com.tbtechs.focusflow.data.local.entity.UsageRollupAppDayEntity
import com.tbtechs.focusflow.data.local.entity.UsageRollupDayEntity
import com.tbtechs.focusflow.data.local.entity.UsageRollupSessionEntity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.temporal.ChronoUnit

class UsageDetectorSeamTest {
    private val today = LocalDate.parse("2026-10-08")

    @Test
    fun everyUsageDetectorUsesLegacyForAWindowEntirelyBeforeCutover() = runBlocking {
        UsageDetectorWindow.entries.forEach { detector ->
            val range = UsageDetectorWindows.range(detector, today)
            val store = TestHistoryStore(cutoverDate = range.end.plusDays(1).toString())

            assertTrue(
                "Expected legacy history for $detector",
                UsageHistoryRepository(store).detectorHistory(
                    range.start.toString(),
                    range.end.toString(),
                    today.toString(),
                ) != null,
            )
            assertEquals(1, store.legacyReads)
            assertEquals(0, store.rollupReads)
        }
    }

    @Test
    fun everyUsageDetectorUsesCompleteRollupsAfterCutover() = runBlocking {
        UsageDetectorWindow.entries.forEach { detector ->
            val range = UsageDetectorWindows.range(detector, today)
            val store = TestHistoryStore(
                cutoverDate = range.start.toString(),
                completeRollups = true,
            )

            assertTrue(
                "Expected complete rollup history for $detector",
                UsageHistoryRepository(store).detectorHistory(
                    range.start.toString(),
                    range.end.toString(),
                    today.toString(),
                ) != null,
            )
            assertEquals(0, store.legacyReads)
            assertEquals(1, store.rollupReads)
        }
    }

    @Test
    fun everyUsageDetectorRefusesWindowsCrossingTheCutoverSeam() = runBlocking {
        UsageDetectorWindow.entries.forEach { detector ->
            val range = UsageDetectorWindows.range(detector, today)
            val store = TestHistoryStore(
                cutoverDate = range.start.plusDays(1).toString(),
                completeRollups = true,
            )

            assertNull(
                "Expected seam-crossing history to be refused for $detector",
                UsageHistoryRepository(store).detectorHistory(
                    range.start.toString(),
                    range.end.toString(),
                    today.toString(),
                ),
            )
            assertEquals(0, store.legacyReads)
            assertEquals(0, store.rollupReads)
        }
    }

    @Test
    fun everyUsageDetectorWindowEndsYesterdayAndRepositoryClampsToday() = runBlocking {
        UsageDetectorWindow.entries.forEach { detector ->
            val range = UsageDetectorWindows.range(detector, today)
            assertEquals(today.minusDays(1), range.end)
            assertEquals(
                detector.days,
                ChronoUnit.DAYS.between(range.start, range.end) + 1,
            )
            assertFalse(range.end == today)

            val store = TestHistoryStore(
                cutoverDate = range.start.toString(),
                completeRollups = true,
            )
            val result = UsageHistoryRepository(store).detectorHistory(
                range.start.toString(),
                today.toString(),
                today.toString(),
            )
            assertTrue(result != null)
            assertEquals(today.minusDays(1).toString(), store.lastRollupEnd)
        }
    }

    private class TestHistoryStore(
        cutoverDate: String,
        private val completeRollups: Boolean = false,
    ) : UsageHistoryStore {
        private val state = UsagePipelineStateEntity(
            cutoverDate = cutoverDate,
            pipelineVersion = UsagePipelineStateEntity.CURRENT_PIPELINE_VERSION,
            shadowStartedOn = "2026-09-01",
        )
        var legacyReads = 0
        var rollupReads = 0
        var lastRollupEnd: String? = null

        override suspend fun legacyAppDays(
            startDate: String,
            endDate: String,
        ): List<UsageHistoryAppDay> {
            legacyReads++
            return emptyList()
        }

        override suspend fun legacySessions(
            startDate: String,
            endDate: String,
        ): List<AppSessionEntity> {
            legacyReads++
            return emptyList()
        }

        override suspend fun pipelineState(): UsagePipelineStateEntity = state

        override suspend fun rollupDays(
            startDate: String,
            endDate: String,
        ): List<UsageRollupDayEntity> {
            rollupReads++
            lastRollupEnd = endDate
            if (!completeRollups) return emptyList()
            val first = LocalDate.parse(startDate)
            val end = LocalDate.parse(endDate)
            return generateSequence(first) { it.plusDays(1) }
                .takeWhile { !it.isAfter(end) }
                .map { date ->
                    UsageRollupDayEntity(
                        date = date.toString(),
                        status = UsageRollupDayEntity.COMPLETE,
                        coverageStartMs = null,
                        coverageEndMs = null,
                        pipelineVersion = UsagePipelineStateEntity.CURRENT_PIPELINE_VERSION,
                        computedAtMs = 1L,
                        totalForegroundMs = 0L,
                    )
                }
                .toList()
        }

        override suspend fun rollupAppDays(date: String): List<UsageRollupAppDayEntity> = emptyList()

        override suspend fun rollupSessions(
            startDate: String,
            endDate: String,
        ): List<UsageRollupSessionEntity> = emptyList()
    }
}
