package com.tbtechs.focusflow.analytics.detection

import com.tbtechs.focusflow.data.local.dao.AppSessionDao
import com.tbtechs.focusflow.data.local.dao.AppUsageRangeRow
import com.tbtechs.focusflow.data.local.dao.ClarifyingQuestionDao
import com.tbtechs.focusflow.data.local.dao.DailyAppUsageDao
import com.tbtechs.focusflow.data.local.dao.DayRatingDao
import com.tbtechs.focusflow.data.local.dao.FindingAcknowledgementDao
import com.tbtechs.focusflow.data.local.dao.FindingDao
import com.tbtechs.focusflow.data.local.dao.FocusSessionDao
import com.tbtechs.focusflow.data.local.dao.TaskDao
import com.tbtechs.focusflow.data.local.entity.DailyAppUsageEntity
import com.tbtechs.focusflow.data.local.entity.DayRatingEntity
import com.tbtechs.focusflow.data.local.entity.FindingEntity
import com.tbtechs.focusflow.data.repository.ClarifyingQuestionRepository
import com.tbtechs.focusflow.data.repository.FindingRepository
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test

class FindingDetectionRunnerImpl5Test {

    @Test
    fun dailyRunnerSubmitsSubstitutionAndAllowanceSuggestionFindings() = runBlocking {
        val today = LocalDate.now()
        val usageRows = (0..29).flatMap { daysAgo ->
            val date = today.minusDays(daysAgo.toLong())
            val downMinutes = when (daysAgo) {
                in 0..4 -> 15
                in 5..6 -> 50
                7 -> 42
                in 8..13 -> 42
                in 14..20 -> 50
                else -> 60
            }
            listOf(
                usageRow("down", date, downMinutes, "social"),
                usageRow("up", date, 120 - downMinutes, "entertainment"),
            )
        }
        val ratings = (0..7).map { daysAgo ->
            DayRatingEntity(
                date = today.minusDays(daysAgo.toLong()).toString(),
                rating = if (daysAgo < 5) 8 else 3,
                contextTag = null,
                note = null,
                appTags = "[]",
                wordTags = "[]",
                createdAt = Instant.EPOCH.toString(),
                updatedAt = Instant.EPOCH.toString(),
            )
        }
        val dailyUsageDao = RecordingDailyAppUsageDao(usageRows)
        val submitted = mutableListOf<FindingEntity>()
        val findingDao = fakeDao(FindingDao::class.java) { method, args ->
            when (method.name) {
                "getExisting", "getMostRecentDetected" -> null
                "insert" -> {
                    submitted += args[0] as FindingEntity
                    Unit
                }
                else -> null
            }
        }
        val acknowledgementDao =
            fakeDao(FindingAcknowledgementDao::class.java) { _, _ -> Unit }

        val runner = FindingDetectionRunner(
            taskDao = fakeDao(TaskDao::class.java) { method, _ ->
                if (method.name == "getTasksInDateRange") emptyList<Any>() else null
            },
            focusSessionDao = fakeDao(FocusSessionDao::class.java) { method, _ ->
                when (method.name) {
                    "getSessionsWithOverrideCount", "getEstimationErrors" -> emptyList<Any>()
                    else -> null
                }
            },
            findingRepository = FindingRepository(findingDao, acknowledgementDao),
            dailyAppUsageDao = dailyUsageDao,
            appSessionDao = fakeDao(AppSessionDao::class.java) { method, _ ->
                when (method.name) {
                    "getFirstSessionEachDay",
                    "getSessionStatsByDay",
                    "getSessionsForPackageInRange" -> emptyList<Any>()
                    else -> null
                }
            },
            dayRatingDao = fakeDao(DayRatingDao::class.java) { method, args ->
                if (method.name == "getForDateRange") {
                    val start = args[0] as String
                    val end = args[1] as String
                    ratings.filter { it.date in start..end }
                } else {
                    null
                }
            },
            clarifyingQuestionRepository = ClarifyingQuestionRepository(
                fakeDao(ClarifyingQuestionDao::class.java) { method, _ ->
                    when (method.name) {
                        "countAskedSince" -> 0
                        "insert", "answer", "deleteAnsweredBefore" -> Unit
                        else -> null
                    }
                },
            ),
        )

        runner.runAll()

        assertTrue(submitted.any { it.detectionType == "SUBSTITUTION" })
        assertTrue(submitted.any { it.detectionType == "ALLOWANCE_SUGGESTION" })
        assertTrue(
            dailyUsageDao.queriedRanges.any {
                it.first == today.minusDays(28).toString() && it.second == today.toString()
            },
        )
        assertTrue(
            dailyUsageDao.queriedRanges.any {
                it.first == today.minusDays(30).toString() && it.second == today.toString()
            },
        )
    }

    private fun usageRow(
        packageName: String,
        date: LocalDate,
        minutes: Int,
        category: String,
    ) = AppUsageRangeRow(
        packageName = packageName,
        appName = packageName,
        category = category,
        date = date.toString(),
        foregroundMs = minutes * 60_000L,
        hourlyMs = "",
        launchCount = 1,
        lastUsedAt = 0L,
    )

    private class RecordingDailyAppUsageDao(
        private val rows: List<AppUsageRangeRow>,
    ) : DailyAppUsageDao() {
        val queriedRanges = mutableListOf<Pair<String, String>>()

        override suspend fun upsert(entity: DailyAppUsageEntity) = Unit

        override suspend fun getForPackageAndDate(
            date: String,
            packageName: String,
        ): DailyAppUsageEntity? = null

        override suspend fun getForDateRange(
            startDate: String,
            endDate: String,
        ): List<AppUsageRangeRow> {
            queriedRanges += startDate to endDate
            return rows.filter { it.date in startDate..endDate }
        }

        override suspend fun deleteOlderThan(cutoffDate: String) = Unit

        override suspend fun countDistinctDates(): Int = rows.map { it.date }.distinct().size
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T : Any> fakeDao(
        daoType: Class<T>,
        answer: (Method, Array<out Any?>) -> Any?,
    ): T = Proxy.newProxyInstance(
        daoType.classLoader,
        arrayOf(daoType),
    ) { _, method, arguments ->
        answer(method, arguments ?: emptyArray())
    } as T
}