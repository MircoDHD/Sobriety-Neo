package com.katiearose.sobriety.activities

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.katiearose.sobriety.MilestoneAdapter
import com.katiearose.sobriety.R
import com.katiearose.sobriety.databinding.ActivityMilestonesBinding
import com.katiearose.sobriety.databinding.DialogAddMilestoneBinding
import com.katiearose.sobriety.shared.Addiction
import com.katiearose.sobriety.shared.CacheHandler
import com.katiearose.sobriety.utils.applyThemes
import com.katiearose.sobriety.utils.isInputEmpty
import com.katiearose.sobriety.utils.loadAddictionFromIntent
import com.katiearose.sobriety.utils.showConfirmDialog
import com.katiearose.sobriety.utils.write
import kotlinx.datetime.DateTimeUnit

/**
 * "Milestones" screen of one addiction: goals like "7 days" with a progress bar and the date on
 * which they will be reached. Opened from the "more" button on a card.
 */
class Milestones : AppCompatActivity() {

    private companion object {
        /**
         * Largest number accepted for a milestone ("9999 years" is still far more than a life).
         * Without a limit a huge number caused a crash (it did not fit into an Int, or the date
         * calculation overflowed).
         */
        const val MAX_AMOUNT = 9999
    }

    private lateinit var binding: ActivityMilestonesBinding
    private lateinit var addiction: Addiction
    private lateinit var cacheHandler: CacheHandler
    private lateinit var adapter: MilestoneAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        applyThemes()
        super.onCreate(savedInstanceState)
        binding = ActivityMilestonesBinding.inflate(layoutInflater)
        setContentView(binding.root)
        cacheHandler = CacheHandler(this)

        addiction = loadAddictionFromIntent() ?: run {
            finish()
            return
        }
        adapter = MilestoneAdapter(addiction, this) {
            val action: () -> Unit = {
                addiction.milestones.remove(it)
                cacheHandler.write()
                update()
            }
            showConfirmDialog(getString(R.string.delete), getString(R.string.delete_milestone_confirm), action)
        }
        binding.milestoneList.layoutManager = LinearLayoutManager(this)
        binding.milestoneList.setHasFixedSize(true)
        binding.milestoneList.adapter = adapter

        // The "+" button opens a bottom sheet with a number and a unit (hours ... years).
        binding.addMilestoneFab.setOnClickListener {
            val dialogViewBinding = DialogAddMilestoneBinding.inflate(layoutInflater)
            val dialog = BottomSheetDialog(this)
            dialog.setContentView(dialogViewBinding.root)
            val units = resources.getStringArray(R.array.time_units)
            dialogViewBinding.btnSaveMilestone.setOnClickListener {
                val inputFields = listOf(dialogViewBinding.milestoneNumberInputLayout, dialogViewBinding.milestoneTimeUnitInputLayout)
                // null if the text is empty or too big for an Int (the old toInt() crashed)
                val number = dialogViewBinding.milestoneNumberInput.text.toString().toIntOrNull()
                when {
                    dialogViewBinding.milestoneNumberInput.isInputEmpty() || number == 0 -> {
                        dialogViewBinding.milestoneNumberInputLayout.error = getString(R.string.error_empty_amount)
                        inputFields.forEach { if (it !== dialogViewBinding.milestoneNumberInputLayout) it.error = null }
                    }
                    number == null || number > MAX_AMOUNT -> {
                        dialogViewBinding.milestoneNumberInputLayout.error =
                            getString(R.string.error_amount_too_large, MAX_AMOUNT)
                        inputFields.forEach { if (it !== dialogViewBinding.milestoneNumberInputLayout) it.error = null }
                    }
                    dialogViewBinding.unitInput.text.toString().isEmpty() -> {
                        dialogViewBinding.milestoneTimeUnitInputLayout.error = getString(R.string.error_empty_unit)
                        inputFields.forEach { if (it !== dialogViewBinding.milestoneTimeUnitInputLayout) it.error = null }
                    }
                    else -> {
                        // The unit is chosen from a dropdown; its position matches R.array.time_units.
                        when (dialogViewBinding.unitInput.text.toString()) {
                            units[0] -> addiction.milestones.add(Pair(number, DateTimeUnit.HOUR))
                            units[1] -> addiction.milestones.add(Pair(number, DateTimeUnit.DAY))
                            units[2] -> addiction.milestones.add(Pair(number, DateTimeUnit.WEEK))
                            units[3] -> addiction.milestones.add(Pair(number, DateTimeUnit.MONTH))
                            units[4] -> addiction.milestones.add(Pair(number, DateTimeUnit.YEAR))
                        }
                        cacheHandler.write()
                        adapter.update()
                        dialog.dismiss()
                    }
                }
            }
            dialog.show()
        }
    }

    /** Saves the change and refreshes the list. */
    private fun update() {
        cacheHandler.write()
        adapter.update()
    }
}
