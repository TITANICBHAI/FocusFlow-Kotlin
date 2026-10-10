package com.tbtechs.focusflow.analytics

import android.content.Context
import android.util.Log
import com.tbtechs.focusflow.data.local.entity.UsageRollupDayEntity
import com.tbtechs.focusflow.data.repository.AppUsageInfo
import com.tbtechs.focusflow.data.repository.HourlyUsageSummary
import com.tbtechs.focusflow.data.repository.UsageSummary
import kotlinx.coroutines.CancellationException
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class DeviceUsageSnapshot(
    val summary: UsageSummary,
    val hourly: HourlyUsageSummary,
    val daily: List<UsageDaySummary>,
    val coverage: AnalyticsSnapshot.UsageCoverage,
    val eventsAvailable: Boolean,
)

/**
 * Builds all Stats device-time metrics from the same UsageEvents pass.
 * Persisted rollups remain the source for background history consumers.
 */
class DeviceUsageSource(
    private val context: Context,
    private val eventSource: UsageEventsSource,
    private val usageHistoryRepository: UsageHistoryRepository,
    private val rollupWriter: UsageRollupWriter,
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
                eventsAvailable = false,
            )
        }

        val zone = zoneId()
        val lookbackStartMs = rangeStartMs - SESSION_LOOKBACK_MS
        val firstDate = Instant.ofEpochMilli(rangeStartMs).atZone(zone).toLocalDate()
        val lastDate = Instant.ofEpochMilli(effectiveEndMs).atZone(zone).toLocalDate()
        val today = Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate()
        val eventRead = eventSource.readForegroundEvents(lookbackStartMs, effectiveEndMs)
        val availableRead = (eventRead as? UsageEventRead.Available)
            ?.takeIf { it.events.isNotEmpty() || it.earliestEventAtMs != null }
        val sessions = availableRead?.let {
            spanTracker.sessions(
                events = it.events,
                windowStartMs = rangeStartMs,
                windowEndMs = effectiveEndMs,
                nowMs = effectiveEndMs,
            )
        }.orEmpty()
        val pipelineDays = UsageCalendarAggregator.aggregateByDate(
            sessions = sessions,
            rangeStartMs = rangeStartMs,
            rangeEndMs = effectiveEndMs,
            zoneId = zone,
        )

        val pastEnd = minOf(lastDate, today.minusDays(1))
        if (availableRead != null && firstDate <= pastEnd) {
            try {
                rollupWriter.writeDaysFromEvents(
                    startDate = firstDate.toString(),
                    endDate = pastEnd.toString(),
                    sessions = sessions,
                    earliestEventAtMs = availableRead.earliestEventAtMs,
                    nowMs = effectiveEndMs,
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Log.w(TAG, "Could not persist on-demand usage rollups", error)
            }
        }
        val rollups = if (firstDate <= pastEnd) {
            try {
                usageHistoryRepository.deviceStatsRollups(firstDate.toString(), pastEnd.toString())
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Log.w(TAG, "Could not read persisted usage rollups", error)
                emptyMap()
            }
        } else {
            emptyMap()
        }

        val selectedRows = mutableListOf<UsageHistoryAppDay>()
        var completeDays = 0
        var partialDays = 0
        var missingDays = 0
        var hasRollupSource = false
        var date = firstDate
        while (!date.isAfter(lastDate)) {
            val dateText = date.toString()
            val rollup = rollups[dateText]
            val source = UsageHistorySourcePolicy.selectDeviceStats(
                phase = UsageHistoryReadPhase.CUTOVER,
                date = dateText,
                today = today.toString(),
                rollupStatus = rollup?.status,
                eventsAvailable = availableRead != null,
            )
            val dayStartMs = date.atStartOfDay(zone).toInstant().toEpochMilli()
            val dayEndMs = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
            val coverageStatus = when (source) {
                UsageHistorySource.ROLLUP -> {
                    hasRollupSource = true
                    UsageRollupDayEntity.COMPLETE
                }
                UsageHistorySource.PARTIAL_ROLLUP -> {
                    hasRollupSource = true
                    UsageRollupDayEntity.PARTIAL
                }
                UsageHistorySource.LIVE_PIPELINE,
                UsageHistorySource.ON_DEMAND_PIPELINE -> pipelineCoverageStatus(
                    date = date,
                    dayStartMs = dayStartMs,
                    dayEndMs = dayEndMs,
                    earliestEventAtMs = availableRead?.earliestEventAtMs,
                    sessions = sessions,
                    zoneId = zone,
                )
                else -> null
            }

            when (source) {
                UsageHistorySource.ROLLUP,
                UsageHistorySource.PARTIAL_ROLLUP -> selectedRows += rollup?.appDays.orEmpty()
                UsageHistorySource.LIVE_PIPELINE,
                UsageHistorySource.ON_DEMAND_PIPELINE -> selectedRows +=
                    pipelineDays[dateText].orEmpty().map { it.toHistoryAppDay() }
                else -> Unit
            }
            when (coverageStatus) {
                UsageRollupDayEntity.COMPLETE -> completeDays++
                UsageRollupDayEntity.PARTIAL -> partialDays++
                else -> missingDays++
            }
            date = date.plusDays(1)
        }

        if (availableRead == null && !hasRollupSource) {
            val reason = (eventRead as? UsageEventRead.Unknown)?.reason?.name
                ?: "No UsageEvents were returned and no persisted rollups cover this range"
            throw DeviceUsageUnavailableException(reason)
        }

        val displayRows = selectedRows
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
                appName = rows.firstOrNull()?.appName?.takeIf { it.isNotBlank() }
                    ?: UsageAppMetadata.resolveAppName(context, packageName),
                foregroundMinutes = (foregroundMs / MILLIS_PER_MINUTE).toInt(),
                launchCount = rows.sumOf { it.launchCount },
                lastUsedAt = rows.maxOfOrNull { it.lastUsedAtMs } ?: 0L,
            )
        }.sortedByDescending { it.foregroundMinutes }

        val hourlyMilliseconds = List(24) { hour ->
            selectedRows.sumOf { it.hourlyMs.asHourlyList().getOrElse(hour) { 0L } }
        }
        val daily = selectedRows.groupBy { it.date }
            .map { (date, rows) ->
                val localDate = LocalDate.parse(date)
                UsageDaySummary(
                    date = date,
                    dayOfWeek = localDate.dayOfWeek.value % 7,
                    totalMinutes = (rows.sumOf { it.foregroundMs } / MILLIS_PER_MINUTE).toInt(),
                )
            }
            .sortedBy { it.date }

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
            coverage = AnalyticsSnapshot.UsageCoverage(completeDays, partialDays, missingDays),
            eventsAvailable = availableRead != null,
        )
    }

    private fun pipelineCoverageStatus(
        date: LocalDate,
        dayStartMs: Long,
        dayEndMs: Long,
        earliestEventAtMs: Long?,
        sessions: List<ForegroundSession>,
        zoneId: ZoneId,
    ): String = when {
        earliestEventAtMs == null || earliestEventAtMs >= dayEndMs -> "MISSING"
        else -> UsageRollupCoverage.status(
            earliestEventAtMs = earliestEventAtMs,
            dayStartMs = dayStartMs,
            unresolvedSessionStartedOnDate = sessions.any {
                it.isInferredTail &&
                    Instant.ofEpochMilli(it.startedAtMs).atZone(zoneId).toLocalDate() == date
            },
        )
    }

    private fun AppUsageDay.toHistoryAppDay(): UsageHistoryAppDay = UsageHistoryAppDay(
        date = date,
        packageName = packageName,
        appName = UsageAppMetadata.resolveAppName(context, packageName),
        category = UsageAppMetadata.resolveCategory(context, packageName),
        foregroundMs = foregroundMs,
        hourlyMs = hourlyMs.joinToString(","),
        launchCount = launchCount,
        lastUsedAtMs = lastUsedAtMs,
    )

    private fun String.asHourlyList(): List<Long> = split(',')
        .mapNotNull(String::toLongOrNull)
        .take(24)
        .let { values -> List(24) { values.getOrElse(it) { 0L } } }

    private fun isLaunchable(packageName: String): Boolean =
        try {
            context.packageManager.getLaunchIntentForPackage(packageName) != null
        } catch (_: Exception) {
            false
        }

    companion object {
        private const val TAG = "DeviceUsageSource"
        private const val SESSION_LOOKBACK_MS = 6L * 60L * 60L * 1_000L
        private const val MILLIS_PER_MINUTE = 60_000L
        private const val MIN_DISPLAY_FOREGROUND_MS = 500L
    }
}

class DeviceUsageUnavailableException(message: String) : IllegalStateException(message)
