package com.tbtechs.focusflow.enforcement

import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.tbtechs.focusflow.analytics.ForegroundSpanTracker
import com.tbtechs.focusflow.analytics.UsageEventRead
import com.tbtechs.focusflow.data.repository.UsageStatsRepository
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import org.json.JSONArray

/**
 * Reads allowance usage from the shared foreground-event pipeline. Its timer
 * exists only while an allowance app is in the foreground.
 */
internal class AllowanceUsageCoordinator(
    context: Context,
    private val prefs: SharedPreferences,
    private val ledger: AllowanceLedger,
    private val scope: CoroutineScope,
    private val onReconciled: (String) -> Unit,
) {
    private val appContext = context.applicationContext
    private val source = UsageStatsRepository(appContext)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val tracker = ForegroundSpanTracker(
        excludedPackages = ForegroundSpanTracker.DEFAULT_EXCLUDED_PACKAGES + appContext.packageName,
    )
    private val zoneId = ZoneId.systemDefault()
    private var currentTarget: AllowanceUsageTarget? = null
    private var segmentCheckpointAtMs = 0L
    private var segmentDate: String? = null
    private var pendingBridgePackage: String? = null
    private var pendingBridgeAtMs = 0L
    private var lastSuccessfulReadAtMs = 0L
    private var unlockedAtMs = System.currentTimeMillis()
    private var usageAccessAvailable = true
    private var freshness = AllowanceUsageFreshness.STALE
    private var readJob: Job? = null
    private var readAgain = false

    private val tickRunnable = object : Runnable {
        override fun run() {
            val target = currentTarget ?: return
            val now = System.currentTimeMillis()
            checkpointIfStale(target, now)
            refresh("foreground_tick")
            val delay = if (freshness == AllowanceUsageFreshness.FRESH) {
                AllowanceUsageFreshnessReducer.FRESH_FOR_MS / 2
            } else {
                CHECKPOINT_INTERVAL_MS
            }
            mainHandler.postDelayed(this, delay)
        }
    }

    fun onServiceStarted(savedForegroundPackage: String?) {
        val now = System.currentTimeMillis()
        recoverPersistedCheckpoint(appContext, prefs, ledger, now)
        lastSuccessfulReadAtMs = configuredTargets().maxOfOrNull { target ->
            ledger.usage(target.packageName).confirmedAtMs
        } ?: 0L
        val target = savedForegroundPackage?.let(::targetForPackage)
        if (target != null) {
            currentTarget = target
            segmentCheckpointAtMs = now
            segmentDate = today(now)
            if (target.mode == AllowanceLedger.MODE_INTERVAL) {
                val start = ledger.ensureIntervalWindowStarted(
                    target.packageName,
                    today(now),
                    now,
                    target.windowMs,
                )
                currentTarget = target.copy(windowStartMs = start)
            }
            persistMarker(target.packageName, now)
            scheduleTick()
        }
        refresh("service_start")
    }

    fun onForegroundPackage(packageName: String, atMs: Long = System.currentTimeMillis()) {
        if (packageName in AllowanceUsagePipeline.dialogBridgePackages) {
            val active = currentTarget
            if (active != null) {
                finishSegment(active, atMs)
                pendingBridgePackage = active.packageName
                pendingBridgeAtMs = atMs
                currentTarget = null
                clearMarker(active.packageName)
                mainHandler.removeCallbacks(tickRunnable)
            }
            return
        }

        val target = targetForPackage(packageName)
        val active = currentTarget
        if (active?.packageName.equals(target?.packageName, ignoreCase = true) &&
            active != null && target != null
        ) {
            return
        }
        if (active != null) {
            finishSegment(active, atMs)
            clearMarker(active.packageName)
            currentTarget = null
            mainHandler.removeCallbacks(tickRunnable)
        }
        if (target == null) {
            pendingBridgePackage = null
            pendingBridgeAtMs = 0L
            refresh("foreground_left_allowance")
            return
        }

        val bridgeReturn = pendingBridgePackage.equals(target.packageName, ignoreCase = true) &&
            atMs >= pendingBridgeAtMs &&
            atMs - pendingBridgeAtMs <= AllowanceUsagePipeline.DIALOG_BRIDGE_MAX_MS
        pendingBridgePackage = null
        pendingBridgeAtMs = 0L
        val currentDay = today(atMs)
        var nextTarget = target
        if (target.mode == AllowanceLedger.MODE_INTERVAL) {
            val start = ledger.ensureIntervalWindowStarted(
                target.packageName,
                currentDay,
                atMs,
                target.windowMs,
            )
            nextTarget = target.copy(windowStartMs = start)
        }
        if (target.mode == AllowanceLedger.MODE_COUNT && !bridgeReturn) {
            ledger.addEstimatedOpen(target.packageName, currentDay)
        }
        currentTarget = nextTarget
        segmentDate = currentDay
        segmentCheckpointAtMs = maxOf(atMs, ledger.usage(target.packageName).confirmedAtMs)
        persistMarker(target.packageName, atMs)
        scheduleTick()
        refresh("allowance_app_open")
    }

    fun onScreenOff(atMs: Long = System.currentTimeMillis()) {
        currentTarget?.let { finishSegment(it, atMs) }
        currentTarget?.let { clearMarker(it.packageName) }
        currentTarget = null
        pendingBridgePackage = null
        pendingBridgeAtMs = 0L
        mainHandler.removeCallbacks(tickRunnable)
    }

    fun onUserPresent() {
        unlockedAtMs = System.currentTimeMillis()
    }

    fun onConfigurationChanged(atMs: Long = System.currentTimeMillis()) {
        currentTarget?.let {
            finishSegment(it, atMs)
            clearMarker(it.packageName)
        }
        currentTarget = null
        pendingBridgePackage = null
        pendingBridgeAtMs = 0L
        mainHandler.removeCallbacks(tickRunnable)
        refresh("allowance_config_changed")
    }

    fun onServiceStopping(atMs: Long = System.currentTimeMillis()) {
        currentTarget?.let {
            finishSegment(it, atMs)
            persistMarker(it.packageName, atMs)
        }
        currentTarget = null
        mainHandler.removeCallbacks(tickRunnable)
        readJob?.cancel()
    }

    fun setActiveSessionEnd(packageName: String, endAtMs: Long) {
        if (prefs.getString(AppBlockerAccessibilityService.PREF_ACTIVE_SESSION_PKG, null)
                ?.equals(packageName, ignoreCase = true) == true
        ) {
            prefs.edit()
                .putLong(AppBlockerAccessibilityService.PREF_ACTIVE_SESSION_END_MS, endAtMs)
                .apply()
        }
    }

    fun freshness(nowMs: Long = System.currentTimeMillis()): AllowanceUsageFreshness =
        AllowanceUsageFreshnessReducer.reduce(
            hasUsageAccess = usageAccessAvailable,
            lastSuccessfulReadAtMs = lastSuccessfulReadAtMs,
            unlockedAtMs = unlockedAtMs,
            nowMs = nowMs,
        )

    private fun refresh(reason: String) {
        if (readJob?.isActive == true) {
            readAgain = true
            return
        }
        val now = System.currentTimeMillis()
        val targets = configuredTargets().map { target ->
            if (target.mode == AllowanceLedger.MODE_INTERVAL) {
                target.copy(windowStartMs = ledger.usage(target.packageName).windowStartMs)
            } else {
                target
            }
        }
        if (targets.isEmpty()) return
        val todayStart = localMidnight(now)
        val earliestPeriod = targets.mapNotNull { target ->
            when (target.mode) {
                AllowanceLedger.MODE_INTERVAL -> target.windowStartMs.takeIf { it > 0L }
                else -> todayStart
            }
        }.minOrNull() ?: todayStart
        val queryStart = (earliestPeriod - LOOKBACK_MS).coerceAtLeast(0L)
        readJob = scope.launch {
            val read = try {
                withTimeout(READ_TIMEOUT_MS) {
                    source.readForegroundEvents(queryStart, now)
                }
            } catch (_: TimeoutCancellationException) {
                UsageEventRead.Unknown(UsageEventRead.Unknown.Reason.EVENTS_UNAVAILABLE)
            } catch (_: SecurityException) {
                UsageEventRead.Unknown(UsageEventRead.Unknown.Reason.ACCESS_REVOKED)
            } catch (_: Exception) {
                UsageEventRead.Unknown(UsageEventRead.Unknown.Reason.EVENTS_UNAVAILABLE)
            }
            val completedAt = System.currentTimeMillis()
            when (read) {
                is UsageEventRead.Unknown -> {
                    usageAccessAvailable =
                        read.reason != UsageEventRead.Unknown.Reason.PERMISSION_MISSING &&
                            read.reason != UsageEventRead.Unknown.Reason.ACCESS_REVOKED
                    val newState = AllowanceUsageFreshnessReducer.reduce(
                        hasUsageAccess = usageAccessAvailable,
                        lastSuccessfulReadAtMs = lastSuccessfulReadAtMs,
                        unlockedAtMs = unlockedAtMs,
                        nowMs = completedAt,
                    )
                    if (newState != freshness) currentTarget?.let { persistMarker(it.packageName, completedAt) }
                    freshness = newState
                    currentTarget?.let { checkpointIfStale(it, completedAt) }
                    Log.i(TAG, "allowance pipeline state=$freshness reason=$reason")
                }
                is UsageEventRead.Available -> {
                    usageAccessAvailable = true
                    val sessions = tracker.sessions(
                        events = read.events,
                        windowStartMs = queryStart,
                        windowEndMs = now,
                        nowMs = now,
                    )
                    targets.forEach { target ->
                        val measurement = AllowanceUsagePipeline.measure(
                            target = target,
                            sessions = sessions,
                            todayStartMs = todayStart,
                            nowMs = now,
                            zoneId = zoneId,
                        )
                        when (target.mode) {
                            AllowanceLedger.MODE_COUNT -> ledger.reconcileCountUsage(
                                target.packageName,
                                today(now),
                                measurement.count,
                                completedAt,
                            )
                            AllowanceLedger.MODE_TIME_BUDGET,
                            AllowanceLedger.MODE_INTERVAL -> ledger.reconcileTimeUsage(
                                packageName = target.packageName,
                                mode = target.mode,
                                today = today(now),
                                windowStartMs = target.windowStartMs,
                                usedMs = measurement.usedMs,
                                atMs = completedAt,
                            )
                        }
                        onReconciled(target.packageName)
                    }
                    lastSuccessfulReadAtMs = completedAt
                    freshness = AllowanceUsageFreshness.FRESH
                    currentTarget?.let { active ->
                        segmentCheckpointAtMs = completedAt
                        segmentDate = today(completedAt)
                        persistMarker(active.packageName, completedAt)
                    }
                    Log.d(TAG, "allowance pipeline read succeeded reason=$reason")
                }
            }
            readJob = null
            if (readAgain) {
                readAgain = false
                refresh("coalesced")
            }
        }
    }

    private fun checkpointIfStale(target: AllowanceUsageTarget, nowMs: Long) {
        if (today(nowMs) != segmentDate) {
            segmentDate = today(nowMs)
            segmentCheckpointAtMs = localMidnight(nowMs)
        }
        val state = freshness(nowMs)
        if (state == AllowanceUsageFreshness.FRESH) return
        val start = maxOf(
            segmentCheckpointAtMs,
            ledger.usage(target.packageName).confirmedAtMs,
            if (target.mode == AllowanceLedger.MODE_TIME_BUDGET) localMidnight(nowMs) else 0L,
            if (target.mode == AllowanceLedger.MODE_INTERVAL) target.windowStartMs else 0L,
        )
        val capEnd = if (target.mode == AllowanceLedger.MODE_INTERVAL && target.windowStartMs > 0L) {
            minOf(nowMs, target.windowStartMs + target.windowMs)
        } else {
            nowMs
        }
        if (target.mode == AllowanceLedger.MODE_TIME_BUDGET ||
            target.mode == AllowanceLedger.MODE_INTERVAL
        ) {
            ledger.addEstimatedTimeUsage(
                packageName = target.packageName,
                mode = target.mode,
                today = today(nowMs),
                windowStartMs = target.windowStartMs,
                deltaMs = (capEnd - start).coerceAtLeast(0L)
                    .coerceAtMost(AllowanceLedger.MAX_ESTIMATED_SEGMENT_MS),
            )
        }
        segmentCheckpointAtMs = nowMs
        persistMarker(target.packageName, nowMs)
    }

    private fun finishSegment(target: AllowanceUsageTarget, atMs: Long) {
        val start = maxOf(
            segmentCheckpointAtMs,
            ledger.usage(target.packageName).confirmedAtMs,
            if (target.mode == AllowanceLedger.MODE_TIME_BUDGET) localMidnight(atMs) else 0L,
            if (target.mode == AllowanceLedger.MODE_INTERVAL) target.windowStartMs else 0L,
        )
        val end = if (target.mode == AllowanceLedger.MODE_INTERVAL && target.windowStartMs > 0L) {
            minOf(atMs, target.windowStartMs + target.windowMs)
        } else {
            atMs
        }
        if (target.mode == AllowanceLedger.MODE_TIME_BUDGET ||
            target.mode == AllowanceLedger.MODE_INTERVAL
        ) {
            val elapsed = (end - start).coerceAtLeast(0L)
                .coerceAtMost(AllowanceLedger.MAX_ESTIMATED_SEGMENT_MS)
            ledger.addEstimatedTimeUsage(
                packageName = target.packageName,
                mode = target.mode,
                today = today(atMs),
                windowStartMs = target.windowStartMs,
                deltaMs = elapsed,
            )
        }
        segmentCheckpointAtMs = atMs
    }

    private fun configuredTargets(): List<AllowanceUsageTarget> {
        val raw = prefs.getString(AppBlockerAccessibilityService.PREF_DAILY_ALLOWANCE_CONFIG, null)
            ?: return emptyList()
        return try {
            val entries = JSONArray(raw)
            buildList {
                for (index in 0 until entries.length()) {
                    val item = entries.optJSONObject(index) ?: continue
                    val pkg = item.optString("packageName", "").takeIf(String::isNotBlank) ?: continue
                    val mode = item.optString("mode", AllowanceLedger.MODE_COUNT)
                    when (mode) {
                        AllowanceLedger.MODE_COUNT ->
                            add(AllowanceUsageTarget(pkg, mode))
                        AllowanceLedger.MODE_TIME_BUDGET ->
                            add(AllowanceUsageTarget(pkg, mode))
                        AllowanceLedger.MODE_INTERVAL ->
                            add(
                                AllowanceUsageTarget(
                                    packageName = pkg,
                                    mode = mode,
                                    windowStartMs = ledger.usage(pkg).windowStartMs,
                                    windowMs = item.optInt("intervalHours", 1)
                                        .coerceAtLeast(1).toLong() * HOUR_MS,
                                ),
                            )
                    }
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun targetForPackage(packageName: String): AllowanceUsageTarget? =
        configuredTargets().firstOrNull { it.packageName.equals(packageName, ignoreCase = true) }

    private fun persistMarker(packageName: String, atMs: Long) {
        prefs.edit()
            .putString(AppBlockerAccessibilityService.PREF_ACTIVE_SESSION_PKG, packageName)
            .putLong(AppBlockerAccessibilityService.PREF_ACTIVE_SESSION_LAST_CHECKPOINT_MS, atMs)
            .apply()
    }

    private fun clearMarker(packageName: String) {
        if (prefs.getString(AppBlockerAccessibilityService.PREF_ACTIVE_SESSION_PKG, null)
                ?.equals(packageName, ignoreCase = true) != true
        ) return
        prefs.edit()
            .remove(AppBlockerAccessibilityService.PREF_ACTIVE_SESSION_PKG)
            .remove(AppBlockerAccessibilityService.PREF_ACTIVE_SESSION_LAST_CHECKPOINT_MS)
            .remove(AppBlockerAccessibilityService.PREF_ACTIVE_SESSION_END_MS)
            .apply()
    }

    private fun scheduleTick() {
        mainHandler.removeCallbacks(tickRunnable)
        mainHandler.postDelayed(tickRunnable, INITIAL_REFRESH_DELAY_MS)
    }

    private fun localMidnight(atMs: Long): Long =
        Instant.ofEpochMilli(atMs).atZone(zoneId).toLocalDate().atStartOfDay(zoneId)
            .toInstant().toEpochMilli()

    private fun today(atMs: Long): String =
        Instant.ofEpochMilli(atMs).atZone(zoneId).toLocalDate()
            .format(DateTimeFormatter.ISO_LOCAL_DATE)

    companion object {
        const val CHECKPOINT_INTERVAL_MS = 15_000L
        const val MAX_RECOVERABLE_CHECKPOINT_GAP_MS = 2 * CHECKPOINT_INTERVAL_MS
        const val READ_TIMEOUT_MS = 5_000L
        private const val INITIAL_REFRESH_DELAY_MS = 30_000L
        private const val LOOKBACK_MS = 6 * 60 * 60 * 1_000L
        private const val HOUR_MS = 60 * 60 * 1_000L
        private const val TAG = "AllowanceUsage"

        fun recoverPersistedCheckpoint(
            context: Context,
            prefs: SharedPreferences,
            ledger: AllowanceLedger,
            nowMs: Long,
        ) {
            val packageName = prefs.getString(
                AppBlockerAccessibilityService.PREF_ACTIVE_SESSION_PKG,
                null,
            ) ?: return
            val checkpointAtMs = prefs.getLong(
                AppBlockerAccessibilityService.PREF_ACTIVE_SESSION_LAST_CHECKPOINT_MS,
                0L,
            )
            if (checkpointAtMs <= 0L) return
            val elapsed = (nowMs - checkpointAtMs).coerceAtLeast(0L)
            val config = try {
                val array = JSONArray(
                    prefs.getString(
                        AppBlockerAccessibilityService.PREF_DAILY_ALLOWANCE_CONFIG,
                        "[]",
                    ) ?: "[]",
                )
                (0 until array.length())
                    .mapNotNull { array.optJSONObject(it) }
                    .firstOrNull {
                        it.optString("packageName", "").equals(packageName, ignoreCase = true)
                    }
            } catch (_: Exception) {
                null
            } ?: return
            val mode = config.optString("mode", AllowanceLedger.MODE_COUNT)
            if (mode != AllowanceLedger.MODE_TIME_BUDGET && mode != AllowanceLedger.MODE_INTERVAL) {
                prefs.edit()
                    .putLong(AppBlockerAccessibilityService.PREF_ACTIVE_SESSION_LAST_CHECKPOINT_MS, nowMs)
                    .apply()
                return
            }
            val currentDate = Instant.ofEpochMilli(nowMs).atZone(ZoneId.systemDefault())
                .toLocalDate().format(DateTimeFormatter.ISO_LOCAL_DATE)
            val windowStart = ledger.usage(packageName).windowStartMs
            val recovered = ledger.recoverCheckpointTime(
                packageName = packageName,
                mode = mode,
                today = currentDate,
                windowStartMs = windowStart,
                elapsedMs = elapsed,
                maximumRecoveryMs = MAX_RECOVERABLE_CHECKPOINT_GAP_MS,
            )
            prefs.edit()
                .putLong(AppBlockerAccessibilityService.PREF_ACTIVE_SESSION_LAST_CHECKPOINT_MS, nowMs)
                .apply()
            Log.i(
                TAG,
                "recovered checkpoint pkg=$packageName gapMs=$elapsed recoveredMs=$recovered",
            )
        }
    }
}
