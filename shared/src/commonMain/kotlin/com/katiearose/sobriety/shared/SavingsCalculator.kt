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
 *  - Addiction.timeSaving:   time saved per day (hours:minutes).
 *  - Addiction.relapseCosts: money and time spent during relapses ([RelapseCost]).
 *
 * Calculation:
 *   saved = amount per day * sober days - spent during relapses
 * "Sober days" are the summed lengths of all attempts (the time between relapses) as exact
 * fractions of 24 hours, so the value grows smoothly. Money spent during relapses is subtracted
 * from MONEY savings in the [Addiction.primaryCurrency]; time spent is subtracted from the time
 * saving. The result can be negative if more was spent than saved.
 */

/** What kind of thing is saved. Decides how the value is shown and whether relapse costs apply. */
@Serializable
enum class SavingCategory {
    /** Money, the unit is a currency such as "€" or "USD". Relapse money costs are subtracted. */
    MONEY,

    /** Anything else with a free unit, e.g. "cigarettes" or "drinks". */
    CUSTOM
}

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

/** Total and "this month" value of one saving. */
data class SavingTotal(
    val name: String,
    val unit: String,
    val category: SavingCategory,
    val total: Double,
    val thisMonth: Double
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

    val moneySpentTotal = relapseCosts.sumOf { it.money }
    val moneySpentMonth = relapseCosts.filter { it.timeMillis >= monthStart }.sumOf { it.money }
    val minutesSpentTotal = relapseCosts.sumOf { it.minutes }
    val minutesSpentMonth = relapseCosts.filter { it.timeMillis >= monthStart }.sumOf { it.minutes }

    val currency = primaryCurrency
    val items = savings.map { (name, value) ->
        val (perDay, unit) = value
        val category = savingsMeta[name]?.category ?: SavingCategory.CUSTOM
        SavingTotal(name, unit, category, perDay * soberDaysTotal, perDay * soberDaysMonth)
    }

    // Add up money per currency and subtract the relapse costs once, from the primary currency.
    val moneyByCurrency = LinkedHashMap<String, SavingTotal>()
    for (item in items.filter { it.category == SavingCategory.MONEY }) {
        val existing = moneyByCurrency[item.unit]
        moneyByCurrency[item.unit] = existing?.copy(
            total = existing.total + item.total,
            thisMonth = existing.thisMonth + item.thisMonth
        ) ?: item.copy(name = item.unit)
    }
    moneyByCurrency[currency]?.let {
        moneyByCurrency[currency] = it.copy(
            total = it.total - moneySpentTotal,
            thisMonth = it.thisMonth - moneySpentMonth
        )
    }

    val timePerDayMinutes = timeSaving.hour * 60 + timeSaving.minute
    val timeSet = timePerDayMinutes > 0
    return SavingsSummary(
        items = items,
        moneyByCurrency = moneyByCurrency,
        timeTotalMinutes = if (timeSet) timePerDayMinutes * soberDaysTotal - minutesSpentTotal else null,
        timeThisMonthMinutes = if (timeSet) timePerDayMinutes * soberDaysMonth - minutesSpentMonth else null,
        moneySpentTotal = moneySpentTotal,
        moneySpentThisMonth = moneySpentMonth,
        minutesSpentTotal = minutesSpentTotal,
        minutesSpentThisMonth = minutesSpentMonth
    )
}
