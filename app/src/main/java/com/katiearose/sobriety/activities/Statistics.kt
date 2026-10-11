package com.katiearose.sobriety.activities

import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import com.katiearose.sobriety.R
import com.katiearose.sobriety.databinding.ActivityStatisticsBinding
import com.katiearose.sobriety.databinding.ItemStatisticsSectionBinding
import com.katiearose.sobriety.shared.relapsesPerMonth
import com.katiearose.sobriety.shared.statistics
import com.katiearose.sobriety.utils.Trackers
import com.katiearose.sobriety.utils.applyThemes
import com.katiearose.sobriety.utils.convertSecondsToString
import com.katiearose.sobriety.utils.handleInfoMenuItem
import com.katiearose.sobriety.utils.write
import com.katiearose.sobriety.shared.CacheHandler
import kotlinx.datetime.TimeZone
import java.text.DateFormatSymbols

/**
 * Statistics screen (Sobriety Neo, both UI variants).
 *
 * 1. An overview card: relapses per month over the last 12 months, for all trackers together.
 * 2. One card per tracker: current and longest streak, average attempt, relapses, total clean
 *    time, and a bar chart of the length of the last attempts (in days).
 *
 * The numbers come from Statistics.kt in the shared module. The screen is built once when it is
 * opened; it does not tick, so it costs nothing while it is not on screen.
 */
class Statistics : AppCompatActivity() {

    private lateinit var binding: ActivityStatisticsBinding

    private companion object {
        const val MONTHS = 12
        const val MAX_ATTEMPT_BARS = 15
        const val DAY_MILLIS = 24.0 * 60 * 60 * 1000
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        applyThemes()
        super.onCreate(savedInstanceState)
        binding = ActivityStatisticsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        if (Trackers.ensureLoaded(this)) CacheHandler(this).write()
        build()
    }

    private fun newSection(): ItemStatisticsSectionBinding =
        ItemStatisticsSectionBinding.inflate(layoutInflater, binding.sections, true)

    private fun build() {
        val now = System.currentTimeMillis()
        val addictions = Trackers.addictions
        if (addictions.isEmpty()) {
            newSection().apply {
                sectionTitle.setText(R.string.statistics)
                sectionNumbers.setText(R.string.statistics_empty)
                chart.visibility = android.view.View.GONE
            }
            return
        }

        // Overview: relapses per month.
        val monthNames = DateFormatSymbols.getInstance().shortMonths
        val perMonth = relapsesPerMonth(addictions, now, MONTHS, TimeZone.currentSystemDefault())
        val all = addictions.map { it.statistics(now) }
        newSection().apply {
            sectionTitle.setText(R.string.statistics_overview)
            sectionNumbers.text = getString(
                R.string.statistics_overview_numbers,
                addictions.size,
                all.sumOf { it.relapseCount },
                all.sumOf { it.relapsesLast30Days }
            )
            chartTitle.setText(R.string.statistics_relapses_per_month)
            chart.setData(perMonth.map { (month, count) -> monthNames[month.month - 1] to count.toFloat() })
        }

        // One card per tracker.
        for ((addiction, st) in addictions.zip(all)) {
            newSection().apply {
                sectionTitle.text = addiction.name
                fun d(millis: Long) = if (millis <= 0) "–" else convertSecondsToString(millis / 1000)
                sectionNumbers.text = getString(
                    R.string.statistics_tracker_numbers,
                    d(st.currentStreak),
                    d(st.longestStreak),
                    d(st.averageFinished),
                    st.relapseCount,
                    st.relapsesLast30Days,
                    d(st.totalSober)
                )
                val attempts = st.attemptDurations.takeLast(MAX_ATTEMPT_BARS)
                val firstNumber = st.attemptDurations.size - attempts.size + 1
                chartTitle.text = getString(R.string.statistics_attempt_lengths)
                chart.setData(
                    attempts.mapIndexed { i, ms -> "#${firstNumber + i}" to (ms / DAY_MILLIS).toFloat() }
                ) { "%.1f".format(it) }
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_info, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean =
        handleInfoMenuItem(item.itemId, R.string.statistics, R.string.info_statistics) || super.onOptionsItemSelected(item)
}
