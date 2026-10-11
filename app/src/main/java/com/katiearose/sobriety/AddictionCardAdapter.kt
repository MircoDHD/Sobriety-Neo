package com.katiearose.sobriety

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat
import androidx.recyclerview.widget.RecyclerView
import com.katiearose.sobriety.activities.Main
import com.katiearose.sobriety.databinding.CardAddictionBinding
import com.katiearose.sobriety.shared.Addiction
import com.katiearose.sobriety.utils.convertRangeToString
import com.katiearose.sobriety.utils.convertSecondsToString
import com.katiearose.sobriety.utils.getDateFormatPattern
import com.katiearose.sobriety.utils.getAverageAttemptsWindow
import com.katiearose.sobriety.utils.getSharedPref
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Shows one card per addiction on the main screen, with the running counter.
 *
 * ## How the counter is updated
 * The counter text must be refreshed every second. The old version started an endless coroutine
 * for every card; these were never stopped, kept running after the screen was closed and kept the
 * old activity in memory (a memory and battery leak that got worse with every screen rotation).
 *
 * Now there is **one** timer, owned by the [Main] activity, which only runs while the screen is
 * visible. Every second it calls `notifyItemRangeChanged(0, count, PAYLOAD_TICK)`. Android then
 * calls [onBindViewHolder] with that payload and only the counter text is redrawn, without a
 * change animation.
 */
class AddictionCardAdapter(
    private val context: Context,
    private val deleteButtonAction: (Addiction) -> Unit,
    private val relapseButtonAction: (Addiction) -> Unit,
    private val stopButtonAction: (Addiction) -> Unit,
    private val timelineButtonAction: (Addiction) -> Unit,
    private val priorityTextViewAction: (Addiction) -> Unit,
    private val miscButtonAction: (Addiction) -> Unit,
    private val cardButtonAction: (Addiction) -> Unit
) :
    RecyclerView.Adapter<AddictionCardAdapter.AddictionCardViewHolder>() {

    companion object {
        /** Payload that means "only refresh the running counter text of this card". */
        const val PAYLOAD_TICK = "tick"
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): AddictionCardViewHolder {
        val binding =
            CardAddictionBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        // Each button gets the position of its card; it is turned into the Addiction here.
        return AddictionCardViewHolder(binding,
            { deleteButtonAction(Main.addictions[it]) },
            { relapseButtonAction(Main.addictions[it]) },
            { stopButtonAction(Main.addictions[it]) },
            { timelineButtonAction(Main.addictions[it]) },
            { priorityTextViewAction(Main.addictions[it]) },
            { miscButtonAction(Main.addictions[it]) },
            { cardButtonAction(Main.addictions[it]) },
            { Main.addictions[it] })
    }

    /** Full bind: name, priority, average and counter. */
    override fun onBindViewHolder(
        holder: AddictionCardViewHolder,
        position: Int
    ) {
        val stringBuilder = StringBuilder(context.getString(R.string.priority)).append(": ")
        val addiction = Main.addictions[position]

        holder.textViewName.text = addiction.name
        holder.textViewPriority.text = when (addiction.priority) {
            Addiction.Priority.HIGH -> stringBuilder.append(context.getString(R.string.high))
            Addiction.Priority.MEDIUM -> stringBuilder.append(context.getString(R.string.medium))
            Addiction.Priority.LOW -> stringBuilder.append(context.getString(R.string.low))
        }
        holder.textViewPriority.setTextColor(
            when (addiction.priority) {
                Addiction.Priority.HIGH -> ResourcesCompat.getColor(
                    context.resources,
                    R.color.red,
                    context.theme
                )
                Addiction.Priority.MEDIUM -> ResourcesCompat.getColor(
                    context.resources,
                    R.color.orange,
                    context.theme
                )
                Addiction.Priority.LOW -> ResourcesCompat.getColor(
                    context.resources,
                    R.color.green,
                    context.theme
                )
            }
        )
        // Average length of the last N finished attempts (N is a setting). Hidden if there is
        // nothing to average yet.
        val numAttempts = context.getSharedPref().getAverageAttemptsWindow()
        val averageRelapseDuration = addiction.calculateRecentAverage(numAttempts)
        if (averageRelapseDuration == null) {
            holder.textViewAverage.visibility = View.GONE
        } else {
            holder.textViewAverage.visibility = View.VISIBLE
            holder.textViewAverage.text =
                context.getString(
                    R.string.recent_avg,
                    context.convertSecondsToString(averageRelapseDuration / 1000L)
                )
        }

        holder.refresh()
    }

    /** Partial bind: with [PAYLOAD_TICK] only the counter text is updated (called every second). */
    override fun onBindViewHolder(
        holder: AddictionCardViewHolder,
        position: Int,
        payloads: MutableList<Any>
    ) {
        if (payloads.contains(PAYLOAD_TICK)) holder.refresh()
        else super.onBindViewHolder(holder, position, payloads)
    }

    override fun getItemCount() = Main.addictions.size

    class AddictionCardViewHolder(
        private val binding: CardAddictionBinding, deleteButtonAction: (Int) -> Unit,
        relapseButtonAction: (Int) -> Unit,
        stopButtonAction: (Int) -> Unit,
        timelineButtonAction: (Int) -> Unit,
        priorityTextViewAction: (Int) -> Unit,
        miscButtonAction: (Int) -> Unit,
        cardButtonAction: (Int) -> Unit,
        private val addictionSupplier: (Int) -> Addiction) : RecyclerView.ViewHolder(binding.root) {
        val textViewName: TextView = binding.textViewAddictionName
        val textViewPriority: TextView = binding.textViewPriority
        val textViewAverage: TextView = binding.textViewAverage
        private lateinit var addiction: Addiction

        /** Re-reads the addiction of this card and redraws the counter text. */
        fun refresh() {
            // The card can be in the middle of being removed; there is nothing to show then.
            val position = adapterPosition
            if (position == RecyclerView.NO_POSITION) return
            addiction = addictionSupplier(position)
            displayInfo()
        }

        private fun getFormattedDate(milli: Long): String {
            val dateFormat = DateTimeFormatter.ofPattern(binding.root.context.getSharedPref().getDateFormatPattern())
            val date = Instant.ofEpochMilli(milli).atZone(ZoneId.systemDefault())
            return dateFormat.format(date)
        }

        /** Sets the big counter text, which depends on the status of the addiction. */
        private fun displayInfo() {
            binding.textViewTime.text = when (addiction.status) {
                // Running: time since the last relapse.
                Addiction.Status.Ongoing -> binding.root.context.convertRangeToString(addiction.history.keys.last())

                // Stopped: the counter is frozen at the time it was stopped.
                Addiction.Status.Stopped -> binding.root.context.getString(
                    R.string.stop_notice,
                    getFormattedDate(addiction.history.values.last()),
                    binding.root.context.convertRangeToString(addiction.history.keys.last(), addiction.history.values.last())
                )

                // Waiting: time left until the start.
                Addiction.Status.Future -> binding.root.context.getString(
                    R.string.time_until_tracked,
                    binding.root.context.convertRangeToString(System.currentTimeMillis(), addiction.history.keys.last())
                )
            }
        }

        /**
         * Runs a button action with the current position of the card. If the position is not
         * known (card is being removed or the list is being changed) the click is ignored instead
         * of crashing with an index of -1.
         */
        private fun withPosition(action: (Int) -> Unit) {
            val position = adapterPosition
            if (position != RecyclerView.NO_POSITION) action(position)
        }

        init {
            binding.imageDelete.setOnClickListener { withPosition(deleteButtonAction) }
            binding.imageReset.setOnClickListener { withPosition(relapseButtonAction) }
            binding.imageStop.setOnClickListener { withPosition(stopButtonAction) }
            binding.imageTimeline.setOnClickListener { withPosition(timelineButtonAction) }
            textViewPriority.setOnClickListener { withPosition(priorityTextViewAction) }
            binding.imageMisc.setOnClickListener { withPosition(miscButtonAction) }
            binding.cardViewAddiction.setOnClickListener { withPosition(cardButtonAction) }
        }
    }
}
