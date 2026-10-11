package com.katiearose.sobriety.utils

import android.content.Context
import com.katiearose.sobriety.R
import com.katiearose.sobriety.shared.SavingCategory
import com.katiearose.sobriety.shared.SavingsSummary
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToLong

/*
 * Turning the numbers of SavingsCalculator.kt (shared module) into text for the screen.
 */

/** "1,234.50 €" (grouping and decimal separator follow the phone's language). */
fun formatAmount(amount: Double, unit: String): String =
    String.format(Locale.getDefault(), "%,.2f %s", amount, unit).trim()

/** Like [formatAmount], but without decimals for whole things ("40 cigarettes"). */
fun formatCount(amount: Double, unit: String): String =
    if (abs(amount) >= 100 || amount == amount.roundToLong().toDouble())
        String.format(Locale.getDefault(), "%,d %s", amount.roundToLong(), unit).trim()
    else formatAmount(amount, unit)

/**
 * A duration in minutes as short text: "3 d 4 h" from one day upwards, otherwise "2 h 15 min".
 * Negative values (more time spent than saved) get a minus sign.
 */
fun Context.formatMinutesShort(minutes: Double): String {
    val total = minutes.roundToLong()
    val sign = if (total < 0) "-" else ""
    val m = abs(total)
    val days = m / (24 * 60)
    val hours = (m % (24 * 60)) / 60
    val mins = m % 60
    return sign + if (days > 0) getString(R.string.duration_days_hours, days, hours)
    else getString(R.string.duration_hours_minutes, hours, mins)
}

/**
 * The short savings text for a card on the main screen, for example
 * "💰 123.40 € saved · 45.10 € this month" and "⏱ 3 d 4 h saved · 20 h this month".
 *
 * @return null if there is nothing to show (no money saving and no time saving set)
 */
fun Context.savingsCardText(summary: SavingsSummary): String? {
    val lines = mutableListOf<String>()
    for (money in summary.moneyByCurrency.values) {
        lines += getString(
            R.string.card_savings_money,
            formatAmount(money.total, money.unit),
            formatAmount(money.thisMonth, money.unit)
        )
    }
    if (summary.timeTotalMinutes != null && summary.timeThisMonthMinutes != null) {
        lines += getString(
            R.string.card_savings_time,
            formatMinutesShort(summary.timeTotalMinutes!!),
            formatMinutesShort(summary.timeThisMonthMinutes!!)
        )
    }
    return if (lines.isEmpty()) null else lines.joinToString("\n")
}

/** The full overview for the Savings screen (one line per value). */
fun Context.savingsDetailText(summary: SavingsSummary, currency: String): String {
    if (summary.isEmpty && summary.moneySpentTotal == 0.0 && summary.minutesSpentTotal == 0L)
        return getString(R.string.savings_totals_empty)
    val lines = mutableListOf<String>()
    for (money in summary.moneyByCurrency.values) {
        lines += getString(
            R.string.savings_total_line, getString(R.string.saving_category_money),
            formatAmount(money.total, money.unit), formatAmount(money.thisMonth, money.unit)
        )
    }
    for (item in summary.items.filter { it.category == SavingCategory.CUSTOM }) {
        lines += getString(
            R.string.savings_total_line, item.name,
            formatCount(item.total, item.unit), formatCount(item.thisMonth, item.unit)
        )
    }
    if (summary.timeTotalMinutes != null && summary.timeThisMonthMinutes != null) {
        lines += getString(
            R.string.savings_total_line, getString(R.string.time),
            formatMinutesShort(summary.timeTotalMinutes!!), formatMinutesShort(summary.timeThisMonthMinutes!!)
        )
    }
    if (summary.moneySpentTotal > 0.0) {
        lines += getString(
            R.string.savings_spent_money_line,
            formatAmount(summary.moneySpentTotal, currency),
            formatAmount(summary.moneySpentThisMonth, currency)
        )
    }
    if (summary.minutesSpentTotal > 0L) {
        lines += getString(
            R.string.savings_spent_time_line,
            formatMinutesShort(summary.minutesSpentTotal.toDouble()),
            formatMinutesShort(summary.minutesSpentThisMonth.toDouble())
        )
    }
    return lines.joinToString("\n")
}
