package com.katiearose.sobriety.utils

import android.app.Activity
import android.content.Context
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.widget.AppCompatEditText
import androidx.preference.PreferenceManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.katiearose.sobriety.R
import com.katiearose.sobriety.activities.Main
import com.katiearose.sobriety.shared.Addiction
import com.katiearose.sobriety.shared.CacheHandler
import com.katiearose.sobriety.shared.ElapsedTime
import kotlinx.datetime.DateTimeUnit

// Approximate lengths in seconds. They are only used by convertSecondsToString() for *durations*
// (for example the average length of earlier attempts). Counting up from a real date uses
// ElapsedTime instead, which follows the calendar.
private const val MINUTE = 60
private const val HOUR = MINUTE * 60
private const val DAY = HOUR * 24
private const val WEEK = DAY * 7
private const val YEAR = (DAY * 365.25).toInt()
private const val MONTH = YEAR / 12

/**
 * Turns a length of time into text like "1 month 2 weeks 3 days 4 hours 5 minutes and 6 seconds".
 *
 * This is for **durations without a start date** (average of earlier attempts, length of a
 * finished attempt). A month is counted as 1/12 of 365.25 days, so the result is a good
 * approximation, not a calendar calculation. For the running counter use [convertRangeToString].
 *
 * @param given number of seconds; -1 returns an empty string
 */
fun Context.convertSecondsToString(given: Long): String {
    if (given == -1L) return ""
    var time = given
    val s = time % MINUTE
    time -= s
    val m = (time % HOUR) / MINUTE
    time -= m * MINUTE
    val h = (time % DAY) / HOUR
    time -= h * HOUR
    val d = (time % WEEK) / DAY
    time -= d * DAY
    val w = (time % MONTH) / WEEK
    time -= w * WEEK
    val mo = (time % YEAR) / MONTH
    time -= mo * MONTH
    val y = time / YEAR
    val stringBuilder = StringBuilder()
    if (y != 0L) stringBuilder.append(resources.getQuantityString(R.plurals.years, y.toInt(), y)).append(" ")
    if (mo != 0L) stringBuilder.append(resources.getQuantityString(R.plurals.months, mo.toInt(), mo)).append(" ")
    if (w != 0L) stringBuilder.append(resources.getQuantityString(R.plurals.weeks, w.toInt(), w)).append(" ")
    if (d != 0L) stringBuilder.append(resources.getQuantityString(R.plurals.days, d.toInt(), d)).append(" ")
    if (h != 0L) stringBuilder.append(resources.getQuantityString(R.plurals.hours, h.toInt(), h)).append(" ")
    if (m != 0L) stringBuilder.append(resources.getQuantityString(R.plurals.minutes, m.toInt(), m)).append(" ")
    // "and" is only inserted when there is something before the seconds.
    if (!(y == 0L && mo == 0L && w == 0L && d == 0L && h == 0L && m == 0L)) stringBuilder.append(
        getString(R.string.and)
    ).append(" ")
    stringBuilder.append(resources.getQuantityString(R.plurals.seconds, s.toInt(), s))
    return stringBuilder.toString()
}

/**
 * Text for the time between two moments, for example "2 months 1 week 3 days 4 hours 5 minutes
 * and 6 seconds". This is the running counter on the main screen.
 *
 * The numbers come from [ElapsedTime], which fixes the old bug where the counter showed exactly
 * one day too much (the old version mixed calendar days with elapsed hours).
 *
 * @param start start in epoch milliseconds; -1 returns an empty string
 * @param end end in epoch milliseconds, defaults to now
 */
fun Context.convertRangeToString(start: Long, end: Long = System.currentTimeMillis()): String {
    if (start == -1L) return ""
    val elapsed = ElapsedTime.between(start, end)

    val y = elapsed.years
    val mo = elapsed.months
    val w = elapsed.weeks
    val d = elapsed.days
    val h = elapsed.hours
    val m = elapsed.minutes
    val s = elapsed.seconds

    // Units that are zero are left out ("3 days and 5 seconds" instead of "0 months 3 days ...").
    val stringBuilder = StringBuilder()
    if (y != 0) stringBuilder.append(resources.getQuantityString(R.plurals.years, y, y)).append(" ")
    if (mo != 0) stringBuilder.append(resources.getQuantityString(R.plurals.months, mo, mo)).append(" ")
    if (w != 0) stringBuilder.append(resources.getQuantityString(R.plurals.weeks, w, w)).append(" ")
    if (d != 0) stringBuilder.append(resources.getQuantityString(R.plurals.days, d, d)).append(" ")
    if (h != 0) stringBuilder.append(resources.getQuantityString(R.plurals.hours, h, h)).append(" ")
    if (m != 0) stringBuilder.append(resources.getQuantityString(R.plurals.minutes, m, m)).append(" ")
    if (!(y == 0 && mo == 0 && w == 0 && d == 0 && h == 0 && m == 0)) stringBuilder.append(
        getString(R.string.and)
    ).append(" ")
    stringBuilder.append(resources.getQuantityString(R.plurals.seconds, s, s))
    return stringBuilder.toString()
}

/**
 * Data for the progress bars on the summary screen: one bar per unit (years, months, days, hours,
 * minutes, seconds).
 *
 * Each triple contains the raw number (e.g. 12), the progress in per mille (0..1000, for example
 * 500 = half full), and the unit (hours).
 *
 * The bars fill up towards the next bigger unit: the hour bar goes to 24, the minute and second
 * bars to 60, the day bar to the length of the current month, the month bar to 12. The year bar
 * has no natural end, so it crosses 50% at 2 years and never completes.
 *
 * @param start start in epoch milliseconds
 * @param end end in epoch milliseconds, defaults to now (pass the stop time of a stopped addiction)
 */
fun convertRangeToPercentList(start: Long, end: Long = System.currentTimeMillis())
        : List<Triple<Int, Int, DateTimeUnit>> {
    val elapsed = ElapsedTime.between(start, end)

    // Weeks make the visualization non-whole so they are omitted.
    // Initially the maximum ("denominator") is stored in the second part of the triple and is
    // turned into the per mille value in the map() below.
    return listOf(
        Triple(elapsed.years, elapsed.years + 2, DateTimeUnit.YEAR),
        Triple(elapsed.months, 12, DateTimeUnit.MONTH),
        // The day bar uses the days of the running month (not only 0..6) and its real length.
        Triple(elapsed.daysIntoCycle, elapsed.daysInCycle, DateTimeUnit.DAY),
        Triple(elapsed.hours, 24, DateTimeUnit.HOUR),
        Triple(elapsed.minutes, 60, DateTimeUnit.MINUTE),
        Triple(elapsed.seconds, 60, DateTimeUnit.SECOND)
    ).map {
        Triple(
            it.first,
            // coerceIn: a bar can never be fuller than 100%.
            (1000 * it.first.toDouble().div(it.second)).toInt().coerceIn(0, 1000),
            it.third
        )
    }
}

/** Shows a dialog with OK and Cancel. [action] is only run when the user presses OK. */
fun Context.showConfirmDialog(title: String, message: String, action: () -> Unit) {
    MaterialAlertDialogBuilder(this)
        .setTitle(title)
        .setMessage(message)
        .setPositiveButton(android.R.string.ok) { _, _ -> action() }
        .setNegativeButton(android.R.string.cancel, null)
        .show()
}

/**
 * Applies the theme the user chose in the settings (light / dark / system and Material You).
 * Has to be called at the start of every activity, before `super.onCreate`.
 */
fun Activity.applyThemes() {
    val preferences = PreferenceManager.getDefaultSharedPreferences(this)
    when (preferences.getString("theme", "system")) {
        "light" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        "dark" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        "system" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
    }
    if (preferences.getBoolean("material_you", false)) {
        setTheme(R.style.Theme_Sobriety_Material3)
    } else setTheme(R.style.Theme_Sobriety)
}

/**
 * Reads which addiction an activity should show from the intent (the position in
 * [Main.addictions]). Fails with an exception if the activity was started without it.
 */
fun Activity.checkValidIntentData() : Int {
    val index = intent.getIntExtra(Main.EXTRA_ADDICTION_POSITION, -1)
    require(index != -1) { "Invalid intent data received" }
    return index
}

/**
 * Returns the addiction this activity was opened for, or null if it does not exist (the caller
 * should then call `finish()`).
 *
 * The list of addictions lives in a static variable ([Main.addictions]). When Android ends the app
 * process in the background and the user later returns to a detail screen (summary, timeline,
 * notes...), Android recreates only that screen and the static list is empty. The old code then
 * crashed with an IndexOutOfBoundsException. Here the data is read from disk in that case.
 */
fun Activity.loadAddictionFromIntent(): Addiction? {
    val index = checkValidIntentData()
    if (Main.addictions.isEmpty()) Main.addictions.addAll(CacheHandler(this).loadFromDisk())
    return Main.addictions.getOrNull(index)
}

fun AppCompatEditText.isInputEmpty(): Boolean = text == null || text.toString().isBlank()

/**
 * Parses a number that a person typed, accepting both "4.50" and "4,50".
 * Many keyboards (for example with German settings) type a comma as the decimal separator, and
 * `String.toDouble()` throws an exception for it, which crashed the app.
 *
 * @return the number, or null if the text is not a normal, non-negative number
 */
fun String.toDoubleLenient(): Double? =
    trim().replace(',', '.').toDoubleOrNull()?.takeIf { it.isFinite() && it >= 0.0 }

/** Switches between visible and gone. */
fun View.toggleVisibility() {
    visibility = if (visibility == View.VISIBLE) View.GONE else View.VISIBLE
}

fun Context.toast(@StringRes textRes: Int) = Toast.makeText(this, textRes, Toast.LENGTH_SHORT).show()

/** Lets code write `textView.textResource = R.string.x`. Setting works, reading is not supported. */
inline var TextView.textResource: Int
    set(@StringRes value) = setText(value)
    @Deprecated("This property is set-only, don't bother.", level = DeprecationLevel.HIDDEN)
    get() = error("don't")

/** Saves all addictions of the app ([Main.addictions]) to disk. */
fun CacheHandler.write() = writeCache(Main.addictions)
