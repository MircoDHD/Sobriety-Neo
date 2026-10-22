package com.katiearose.sobriety.activities

import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import com.google.android.material.chip.Chip
import com.katiearose.sobriety.R
import com.katiearose.sobriety.databinding.ActivityStatisticsBinding
import com.katiearose.sobriety.databinding.ItemStatisticsChipBinding
import com.katiearose.sobriety.databinding.ItemStatisticsTileBinding
import com.katiearose.sobriety.shared.Addiction
import com.katiearose.sobriety.shared.AddictionStatistics
import com.katiearose.sobriety.shared.CacheHandler
import com.katiearose.sobriety.shared.relapsesPerMonth
import com.katiearose.sobriety.shared.statistics
import com.katiearose.sobriety.utils.Trackers
import com.katiearose.sobriety.utils.applyThemes
import com.katiearose.sobriety.utils.compactDurationText
import com.katiearose.sobriety.utils.getSharedPref
import com.katiearose.sobriety.utils.handleInfoMenuItem
import com.katiearose.sobriety.utils.write
import kotlinx.datetime.TimeZone
import java.text.DateFormatSymbols

/**
 * Statistics screen (Sobriety Neo, both UI variants).
 *
 * Shows one selection at a time instead of all trackers below each other (that was too cluttered):
 * 1. A selector at the top: "All" and one chip per tracker. The choice is remembered.
 * 2. Four tiles with the key numbers of the selection, durations in a short format ("3 mo 2 w").
 *    - All: entries, time clean in total, relapses in total, relapses in the last 30 days
 *    - One tracker: current streak, longest streak, relapses in the last 30 days, clean in total
 * 3. One chart at a time: relapses per month for "All"; for one tracker a toggle switches between
 *    relapses per month and the length of the attempts (with the average of the finished ones).
 *    Without any relapse there is nothing to compare, so a short hint replaces the chart.
 *
 * The numbers come from Statistics.kt in the shared module. The screen is built when it is
 * opened and when the selection changes; it does not tick, so it costs nothing while it is not
 * on screen.
 */
class Statistics : AppCompatActivity() {

    private lateinit var binding: ActivityStatisticsBinding

    private companion object {
        const val MONTHS = 12
        const val MAX_ATTEMPT_BARS = 15
        const val DAY_MILLIS = 24.0 * 60 * 60 * 1000

        /** Name of the selected tracker; missing or unknown means "All". */
        const val PREF_SELECTED = "statistics_selected_tracker"
    }

    /** The tracker shown at the moment (null = "All"); used by the chart toggle. */
    private var current: Addiction? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        applyThemes()
        super.onCreate(savedInstanceState)
        binding = ActivityStatisticsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        if (Trackers.ensureLoaded(this)) CacheHandler(this).write()

        val addictions = Trackers.addictions
        if (addictions.isEmpty()) {
            showEmpty()
            return
        }
        buildSelector(addictions)
    }

    /** Without any tracker there is nothing to select: only a hint, no tiles and no chart. */
    private fun showEmpty() {
        binding.trackerChips.visibility = View.GONE
        binding.tiles.visibility = View.GONE
        showNoData(R.string.statistics_empty)
    }

    /** Hides the chart card and shows [text] in its place. */
    private fun showNoData(text: Int) {
        binding.chartCard.visibility = View.GONE
        binding.noData.visibility = View.VISIBLE
        binding.noData.setText(text)
    }

    /**
     * Adds the chips ("All" + one per tracker) and selects the remembered one.
     *
     * Trackers are identified by name: names are unique (see the import, which renames
     * duplicates), and a name survives restarts, unlike a list position.
     */
    private fun buildSelector(addictions: List<Addiction>) {
        val remembered = getSharedPref().getString(PREF_SELECTED, null)
        // null stands for "All".
        val choices: List<Addiction?> = listOf<Addiction?>(null) + addictions
        val chipIds = choices.map { addiction ->
            val chip: Chip = ItemStatisticsChipBinding.inflate(layoutInflater, binding.trackerChips, false).root
            chip.id = View.generateViewId()
            chip.text = addiction?.name ?: getString(R.string.statistics_all)
            binding.trackerChips.addView(chip)
            chip.id
        }

        val selectedIndex = choices.indexOfFirst { it?.name == remembered }.coerceAtLeast(0)
        binding.trackerChips.check(chipIds[selectedIndex])
        binding.chartToggle.check(R.id.chart_per_month)
        binding.chartToggle.addOnButtonCheckedListener { _, _, isChecked ->
            // Called for the button that is unchecked as well; react only once, to the new one.
            if (isChecked) showChart(current, System.currentTimeMillis())
        }
        show(choices[selectedIndex])
        // Scroll the selected chip into view once the chips have been laid out.
        binding.trackerChips.post {
            binding.trackerChips.findViewById<View>(chipIds[selectedIndex])?.let { chip ->
                (binding.trackerChips.parent as? View)?.scrollTo(chip.left, 0)
            }
        }

        binding.trackerChips.setOnCheckedStateChangeListener { _, checkedIds ->
            val index = chipIds.indexOf(checkedIds.firstOrNull() ?: return@setOnCheckedStateChangeListener)
            if (index < 0) return@setOnCheckedStateChangeListener
            val selected = choices[index]
            getSharedPref().edit {
                if (selected == null) remove(PREF_SELECTED) else putString(PREF_SELECTED, selected.name)
            }
            show(selected)
        }
    }

    /** Fills the tiles and the chart for "All" ([addiction] == null) or one tracker. */
    private fun show(addiction: Addiction?) {
        current = addiction
        val now = System.currentTimeMillis()
        if (addiction == null) showAllTiles(now) else showTrackerTiles(addiction, now)
        showChart(addiction, now)
    }

    private fun showAllTiles(now: Long) {
        val all = Trackers.addictions.map { it.statistics(now) }
        setTile(binding.tile1, Trackers.addictions.size.toString(), R.string.statistics_entries)
        setTile(binding.tile2, compactDurationText(all.sumOf { it.totalSober }, now), R.string.statistics_clean_total)
        setTile(binding.tile3, all.sumOf { it.relapseCount }.toString(), R.string.statistics_relapses_total)
        setTile(binding.tile4, all.sumOf { it.relapsesLast30Days }.toString(), R.string.statistics_relapses_30_days)
    }

    private fun showTrackerTiles(addiction: Addiction, now: Long) {
        val st = addiction.statistics(now)
        setTile(binding.tile1, currentStreakText(addiction, st, now), R.string.statistics_current_streak)
        setTile(binding.tile2, compactDurationText(st.longestStreak, now), R.string.statistics_longest_streak)
        setTile(binding.tile3, st.relapsesLast30Days.toString(), R.string.statistics_relapses_30_days)
        setTile(binding.tile4, compactDurationText(st.totalSober, now), R.string.statistics_clean_total)
    }

    /**
     * Shows the chart for "All" ([addiction] == null) or one tracker, or a hint if there was no
     * relapse yet (a single attempt and an empty monthly chart say nothing).
     */
    private fun showChart(addiction: Addiction?, now: Long) {
        val addictions = if (addiction == null) Trackers.addictions else listOf(addiction)
        val stats = addictions.map { it.statistics(now) }
        if (stats.sumOf { it.relapseCount } == 0) {
            showNoData(if (addiction == null) R.string.statistics_no_relapses_all else R.string.statistics_no_relapses)
            return
        }
        binding.noData.visibility = View.GONE
        binding.chartCard.visibility = View.VISIBLE
        // "All" has only the monthly chart: a plain title instead of the toggle.
        binding.chartToggle.visibility = if (addiction == null) View.GONE else View.VISIBLE
        binding.chartTitle.visibility = if (addiction == null) View.VISIBLE else View.GONE

        val attempts = addiction != null && binding.chartToggle.checkedButtonId == R.id.chart_attempts
        if (attempts) {
            val st = stats.single()
            val shown = st.attemptDurations.takeLast(MAX_ATTEMPT_BARS)
            val firstNumber = st.attemptDurations.size - shown.size + 1
            binding.chart.setData(
                shown.mapIndexed { i, ms -> "#${firstNumber + i}" to (ms / DAY_MILLIS).toFloat() }
            ) { days -> if (days < 10f) "%.1f".format(days) else "%.0f".format(days) }
            var caption = getString(R.string.statistics_caption_attempts, MAX_ATTEMPT_BARS)
            if (st.averageFinished > 0) {
                caption += "\n" + getString(R.string.statistics_average_attempt, compactDurationText(st.averageFinished, now))
            }
            binding.chartCaption.text = caption
        } else {
            val monthNames = DateFormatSymbols.getInstance().shortMonths
            val perMonth = relapsesPerMonth(addictions, now, MONTHS, TimeZone.currentSystemDefault())
            binding.chart.setData(perMonth.map { (month, count) -> monthNames[month.month - 1] to count.toFloat() })
            binding.chartCaption.setText(R.string.statistics_caption_months)
        }
    }

    /** The current streak, or why there is none (stopped, or the start lies in the future). */
    private fun currentStreakText(addiction: Addiction, st: AddictionStatistics, now: Long): String =
        when (addiction.status) {
            Addiction.Status.Ongoing -> compactDurationText(st.currentStreak, now)
            Addiction.Status.Stopped -> getString(R.string.statistics_stopped)
            Addiction.Status.Future -> getString(R.string.statistics_not_started)
        }

    private fun setTile(tile: ItemStatisticsTileBinding, value: String, label: Int) {
        tile.tileValue.text = value
        tile.tileLabel.setText(label)
        // Read as one item by screen readers: "Current streak: 3 mo 2 w".
        tile.root.contentDescription = "${getString(label)}: $value"
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_info, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean =
        handleInfoMenuItem(item.itemId, R.string.statistics, R.string.info_statistics) || super.onOptionsItemSelected(item)
}
