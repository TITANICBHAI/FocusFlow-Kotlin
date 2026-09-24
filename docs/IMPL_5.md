# IMPL_5 — Substitution Detection · Adaptive Allowance Suggestion
### Based on actual codebase (app.zip + done.zip)

**This is the last document in the originally planned sequence**
(1A → 1B → 2 → 3 → 4 → 5). Notification Conditioning remains a separate,
deferred detection because its blocking-attempt source has not been verified.
The IMPL_5 implementation and focused test sources are now present; hosted
Android verification remains pending.

Both detectors here reuse `daily_app_usage` and `day_ratings` — no new
tables, no new DAO methods beyond what IMPL_4 already wired into
`FindingDetectionRunner`. Everything in this document lives in files that
already exist.

---

## An honesty flag before building the allowance suggestion

The original spec said the allowance suggestion would appear "in the
existing daily allowance settings screen, below the manual input." I have
not seen that screen in this codebase — nothing shared so far shows a
per-app allowance/limit entity, DAO, or settings UI. Building against an
unverified integration point would repeat exactly the mistake avoided with
Notification Conditioning.

**What ships instead:** the allowance suggestion surfaces as a Finding —
through the exact same `FindingEntity` / `FindingRepository` /
`FindingCardView` / `FindingsSection` pipeline every other detection already
uses. The user sees "Based on your own data: your best-rated days average
22m on Instagram, your worst average 61m — your data suggests ~25m" as a
card in Findings, same as everything else. If an actual allowance-setting
screen exists, wiring a "Set 25m limit" action into it is a small follow-up
once that file is shared — the detection and suggestion math doesn't change.

---

## Step 0 — Extract a shared weekly-average helper (refactor of IMPL_4)

`detectEscalatingCapture` (shipped in IMPL_4) and the new `detectSubstitution`
both need the same thing: per-package average usage across four 7-day
buckets. Extracting this now avoids duplicating the bucketing logic.

In `analytics/detection/ManipulationDetectors.kt`, add these imports at the
top if not already present:

```kotlin
import java.time.LocalDate
import java.time.temporal.ChronoUnit
```

Add this shared helper and constant near the other shared helpers
(`durationStats`, `categoryMap`, etc.):

```kotlin
private const val MIN_DAYS_PER_WEEK = 4

internal data class WeeklyAverages(
    val appName: String,
    val week1: Double,   // avg foreground_ms, oldest 7-day bucket
    val week2: Double,
    val week3: Double,
    val week4: Double,   // most recent 7-day bucket
)

/**
 * Buckets [usageRows] into four 7-day weeks relative to [today] (week 4 =
 * most recent) and returns the per-package average daily foreground_ms in
 * each bucket. A package is excluded entirely if any bucket has fewer than
 * [MIN_DAYS_PER_WEEK] days of data — a sparse week is skipped rather than
 * averaged from too few points and mistaken for a real trend.
 */
internal fun computeWeeklyAverages(
    usageRows: List<AppUsageRangeRow>,
    today: LocalDate,
): Map<String, WeeklyAverages> {
    val weekOf = { date: LocalDate ->
        val daysAgo = ChronoUnit.DAYS.between(date, today)
        when {
            daysAgo < 7  -> 4
            daysAgo < 14 -> 3
            daysAgo < 21 -> 2
            daysAgo < 28 -> 1
            else         -> 0   // outside the 28-day window, discarded
        }
    }

    return usageRows.groupBy { it.packageName }.mapNotNull { (pkg, rows) ->
        val byWeek = rows.mapNotNull { row ->
            val date = runCatching { LocalDate.parse(row.date) }.getOrNull() ?: return@mapNotNull null
            val w = weekOf(date)
            if (w == 0) null else w to row.foregroundMs
        }.groupBy({ it.first }, { it.second })

        if (listOf(1, 2, 3, 4).any { (byWeek[it]?.size ?: 0) < MIN_DAYS_PER_WEEK }) return@mapNotNull null

        val avg = (1..4).associateWith { w -> byWeek.getValue(w).average() }
        pkg to WeeklyAverages(
            appName = rows.first().appName,
            week1 = avg.getValue(1), week2 = avg.getValue(2),
            week3 = avg.getValue(3), week4 = avg.getValue(4),
        )
    }.toMap()
}
```

**Replace the body of `detectEscalatingCapture`** (from IMPL_4) with the
version below — same signature, same behaviour, now built on the shared
helper instead of its own inline bucketing:

```kotlin
fun detectEscalatingCapture(
    usageRows: List<AppUsageRangeRow>,
    today: LocalDate,
): FindingEntity? {
    val weekly = computeWeeklyAverages(usageRows, today)
    if (weekly.isEmpty()) return null

    data class Candidate(val pkg: String, val appName: String, val growth: Double, val week1Min: Double, val week4Min: Double)

    val candidates = weekly.mapNotNull { (pkg, w) ->
        if (!(w.week2 > w.week1 && w.week3 > w.week2 && w.week4 > w.week3)) return@mapNotNull null
        if (w.week1 <= 5 * MS_PER_MIN) return@mapNotNull null
        val growth = (w.week4 - w.week1) / w.week1
        if (growth < 0.40) return@mapNotNull null
        Candidate(pkg, w.appName, growth, w.week1 / MS_PER_MIN, w.week4 / MS_PER_MIN)
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
```

The old inline `weekOf` lambda and per-package bucketing block inside
`detectEscalatingCapture` are removed — the shared helper does that now.
Behaviour is identical; the only difference is where the logic lives.

---

## Step 1 — Two new detectors, added to `ManipulationDetectors.kt`

Append these below `detectEscalatingCapture`.

### 1.1 — Substitution

```kotlin
// ═════════════════════════════════════════════════════════════════════════════
// 6 — Substitution
// ═════════════════════════════════════════════════════════════════════════════

/**
 * Finds app pairs where one app's usage dropped 30%+ over 4 weeks while
 * another rose 30%+, and the combined total barely moved — the signature of
 * time being redirected rather than actually reduced. Total screen time
 * staying flat while its composition shifts is easy to miss without this
 * comparison; a person who feels good about cutting one app rarely checks
 * whether another quietly absorbed the difference.
 *
 * Unlike single-app detections, this finding has no [FindingEntity.subjectPackage] —
 * it describes a relationship between two apps, not one app's behaviour.
 */
fun detectSubstitution(
    usageRows: List<AppUsageRangeRow>,
    today: LocalDate,
): FindingEntity? {
    val weekly = computeWeeklyAverages(usageRows, today)
    if (weekly.size < 2) return null

    data class Pair(
        val downName: String, val downPct: Int,
        val upName: String, val upPct: Int,
        val combinedShift: Int,
    )

    val entries = weekly.entries.toList()
    val pairs = mutableListOf<Pair>()

    for (i in entries.indices) {
        for (j in entries.indices) {
            if (i == j) continue
            val a = entries[i].value   // candidate "down" app
            val b = entries[j].value   // candidate "up" app
            if (a.week1 <= 0.0 || b.week1 <= 0.0) continue

            val pctA = (a.week4 - a.week1) / a.week1
            val pctB = (b.week4 - b.week1) / b.week1
            if (pctA > -0.30) continue
            if (pctB < 0.30) continue

            val totalW1 = a.week1 + b.week1
            val totalW4 = a.week4 + b.week4
            val totalShiftFrac = kotlin.math.abs(totalW4 - totalW1) / totalW1
            if (totalShiftFrac >= 0.15) continue

            val downPct = round(-pctA * 100).toInt()
            val upPct   = round(pctB * 100).toInt()
            pairs.add(Pair(a.appName, downPct, b.appName, upPct, downPct + upPct))
        }
    }

    val winner = pairs.maxByOrNull { it.combinedShift } ?: return null

    val fingerprint = evidenceFingerprint(
        "SUBSTITUTION", "${winner.downName}|${winner.upName}",
        sampleSize = 28, keyMetric = winner.combinedShift.toDouble(), metricUnit = 10.0,
    )

    return newFinding(
        detectionType = "SUBSTITUTION",
        headline      = "Same time, different destination",
        body          = "Your combined time across these two apps has barely changed over the last " +
                         "4 weeks. What shifted: ${winner.downName} is down ${winner.downPct}% while " +
                         "${winner.upName} is up ${winner.upPct}%. The time budget stayed the same — " +
                         "the destination moved.",
        evidenceLine  = "Compared over 4 weeks",
        fingerprint   = fingerprint,
        evidenceJson  = """{"down_app":"${winner.downName}","down_pct":${winner.downPct},"up_app":"${winner.upName}","up_pct":${winner.upPct}}""",
    )
}
```

### 1.2 — Allowance Suggestion

```kotlin
// ═════════════════════════════════════════════════════════════════════════════
// 7 — Allowance Suggestion
// ═════════════════════════════════════════════════════════════════════════════

private val ALLOWANCE_ELIGIBLE_CATEGORIES = setOf("social", "entertainment")

/**
 * Compares usage of a discretionary app on the user's highest-rated days
 * against their lowest-rated days. When usage is meaningfully lower on the
 * days the user themself called good, that gap — not a generic guideline —
 * is the suggestion.
 *
 * Ships as an informational Finding rather than wired into a limit-setting
 * screen (see the note at the top of this document for why). The suggested
 * number is high-rated-day average usage plus a 10% buffer, rounded to the
 * nearest 5 minutes.
 */
fun detectAllowanceSuggestion(
    usageRows: List<AppUsageRangeRow>,
    ratings: List<DayRatingEntity>,
): FindingEntity? {
    if (ratings.size < 7) return null
    val ratingByDate = ratings.associateBy { it.date }

    val byPackage = usageRows
        .filter { it.category in ALLOWANCE_ELIGIBLE_CATEGORIES }
        .groupBy { it.packageName }

    data class Candidate(
        val pkg: String, val appName: String,
        val highAvgMin: Double, val lowAvgMin: Double,
        val highN: Int, val lowN: Int, val suggestedMin: Int,
    )

    val candidates = byPackage.mapNotNull { (pkg, rows) ->
        val highDays = rows.filter { (ratingByDate[it.date]?.rating ?: -1) >= 7 }
        val lowDays  = rows.filter { (ratingByDate[it.date]?.rating ?: 99) <= 4 }
        if (highDays.size < 5 || lowDays.size < 3) return@mapNotNull null

        val highMin = highDays.map { it.foregroundMs }.average() / MS_PER_MIN
        val lowMin  = lowDays.map { it.foregroundMs }.average() / MS_PER_MIN
        if (highMin >= lowMin - 15.0) return@mapNotNull null

        val suggested = (round(highMin * 1.1 / 5.0) * 5.0).toInt().coerceAtLeast(5)
        Candidate(pkg, rows.first().appName, highMin, lowMin, highDays.size, lowDays.size, suggested)
    }

    // Prefer the candidate with the largest high-vs-low gap — the clearest signal
    val winner = candidates.maxByOrNull { it.lowAvgMin - it.highAvgMin } ?: return null

    val fingerprint = evidenceFingerprint(
        "ALLOWANCE_SUGGESTION", winner.pkg,
        sampleSize = winner.highN + winner.lowN,
        keyMetric  = winner.lowAvgMin - winner.highAvgMin, metricUnit = 5.0,
    )

    return newFinding(
        detectionType = "ALLOWANCE_SUGGESTION",
        headline      = "Based on your own data",
        body          = "On days you rate 7 or higher, your ${winner.appName} use averages " +
                         "${round(winner.highAvgMin).toInt()}m. On days you rate 4 or below, it " +
                         "averages ${round(winner.lowAvgMin).toInt()}m. Your data suggests a " +
                         "${winner.suggestedMin}m daily limit — close to what your best days already look like.",
        evidenceLine  = "Based on ${winner.highN} high-rated and ${winner.lowN} low-rated days",
        fingerprint   = fingerprint,
        evidenceJson  = """{"high_avg_min":${round(winner.highAvgMin).toInt()},"low_avg_min":${round(winner.lowAvgMin).toInt()},"suggested_min":${winner.suggestedMin}}""",
    ).copy(subjectPackage = winner.pkg, subjectAppName = winner.appName)
}
```

---

## Step 2 — Two small edits to `FindingCardView.kt` (from IMPL_2)

`ALLOWANCE_SUGGESTION` needs its own category label (not "What X is doing" —
it isn't describing a design tactic) and shouldn't show the
intentional/aware response buttons — "I chose this" doesn't fit a bare
number suggestion.

**Edit 1 — category label.** Replace `findingCategoryLabel`:

```kotlin
private fun findingCategoryLabel(finding: FindingEntity): String = when {
    finding.detectionType == "ALLOWANCE_SUGGESTION" -> "BASED ON YOUR DATA"
    finding.detectionType in MANIPULATION_TYPES && finding.subjectAppName != null ->
        "WHAT ${finding.subjectAppName.uppercase()} IS DOING"
    else -> "FINDING"
}
```

**Edit 2 — suppress response buttons for this type.** Add near
`MANIPULATION_TYPES`:

```kotlin
private val NO_RESPONSE_TYPES = setOf("ALLOWANCE_SUGGESTION")
```

Change the `showReply` computation:

```kotlin
val showReply = finding.state == "seen" && finding.detectionType !in NO_RESPONSE_TYPES
```

Substitution keeps the normal response buttons — "I chose this" /
"I didn't know" both read naturally for a pattern the user might or might not
have noticed about their own behaviour.

---

## Step 3 — Extend `FindingDetectionRunner.kt` (from IMPL_4)

No new constructor dependencies — `dailyAppUsageDao` and `dayRatingDao` are
already wired in from IMPL_4. Add two new private methods and two new calls
in `runAll()`.

### 3.1 — `runAll()` additions

```kotlin
suspend fun runAll() {
    // ... all nine existing calls from IMPL_3 and IMPL_4, unchanged ...

    // IMPL_5 — substitution and allowance suggestion
    runCatching { runSubstitution() }
        .onFailure { Log.e(TAG, "substitution failed: ${it.message}", it) }
    runCatching { runAllowanceSuggestion() }
        .onFailure { Log.e(TAG, "allowanceSuggestion failed: ${it.message}", it) }
}
```

### 3.2 — New private methods

```kotlin
private suspend fun runSubstitution() {
    val start = dateRangeStart(28)
    val end   = dateRangeEnd()
    val usageRows = dailyAppUsageDao.getForDateRange(start, end)
    if (usageRows.isEmpty()) return
    detectSubstitution(usageRows, java.time.LocalDate.now())?.let { findingRepository.submit(it) }
}

private suspend fun runAllowanceSuggestion() {
    val start = dateRangeStart(30)
    val end   = dateRangeEnd()
    val usageRows = dailyAppUsageDao.getForDateRange(start, end)
    if (usageRows.isEmpty()) return
    val ratings = dayRatingDao.getForDateRange(start, end)
    detectAllowanceSuggestion(usageRows, ratings)?.let { findingRepository.submit(it) }
}
```

### 3.3 — New import

```kotlin
import com.tbtechs.focusflow.analytics.detection.detectSubstitution
import com.tbtechs.focusflow.analytics.detection.detectAllowanceSuggestion
```

No `AppModule.kt` changes needed — every dependency this step uses was
already threaded through the runner's constructor in IMPL_4.

---

## What IMPL_5 delivers

- **Substitution** — surfaces when reduced use of one discretionary app is
  quietly absorbed by another, something that's invisible to any single-app
  screen-time total
- **Allowance Suggestion** — a concrete, personally-derived number
  ("~25m") grounded in the user's own rated days rather than a generic
  guideline, delivered honestly as a Finding rather than pretending
  integration with an unverified settings screen
- A small refactor (`computeWeeklyAverages`) that removes duplicated
  week-bucketing logic between Escalating Capture and Substitution — same
  behaviour, one source of truth

## Where the full build stands

| Document | Covers | Status |
|---|---|---|
| IMPL_1A | Tracker, daily_app_usage, app_sessions, MIGRATION_5_6 | Complete per tracker |
| IMPL_1B | 7 tables, repositories, notification, prune | Complete per tracker |
| IMPL_2 | DayRatingBar, FindingCardView, FindingsSection, ColdStartSheet | Complete; review fixes applied |
| IMPL_3 | Post-failure cascade, session sweet spot, estimation drift, day-of-week outlier | Implemented; source complete; hosted build blocked |
| IMPL_4 | Variable reward loop, infinite session design, morning hijack, escalating capture, streak lock-in | Implemented; source complete; hosted build blocked |
| IMPL_5 | Substitution, allowance suggestion | Implemented; focused tests added; GitHub Actions verification pending |

The nine detections from IMPL_3 and IMPL_4 plus both IMPL_5 findings are
implemented in source. Focused IMPL_5 test coverage is present; run the
Android checks through GitHub Actions to verify it.
Notification Conditioning remains separately deferred pending verification of
`TemptationLogManager.kt` and the DAO or table behind `blocking.byHour`.
