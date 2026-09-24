# IMPL_1A — Data Layer
## Entities · DAOs · MIGRATION_5_6 · AppUsageAndSessionTracker
### Based on actual codebase (app.zip)

**DB name:** `focusday.db`  
**Current version:** 5 → **New version:** 6  
**Prefs file:** `"focusday_prefs"` (existing, do not create a new one)

---

## What was wrong in the previous Phase 1A doc

| Previous assumption | Reality (from code) |
|---|---|
| DB version 4 → 5 | **DB already at 5.** `MIGRATION_4_5` exists — handles `report_notes`. New is `MIGRATION_5_6`. |
| DB name `focusflow.db` | **`focusday.db`** — the hybrid app's name, must stay |
| Hook point was approximate | Exact: **after line ~1033**, after the `prefs.edit()...apply()` block that writes `current_foreground_pkg` |
| `UsageStatsRepository.getUsageSummary` needed writing | **Already exists and is production-quality.** Do not touch it. |
| Pre-Q event type branches needed | `getHourlyUsageSummary` still has `if (SDK >= Q)` in `getForegroundApp()` — **leave for now**, clean up separately per API_COMPAT.md |

---

## File 1 — `DailyAppUsageEntity.kt`

`data/local/entity/DailyAppUsageEntity.kt`

```kotlin
package com.tbtechs.focusflow.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index

/**
 * One row per app per local calendar day.
 *
 * Written in real time by [AppUsageAndSessionTracker] via
 * [AppBlockerAccessibilityService]. Never written retroactively.
 *
 * ## Why real-time, not queryEvents
 * Android's UsageEvents are retained for approximately 7 days. Any pattern
 * that needs 14–90 days of session or usage history (variable reward loop,
 * escalating capture) cannot rely on queryEvents retroactively. Writing as
 * events happen — via the accessibility service already intercepting every
 * foreground transition — builds a permanent record.
 *
 * ## [hourlyMs]
 * CSV of 24 Long values: foreground milliseconds per hour of the local day.
 * Index 0 = 00:00–00:59. Example: `"0,0,240000,180000,..."`
 * Produced by [AppUsageAndSessionTracker.splitIntoHourlySegments] which
 * correctly splits sessions across midnight crossings.
 *
 * ## [foregroundMs]
 * Running total; always equals sum(parseHourlyMs()).
 *
 * ## [launchCount]
 * Incremented once per foreground transition (each time the app comes to
 * the foreground from a different package).
 *
 * Pruned to 90 days by [BackgroundFetchWorker].
 */
@Entity(
    tableName = "daily_app_usage",
    primaryKeys = ["date", "package_name"],
    indices = [
        Index(value = ["package_name", "date"], name = "idx_dau_package_date"),
    ],
)
data class DailyAppUsageEntity(

    /** Local YYYY-MM-DD. Device timezone — never UTC midnight. */
    @ColumnInfo(name = "date")
    val date: String,

    @ColumnInfo(name = "package_name")
    val packageName: String,

    /** Refreshed on every launch. Blank only on the first increment before
     *  the display name is resolved. */
    @ColumnInfo(name = "app_name")
    val appName: String,

    /** One of: 'social' | 'entertainment' | 'productivity' | 'news' |
     *  'utility' | 'communication' | 'other'. Nullable until resolved. */
    @ColumnInfo(name = "category")
    val category: String?,

    /** Total foreground milliseconds for this date. */
    @ColumnInfo(name = "foreground_ms", defaultValue = "0")
    val foregroundMs: Long,

    /** Comma-separated 24 Long values (ms per hour). Empty string until
     *  the first heartbeat commit. */
    @ColumnInfo(name = "hourly_ms", defaultValue = "")
    val hourlyMs: String,

    @ColumnInfo(name = "launch_count", defaultValue = "0")
    val launchCount: Int,

    /** Epoch ms of the most recent foreground event for this app on this date. */
    @ColumnInfo(name = "last_used_at", defaultValue = "0")
    val lastUsedAt: Long,
)

/** Parses [DailyAppUsageEntity.hourlyMs] into a 24-element LongArray. Safe on
 *  empty or malformed input. */
fun DailyAppUsageEntity.parseHourlyMs(): LongArray {
    if (hourlyMs.isBlank()) return LongArray(24)
    val parts = hourlyMs.split(",")
    return LongArray(24) { i -> parts.getOrNull(i)?.toLongOrNull() ?: 0L }
}
```

---

## File 2 — `AppSessionEntity.kt`

`data/local/entity/AppSessionEntity.kt`

```kotlin
package com.tbtechs.focusflow.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One row per closed foreground session.
 *
 * Written by [AppUsageAndSessionTracker.closeCurrentSession] on every
 * foreground transition and on screen-off. Only closed sessions are stored
 * (both [endedAt] and [durationMs] are always non-null in a committed row).
 *
 * ## Session algorithm (aw-android foreground state machine)
 * Only one app is ever in the foreground. When App B's RESUMED fires,
 * App A's session is closed — even if App A's PAUSED never arrived. This
 * handles Samsung and other OEM variants that skip ACTIVITY_PAUSED.
 *
 * ## Duration filters
 * Sessions < 1 000 ms (sub-second blips) and > 4 hours (device-sleep
 * inflation) are excluded before insertion.
 *
 * Pruned to 90 days by [BackgroundFetchWorker].
 */
@Entity(
    tableName = "app_sessions",
    indices = [
        Index(value = ["package_name", "local_date"], name = "idx_as_package_date"),
        Index(value = ["started_at"],                 name = "idx_as_started_at"),
    ],
)
data class AppSessionEntity(

    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0,

    @ColumnInfo(name = "package_name")
    val packageName: String,

    @ColumnInfo(name = "app_name")
    val appName: String,

    /** Epoch milliseconds. Wall clock at foreground transition. */
    @ColumnInfo(name = "started_at")
    val startedAt: Long,

    /** Epoch milliseconds. Wall clock when session ended. Never null in stored rows. */
    @ColumnInfo(name = "ended_at")
    val endedAt: Long,

    /** endedAt − startedAt. Never null in stored rows. */
    @ColumnInfo(name = "duration_ms")
    val durationMs: Long,

    /** Local YYYY-MM-DD derived from [startedAt] at write time. */
    @ColumnInfo(name = "local_date")
    val localDate: String,
)
```

---

## File 3 — `DailyAppUsageDao.kt`

`data/local/dao/DailyAppUsageDao.kt`

**Must be `abstract class`** — `@Transaction` functions with business logic
cannot be in interfaces in Room.

```kotlin
package com.tbtechs.focusflow.data.local.dao

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.tbtechs.focusflow.data.local.entity.DailyAppUsageEntity
import com.tbtechs.focusflow.data.local.entity.parseHourlyMs

/** Returned by [getForDateRange]. One row per (package, date) pair. */
data class AppUsageRangeRow(
    @ColumnInfo(name = "package_name") val packageName: String,
    @ColumnInfo(name = "app_name")     val appName: String,
    @ColumnInfo(name = "category")     val category: String?,
    @ColumnInfo(name = "date")         val date: String,
    @ColumnInfo(name = "foreground_ms") val foregroundMs: Long,
    @ColumnInfo(name = "hourly_ms")    val hourlyMs: String,
    @ColumnInfo(name = "launch_count") val launchCount: Int,
    @ColumnInfo(name = "last_used_at") val lastUsedAt: Long,
)

@Dao
abstract class DailyAppUsageDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsert(entity: DailyAppUsageEntity)

    @Query("""
        SELECT * FROM daily_app_usage
        WHERE date = :date AND package_name = :packageName
        LIMIT 1
    """)
    abstract suspend fun getForPackageAndDate(
        date: String,
        packageName: String,
    ): DailyAppUsageEntity?

    /**
     * Accumulates [durationMs] into [hour] bucket and total [foregroundMs].
     * Creates the row if absent (using [appName] and [category] for the first row).
     *
     * Called by [AppUsageAndSessionTracker] every heartbeat and on every
     * foreground switch. The tracker's single-threaded coroutine scope
     * prevents concurrent writes to the same row.
     */
    @Transaction
    open suspend fun addForegroundTime(
        date:        String,
        packageName: String,
        appName:     String,
        category:    String?,
        hour:        Int,
        durationMs:  Long,
        lastUsedAt:  Long,
    ) {
        if (durationMs <= 0L) return
        val existing = getForPackageAndDate(date, packageName)
        val hourly   = existing?.parseHourlyMs() ?: LongArray(24)
        hourly[hour] += durationMs

        upsert(
            existing?.copy(
                appName      = appName,
                foregroundMs = existing.foregroundMs + durationMs,
                hourlyMs     = hourly.joinToString(","),
                lastUsedAt   = maxOf(existing.lastUsedAt, lastUsedAt),
            ) ?: DailyAppUsageEntity(
                date         = date,
                packageName  = packageName,
                appName      = appName,
                category     = category,
                foregroundMs = durationMs,
                hourlyMs     = hourly.joinToString(","),
                launchCount  = 0,
                lastUsedAt   = lastUsedAt,
            )
        )
    }

    /**
     * Increments [launchCount] and refreshes [appName] / [category].
     * Creates the row if absent (foregroundMs = 0 until the first heartbeat).
     *
     * Called once per foreground transition, immediately when a new package
     * comes to the foreground.
     */
    @Transaction
    open suspend fun incrementLaunchCount(
        date:        String,
        packageName: String,
        appName:     String,
        category:    String?,
        lastUsedAt:  Long,
    ) {
        val existing = getForPackageAndDate(date, packageName)
        upsert(
            existing?.copy(
                launchCount = existing.launchCount + 1,
                appName     = appName,
                category    = existing.category ?: category,
                lastUsedAt  = maxOf(existing.lastUsedAt, lastUsedAt),
            ) ?: DailyAppUsageEntity(
                date         = date,
                packageName  = packageName,
                appName      = appName,
                category     = category,
                foregroundMs = 0L,
                hourlyMs     = "",
                launchCount  = 1,
                lastUsedAt   = lastUsedAt,
            )
        )
    }

    /**
     * Returns all rows in [startDate, endDate] inclusive, ordered by date
     * ascending then foreground_ms descending. Used by the detection engine
     * and analytics processor.
     */
    @Query("""
        SELECT package_name, app_name, category, date,
               foreground_ms, hourly_ms, launch_count, last_used_at
        FROM   daily_app_usage
        WHERE  date BETWEEN :startDate AND :endDate
        ORDER  BY date ASC, foreground_ms DESC
    """)
    abstract suspend fun getForDateRange(
        startDate: String,
        endDate:   String,
    ): List<AppUsageRangeRow>

    @Query("DELETE FROM daily_app_usage WHERE date < :cutoffDate")
    abstract suspend fun deleteOlderThan(cutoffDate: String)

    @Query("SELECT COUNT(DISTINCT date) FROM daily_app_usage")
    abstract suspend fun countDistinctDates(): Int
}
```

---

## File 4 — `AppSessionDao.kt`

`data/local/dao/AppSessionDao.kt`

Interface is fine here — no concrete `@Transaction` methods needed.

```kotlin
package com.tbtechs.focusflow.data.local.dao

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.tbtechs.focusflow.data.local.entity.AppSessionEntity

/** Per-app per-day session aggregate. Returned by [getSessionStatsByDay]. */
data class SessionStatRow(
    @ColumnInfo(name = "package_name")    val packageName: String,
    @ColumnInfo(name = "local_date")      val localDate: String,
    @ColumnInfo(name = "session_count")   val sessionCount: Int,
    @ColumnInfo(name = "avg_duration_ms") val avgDurationMs: Double,
    @ColumnInfo(name = "min_duration_ms") val minDurationMs: Long,
    @ColumnInfo(name = "max_duration_ms") val maxDurationMs: Long,
    @ColumnInfo(name = "total_ms")        val totalMs: Long,
)

/** First session of each calendar day. Returned by [getFirstSessionEachDay]. */
data class FirstSessionRow(
    @ColumnInfo(name = "local_date")   val localDate: String,
    @ColumnInfo(name = "package_name") val packageName: String,
    @ColumnInfo(name = "app_name")     val appName: String,
    @ColumnInfo(name = "started_at")   val startedAt: Long,
)

@Dao
interface AppSessionDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(session: AppSessionEntity): Long

    /**
     * Aggregated session stats per package per day.
     * Powers: variable reward loop (sessionCount, avgDurationMs, CV),
     *         infinite session design (maxDurationMs variance).
     */
    @Query("""
        SELECT package_name,
               local_date,
               COUNT(*)          AS session_count,
               AVG(duration_ms)  AS avg_duration_ms,
               MIN(duration_ms)  AS min_duration_ms,
               MAX(duration_ms)  AS max_duration_ms,
               SUM(duration_ms)  AS total_ms
        FROM   app_sessions
        WHERE  local_date BETWEEN :startDate AND :endDate
          AND  duration_ms > 0
        GROUP  BY package_name, local_date
        ORDER  BY local_date ASC
    """)
    suspend fun getSessionStatsByDay(
        startDate: String,
        endDate:   String,
    ): List<SessionStatRow>

    /**
     * The first session opened each day. Powers morning hijack detection
     * (first app after unlock).
     */
    @Query("""
        SELECT local_date,
               package_name,
               app_name,
               MIN(started_at) AS started_at
        FROM   app_sessions
        WHERE  local_date BETWEEN :startDate AND :endDate
        GROUP  BY local_date
        ORDER  BY local_date ASC
    """)
    suspend fun getFirstSessionEachDay(
        startDate: String,
        endDate:   String,
    ): List<FirstSessionRow>

    @Query("DELETE FROM app_sessions WHERE local_date < :cutoffDate")
    suspend fun deleteOlderThan(cutoffDate: String)

    @Query("SELECT COUNT(DISTINCT local_date) FROM app_sessions WHERE duration_ms > 0")
    suspend fun countDistinctDaysWithData(): Int
}
```

---

## File 5 — Add `MIGRATION_5_6` to `FocusFlowDatabase.kt`

Add inside the `companion object`, after `MIGRATION_4_5`:

```kotlin
/**
 * Adds the behavioural intelligence tables: Phase 1.
 *
 * | Table                  | Purpose                                                  |
 * |------------------------|----------------------------------------------------------|
 * | daily_app_usage        | Real-time per-app foreground totals with hourly breakdown |
 * | app_sessions           | Individual closed foreground sessions                    |
 * | day_ratings            | User's 1–10 end-of-day self-rating                       |
 * | findings               | Stateful behavioural findings (lifecycle: detected→resolved) |
 * | finding_acknowledgements | User responses to findings                              |
 * | behavioural_hypotheses | Cold-start seed question answers                         |
 * | clarifying_questions   | System-generated inconsistency prompts                   |
 *
 * NOTE: daily_app_usage and app_sessions are populated by
 * [AppUsageAndSessionTracker] in real time from [AppBlockerAccessibilityService].
 * They are NOT populated retroactively from queryEvents — the OS only retains
 * UsageEvents for approximately 7 days, making retroactive population unusable
 * for pattern detection that requires 14–90 days of history.
 */
val MIGRATION_5_6: Migration = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {

        // ── daily_app_usage ───────────────────────────────────────────────────
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `daily_app_usage` (
                `date`          TEXT    NOT NULL,
                `package_name`  TEXT    NOT NULL,
                `app_name`      TEXT    NOT NULL,
                `category`      TEXT,
                `foreground_ms` INTEGER NOT NULL DEFAULT 0,
                `hourly_ms`     TEXT    NOT NULL DEFAULT '',
                `launch_count`  INTEGER NOT NULL DEFAULT 0,
                `last_used_at`  INTEGER NOT NULL DEFAULT 0,
                PRIMARY KEY(`date`, `package_name`)
            )
        """.trimIndent())
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `idx_dau_package_date` " +
            "ON `daily_app_usage` (`package_name`, `date`)"
        )

        // ── app_sessions ──────────────────────────────────────────────────────
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `app_sessions` (
                `id`           INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `package_name` TEXT    NOT NULL,
                `app_name`     TEXT    NOT NULL,
                `started_at`   INTEGER NOT NULL,
                `ended_at`     INTEGER NOT NULL,
                `duration_ms`  INTEGER NOT NULL,
                `local_date`   TEXT    NOT NULL
            )
        """.trimIndent())
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `idx_as_package_date` " +
            "ON `app_sessions` (`package_name`, `local_date`)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `idx_as_started_at` " +
            "ON `app_sessions` (`started_at`)"
        )

        // ── day_ratings ───────────────────────────────────────────────────────
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `day_ratings` (
                `date`        TEXT    NOT NULL,
                `rating`      INTEGER NOT NULL,
                `context_tag` TEXT,
                `note`        TEXT,
                `app_tags`    TEXT    NOT NULL DEFAULT '[]',
                `word_tags`   TEXT    NOT NULL DEFAULT '[]',
                `created_at`  TEXT    NOT NULL,
                `updated_at`  TEXT    NOT NULL,
                PRIMARY KEY(`date`)
            )
        """.trimIndent())

        // ── findings ──────────────────────────────────────────────────────────
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `findings` (
                `id`                   TEXT    NOT NULL,
                `detection_type`       TEXT    NOT NULL,
                `subject_package`      TEXT,
                `subject_app_name`     TEXT,
                `state`                TEXT    NOT NULL DEFAULT 'detected',
                `evidence_fingerprint` TEXT    NOT NULL,
                `evidence_json`        TEXT    NOT NULL,
                `headline`             TEXT    NOT NULL,
                `body`                 TEXT    NOT NULL,
                `evidence_line`        TEXT    NOT NULL,
                `first_detected_at`    TEXT    NOT NULL,
                `last_updated_at`      TEXT    NOT NULL,
                `seen_at`              TEXT,
                `resolved_at`          TEXT,
                `suppressed_until`     TEXT,
                PRIMARY KEY(`id`)
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX IF NOT EXISTS `idx_findings_state` ON `findings` (`state`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `idx_findings_package` ON `findings` (`subject_package`)")

        // ── finding_acknowledgements ──────────────────────────────────────────
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `finding_acknowledgements` (
                `id`                   INTEGER NOT NULL,
                `finding_id`           TEXT    NOT NULL,
                `response`             TEXT    NOT NULL,
                `evidence_fingerprint` TEXT    NOT NULL,
                `created_at`           TEXT    NOT NULL,
                `note`                 TEXT,
                PRIMARY KEY(`id`),
                FOREIGN KEY(`finding_id`) REFERENCES `findings`(`id`) ON DELETE CASCADE
            )
        """.trimIndent())
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `idx_finding_ack_finding_id` " +
            "ON `finding_acknowledgements` (`finding_id`)"
        )

        // ── behavioural_hypotheses ────────────────────────────────────────────
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `behavioural_hypotheses` (
                `id`             TEXT NOT NULL,
                `question_id`    TEXT NOT NULL,
                `answer_text`    TEXT NOT NULL,
                `answer_package` TEXT,
                `created_at`     TEXT NOT NULL,
                PRIMARY KEY(`id`)
            )
        """.trimIndent())

        // ── clarifying_questions ──────────────────────────────────────────────
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `clarifying_questions` (
                `id`              TEXT NOT NULL,
                `question_type`   TEXT NOT NULL,
                `date_of_concern` TEXT NOT NULL,
                `context_json`    TEXT NOT NULL,
                `asked_at`        TEXT NOT NULL,
                `response`        TEXT,
                `responded_at`    TEXT,
                PRIMARY KEY(`id`)
            )
        """.trimIndent())
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `idx_cq_asked_at` " +
            "ON `clarifying_questions` (`asked_at`)"
        )
    }
}
```

---

## File 6 — Update `FocusFlowDatabase.kt` `@Database` annotation

Change `version = 5` to `version = 6` and add the new entities and DAO accessors:

```kotlin
@Database(
    entities = [
        TaskEntity::class,
        FocusSessionEntity::class,
        FocusOverrideEntity::class,
        DailyCompletionEntity::class,
        AchievementEntity::class,
        WeeklyInsightEntity::class,
        ReportNoteEntity::class,
        // Phase 1 — behavioural intelligence
        DailyAppUsageEntity::class,
        AppSessionEntity::class,
        DayRatingEntity::class,
        FindingEntity::class,
        FindingAcknowledgementEntity::class,
        BehaviouralHypothesisEntity::class,
        ClarifyingQuestionEntity::class,
    ],
    version = 6,   // was 5
    exportSchema = true,
)
abstract class FocusFlowDatabase : RoomDatabase() {

    // existing — unchanged
    abstract fun taskDao(): TaskDao
    abstract fun focusSessionDao(): FocusSessionDao
    abstract fun focusOverrideDao(): FocusOverrideDao
    abstract fun dailyCompletionDao(): DailyCompletionDao
    abstract fun achievementDao(): AchievementDao
    abstract fun weeklyInsightDao(): WeeklyInsightDao
    abstract fun reportNotesDao(): ReportNotesDao

    // Phase 1 additions
    abstract fun dailyAppUsageDao(): DailyAppUsageDao
    abstract fun appSessionDao(): AppSessionDao
    abstract fun dayRatingDao(): DayRatingDao
    abstract fun findingDao(): FindingDao
    abstract fun findingAcknowledgementDao(): FindingAcknowledgementDao
    abstract fun behaviouralHypothesisDao(): BehaviouralHypothesisDao
    abstract fun clarifyingQuestionDao(): ClarifyingQuestionDao
```

Add `MIGRATION_5_6` to the builder in `AppModule.init()`:

```kotlin
database = Room.databaseBuilder(
    app,
    FocusFlowDatabase::class.java,
    FocusFlowDatabase.DB_NAME,
)
    .addMigrations(
        FocusFlowDatabase.MIGRATION_0_1,
        FocusFlowDatabase.MIGRATION_1_2,
        FocusFlowDatabase.MIGRATION_2_3,
        FocusFlowDatabase.MIGRATION_3_4,
        FocusFlowDatabase.MIGRATION_4_5,
        FocusFlowDatabase.MIGRATION_5_6,   // ← add
    )
    .addCallback(/* existing callback unchanged */)
    .build()
```

---

## File 7 — `AppUsageAndSessionTracker.kt`

`analytics/AppUsageAndSessionTracker.kt`

```kotlin
package com.tbtechs.focusflow.analytics

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.SystemClock
import android.util.Log
import com.tbtechs.focusflow.data.local.dao.AppSessionDao
import com.tbtechs.focusflow.data.local.dao.DailyAppUsageDao
import com.tbtechs.focusflow.data.local.entity.AppSessionEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Tracks app usage and individual sessions in real time via accessibility events.
 *
 * ## Two outputs from one hook
 *
 * **[daily_app_usage]** — per-day foreground totals with 24-bucket hourly breakdown.
 * Written every [HEARTBEAT_MS] and on every foreground transition.
 * Pattern: Curbox AppUsageTracker (heartbeat + hourly segmentation).
 *
 * **[app_sessions]** — individual closed sessions with exact timestamps.
 * Written on every foreground transition (the outgoing app's session closes).
 * Pattern: aw-android SessionParser (single open-package state machine).
 *
 * ## Why this solves the queryEvents problem
 * Android's UsageEvents are retained for ~7 days only. Behavioural detections
 * requiring 14–90 days of session data cannot rely on queryEvents. By writing
 * from the accessibility service that is already intercepting every foreground
 * transition for blocking, FocusFlow builds a permanent history with no new
 * permissions or background work.
 *
 * ## Thread safety
 * All DAO calls run on a single-threaded IO dispatcher — the same pattern used
 * by every other repository in this codebase. The dispatcher's serial execution
 * prevents concurrent writes to the same row without locks.
 *
 * ## Lifecycle
 * Create in [AppBlockerAccessibilityService.onServiceConnected].
 * Call [destroy] in [AppBlockerAccessibilityService.onInterrupt].
 */
class AppUsageAndSessionTracker(
    private val context: Context,
    private val dailyUsageDao: DailyAppUsageDao,
    private val sessionDao: AppSessionDao,
) {

    private companion object {
        private const val TAG            = "UsageSessionTracker"
        private const val HEARTBEAT_MS   = 20_000L
        private const val MIN_SESSION_MS = 1_000L
        private const val MAX_SESSION_MS = 4L * 60 * 60 * 1_000L   // 4 hours

        private val DATE_FMT = DateTimeFormatter.ISO_LOCAL_DATE

        // System packages that generate noise events with no user-facing meaning.
        // Matches the NEVER_BLOCK list philosophy in AppBlockerAccessibilityService.
        private val IGNORED_PACKAGES = setOf(
            "com.android.systemui",
            "com.android.launcher",
            "com.google.android.apps.nexuslauncher",
            "com.miui.home",
            "com.sec.android.app.launcher",
            "com.huawei.android.launcher",
            "com.oneplus.launcher",
        )
    }

    // Single-threaded IO: prevents race conditions on DAO read-modify-write
    private val scope        = CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(1))
    private var heartbeatJob: Job? = null

    // ── Shared foreground state ───────────────────────────────────────────────
    // elapsedRealtime = monotonic clock — immune to timezone / NTP / user changes.
    //                   Used for duration arithmetic.
    // currentTimeMillis = wall clock — used for date/hour attribution, lastUsedAt.

    @Volatile private var currentPackage      = ""
    @Volatile private var sessionStartElapsed = 0L
    @Volatile private var sessionStartWall    = 0L
    @Volatile private var lastCommitElapsed   = 0L
    @Volatile private var screenOn            = true

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Called from [AppBlockerAccessibilityService.onAccessibilityEvent] after
     * the `prefs.edit().putString("current_foreground_pkg", pkg)...apply()`
     * block (around line 1033).
     *
     * [pkg] = `ev.packageName?.toString()` (already resolved at that point).
     * [nowMs] = `now` (already `System.currentTimeMillis()` in scope).
     * [nowElapsed] = `SystemClock.elapsedRealtime()` — caller must compute.
     */
    fun onWindowStateChanged(pkg: String, nowMs: Long, nowElapsed: Long) {
        if (!screenOn)               return
        if (pkg in IGNORED_PACKAGES) return
        if (pkg == context.packageName) return
        if (pkg == currentPackage)   return   // same app: no-op

        // Close outgoing app
        closeCurrentSession(nowElapsed, nowMs)
        commitDailyUsage(nowElapsed)
        stopHeartbeat()

        // Open incoming app
        currentPackage      = pkg
        sessionStartElapsed = nowElapsed
        sessionStartWall    = nowMs
        lastCommitElapsed   = nowElapsed

        val appName  = resolveAppName(pkg)
        val category = resolveCategory(pkg)
        val date     = epochMsToLocalDate(nowMs)
        scope.launch {
            dailyUsageDao.incrementLaunchCount(
                date        = date,
                packageName = pkg,
                appName     = appName,
                category    = category,
                lastUsedAt  = nowMs,
            )
        }
        startHeartbeat()
    }

    /**
     * Called from [AppBlockerAccessibilityService.registerScreenStateReceiver]
     * when [android.content.Intent.ACTION_SCREEN_OFF] fires. Closes the current
     * session immediately — prevents durations inflating while the screen is off.
     */
    fun onScreenOff(nowMs: Long, nowElapsed: Long) {
        screenOn = false
        stopHeartbeat()
        closeCurrentSession(nowElapsed, nowMs)
        commitDailyUsage(nowElapsed)
    }

    /**
     * Called when [android.content.Intent.ACTION_USER_PRESENT] fires (user
     * unlocked — not just screen on). Tracking resumes on the next
     * [onWindowStateChanged] when the foreground app surfaces.
     */
    fun onUserPresent() {
        screenOn = true
        // Do NOT reset currentPackage here — the next TYPE_WINDOW_STATE_CHANGED
        // arrives within milliseconds.
    }

    /** Release the coroutine scope. Call from [AppBlockerAccessibilityService.onInterrupt]. */
    fun destroy() {
        stopHeartbeat()
        scope.cancel()
        Log.d(TAG, "destroyed")
    }

    // ── Heartbeat ─────────────────────────────────────────────────────────────

    private fun startHeartbeat() {
        heartbeatJob = scope.launch {
            while (true) {
                delay(HEARTBEAT_MS)
                commitDailyUsage(SystemClock.elapsedRealtime())
            }
        }
    }

    private fun stopHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = null
    }

    // ── Daily usage ───────────────────────────────────────────────────────────

    /**
     * Computes the wall-clock interval since [lastCommitElapsed], splits it
     * across hour and date boundaries via [splitIntoHourlySegments], and writes
     * each segment to [daily_app_usage].
     */
    private fun commitDailyUsage(nowElapsed: Long) {
        val pkg = currentPackage.takeIf { it.isNotEmpty() } ?: return
        if (nowElapsed <= lastCommitElapsed) return

        val startWall     = sessionStartWall + (lastCommitElapsed - sessionStartElapsed)
        val endWall       = sessionStartWall + (nowElapsed - sessionStartElapsed)
        lastCommitElapsed = nowElapsed
        if (endWall <= startWall) return

        val appName  = resolveAppName(pkg)
        val category = resolveCategory(pkg)
        val segments = splitIntoHourlySegments(startWall, endWall)

        scope.launch {
            segments.forEach { seg ->
                dailyUsageDao.addForegroundTime(
                    date        = seg.date,
                    packageName = pkg,
                    appName     = appName,
                    category    = category,
                    hour        = seg.hour,
                    durationMs  = seg.durationMs,
                    lastUsedAt  = seg.endWall,
                )
            }
        }
    }

    // ── Individual sessions ───────────────────────────────────────────────────

    /**
     * Closes the currently tracked package's session and writes to [app_sessions]
     * if the duration is within [MIN_SESSION_MS]..[MAX_SESSION_MS].
     *
     * The key insight from aw-android: only one app is ever in the foreground.
     * A different app's RESUMED implicitly closes the prior session — even if
     * the prior app's PAUSED never arrived (common on Samsung/Pixel OEMs).
     */
    private fun closeCurrentSession(nowElapsed: Long, nowMs: Long) {
        val pkg = currentPackage.takeIf { it.isNotEmpty() } ?: return
        currentPackage = ""

        val endWall    = sessionStartWall + (nowElapsed - sessionStartElapsed)
        val durationMs = endWall - sessionStartWall

        if (durationMs < MIN_SESSION_MS || durationMs > MAX_SESSION_MS) return

        scope.launch {
            sessionDao.insert(
                AppSessionEntity(
                    packageName = pkg,
                    appName     = resolveAppName(pkg),
                    startedAt   = sessionStartWall,
                    endedAt     = endWall,
                    durationMs  = durationMs,
                    localDate   = epochMsToLocalDate(sessionStartWall),
                )
            )
        }
    }

    // ── Hourly segmentation ───────────────────────────────────────────────────

    private data class HourSegment(
        val date:       String,
        val hour:       Int,
        val durationMs: Long,
        val endWall:    Long,
    )

    /**
     * Splits a wall-clock interval into per-hour segments. Handles midnight
     * crossings: a session running 23:50→00:10 produces two segments attributed
     * to the correct dates and hours.
     */
    private fun splitIntoHourlySegments(startWall: Long, endWall: Long): List<HourSegment> {
        if (endWall <= startWall) return emptyList()
        val zone     = ZoneId.systemDefault()
        val segments = mutableListOf<HourSegment>()
        var cursor   = startWall

        while (cursor < endWall) {
            val zdt      = Instant.ofEpochMilli(cursor).atZone(zone)
            val nextHour = zdt.withMinute(0).withSecond(0).withNano(0)
                .plusHours(1).toInstant().toEpochMilli()
            val segEnd   = minOf(endWall, nextHour)
            segments.add(
                HourSegment(
                    date       = zdt.toLocalDate().format(DATE_FMT),
                    hour       = zdt.hour,
                    durationMs = segEnd - cursor,
                    endWall    = segEnd,
                )
            )
            cursor = segEnd
        }
        return segments
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun epochMsToLocalDate(ms: Long): String =
        Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault())
            .toLocalDate().format(DATE_FMT)

    private fun resolveAppName(packageName: String): String = try {
        val pm = context.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
    } catch (_: PackageManager.NameNotFoundException) { packageName }

    /**
     * [ApplicationInfo.category] is API 26+ — always available at our min SDK 29.
     * Falls back to package-name heuristics for [ApplicationInfo.CATEGORY_UNDEFINED] (-1).
     */
    private fun resolveCategory(packageName: String): String {
        val api = try {
            context.packageManager.getApplicationInfo(packageName, 0).category
        } catch (_: PackageManager.NameNotFoundException) {
            ApplicationInfo.CATEGORY_UNDEFINED
        }
        return when (api) {
            ApplicationInfo.CATEGORY_SOCIAL        -> "social"
            ApplicationInfo.CATEGORY_VIDEO,
            ApplicationInfo.CATEGORY_AUDIO         -> "entertainment"
            ApplicationInfo.CATEGORY_PRODUCTIVITY  -> "productivity"
            ApplicationInfo.CATEGORY_NEWS          -> "news"
            ApplicationInfo.CATEGORY_MAPS,
            ApplicationInfo.CATEGORY_IMAGE         -> "utility"
            ApplicationInfo.CATEGORY_GAME          -> "entertainment"
            else                                   -> packageNameHeuristic(packageName)
        }
    }

    private fun packageNameHeuristic(pkg: String): String {
        val p = pkg.lowercase(Locale.ROOT)
        return when {
            p.contains("instagram") || p.contains("facebook") ||
            p.contains("twitter")   || p.contains("snapchat") ||
            p.contains("tiktok")    || p.contains("linkedin") ||
            p.contains("reddit")    -> "social"
            p.contains("youtube")   || p.contains("netflix") ||
            p.contains("spotify")   || p.contains("twitch")  -> "entertainment"
            p.contains("whatsapp")  || p.contains("telegram") ||
            p.contains("discord")   || p.contains("messenger") -> "communication"
            p.contains("chrome")    || p.contains("gmail")   ||
            p.contains("drive")     || p.contains("maps")    ||
            p.contains("calendar")  || p.contains("sheets")  -> "utility"
            else                                              -> "other"
        }
    }
}
```

---

## File 8 — Hook in `AppBlockerAccessibilityService.kt`

### A — Field declaration (add near other `private var` fields at top of class)

```kotlin
// ── Behavioural intelligence tracker ─────────────────────────────────────────
private lateinit var usageSessionTracker: AppUsageAndSessionTracker
```

### B — Initialise in `onServiceConnected()`

Add at the **end** of `onServiceConnected()`, after `startForegroundWatchdog()`:

```kotlin
usageSessionTracker = AppUsageAndSessionTracker(
    context      = this,
    dailyUsageDao = AppModule.database.dailyAppUsageDao(),
    sessionDao   = AppModule.database.appSessionDao(),
)
```

### C — The hook in `onAccessibilityEvent`

Find the block (around line 1033):
```kotlin
prefs.edit()
    .putString("current_foreground_pkg", pkg)
    .putString("current_foreground_cls", cls)
    .apply()
```

Add immediately after the `.apply()` line:
```kotlin
// ── Behavioural intelligence: usage and session tracking ─────────────────────
usageSessionTracker.onWindowStateChanged(
    pkg        = pkg,
    nowMs      = now,                            // already in scope
    nowElapsed = SystemClock.elapsedRealtime(),
)
```

### D — Screen-off and unlock in `registerScreenStateReceiver()`

Inside the existing `when (intent?.action)` block, add calls alongside the
existing `ACTION_SCREEN_OFF` and `ACTION_USER_PRESENT` handling:

```kotlin
Intent.ACTION_SCREEN_OFF -> {
    // ... existing allowance tracking code unchanged ...
    usageSessionTracker.onScreenOff(
        nowMs      = System.currentTimeMillis(),
        nowElapsed = SystemClock.elapsedRealtime(),
    )
}

Intent.ACTION_USER_PRESENT -> {
    // ... existing allowance tracking code unchanged ...
    usageSessionTracker.onUserPresent()
}
```

### E — Cleanup in `onInterrupt()`

```kotlin
override fun onInterrupt() {
    usageSessionTracker.destroy()
    // ... existing code unchanged ...
}
```

---

## Import to add to `AppBlockerAccessibilityService.kt`

```kotlin
import android.os.SystemClock
import com.tbtechs.focusflow.analytics.AppUsageAndSessionTracker
```

---

## What IMPL_1A delivers

After these 8 changes:

- `daily_app_usage` is populated for every app the user opens — hourly breakdown
  included — with no dependency on queryEvents retention limits
- `app_sessions` records every individual foreground session with exact timestamps
- The 7 remaining Phase 1 tables (`day_ratings`, `findings`, etc.) exist in the DB
  ready for IMPL_1B's repositories
- DB is at version 6
- `AppModule.database.dailyAppUsageDao()` and `appSessionDao()` are available
- Zero changes to `UsageStatsRepository` — it stays exactly as-is
- `observedMinutesByDayOfWeek` in `AnalyticsSnapshot.PhoneUsage` is untouched
