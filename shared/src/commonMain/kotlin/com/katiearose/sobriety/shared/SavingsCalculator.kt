package com.katiearose.sobriety.shared

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.Serializable

/*
 * Savings: what the user saves by not doing the addiction, and how much is saved in total.
 *
 * Data model (all stored inside [Addiction]):
 *  - Addiction.savings:      name -> (amount PER DAY, unit). This map exists since the original
 *                            app; the amount is always normalized to one day.
 *  - Addiction.savingsMeta:  name -> [SavingMeta] (category and how the user entered the rate).
 *                            Added by Sobriety Neo. Savings without an entry here are treated as
 *                            CUSTOM, entered per day (that is what the original app did).
 *  - Addiction.timeSaving:   time saved per day (hours:minutes) from the original app. Sobriety Neo
 *                            moves it into a TIME saving when the data is loaded
 *                            ([migrateLegacyTimeSaving]); afterwards it is 00:00.
 *  - Addiction.relapseCosts: money and time spent during relapses ([RelapseCost]).
 *
 * Calculation:
 *   saved = amount per day * sober days - spent during relapses
 * "Sober days" are the summed lengths of all attempts (the time between relapses) as exact
 * fractions of 24 hours, so the value grows smoothly. Money spent during relapses is subtracted
 * from MONEY savings in the [Addiction.primaryCurrency]; time spent is subtracted from the time
 * saving. The result can be negative if more was spent than saved.
 *
 * Three windows are calculated: all time, the current calendar month, and the current streak
 * (the running attempt since the last relapse). The current streak subtracts no relapse costs:
 * they belong to the relapse that ended the previous attempt, not to the clean time after it.
 */

/** What kind of thing is saved. Decides how the value is shown and whether relapse costs apply. */
@Serializable
enum class SavingCategory {
    /** Money, the unit is a currency such as "€" or "USD". Relapse money costs are subtracted. */
    MONEY,

    /** Anything else with a free unit, e.g. "cigarettes" or "drinks". */
    CUSTOM,

    /**
     * Time. The amount is in HOURS (unit "h"); it is added to the time saved and relapse time costs
     * are subtracted. Replaces the separate "time saved per day" of the original app
     * (see [migrateLegacyTimeSaving]).
     */
    TIME
}

/** Unit stored for [SavingCategory.TIME] savings. */
const val TIME_SAVING_UNIT = "h"

/** The period the user entered the amount for ("4.50 € per day", "20 € per week"). */
@Serializable
enum class RatePeriod(val days: Double) {
    DAY(1.0),
    WEEK(7.0),

    /** An average month (365.2425 / 12 days), so 12 months add up to one average year. */
    MONTH(365.2425 / 12.0)
}

/**
 * Extra information about one entry of [Addiction.savings].
 *
 * @property enteredAmount the amount exactly as the user typed it, for the [period]
 *   (the per-day value in [Addiction.savings] is derived from it)
 */
@Serializable
data class SavingMeta(
    val category: SavingCategory = SavingCategory.CUSTOM,
    val period: RatePeriod = RatePeriod.DAY,
    val enteredAmount: Double = 0.0
)

/**
 * What one relapse cost, entered optionally when logging the relapse.
 *
 * @property timeMillis when the relapse was logged (epoch milliseconds)
 * @property money money spent, in the [Addiction.primaryCurrency]; 0 if not entered
 * @property minutes time spent in minutes; 0 if not entered
 */
@Serializable
data class RelapseCost(
    val timeMillis: Long,
    val money: Double = 0.0,
    val minutes: Long = 0
)

/**
 * Total, "this month" and "current streak" value of one saving.
 *
 * @property currentStreak saved during the running attempt; 0 if the addiction is stopped or
 *   starts in the future (see [SavingsSummary.hasCurrentStreak])
 */
data class SavingTotal(
    val name: String,
    val unit: String,
    val category: SavingCategory,
    val total: Double,
    val thisMonth: Double,
    val currentStreak: Double = 0.0
)

/** Everything the UI needs to show the savings of one addiction. */
data class SavingsSummary(
    /** One entry per saving (money and custom), in the order of [Addiction.savings]. */
    val items: List<SavingTotal>,
    /** Money saved per currency (several money savings with the same currency are added up). */
    val moneyByCurrency: Map<String, SavingTotal>,
    /** Time saved in minutes (null if no time saving is set). */
    val timeTotalMinutes: Double?,
    val timeThisMonthMinutes: Double?,
    /** Time saved in the current streak in minutes (null if no time saving is set). */
    val timeCurrentStreakMinutes: Double?,
    /** True if an attempt is running, i.e. the "current streak" values mean something. */
    val hasCurrentStreak: Boolean,
    /** Spent during relapses, over all time and in the current month. */
    val moneySpentTotal: Double,
    val moneySpentThisMonth: Double,
    val minutesSpentTotal: Long,
    val minutesSpentThisMonth: Long
) {
    /** True if there is anything to show at all. */
    val isEmpty: Boolean get() = items.isEmpty() && timeTotalMinutes == null
}

private const val MILLIS_PER_DAY = 24.0 * 60 * 60 * 1000

/**
 * The currency used for money spent during relapses: the unit of the first MONEY saving,
 * or "" if there is none.
 */
val Addiction.primaryCurrency: String
    get() = savings.keys.firstOrNull { savingsMeta[it]?.category == SavingCategory.MONEY }
        ?.let { savings[it]?.second } ?: ""

/**
 * Moves the "time saved per day" of the original app into a saving of the TIME category, so that
 * time is chosen like every other category. Does nothing if no time saving is set.
 *
 * @param name name for the new saving (localized "Time"); a number is added if it exists
 * @return true if the addiction was changed and has to be saved
 */
fun Addiction.migrateLegacyTimeSaving(name: String): Boolean {
    val minutes = timeSaving.hour * 60 + timeSaving.minute
    if (minutes == 0) return false
    var unique = name
    var counter = 2
    while (unique in savings) unique = "$name (${counter++})"
    val hours = minutes / 60.0
    savings[unique] = Pair(hours, TIME_SAVING_UNIT)
    savingsMeta[unique] = SavingMeta(SavingCategory.TIME, RatePeriod.DAY, hours)
    timeSaving = kotlinx.datetime.LocalTime(0, 0)
    return true
}

/** True if at least one saving is in the MONEY category. */
val Addiction.hasMoneySaving: Boolean
    get() = savings.keys.any { savingsMeta[it]?.category == SavingCategory.MONEY }

/**
 * Sober time (milliseconds) inside the window [fromMillis, toMillis).
 *
 * Every attempt in [Addiction.history] counts from its start to its end; the running attempt
 * (end = 0) counts until [nowMillis]. Attempts that start in the future count nothing.
 */
fun Addiction.soberMillisBetween(fromMillis: Long, toMillis: Long, nowMillis: Long): Long {
    var sum = 0L
    for ((start, end) in history) {
        val realEnd = if (end == 0L) nowMillis else end
        val s = maxOf(start, fromMillis)
        val e = minOf(realEnd, toMillis, nowMillis)
        if (e > s) sum += e - s
    }
    return sum
}

/** Start of the current calendar month (day 1, 00:00) in [timeZone], as epoch milliseconds. */
fun startOfMonthMillis(nowMillis: Long, timeZone: TimeZone): Long {
    val now = Instant.fromEpochMilliseconds(nowMillis).toLocalDateTime(timeZone)
    return LocalDateTime(now.year, now.month, 1, 0, 0).toInstant(timeZone).toEpochMilliseconds()
}

/**
 * Calculates all savings of this addiction.
 *
 * @param nowMillis the current time; a parameter so that tests can use a fixed time
 */
fun Addiction.calculateSavings(
    nowMillis: Long = Clock.System.now().toEpochMilliseconds(),
    timeZone: TimeZone = TimeZone.currentSystemDefault()
): SavingsSummary {
    val monthStart = startOfMonthMillis(nowMillis, timeZone)
    val soberDaysTotal = soberMillisBetween(Long.MIN_VALUE, Long.MAX_VALUE, nowMillis) / MILLIS_PER_DAY
    val soberDaysMonth = soberMillisBetween(monthStart, Long.MAX_VALUE, nowMillis) / MILLIS_PER_DAY
    // The running attempt starts at the last key of the history (the last relapse or the start).
    val hasCurrentStreak = status == Addiction.Status.Ongoing
    val soberDaysStreak = if (hasCurrentStreak)
        soberMillisBetween(history.keys.last(), Long.MAX_VALUE, nowMillis) / MILLIS_PER_DAY else 0.0

    val moneySpentTotal = relapseCosts.sumOf { it.money }
    val moneySpentMonth = relapseCosts.filter { it.timeMillis >= monthStart }.sumOf { it.money }
    val minutesSpentTotal = relapseCosts.sumOf { it.minutes }
    val minutesSpentMonth = relapseCosts.filter { it.timeMillis >= monthStart }.sumOf { it.minutes }

    val currency = primaryCurrency
    val items = savings.map { (name, value) ->
        val (perDay, unit) = value
        val category = savingsMeta[name]?.category ?: SavingCategory.CUSTOM
        SavingTotal(name, unit, category, perDay * soberDaysTotal, perDay * soberDaysMonth, perDay * soberDaysStreak)
    }

    // Add up money per currency and subtract the relapse costs once, from the primary currency
    // (not from the current streak, see the comment at the top of this file).
    val moneyByCurrency = LinkedHashMap<String, SavingTotal>()
    for (item in items.filter { it.category == SavingCategory.MONEY }) {
        val existing = moneyByCurrency[item.unit]
        moneyByCurrency[item.unit] = existing?.copy(
            total = existing.total + item.total,
            thisMonth = existing.thisMonth + item.thisMonth,
            currentStreak = existing.currentStreak + item.currentStreak
        ) ?: item.copy(name = item.unit)
    }
    moneyByCurrency[currency]?.let {
        moneyByCurrency[currency] = it.copy(
            total = it.total - moneySpentTotal,
            thisMonth = it.thisMonth - moneySpentMonth
        )
    }

    // Time: the legacy value (normally 0 after the migration) plus all TIME savings (hours/day).
    val timePerDayMinutes = timeSaving.hour * 60 + timeSaving.minute +
        items.filter { it.category == SavingCategory.TIME }.sumOf { savings[it.name]!!.first * 60.0 }
    val timeSet = timePerDayMinutes > 0
    return SavingsSummary(
        items = items,
        moneyByCurrency = moneyByCurrency,
        timeTotalMinutes = if (timeSet) timePerDayMinutes * soberDaysTotal - minutesSpentTotal else null,
        timeThisMonthMinutes = if (timeSet) timePerDayMinutes * soberDaysMonth - minutesSpentMonth else null,
        timeCurrentStreakMinutes = if (timeSet) timePerDayMinutes * soberDaysStreak else null,
        hasCurrentStreak = hasCurrentStreak,
        moneySpentTotal = moneySpentTotal,
        moneySpentThisMonth = moneySpentMonth,
        minutesSpentTotal = minutesSpentTotal,
        minutesSpentThisMonth = minutesSpentMonth
    )
}
