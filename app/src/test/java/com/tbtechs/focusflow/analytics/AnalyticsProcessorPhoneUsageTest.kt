package com.tbtechs.focusflow.analytics

import com.tbtechs.focusflow.data.repository.HourlyUsageSummary
import com.tbtechs.focusflow.data.repository.UsageSummary
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Test

class AnalyticsProcessorPhoneUsageTest {
    private val now = ZonedDateTime.parse("2026-10-08T12:00:00Z")

    @Test
    fun phoneUsageCardRemainsAvailableWhenHourlyDataIsMissing() {
        val snapshot = createAnalyticsSnapshot(
            window = ANALYTICS_WEEK,
            range = AnalyticsRange(start = now.minusDays(6), end = now),
            source = source(
                usageSummary = UsageSummary(totalMinutes = 25, apps = emptyList()),
                usageHourly = null,
            ),
            generatedAt = now.toInstant().toString(),
        )

        val phoneUsage = snapshot.phoneUsage ?: error("Expected phone usage from summary data")
        assertEquals(25, phoneUsage.totalMinutes)
        assertEquals(0.0, phoneUsage.byHour.values.sum(), 0.0)
    }

    @Test
    fun phoneUsageCardRemainsAvailableWhenSummaryDataIsMissing() {
        val hourly = HourlyUsageSummary(
            foregroundMillisecondsByHour = List(24) { hour ->
                if (hour == 9) 20L * 60L * 1_000L else 0L
            },
            totalForegroundMilliseconds = 20L * 60L * 1_000L,
        )
        val snapshot = createAnalyticsSnapshot(
            window = ANALYTICS_WEEK,
            range = AnalyticsRange(start = now.minusDays(6), end = now),
            source = source(usageSummary = null, usageHourly = hourly),
            generatedAt = now.toInstant().toString(),
        )

        val phoneUsage = snapshot.phoneUsage ?: error("Expected phone usage from hourly data")
        assertEquals(20, phoneUsage.totalMinutes)
        assertEquals(20.0, phoneUsage.byHour.getValue(9), 0.0)
    }

    private fun source(
        usageSummary: UsageSummary?,
        usageHourly: HourlyUsageSummary?,
    ) = AnalyticsSourceData(
        tasks = emptyList(),
        sessions = emptyList(),
        estimationErrors = emptyList(),
        tasksByHour = emptyList(),
        weeklyRates = emptyList(),
        temptations = emptyList(),
        usageSummary = usageSummary,
        usageHourly = usageHourly,
    )
}
