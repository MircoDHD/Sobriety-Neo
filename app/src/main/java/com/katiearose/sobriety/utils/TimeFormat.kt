package com.katiearose.sobriety.utils

import android.content.Context
import com.katiearose.sobriety.R
import com.katiearose.sobriety.shared.Addiction
import com.katiearose.sobriety.shared.CounterMode
import com.katiearose.sobriety.shared.DisplaySettings
import com.katiearose.sobriety.shared.ElapsedTime
import com.katiearose.sobriety.shared.TimeUnitLevel
import com.katiearose.sobriety.shared.compactDurationUnits
import com.katiearose.sobriety.shared.nextMilestone
import com.katiearose.sobriety.shared.visibleUnits
import kotlinx.datetime.DateTimeUnit
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/*
 * Text for the counters of Sobriety Neo, taking the per-tracker display options into account
 * (see DisplaySettings.kt in the shared module).
 */

/** Plural resource without a trailing comma for each unit ("%d year" / "%d years"). */
private fun pluralFor(level: TimeUnitLevel): Int = when (level) {
    TimeUnitLevel.YEARS -> R.plurals.years_plain
    TimeUnitLevel.MONTHS -> R.plurals.months_plain
    TimeUnitLevel.WEEKS -> R.plurals.weeks_plain
    TimeUnitLevel.DAYS -> R.plurals.days_plain
    TimeUnitLevel.HOURS -> R.plurals.hours_plain
    TimeUnitLevel.MINUTES -> R.plurals.minutes_plain
    TimeUnitLevel.SECONDS -> R.plurals.seconds_plain
}

/** "3 days", "1 hour" ... */
fun Context.unitText(level: TimeUnitLevel, value: Int): String =
    resources.getQuantityString(pluralFor(level), value, value)

/**
 * The time between [start] and [end] as text, using the display options of a tracker.
 * With the default options this is exactly the text of the original app.
 */
fun Context.convertRangeToString(start: Long, end: Long, settings: DisplaySettings): String {
    if (settings.showsEverything) return convertRangeToString(start, end)
    val units = ElapsedTime.between(start, end).visibleUnits(settings)
    if (units.isEmpty()) return getString(R.string.less_than, unitText(settings.smallestUnit, 1))
    return units.joinToString(" ") { unitText(it.first, it.second) }
}

/** Format string of each unit in the short format ("%d y", "%d mo", ...). */
private fun shortFormatFor(level: TimeUnitLevel): Int = when (level) {
    TimeUnitLevel.YEARS -> R.string.duration_short_years
    TimeUnitLevel.MONTHS -> R.string.duration_short_months
    TimeUnitLevel.WEEKS -> R.string.duration_short_weeks
    TimeUnitLevel.DAYS -> R.string.duration_short_days
    TimeUnitLevel.HOURS -> R.string.duration_short_hours
    TimeUnitLevel.MINUTES -> R.string.duration_short_minutes
    TimeUnitLevel.SECONDS -> R.string.duration_short_seconds
}

/**
 * A duration in the short format of the Statistics screen, e.g. "3 mo 2 w" or "5 h 30 min"
 * (at most two units, see compactDurationUnits() in the shared module). Below one minute the
 * text is "< 1 min"; nothing at all (e.g. a tracker that starts in the future) is "0 min", so
 * the two cases can be told apart.
 *
 * @param millis the duration, e.g. a streak
 * @param now the moment the duration ends at; the calendar part is counted back from here
 */
fun Context.compactDurationText(millis: Long, now: Long = System.currentTimeMillis()): String {
    if (millis <= 0) return getString(R.string.duration_short_minutes, 0)
    val units = compactDurationUnits(millis, now)
    if (units.isEmpty()) return getString(R.string.duration_short_less_than_minute)
    return units.joinToString(" ") { getString(shortFormatFor(it.first), it.second) }
}

/** A milestone as text, e.g. "3 months". */
fun Context.milestoneText(milestone: Pair<Int, DateTimeUnit>): String {
    val level = when (milestone.second) {
        DateTimeUnit.HOUR -> TimeUnitLevel.HOURS
        DateTimeUnit.DAY -> TimeUnitLevel.DAYS
        DateTimeUnit.WEEK -> TimeUnitLevel.WEEKS
        DateTimeUnit.MONTH -> TimeUnitLevel.MONTHS
        DateTimeUnit.YEAR -> TimeUnitLevel.YEARS
        else -> TimeUnitLevel.DAYS
    }
    return unitText(level, milestone.first)
}

/**
 * "Next milestone: 1 month, in 3 days 4 hours", or null if there are no milestones left (or the
 * tracker is stopped).
 */
fun Context.milestoneCountdownText(addiction: Addiction, now: Long = System.currentTimeMillis()): String? {
    if (addiction.status == Addiction.Status.Stopped) return null
    val (milestone, goal) = addiction.nextMilestone(now) ?: return null
    return getString(
        R.string.next_milestone_countdown,
        milestoneText(milestone),
        convertRangeToString(now, goal, addiction.display)
    )
}

/** Date in the format chosen in the settings. */
fun Context.formatDate(millis: Long): String {
    val dateFormat = DateTimeFormatter.ofPattern(getSharedPref().getDateFormatPattern())
    return dateFormat.format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()))
}

/**
 * The main time text of a card: depending on the status and on the display mode of the tracker
 * the counter, the countdown to the next milestone, or both.
 */
fun Context.cardTimeText(addiction: Addiction, now: Long = System.currentTimeMillis()): String {
    val display = addiction.display
    return when (addiction.status) {
        Addiction.Status.Ongoing -> {
            val counter = convertRangeToString(addiction.history.keys.last(), now, display)
            val countdown = milestoneCountdownText(addiction, now)
            when (display.mode) {
                CounterMode.COUNTER -> counter
                // Without milestones left there is nothing to count down to: show the counter.
                CounterMode.MILESTONE -> countdown ?: counter
                CounterMode.BOTH -> if (countdown == null) counter else "$counter\n$countdown"
            }
        }
        Addiction.Status.Stopped -> getString(
            R.string.stop_notice,
            formatDate(addiction.history.values.last()),
            convertRangeToString(addiction.history.keys.last(), addiction.history.values.last(), display)
        )
        Addiction.Status.Future -> getString(
            R.string.time_until_tracked,
            convertRangeToString(now, addiction.history.keys.last(), display)
        )
    }
}
