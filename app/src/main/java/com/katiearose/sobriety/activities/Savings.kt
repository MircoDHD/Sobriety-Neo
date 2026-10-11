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
import com.katiearose.sobriety.R
import com.katiearose.sobriety.SavingsAdapter
import com.katiearose.sobriety.databinding.ActivitySavingsBinding
import com.katiearose.sobriety.databinding.DialogAddSavingBinding
import com.katiearose.sobriety.shared.Addiction
import com.katiearose.sobriety.shared.CacheHandler
import com.katiearose.sobriety.shared.RatePeriod
import com.katiearose.sobriety.shared.SavingCategory
import com.katiearose.sobriety.shared.SavingMeta
import com.katiearose.sobriety.shared.TIME_SAVING_UNIT
import com.katiearose.sobriety.shared.calculateSavings
import com.katiearose.sobriety.shared.primaryCurrency
import com.katiearose.sobriety.utils.applyThemes
import com.katiearose.sobriety.utils.handleInfoMenuItem
import com.katiearose.sobriety.utils.isInputEmpty
import com.katiearose.sobriety.utils.loadAddictionFromIntent
import com.katiearose.sobriety.utils.savingsDetailText
import com.katiearose.sobriety.utils.setInfoClick
import com.katiearose.sobriety.utils.showConfirmDialog
import com.katiearose.sobriety.utils.toDoubleLenient
import com.katiearose.sobriety.utils.toggleVisibility
import com.katiearose.sobriety.utils.write

/**
 * "Savings" screen of one addiction: what is saved by not doing it (money, time, cigarettes, ...)
 * and how much has been saved in total. Opened from the "more" button on a card.
 *
 * Sobriety Neo:
 *  - a totals card at the top (see SavingsCalculator.kt in the shared module),
 *  - every saving has a category: money, time or other. Time used to be a separate field
 *    ("time saved per day"); it is now a category like the others and old values are moved into
 *    a "Time" saving when the data is loaded (Trackers.migrate),
 *  - the name is optional; without a name the category name is used ("Money", "Time").
 */
class Savings : AppCompatActivity() {

    private lateinit var binding: ActivitySavingsBinding
    private lateinit var addiction: Addiction
    private lateinit var cacheHandler: CacheHandler
    private lateinit var adapter: SavingsAdapter

    /** Order of the categories in the dropdown (must match R.array.saving_categories). */
    private val categoryOrder = listOf(SavingCategory.MONEY, SavingCategory.TIME, SavingCategory.CUSTOM)

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

        binding.btnAddOther.setOnClickListener { showAddSavingDialog(null) }
        // Expand / collapse the list (the icon, content description and tooltip follow).
        binding.btnExpandCollapseOther.setOnClickListener {
            binding.otherSavingsList.toggleVisibility()
            val visible = binding.otherSavingsList.visibility == View.VISIBLE
            binding.btnExpandCollapseOther.apply {
                setImageResource(if (visible) R.drawable.expand_less_24px else R.drawable.expand_more_24px)
                contentDescription = getString(if (visible) R.string.collapse else R.string.expand)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                    tooltipText = getString(if (visible) R.string.collapse else R.string.expand)
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

    /** A name that is not used yet: "Money", "Money (2)", ... */
    private fun uniqueName(base: String): String {
        var name = base
        var counter = 2
        while (name in addiction.savings) name = "$base (${counter++})"
        return name
    }

    /**
     * Shows the bottom sheet for adding a new saving, or for changing one that already exists.
     *
     * [Addiction.savings] stores the amount per day (as in the original app); category, period
     * and the amount as typed are stored in [Addiction.savingsMeta].
     *
     * @param existingSaving the saving to edit, or null to add a new one. When editing, the name
     *   can not be changed (it is the key in the savings map).
     */
    private fun showAddSavingDialog(existingSaving: Pair<String, Pair<Double, String>>?) {
        val sheet = DialogAddSavingBinding.inflate(layoutInflater)
        val dialog = BottomSheetDialog(this)
        dialog.setContentView(sheet.root)
        sheet.btnInfo.setInfoClick(R.string.add_new_saving, R.string.info_add_saving)

        val periods = RatePeriod.values()
        val categoryNames = resources.getStringArray(R.array.saving_categories)
        val periodNames = resources.getStringArray(R.array.rate_periods)
        // The chosen dropdown entries are tracked by index instead of reading the text back.
        var category = SavingCategory.MONEY
        var period = RatePeriod.DAY

        // The fields depend on the category: time needs no unit (always hours), money asks for a
        // currency, other things for a unit.
        fun updateFields() {
            sheet.unitInputLayout.visibility = if (category == SavingCategory.TIME) View.GONE else View.VISIBLE
            sheet.unitInputLayout.hint = getString(
                if (category == SavingCategory.MONEY) R.string.saving_currency_hint else R.string.saving_unit_hint
            )
            sheet.savingsAmountInputLayout.hint = getString(
                if (category == SavingCategory.TIME) R.string.saving_hours_hint else R.string.amount_saved_short
            )
            sheet.savingsNameInputLayout.hint = getString(
                if (category == SavingCategory.CUSTOM) R.string.add_new_saving_hint else R.string.saving_name_optional
            )
        }

        if (existingSaving != null) {
            sheet.nameStr.visibility = View.INVISIBLE
            sheet.savingsNameInputLayout.visibility = View.GONE
            val meta = addiction.savingsMeta[existingSaving.first]
            category = meta?.category ?: SavingCategory.CUSTOM
            period = meta?.period ?: RatePeriod.DAY
            val entered = meta?.enteredAmount ?: existingSaving.second.first
            sheet.savingsAmountInput.setText(entered.toString())
            sheet.unitInput.setText(existingSaving.second.second)
        } else {
            // Suggest the currency of the other money savings.
            sheet.unitInput.setText(addiction.primaryCurrency)
        }
        // "false": do not filter the dropdown list by the text that is set.
        sheet.savingsCategoryInput.setText(categoryNames[categoryOrder.indexOf(category)], false)
        sheet.savingsPeriodInput.setText(periodNames[period.ordinal], false)
        sheet.savingsCategoryInput.setOnItemClickListener { _, _, position, _ ->
            category = categoryOrder[position]
            updateFields()
        }
        sheet.savingsPeriodInput.setOnItemClickListener { _, _, position, _ -> period = periods[position] }
        updateFields()

        sheet.btnSaveSaving.setOnClickListener {
            val inputFields = listOf(sheet.savingsAmountInputLayout, sheet.unitInputLayout, sheet.savingsNameInputLayout)
            // Accepts "4.50" as well as "4,50" (the old toDouble() crashed for a comma).
            val amount = sheet.savingsAmountInput.text.toString().toDoubleLenient()
            fun showError(layout: TextInputLayout, message: String) {
                layout.error = message
                inputFields.forEach { if (it !== layout) it.error = null }
            }
            val needsUnit = category != SavingCategory.TIME
            // Only "other" savings need a name; money and time fall back to the category name.
            val needsName = existingSaving == null && category == SavingCategory.CUSTOM
            when {
                sheet.savingsAmountInput.isInputEmpty() ->
                    showError(sheet.savingsAmountInputLayout, getString(R.string.error_empty_amount))
                amount == null || amount < 0 ->
                    showError(sheet.savingsAmountInputLayout, getString(R.string.error_invalid_amount))
                needsUnit && sheet.unitInput.isInputEmpty() ->
                    showError(sheet.unitInputLayout, getString(R.string.error_empty_unit))
                needsName && sheet.savingsNameInput.isInputEmpty() ->
                    showError(sheet.savingsNameInputLayout, getString(R.string.error_empty_name))
                else -> {
                    val typedName = sheet.savingsNameInput.text.toString().trim()
                    val name = existingSaving?.first ?: when {
                        typedName.isNotEmpty() -> uniqueName(typedName)
                        else -> uniqueName(categoryNames[categoryOrder.indexOf(category)])
                    }
                    val unit = if (needsUnit) sheet.unitInput.text.toString().trim() else TIME_SAVING_UNIT
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

    /** Recalculates the totals card at the top (money, time, other savings, spent on relapses). */
    private fun updateTotals() {
        binding.totalsText.text = savingsDetailText(addiction.calculateSavings(), addiction.primaryCurrency)
    }

    override fun onResume() {
        super.onResume()
        // The totals grow over time; refresh them whenever the screen is shown again.
        if (::addiction.isInitialized) updateTotals()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_info, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean =
        handleInfoMenuItem(item.itemId, R.string.savings, R.string.info_savings) || super.onOptionsItemSelected(item)
}
