package com.tbtechs.focusflow.analytics.detection

import android.util.Log
import com.tbtechs.focusflow.data.local.dao.AppSessionDao
import com.tbtechs.focusflow.data.local.dao.DailyAppUsageDao
import com.tbtechs.focusflow.data.local.dao.DayRatingDao
import com.tbtechs.focusflow.data.local.dao.FocusSessionDao
import com.tbtechs.focusflow.data.local.dao.TaskDao
import com.tbtechs.focusflow.data.local.entity.AppSessionEntity
import com.tbtechs.focusflow.data.local.entity.ClarifyingQuestionEntity
import com.tbtechs.focusflow.data.repository.ClarifyingQuestionRepository
import com.tbtechs.focusflow.data.repository.FindingRepository
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.UUID

/**
 * Runs the existing-data and manipulation detectors against their own rolling
 * windows.
 * Each detector is isolated so one malformed row cannot prevent the others
 * from producing findings.
 */
class FindingDetectionRunner(
    private val taskDao: TaskDao,
    private val focusSessionDao: FocusSessionDao,
    private val findingRepository: FindingRepository,
    private val dailyAppUsageDao: DailyAppUsageDao,
    private val appSessionDao: AppSessionDao,
    private val dayRatingDao: DayRatingDao,
    private val clarifyingQuestionRepository: ClarifyingQuestionRepository,
) {
    private companion object {
        private const val TAG = "FindingDetectionRunner"
        private val ISO_DATE = DateTimeFormatter.ISO_LOCAL_DATE
    }

    suspend fun runAll() {
        runCatching { runPostFailureCascade() }
            .onFailure { Log.e(TAG, "postFailureCascade failed", it) }
        runCatching { runSessionSweetSpot() }
            .onFailure { Log.e(TAG, "sessionSweetSpot failed", it) }
        runCatching { runEstimationDrift() }
            .onFailure { Log.e(TAG, "estimationDrift failed", it) }
        runCatching { runDayOfWeekOutlier() }
            .onFailure { Log.e(TAG, "dayOfWeekOutlier failed", it) }
        runCatching { runMorningHijack() }
            .onFailure { Log.e(TAG, "morningHijack failed", it) }
        runCatching { runVariableRewardLoop() }
            .onFailure { Log.e(TAG, "variableRewardLoop failed", it) }
        runCatching { runEscalatingCapture() }
            .onFailure { Log.e(TAG, "escalatingCapture failed", it) }
        runCatching { runStreakLockIn() }
            .onFailure { Log.e(TAG, "streakLockIn failed", it) }
        runCatching { runInfiniteSessionDesign() }
            .onFailure { Log.e(TAG, "infiniteSessionDesign failed", it) }
        runCatching { runSubstitution() }
            .onFailure { Log.e(TAG, "substitution failed", it) }
        runCatching { runAllowanceSuggestion() }
            .onFailure { Log.e(TAG, "allowanceSuggestion failed", it) }
    }

    private suspend fun runPostFailureCascade() {
        val tasks = taskDao.getTasksInDateRange(dateRangeStart(60), dateRangeEnd())
        detectPostFailureCascade(tasks)?.let { findingRepository.submit(it) }
    }

    private suspend fun runSessionSweetSpot() {
        val sessions = focusSessionDao.getSessionsWithOverrideCount(
            startISO = isoRangeStart(90),
            endISO = isoRangeEnd(),
        )
        detectSessionSweetSpot(sessions)?.let { findingRepository.submit(it) }
    }

    private suspend fun runEstimationDrift() {
        val errors = focusSessionDao.getEstimationErrors(
            startISO = isoRangeStart(60),
            endISO = isoRangeEnd(),
        )
        detectEstimationDrift(errors)?.let { findingRepository.submit(it) }
    }

    private suspend fun runDayOfWeekOutlier() {
        val tasks = taskDao.getTasksInDateRange(dateRangeStart(42), dateRangeEnd())
        detectDayOfWeekOutlier(tasks)?.let { findingRepository.submit(it) }
    }

    private suspend fun runMorningHijack() {
        val start = dateRangeStart(14)
        val end = dateRangeEnd()
        val firstSessions = appSessionDao.getFirstSessionEachDay(start, end)
        if (firstSessions.isEmpty()) return

        val usageRows = dailyAppUsageDao.getForDateRange(start, end)
        val categories = usageRows
            .groupBy { it.packageName }
            .mapValues { (_, rows) ->
                rows.asReversed().firstOrNull { it.category != null }?.category
            }

        val result = detectMorningHijack(firstSessions, categories) ?: return
        if (!findingRepository.submit(result.finding)) return

        maybeQueueMorningHijackQuestion(result)
    }

    private suspend fun maybeQueueMorningHijackQuestion(result: MorningHijackResult) {
        val dates = result.qualifyingDates.sorted()
        if (dates.isEmpty()) return

        val ratings = dayRatingDao.getForDateRange(
            startDate = dates.first(),
            endDate = dates.last(),
        ).filter { it.date in result.qualifyingDates }
        if (ratings.size < 3) return

        val averageRating = ratings.map { it.rating }.average()
        if (averageRating < 7.0) return

        val mostRecent = ratings.maxByOrNull { it.date } ?: return
        val appName = result.finding.subjectAppName ?: return
        val contextJson = """{"appName":"${jsonEscape(appName)}","date":"${mostRecent.date}","rating":"${mostRecent.rating}"}"""

        clarifyingQuestionRepository.submitIfAllowed(
            ClarifyingQuestionEntity(
                id = UUID.randomUUID().toString(),
                questionType = "morning_high_rating",
                dateOfConcern = mostRecent.date,
                contextJson = contextJson,
                askedAt = Instant.now().toString(),
                response = null,
                respondedAt = null,
            ),
        )
    }

    private suspend fun runVariableRewardLoop() {
        val start = dateRangeStart(21)
        val end = dateRangeEnd()
        val dailyStats = appSessionDao.getSessionStatsByDay(start, end)
        if (dailyStats.isEmpty()) return

        val usageRows = dailyAppUsageDao.getForDateRange(start, end)
        val appNames = usageRows.associate { it.packageName to it.appName }
        val roughCandidates = dailyStats
            .groupBy { it.packageName }
            .filter { (_, days) ->
                days.size >= 10 &&
                    days.sumOf { it.sessionCount }.toDouble() / days.size >= 5.0
            }
            .keys
        val rawByPackage = loadRawSessions(roughCandidates, start, end)

        detectVariableRewardLoop(dailyStats, rawByPackage, appNames)
            ?.let { findingRepository.submit(it) }
    }

    private suspend fun runInfiniteSessionDesign() {
        val start = dateRangeStart(21)
        val end = dateRangeEnd()
        val dailyStats = appSessionDao.getSessionStatsByDay(start, end)
        if (dailyStats.isEmpty()) return

        val usageRows = dailyAppUsageDao.getForDateRange(start, end)
        val appNames = usageRows.associate { it.packageName to it.appName }
        val categories = usageRows
            .groupBy { it.packageName }
            .mapValues { (_, rows) ->
                rows.asReversed().firstOrNull { it.category != null }?.category
            }
        val roughCandidates = dailyStats
            .groupBy { it.packageName }
            .filter { (packageName, days) ->
                categories[packageName] != "utility" && days.size >= 14
            }
            .keys
        val rawByPackage = loadRawSessions(roughCandidates, start, end)

        detectInfiniteSessionDesign(dailyStats, rawByPackage, appNames, categories)
            ?.let { findingRepository.submit(it) }
    }

    private suspend fun runEscalatingCapture() {
        val start = dateRangeStart(28)
        val end = dateRangeEnd()
        val usageRows = dailyAppUsageDao.getForDateRange(start, end)
        if (usageRows.isEmpty()) return

        detectEscalatingCapture(usageRows, LocalDate.now())
            ?.let { findingRepository.submit(it) }
    }

    private suspend fun runStreakLockIn() {
        val windowDays = 14
        val start = dateRangeStart(windowDays.toLong())
        val end = dateRangeEnd()
        val usageRows = dailyAppUsageDao.getForDateRange(start, end)
        if (usageRows.isEmpty()) return

        val ratings = dayRatingDao.getForDateRange(start, end)
        detectStreakLockIn(usageRows, ratings, windowDays)
            ?.let { findingRepository.submit(it) }
    }

    private suspend fun runSubstitution() {
        val start = dateRangeStart(28)
        val end = dateRangeEnd()
        val usageRows = dailyAppUsageDao.getForDateRange(start, end)
        if (usageRows.isEmpty()) return

        detectSubstitution(usageRows, LocalDate.now())
            ?.let { findingRepository.submit(it) }
    }

    private suspend fun runAllowanceSuggestion() {
        val start = dateRangeStart(30)
        val end = dateRangeEnd()
        val usageRows = dailyAppUsageDao.getForDateRange(start, end)
        if (usageRows.isEmpty()) return

        val ratings = dayRatingDao.getForDateRange(start, end)
        detectAllowanceSuggestion(usageRows, ratings)
            ?.let { findingRepository.submit(it) }
    }

    private suspend fun loadRawSessions(
        packages: Set<String>,
        startDate: String,
        endDate: String,
    ): Map<String, List<AppSessionEntity>> {
        val sessionsByPackage = mutableMapOf<String, List<AppSessionEntity>>()
        for (packageName in packages) {
            sessionsByPackage[packageName] =
                appSessionDao.getSessionsForPackageInRange(packageName, startDate, endDate)
        }
        return sessionsByPackage
    }

    private fun dateRangeStart(days: Long): String =
        LocalDate.now().minusDays(days).format(ISO_DATE)

    private fun dateRangeEnd(): String =
        LocalDate.now().format(ISO_DATE)

    private fun isoRangeStart(days: Long): String =
        LocalDateTime.of(LocalDate.now().minusDays(days), LocalTime.MIDNIGHT).toString()

    private fun isoRangeEnd(): String =
        LocalDateTime.now().toString()

    private fun jsonEscape(value: String): String =
        value.replace("\\", "\\\\").replace("\"", "\\\"")
}