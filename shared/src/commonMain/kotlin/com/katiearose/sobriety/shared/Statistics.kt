package com.katiearose.sobriety.shared

import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/*
 * Statistics of Sobriety Neo (shown on the Statistics screen).
 *
 * Terms:
 *  - attempt: one entry of Addiction.history, from a start to the next relapse (or until now)
 *  - relapse: the start of every attempt except the first one
 */

/** Key for one calendar month, e.g. 2026-10. */
data class YearMonth(val year: Int, val month: Int) : Comparable<YearMonth> {
    override fun compareTo(other: YearMonth): Int =
        if (year != other.year) year.compareTo(other.year) else month.compareTo(other.month)

    /** The month before this one. */
    fun previous(): YearMonth = if (month == 1) YearMonth(year - 1, 12) else YearMonth(year, month - 1)
}

/**
 * Statistics of one addiction.
 *
 * All durations are in milliseconds.
 */
data class AddictionStatistics(
    /** Length of every attempt in order; the running one is counted until now. */
    val attemptDurations: List<Long>,
    /** The running attempt (0 if stopped or not started yet). */
    val currentStreak: Long,
    val longestStreak: Long,
    /** Average length of the finished attempts (0 if there are none). */
    val averageFinished: Long,
    /** Number of logged relapses. */
    val relapseCount: Int,
    /** Relapses in the last 30 days. */
    val relapsesLast30Days: Int,
    /** Sum of all attempts. */
    val totalSober: Long,
    /** Epoch milliseconds of every relapse. */
    val relapseTimes: List<Long>
)

private const val DAY_MILLIS = 24L * 60 * 60 * 1000

/** Calculates the [AddictionStatistics] of this addiction at [nowMillis]. */
fun Addiction.statistics(nowMillis: Long): AddictionStatistics {
    val entries = history.entries.toList()
    val durations = entries.map { (start, end) ->
        val realEnd = if (end == 0L) nowMillis else end
        (realEnd - start).coerceAtLeast(0)
    }
    val finished = entries.indices.filter { entries[it].value != 0L }.map { durations[it] }
    // Every attempt after the first one started with a relapse.
    val relapseTimes = entries.drop(1).map { it.key }.filter { it <= nowMillis }
    return AddictionStatistics(
        attemptDurations = durations,
        currentStreak = if (status == Addiction.Status.Ongoing) durations.lastOrNull() ?: 0 else 0,
        longestStreak = durations.maxOrNull() ?: 0,
        averageFinished = if (finished.isEmpty()) 0 else finished.sum() / finished.size,
        relapseCount = relapseTimes.size,
        relapsesLast30Days = relapseTimes.count { it > nowMillis - 30 * DAY_MILLIS },
        totalSober = durations.sum(),
        relapseTimes = relapseTimes
    )
}

/**
 * Number of relapses per calendar month over the last [months] months (oldest first), for all
 * given addictions together.
 */
fun relapsesPerMonth(
    addictions: List<Addiction>,
    nowMillis: Long,
    months: Int,
    timeZone: TimeZone
): List<Pair<YearMonth, Int>> {
    val now = Instant.fromEpochMilliseconds(nowMillis).toLocalDateTime(timeZone)
    var key = YearMonth(now.year, now.monthNumber)
    val keys = ArrayList<YearMonth>()
    repeat(months) {
        keys.add(0, key)
        key = key.previous()
    }
    val counts = keys.associateWith { 0 }.toMutableMap()
    for (addiction in addictions) {
        for (time in addiction.statistics(nowMillis).relapseTimes) {
            val date = Instant.fromEpochMilliseconds(time).toLocalDateTime(timeZone)
            val k = YearMonth(date.year, date.monthNumber)
            if (k in counts) counts[k] = counts.getValue(k) + 1
        }
    }
    return keys.map { it to counts.getValue(it) }
}

/**
 * Display options of the short durations on the Statistics screen: at most two units, nothing
 * smaller than minutes ("3 mo 2 w", "5 h 30 min").
 */
val COMPACT_DURATION = DisplaySettings(maxUnits = 2, smallestUnit = TimeUnitLevel.MINUTES)

/**
 * Splits a duration into the units of the short format ([COMPACT_DURATION]).
 *
 * A duration of milliseconds has no calendar position, but months and years have different
 * lengths. So the duration is read as "the time that ends now" and split with the same
 * calendar-correct [ElapsedTime] as the counters. For the current streak this gives exactly
 * the same numbers as the card (its start is the last relapse, its end is now).
 *
 * @param durationMillis the duration, e.g. a streak or the time clean in total
 * @param nowMillis the moment the duration ends at (normally the current time)
 * @param timeZone zone of the calendar part; tests pass a fixed one
 * @return the units to show, largest first; empty if less than one minute (or nothing) passed
 */
fun compactDurationUnits(
    durationMillis: Long,
    nowMillis: Long,
    timeZone: TimeZone = TimeZone.currentSystemDefault()
): List<Pair<TimeUnitLevel, Int>> {
    if (durationMillis <= 0) return emptyList()
    return ElapsedTime.between(nowMillis - durationMillis, nowMillis, timeZone).visibleUnits(COMPACT_DURATION)
}
