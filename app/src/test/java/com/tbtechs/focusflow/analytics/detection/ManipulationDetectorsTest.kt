package com.tbtechs.focusflow.analytics.detection

import com.tbtechs.focusflow.data.local.dao.AppUsageRangeRow
import com.tbtechs.focusflow.data.local.dao.FirstSessionRow
import com.tbtechs.focusflow.data.local.dao.SessionStatRow
import com.tbtechs.focusflow.data.local.entity.AppSessionEntity
import com.tbtechs.focusflow.data.local.entity.DayRatingEntity
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ManipulationDetectorsTest {

    @Test
    fun variableRewardLoopRequiresHighFrequencyShortSessionsAndChoosesHighestCv() {
        val stats = listOf(
            *(1..10).map {
                SessionStatRow("steady", "2026-01-$it", 5, 120_000.0, 120_000, 120_000, 600_000)
            }.toTypedArray(),
            *(1..10).map {
                SessionStatRow("variable", "2026-01-$it", 5, 120_000.0, 60_000, 1_200_000, 600_000)
            }.toTypedArray(),
        )
        val raw = mapOf(
            "steady" to (1..50).map { appSession("steady", it.toLong(), 120_000) },
            "variable" to (1..50).map {
                appSession("variable", it.toLong(), if (it % 2 == 0) 60_000 else 1_200_000)
            },
        )

        val finding = detectVariableRewardLoop(
            dailyStats = stats,
            rawSessionsByPackage = raw,
            appNames = mapOf("steady" to "Steady", "variable" to "Variable"),
        )

        assertNotNull(finding)
        assertEquals("variable", finding!!.subjectPackage)
    }

    @Test
    fun infiniteSessionDesignExcludesUtilityAndRequiresLongVariance() {
        val stats = (1..14).map {
            SessionStatRow("video", "2026-01-$it", 1, 600_000.0, 60_000, 6_000_000, 600_000)
        }
        val raw = mapOf(
            "video" to buildList {
                repeat(13) { add(appSession("video", it.toLong(), 60_000)) }
                add(appSession("video", 99, 6_000_000))
            },
        )

        val finding = detectInfiniteSessionDesign(
            dailyStats = stats,
            rawSessionsByPackage = raw,
            appNames = mapOf("video" to "Video"),
            categories = mapOf("video" to "entertainment"),
        )

        assertNotNull(finding)
        assertEquals("video", finding!!.subjectPackage)
        assertNull(
            detectInfiniteSessionDesign(
                stats,
                raw,
                mapOf("video" to "Video"),
                mapOf("video" to "utility"),
            ),
        )
    }

    @Test
    fun morningHijackRequiresSevenDaysAndReturnsQualifyingDates() {
        val firstSessions = (1..7).map { day ->
            FirstSessionRow(
                localDate = "2026-02-${"%02d".format(day)}",
                packageName = if (day <= 5) "social" else "calendar",
                appName = if (day <= 5) "Social" else "Calendar",
                startedAt = day.toLong(),
            )
        }

        val result = detectMorningHijack(
            firstSessions = firstSessions,
            categories = mapOf("social" to "social", "calendar" to "utility"),
        )

        assertNotNull(result)
        assertEquals("social", result!!.packageName)
        assertEquals(5, result.qualifyingDates.size)
        assertEquals("MORNING_HIJACK", result.finding.detectionType)
    }

    @Test
    fun escalatingCaptureRequiresFourPopulatedIncreasingWeeks() {
        val today = LocalDate.of(2026, 3, 28)
        val rows = (0 until 28).map { daysAgo ->
            val week = 4 - (daysAgo / 7)
            usageRow(
                packageName = "capture",
                date = today.minusDays(daysAgo.toLong()),
                foregroundMs = week * 10L * 60_000L,
            )
        }

        val finding = detectEscalatingCapture(rows, today)

        assertNotNull(finding)
        assertEquals("capture", finding!!.subjectPackage)
        assertTrue(finding.evidenceJson.contains("\"growth_pct\":300"))
    }

    @Test
    fun streakLockInRequiresEligibleCategoryConsistencyAndLowRatings() {
        val start = LocalDate.of(2026, 4, 1)
        val rows = (0 until 14).map {
            usageRow(
                packageName = "social",
                date = start.plusDays(it.toLong()),
                foregroundMs = 10 * 60_000L,
                category = "social",
                launchCount = 1,
            )
        }
        val ratings = listOf(0, 1, 2).map {
            rating(start.plusDays(it.toLong()), 4)
        }

        val finding = detectStreakLockIn(rows, ratings, windowDays = 14)

        assertNotNull(finding)
        assertEquals("social", finding!!.subjectPackage)
        assertNull(detectStreakLockIn(rows, emptyList(), windowDays = 14))
    }

    private fun appSession(packageName: String, id: Long, durationMs: Long) =
        AppSessionEntity(
            id = id,
            packageName = packageName,
            appName = packageName,
            startedAt = id,
            endedAt = id + durationMs,
            durationMs = durationMs,
            localDate = "2026-01-01",
        )

    private fun usageRow(
        packageName: String,
        date: LocalDate,
        foregroundMs: Long,
        category: String? = "social",
        launchCount: Int = 1,
    ) = AppUsageRangeRow(
        packageName = packageName,
        appName = packageName,
        category = category,
        date = date.toString(),
        foregroundMs = foregroundMs,
        hourlyMs = "",
        launchCount = launchCount,
        lastUsedAt = 0L,
    )

    private fun rating(date: LocalDate, value: Int) =
        DayRatingEntity(
            date = date.toString(),
            rating = value,
            contextTag = null,
            note = null,
            appTags = "[]",
            wordTags = "[]",
            createdAt = Instant.EPOCH.toString(),
            updatedAt = Instant.EPOCH.toString(),
        )
}