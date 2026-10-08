package com.tbtechs.focusflow.analytics

import com.tbtechs.focusflow.data.local.dao.AppSessionDao
import com.tbtechs.focusflow.data.local.dao.AppUsageRangeRow
import com.tbtechs.focusflow.data.local.dao.DailyAppUsageDao
import com.tbtechs.focusflow.data.local.dao.UsageRollupDao
import com.tbtechs.focusflow.data.local.entity.AppSessionEntity
import com.tbtechs.focusflow.data.local.entity.UsagePipelineStateEntity
import com.tbtechs.focusflow.data.local.entity.UsageRollupAppDayEntity
import com.tbtechs.focusflow.data.local.entity.UsageRollupDayEntity
import com.tbtechs.focusflow.data.local.entity.UsageRollupSessionEntity
import java.time.LocalDate
import java.time.ZoneId

data class UsageHistoryAppDay(
    val date: String,
    val packageName: String,
    val appName: String,
    val category: String?,
    val foregroundMs: Long,
    val hourlyMs: String,
    val launchCount: Int,
    val lastUsedAtMs: Long,
)

data class DetectorUsageHistory(
    val appDays: List<AppUsageRangeRow>,
    val sessions: List<AppSessionEntity>,
)

data class DeviceStatsRollupDay(
    val status: String,
    val appDays: List<UsageHistoryAppDay>,
)

/**
 * Central store boundary for legacy and pipeline history. Default pipeline
 * methods keep pure legacy test stores small; Room supplies the rollup reads.
 */
interface UsageHistoryStore {
    suspend fun legacyAppDays(startDate: String, endDate: String): List<UsageHistoryAppDay>
    suspend fun legacySessions(startDate: String, endDate: String): List<AppSessionEntity>
    suspend fun pipelineState(): UsagePipelineStateEntity? = null
    suspend fun rollupDays(startDate: String, endDate: String): List<UsageRollupDayEntity> = emptyList()
    suspend fun rollupAppDays(date: String): List<UsageRollupAppDayEntity> = emptyList()
    suspend fun rollupSessions(startDate: String, endDate: String): List<UsageRollupSessionEntity> =
        emptyList()
    suspend fun liveHasSessionToday(date: String): Boolean = false
}

class UsageHistoryRepository(
    private val store: UsageHistoryStore,
) {
    suspend fun shouldWriteLegacy(today: String): Boolean =
        UsageHistorySourcePolicy.shouldWriteLegacy(
            cutoverDate = store.pipelineState()?.cutoverDate,
            today = today,
        )

    suspend fun appDays(startDate: String, endDate: String): List<UsageHistoryAppDay> =
        store.legacyAppDays(startDate, endDate)

    suspend fun sessions(startDate: String, endDate: String): List<AppSessionEntity> =
        store.legacySessions(startDate, endDate)

    suspend fun deviceStatsRollups(
        startDate: String,
        endDate: String,
    ): Map<String, DeviceStatsRollupDay> =
        store.rollupDays(startDate, endDate).associate { day ->
            day.date to DeviceStatsRollupDay(
                status = day.status,
                appDays = store.rollupAppDays(day.date).map { row ->
                    UsageHistoryAppDay(
                        date = row.date,
                        packageName = row.packageName,
                        appName = row.appName,
                        category = row.category,
                        foregroundMs = row.foregroundMs,
                        hourlyMs = row.hourlyMs,
                        launchCount = row.launchCount,
                        lastUsedAtMs = row.lastUsedAtMs,
                    )
                },
            )
        }

    /**
     * Returns one source for a detector window. Seam-crossing windows are
     * refused; after cutover only COMPLETE rollups are eligible.
     */
    suspend fun detectorHistory(
        startDate: String,
        endDate: String,
        today: String,
    ): DetectorUsageHistory? {
        val cutoverDate = store.pipelineState()?.cutoverDate
        if (UsageHistorySourcePolicy.crossesSeam(startDate, endDate, cutoverDate)) return null
        if (cutoverDate == null || endDate < cutoverDate) {
            return DetectorUsageHistory(
                appDays = store.legacyAppDays(startDate, endDate).map { row ->
                    AppUsageRangeRow(
                        packageName = row.packageName,
                        appName = row.appName,
                        category = row.category,
                        date = row.date,
                        foregroundMs = row.foregroundMs,
                        hourlyMs = row.hourlyMs,
                        launchCount = row.launchCount,
                        lastUsedAt = row.lastUsedAtMs,
                    )
                },
                sessions = store.legacySessions(startDate, endDate),
            )
        }
        if (startDate < cutoverDate || startDate >= today) return null

        val completeDates = store.rollupDays(startDate, endDate)
            .filter { it.status == UsageRollupDayEntity.COMPLETE }
            .map { it.date }
            .toSet()
        val appDays = completeDates
            .sorted()
            .flatMap(store::rollupAppDays)
            .map { row ->
                AppUsageRangeRow(
                    packageName = row.packageName,
                    appName = row.appName,
                    category = row.category,
                    date = row.date,
                    foregroundMs = row.foregroundMs,
                    hourlyMs = row.hourlyMs,
                    launchCount = row.launchCount,
                    lastUsedAt = row.lastUsedAtMs,
                )
            }
        val sessions = store.rollupSessions(startDate, endDate)
            .filter { it.localDate in completeDates }
            .map { row ->
                AppSessionEntity(
                    packageName = row.packageName,
                    appName = "",
                    startedAt = row.startedAtMs,
                    endedAt = row.endedAtMs,
                    durationMs = row.durationMs,
                    localDate = row.localDate,
                )
            }
        val names = appDays.associate { it.packageName to it.appName }
        return DetectorUsageHistory(
            appDays = appDays,
            sessions = sessions.map { session ->
                session.copy(appName = names[session.packageName].orEmpty())
            },
        )
    }

    suspend fun groupBUsageDates(
        startDate: String,
        endDate: String,
        today: String,
        consumer: UsageHistoryConsumer,
    ): Set<String> {
        val cutoverDate = store.pipelineState()?.cutoverDate
        val phase = if (cutoverDate == null) {
            UsageHistoryReadPhase.SHADOW
        } else {
            UsageHistoryReadPhase.CUTOVER
        }
        val legacyDates = if (cutoverDate == null || startDate < cutoverDate) {
            val legacyEnd = if (cutoverDate == null) {
                endDate
            } else {
                minOf(endDate, LocalDate.parse(cutoverDate).minusDays(1).toString())
            }
            if (legacyEnd >= startDate) {
                store.legacyAppDays(startDate, legacyEnd).map { it.date }.toSet()
            } else {
                emptySet()
            }
        } else {
            emptySet()
        }
        val rollupStart = cutoverDate?.let { maxOf(startDate, it) }
        val rollupEnd = minOf(endDate, LocalDate.parse(today).minusDays(1).toString())
        val rollupDays = if (rollupStart != null && rollupStart <= rollupEnd) {
            store.rollupDays(rollupStart, rollupEnd).associateBy { it.date }
        } else {
            emptyMap()
        }
        val liveHasSession = if (today in startDate..endDate && cutoverDate != null) {
            store.liveHasSessionToday(today)
        } else {
            false
        }

        return generateSequence(LocalDate.parse(startDate)) { it.plusDays(1) }
            .takeWhile { it.toString() <= endDate }
            .map { it.toString() }
            .filter { date ->
                when (
                    UsageHistorySourcePolicy.selectHistory(
                        phase = phase,
                        date = date,
                        today = today,
                        cutoverDate = cutoverDate,
                        consumer = consumer,
                        rollupStatus = rollupDays[date]?.status,
                        eventsAvailable = false,
                        liveHasSession = liveHasSession,
                    )
                ) {
                    UsageHistorySource.LEGACY -> date in legacyDates
                    UsageHistorySource.ROLLUP -> true
                    UsageHistorySource.LIVE_PIPELINE -> true
                    else -> false
                }
            }
            .toSet()
    }

    fun sourceFor(
        phase: UsageHistoryReadPhase,
        date: String,
        today: String,
        cutoverDate: String?,
        consumer: UsageHistoryConsumer,
        rollupStatus: String?,
        eventsAvailable: Boolean,
        liveHasSession: Boolean = false,
    ): UsageHistorySource = UsageHistorySourcePolicy.selectHistory(
        phase = phase,
        date = date,
        today = today,
        cutoverDate = cutoverDate,
        consumer = consumer,
        rollupStatus = rollupStatus,
        eventsAvailable = eventsAvailable,
        liveHasSession = liveHasSession,
    )
}

class RoomUsageHistoryStore(
    private val dailyAppUsageDao: DailyAppUsageDao,
    private val appSessionDao: AppSessionDao,
    private val usageRollupDao: UsageRollupDao,
    private val eventSource: UsageEventsSource,
    private val packageName: String,
) : UsageHistoryStore {
    private val liveTracker = ForegroundSpanTracker(
        excludedPackages = ForegroundSpanTracker.DEFAULT_EXCLUDED_PACKAGES + packageName,
    )

    override suspend fun legacyAppDays(
        startDate: String,
        endDate: String,
    ): List<UsageHistoryAppDay> = dailyAppUsageDao.getForDateRange(startDate, endDate).map {
        UsageHistoryAppDay(
            date = it.date,
            packageName = it.packageName,
            appName = it.appName,
            category = it.category,
            foregroundMs = it.foregroundMs,
            hourlyMs = it.hourlyMs,
            launchCount = it.launchCount,
            lastUsedAtMs = it.lastUsedAt,
        )
    }

    override suspend fun legacySessions(
        startDate: String,
        endDate: String,
    ): List<AppSessionEntity> = appSessionDao.getAllSessionsInRange(startDate, endDate)

    override suspend fun pipelineState(): UsagePipelineStateEntity? =
        usageRollupDao.getPipelineState()

    override suspend fun rollupDays(
        startDate: String,
        endDate: String,
    ): List<UsageRollupDayEntity> = usageRollupDao.getDays(startDate, endDate)

    override suspend fun rollupAppDays(date: String): List<UsageRollupAppDayEntity> =
        usageRollupDao.getAppDays(date)

    override suspend fun rollupSessions(
        startDate: String,
        endDate: String,
    ): List<UsageRollupSessionEntity> = usageRollupDao.getSessions(startDate, endDate)

    override suspend fun liveHasSessionToday(date: String): Boolean {
        val zone = ZoneId.systemDefault()
        val localDate = LocalDate.parse(date)
        val dayStart = localDate.atStartOfDay(zone).toInstant().toEpochMilli()
        val nowMs = System.currentTimeMillis()
        val events = eventSource.readForegroundEvents(
            dayStart - 6L * 60L * 60L * 1_000L,
            nowMs,
        )
        if (events !is UsageEventRead.Available) return false
        val sessions = liveTracker.sessions(
            events = events.events,
            windowStartMs = dayStart,
            windowEndMs = nowMs,
            nowMs = nowMs,
        )
        return sessions.any { it.endedAtMs > dayStart && it.startedAtMs < nowMs }
    }
}
