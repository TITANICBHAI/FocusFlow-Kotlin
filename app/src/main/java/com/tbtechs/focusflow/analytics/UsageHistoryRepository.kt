package com.tbtechs.focusflow.analytics

import com.tbtechs.focusflow.data.local.dao.AppSessionDao
import com.tbtechs.focusflow.data.local.dao.DailyAppUsageDao
import com.tbtechs.focusflow.data.local.entity.AppSessionEntity

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

/**
 * Batch 3's merged-history boundary remains shadow-only: no rollup DAO is
 * available to these reads, so legacy data remains the sole served source.
 */
interface UsageHistoryStore {
    suspend fun legacyAppDays(startDate: String, endDate: String): List<UsageHistoryAppDay>
    suspend fun legacySessions(startDate: String, endDate: String): List<AppSessionEntity>
}

class UsageHistoryRepository(
    private val store: UsageHistoryStore,
) {
    suspend fun appDays(startDate: String, endDate: String): List<UsageHistoryAppDay> =
        store.legacyAppDays(startDate, endDate)

    suspend fun sessions(startDate: String, endDate: String): List<AppSessionEntity> =
        store.legacySessions(startDate, endDate)

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
) : UsageHistoryStore {
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
}
