# IMPL_4 — Detection Engine (Manipulation Patterns)
## Variable Reward Loop · Infinite Session Design · Morning Hijack · Escalating Capture · Streak Lock-in
### Based on actual codebase (app.zip + done.zip)

These five detectors require `daily_app_usage` and `app_sessions` — the
tables IMPL_1A's tracker has been accumulating since it shipped. They will
return nothing useful until real history builds up (14–28 days depending on
the detector), which is expected and handled by each detector's own sample
thresholds.

**Deferred: Notification Conditioning.** This detection needs per-day,
per-hour, per-package blocking-attempt counts. Everything seen in this
conversation about blocking data is the window-scoped
`AnalyticsSnapshot.BlockingMetrics.byHour` aggregate — built somewhere inside
`AnalyticsProcessor.kt` from a source I haven't read. Writing this detector
now would mean guessing table and column names for data I haven't verified,
which is exactly the mistake this whole build has been careful to avoid.
**To complete this one: share `TemptationLogManager.kt` and whatever DAO
backs `blocking.byHour`** (likely a `FocusOverrideDao` query or a separate
temptation-log table) — that's the one file needed, everything else here
ships without it.

---

## Step 0 — Confirm shared helper visibility from IMPL_3

`evidenceFingerprint`, `newFinding`, `localDateOf`, `localHourOf`, and
`LOCAL_DATE_FMT` are `internal` in the current `FindingDetectors.kt`. This new
file is in the same package (`analytics.detection`) and can reuse them
directly. No additional visibility change is required.

If starting from an older IMPL_3 revision, change:

```kotlin
private val LOCAL_DATE_FMT = DateTimeFormatter.ISO_LOCAL_DATE
```
to:
```kotlin
internal val LOCAL_DATE_FMT = DateTimeFormatter.ISO_LOCAL_DATE
```

 And change these four `private fun` declarations to `internal fun`:
- `evidenceFingerprint`
- `localDateOf`
- `localHourOf`
- `newFinding`

 No other changes to that file. Everything else stays `private` as written.

---

## Step 1 — Add raw session query to `AppSessionDao.kt` (IMPL_1A)

`getSessionStatsByDay` gives per-day aggregates (count, avg, min, max) — enough
to identify candidate apps cheaply, but variance doesn't aggregate linearly
across days, so computing a true coefficient of variation needs the raw
per-session durations for the shortlisted candidates only.

Add this method to the existing `AppSessionDao` interface:

```kotlin
/**
 * Raw closed sessions for one package in a date range, ordered by start time.
 * Used for true stddev/CV calculations on a small shortlist of candidate
 * packages — after [getSessionStatsByDay] has cheaply filtered candidates
 * from per-day aggregates. Fetching this for every package up front would
 * be wasteful; fetching it for 2–5 shortlisted candidates is not.
 */
@Query("""
    SELECT * FROM app_sessions
    WHERE  package_name = :packageName
      AND  local_date BETWEEN :startDate AND :endDate
      AND  duration_ms > 0
    ORDER  BY started_at ASC
""")
suspend fun getSessionsForPackageInRange(
    packageName: String,
    startDate:   String,
    endDate:     String,
): List<AppSessionEntity>
```

---

## Step 2 — `ManipulationDetectors.kt`

`analytics/detection/ManipulationDetectors.kt`

Five pure functions, same shape as IMPL_3's `FindingDetectors.kt`: take data,
return `FindingEntity?`, no side effects, no DB writes.

**Design note on multi-candidate detectors:** four of these five can match
several apps at once. Each function returns at most one finding — the
strongest match — rather than one per qualifying app. This isn't just
simplicity: `FindingRepository.insertIfCooldownElapsed` silently drops any
finding submitted while the 7-day cooldown is active, so if a detector
called `submit()` for three qualifying apps in the same run, two would be
lost outright rather than queued. Returning only the strongest candidate
means nothing is wasted — a second-place app that still qualifies next
week gets evaluated fresh then.

```kotlin
package com.tbtechs.focusflow.analytics.detection

import com.tbtechs.focusflow.data.local.dao.AppUsageRangeRow
import com.tbtechs.focusflow.data.local.dao.FirstSessionRow
import com.tbtechs.focusflow.data.local.dao.SessionStatRow
import com.tbtechs.focusflow.data.local.entity.AppSessionEntity
import com.tbtechs.focusflow.data.local.entity.DayRatingEntity
import com.tbtechs.focusflow.data.local.entity.FindingEntity
import kotlin.math.round
import kotlin.math.sqrt

// ═════════════════════════════════════════════════════════════════════════════
// Shared helpers
// ═════════════════════════════════════════════════════════════════════════════

private data class DurationStats(val mean: Double, val stdDev: Double, val max: Double, val count: Int)

/** Population mean/stddev of session durations in ms. */
private fun durationStats(sessions: List<AppSessionEntity>): DurationStats? {
    if (sessions.isEmpty()) return null
    val values = sessions.map { it.durationMs.toDouble() }
    val mean   = values.average()
    val variance = values.sumOf { (it - mean) * (it - mean) } / values.size
    return DurationStats(mean = mean, stdDev = sqrt(variance), max = values.max(), count = values.size)
}

/** Builds a package → category map from daily_app_usage rows, latest non-null wins. */
private fun categoryMap(rows: List<AppUsageRangeRow>): Map<String, String?> {
    val map = mutableMapOf<String, String?>()
    rows.forEach { row -> if (row.category != null || row.packageName !in map) map[row.packageName] = row.category }
    return map
}

/** Builds a package → display name map, latest wins. */
private fun appNameMap(rows: List<AppUsageRangeRow>): Map<String, String> =
    rows.associate { it.packageName to it.appName }

private const val MS_PER_MIN = 60_000.0

// ═════════════════════════════════════════════════════════════════════════════
// 1 — Variable Reward Loop
// ═════════════════════════════════════════════════════════════════════════════

/**
 * Pass 1 (cheap): from per-day aggregates, find packages with enough sessions
 * per day and a short exact mean session length. The exact overall mean is
 * derivable from day-level aggregates alone (a weighted average of daily
 * means equals the true overall mean) — no raw fetch needed for this filter.
 *
 * Pass 2 (targeted): for the shortlist, fetch raw sessions to compute the true
 * coefficient of variation, which day-level aggregates cannot provide (variance
 * does not aggregate linearly).
 *
 * [rawSessionsByPackage] should contain entries only for packages that passed
 * pass 1 — the runner fetches these selectively.
 */
fun detectVariableRewardLoop(
    dailyStats: List<SessionStatRow>,
    rawSessionsByPackage: Map<String, List<AppSessionEntity>>,
    appNames: Map<String, String>,
): FindingEntity? {
    val byPackage = dailyStats.groupBy { it.packageName }

    data class Candidate(val pkg: String, val cv: Double, val meanMin: Double, val sampleDays: Int)

    val candidates = byPackage.mapNotNull { (pkg, days) ->
        val totalSessions = days.sumOf { it.sessionCount }
        val sampleDays     = days.size
        if (sampleDays < 10) return@mapNotNull null
        val avgLaunchPerDay = totalSessions.toDouble() / sampleDays
        if (avgLaunchPerDay < 5.0) return@mapNotNull null

        // Exact overall mean from weighted daily means
        val weightedMeanMs = days.sumOf { it.avgDurationMs * it.sessionCount } / totalSessions
        if (weightedMeanMs > 4 * MS_PER_MIN) return@mapNotNull null

        val raw = rawSessionsByPackage[pkg] ?: return@mapNotNull null
        val stats = durationStats(raw) ?: return@mapNotNull null
        if (stats.mean == 0.0) return@mapNotNull null
        val cv = stats.stdDev / stats.mean
        if (cv < 0.9) return@mapNotNull null

        Candidate(pkg, cv, stats.mean / MS_PER_MIN, sampleDays)
    }

    val winner = candidates.maxByOrNull { it.cv } ?: return null
    val appName = appNames[winner.pkg] ?: winner.pkg
    val raw = rawSessionsByPackage.getValue(winner.pkg)
    val minMin = raw.minOf { it.durationMs } / MS_PER_MIN
    val maxMin = raw.maxOf { it.durationMs } / MS_PER_MIN
    val avgLaunches = round((raw.size.toDouble() / winner.sampleDays) * 10) / 10

    val fingerprint = evidenceFingerprint(
        "VARIABLE_REWARD_LOOP", winner.pkg,
        sampleSize = raw.size, keyMetric = winner.cv, metricUnit = 0.1,
    )

    return newFinding(
        detectionType = "VARIABLE_REWARD_LOOP",
        headline      = "Variable reward loop",
        body          = "You open $appName about $avgLaunches times a day. Average session: " +
                         "${round(winner.meanMin * 10) / 10}m — but they range from " +
                         "${round(minMin * 10) / 10}m to ${round(maxMin * 10) / 10}m. Sessions with " +
                         "no consistent length are the signature of a feed with no designed stopping point.",
        evidenceLine  = "Observed across ${winner.sampleDays} days, ${raw.size} sessions",
        fingerprint   = fingerprint,
        evidenceJson  = """{"cv":${round(winner.cv * 100) / 100},"avg_launches_per_day":$avgLaunches,"sample_days":${winner.sampleDays}}""",
    ).copy(subjectPackage = winner.pkg, subjectAppName = appName)
}

// ═════════════════════════════════════════════════════════════════════════════
// 2 — Infinite Session Design
// ═════════════════════════════════════════════════════════════════════════════

/**
 * Same two-pass shape as variable reward loop, but looking for high *duration*
 * variance rather than high frequency with short duration. Excludes 'utility'
 * category apps — a maps or banking app legitimately has unpredictable session
 * lengths that have nothing to do with a lack of a designed stopping point.
 */
fun detectInfiniteSessionDesign(
    dailyStats: List<SessionStatRow>,
    rawSessionsByPackage: Map<String, List<AppSessionEntity>>,
    appNames: Map<String, String>,
    categories: Map<String, String?>,
): FindingEntity? {
    val byPackage = dailyStats.groupBy { it.packageName }

    data class Candidate(val pkg: String, val stdDevMin: Double, val meanMin: Double, val maxMin: Double, val sampleDays: Int, val sessionCount: Int)

    val candidates = byPackage.mapNotNull { (pkg, days) ->
        if (categories[pkg] == "utility") return@mapNotNull null
        val sampleDays = days.size
        if (sampleDays < 14) return@mapNotNull null

        val totalSessions = days.sumOf { it.sessionCount }
        val weightedMeanMs = days.sumOf { it.avgDurationMs * it.sessionCount } / totalSessions
        if (weightedMeanMs <= 5 * MS_PER_MIN) return@mapNotNull null

        val raw = rawSessionsByPackage[pkg] ?: return@mapNotNull null
        val stats = durationStats(raw) ?: return@mapNotNull null
        val stdDevMin = stats.stdDev / MS_PER_MIN
        val meanMin   = stats.mean / MS_PER_MIN
        val maxMin    = stats.max / MS_PER_MIN
        if (stdDevMin <= 20.0) return@mapNotNull null
        if (maxMin <= 3 * meanMin) return@mapNotNull null

        Candidate(pkg, stdDevMin, meanMin, maxMin, sampleDays, raw.size)
    }

    val winner = candidates.maxByOrNull { it.stdDevMin } ?: return null
    val appName = appNames[winner.pkg] ?: winner.pkg

    val fingerprint = evidenceFingerprint(
        "INFINITE_SESSION_DESIGN", winner.pkg,
        sampleSize = winner.sessionCount, keyMetric = winner.stdDevMin, metricUnit = 5.0,
    )

    return newFinding(
        detectionType = "INFINITE_SESSION_DESIGN",
        headline      = "Infinite session design",
        body          = "Your $appName sessions have no typical length. They've run as short as a " +
                         "few minutes and as long as ${round(winner.maxMin).toInt()}m. That variance " +
                         "isn't your preference — it's the absence of a designed stopping point. You " +
                         "supply the brake each time.",
        evidenceLine  = "Observed across ${winner.sampleDays} days, ${winner.sessionCount} sessions",
        fingerprint   = fingerprint,
        evidenceJson  = """{"stddev_min":${round(winner.stdDevMin)},"mean_min":${round(winner.meanMin)},"max_min":${round(winner.maxMin)}}""",
    ).copy(subjectPackage = winner.pkg, subjectAppName = appName)
}

// ═════════════════════════════════════════════════════════════════════════════
// 3 — Morning Hijack
// ═════════════════════════════════════════════════════════════════════════════

data class MorningHijackResult(val finding: FindingEntity, val qualifyingDates: List<String>, val packageName: String)

/**
 * For each package that was ever the first app opened in the window, checks
 * what fraction of all data-bearing days it held that position. Only
 * social/entertainment category packages are eligible — a journalist who
 * deliberately opens a news app first is not what this detects.
 *
 * Returns the qualifying dates alongside the finding so the runner can check
 * day ratings on those specific dates and decide whether to queue a
 * clarifying question.
 */
fun detectMorningHijack(
    firstSessions: List<FirstSessionRow>,
    categories: Map<String, String?>,
): MorningHijackResult? {
    if (firstSessions.size < 7) return null
    val totalDays = firstSessions.size

    val byPackage = firstSessions.groupBy { it.packageName }
    data class Candidate(val pkg: String, val appName: String, val fraction: Double, val dates: List<String>)

    val candidates = byPackage.mapNotNull { (pkg, rows) ->
        val category = categories[pkg]
        if (category != "social" && category != "entertainment") return@mapNotNull null
        val fraction = rows.size.toDouble() / totalDays
        if (fraction < 0.65) return@mapNotNull null
        Candidate(pkg, rows.first().appName, fraction, rows.map { it.localDate })
    }

    val winner = candidates.maxByOrNull { it.fraction } ?: return null
    val pct = round(winner.fraction * 100).toInt()

    val fingerprint = evidenceFingerprint(
        "MORNING_HIJACK", winner.pkg,
        sampleSize = totalDays, keyMetric = winner.fraction, metricUnit = 0.05,
    )

    val finding = newFinding(
        detectionType = "MORNING_HIJACK",
        headline      = "Morning hijack",
        body          = "On $pct% of recent mornings, ${winner.appName} was the first thing you " +
                         "opened — before starting anything else. Apps compete for this window " +
                         "specifically. Whoever captures your attention first shapes the mode you " +
                         "work in for the rest of the day.",
        evidenceLine  = "Observed on ${winner.dates.size} of $totalDays days",
        fingerprint   = fingerprint,
        evidenceJson  = """{"fraction_pct":$pct,"sample_days":$totalDays}""",
    ).copy(subjectPackage = winner.pkg, subjectAppName = winner.appName)

    return MorningHijackResult(finding, winner.dates, winner.pkg)
}

// ═════════════════════════════════════════════════════════════════════════════
// 4 — Escalating Capture
// ═════════════════════════════════════════════════════════════════════════════

/**
 * Buckets a 28-day window into four 7-day weeks (week 4 = most recent) and
 * checks for monotonically increasing average daily usage with at least 40%
 * total growth from week 1 to week 4. A week bucket needs at least 4 days of
 * data present to be trusted — sparse weeks are skipped rather than treated
 * as zero.
 */
fun detectEscalatingCapture(
    usageRows: List<AppUsageRangeRow>,
    today: java.time.LocalDate,
): FindingEntity? {
    val weekOf = { date: java.time.LocalDate ->
        val daysAgo = java.time.temporal.ChronoUnit.DAYS.between(date, today)
        when {
            daysAgo < 7  -> 4
            daysAgo < 14 -> 3
            daysAgo < 21 -> 2
            daysAgo < 28 -> 1
            else         -> 0   // out of window, discarded
        }
    }

    val byPackage = usageRows.groupBy { it.packageName }
    data class Candidate(val pkg: String, val appName: String, val growth: Double, val week1Min: Double, val week4Min: Double)

    val candidates = byPackage.mapNotNull { (pkg, rows) ->
        val byWeek = rows.mapNotNull { row ->
            val date = runCatching { java.time.LocalDate.parse(row.date) }.getOrNull() ?: return@mapNotNull null
            val w = weekOf(date)
            if (w == 0) null else w to row.foregroundMs
        }.groupBy({ it.first }, { it.second })

        if (listOf(1, 2, 3, 4).any { (byWeek[it]?.size ?: 0) < 4 }) return@mapNotNull null

        val weekAvg = (1..4).associateWith { w -> byWeek.getValue(w).average() }
        val w1 = weekAvg.getValue(1); val w2 = weekAvg.getValue(2)
        val w3 = weekAvg.getValue(3); val w4 = weekAvg.getValue(4)

        if (!(w2 > w1 && w3 > w2 && w4 > w3)) return@mapNotNull null
        if (w1 <= 5 * MS_PER_MIN) return@mapNotNull null   // baseline too small to be meaningful

        val growth = (w4 - w1) / w1
        if (growth < 0.40) return@mapNotNull null

        Candidate(pkg, rows.first().appName, growth, w1 / MS_PER_MIN, w4 / MS_PER_MIN)
    }

    val winner = candidates.maxByOrNull { it.growth } ?: return null
    val growthPct = round(winner.growth * 100).toInt()

    val fingerprint = evidenceFingerprint(
        "ESCALATING_CAPTURE", winner.pkg,
        sampleSize = 28, keyMetric = winner.growth, metricUnit = 0.1,
    )

    return newFinding(
        detectionType = "ESCALATING_CAPTURE",
        headline      = "Escalating capture",
        body          = "${winner.appName} was taking ${round(winner.week1Min).toInt()}m of your day " +
                         "4 weeks ago. It's at ${round(winner.week4Min).toInt()}m now — up $growthPct%. " +
                         "Usage that grows every week without a deliberate decision is the algorithm " +
                         "improving its model of you, not you choosing to spend more time there.",
        evidenceLine  = "Growth measured over 4 weeks",
        fingerprint   = fingerprint,
        evidenceJson  = """{"growth_pct":$growthPct,"week1_min":${round(winner.week1Min).toInt()},"week4_min":${round(winner.week4Min).toInt()}}""",
    ).copy(subjectPackage = winner.pkg, subjectAppName = winner.appName)
}

// ═════════════════════════════════════════════════════════════════════════════
// 5 — Streak Lock-in
// ═════════════════════════════════════════════════════════════════════════════

private val STREAK_ELIGIBLE_CATEGORIES = setOf("social", "entertainment", "communication")

/**
 * Finds apps opened on nearly every day of the window regardless of how the
 * user rated those days — the signature of loss-aversion-driven engagement
 * rather than a routine the user actually values. Requires at least 3 day
 * ratings in the window to have any comparison basis at all.
 */
fun detectStreakLockIn(
    usageRows: List<AppUsageRangeRow>,
    ratings: List<DayRatingEntity>,
    windowDays: Int,
): FindingEntity? {
    if (ratings.size < 3) return null
    val ratingByDate = ratings.associateBy { it.date }

    val byPackage = usageRows
        .filter { it.launchCount > 0 && it.category in STREAK_ELIGIBLE_CATEGORIES }
        .groupBy { it.packageName }

    data class Candidate(
        val pkg: String, val appName: String, val consistency: Double,
        val openDates: Set<String>, val lowRatedDates: List<String>, val avgLowRating: Double,
    )

    val candidates = byPackage.mapNotNull { (pkg, rows) ->
        val openDates = rows.map { it.date }.toSet()
        val consistency = openDates.size.toDouble() / windowDays
        if (consistency < 0.90) return@mapNotNull null

        val lowRatedDates = openDates.filter { d -> (ratingByDate[d]?.rating ?: 99) <= 4 }
        if (lowRatedDates.size < 3) return@mapNotNull null

        val avgLow = lowRatedDates.mapNotNull { ratingByDate[it]?.rating }.average()
        Candidate(pkg, rows.first().appName, consistency, openDates, lowRatedDates, avgLow)
    }

    val winner = candidates.maxByOrNull { it.consistency } ?: return null

    val fingerprint = evidenceFingerprint(
        "STREAK_LOCK_IN", winner.pkg,
        sampleSize = winner.openDates.size, keyMetric = winner.consistency, metricUnit = 0.05,
    )

    return newFinding(
        detectionType = "STREAK_LOCK_IN",
        headline      = "Streak lock-in",
        body          = "You've opened ${winner.appName} on ${winner.openDates.size} of the last " +
                         "$windowDays days — including ${winner.lowRatedDates.size} days you rated " +
                         "${round(winner.avgLowRating * 10) / 10}/10 or lower. Streak mechanics use loss " +
                         "aversion — the fear of losing what you've built — to require daily engagement " +
                         "regardless of whether the day is going well.",
        evidenceLine  = "Observed across ${winner.openDates.size} days, ${winner.lowRatedDates.size} low-rated",
        fingerprint   = fingerprint,
        evidenceJson  = """{"consistency_pct":${round(winner.consistency * 100).toInt()},"low_rated_days":${winner.lowRatedDates.size},"avg_low_rating":${round(winner.avgLowRating * 10) / 10}}""",
    ).copy(subjectPackage = winner.pkg, subjectAppName = winner.appName)
}
```

---

## Step 3 — Extend `FindingDetectionRunner.kt` (IMPL_3)

Add the new dependencies to the constructor and four new methods to `runAll()`.
This stays the **same class** from IMPL_3 — one canonical detection pipeline,
not a second parallel runner.

### 3.1 — Constructor additions

```kotlin
class FindingDetectionRunner(
    private val taskDao:                     TaskDao,
    private val focusSessionDao:             FocusSessionDao,
    private val findingRepository:           FindingRepository,
    // IMPL_4 additions
    private val dailyAppUsageDao:            DailyAppUsageDao,
    private val appSessionDao:               AppSessionDao,
    private val dayRatingDao:                DayRatingDao,
    private val clarifyingQuestionRepository: ClarifyingQuestionRepository,
) {
```

### 3.2 — `runAll()` additions

The order here is a soft priority: `FindingRepository`'s cooldown means only
the first detector to find a qualifying pattern on any given day actually
surfaces — the rest are silently dropped and re-evaluated fresh next run.
Ordering by product relevance means the most useful pattern wins ties.

```kotlin
suspend fun runAll() {
    // Existing IMPL_3 detectors — unchanged
    runCatching { runPostFailureCascade() }
        .onFailure { Log.e(TAG, "postFailureCascade failed: ${it.message}", it) }
    runCatching { runSessionSweetSpot() }
        .onFailure { Log.e(TAG, "sessionSweetSpot failed: ${it.message}", it) }
    runCatching { runEstimationDrift() }
        .onFailure { Log.e(TAG, "estimationDrift failed: ${it.message}", it) }
    runCatching { runDayOfWeekOutlier() }
        .onFailure { Log.e(TAG, "dayOfWeekOutlier failed: ${it.message}", it) }

    // IMPL_4 — manipulation detectors
    runCatching { runMorningHijack() }
        .onFailure { Log.e(TAG, "morningHijack failed: ${it.message}", it) }
    runCatching { runVariableRewardLoop() }
        .onFailure { Log.e(TAG, "variableRewardLoop failed: ${it.message}", it) }
    runCatching { runEscalatingCapture() }
        .onFailure { Log.e(TAG, "escalatingCapture failed: ${it.message}", it) }
    runCatching { runStreakLockIn() }
        .onFailure { Log.e(TAG, "streakLockIn failed: ${it.message}", it) }
    runCatching { runInfiniteSessionDesign() }
        .onFailure { Log.e(TAG, "infiniteSessionDesign failed: ${it.message}", it) }
}
```

### 3.3 — New private methods

```kotlin
private suspend fun runMorningHijack() {
    val start = dateRangeStart(14)
    val end   = dateRangeEnd()
    val firstSessions = appSessionDao.getFirstSessionEachDay(start, end)
    if (firstSessions.isEmpty()) return

    val usageRows  = dailyAppUsageDao.getForDateRange(start, end)
    val categories = usageRows.groupBy { it.packageName }
        .mapValues { (_, rows) -> rows.firstOrNull { it.category != null }?.category }

    val result = detectMorningHijack(firstSessions, categories) ?: return
    val didSurface = findingRepository.submit(result.finding)
    if (!didSurface) return   // cooldown active — skip the clarifying-question check too

    maybeQueueMorningHijackQuestion(result)
}

/**
 * If the user rated the qualifying morning-hijack days 7+ on average, this is
 * exactly the case the product spec calls out: someone who opens a "hijack"
 * app first thing but by their own account it's not making their days worse.
 * Worth asking rather than assuming.
 */
private suspend fun maybeQueueMorningHijackQuestion(result: MorningHijackResult) {
    val ratings = dayRatingDao.getForDateRange(
        result.qualifyingDates.min(), result.qualifyingDates.max(),
    ).filter { it.date in result.qualifyingDates }

    if (ratings.size < 3) return
    val avgRating = ratings.map { it.rating }.average()
    if (avgRating < 7.0) return

    val mostRecent = ratings.maxByOrNull { it.date } ?: return
    val appName    = result.finding.subjectAppName ?: return

    val contextJson = """{"appName":"$appName","date":"${mostRecent.date}","rating":"${mostRecent.rating}"}"""
    clarifyingQuestionRepository.submitIfAllowed(
        com.tbtechs.focusflow.data.local.entity.ClarifyingQuestionEntity(
            id            = java.util.UUID.randomUUID().toString(),
            questionType  = "morning_high_rating",
            dateOfConcern = mostRecent.date,
            contextJson   = contextJson,
            askedAt       = java.time.Instant.now().toString(),
            response      = null,
            respondedAt   = null,
        )
    )
}

private suspend fun runVariableRewardLoop() {
    val start = dateRangeStart(21)
    val end   = dateRangeEnd()
    val dailyStats = appSessionDao.getSessionStatsByDay(start, end)
    if (dailyStats.isEmpty()) return

    val usageRows = dailyAppUsageDao.getForDateRange(start, end)
    val names     = usageRows.associate { it.packageName to it.appName }

    // Pass 1 candidate packages (cheap filter, mirrors the detector's own logic
    // loosely so we don't fetch raw sessions for packages that can't possibly qualify)
    val roughCandidates = dailyStats.groupBy { it.packageName }
        .filter { (_, days) -> days.size >= 10 && (days.sumOf { it.sessionCount }.toDouble() / days.size) >= 5.0 }
        .keys

    val rawByPackage = loadRawSessions(roughCandidates, start, end)

    detectVariableRewardLoop(dailyStats, rawByPackage, names)?.let { findingRepository.submit(it) }
}

private suspend fun runInfiniteSessionDesign() {
    val start = dateRangeStart(21)
    val end   = dateRangeEnd()
    val dailyStats = appSessionDao.getSessionStatsByDay(start, end)
    if (dailyStats.isEmpty()) return

    val usageRows  = dailyAppUsageDao.getForDateRange(start, end)
    val names      = usageRows.associate { it.packageName to it.appName }
    val categories = usageRows.groupBy { it.packageName }
        .mapValues { (_, rows) -> rows.firstOrNull { it.category != null }?.category }

    val roughCandidates = dailyStats.groupBy { it.packageName }
        .filter { (pkg, days) -> categories[pkg] != "utility" && days.size >= 14 }
        .keys

    val rawByPackage = loadRawSessions(roughCandidates, start, end)

    detectInfiniteSessionDesign(dailyStats, rawByPackage, names, categories)
        ?.let { findingRepository.submit(it) }
}

private suspend fun runEscalatingCapture() {
    val start = dateRangeStart(28)
    val end   = dateRangeEnd()
    val usageRows = dailyAppUsageDao.getForDateRange(start, end)
    if (usageRows.isEmpty()) return
    detectEscalatingCapture(usageRows, java.time.LocalDate.now())?.let { findingRepository.submit(it) }
}

private suspend fun runStreakLockIn() {
    val windowDays = 14
    val start = dateRangeStart(windowDays.toLong())
    val end   = dateRangeEnd()
    val usageRows = dailyAppUsageDao.getForDateRange(start, end)
    if (usageRows.isEmpty()) return
    val ratings = dayRatingDao.getForDateRange(start, end)
    detectStreakLockIn(usageRows, ratings, windowDays)?.let { findingRepository.submit(it) }
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
```

### 3.4 — New imports for `FindingDetectionRunner.kt`

```kotlin
import com.tbtechs.focusflow.analytics.detection.MorningHijackResult
import com.tbtechs.focusflow.analytics.detection.detectEscalatingCapture
import com.tbtechs.focusflow.analytics.detection.detectInfiniteSessionDesign
import com.tbtechs.focusflow.analytics.detection.detectMorningHijack
import com.tbtechs.focusflow.analytics.detection.detectStreakLockIn
import com.tbtechs.focusflow.analytics.detection.detectVariableRewardLoop
import com.tbtechs.focusflow.data.local.dao.AppSessionDao
import com.tbtechs.focusflow.data.local.dao.DailyAppUsageDao
import com.tbtechs.focusflow.data.local.dao.DayRatingDao
import com.tbtechs.focusflow.data.repository.ClarifyingQuestionRepository
```

---

## Step 4 — `AppModule.kt` — update the runner construction

Replace the `FindingDetectionRunner(...)` construction from IMPL_3 with:

```kotlin
findingDetectionRunner = FindingDetectionRunner(
    taskDao                      = database.taskDao(),
    focusSessionDao              = database.focusSessionDao(),
    findingRepository            = findingRepository,
    dailyAppUsageDao             = database.dailyAppUsageDao(),
    appSessionDao                = database.appSessionDao(),
    dayRatingDao                 = database.dayRatingDao(),
    clarifyingQuestionRepository = clarifyingQuestionRepository,
)
```

No change needed to `BackgroundFetchWorker.kt` — it already calls
`findingDetectionRunner.runAll()` once daily from IMPL_3; the extended
`runAll()` now covers all nine detectors through that same call.

---

## What IMPL_4 delivers

- Five manipulation detectors, each gated on realistic sample sizes (10–28
  days depending on the detector) so they stay silent rather than firing on
  noise while the tracker's history is still thin
- The Morning Hijack detector wires into the clarifying-question
  infrastructure from IMPL_1B exactly as the original spec intended — a
  hijack pattern that correlates with high day ratings gets a question
  instead of an assumption
- One canonical detection pipeline — `FindingDetectionRunner.runAll()` — now
  running nine detectors total, still triggered once daily from
  `BackgroundFetchWorker`, still fully decoupled from whether the user opens
  Stats that day

## What's still deferred

**Notification Conditioning** — needs the actual blocking-attempt data
source. Share `TemptationLogManager.kt` and its backing DAO/table and this
becomes a same-day addition to this same file and runner.

**IMPL_5** — substitution detection (needs 4 weeks of `daily_app_usage` for
2+ apps, same tables as here, just a different comparison) and the adaptive
daily allowance suggestion (needs the day-rating × usage correlation this
conversation specced early on). Both are buildable now with what exists —
they were sequenced last because they're lower-urgency than getting real
manipulation detection running.
