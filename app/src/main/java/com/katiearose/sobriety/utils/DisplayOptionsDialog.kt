package com.katiearose.sobriety.utils

import android.app.Activity
import android.widget.LinearLayout
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.katiearose.sobriety.R
import com.katiearose.sobriety.databinding.DialogDisplayOptionsBinding
import com.katiearose.sobriety.shared.Addiction
import com.katiearose.sobriety.shared.CounterMode
import com.katiearose.sobriety.shared.DisplaySettings
import com.katiearose.sobriety.shared.TimeUnitLevel

/**
 * Shows the "Display options" bottom sheet of one tracker (both UI variants):
 *  - what the card shows: counter, countdown to the next milestone, or both,
 *  - how many units are shown at most,
 *  - the smallest unit shown.
 * A live preview shows the result. The time itself is always tracked exactly.
 *
 * @param addExtras lets the caller add more options to the sheet (the new UI adds the visible
 *   buttons and the tabs); it returns a function that is called when "Save" is pressed and that
 *   returns the settings with its changes applied
 * @param onSaved called after the settings were stored in [addiction] (the caller saves to disk)
 */
fun Activity.showDisplayOptionsDialog(
    addiction: Addiction,
    addExtras: ((container: LinearLayout, current: DisplaySettings) -> ((DisplaySettings) -> DisplaySettings))? = null,
    onSaved: () -> Unit
) {
    val sheet = DialogDisplayOptionsBinding.inflate(layoutInflater)
    val dialog = BottomSheetDialog(this)
    dialog.setContentView(sheet.root)
    sheet.optionsTitle.text = getString(R.string.display_options_for, addiction.name)
    sheet.btnInfo.setInfoClick(R.string.display_options, R.string.info_display_options)

    var settings = addiction.display
    val modes = CounterMode.values()
    val levels = TimeUnitLevel.values()
    val modeNames = resources.getStringArray(R.array.counter_modes)
    val maxUnitNames = resources.getStringArray(R.array.max_units) // index 0 = all, 1..n = n units
    val levelNames = resources.getStringArray(R.array.time_unit_levels)

    fun updatePreview() {
        val start = addiction.history.keys.last()
        val end = if (addiction.status == Addiction.Status.Stopped) addiction.history.values.last()
        else System.currentTimeMillis()
        sheet.preview.text = getString(
            R.string.display_preview,
            if (start <= end) convertRangeToString(start, end, settings) else convertRangeToString(end, start, settings)
        )
    }

    // "false" = do not filter the dropdown by the text that is set.
    sheet.modeInput.setText(modeNames[settings.mode.ordinal], false)
    sheet.maxUnitsInput.setText(maxUnitNames[settings.maxUnits.coerceIn(0, maxUnitNames.size - 1)], false)
    sheet.smallestUnitInput.setText(levelNames[settings.smallestUnit.ordinal], false)
    sheet.modeInput.setOnItemClickListener { _, _, position, _ ->
        settings = settings.copy(mode = modes[position]); updatePreview()
    }
    sheet.maxUnitsInput.setOnItemClickListener { _, _, position, _ ->
        settings = settings.copy(maxUnits = position); updatePreview()
    }
    sheet.smallestUnitInput.setOnItemClickListener { _, _, position, _ ->
        settings = settings.copy(smallestUnit = levels[position]); updatePreview()
    }
    updatePreview()

    val applyExtras = addExtras?.invoke(sheet.extraOptions, settings)

    sheet.btnSave.setOnClickListener {
        addiction.display = applyExtras?.invoke(settings) ?: settings
        onSaved()
        dialog.dismiss()
    }
    dialog.show()
}
