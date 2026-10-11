package com.katiearose.sobriety.utils

import android.app.Dialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.katiearose.sobriety.R
import com.katiearose.sobriety.databinding.DialogAddNoteAfterRelapseBinding
import com.katiearose.sobriety.databinding.DialogRelapseBinding
import com.katiearose.sobriety.shared.Addiction
import com.katiearose.sobriety.shared.CacheHandler
import com.katiearose.sobriety.shared.RelapseCost
import com.katiearose.sobriety.shared.primaryCurrency
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/*
 * Dialogs used by both main screens (new UI and classic UI) when a relapse is logged.
 */

/** Upper limits for the "time spent" fields of the relapse dialog. */
private const val MAX_RELAPSE_HOURS = 999L
private const val MAX_RELAPSE_MINUTES = 59999L

/**
 * Bottom sheet for logging a relapse, with optional fields for the money and time spent.
 * Empty fields count as 0. The costs are stored with the relapse and subtracted from the
 * savings (see SavingsCalculator.kt).
 */
fun AppCompatActivity.showRelapseSheet(addiction: Addiction, onLogged: () -> Unit) {
    val sheet = DialogRelapseBinding.inflate(layoutInflater)
    val dialog = BottomSheetDialog(this)
    dialog.setContentView(sheet.root)
    sheet.relapseQuestion.text = getString(R.string.relapse_confirm, addiction.name)
    sheet.btnInfo.setInfoClick(R.string.relapse, R.string.info_relapse)
    // Show the currency of the money savings in the hint, e.g. "Money spent (€)".
    val currency = addiction.primaryCurrency
    if (currency.isNotEmpty())
        sheet.moneyInputLayout.hint = getString(R.string.relapse_money_spent_with_currency, currency)
    sheet.btnCancel.setOnClickListener { dialog.dismiss() }
    sheet.btnLogRelapse.setOnClickListener {
        val moneyText = sheet.moneyInput.text.toString()
        val money = if (moneyText.isBlank()) 0.0 else moneyText.toDoubleLenient()
        val hours = sheet.hoursInput.text.toString().trim().let { t -> if (t.isEmpty()) 0L else t.toLongOrNull() }
        val minutes = sheet.minutesInput.text.toString().trim().let { t -> if (t.isEmpty()) 0L else t.toLongOrNull() }
        // Check the inputs; nothing is saved while one of them is invalid.
        sheet.moneyInputLayout.error =
            if (money == null || money < 0) getString(R.string.error_invalid_amount) else null
        sheet.hoursInputLayout.error =
            if (hours == null || hours !in 0..MAX_RELAPSE_HOURS) getString(R.string.error_amount_too_large, MAX_RELAPSE_HOURS) else null
        sheet.minutesInputLayout.error =
            if (minutes == null || minutes !in 0..MAX_RELAPSE_MINUTES) getString(R.string.error_amount_too_large, MAX_RELAPSE_MINUTES) else null
        if (money == null || money < 0 || hours == null || hours !in 0..MAX_RELAPSE_HOURS ||
            minutes == null || minutes !in 0..MAX_RELAPSE_MINUTES) return@setOnClickListener

        addiction.relapse(RelapseCost(System.currentTimeMillis(), money, hours * 60 + minutes))
        DiagnosticsLog.event("relapse_logged", "with_costs" to (money > 0 || hours > 0 || minutes > 0))
        CacheHandler(this).write()
        onLogged()
        dialog.dismiss()
        showNoteAfterRelapseIfEnabled(addiction)
    }
    dialog.show()
}

/**
 * Asks the user whether to write a note about the relapse (can be turned off in the
 * settings or with the "don't show again" checkbox). The note is saved for today's date.
 */
fun AppCompatActivity.showNoteAfterRelapseIfEnabled(addiction: Addiction) {
    val pref = getSharedPref()
    if (pref.getAddNoteAfterRelapsePref()) {
        var dialog: Dialog? = null
        val dialogViewBinding = DialogAddNoteAfterRelapseBinding.inflate(layoutInflater)
        val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
        with(dialogViewBinding) {
            noteInput.setText(addiction.dailyNotes[today]) //TextView.setText does accept a null CharSequence.
            btnSave.setOnClickListener { _ ->
                if (noteInput.isInputEmpty()) {
                    noteInputLayout.error = getString(R.string.error_empty_note)
                } else {
                    addiction.dailyNotes[today] = noteInput.text.toString()
                    CacheHandler(this@showNoteAfterRelapseIfEnabled).write()
                    requireNotNull(dialog).dismiss()
                }
            }
        }
        with(BottomSheetDialog(this)) {
            dialog = this
            setContentView(dialogViewBinding.root)
            setOnDismissListener {
                if (dialogViewBinding.dontShowAgain.isChecked) {
                    pref.edit { putBoolean("add_note_after_relapse", false) }
                }
            }
            show()
        }
    }
}
