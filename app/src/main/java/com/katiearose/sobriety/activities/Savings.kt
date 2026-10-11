package com.katiearose.sobriety.activities

import android.os.Build
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.textfield.TextInputLayout
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat.CLOCK_24H
import com.katiearose.sobriety.R
import com.katiearose.sobriety.SavingsAdapter
import com.katiearose.sobriety.databinding.ActivitySavingsBinding
import com.katiearose.sobriety.databinding.DialogAddSavingBinding
import com.katiearose.sobriety.shared.Addiction
import com.katiearose.sobriety.shared.CacheHandler
import com.katiearose.sobriety.shared.RatePeriod
import com.katiearose.sobriety.shared.SavingCategory
import com.katiearose.sobriety.shared.SavingMeta
import com.katiearose.sobriety.shared.calculateSavings
import com.katiearose.sobriety.shared.primaryCurrency
import com.katiearose.sobriety.utils.handleInfoMenuItem
import com.katiearose.sobriety.utils.savingsDetailText
import com.katiearose.sobriety.utils.setInfoClick
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
                // Open at the value that is currently set.
                .setHour(addiction.timeSaving.hour)
                .setMinute(addiction.timeSaving.minute)
                .setTimeFormat(CLOCK_24H)
                .build()) {
                addOnPositiveButtonClickListener {
                    addiction.timeSaving = LocalTime(hour, minute)
                    cacheHandler.write()
                    updateSavedTimeDisplay()
                    updateTotals()
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
                addiction.savingsMeta.remove(it.first)
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
     * Sobriety Neo: the user also chooses a category (money or other) and whether the amount is
     * per day, per week or per month. [Addiction.savings] still stores the amount per day (as in
     * the original app); category, period and the amount as typed are stored in
     * [Addiction.savingsMeta].
     *
     * @param existingSaving the saving to edit, or null to add a new one. When editing, the name
     *   can not be changed (it is the key in the savings map).
     */
    private fun showAddSavingDialog(existingSaving: Pair<String, Pair<Double, String>>?) {
        val dialogViewBinding = DialogAddSavingBinding.inflate(layoutInflater)
        val dialog = BottomSheetDialog(this)
        dialog.setContentView(dialogViewBinding.root)
        dialogViewBinding.btnInfo.setInfoClick(R.string.add_new_saving, R.string.info_add_saving)

        val categories = SavingCategory.values()
        val periods = RatePeriod.values()
        val categoryNames = resources.getStringArray(R.array.saving_categories)
        val periodNames = resources.getStringArray(R.array.rate_periods)
        // The chosen dropdown entries. They are tracked by index instead of reading the text back.
        var category = SavingCategory.MONEY
        var period = RatePeriod.DAY

        fun updateUnitHint() {
            dialogViewBinding.unitInputLayout.hint = getString(
                if (category == SavingCategory.MONEY) R.string.saving_currency_hint else R.string.saving_unit_hint
            )
        }

        if (existingSaving != null) {
            dialogViewBinding.nameStr.visibility = View.GONE
            dialogViewBinding.savingsNameInputLayout.visibility = View.GONE
            val meta = addiction.savingsMeta[existingSaving.first]
            category = meta?.category ?: SavingCategory.CUSTOM
            period = meta?.period ?: RatePeriod.DAY
            val entered = meta?.enteredAmount ?: existingSaving.second.first
            dialogViewBinding.savingsAmountInput.setText(entered.toString())
            dialogViewBinding.unitInput.setText(existingSaving.second.second)
        }
        // "false": do not filter the dropdown list by the text that is set.
        dialogViewBinding.savingsCategoryInput.setText(categoryNames[category.ordinal], false)
        dialogViewBinding.savingsPeriodInput.setText(periodNames[period.ordinal], false)
        dialogViewBinding.savingsCategoryInput.setOnItemClickListener { _, _, position, _ ->
            category = categories[position]
            updateUnitHint()
        }
        dialogViewBinding.savingsPeriodInput.setOnItemClickListener { _, _, position, _ ->
            period = periods[position]
        }
        updateUnitHint()

        dialogViewBinding.btnSaveSaving.setOnClickListener {
            val inputFields = listOf(dialogViewBinding.savingsAmountInputLayout, dialogViewBinding.unitInputLayout, dialogViewBinding.savingsNameInputLayout)
            // Accepts "4.50" as well as "4,50". The old code called toDouble() directly, which
            // crashed the app for a comma (the decimal separator of many keyboards/languages).
            val amount = dialogViewBinding.savingsAmountInput.text.toString().toDoubleLenient()
            fun showError(layout: TextInputLayout, message: String) {
                layout.error = message
                inputFields.forEach { if (it !== layout) it.error = null }
            }
            // Each branch shows one error and clears the errors of the other fields.
            when {
                dialogViewBinding.savingsAmountInput.isInputEmpty() ->
                    showError(dialogViewBinding.savingsAmountInputLayout, getString(R.string.error_empty_amount))
                amount == null || amount < 0 ->
                    showError(dialogViewBinding.savingsAmountInputLayout, getString(R.string.error_invalid_amount))
                dialogViewBinding.unitInput.isInputEmpty() ->
                    showError(dialogViewBinding.unitInputLayout, getString(R.string.error_empty_unit))
                existingSaving == null && dialogViewBinding.savingsNameInput.isInputEmpty() ->
                    showError(dialogViewBinding.savingsNameInputLayout, getString(R.string.error_empty_name))
                else -> {
                    val name = existingSaving?.first ?: dialogViewBinding.savingsNameInput.text.toString().trim()
                    val unit = dialogViewBinding.unitInput.text.toString().trim()
                    addiction.savings[name] = Pair(amount / period.days, unit)
                    addiction.savingsMeta[name] = SavingMeta(category, period, amount)
                    update()
                    dialog.dismiss()
                }
            }
        }
        dialog.show()
    }

    /** Saves the change and refreshes the list and the totals. */
    private fun update() {
        cacheHandler.write()
        adapter.update()
        updateTotals()
    }

    /** Recalculates the totals card at the top (money, other savings, time, spent on relapses). */
    private fun updateTotals() {
        binding.totalsText.text = savingsDetailText(addiction.calculateSavings(), addiction.primaryCurrency)
    }

    override fun onResume() {
        super.onResume()
        // The totals grow over time; refresh them whenever the screen is shown again.
        updateTotals()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_info, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean =
        handleInfoMenuItem(item.itemId, R.string.savings, R.string.info_savings) || super.onOptionsItemSelected(item)

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
