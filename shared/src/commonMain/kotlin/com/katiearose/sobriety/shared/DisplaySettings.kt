package com.katiearose.sobriety.shared

import kotlinx.datetime.DateTimeUnit
import kotlinx.serialization.Serializable

/*
 * Per-tracker display options of Sobriety Neo (stored in Addiction.display).
 *
 * They only change how the time is SHOWN. The time is always tracked to the millisecond.
 */

/** The units of the counter, from the largest to the smallest. The order matters. */
@Serializable
enum class TimeUnitLevel {
    YEARS, MONTHS, WEEKS, DAYS, HOURS, MINUTES, SECONDS
}

/** What the card on the main screen shows as its main information. */
@Serializable
enum class CounterMode {
    /** Time since the last relapse (the classic counter). */
    COUNTER,

    /** Countdown to the next milestone. */
    MILESTONE,

    /** Both, the counter first. */
    BOTH
}

/** Keys of the card buttons that can be hidden in the new UI (the relapse button can not). */
object CardButtons {
    const val TIMELINE = "timeline"
    const val NOTES = "notes"
    const val SAVINGS = "savings"
    const val MILESTONES = "milestones"
    const val STOP = "stop"
    const val DELETE = "delete"

    /** All hideable buttons, in the order they appear on the card. */
    val ALL = listOf(TIMELINE, NOTES, SAVINGS, MILESTONES, STOP, DELETE)
}

/**
 * @property maxUnits how many units are shown at most, counted from the largest unit that is not
 *   zero (0 = no limit). Example: 1 shows only "2 years" or, below one year, only "5 months".
 * @property smallestUnit units smaller than this are hidden (e.g. MINUTES hides the seconds)
 * @property mode counter, countdown to the next milestone, or both
 * @property hiddenButtons keys from [CardButtons] that are not shown on the card (new UI only)
 */
@Serializable
data class DisplaySettings(
    val maxUnits: Int = 0,
    val smallestUnit: TimeUnitLevel = TimeUnitLevel.SECONDS,
    val mode: CounterMode = CounterMode.COUNTER,
    val hiddenButtons: Set<String> = emptySet()
) {
    /** True if the time is shown completely, as in the original app. */
    val showsEverything: Boolean get() = maxUnits <= 0 && smallestUnit == TimeUnitLevel.SECONDS
}

/** The value of each unit, in the order of [TimeUnitLevel]. */
fun ElapsedTime.unitValues(): List<Pair<TimeUnitLevel, Int>> = listOf(
    TimeUnitLevel.YEARS to years,
    TimeUnitLevel.MONTHS to months,
    TimeUnitLevel.WEEKS to weeks,
    TimeUnitLevel.DAYS to days,
    TimeUnitLevel.HOURS to hours,
    TimeUnitLevel.MINUTES to minutes,
    TimeUnitLevel.SECONDS to seconds
)

/**
 * The units that should be shown for this elapsed time.
 *
 * 1. Start at the largest unit that is not zero.
 * 2. Take the units down to [DisplaySettings.smallestUnit], at most [DisplaySettings.maxUnits].
 * 3. Leave out units that are zero ("1 year 3 days" instead of "1 year 0 months 0 weeks 3 days").
 *
 * @return the units to show, or an empty list if everything that may be shown is zero
 *   (e.g. smallest unit "years" and less than one year has passed); the UI then shows
 *   "less than 1 year".
 */
fun ElapsedTime.visibleUnits(settings: DisplaySettings): List<Pair<TimeUnitLevel, Int>> {
    val all = unitValues()
    val smallest = settings.smallestUnit.ordinal
    val first = all.indexOfFirst { it.second != 0 }
    if (first == -1 || first > smallest) return emptyList()
    var window = all.subList(first, smallest + 1)
    if (settings.maxUnits > 0) window = window.take(settings.maxUnits)
    return window.filter { it.second != 0 }
}

/**
 * The next milestone that has not been reached yet, with its goal time.
 *
 * @return (milestone, goal in epoch milliseconds), or null if there is none left
 */
fun Addiction.nextMilestone(nowMillis: Long): Pair<Pair<Int, DateTimeUnit>, Long>? =
    milestones
        .map { it to milestoneGoalMillis(it) }
        .filter { it.second > nowMillis && it.second != Long.MAX_VALUE }
        .minByOrNull { it.second }
