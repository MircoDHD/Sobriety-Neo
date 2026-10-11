package com.katiearose.sobriety.activities

import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat.CLOCK_24H
import com.katiearose.sobriety.R
import com.katiearose.sobriety.SavingsAdapter
import com.katiearose.sobriety.databinding.ActivitySavingsBinding
import com.katiearose.sobriety.databinding.DialogAddSavingBinding
import com.katiearose.sobriety.shared.Addiction
import com.katiearose.sobriety.shared.CacheHandler
import com.katiearose.sobriety.utils.applyThemes
import com.katiearose.sobriety.utils.isInputEmpty
import com.katiearose.sobriety.utils.loadAddictionFromIntent
import com.katiearose.sobriety.utils.showConfirmDialog
import com.katiearose.sobriety.utils.toDoubleLenient
import com.katiearose.sobriety.utils.toggleVisibility
import com.katiearose.sobriety.utils.write
import kotlinx.datetime.LocalTime

/**
 * "Savings" screen of one addiction: how much time and what else (money, cigarettes, ...) is
 * saved per day by not doing it. Opened from the "more" button on a card.
 */
class Savings : AppCompatActivity() {

    private lateinit var binding: ActivitySavingsBinding
    private lateinit var addiction: Addiction
    private lateinit var cacheHandler: CacheHandler
    private lateinit var adapter: SavingsAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        applyThemes()
        super.onCreate(savedInstanceState)
        binding = ActivitySavingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        cacheHandler = CacheHandler(this)

        addiction = loadAddictionFromIntent() ?: run {
            finish()
            return
        }
        updateSavedTimeDisplay()

        // Edit the "time saved per day" with a time picker (hours and minutes).
        binding.btnEditTime.setOnClickListener {
            with(MaterialTimePicker.Builder()
                .setTitleText(R.string.select_time_saved)
                .setTimeFormat(CLOCK_24H)
                .build()) {
                addOnPositiveButtonClickListener {
                    addiction.timeSaving = LocalTime(hour, minute)
                    cacheHandler.write()
                    updateSavedTimeDisplay()
                }
                show(supportFragmentManager, null)
            }
        }
        // Expand / collapse the time card (the icon, content description and tooltip follow).
        binding.btnExpandCollapseTime.setOnClickListener {
            binding.timeSavedCard.toggleVisibility()
            binding.btnExpandCollapseTime.apply {
                setImageResource(if (binding.timeSavedCard.visibility == View.VISIBLE) R.drawable.expand_less_24px else R.drawable.expand_more_24px)
                contentDescription =
                    if (binding.timeSavedCard.visibility == View.VISIBLE) getString(R.string.collapse) else getString(
                        R.string.expand
                    )
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                    tooltipText = if (binding.timeSavedCard.visibility == View.VISIBLE) getString(R.string.collapse) else getString(
                            R.string.expand
                        )
            }
        }

        binding.btnAddOther.setOnClickListener { showAddSavingDialog(null) }
        // Same expand / collapse behavior for the list of other savings.
        binding.btnExpandCollapseOther.setOnClickListener {
            binding.otherSavingsList.toggleVisibility()
            binding.btnExpandCollapseOther.apply {
                setImageResource(if (binding.otherSavingsList.visibility == View.VISIBLE) R.drawable.expand_less_24px else R.drawable.expand_more_24px)
                contentDescription = if (binding.otherSavingsList.visibility == View.VISIBLE) getString(R.string.collapse) else getString(
                        R.string.expand
                    )
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                    tooltipText = if (binding.otherSavingsList.visibility == View.VISIBLE) getString(R.string.collapse) else getString(
                            R.string.expand
                        )
            }
        }

        adapter = SavingsAdapter(addiction, this, { showAddSavingDialog(it) }, {
            val action: () -> Unit = {
                addiction.savings.remove(it.first)
                update()
            }
            showConfirmDialog(getString(R.string.delete), getString(R.string.delete_saving_confirm, it.first), action)
        })
        binding.otherSavingsList.layoutManager = LinearLayoutManager(this)
        binding.otherSavingsList.adapter = adapter
    }

    /**
     * Shows the bottom sheet for adding a new saving, or for changing one that already exists.
     *
     * @param existingSaving the saving to edit, or null to add a new one. When editing, the name
     *   can not be changed (it is the key in the savings map).
     */
    private fun showAddSavingDialog(existingSaving: Pair<String, Pair<Double, String>>?) {
        val dialogViewBinding = DialogAddSavingBinding.inflate(layoutInflater)
        val dialog = BottomSheetDialog(this)
        dialog.setContentView(dialogViewBinding.root)
        if (existingSaving != null) {
            dialogViewBinding.nameStr.visibility = View.GONE
            dialogViewBinding.savingsNameInputLayout.visibility = View.GONE
            dialogViewBinding.savingsAmountInput.setText(existingSaving.second.first.toString())
            dialogViewBinding.unitInput.setText(existingSaving.second.second)
        }
        dialogViewBinding.btnSaveSaving.setOnClickListener {
            val inputFields = listOf(dialogViewBinding.savingsAmountInputLayout, dialogViewBinding.unitInputLayout, dialogViewBinding.savingsNameInputLayout)
            // Accepts "4.50" as well as "4,50". The old code called toDouble() directly, which
            // crashed the app for a comma (the decimal separator of many keyboards/languages).
            val amount = dialogViewBinding.savingsAmountInput.text.toString().toDoubleLenient()
            // Each branch shows one error and clears the errors of the other fields.
            when {
                dialogViewBinding.savingsAmountInput.isInputEmpty() -> {
                    dialogViewBinding.savingsAmountInputLayout.error = getString(R.string.error_empty_amount)
                    inputFields.forEach { if (it !== dialogViewBinding.savingsAmountInputLayout) it.error = null }
                }
                amount == null -> {
                    dialogViewBinding.savingsAmountInputLayout.error = getString(R.string.error_invalid_amount)
                    inputFields.forEach { if (it !== dialogViewBinding.savingsAmountInputLayout) it.error = null }
                }
                dialogViewBinding.unitInput.isInputEmpty() -> {
                    dialogViewBinding.unitInputLayout.error = getString(R.string.error_empty_unit)
                    inputFields.forEach { if (it !== dialogViewBinding.unitInputLayout) it.error = null }
                }
                else -> {
                    if (existingSaving != null) {
                        addiction.savings[existingSaving.first] = Pair(
                            amount,
                            dialogViewBinding.unitInput.text.toString())
                        update()
                        dialog.dismiss()
                    } else {
                        if (dialogViewBinding.savingsNameInput.isInputEmpty()) {
                            dialogViewBinding.savingsNameInputLayout.error = getString(R.string.error_empty_name)
                            inputFields.forEach { if (it !== dialogViewBinding.savingsNameInputLayout) it.error = null }
                        } else {
                            addiction.savings[dialogViewBinding.savingsNameInput.text.toString()] =
                                Pair(
                                    amount,
                                    dialogViewBinding.unitInput.text.toString())
                            update()
                            dialog.dismiss()
                        }
                    }
                }
            }
        }
        dialog.show()
    }

    /** Saves the change and refreshes the list. */
    private fun update() {
        cacheHandler.write()
        adapter.update()
    }

    /** Shows the saved time per day as "X hours Y minutes", or "not set" if it is 00:00. */
    private fun updateSavedTimeDisplay() {
        binding.timeSaved.text =
            if (addiction.timeSaving.hour == 0 && addiction.timeSaving.minute == 0) getString(R.string.no_set)
            else StringBuilder(resources.getQuantityString(R.plurals.hours, addiction.timeSaving.hour, addiction.timeSaving.hour))
                .append(" ")
                .append(resources.getQuantityString(R.plurals.minutes, addiction.timeSaving.minute, addiction.timeSaving.minute))
                .apply { deleteAt(length - 1) } //delete the comma at the end
    }
}
