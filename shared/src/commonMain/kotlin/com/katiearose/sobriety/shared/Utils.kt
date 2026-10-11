package com.katiearose.sobriety.shared

import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.Instant

/** Whole seconds that have passed between this moment and now (negative if it lies in the future). */
fun Instant.secondsFromNow(): Long = Clock.System.now().epochSeconds - this.epochSeconds

/**
 * Rough length of a time unit in milliseconds.
 *
 * Months and years do not have a fixed length, so a month is counted as 30.4375 days here
 * (365.25 / 12). This is **only good for comparing and sorting** units with each other
 * (for example "2 weeks" < "1 month"). Never use it to calculate real dates - use
 * [Addiction.milestoneGoalMillis] or [ElapsedTime] for that, they use the calendar.
 */
fun DateTimeUnit.toMillis(): Long {
    return when (this) {
        is DateTimeUnit.TimeBased -> nanoseconds / 1_000_000
        is DateTimeUnit.DayBased -> days.toLong() * 24 * 60 * 60 * 1000
        is DateTimeUnit.MonthBased -> (months * 30.4375 * 24 * 60 * 60 * 1000).toLong()
    }
}

/**
 * Puts the specified value to the last key in this map.
 * Used to close the newest attempt: its key (start time) stays, the value (end time) is set.
 * Must not be called on an empty map.
 */
fun <K, V> LinkedHashMap<K, V>.putLast(value: V) {
    put(keys.last(), value)
}
