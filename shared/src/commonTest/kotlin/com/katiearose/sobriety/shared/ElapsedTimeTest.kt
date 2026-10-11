package com.katiearose.sobriety.shared

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tests for [ElapsedTime]. Every test uses a fixed time zone (Berlin) so the result does not
 * depend on the settings of the machine that runs the tests.
 *
 * Most important are the "+1 day" regression tests: the released app showed one day too much
 * whenever the current time of day was earlier than the time of day of the relapse.
 */
class ElapsedTimeTest {

    private val berlin = TimeZone.of("Europe/Berlin")

    /** Epoch milliseconds of a local date/time in Berlin. */
    private fun at(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long =
        LocalDateTime(year, month, day, hour, minute).toInstant(berlin).toEpochMilliseconds()

    private fun between(start: Long, end: Long) = ElapsedTime.between(start, end, berlin)

    // ---- Regression tests for the "+1 day" bug -------------------------------------------

    @Test
    fun threeHoursAcrossMidnightIsNotOneDay() {
        // Relapse at 22:00, three hours later it is 01:00 on the next calendar day.
        val result = between(at(2026, 10, 5, 22, 0), at(2026, 10, 6, 1, 0))
        assertEquals(0, result.days, "no full day has passed")
        assertEquals(0, result.weeks)
        assertEquals(3, result.hours)
        assertEquals(0L, result.totalDays)
    }

    @Test
    fun exactlyOneDayAfterRelapse() {
        val result = between(at(2026, 10, 5, 22, 0), at(2026, 10, 6, 22, 0))
        assertEquals(1, result.days)
        assertEquals(0, result.hours)
        assertEquals(1L, result.totalDays)
    }

    @Test
    fun twentyThreeHoursIsStillZeroDays() {
        val result = between(at(2026, 10, 5, 22, 0), at(2026, 10, 6, 21, 0))
        assertEquals(0, result.days)
        assertEquals(23, result.hours)
    }

    @Test
    fun almostTwoDays() {
        val result = between(at(2026, 10, 5, 22, 0), at(2026, 10, 7, 21, 0))
        assertEquals(1, result.days)
        assertEquals(23, result.hours)
        assertEquals(1L, result.totalDays)
    }

    @Test
    fun monthsAndWeeksWhenTimeOfDayIsEarlier() {
        // 2026-08-20 08:00 -> 2026-10-11 07:00 is 1 month, 2 weeks, 6 days and 23 hours.
        val result = between(at(2026, 8, 20, 8, 0), at(2026, 10, 11, 7, 0))
        assertEquals(1, result.months)
        assertEquals(2, result.weeks)
        assertEquals(6, result.days)
        assertEquals(23, result.hours)
    }

    @Test
    fun monthsAndWeeksWhenTimeOfDayIsLater() {
        // One hour later the 3rd week is complete.
        val result = between(at(2026, 8, 20, 8, 0), at(2026, 10, 11, 9, 0))
        assertEquals(1, result.months)
        assertEquals(3, result.weeks)
        assertEquals(0, result.days)
        assertEquals(1, result.hours)
    }

    // ---- Daylight saving time ---------------------------------------------------------------

    @Test
    fun clocksGoBackStillOneDayAtSameWallClockTime() {
        // Clocks go back on 2026-10-25, so this day has 25 real hours.
        val result = between(at(2026, 10, 24, 22, 0), at(2026, 10, 25, 22, 0))
        assertEquals(1, result.days)
        assertEquals(0, result.hours)
    }

    @Test
    fun clocksGoBackNeverShowsTwentyFourHours() {
        // 24 real hours after 22:00 on the 24th it is only 21:00 on the 25th (clocks went back).
        // Depending on how the repeated hour is counted this is "23 hours" or "1 day", but it
        // must never be shown as "24 hours".
        val start = at(2026, 10, 24, 22, 0)
        val result = between(start, start + 24L * 60 * 60 * 1000)
        assertTrue(result.hours < 24, "hours must stay below 24 but were ${result.hours}")
        assertTrue(result.days <= 1)
    }

    // ---- Edge cases ---------------------------------------------------------------------------

    @Test
    fun zeroAndNegativeRangesGiveZero() {
        val t = at(2026, 10, 5, 12, 0)
        val same = between(t, t)
        assertEquals(0, same.seconds)
        assertEquals(0L, same.totalDays)
        val reversed = between(t, t - 5000)
        assertEquals(0, reversed.seconds)
        assertTrue(reversed.daysInCycle > 0, "daysInCycle is used as a divisor and must not be 0")
    }

    @Test
    fun secondsAreSplitCorrectly() {
        val start = at(2026, 10, 5, 12, 0)
        val result = between(start, start + (1L * 3600 + 2 * 60 + 3) * 1000)
        assertEquals(1, result.hours)
        assertEquals(2, result.minutes)
        assertEquals(3, result.seconds)
    }

    @Test
    fun dayProgressNeverExceedsOneHundredPercent() {
        // Starting on 10 February: the month cycle runs until 10 March, which is 28 days.
        val result = between(at(2026, 2, 10, 12, 0), at(2026, 2, 20, 12, 0))
        assertEquals(10, result.daysIntoCycle)
        assertEquals(28, result.daysInCycle)
        // Start on the 31st (the hardest case because shorter months clamp the date) and check
        // the invariant for more than a year, day by day. The old code could go above 100%.
        val start = at(2026, 1, 31, 23, 30)
        for (d in 0 until 400) {
            val r = between(start, start + d * 24L * 3600 * 1000 + 12345)
            assertTrue(r.daysIntoCycle <= r.daysInCycle, "day $d: ${r.daysIntoCycle} of ${r.daysInCycle}")
            assertTrue(r.daysInCycle in 28..31, "day $d: cycle length ${r.daysInCycle}")
        }
    }
}
