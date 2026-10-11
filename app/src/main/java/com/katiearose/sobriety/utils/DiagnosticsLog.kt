package com.katiearose.sobriety.utils

import android.content.Context
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.preference.PreferenceManager
import com.katiearose.sobriety.BuildConfig
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Optional, anonymous diagnostics log of Sobriety Neo.
 *
 * ## Privacy rules (please keep them when adding new events)
 *  - **Off by default.** Nothing is written unless the user turns on
 *    Settings → Diagnostics → "Collect diagnostic logs" ([PREF_ENABLED]).
 *  - **Stays on the device.** The log is a text file in the app's private storage. It only leaves
 *    the phone when the user exports it (Settings → Diagnostics → Export) and sends it to someone.
 *  - **Anonymous.** Only event names, numbers and true/false values are stored. Never store names
 *    of addictions, notes, amounts of money, savings names, dates of relapses, device model or any
 *    other free text. [event] only accepts numbers and booleans as values for this reason, and
 *    crashes are stored without the exception message (messages can contain user input).
 *  - **Small.** The file is limited to [MAX_BYTES]; when it is full the older half is dropped.
 *
 * ## What is recorded
 *  - `app_start`: time from creating the main screen until it is visible, number of trackers
 *  - `data_load` / `data_save`: duration in ms and number of trackers
 *  - `screen_<name>`: a screen was opened, and how long it took to appear (ms)
 *  - `ticks`: per visit of the main screen, how many counter updates ran and how many were slow
 *  - `relapse_logged`: whether costs were entered (true/false only)
 *  - `trim_memory`: Android asked the app to free memory (level number)
 *  - `crash`: exception type and the stack trace lines (no message)
 *
 * The header of an exported file contains the app version, Android version (API level), and
 * screen size class.
 */
object DiagnosticsLog {

    const val PREF_ENABLED = "diagnostics_enabled"
    private const val TAG = "SobrietyDiagnostics"
    private const val FILE_NAME = "diagnostics.log"
    private const val MAX_BYTES = 256 * 1024

    private var appContext: Context? = null
    private val timeFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    /** Called once from [com.katiearose.sobriety.SobrietyApp.onCreate]. */
    fun init(context: Context) {
        appContext = context.applicationContext
    }

    private fun file(context: Context) = File(context.filesDir, FILE_NAME)

    /** True if the user has turned diagnostics on. */
    fun isEnabled(context: Context? = appContext): Boolean =
        context != null && PreferenceManager.getDefaultSharedPreferences(context)
            .getBoolean(PREF_ENABLED, false)

    /**
     * Records an event, if diagnostics are enabled.
     *
     * @param name a fixed event name written in the code (never user input)
     * @param fields key/value pairs; values must be numbers or booleans (anything else is dropped)
     */
    fun event(name: String, vararg fields: Pair<String, Any>) {
        val context = appContext ?: return
        if (!isEnabled(context)) return
        val line = buildString {
            append(timeFormat.format(Date())).append(" event=").append(name)
            for ((key, value) in fields) {
                if (value is Number || value is Boolean) append(' ').append(key).append('=').append(value)
            }
        }
        append(context, line)
    }

    /** Overload kept for call sites that already have a context at hand. */
    @Suppress("UNUSED_PARAMETER")
    fun event(context: Context, name: String, vararg fields: Pair<String, Any>) = event(name, *fields)

    /** Records a crash: the exception types and stack trace lines, without messages. */
    fun crash(throwable: Throwable) {
        val context = appContext ?: return
        if (!isEnabled(context)) return
        val text = buildString {
            append(timeFormat.format(Date())).append(" event=crash\n")
            var t: Throwable? = throwable
            var depth = 0
            while (t != null && depth < 5) {
                append(if (depth == 0) "  type=" else "  caused_by=").append(t.javaClass.name).append('\n')
                for (frame in t.stackTrace.take(25)) append("    at ").append(frame.toString()).append('\n')
                t = t.cause
                depth++
            }
        }
        append(context, text.trimEnd())
    }

    @Synchronized
    private fun append(context: Context, text: String) {
        try {
            val f = file(context)
            if (f.exists() && f.length() > MAX_BYTES) {
                // Keep the newer half of the file.
                val content = f.readText()
                f.writeText(content.substring(content.length / 2).substringAfter('\n'))
            }
            f.appendText(text + "\n")
        } catch (e: Exception) {
            // Diagnostics must never break the app.
            Log.w(TAG, "Could not write diagnostics", e)
        }
    }

    /** Size of the log file in bytes (0 if there is none). */
    fun sizeBytes(context: Context): Long = file(context).let { if (it.exists()) it.length() else 0L }

    /** Deletes all recorded diagnostics. */
    @Synchronized
    fun clear(context: Context) {
        file(context).delete()
    }

    /** Header for an exported file: versions and screen size class only. */
    private fun header(context: Context): String {
        val config = context.resources.configuration
        return buildString {
            append("# Sobriety Neo diagnostics log\n")
            append("# app_version=").append(BuildConfig.VERSION_NAME)
                .append(" version_code=").append(BuildConfig.VERSION_CODE).append('\n')
            append("# android_api=").append(Build.VERSION.SDK_INT).append('\n')
            append("# screen_width_dp=").append(config.screenWidthDp)
                .append(" screen_height_dp=").append(config.screenHeightDp).append('\n')
            append("# exported=").append(timeFormat.format(Date())).append('\n')
        }
    }

    /**
     * Writes the header and the log to [uri] (chosen by the user in the system file picker).
     *
     * @return true on success
     */
    fun export(context: Context, uri: Uri): Boolean = try {
        val f = file(context)
        val body = if (f.exists()) f.readText() else ""
        context.contentResolver.openOutputStream(uri, "wt")?.bufferedWriter()?.use {
            it.write(header(context))
            it.write(body)
        } != null
    } catch (e: Exception) {
        Log.w(TAG, "Export failed", e)
        false
    }
}
