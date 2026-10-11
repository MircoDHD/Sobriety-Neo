package com.katiearose.sobriety.shared

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import kotlinx.datetime.periodUntil
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime

private const val MILLIS_PER_DAY = 24L * 60 * 60 * 1000

/**
 * The time that has passed between two moments, split into the units that are shown to the user
 * ("1 year 2 months 3 weeks 4 days 5 hours 6 minutes and 7 seconds").
 *
 * ## Why this class exists (the "+1 day" bug)
 * The old code computed the calendar part (years/months/days) with `java.time.Period` on the
 * *dates* only and the clock part (hours/minutes/seconds) with `Duration` on the full timestamps.
 * A `Period` counts how many midnights were crossed, not how many full days passed, so a relapse
 * at 22:00 showed "1 day 3 hours" at 01:00 the next morning, which is only 3 hours later.
 * The result was always exactly one day too much whenever the current time of day was
 * earlier than the time of day of the relapse.
 *
 * All values here are derived from one single calculation
 * ([Instant.periodUntil]), in which a "day" only counts once the *full* day has elapsed,
 * measured in the given [TimeZone]. That also makes the result correct across daylight saving
 * time changes: 22:00 on Saturday to 22:00 on Sunday is exactly one day, even if the clocks
 * were changed during the night.
 *
 * @property years full years
 * @property months full months, 0..11
 * @property weeks full weeks of the remaining days, 0..4
 * @property days remaining days after the weeks were taken out, 0..6
 * @property hours remaining hours, 0..23
 * @property minutes remaining minutes, 0..59
 * @property seconds remaining seconds, 0..59
 * @property totalDays complete 24 hour blocks between start and end (ignores months and years),
 *   meant for a "N days" display
 * @property daysIntoCycle days since the last full month, 0..31. Unlike [weeks]/[days] it is not
 *   split into weeks. Used for the "days" progress bar.
 * @property daysInCycle length in days of the month that is currently running (28..31). Together
 *   with [daysIntoCycle] this gives the progress of the day bar. [daysIntoCycle] is never larger
 *   than this value, but it can be equal (for example when the start date is the 31st and the
 *   month has only 28 days), which shows a full bar for a moment.
 */
data class ElapsedTime(
    val years: Int,
    val months: Int,
    val weeks: Int,
    val days: Int,
    val hours: Int,
    val minutes: Int,
    val seconds: Int,
    val totalDays: Long,
    val daysIntoCycle: Int,
    val daysInCycle: Int,
) {
    companion object {
        /** Used when nothing has elapsed yet. [daysInCycle] must never be 0 (it is a divisor). */
        private val ZERO = ElapsedTime(0, 0, 0, 0, 0, 0, 0, 0, 0, 30)

        /**
         * Calculates the elapsed time between two moments.
         *
         * @param startMillis start in epoch milliseconds (e.g. the time of the last relapse)
         * @param endMillis end in epoch milliseconds (e.g. "now", or the time of stopping)
         * @param timeZone zone used for the calendar part. Defaults to the device zone.
         *   Tests pass a fixed zone so that they behave the same on every machine.
         * @return the split-up duration. If [endMillis] is not after [startMillis] (e.g. a start
         *   in the future, or clocks that were set back) everything is zero rather than negative.
         */
        fun between(
            startMillis: Long,
            endMillis: Long,
            timeZone: TimeZone = TimeZone.currentSystemDefault(),
        ): ElapsedTime {
            if (endMillis <= startMillis) return ZERO

            val start = Instant.fromEpochMilliseconds(startMillis)
            val end = Instant.fromEpochMilliseconds(endMillis)

            // One consistent calculation for every unit: full months first, then full days,
            // then whatever is left over as hours/minutes/seconds.
            val period = start.periodUntil(end, timeZone)

            // Length of the month cycle we are currently in. A cycle starts at
            // "start + years + months" and ends one month later (so it is 28 to 31 days long).
            val cycleStart = start.plus(DatePeriod(years = period.years, months = period.months), timeZone)
            val cycleEnd = start.plus(DatePeriod(years = period.years, months = period.months + 1), timeZone)
            val daysInCycle = cycleStart.toLocalDateTime(timeZone).date
                .daysUntil(cycleEnd.toLocalDateTime(timeZone).date)

            var wholeDays = period.days
            var hours = period.hours.toInt()
            // On the day the clocks are set back, a day has 25 real hours, so the leftover time
            // can come out as "24 hours". Show that as a full day instead.
            if (hours >= 24) {
                wholeDays += hours / 24
                hours %= 24
            }

            // Never let a divisor become 0 (it would crash the progress bars).
            val cycleLength = daysInCycle.coerceAtLeast(1)

            return ElapsedTime(
                years = period.years,
                months = period.months,
                weeks = wholeDays / 7,
                days = wholeDays % 7,
                hours = hours,
                minutes = period.minutes.toInt(),
                seconds = period.seconds.toInt(),
                totalDays = (endMillis - startMillis) / MILLIS_PER_DAY,
                // At most the cycle length, see the note on [daysIntoCycle].
                daysIntoCycle = wholeDays.coerceAtMost(cycleLength),
                daysInCycle = cycleLength,
            )
        }
    }
}
