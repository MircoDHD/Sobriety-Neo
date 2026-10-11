package com.katiearose.sobriety.utils

import android.content.Context
import androidx.core.content.edit
import com.katiearose.sobriety.R
import com.katiearose.sobriety.shared.Addiction
import org.json.JSONArray
import org.json.JSONObject

/**
 * Rule that fills a tab automatically. A tab shows every tracker that matches its rule OR was
 * added to it by hand ([TrackerTab.members]).
 */
enum class TabRule {
    /** Only trackers added by hand. */
    NONE,
    PRIORITY_HIGH,
    PRIORITY_MEDIUM,
    PRIORITY_LOW,
    /** Trackers that are counting (not stopped, not waiting). */
    ONGOING,
    /** Stopped trackers. */
    STOPPED;

    fun matches(addiction: Addiction): Boolean = when (this) {
        NONE -> false
        PRIORITY_HIGH -> addiction.priority == Addiction.Priority.HIGH
        PRIORITY_MEDIUM -> addiction.priority == Addiction.Priority.MEDIUM
        PRIORITY_LOW -> addiction.priority == Addiction.Priority.LOW
        ONGOING -> addiction.status == Addiction.Status.Ongoing
        STOPPED -> addiction.status == Addiction.Status.Stopped
    }
}

/**
 * One user-defined tab of the main screen (new UI).
 *
 * @property members names of trackers added by hand (tracker names are unique)
 */
data class TrackerTab(
    val id: Long,
    var name: String,
    var rule: TabRule,
    val members: MutableSet<String> = LinkedHashSet()
) {
    fun contains(addiction: Addiction) = rule.matches(addiction) || addiction.name in members
}

/**
 * Stores the tabs of the new UI in the app preferences (as JSON), in the order they are shown.
 * The "All" tab is not stored; it is always first and can be hidden in the settings.
 *
 * On the very first start one example tab is created: "High priority" (filled automatically).
 */
object TabStore {
    private const val PREF_TABS = "tabs_json"
    const val PREF_SHOW_ALL = "show_all_tab"
    const val PREF_SORT = "card_sort"

    fun load(context: Context): MutableList<TrackerTab> {
        val text = context.getSharedPref().getString(PREF_TABS, null)
            ?: return mutableListOf(
                TrackerTab(1, context.getString(R.string.tab_default_high), TabRule.PRIORITY_HIGH)
            ).also { save(context, it) }
        return try {
            val array = JSONArray(text)
            MutableList(array.length()) { i ->
                val o = array.getJSONObject(i)
                val members = o.optJSONArray("members")
                TrackerTab(
                    id = o.getLong("id"),
                    name = o.getString("name"),
                    rule = runCatching { TabRule.valueOf(o.getString("rule")) }.getOrDefault(TabRule.NONE),
                    members = LinkedHashSet<String>().apply {
                        if (members != null) for (j in 0 until members.length()) add(members.getString(j))
                    }
                )
            }
        } catch (e: Exception) {
            mutableListOf()
        }
    }

    fun save(context: Context, tabs: List<TrackerTab>) {
        val array = JSONArray()
        for (tab in tabs) {
            array.put(JSONObject().apply {
                put("id", tab.id)
                put("name", tab.name)
                put("rule", tab.rule.name)
                put("members", JSONArray(tab.members.toList()))
            })
        }
        context.getSharedPref().edit { putString(PREF_TABS, array.toString()) }
    }

    /** A new id that is not used by any tab yet. */
    fun newId(tabs: List<TrackerTab>): Long = (tabs.maxOfOrNull { it.id } ?: 0) + 1

    fun showAllTab(context: Context): Boolean = context.getSharedPref().getBoolean(PREF_SHOW_ALL, true)

    /** Removes a deleted tracker from all tabs. */
    fun forget(context: Context, name: String) {
        val tabs = load(context)
        if (tabs.any { it.members.remove(name) }) save(context, tabs)
    }

    /** Sorts trackers by the order chosen in the settings ("Sort cards by"). */
    fun sort(context: Context, list: List<Addiction>, now: Long): List<Addiction> {
        fun streak(a: Addiction) =
            if (a.status == Addiction.Status.Ongoing) now - a.history.keys.last() else -1L
        return when (context.getSharedPref().getString(PREF_SORT, "priority")) {
            "streak_desc" -> list.sortedByDescending { streak(it) }
            "streak_asc" -> list.sortedBy { if (streak(it) < 0) Long.MAX_VALUE else streak(it) }
            "name" -> list.sortedBy { it.name.lowercase() }
            else -> list.sortedBy { it.priority.ordinal }
        }
    }
}
