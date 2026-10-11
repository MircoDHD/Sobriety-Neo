package com.katiearose.sobriety.utils

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager

// Typed access to the app settings. The keys are defined in res/xml/root_preferences.xml.

fun Context.getSharedPref(): SharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)

/** Pattern for java.time.format.DateTimeFormatter, e.g. "MMMM dd uuuu". */
fun SharedPreferences.getDateFormatPattern(): String = getString("date_format", "MMMM dd uuuu")!!

/** Whether the "write a note?" dialog appears after a relapse. */
fun SharedPreferences.getAddNoteAfterRelapsePref(): Boolean =
    getBoolean("add_note_after_relapse", true)

/** Highest accepted value for [getAverageAttemptsWindow]; more than this makes no sense. */
const val MAX_AVERAGE_ATTEMPTS_WINDOW = 1000

/**
 * How many earlier attempts are used for the "recent average" on the main screen (0 = off).
 *
 * The setting is a free text field. The old code used `Integer.parseInt`, which crashed the whole
 * app on every start when the field was empty or contained a huge number. Now anything that is not
 * a sensible number falls back to the default of 3.
 */
fun SharedPreferences.getAverageAttemptsWindow(): Int =
    getString("average_attempts_window", "3")?.trim()?.toIntOrNull()
        ?.takeIf { it in 0..MAX_AVERAGE_ATTEMPTS_WINDOW } ?: 3

/** Whether the timeline shows the alternative (vertical line) layout. */
fun SharedPreferences.getAltTimelinePref(): Boolean = getBoolean("alt_timeline_view", false)

/** "asc", "desc" or anything else for unsorted. */
fun SharedPreferences.getSortNotesPref(): String = getString("sort_notes", "asc")!!

/** "asc", "desc" or anything else for unsorted. */
fun SharedPreferences.getSortMilestonesPref(): String = getString("sort_milestones", "asc")!!

fun SharedPreferences.getHideCompletedMilestonesPref(): Boolean =
    getBoolean("hide_completed_milestones", false)
