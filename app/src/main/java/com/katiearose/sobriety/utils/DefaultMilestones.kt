package com.katiearose.sobriety.utils

import android.content.Context
import androidx.core.content.edit
import com.katiearose.sobriety.shared.Addiction
import kotlinx.datetime.DateTimeUnit

/**
 * Default milestones of Sobriety Neo.
 *
 * New trackers get these milestones automatically, unless the user turned it off in the settings
 * (Settings → Milestones → "Default milestones", opt-out). The list can be changed in the settings
 * and reset to the standard list. Milestones can always be removed from a tracker afterwards.
 *
 * Stored in the preferences as text, e.g. "1:DAY,1:WEEK,1:MONTH".
 */
object DefaultMilestones {
    const val PREF_ENABLED = "default_milestones_enabled"
    const val PREF_LIST = "default_milestones"

    /** The standard list: 1 day, 1 week, 1, 3 and 6 months, 1, 2 and 5 years. */
    val STANDARD: List<Pair<Int, DateTimeUnit>> = listOf(
        1 to DateTimeUnit.DAY,
        1 to DateTimeUnit.WEEK,
        1 to DateTimeUnit.MONTH,
        3 to DateTimeUnit.MONTH,
        6 to DateTimeUnit.MONTH,
        1 to DateTimeUnit.YEAR,
        2 to DateTimeUnit.YEAR,
        5 to DateTimeUnit.YEAR
    )

    /** The units a milestone can have, with the names used in the stored text. */
    val UNITS: List<Pair<String, DateTimeUnit>> = listOf(
        "HOUR" to DateTimeUnit.HOUR,
        "DAY" to DateTimeUnit.DAY,
        "WEEK" to DateTimeUnit.WEEK,
        "MONTH" to DateTimeUnit.MONTH,
        "YEAR" to DateTimeUnit.YEAR
    )

    private fun encode(list: List<Pair<Int, DateTimeUnit>>): String =
        list.joinToString(",") { m -> "${m.first}:${UNITS.first { it.second == m.second }.first}" }

    private fun decode(text: String): List<Pair<Int, DateTimeUnit>> =
        text.split(",").mapNotNull { part ->
            val (number, unit) = part.split(":").takeIf { it.size == 2 } ?: return@mapNotNull null
            val n = number.toIntOrNull() ?: return@mapNotNull null
            val u = UNITS.firstOrNull { it.first == unit }?.second ?: return@mapNotNull null
            n to u
        }

    fun isEnabled(context: Context): Boolean = context.getSharedPref().getBoolean(PREF_ENABLED, true)

    /** The current list, sorted by length. */
    fun get(context: Context): List<Pair<Int, DateTimeUnit>> {
        val text = context.getSharedPref().getString(PREF_LIST, null) ?: return STANDARD
        return decode(text).sortedBy { it.first * it.second.approximateMillis() }
    }

    fun set(context: Context, list: List<Pair<Int, DateTimeUnit>>) =
        context.getSharedPref().edit { putString(PREF_LIST, encode(list.distinct())) }

    fun reset(context: Context) = context.getSharedPref().edit { remove(PREF_LIST) }

    /**
     * Adds the default milestones to [addiction] if the setting is on.
     * Milestones the tracker already has are not added twice.
     */
    fun applyIfEnabled(context: Context, addiction: Addiction) {
        if (isEnabled(context)) addTo(addiction, get(context))
    }

    /** Adds [list] to the milestones of [addiction]. @return true if something was added */
    fun addTo(addiction: Addiction, list: List<Pair<Int, DateTimeUnit>>): Boolean =
        addiction.milestones.addAll(list)

    /** Rough length of a unit, only used to sort milestones. */
    private fun DateTimeUnit.approximateMillis(): Long = when (this) {
        DateTimeUnit.HOUR -> 3_600_000L
        DateTimeUnit.DAY -> 86_400_000L
        DateTimeUnit.WEEK -> 7 * 86_400_000L
        DateTimeUnit.MONTH -> 2_629_746_000L
        DateTimeUnit.YEAR -> 31_556_952_000L
        else -> 86_400_000L
    }
}
