package com.tbtechs.focusflow.analytics

import com.tbtechs.focusflow.data.local.entity.AppSessionEntity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class UsageHistorySourcePolicyTest {
    private val cutover = "2026-10-01"
    private val today = "2026-10-08"

    @Test
    fun shadowModeKeepsCurrentStatsAndLegacyHistory() = runBlocking {
        assertEquals(
            UsageHistorySource.CURRENT_STATS,
            UsageHistorySourcePolicy.selectDeviceStats(
                UsageHistoryReadPhase.SHADOW, today, today, "COMPLETE", true,
            ),
        )
        assertEquals(
            UsageHistorySource.LEGACY,
            source(
                "2026-10-02",
                UsageHistoryConsumer.DETECTOR,
                "COMPLETE",
                true,
                phase = UsageHistoryReadPhase.SHADOW,
            ),
        )

        val store = CountingLegacyStore()
        val repository = UsageHistoryRepository(store)
        repository.appDays("2026-10-01", today)
        repository.sessions("2026-10-01", today)
        assertEquals(2, store.legacyReads)
    }

    @Test
    fun cutoverDeviceStatsUsesLivePipelineForToday() {
        assertEquals(
            UsageHistorySource.LIVE_PIPELINE,
            UsageHistorySourcePolicy.selectDeviceStats(
                UsageHistoryReadPhase.CUTOVER, today, today, "COMPLETE", true,
            ),
        )
    }

    @Test
    fun detectorsExcludeTodayAndRatingEligibilityUsesLiveSession() {
        assertEquals(
            UsageHistorySource.EXCLUDED,
            source(today, UsageHistoryConsumer.DETECTOR, null, true, liveHasSession = true),
        )
        assertEquals(
            UsageHistorySource.LIVE_PIPELINE,
            source(today, UsageHistoryConsumer.RATING_ELIGIBILITY, null, true, liveHasSession = true),
        )
    }

    @Test
    fun legacyWinsBeforeCutoverEvenWhenShadowRollupIsComplete() {
        assertEquals(
            UsageHistorySource.LEGACY,
            source("2026-09-30", UsageHistoryConsumer.DATA_HEALTH, "COMPLETE", true),
        )
    }

    @Test
    fun postCutoverLegacyRowsAreIgnoredWhenNoCompleteRollupExists() {
        assertEquals(
            UsageHistorySource.ON_DEMAND_PIPELINE,
            source("2026-10-02", UsageHistoryConsumer.RATING_ELIGIBILITY, null, true),
        )
    }

    @Test
    fun missingYesterdayUsesOnDemandPipelineWhenEventsRemainAvailable() {
        val selected = source("2026-10-07", UsageHistoryConsumer.DATA_HEALTH, null, true)
        assertEquals(UsageHistorySource.ON_DEMAND_PIPELINE, selected)
    }

    @Test
    fun liveDaySliceMatchesTheLaterRollupForTheSameEventLog() {
        val zone = ZoneId.of("UTC")
        val start = LocalDate.parse("2026-10-07").atStartOfDay(zone).toInstant().toEpochMilli()
        val midnight = LocalDate.parse("2026-10-08").atStartOfDay(zone).toInstant().toEpochMilli()
        val session = ForegroundSession("app.a", start + 23 * HOUR, midnight + 20 * MINUTE, "A", true)
        val beforeClose = UsageCalendarAggregator.aggregate(
            sessions = listOf(session.copy(endedAtMs = midnight)),
            rangeStartMs = start,
            rangeEndMs = midnight,
            zoneId = zone,
        )
        val afterClose = UsageCalendarAggregator.aggregate(
            sessions = listOf(session),
            rangeStartMs = start,
            rangeEndMs = midnight,
            zoneId = zone,
        )
        assertEquals(beforeClose, afterClose)
    }

    @Test
    fun partialRollupIsFlaggedForDeviceStatsAndMissingToDetectors() {
        assertEquals(
            UsageHistorySource.PARTIAL_ROLLUP,
            UsageHistorySourcePolicy.selectDeviceStats(
                UsageHistoryReadPhase.CUTOVER, "2026-10-07", today, "PARTIAL", false,
            ),
        )
        assertEquals(
            UsageHistorySource.MISSING,
            source(
                "2026-10-07",
                UsageHistoryConsumer.DETECTOR,
                rollupStatus = "PARTIAL",
                eventsAvailable = false,
            ),
        )
    }

    @Test
    fun unavailableEventsForTodayAreUnknownRatherThanZero() {
        assertEquals(
            UsageHistorySource.UNKNOWN,
            UsageHistorySourcePolicy.selectDeviceStats(
                UsageHistoryReadPhase.CUTOVER, today, today, null, false,
            ),
        )
    }

    @Test
    fun clearingCutoverRestoresLegacyForEveryHistoryConsumer() {
        UsageHistoryConsumer.entries.forEach { consumer ->
            assertEquals(
                UsageHistorySource.LEGACY,
                source("2026-10-07", consumer, "COMPLETE", true, cutoverDate = null),
            )
        }
    }

    @Test
    fun selectionReturnsExactlyOneSourceAcrossModesDatesAndRows() {
        val dates = listOf("2026-09-30", cutover, "2026-10-07", today)
        val cuts = listOf(null, cutover)
        val statuses = listOf(null, "PARTIAL", "COMPLETE")
        dates.forEach { date ->
            cuts.forEach { cut ->
                statuses.forEach { status ->
                    UsageHistoryConsumer.entries.forEach { consumer ->
                        val selected = source(date, consumer, status, eventsAvailable = true, cutoverDate = cut)
                        assertTrue(selected in UsageHistorySource.entries)
                        assertFalse(selected == UsageHistorySource.MISSING && date < (cut ?: today) &&
                            consumer != UsageHistoryConsumer.DETECTOR)
                    }
                }
            }
        }
    }

    @Test
    fun seamPredicateIncludesCutoverOnEndButNotOnStart() {
        assertTrue(UsageHistorySourcePolicy.crossesSeam("2026-09-30", cutover, cutover))
        assertFalse(UsageHistorySourcePolicy.crossesSeam(cutover, today, cutover))
        assertFalse(UsageHistorySourcePolicy.crossesSeam("2026-09-30", "2026-09-30", cutover))
    }

    @Test
    fun midnightSessionHasOneStartDateAndClippedDailyTime() {
        val zone = ZoneId.of("UTC")
        val start = LocalDate.parse("2026-10-07").atTime(23, 50).atZone(zone)
            .toInstant().toEpochMilli()
        val end = LocalDate.parse("2026-10-08").atTime(0, 20).atZone(zone)
            .toInstant().toEpochMilli()
        val rows = UsageCalendarAggregator.aggregate(
            listOf(ForegroundSession("app.a", start, end, "A", true)),
            start,
            end,
            zone,
        ).associateBy { it.date }
        assertEquals(10 * MINUTE, rows.getValue("2026-10-07").foregroundMs)
        assertEquals(20 * MINUTE, rows.getValue("2026-10-08").foregroundMs)
        assertEquals(1, rows.getValue("2026-10-07").sessionCount)
        assertEquals(0, rows.getValue("2026-10-08").sessionCount)
    }

    @Test
    fun openStartDateIsPartialUntilSessionCloses() {
        val dayStart = LocalDate.parse("2026-10-07").atStartOfDay(ZoneId.of("UTC"))
            .toInstant().toEpochMilli()
        assertEquals(
            "PARTIAL",
            UsageRollupCoverage.status(dayStart - 1, dayStart, unresolvedSessionStartedOnDate = true),
        )
        assertEquals(
            "COMPLETE",
            UsageRollupCoverage.status(dayStart - 1, dayStart, unresolvedSessionStartedOnDate = false),
        )
    }

    private fun source(
        date: String,
        consumer: UsageHistoryConsumer,
        rollupStatus: String?,
        eventsAvailable: Boolean,
        cutoverDate: String? = cutover,
        liveHasSession: Boolean = false,
        phase: UsageHistoryReadPhase = UsageHistoryReadPhase.CUTOVER,
    ) = UsageHistorySourcePolicy.selectHistory(
        phase = phase,
        date = date,
        today = today,
        cutoverDate = cutoverDate,
        consumer = consumer,
        rollupStatus = rollupStatus,
        eventsAvailable = eventsAvailable,
        liveHasSession = liveHasSession,
    )

    private class CountingLegacyStore : UsageHistoryStore {
        var legacyReads = 0

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
    }

    private companion object {
        const val MINUTE = 60_000L
        const val HOUR = 60 * MINUTE
    }
}
