package com.tbtechs.focusflow.analytics.detection

import android.util.Log
import com.tbtechs.focusflow.data.local.dao.FocusSessionDao
import com.tbtechs.focusflow.data.local.dao.TaskDao
import com.tbtechs.focusflow.data.repository.FindingRepository
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * Runs the four existing-data detectors against their own rolling windows.
 * Each detector is isolated so one malformed row cannot prevent the others
 * from producing findings.
 */
class FindingDetectionRunner(
    private val taskDao: TaskDao,
    private val focusSessionDao: FocusSessionDao,
    private val findingRepository: FindingRepository,
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

    private fun dateRangeStart(days: Long): String =
        LocalDate.now().minusDays(days).format(ISO_DATE)

    private fun dateRangeEnd(): String =
        LocalDate.now().format(ISO_DATE)

    private fun isoRangeStart(days: Long): String =
        LocalDateTime.of(LocalDate.now().minusDays(days), LocalTime.MIDNIGHT).toString()

    private fun isoRangeEnd(): String =
        LocalDateTime.now().toString()
}