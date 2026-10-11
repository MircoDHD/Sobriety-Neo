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

            findPreference<Preference>("pref_app_version")?.summary = BuildConfig.VERSION_NAME

            findPreference<Preference>("pref_app_issue_tracker")?.setOnPreferenceClickListener {
                val i = Intent(Intent.ACTION_VIEW)
                i.data = Uri.parse(requireContext().getString(R.string.app_issue_url))
                startActivity(i)
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
