package com.tbtechs.focusflow.analytics.detection

import com.tbtechs.focusflow.data.local.dao.AppUsageRangeRow
import com.tbtechs.focusflow.data.local.dao.FirstSessionRow
import com.tbtechs.focusflow.data.local.dao.SessionStatRow
import com.tbtechs.focusflow.data.local.entity.AppSessionEntity
import com.tbtechs.focusflow.data.local.entity.DayRatingEntity
import com.tbtechs.focusflow.data.local.entity.FindingEntity
import kotlin.math.round
import kotlin.math.sqrt

private const val MS_PER_MIN = 60_000.0

private data class DurationStats(
    val mean: Double,
    val stdDev: Double,
    val max: Double,
    val count: Int,
)

private fun durationStats(sessions: List<AppSessionEntity>): DurationStats? {
    if (sessions.isEmpty()) return null
    val values = sessions.map { it.durationMs.toDouble() }
    val mean = values.average()
    val variance = values.sumOf { value -> (value - mean) * (value - mean) } / values.size
    return DurationStats(
        mean = mean,
        stdDev = sqrt(variance),
        max = values.maxOrNull() ?: return null,
        count = values.size,
    )
}

private val STREAK_ELIGIBLE_CATEGORIES =
    setOf("social", "entertainment", "communication")

/**
 * Finds a high-frequency app with short, highly variable sessions.
 *
 * The caller supplies raw sessions only for packages that passed the cheap
 * daily aggregate filter.
 */
fun detectVariableRewardLoop(
    dailyStats: List<SessionStatRow>,
    rawSessionsByPackage: Map<String, List<AppSessionEntity>>,
    appNames: Map<String, String>,
): FindingEntity? {
    data class Candidate(
        val packageName: String,
        val cv: Double,
        val meanMinutes: Double,
        val sampleDays: Int,
        val raw: List<AppSessionEntity>,
    )

    val candidates = dailyStats
        .groupBy { it.packageName }
        .mapNotNull { (packageName, days) ->
            val totalSessions = days.sumOf { it.sessionCount }
            val sampleDays = days.size
            if (sampleDays < 10 || totalSessions == 0) return@mapNotNull null
            if (totalSessions.toDouble() / sampleDays < 5.0) return@mapNotNull null

            val weightedMeanMs =
                days.sumOf { it.avgDurationMs * it.sessionCount } / totalSessions
            if (weightedMeanMs > 4 * MS_PER_MIN) return@mapNotNull null

            val raw = rawSessionsByPackage[packageName] ?: return@mapNotNull null
            val stats = durationStats(raw) ?: return@mapNotNull null
            if (stats.mean == 0.0) return@mapNotNull null
            val cv = stats.stdDev / stats.mean
            if (cv < 0.9) return@mapNotNull null

            Candidate(packageName, cv, stats.mean / MS_PER_MIN, sampleDays, raw)
        }

    val winner = candidates.maxByOrNull { it.cv } ?: return null
    val appName = appNames[winner.packageName] ?: winner.packageName
    val minMinutes = winner.raw.minOf { it.durationMs } / MS_PER_MIN
    val maxMinutes = winner.raw.maxOf { it.durationMs } / MS_PER_MIN
    val averageLaunches =
        round((winner.raw.size.toDouble() / winner.sampleDays) * 10) / 10

    val fingerprint = evidenceFingerprint(
        detectionType = "VARIABLE_REWARD_LOOP",
        subjectPackage = winner.packageName,
        sampleSize = winner.raw.size,
        keyMetric = winner.cv,
        metricUnit = 0.1,
    )

    return newFinding(
        detectionType = "VARIABLE_REWARD_LOOP",
        headline = "Variable reward loop",
        body = "You open $appName about $averageLaunches times a day. Average session: " +
            "${round(winner.meanMinutes * 10) / 10}m — but they range from " +
            "${round(minMinutes * 10) / 10}m to ${round(maxMinutes * 10) / 10}m. Sessions with " +
            "no consistent length are the signature of a feed with no designed stopping point.",
        evidenceLine = "Observed across ${winner.sampleDays} days, " +
            "${winner.raw.size} sessions",
        fingerprint = fingerprint,
        evidenceJson = """{"cv":${round(winner.cv * 100) / 100},"avg_launches_per_day":$averageLaunches,"sample_days":${winner.sampleDays}}""",
    ).copy(
        subjectPackage = winner.packageName,
        subjectAppName = appName,
    )
}

/**
 * Finds a non-utility app whose session duration has both a large spread and
 * an unusually long maximum session.
 */
fun detectInfiniteSessionDesign(
    dailyStats: List<SessionStatRow>,
    rawSessionsByPackage: Map<String, List<AppSessionEntity>>,
    appNames: Map<String, String>,
    categories: Map<String, String?>,
): FindingEntity? {
    data class Candidate(
        val packageName: String,
        val stdDevMinutes: Double,
        val meanMinutes: Double,
        val maxMinutes: Double,
        val sampleDays: Int,
        val sessionCount: Int,
    )

    val candidates = dailyStats
        .groupBy { it.packageName }
        .mapNotNull { (packageName, days) ->
            if (categories[packageName] == "utility") return@mapNotNull null
            val sampleDays = days.size
            if (sampleDays < 14) return@mapNotNull null

            val totalSessions = days.sumOf { it.sessionCount }
            if (totalSessions == 0) return@mapNotNull null
            val weightedMeanMs =
                days.sumOf { it.avgDurationMs * it.sessionCount } / totalSessions
            if (weightedMeanMs <= 5 * MS_PER_MIN) return@mapNotNull null

            val raw = rawSessionsByPackage[packageName] ?: return@mapNotNull null
            val stats = durationStats(raw) ?: return@mapNotNull null
            val stdDevMinutes = stats.stdDev / MS_PER_MIN
            val meanMinutes = stats.mean / MS_PER_MIN
            val maxMinutes = stats.max / MS_PER_MIN
            if (stdDevMinutes <= 20.0 || maxMinutes <= 3 * meanMinutes) {
                return@mapNotNull null
            }

            Candidate(
                packageName,
                stdDevMinutes,
                meanMinutes,
                maxMinutes,
                sampleDays,
                stats.count,
            )
        }

    val winner = candidates.maxByOrNull { it.stdDevMinutes } ?: return null
    val appName = appNames[winner.packageName] ?: winner.packageName
    val fingerprint = evidenceFingerprint(
        detectionType = "INFINITE_SESSION_DESIGN",
        subjectPackage = winner.packageName,
        sampleSize = winner.sessionCount,
        keyMetric = winner.stdDevMinutes,
        metricUnit = 5.0,
    )

    return newFinding(
        detectionType = "INFINITE_SESSION_DESIGN",
        headline = "Infinite session design",
        body = "Your $appName sessions have no typical length. They've run as short as a " +
            "few minutes and as long as ${round(winner.maxMinutes).toInt()}m. That variance " +
            "isn't your preference — it's the absence of a designed stopping point. You " +
            "supply the brake each time.",
        evidenceLine = "Observed across ${winner.sampleDays} days, " +
            "${winner.sessionCount} sessions",
        fingerprint = fingerprint,
        evidenceJson = """{"stddev_min":${round(winner.stdDevMinutes)},"mean_min":${round(winner.meanMinutes)},"max_min":${round(winner.maxMinutes)}}""",
    ).copy(
        subjectPackage = winner.packageName,
        subjectAppName = appName,
    )
}

data class MorningHijackResult(
    val finding: FindingEntity,
    val qualifyingDates: List<String>,
    val packageName: String,
)

/**
 * Finds a social or entertainment app that captures the first session on at
 * least 65% of represented mornings.
 */
fun detectMorningHijack(
    firstSessions: List<FirstSessionRow>,
    categories: Map<String, String?>,
): MorningHijackResult? {
    if (firstSessions.size < 7) return null

    data class Candidate(
        val packageName: String,
        val appName: String,
        val fraction: Double,
        val dates: List<String>,
    )

    val totalDays = firstSessions.size
    val candidates = firstSessions
        .groupBy { it.packageName }
        .mapNotNull { (packageName, rows) ->
            val category = categories[packageName]
            if (category != "social" && category != "entertainment") {
                return@mapNotNull null
            }
            val fraction = rows.size.toDouble() / totalDays
            if (fraction < 0.65) return@mapNotNull null
            Candidate(
                packageName = packageName,
                appName = rows.first().appName,
                fraction = fraction,
                dates = rows.map { it.localDate },
            )
        }

    val winner = candidates.maxByOrNull { it.fraction } ?: return null
    val percentage = round(winner.fraction * 100).toInt()
    val fingerprint = evidenceFingerprint(
        detectionType = "MORNING_HIJACK",
        subjectPackage = winner.packageName,
        sampleSize = totalDays,
        keyMetric = winner.fraction,
        metricUnit = 0.05,
    )

    val finding = newFinding(
        detectionType = "MORNING_HIJACK",
        headline = "Morning hijack",
        body = "On $percentage% of recent mornings, ${winner.appName} was the first thing " +
            "you opened — before starting anything else. Apps compete for this window " +
            "specifically. Whoever captures your attention first shapes the mode you " +
            "work in for the rest of the day.",
        evidenceLine = "Observed on ${winner.dates.size} of $totalDays days",
        fingerprint = fingerprint,
        evidenceJson = """{"fraction_pct":$percentage,"sample_days":$totalDays}""",
    ).copy(
        subjectPackage = winner.packageName,
        subjectAppName = winner.appName,
    )

    return MorningHijackResult(
        finding = finding,
        qualifyingDates = winner.dates,
        packageName = winner.packageName,
    )
}

/**
 * Finds a package whose average foreground time rises every week over a
 * complete 28-day window.
 */
fun detectEscalatingCapture(
    usageRows: List<AppUsageRangeRow>,
    today: java.time.LocalDate,
): FindingEntity? {
    val weekOf = { date: java.time.LocalDate ->
        val daysAgo = java.time.temporal.ChronoUnit.DAYS.between(date, today)
        when {
            daysAgo < 7 -> 4
            daysAgo < 14 -> 3
            daysAgo < 21 -> 2
            daysAgo < 28 -> 1
            else -> 0
        }
    }

    data class Candidate(
        val packageName: String,
        val appName: String,
        val growth: Double,
        val week1Minutes: Double,
        val week4Minutes: Double,
    )

    val candidates = usageRows
        .groupBy { it.packageName }
        .mapNotNull { (packageName, rows) ->
            val byWeek = rows
                .mapNotNull { row ->
                    val date = runCatching {
                        java.time.LocalDate.parse(row.date)
                    }.getOrNull() ?: return@mapNotNull null
                    val week = weekOf(date)
                    if (week == 0) null else week to row.foregroundMs
                }
                .groupBy({ it.first }, { it.second })

            if ((1..4).any { (byWeek[it]?.size ?: 0) < 4 }) {
                return@mapNotNull null
            }

            val weekAverage = (1..4).associateWith { week ->
                byWeek.getValue(week).average()
            }
            val week1 = weekAverage.getValue(1)
            val week2 = weekAverage.getValue(2)
            val week3 = weekAverage.getValue(3)
            val week4 = weekAverage.getValue(4)
            if (!(week2 > week1 && week3 > week2 && week4 > week3)) {
                return@mapNotNull null
            }
            if (week1 <= 5 * MS_PER_MIN) return@mapNotNull null

            val growth = (week4 - week1) / week1
            if (growth < 0.40) return@mapNotNull null

            Candidate(
                packageName = packageName,
                appName = rows.first().appName,
                growth = growth,
                week1Minutes = week1 / MS_PER_MIN,
                week4Minutes = week4 / MS_PER_MIN,
            )
        }

    val winner = candidates.maxByOrNull { it.growth } ?: return null
    val growthPercentage = round(winner.growth * 100).toInt()
    val fingerprint = evidenceFingerprint(
        detectionType = "ESCALATING_CAPTURE",
        subjectPackage = winner.packageName,
        sampleSize = 28,
        keyMetric = winner.growth,
        metricUnit = 0.1,
    )

    return newFinding(
        detectionType = "ESCALATING_CAPTURE",
        headline = "Escalating capture",
        body = "${winner.appName} was taking ${round(winner.week1Minutes).toInt()}m of your day " +
            "4 weeks ago. It's at ${round(winner.week4Minutes).toInt()}m now — up " +
            "$growthPercentage%. Usage that grows every week without a deliberate decision " +
            "is the algorithm improving its model of you, not you choosing to spend more " +
            "time there.",
        evidenceLine = "Growth measured over 4 weeks",
        fingerprint = fingerprint,
        evidenceJson = """{"growth_pct":$growthPercentage,"week1_min":${round(winner.week1Minutes).toInt()},"week4_min":${round(winner.week4Minutes).toInt()}}""",
    ).copy(
        subjectPackage = winner.packageName,
        subjectAppName = winner.appName,
    )
}

/**
 * Finds an eligible app opened on nearly every day, including days the user
 * rated poorly.
 */
fun detectStreakLockIn(
    usageRows: List<AppUsageRangeRow>,
    ratings: List<DayRatingEntity>,
    windowDays: Int,
): FindingEntity? {
    if (ratings.size < 3 || windowDays <= 0) return null
    val ratingByDate = ratings.associateBy { it.date }

    data class Candidate(
        val packageName: String,
        val appName: String,
        val consistency: Double,
        val openDates: Set<String>,
        val lowRatedDates: List<String>,
        val averageLowRating: Double,
    )

    val candidates = usageRows
        .filter {
            it.launchCount > 0 &&
                it.category in STREAK_ELIGIBLE_CATEGORIES
        }
        .groupBy { it.packageName }
        .mapNotNull { (packageName, rows) ->
            val openDates = rows.map { it.date }.toSet()
            val consistency = openDates.size.toDouble() / windowDays
            if (consistency < 0.90) return@mapNotNull null

            val lowRatedDates = openDates.filter {
                (ratingByDate[it]?.rating ?: 99) <= 4
            }
            if (lowRatedDates.size < 3) return@mapNotNull null

            val averageLowRating = lowRatedDates
                .mapNotNull { ratingByDate[it]?.rating }
                .average()
            Candidate(
                packageName = packageName,
                appName = rows.first().appName,
                consistency = consistency,
                openDates = openDates,
                lowRatedDates = lowRatedDates,
                averageLowRating = averageLowRating,
            )
        }

    val winner = candidates.maxByOrNull { it.consistency } ?: return null
    val fingerprint = evidenceFingerprint(
        detectionType = "STREAK_LOCK_IN",
        subjectPackage = winner.packageName,
        sampleSize = winner.openDates.size,
        keyMetric = winner.consistency,
        metricUnit = 0.05,
    )

    return newFinding(
        detectionType = "STREAK_LOCK_IN",
        headline = "Streak lock-in",
        body = "You've opened ${winner.appName} on ${winner.openDates.size} of the last " +
            "$windowDays days — including ${winner.lowRatedDates.size} days you rated " +
            "${round(winner.averageLowRating * 10) / 10}/10 or lower. Streak mechanics " +
            "use loss aversion — the fear of losing what you've built — to require daily " +
            "engagement regardless of whether the day is going well.",
        evidenceLine = "Observed across ${winner.openDates.size} days, " +
            "${winner.lowRatedDates.size} low-rated",
        fingerprint = fingerprint,
        evidenceJson = """{"consistency_pct":${round(winner.consistency * 100).toInt()},"low_rated_days":${winner.lowRatedDates.size},"avg_low_rating":${round(winner.averageLowRating * 10) / 10}}""",
    ).copy(
        subjectPackage = winner.packageName,
        subjectAppName = winner.appName,
    )
}