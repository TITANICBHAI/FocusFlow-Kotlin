package com.tbtechs.focusflow.analytics

import android.content.Context
import com.tbtechs.focusflow.data.repository.AppUsageInfo
import com.tbtechs.focusflow.data.repository.HourlyUsageSummary
import com.tbtechs.focusflow.data.repository.UsageSummary
import java.time.LocalDate
import java.time.ZoneId

data class DeviceUsageSnapshot(
    val summary: UsageSummary,
    val hourly: HourlyUsageSummary,
    val daily: List<UsageDaySummary>,
    val coverage: AnalyticsSnapshot.UsageCoverage,
)

/**
 * Builds all Stats device-time metrics from the same UsageEvents pass.
 * Persisted rollups remain the source for background history consumers.
 */
class DeviceUsageSource(
    private val context: Context,
    private val eventSource: UsageEventsSource,
    private val zoneId: () -> ZoneId = ZoneId::systemDefault,
) {
    private val spanTracker = ForegroundSpanTracker(
        excludedPackages = ForegroundSpanTracker.DEFAULT_EXCLUDED_PACKAGES + context.packageName,
    )

    suspend fun read(
        rangeStartMs: Long,
        rangeEndMs: Long,
        nowMs: Long,
    ): DeviceUsageSnapshot? {
        val effectiveEndMs = minOf(rangeEndMs, nowMs)
        if (rangeStartMs >= effectiveEndMs) {
            return DeviceUsageSnapshot(
                summary = UsageSummary(0, emptyList()),
                hourly = HourlyUsageSummary(List(24) { 0L }, 0L),
                daily = emptyList(),
                coverage = AnalyticsSnapshot.UsageCoverage(0, 0, 0),
            )
        }

        val zone = zoneId()
        val lookbackStartMs = rangeStartMs - SESSION_LOOKBACK_MS
        val eventRead = eventSource.readForegroundEvents(lookbackStartMs, effectiveEndMs)
        if (eventRead is UsageEventRead.Unknown) {
            throw DeviceUsageUnavailableException(eventRead.reason.name)
        }
        eventRead as UsageEventRead.Available
        if (eventRead.events.isEmpty() && eventRead.earliestEventAtMs == null) {
            throw DeviceUsageUnavailableException("No UsageEvents were returned")
        }

        val sessions = spanTracker.sessions(
            events = eventRead.events,
            windowStartMs = rangeStartMs,
            windowEndMs = effectiveEndMs,
            nowMs = effectiveEndMs,
        )
        val appDays = UsageCalendarAggregator.aggregate(
            sessions = sessions,
            rangeStartMs = rangeStartMs,
            rangeEndMs = effectiveEndMs,
            zoneId = zone,
        )
        val displayRows = appDays
            .groupBy { it.packageName }
            .mapNotNull { (packageName, rows) ->
                val foregroundMs = rows.sumOf { it.foregroundMs }
                if (foregroundMs < MIN_DISPLAY_FOREGROUND_MS || !isLaunchable(packageName)) {
                    return@mapNotNull null
                }
                packageName to rows
            }

        val apps = displayRows.map { (packageName, rows) ->
            val foregroundMs = rows.sumOf { it.foregroundMs }
            AppUsageInfo(
                packageName = packageName,
                appName = UsageAppMetadata.resolveAppName(context, packageName),
                foregroundMinutes = (foregroundMs / MILLIS_PER_MINUTE).toInt(),
                launchCount = rows.sumOf { it.launchCount },
                lastUsedAt = rows.maxOfOrNull { it.lastUsedAtMs } ?: 0L,
            )
        }.sortedByDescending { it.foregroundMinutes }

        val hourlyMilliseconds = List(24) { hour ->
            appDays.sumOf { it.hourlyMs.getOrElse(hour) { 0L } }
        }
        val daily = appDays.groupBy { it.date }
            .map { (date, rows) ->
                val localDate = LocalDate.parse(date)
                UsageDaySummary(
                    date = date,
                    dayOfWeek = localDate.dayOfWeek.value % 7,
                    totalMinutes = (rows.sumOf { it.foregroundMs } / MILLIS_PER_MINUTE).toInt(),
                )
            }
            .sortedBy { it.date }
        val coverage = buildCoverage(
            rangeStartMs = rangeStartMs,
            rangeEndMs = effectiveEndMs,
            earliestEventAtMs = eventRead.earliestEventAtMs,
            sessions = sessions,
            zoneId = zone,
        )

        return DeviceUsageSnapshot(
            summary = UsageSummary(
                totalMinutes = (displayRows.sumOf { (_, rows) -> rows.sumOf { it.foregroundMs } } /
                    MILLIS_PER_MINUTE).toInt(),
                apps = apps,
            ),
            hourly = HourlyUsageSummary(
                foregroundMillisecondsByHour = hourlyMilliseconds,
                totalForegroundMilliseconds = hourlyMilliseconds.sum(),
            ),
            daily = daily,
            coverage = coverage,
        )
    }

    private fun buildCoverage(
        rangeStartMs: Long,
        rangeEndMs: Long,
        earliestEventAtMs: Long?,
        sessions: List<ForegroundSession>,
        zoneId: ZoneId,
    ): AnalyticsSnapshot.UsageCoverage {
        val firstDate = Instant.ofEpochMilli(rangeStartMs).atZone(zoneId).toLocalDate()
        val lastDate = Instant.ofEpochMilli(rangeEndMs).atZone(zoneId).toLocalDate()
        var complete = 0
        var partial = 0
        var missing = 0
        var date = firstDate
        while (!date.isAfter(lastDate)) {
            val dayStart = date.atStartOfDay(zoneId).toInstant().toEpochMilli()
            val dayEnd = date.plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli()
            when {
                earliestEventAtMs == null || earliestEventAtMs >= dayEnd -> missing++
                earliestEventAtMs <= dayStart &&
                    sessions.none {
                        it.isInferredTail &&
                            Instant.ofEpochMilli(it.startedAtMs).atZone(zoneId).toLocalDate() == date
                    } -> complete++
                else -> partial++
            }
            date = date.plusDays(1)
        }
        return AnalyticsSnapshot.UsageCoverage(complete, partial, missing)
    }

    private fun isLaunchable(packageName: String): Boolean =
        try {
            context.packageManager.getLaunchIntentForPackage(packageName) != null
        } catch (_: Exception) {
            false
        }

    companion object {
        private const val SESSION_LOOKBACK_MS = 6L * 60L * 60L * 1_000L
        private const val MILLIS_PER_MINUTE = 60_000L
        private const val MIN_DISPLAY_FOREGROUND_MS = 500L
    }
}

class DeviceUsageUnavailableException(message: String) : IllegalStateException(message)
