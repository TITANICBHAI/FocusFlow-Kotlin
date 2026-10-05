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

        val midnight = Calendar.getInstance(timeZone).apply {
            timeInMillis = afterMs
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        var next: Long? = null

        // Include yesterday so an overnight window's end can be found after
        // midnight, and eight future days so every weekday can be considered.
        for (dayOffset in -1..8) {
            val start = (midnight.clone() as Calendar).apply {
                add(Calendar.DAY_OF_YEAR, dayOffset)
            }
            if (start.get(Calendar.DAY_OF_WEEK) !in daysOfWeek) continue
            start.set(Calendar.HOUR_OF_DAY, startMinuteOfDay / 60)
            start.set(Calendar.MINUTE, startMinuteOfDay % 60)
            start.set(Calendar.SECOND, 0)
            start.set(Calendar.MILLISECOND, 0)

            val end = (start.clone() as Calendar).apply {
                if (endMinuteOfDay <= startMinuteOfDay) {
                    add(Calendar.DAY_OF_YEAR, 1)
                }
                set(Calendar.HOUR_OF_DAY, endMinuteOfDay / 60)
                set(Calendar.MINUTE, endMinuteOfDay % 60)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }

            listOf(start.timeInMillis, end.timeInMillis)
                .filter { it > afterMs }
                .forEach { boundary ->
                    if (next == null || boundary < next!!) next = boundary
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
            windows.asSequence()
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
                .forEach(::add)
        }
        return boundaries.minOrNull()
    }
}
