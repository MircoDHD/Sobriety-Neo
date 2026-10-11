package com.katiearose.sobriety.utils

import android.content.Context
import android.os.SystemClock
import com.katiearose.sobriety.R
import com.katiearose.sobriety.shared.Addiction
import com.katiearose.sobriety.shared.CacheHandler
import com.katiearose.sobriety.shared.migrateLegacyTimeSaving

/**
 * The list of all trackers (addictions) of the app, shared by every screen.
 *
 * Both main screens (new UI in src/neo, classic UI in src/classic) expose it as `Main.addictions`,
 * so the rest of the code does not need to know which variant is running.
 */
object Trackers {
    val addictions = ArrayList<Addiction>()

    /**
     * Loads the data from disk if the list is still empty (first start, or Android ended the app
     * process in the meantime) and updates old data to the current format.
     *
     * @return true if the data was changed while loading and has to be saved
     */
    fun ensureLoaded(context: Context): Boolean {
        if (addictions.isNotEmpty()) return false
        val start = SystemClock.elapsedRealtime()
        addictions.addAll(CacheHandler(context).loadFromDisk())
        DiagnosticsLog.event(
            "data_load",
            "ms" to (SystemClock.elapsedRealtime() - start),
            "trackers" to addictions.size
        )
        return migrate(context, addictions)
    }

    /**
     * Updates data of older versions: the "time saved per day" of the original app becomes a
     * saving of the category "Time".
     *
     * @return true if something was changed
     */
    fun migrate(context: Context, list: List<Addiction>): Boolean {
        var changed = false
        val timeName = context.getString(R.string.saving_category_time)
        for (addiction in list) {
            if (addiction.migrateLegacyTimeSaving(timeName)) changed = true
        }
        return changed
    }

    /** Sorts by priority (high first); the order used by the classic main screen. */
    fun sortByPriority() = addictions.sortWith { a1, a2 -> a1.priority.compareTo(a2.priority) }
}
