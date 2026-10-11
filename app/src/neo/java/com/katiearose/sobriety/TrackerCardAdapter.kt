package com.katiearose.sobriety

import android.annotation.SuppressLint
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.res.ResourcesCompat
import androidx.recyclerview.widget.RecyclerView
import com.katiearose.sobriety.databinding.CardTrackerBinding
import com.katiearose.sobriety.shared.Addiction
import com.katiearose.sobriety.shared.CardButtons
import com.katiearose.sobriety.shared.ElapsedTime
import com.katiearose.sobriety.shared.calculateSavings
import com.katiearose.sobriety.shared.nextMilestone
import com.katiearose.sobriety.utils.cardTimeText
import com.katiearose.sobriety.utils.savingsCardText

/**
 * Cards of the new UI (one page / tab of the main screen).
 *
 * Unlike the classic adapter it works on its own list (the trackers of one tab, already filtered
 * and sorted) and passes the [Addiction] itself to the callbacks, not a position.
 *
 * The counter is refreshed by the main screen once per second with [PAYLOAD_TICK]; then only the
 * texts and the ring are updated, not the whole card.
 */
class TrackerCardAdapter(
    private val context: Context,
    private val actions: Actions
) : RecyclerView.Adapter<TrackerCardAdapter.CardViewHolder>() {

    /** What happens when something on a card is tapped. Implemented by the main screen. */
    interface Actions {
        fun open(addiction: Addiction)
        fun options(addiction: Addiction)
        fun priority(addiction: Addiction)
        fun relapse(addiction: Addiction)
        fun stop(addiction: Addiction)
        fun delete(addiction: Addiction)
        fun timeline(addiction: Addiction)
        fun notes(addiction: Addiction)
        fun savings(addiction: Addiction)
        fun milestones(addiction: Addiction)
    }

    companion object {
        const val PAYLOAD_TICK = "tick"
    }

    private var items: List<Addiction> = emptyList()

    @SuppressLint("NotifyDataSetChanged")
    fun submit(list: List<Addiction>) {
        items = list
        notifyDataSetChanged()
    }

    /** Refreshes the counters of all cards (called once per second). */
    fun tick() {
        if (items.isNotEmpty()) notifyItemRangeChanged(0, items.size, PAYLOAD_TICK)
    }

    override fun getItemCount() = items.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CardViewHolder =
        CardViewHolder(CardTrackerBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: CardViewHolder, position: Int) = holder.bind(items[position])

    override fun onBindViewHolder(holder: CardViewHolder, position: Int, payloads: MutableList<Any>) {
        if (payloads.contains(PAYLOAD_TICK)) holder.refresh(items[position])
        else holder.bind(items[position])
    }

    inner class CardViewHolder(private val b: CardTrackerBinding) : RecyclerView.ViewHolder(b.root) {

        private fun current(): Addiction? =
            adapterPosition.takeIf { it != RecyclerView.NO_POSITION }?.let { items.getOrNull(it) }

        init {
            fun click(view: View, action: (Addiction) -> Unit) =
                view.setOnClickListener { current()?.let(action) }
            click(b.card) { actions.open(it) }
            b.card.setOnLongClickListener { current()?.let { actions.options(it) }; true }
            click(b.btnOptions) { actions.options(it) }
            click(b.priority) { actions.priority(it) }
            click(b.btnRelapse) { actions.relapse(it) }
            click(b.btnStop) { actions.stop(it) }
            click(b.btnDelete) { actions.delete(it) }
            click(b.btnTimeline) { actions.timeline(it) }
            click(b.btnNotes) { actions.notes(it) }
            click(b.btnSavings) { actions.savings(it) }
            click(b.btnMilestones) { actions.milestones(it) }
        }

        /** Full bind: everything that does not change every second, then [refresh]. */
        fun bind(addiction: Addiction) {
            b.name.text = addiction.name
            val (label, color) = when (addiction.priority) {
                Addiction.Priority.HIGH -> R.string.high to R.color.red
                Addiction.Priority.MEDIUM -> R.string.medium to R.color.orange
                Addiction.Priority.LOW -> R.string.low to R.color.green
            }
            b.priority.text = context.getString(R.string.priority_dot, context.getString(label))
            b.priority.setTextColor(ResourcesCompat.getColor(context.resources, color, context.theme))

            // Buttons the user has hidden for this tracker (the relapse button is always shown).
            val hidden = addiction.display.hiddenButtons
            mapOf(
                CardButtons.TIMELINE to b.btnTimeline,
                CardButtons.NOTES to b.btnNotes,
                CardButtons.SAVINGS to b.btnSavings,
                CardButtons.MILESTONES to b.btnMilestones,
                CardButtons.STOP to b.btnStop,
                CardButtons.DELETE to b.btnDelete
            ).forEach { (key, view) -> view.visibility = if (key in hidden) View.GONE else View.VISIBLE }
            // A stopped or waiting tracker is started again with the same button.
            b.btnRelapse.setText(
                if (addiction.status == Addiction.Status.Ongoing) R.string.relapse else R.string.track_now
            )
            refresh(addiction)
        }

        /** Updates the parts that change over time: ring, counter, savings. */
        fun refresh(addiction: Addiction) {
            val now = System.currentTimeMillis()
            b.time.text = context.cardTimeText(addiction, now)

            val savings = context.savingsCardText(addiction.calculateSavings(now))
            b.savings.visibility = if (savings == null) View.GONE else View.VISIBLE
            b.savings.text = savings

            // Ring: number of whole days; it fills up towards the next milestone, or (without
            // milestones) through the current month.
            val start = addiction.history.keys.last()
            val end = when (addiction.status) {
                Addiction.Status.Stopped -> addiction.history.values.last()
                Addiction.Status.Future -> start
                Addiction.Status.Ongoing -> now
            }
            val elapsed = ElapsedTime.between(start, maxOf(start, end))
            val days = elapsed.totalDays.toInt()
            b.ringNumber.text = days.toString()
            b.ringLabel.text = context.resources.getQuantityString(R.plurals.days_label, days)
            val next = if (addiction.status == Addiction.Status.Ongoing) addiction.nextMilestone(now) else null
            b.ring.progress = if (next != null) {
                addiction.calculateMilestoneProgressionPercentage(next.first).second * 10
            } else {
                (1000.0 * elapsed.daysIntoCycle / elapsed.daysInCycle).toInt().coerceIn(0, 1000)
            }
        }
    }
}
