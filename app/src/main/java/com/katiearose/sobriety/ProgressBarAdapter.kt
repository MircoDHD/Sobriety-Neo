package com.katiearose.sobriety

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView.ViewHolder
import com.katiearose.sobriety.databinding.ListItemProgressBarBinding
import com.katiearose.sobriety.shared.Addiction
import com.katiearose.sobriety.utils.convertRangeToPercentList
import kotlinx.datetime.DateTimeUnit

/**
 * List of progress bars on the summary screen (one bar per unit: years, months, days, hours,
 * minutes, seconds). Units that are still 0 at the top are left out.
 *
 * The adapter does not refresh itself. The [com.katiearose.sobriety.activities.Summary] activity
 * calls [update] every second while it is visible. (The old version started a timer in this class
 * that was never stopped.)
 */
class ProgressBarAdapter(
    private val addiction: Addiction, private val context: Context,
) :
    ListAdapter<Triple<Int, Int, DateTimeUnit>, ProgressBarAdapter.ProgressBarViewHolder>(object :
        DiffUtil.ItemCallback<Triple<Int, Int, DateTimeUnit>>() {
        override fun areItemsTheSame(
            oldItem: Triple<Int, Int, DateTimeUnit>,
            newItem: Triple<Int, Int, DateTimeUnit>
        ): Boolean = oldItem.third == newItem.third

        // Triple.first is the number, second the per mille. Both can change independently
        // (for example when the month changes length), so compare the whole value.
        override fun areContentsTheSame(
            oldItem: Triple<Int, Int, DateTimeUnit>,
            newItem: Triple<Int, Int, DateTimeUnit>
        ): Boolean = oldItem == newItem
    }) {

    init {
        update()
    }

    /** Recalculates all bars for the current moment. */
    fun update() {
        // A stopped addiction stops counting at the moment it was stopped. The old code kept
        // counting up to "now", so the summary of a stopped addiction kept growing.
        val end = if (addiction.status == Addiction.Status.Stopped)
            addiction.history.values.last()
        else
            System.currentTimeMillis()
        val list = convertRangeToPercentList(addiction.history.keys.last(), end)
        // Filter initial zeros (for example no "0 years" bar). If everything is zero (right after
        // a relapse, or a stopped attempt of length 0) indexOfFirst returns -1 and the old code
        // crashed with subList(-1, ...). Show only the seconds bar in that case.
        val firstNonZero = list.indexOfFirst { it.first != 0 }
        submitList(if (firstNonZero == -1) list.takeLast(1) else list.subList(firstNonZero, list.size))
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ProgressBarViewHolder {
        val binding =
            ListItemProgressBarBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ProgressBarViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ProgressBarViewHolder, position: Int) {
        val bar = currentList[position]
        holder.timeUnitProgress.progress = bar.second
        holder.timeUnit.text = when (bar.third) {
            DateTimeUnit.SECOND ->  context.resources.getQuantityString(R.plurals.seconds, bar.first, bar.first)
            DateTimeUnit.MINUTE -> context.resources.getQuantityString(R.plurals.minutes, bar.first, bar.first)
            DateTimeUnit.HOUR -> context.resources.getQuantityString(R.plurals.hours, bar.first, bar.first)
            DateTimeUnit.DAY -> context.resources.getQuantityString(R.plurals.days, bar.first, bar.first)
            DateTimeUnit.MONTH -> context.resources.getQuantityString(R.plurals.months, bar.first, bar.first)
            DateTimeUnit.YEAR -> context.resources.getQuantityString(R.plurals.years, bar.first, bar.first)
            else -> "Unsupported"
        }
    }

    class ProgressBarViewHolder(
        binding: ListItemProgressBarBinding,
    ) :
        ViewHolder(binding.root) {
        val timeUnit = binding.timeUnit
        val timeUnitProgress = binding.timeUnitProgress
    }
}
