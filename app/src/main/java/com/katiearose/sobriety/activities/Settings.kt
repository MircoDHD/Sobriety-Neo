package com.katiearose.sobriety.activities

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.*
import com.katiearose.sobriety.BuildConfig
import com.katiearose.sobriety.R
import com.katiearose.sobriety.shared.Addiction
import com.katiearose.sobriety.shared.CacheHandler
import com.katiearose.sobriety.utils.MAX_AVERAGE_ATTEMPTS_WINDOW
import com.katiearose.sobriety.utils.applyThemes
import com.katiearose.sobriety.utils.DiagnosticsLog
import com.katiearose.sobriety.utils.DefaultMilestones
import com.katiearose.sobriety.utils.Trackers
import com.katiearose.sobriety.utils.milestoneText
import com.katiearose.sobriety.utils.showInfoHtml
import com.katiearose.sobriety.databinding.DialogAddMilestoneBinding
import com.katiearose.sobriety.utils.handleInfoMenuItem
import com.katiearose.sobriety.utils.showInfo
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.time.LocalDate
import java.time.Month
import java.time.format.DateTimeFormatter

/**
 * Settings screen. The list of settings itself is declared in `res/xml/root_preferences.xml`;
 * this class only adds the behavior that XML can not express (theme switching, data export and
 * import, input checks).
 */
class Settings : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        applyThemes()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.settings_activity)
        if (savedInstanceState == null) {
            supportFragmentManager
                .beginTransaction()
                .replace(R.id.settings, SettingsFragment())
                .commit()
        }
    }

    // "i" button in the top bar (Sobriety Neo), see utils/InfoUtils.kt.
    override fun onCreateOptionsMenu(menu: android.view.Menu): Boolean {
        menuInflater.inflate(R.menu.menu_info, menu)
        return true
    }

    override fun onOptionsItemSelected(item: android.view.MenuItem): Boolean =
        if (item.itemId == R.id.info) {
            // The settings explanation is formatted (bold names), so it is HTML.
            showInfoHtml(R.string.settings, R.string.info_settings_html)
            true
        } else super.onOptionsItemSelected(item)

    class SettingsFragment : PreferenceFragmentCompat() {

        companion object {
            // Fixed date used to show what each date format looks like in the format list.
            private val sampleDate = LocalDate.of(2023, Month.JANUARY, 1)
        }

        private lateinit var cacheHandler: CacheHandler

        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.root_preferences, rootKey)
            val themePref = requireNotNull(findPreference<ListPreference>("theme")) { "Wrong key passed for theme preference" }
            // Android 9 and older have no system-wide dark mode, so they get a shorter list.
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
                themePref.setEntries(R.array.theme_entries_p)
                themePref.setEntryValues(R.array.theme_entry_values_p)
            }
            cacheHandler = CacheHandler(requireContext())
            themePref.setOnPreferenceChangeListener { _, _ ->
                requireActivity().recreate()
                true
            }
            val materialYouPref = requireNotNull(findPreference<SwitchPreferenceCompat>("material_you")) { "Wrong key passed for M3 preference" }
            materialYouPref.setOnPreferenceChangeListener { _, _ ->
                requireActivity().recreate()
                true
            }
            val dateFormatPref = requireNotNull(findPreference<ListPreference>("date_format")) { "Wrong key passed for date format preference" }
            // Show an example date next to each pattern so people do not have to read "MMMM dd uuuu".
            dateFormatPref.entries = dateFormatPref.entryValues.map {
                DateTimeFormatter.ofPattern(it.toString()).format(sampleDate)
            }.toTypedArray()

            val averageAttemptsWindow = requireNotNull(findPreference<EditTextPreference>("average_attempts_window"))
            averageAttemptsWindow.setOnBindEditTextListener { editText ->
                editText.inputType = InputType.TYPE_CLASS_NUMBER
            }
            // Only accept a whole number from 0 to MAX_AVERAGE_ATTEMPTS_WINDOW. An empty field
            // used to be saved and then crashed the app on every start.
            averageAttemptsWindow.setOnPreferenceChangeListener { _, newValue ->
                val number = newValue.toString().trim().toIntOrNull()
                val valid = number != null && number in 0..MAX_AVERAGE_ATTEMPTS_WINDOW
                if (!valid) {
                    Toast.makeText(
                        requireContext(),
                        getString(R.string.error_invalid_attempts_window, MAX_AVERAGE_ATTEMPTS_WINDOW),
                        Toast.LENGTH_SHORT
                    ).show()
                }
                valid // returning false means "do not save this value"
            }

            // Export: the system file picker asks where to save, then everything is written there.
            val getExport = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri: Uri? ->
                if (uri != null) {
                    cacheHandler.exportData(Main.addictions, uri)
                }
            }
            findPreference<Preference>("data_export")?.setOnPreferenceClickListener {
                getExport.launch("sobriety_data.json")
                true
            }

            // Import: the file is added to the existing data (nothing is replaced). On success
            // the screen closes and tells the main screen to sort and save the new list.
            val getImport = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
                if (uri != null) {
                    val success = cacheHandler.importData(uri) { imported -> addImported(imported) }
                    if (success) {
                        val intent = Intent()
                            .putExtra("import", true)
                        requireActivity().setResult(Activity.RESULT_OK, intent)
                        requireActivity().finish()
                    }
                }
            }
            findPreference<Preference>("data_import")?.setOnPreferenceClickListener {
                getImport.launch(arrayOf<String>("application/json"))
                true
            }

            setUpDiagnostics()
            setUpDefaultMilestones()
            setUpNewUiPreferences()

            findPreference<Preference>("pref_app_version")?.summary = BuildConfig.VERSION_NAME

            findPreference<Preference>("pref_app_issue_tracker")?.setOnPreferenceClickListener {
                val i = Intent(Intent.ACTION_VIEW)
                i.data = Uri.parse(requireContext().getString(R.string.app_issue_url))
                startActivity(i)
                true
            }
        }

        /**
         * Sobriety Neo: settings that only exist in the new UI (true black theme, tabs, order of
         * the cards). They are removed in the classic variant.
         */
        private fun setUpNewUiPreferences() {
            val amoled = findPreference<SwitchPreferenceCompat>("amoled_black")
            if (!BuildConfig.NEW_UI) {
                amoled?.let { it.parent?.removePreference(it) }
                findPreference<PreferenceCategory>("category_layout")?.let { preferenceScreen.removePreference(it) }
                return
            }
            amoled?.setOnPreferenceChangeListener { _, _ ->
                requireActivity().recreate()
                true
            }
            findPreference<Preference>("manage_tabs")?.setOnPreferenceClickListener {
                // The tabs screen only exists in the new UI, so it is opened by its name.
                startActivity(Intent().setClassName(requireContext(), "com.katiearose.sobriety.activities.Tabs"))
                true
            }
        }

        /**
         * Sobriety Neo: default milestones for new entries (see [DefaultMilestones]).
         *  - "Edit default milestones": uncheck to remove, "Add" for a new one, "Reset" for the
         *    standard list (1 day ... 5 years).
         *  - "Add to existing entries": adds the list to every entry that does not have them yet.
         */
        private fun setUpDefaultMilestones() {
            val editPref = findPreference<Preference>("edit_default_milestones")
            fun updateSummary() {
                editPref?.summary = DefaultMilestones.get(requireContext())
                    .joinToString(", ") { requireContext().milestoneText(it) }
            }
            updateSummary()
            editPref?.setOnPreferenceClickListener {
                showEditDefaultMilestonesDialog { updateSummary() }
                true
            }
            findPreference<Preference>("apply_default_milestones")?.setOnPreferenceClickListener {
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle(R.string.apply_default_milestones)
                    .setMessage(R.string.apply_default_milestones_confirm)
                    .setNegativeButton(android.R.string.cancel, null)
                    .setPositiveButton(android.R.string.ok) { _, _ ->
                        Trackers.ensureLoaded(requireContext())
                        val list = DefaultMilestones.get(requireContext())
                        var changed = 0
                        Main.addictions.forEach { if (DefaultMilestones.addTo(it, list)) changed++ }
                        if (changed > 0) cacheHandler.writeCache(Main.addictions)
                        Toast.makeText(
                            requireContext(),
                            getString(R.string.apply_default_milestones_done, changed),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    .show()
                true
            }
        }

        private fun showEditDefaultMilestonesDialog(onChanged: () -> Unit) {
            val context = requireContext()
            val list = DefaultMilestones.get(context)
            val checked = BooleanArray(list.size) { true }
            MaterialAlertDialogBuilder(context)
                .setTitle(R.string.edit_default_milestones)
                .setMultiChoiceItems(list.map { context.milestoneText(it) }.toTypedArray(), checked) { _, which, isChecked ->
                    checked[which] = isChecked
                }
                .setPositiveButton(R.string.save) { _, _ ->
                    DefaultMilestones.set(context, list.filterIndexed { i, _ -> checked[i] })
                    onChanged()
                }
                .setNeutralButton(R.string.add) { _, _ ->
                    // Keep the current choice, then ask for the new milestone.
                    DefaultMilestones.set(context, list.filterIndexed { i, _ -> checked[i] })
                    showAddDefaultMilestoneDialog(onChanged)
                }
                .setNegativeButton(R.string.reset_to_standard) { _, _ ->
                    DefaultMilestones.reset(context)
                    onChanged()
                }
                .show()
        }

        private fun showAddDefaultMilestoneDialog(onChanged: () -> Unit) {
            val context = requireContext()
            val view = DialogAddMilestoneBinding.inflate(layoutInflater)
            var unitIndex = 1 // days
            view.unitInput.setText(resources.getStringArray(R.array.time_units)[unitIndex], false)
            view.unitInput.setOnItemClickListener { _, _, position, _ -> unitIndex = position }
            val dialog = MaterialAlertDialogBuilder(context)
                .setTitle(R.string.add)
                .setView(view.root)
                .create()
            view.btnSaveMilestone.setOnClickListener {
                val number = view.milestoneNumberInput.text.toString().toIntOrNull()
                if (number == null || number <= 0 || number > 9999) {
                    view.milestoneNumberInputLayout.error = getString(R.string.error_amount_too_large, 9999)
                    return@setOnClickListener
                }
                DefaultMilestones.set(
                    context,
                    DefaultMilestones.get(context) + (number to DefaultMilestones.UNITS[unitIndex].second)
                )
                onChanged()
                dialog.dismiss()
            }
            dialog.show()
        }

        /**
         * Sobriety Neo: the "Diagnostics" section (optional, anonymous log, see [DiagnosticsLog]).
         * Export writes the log to a file chosen by the user, so it can be sent to the developer.
         */
        private fun setUpDiagnostics() {
            val exportPref = findPreference<Preference>("diagnostics_export")
            val deletePref = findPreference<Preference>("diagnostics_delete")
            fun updateSize() {
                val kb = (DiagnosticsLog.sizeBytes(requireContext()) + 1023) / 1024
                deletePref?.summary = getString(R.string.diagnostics_size, kb)
            }
            updateSize()

            findPreference<Preference>("diagnostics_info")?.setOnPreferenceClickListener {
                requireContext().showInfo(R.string.diagnostics_what, R.string.info_diagnostics)
                true
            }
            val getDiagnosticsExport = registerForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri: Uri? ->
                if (uri != null) {
                    val ok = DiagnosticsLog.export(requireContext(), uri)
                    Toast.makeText(
                        requireContext(),
                        if (ok) R.string.diagnostics_exported else R.string.diagnostics_export_failed,
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
            exportPref?.setOnPreferenceClickListener {
                getDiagnosticsExport.launch("sobriety_neo_diagnostics.txt")
                true
            }
            deletePref?.setOnPreferenceClickListener {
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle(R.string.diagnostics_delete)
                    .setMessage(R.string.diagnostics_delete_confirm)
                    .setNegativeButton(android.R.string.cancel, null)
                    .setPositiveButton(R.string.delete) { _, _ ->
                        DiagnosticsLog.clear(requireContext())
                        updateSize()
                    }
                    .show()
                true
            }
        }

        /**
         * Adds imported addictions to the app's list.
         *
         * Names must be unique (the create screen enforces it), so an imported addiction whose
         * name already exists gets a number: "Smoking" becomes "Smoking (2)". Nothing is
         * overwritten or lost. Importing the same file twice therefore creates copies instead of
         * silently producing two entries with the same name, as the old version did.
         */
        private fun addImported(imported: List<Addiction>) {
            val usedNames = Main.addictions.mapTo(HashSet()) { it.name }
            for (addiction in imported) {
                var name = addiction.name
                var counter = 2
                while (name in usedNames) {
                    name = "${addiction.name} ($counter)"
                    counter++
                }
                usedNames.add(name)
                Main.addictions.add(if (name == addiction.name) addiction else addiction.copy(name = name))
            }
        }
    }
}
