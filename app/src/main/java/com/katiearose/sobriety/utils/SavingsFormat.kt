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
 * The short savings text for a card on the main screen: saved in total and saved in the current
 * streak (since the last relapse), for example "💰 123.40 € saved · 12.00 € this streak" and
 * "⏱ 3 d 4 h saved · 20 h this streak". Without a running streak (stopped, or starting in the
 * future) only the total is shown.
 *
 * @return null if there is nothing to show (no money saving and no time saving set)
 */
fun Context.savingsCardText(summary: SavingsSummary): String? {
    val lines = mutableListOf<String>()
    for (money in summary.moneyByCurrency.values) {
        val total = formatAmount(money.total, money.unit)
        lines += if (summary.hasCurrentStreak)
            getString(R.string.card_savings_money, total, formatAmount(money.currentStreak, money.unit))
        else getString(R.string.card_savings_money_total, total)
    }
    val timeTotal = summary.timeTotalMinutes
    val timeStreak = summary.timeCurrentStreakMinutes
    if (timeTotal != null && timeStreak != null) {
        lines += if (summary.hasCurrentStreak)
            getString(R.string.card_savings_time, formatMinutesShort(timeTotal), formatMinutesShort(timeStreak))
        else getString(R.string.card_savings_time_total, formatMinutesShort(timeTotal))
    }
    return if (lines.isEmpty()) null else lines.joinToString("\n")
}

/**
 * One line of the Savings screen: "Money: 123.40 € (this streak: 12.00 € · this month: 45.10 €)".
 * The streak part is left out if no attempt is running.
 */
private fun Context.totalLine(summary: SavingsSummary, label: String, total: String, streak: String, month: String): String =
    if (summary.hasCurrentStreak) getString(R.string.savings_total_line, label, total, streak, month)
    else getString(R.string.savings_total_line_no_streak, label, total, month)

/** The full overview for the Savings screen (one line per value). */
fun Context.savingsDetailText(summary: SavingsSummary, currency: String): String {
    if (summary.isEmpty && summary.moneySpentTotal == 0.0 && summary.minutesSpentTotal == 0L)
        return getString(R.string.savings_totals_empty)
    val lines = mutableListOf<String>()
    for (money in summary.moneyByCurrency.values) {
        lines += totalLine(
            summary, getString(R.string.saving_category_money), formatAmount(money.total, money.unit),
            formatAmount(money.currentStreak, money.unit), formatAmount(money.thisMonth, money.unit)
        )
    }
    for (item in summary.items.filter { it.category == SavingCategory.CUSTOM }) {
        lines += totalLine(
            summary, item.name, formatCount(item.total, item.unit),
            formatCount(item.currentStreak, item.unit), formatCount(item.thisMonth, item.unit)
        )
    }
    val timeTotal = summary.timeTotalMinutes
    val timeMonth = summary.timeThisMonthMinutes
    val timeStreak = summary.timeCurrentStreakMinutes
    if (timeTotal != null && timeMonth != null && timeStreak != null) {
        lines += totalLine(
            summary, getString(R.string.time), formatMinutesShort(timeTotal),
            formatMinutesShort(timeStreak), formatMinutesShort(timeMonth)
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
