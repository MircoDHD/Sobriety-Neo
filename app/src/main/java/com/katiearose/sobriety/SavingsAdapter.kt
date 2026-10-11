package com.katiearose.sobriety

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.ViewHolder
import com.katiearose.sobriety.databinding.ListItemSavingBinding
import com.katiearose.sobriety.shared.Addiction
import com.katiearose.sobriety.shared.RatePeriod
import com.katiearose.sobriety.shared.SavingCategory
import com.katiearose.sobriety.utils.formatAmount

/**
 * List of the "other savings" of one addiction (name and amount saved per day), with buttons to
 * edit and delete each one.
 */
class SavingsAdapter(private val addiction: Addiction, private val context: Context,
                     private val editButtonAction: (Pair<String, Pair<Double, String>>) -> Unit,
                     private val deleteButtonAction: (Pair<String, Pair<Double, String>>) -> Unit):
    ListAdapter<Pair<String, Pair<Double, String>>, SavingsAdapter.SavingsViewHolder>(object :
        DiffUtil.ItemCallback<Pair<String, Pair<Double, String>>>() {
        override fun areItemsTheSame(
            oldItem: Pair<String, Pair<Double, String>>,
            newItem: Pair<String, Pair<Double, String>>
        ): Boolean = oldItem.first == newItem.first

        override fun areContentsTheSame(
            oldItem: Pair<String, Pair<Double, String>>,
            newItem: Pair<String, Pair<Double, String>>
        ): Boolean = oldItem.second == newItem.second
    }) {
    init { update() }

    /**
     * Redraws the list from [Addiction.savings]. notifyDataSetChanged is needed in addition,
     * because a changed category or period is not part of the compared list items.
     */
    @android.annotation.SuppressLint("NotifyDataSetChanged")
    fun update() = submitList(addiction.savings.toList()) { notifyDataSetChanged() }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SavingsViewHolder {
        val binding = ListItemSavingBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return SavingsViewHolder(binding,
            { editButtonAction(currentList[it]) },
            { deleteButtonAction(currentList[it]) })
    }

    override fun onBindViewHolder(holder: SavingsViewHolder, position: Int) {
        val pair = currentList[position]
        holder.savingName.text = pair.first
        // Sobriety Neo: show the amount the way it was entered ("20.00 € per week · Money").
        val meta = addiction.savingsMeta[pair.first]
        val period = meta?.period ?: RatePeriod.DAY
        val amount = meta?.enteredAmount ?: pair.second.first
        val periodText = context.resources.getStringArray(R.array.rate_periods)[period.ordinal]
        val categoryText = context.resources.getStringArray(R.array.saving_categories)[(meta?.category ?: SavingCategory.CUSTOM).ordinal]
        holder.savingsAmountPerDay.text = context.getString(
            R.string.saving_rate_line, formatAmount(amount, pair.second.second), periodText, categoryText
        )
    }

    class SavingsViewHolder(binding: ListItemSavingBinding, editButtonAction: (Int) -> Unit,
                            deleteButtonAction: (Int) -> Unit
    ): ViewHolder(binding.root) {
        val savingName = binding.savingsName
        val savingsAmountPerDay = binding.savingsAmountPerDay
        init {
            // Ignore taps while the position is unknown (row is being removed) instead of crashing.
            binding.btnEditSaving.setOnClickListener {
                if (adapterPosition != RecyclerView.NO_POSITION) editButtonAction(adapterPosition)
            }
            binding.btnDeleteSaving.setOnClickListener {
                if (adapterPosition != RecyclerView.NO_POSITION) deleteButtonAction(adapterPosition)
            }
        }
    }
}