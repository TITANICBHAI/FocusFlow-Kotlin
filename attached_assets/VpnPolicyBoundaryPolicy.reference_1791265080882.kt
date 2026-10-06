package com.tbtechs.focusflow.enforcement

import java.util.Calendar
import java.util.TimeZone

internal data class VpnScheduleWindow(
    val packages: List<String>,
    val daysOfWeek: List<Int>,
    val startMinuteOfDay: Int,
    val endMinuteOfDay: Int,
    val enabled: Boolean = true,
    val vpnEnabled: Boolean = false,
)

/**
 * Shared local-time interpretation for accessibility enforcement and VPN
 * schedule boundaries. Weekdays use Calendar's 1=Sunday ... 7=Saturday values.
 */
internal object GreyoutWindowMath {
    fun isActive(
        daysOfWeek: List<Int>,
        startMinuteOfDay: Int,
        endMinuteOfDay: Int,
        atMs: Long,
        timeZone: TimeZone = TimeZone.getDefault(),
    ): Boolean {
        if (!hasValidWindow(daysOfWeek, startMinuteOfDay, endMinuteOfDay)) return false

        val now = Calendar.getInstance(timeZone).apply { timeInMillis = atMs }
        val currentDay = now.get(Calendar.DAY_OF_WEEK)
        val currentMinute = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
        val overnight = startMinuteOfDay > endMinuteOfDay
        val dayForWindow = if (overnight && currentMinute < endMinuteOfDay) {
            if (currentDay == Calendar.SUNDAY) Calendar.SATURDAY else currentDay - 1
        } else {
            currentDay
        }
        if (dayForWindow !in daysOfWeek) return false

        return if (overnight) {
            currentMinute >= startMinuteOfDay || currentMinute < endMinuteOfDay
        } else {
            currentMinute in startMinuteOfDay until endMinuteOfDay
        }
    }

    fun nextBoundaryAfter(
        daysOfWeek: List<Int>,
        startMinuteOfDay: Int,
        endMinuteOfDay: Int,
        afterMs: Long,
        timeZone: TimeZone = TimeZone.getDefault(),
    ): Long? {
        if (!hasValidWindow(daysOfWeek, startMinuteOfDay, endMinuteOfDay) ||
            startMinuteOfDay == endMinuteOfDay
        ) {
            return null
        }

        // java.time resolves a repeated local time to its FIRST occurrence (GregorianCalendar picks
        // the second), and a skipped local time forward. DST switches themselves are separate
        // boundaries (see VpnPolicyBoundaryPolicy.nextZoneTransitionMs).
        val zone = timeZone.toZoneId()
        val today = java.time.Instant.ofEpochMilli(afterMs).atZone(zone).toLocalDate()
        var next: Long? = null
        // Include yesterday so an overnight window's end can be found after midnight, and eight
        // future days so every weekday can be considered.
        for (dayOffset in -1L..8L) {
            val date = today.plusDays(dayOffset)
            // Calendar.SUNDAY == 1 ... SATURDAY == 7; java.time MONDAY == 1 ... SUNDAY == 7
            val calendarDay = date.dayOfWeek.value % 7 + 1
            if (calendarDay !in daysOfWeek) continue
            val startLocal = date.atTime(startMinuteOfDay / 60, startMinuteOfDay % 60)
            val endDate = if (endMinuteOfDay <= startMinuteOfDay) date.plusDays(1) else date
            val endLocal = endDate.atTime(endMinuteOfDay / 60, endMinuteOfDay % 60)
            for (local in listOf(startLocal, endLocal)) {
                val instant = java.time.ZonedDateTime.ofLocal(local, zone, null).toInstant().toEpochMilli()
                if (instant > afterMs && (next == null || instant < next!!)) next = instant
            }
        }
        return next
    }

    private fun hasValidWindow(
        daysOfWeek: List<Int>,
        startMinuteOfDay: Int,
        endMinuteOfDay: Int,
    ): Boolean =
        daysOfWeek.any { it in Calendar.SUNDAY..Calendar.SATURDAY } &&
            startMinuteOfDay in 0 until 24 * 60 &&
            endMinuteOfDay in 0 until 24 * 60
}

internal object VpnPolicyBoundaryPolicy {
    fun nextZoneTransitionMs(timeZone: TimeZone, afterMs: Long): Long? =
        timeZone.toZoneId().rules
            .nextTransition(java.time.Instant.ofEpochMilli(afterMs))
            ?.instant?.toEpochMilli()

    fun isStandaloneActive(active: Boolean, untilMs: Long, nowMs: Long): Boolean =
        active && (untilMs <= 0L || nowMs < untilMs)

    fun standaloneTargets(
        packages: List<String>,
        active: Boolean,
        untilMs: Long,
        nowMs: Long,
    ): List<String> =
        if (isStandaloneActive(active, untilMs, nowMs)) packages.filter(String::isNotBlank).distinct()
        else emptyList()

    fun scheduleTargets(
        windows: List<VpnScheduleWindow>,
        nowMs: Long,
        timeZone: TimeZone = TimeZone.getDefault(),
    ): List<String> =
        windows.asSequence()
            .filter { it.enabled && it.vpnEnabled && it.packages.isNotEmpty() }
            .filter {
                GreyoutWindowMath.isActive(
                    daysOfWeek = it.daysOfWeek,
                    startMinuteOfDay = it.startMinuteOfDay,
                    endMinuteOfDay = it.endMinuteOfDay,
                    atMs = nowMs,
                    timeZone = timeZone,
                )
            }
            .flatMap { it.packages.asSequence() }
            .filter(String::isNotBlank)
            .distinct()
            .sorted()
            .toList()

    fun nextBoundaryMs(
        activeStandalone: Boolean,
        standaloneUntilMs: Long,
        windows: List<VpnScheduleWindow>,
        nowMs: Long,
        timeZone: TimeZone = TimeZone.getDefault(),
    ): Long? {
        val boundaries = buildList {
            if (isStandaloneActive(activeStandalone, standaloneUntilMs, nowMs) &&
                standaloneUntilMs > nowMs
            ) {
                add(standaloneUntilMs)
            }
            val scheduleBoundaries = windows.asSequence()
                .filter { it.enabled && it.vpnEnabled && it.packages.isNotEmpty() }
                .mapNotNull {
                    GreyoutWindowMath.nextBoundaryAfter(
                        daysOfWeek = it.daysOfWeek,
                        startMinuteOfDay = it.startMinuteOfDay,
                        endMinuteOfDay = it.endMinuteOfDay,
                        afterMs = nowMs,
                        timeZone = timeZone,
                    )
                }
                .toList()
            addAll(scheduleBoundaries)
            // Wall-clock windows change meaning when the UTC offset changes, and Android sends no
            // broadcast for an automatic DST switch. Wake at the switch so the policy is recomputed.
            if (scheduleBoundaries.isNotEmpty()) {
                nextZoneTransitionMs(timeZone, nowMs)?.let(::add)
            }
        }
        return boundaries.minOrNull()
    }
}
