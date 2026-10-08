package com.tbtechs.focusflow.enforcement

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONObject

internal interface AllowanceLedgerStore {
    fun readUsageJson(): String?
    fun writeUsageJson(value: String)
}

data class AllowanceUsageRecord(
    val mode: String?,
    val date: String?,
    val count: Int,
    val windowStartMs: Long,
    val usedMs: Long,
    val confirmedUsedMs: Long,
    val confirmedCount: Int,
    val confirmedAtMs: Long,
    val estimatedExtraMs: Long,
    val estimatedExtraOpens: Int,
)

data class AllowanceReadResult(
    val count: Int,
    val usedMs: Long,
    val windowStartMs: Long,
    val windowExpired: Boolean,
    val remaining: Long,
    val exhausted: Boolean,
)

data class AllowanceLedgerSnapshot(
    val usageJson: String?,
    val usageByPackage: Map<String, AllowanceUsageRecord>,
)

data class AllowanceCountUpdate(val packageName: String, val count: Int)

data class AllowanceTimeUpdate(
    val packageName: String,
    val mode: String,
    val usedMs: Long,
    val windowStartMs: Long = 0L,
)

/**
 * Sole owner of the persisted allowance JSON, its process-wide lock, and
 * legacy count/time/window calculations. SharedPreferences apply() keeps disk
 * writes asynchronous while this instance's cache updates synchronously.
 */
class AllowanceLedger internal constructor(
    private val store: AllowanceLedgerStore,
) {
    private val lock = Any()
    private var cachedUsage: JSONObject? = null
    private var cachedJson: String? = null

    fun <T> withLock(block: () -> T): T = synchronized(lock, block)

    fun snapshot(): AllowanceLedgerSnapshot = withLock {
        val root = usageObjectLocked()
        val usages = linkedMapOf<String, AllowanceUsageRecord>()
        val keys = root.keys()
        while (keys.hasNext()) {
            val packageName = keys.next()
            root.optJSONObject(packageName)?.let { usages[packageName] = parseRecord(it) }
        }
        AllowanceLedgerSnapshot(cachedJson, usages)
    }

    fun usage(packageName: String): AllowanceUsageRecord = withLock {
        parseRecord(usageObjectLocked().optJSONObject(packageName) ?: JSONObject())
    }

    fun readAllowance(
        packageName: String,
        mode: String,
        today: String,
        nowMs: Long,
        limit: Long,
        windowMs: Long = 0L,
    ): AllowanceReadResult = withLock {
        val record = parseRecord(usageObjectLocked().optJSONObject(packageName) ?: JSONObject())
        val windowExpired = mode == MODE_INTERVAL && nowMs > record.windowStartMs + windowMs
        val count = if (mode == MODE_COUNT && record.date == today) record.count else 0
        val usedMs = when (mode) {
            MODE_TIME_BUDGET -> if (record.date == today) record.usedMs else 0L
            MODE_INTERVAL -> if (windowExpired) 0L else record.usedMs
            else -> 0L
        }
        val consumed = if (mode == MODE_COUNT) count.toLong() else usedMs
        val remaining = (limit - consumed).coerceAtLeast(0L)
        val exhausted = when (mode) {
            MODE_COUNT, MODE_TIME_BUDGET -> consumed >= limit
            MODE_INTERVAL -> !windowExpired && consumed >= limit
            else -> false
        }
        AllowanceReadResult(
            count = count,
            usedMs = usedMs,
            windowStartMs = record.windowStartMs,
            windowExpired = windowExpired,
            remaining = remaining,
            exhausted = exhausted,
        )
    }

    fun recordOpen(
        packageName: String,
        mode: String,
        today: String,
        nowMs: Long,
        limit: Long,
        windowMs: Long = 0L,
    ): Long = withLock {
        val root = usageObjectLocked()
        val json = root.optJSONObject(packageName) ?: JSONObject()
        val record = parseRecord(json)
        val sameDay = record.date == today
        val windowExpired = mode == MODE_INTERVAL && nowMs > record.windowStartMs + windowMs
        val sessionEndMs: Long
        val updated = when (mode) {
            MODE_COUNT -> {
                val nextCount = saturatedAdd(if (sameDay) record.count else 0, 1)
                sessionEndMs = 0L
                record.copy(
                    mode = mode,
                    date = today,
                    count = nextCount,
                    confirmedCount = nextCount,
                    confirmedAtMs = if (sameDay) record.confirmedAtMs else 0L,
                    estimatedExtraOpens = 0,
                )
            }
            MODE_TIME_BUDGET -> {
                val used = if (sameDay) record.usedMs else 0L
                sessionEndMs = nowMs + (limit - used).coerceAtLeast(0L)
                record.copy(
                    mode = mode,
                    date = today,
                    usedMs = used,
                    confirmedUsedMs = used,
                    confirmedAtMs = if (sameDay) record.confirmedAtMs else 0L,
                    estimatedExtraMs = 0L,
                )
            }
            MODE_INTERVAL -> {
                val start = if (windowExpired) nowMs else record.windowStartMs
                val used = if (windowExpired) 0L else record.usedMs
                sessionEndMs = nowMs + (limit - used).coerceAtLeast(0L)
                record.copy(
                    mode = mode,
                    date = today,
                    windowStartMs = start,
                    usedMs = used,
                    confirmedUsedMs = used,
                    confirmedAtMs = if (windowExpired) 0L else record.confirmedAtMs,
                    estimatedExtraMs = 0L,
                )
            }
            else -> return@withLock 0L
        }
        writeRecordLocked(root, packageName, json, updated)
        sessionEndMs
    }

    fun accumulateTimedUsage(
        packageName: String,
        mode: String,
        today: String,
        openedAtMs: Long,
        nowMs: Long,
        midnightMs: Long,
        limitMs: Long,
        windowMs: Long,
    ): Boolean = withLock {
        val elapsed = (nowMs - openedAtMs).coerceAtLeast(0L)
        if (elapsed == 0L || (mode != MODE_TIME_BUDGET && mode != MODE_INTERVAL)) {
            return@withLock false
        }
        val root = usageObjectLocked()
        val json = root.optJSONObject(packageName) ?: JSONObject()
        val record = parseRecord(json)
        val sameDay = record.date == today
        val updated = when (mode) {
            MODE_TIME_BUDGET -> {
                val used = if (openedAtMs < midnightMs) {
                    (nowMs - midnightMs).coerceAtLeast(0L).coerceAtMost(limitMs)
                } else {
                    ((if (sameDay) record.usedMs else 0L) + elapsed).coerceAtMost(limitMs)
                }
                record.copy(
                    mode = mode,
                    date = today,
                    usedMs = used,
                    confirmedUsedMs = used,
                    confirmedAtMs = if (sameDay) record.confirmedAtMs else 0L,
                    estimatedExtraMs = 0L,
                )
            }
            else -> {
                val windowEndMs = record.windowStartMs + windowMs
                val used = if (record.windowStartMs > 0L && nowMs > windowEndMs) {
                    val inWindow = (windowEndMs - openedAtMs).coerceAtLeast(0L)
                    (record.usedMs + inWindow).coerceAtMost(limitMs)
                } else {
                    (record.usedMs + elapsed).coerceAtMost(limitMs)
                }
                record.copy(
                    mode = mode,
                    usedMs = used,
                    confirmedUsedMs = used,
                    confirmedAtMs = record.confirmedAtMs,
                    estimatedExtraMs = 0L,
                )
            }
        }
        writeRecordLocked(root, packageName, json, updated)
        true
    }

    fun raiseCountUsage(today: String, updates: List<AllowanceCountUpdate>, atMs: Long): Boolean =
        withLock {
            val root = usageObjectLocked()
            var changed = false
            for (update in updates) {
                val json = root.optJSONObject(update.packageName) ?: JSONObject()
                val record = parseRecord(json)
                val current = if (record.date == today) record.count else 0
                val target = update.count.coerceAtLeast(0)
                if (target <= current) continue
                val updated = record.copy(
                    mode = MODE_COUNT,
                    date = today,
                    count = target,
                    confirmedCount = target,
                    confirmedAtMs = maxOf(
                        if (record.date == today) record.confirmedAtMs else 0L,
                        atMs.coerceAtLeast(0L),
                    ),
                    estimatedExtraOpens = 0,
                )
                writeRecordLocked(root, update.packageName, json, updated, persist = false)
                changed = true
            }
            if (changed) persistLocked()
            changed
        }

    fun raiseTimeUsage(today: String, updates: List<AllowanceTimeUpdate>, atMs: Long): Boolean =
        withLock {
            val root = usageObjectLocked()
            var changed = false
            for (update in updates) {
                if (update.mode != MODE_TIME_BUDGET && update.mode != MODE_INTERVAL) continue
                val json = root.optJSONObject(update.packageName) ?: JSONObject()
                val record = parseRecord(json)
                val samePeriod = if (update.mode == MODE_TIME_BUDGET) {
                    record.date == today
                } else {
                    record.windowStartMs == update.windowStartMs
                }
                val current = if (samePeriod) record.usedMs else 0L
                val target = update.usedMs.coerceAtLeast(0L)
                if (target <= current) continue
                val updated = record.copy(
                    mode = update.mode,
                    date = today,
                    windowStartMs = if (update.mode == MODE_INTERVAL) {
                        update.windowStartMs.coerceAtLeast(0L)
                    } else {
                        record.windowStartMs
                    },
                    usedMs = target,
                    confirmedUsedMs = target,
                    confirmedAtMs = maxOf(
                        if (samePeriod) record.confirmedAtMs else 0L,
                        atMs.coerceAtLeast(0L),
                    ),
                    estimatedExtraMs = 0L,
                )
                writeRecordLocked(root, update.packageName, json, updated, persist = false)
                changed = true
            }
            if (changed) persistLocked()
            changed
        }

    fun reset(packageName: String? = null) = withLock {
        val root = usageObjectLocked()
        if (packageName == null) {
            root.keys().asSequence().toList().forEach(root::remove)
        } else {
            root.remove(packageName)
        }
        persistLocked()
    }

    private fun writeRecordLocked(
        root: JSONObject,
        packageName: String,
        json: JSONObject,
        record: AllowanceUsageRecord,
        persist: Boolean = true,
    ) {
        json.put("mode", record.mode ?: JSONObject.NULL)
        json.put("date", record.date ?: JSONObject.NULL)
        json.put("count", record.count.coerceAtLeast(0))
        json.put("usedMs", record.usedMs.coerceAtLeast(0L))
        json.put("windowStartMs", record.windowStartMs.coerceAtLeast(0L))
        json.put("confirmedUsedMs", record.confirmedUsedMs.coerceAtLeast(0L))
        json.put("confirmedCount", record.confirmedCount.coerceAtLeast(0))
        json.put("confirmedAtMs", record.confirmedAtMs.coerceAtLeast(0L))
        json.put("estimatedExtraMs", record.estimatedExtraMs.coerceAtLeast(0L))
        json.put("estimatedExtraOpens", record.estimatedExtraOpens.coerceAtLeast(0))
        root.put(packageName, json)
        if (persist) persistLocked()
    }

    private fun parseRecord(json: JSONObject): AllowanceUsageRecord {
        val legacyUsedMs = json.optLong("usedMs", 0L).coerceAtLeast(0L)
        val legacyCount = json.optInt("count", 0).coerceAtLeast(0)
        val confirmedUsedMs = json.optLong("confirmedUsedMs", legacyUsedMs).coerceAtLeast(0L)
        val confirmedCount = json.optInt("confirmedCount", legacyCount).coerceAtLeast(0)
        val estimatedExtraMs = json.optLong("estimatedExtraMs", 0L).coerceAtLeast(0L)
        val estimatedExtraOpens = json.optInt("estimatedExtraOpens", 0).coerceAtLeast(0)
        return AllowanceUsageRecord(
            mode = json.optString("mode").takeIf(String::isNotBlank),
            date = json.optString("date").takeIf(String::isNotBlank),
            count = saturatedAdd(confirmedCount, estimatedExtraOpens),
            windowStartMs = json.optLong("windowStartMs", 0L).coerceAtLeast(0L),
            usedMs = saturatedAdd(confirmedUsedMs, estimatedExtraMs),
            confirmedUsedMs = confirmedUsedMs,
            confirmedCount = confirmedCount,
            confirmedAtMs = json.optLong("confirmedAtMs", 0L).coerceAtLeast(0L),
            estimatedExtraMs = estimatedExtraMs,
            estimatedExtraOpens = estimatedExtraOpens,
        )
    }

    private fun usageObjectLocked(): JSONObject {
        cachedUsage?.let { return it }
        val raw = store.readUsageJson()
        cachedJson = raw
        cachedUsage = try {
            if (raw.isNullOrBlank()) JSONObject() else JSONObject(raw)
        } catch (_: Exception) {
            JSONObject()
        }
        return cachedUsage!!
    }

    private fun persistLocked() {
        val json = usageObjectLocked().toString()
        cachedJson = json
        store.writeUsageJson(json)
    }

    companion object {
        const val PREFS_NAME = "focusday_prefs"
        const val PREF_DAILY_ALLOWANCE_USED = "daily_allowance_used"

        const val MODE_COUNT = "count"
        const val MODE_TIME_BUDGET = "time_budget"
        const val MODE_INTERVAL = "interval"

        @Volatile
        private var instance: AllowanceLedger? = null

        fun getInstance(context: Context): AllowanceLedger {
            instance?.let { return it }
            return synchronized(this) {
                instance ?: AllowanceLedger(
                    SharedPreferencesAllowanceStore(
                        context.applicationContext.getSharedPreferences(
                            PREFS_NAME,
                            Context.MODE_PRIVATE,
                        ),
                    ),
                ).also { instance = it }
            }
        }

        private fun saturatedAdd(left: Long, right: Long): Long =
            if (Long.MAX_VALUE - left < right) Long.MAX_VALUE else left + right

        private fun saturatedAdd(left: Int, right: Int): Int =
            if (Int.MAX_VALUE - left < right) Int.MAX_VALUE else left + right
    }
}

private class SharedPreferencesAllowanceStore(
    private val prefs: SharedPreferences,
) : AllowanceLedgerStore {
    override fun readUsageJson(): String? = prefs.getString(
        AllowanceLedger.PREF_DAILY_ALLOWANCE_USED,
        null,
    )

    override fun writeUsageJson(value: String) {
        prefs.edit().putString(AllowanceLedger.PREF_DAILY_ALLOWANCE_USED, value).apply()
    }
}
