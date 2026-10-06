package com.tbtechs.focusflow.enforcement

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Calendar
import java.util.Random
import java.util.TimeZone

class VpnPolicyBoundaryDstTest {
    private val newYork = TimeZone.getTimeZone("America/New_York")

    private fun utc(text: String): Long = Instant.parse(text).toEpochMilli()

    private fun vpnWindow(days: List<Int>, start: Int, end: Int) =
        VpnScheduleWindow(listOf("pkg"), days, start, end, enabled = true, vpnEnabled = true)

    @Test
    fun windowStartInTheSpringForwardGapFiresWhenTheClockJumps() {
        // 2026-03-08 in New York: 02:00 EST -> 03:00 EDT. The wall clock never shows 02:30, so a
        // Sunday 02:30 window is first active at 03:00 EDT, which is 07:00Z.
        val sunday = Calendar.SUNDAY
        val now = utc("2026-03-08T05:00:00Z") // 00:00 EST
        val boundary = VpnPolicyBoundaryPolicy.nextBoundaryMs(
            activeStandalone = false,
            standaloneUntilMs = 0L,
            windows = listOf(vpnWindow(listOf(sunday), 2 * 60 + 30, 9 * 60)),
            nowMs = now,
            timeZone = newYork,
        )
        assertEquals(utc("2026-03-08T07:00:00Z"), boundary)
        assertTrue(
            GreyoutWindowMath.isActive(listOf(sunday), 2 * 60 + 30, 9 * 60, boundary!!, newYork),
        )
    }

    @Test
    fun overnightWindowEndInTheRepeatedHourUsesTheFirstOccurrence() {
        // 2026-11-01 in New York: 02:00 EDT -> 01:00 EST, so 01:30 happens twice. The window
        // Saturday 20:00 -> Sunday 01:30 first ends at 01:30 EDT (05:30Z), not at 01:30 EST.
        val saturday = Calendar.SATURDAY
        val window = vpnWindow(listOf(saturday), 20 * 60, 1 * 60 + 30)
        val boundary = VpnPolicyBoundaryPolicy.nextBoundaryMs(
            activeStandalone = false,
            standaloneUntilMs = 0L,
            windows = listOf(window),
            nowMs = utc("2026-11-01T01:00:00Z"), // Saturday 21:00 EDT
            timeZone = newYork,
        )
        assertEquals(utc("2026-11-01T05:30:00Z"), boundary)
    }

    @Test
    fun clockFallingBackReactivatesTheWindowAtTheTransitionInstant() {
        // Right after 01:30 EDT the window is off; at the 06:00Z switch the wall clock reads
        // 01:00 EST and the same overnight window is active again, so recompute exactly then.
        val saturday = Calendar.SATURDAY
        val window = vpnWindow(listOf(saturday), 20 * 60, 1 * 60 + 30)
        val boundary = VpnPolicyBoundaryPolicy.nextBoundaryMs(
            activeStandalone = false,
            standaloneUntilMs = 0L,
            windows = listOf(window),
            nowMs = utc("2026-11-01T05:45:00Z"),
            timeZone = newYork,
        )
        assertEquals(utc("2026-11-01T06:00:00Z"), boundary)
        assertTrue(
            GreyoutWindowMath.isActive(listOf(saturday), 20 * 60, 1 * 60 + 30, boundary!!, newYork),
        )
    }

    @Test
    fun boundaryIsNeverLaterThanTheFirstRealStateChangeAroundDstSwitches() {
        val random = Random(2026)
        val zones = listOf("America/New_York", "Australia/Sydney", "Australia/Lord_Howe", "Europe/London")
        for (zoneId in zones) {
            val zone = TimeZone.getTimeZone(zoneId)
            val rules = ZoneId.of(zoneId).rules
            val switches = generateSequence(rules.nextTransition(Instant.parse("2026-01-01T00:00:00Z"))) {
                rules.nextTransition(it.instant)
            }.takeWhile { it.instant.isBefore(Instant.parse("2027-01-01T00:00:00Z")) }.toList()
            assertTrue("$zoneId should have DST switches in 2026", switches.isNotEmpty())

            repeat(400) {
                val switchAt = switches[random.nextInt(switches.size)].instant.toEpochMilli()
                val now = switchAt - 5L * 86_400_000L + random.nextInt(10 * 24 * 60) * 60_000L
                val days = (1..7).filter { random.nextInt(3) == 0 }.ifEmpty { listOf(1 + random.nextInt(7)) }
                val start = random.nextInt(1440)
                var end = random.nextInt(1440)
                if (end == start) end = (start + 30) % 1440

                val boundary = VpnPolicyBoundaryPolicy.nextBoundaryMs(
                    activeStandalone = false,
                    standaloneUntilMs = 0L,
                    windows = listOf(vpnWindow(days, start, end)),
                    nowMs = now,
                    timeZone = zone,
                )

                val currentlyActive = GreyoutWindowMath.isActive(days, start, end, now, zone)
                var probe = (now / 60_000L + 1) * 60_000L
                var firstChange: Long? = null
                while (probe <= now + 9L * 86_400_000L) {
                    if (GreyoutWindowMath.isActive(days, start, end, probe, zone) != currentlyActive) {
                        firstChange = probe
                        break
                    }
                    probe += 60_000L
                }
                if (firstChange != null) {
                    assertTrue(
                        "$zoneId days=$days start=$start end=$end now=$now: expected a boundary " +
                            "no later than $firstChange but got $boundary",
                        boundary != null && boundary <= firstChange,
                    )
                }
            }
        }
    }
}
