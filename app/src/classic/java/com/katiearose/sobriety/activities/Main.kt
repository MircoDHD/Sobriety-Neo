package com.katiearose.sobriety.activities

import android.annotation.SuppressLint
import android.app.Dialog
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.BaseTransientBottomBar
import com.google.android.material.snackbar.Snackbar
import com.katiearose.sobriety.AddictionCardAdapter
import com.katiearose.sobriety.R
import com.katiearose.sobriety.databinding.ActivityMainBinding
import com.katiearose.sobriety.databinding.DialogAddNoteAfterRelapseBinding
import com.katiearose.sobriety.databinding.DialogMiscBinding
import com.katiearose.sobriety.databinding.DialogRelapseBinding
import com.katiearose.sobriety.shared.Addiction
import com.katiearose.sobriety.shared.CacheHandler
import com.katiearose.sobriety.shared.RelapseCost
import com.katiearose.sobriety.shared.primaryCurrency
import com.katiearose.sobriety.utils.DiagnosticsLog
import com.katiearose.sobriety.utils.showRelapseSheet
import com.katiearose.sobriety.utils.showNoteAfterRelapseIfEnabled
import com.katiearose.sobriety.utils.DefaultMilestones
import com.katiearose.sobriety.utils.Trackers
import com.katiearose.sobriety.utils.showDisplayOptionsDialog
import com.katiearose.sobriety.utils.handleInfoMenuItem
import com.katiearose.sobriety.utils.setInfoClick
import com.katiearose.sobriety.utils.toDoubleLenient
import com.katiearose.sobriety.utils.applyThemes
import com.katiearose.sobriety.utils.getAddNoteAfterRelapsePref
import com.katiearose.sobriety.utils.getSharedPref
import com.katiearose.sobriety.utils.isInputEmpty
import com.katiearose.sobriety.utils.showConfirmDialog
import com.katiearose.sobriety.utils.write
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * Main screen: the list of all addictions with their running counters, and the entry point to
 * all other screens (timeline, summary, notes, savings, milestones, settings, create).
 */
class Main : AppCompatActivity() {

    companion object {
        const val EXTRA_NAMES = "com.katiearose.sobriety.EXTRA_NAMES"
        // Pass the position of the addiction so that it can be modified
        const val EXTRA_ADDICTION_POSITION = "com.katiearose.sobriety.EXTRA_ADDICTION_POSITION"

        /** A counter update that takes longer than this is counted as "slow" in the diagnostics. */
        private const val SLOW_TICK_MS = 16L


        /**
         * All addictions, kept in a static list so that every screen can reach them. Detail screens
         * receive only a position in this list. The list is sorted by priority. If Android has
         * ended the app process this list is empty again and has to be reloaded from disk (see
         * `loadAddictionFromIntent()` in Utils.kt). The list itself lives in [Trackers], which is
         * shared with the new UI.
         */
        val addictions: ArrayList<Addiction> get() = Trackers.addictions
    }

    private lateinit var adapterAddictions: AddictionCardAdapter
    private lateinit var cacheHandler: CacheHandler
    private lateinit var binding: ActivityMainBinding

    // The one timer for all counters on this screen. It only runs while the screen is visible
    // (started in onResume, stopped in onPause). Once per second it
    //  1. starts waiting ("Future") addictions whose start time has been reached, and
    //  2. redraws the counter text of every card.
    // This replaces the endless coroutine each card used to start (a memory/battery leak) and the
    // Handler.postDelayed per waiting addiction (which was lost on restart and could be added twice).
    private val tickHandler = Handler(Looper.getMainLooper())
    private val tick = object : Runnable {
        override fun run() {
            val started = SystemClock.elapsedRealtime()
            if (activateDueAddictions()) cacheHandler.write()
            if (addictions.isNotEmpty())
                adapterAddictions.notifyItemRangeChanged(0, addictions.size, AddictionCardAdapter.PAYLOAD_TICK)
            tickHandler.postDelayed(this, 1000)
            // Statistics for the optional diagnostics log (written in onPause).
            val took = SystemClock.elapsedRealtime() - started
            tickCount++
            if (took > SLOW_TICK_MS) slowTickCount++
            if (took > maxTickMs) maxTickMs = took
        }
    }

    // Diagnostics (see DiagnosticsLog): counter updates during the current visit of the screen.
    private var tickCount = 0
    private var slowTickCount = 0
    private var maxTickMs = 0L
    private var createdAt = 0L
    private var startLogged = false

    /** Result of the "create" screen: adds the new addiction to the list and saves it. */
    @SuppressLint("NotifyDataSetChanged")
    private val addNewAddiction =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (it.resultCode == RESULT_OK) {
                val data = requireNotNull(requireNotNull(it.data) { "Something is wrong in the Create activity, go check the create() function" }.extras) { "Something is wrong in the Create activity, go check the create() function" }
                val name = data.getString("name")!!
                val instant = data.getLong("instant")
                val priority =
                    Addiction.Priority.values()[data.getInt("priority")]
                // If the start lies in the future the status is Future; the timer (see `tick`)
                // switches it to Ongoing at the right moment.
                val addiction = Addiction.newInstance(name, instant, priority)
                // Sobriety Neo: default milestones (can be turned off in the settings).
                DefaultMilestones.applyIfEnabled(this, addiction)
                addictions.add(addiction)
                addictions.sortWith { a1, a2 -> a1.priority.compareTo(a2.priority) }
                cacheHandler.write()
                adapterAddictions.notifyDataSetChanged()
            }
        }

    /** Result of the settings screen: after a data import the list has to be sorted and saved. */
    private val goToSettings = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (it.resultCode == RESULT_OK) {
            if (it.data?.getBooleanExtra("import", false) == true) {
                Trackers.migrate(this, addictions)
                addictions.sortWith { a1, a2 -> a1.priority.compareTo(a2.priority) }
                cacheHandler.write()
            }
        }
        adapterAddictions.notifyDataSetChanged()
    }

    //needed for this activity to apply md3 theme after user backs away from settings
    private val materialYouSettingListener =
        SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == "material_you") recreate()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        PreferenceManager.setDefaultValues(this, R.xml.root_preferences, false)
        val preferences = PreferenceManager.getDefaultSharedPreferences(this)
        // Android 9 and older have no system dark mode, so "system" is replaced by "light".
        if (preferences.getString("theme", "system") == "system" &&
            Build.VERSION.SDK_INT <= Build.VERSION_CODES.P
        ) {
            preferences.edit { putString("theme", "light") }
        }
        preferences.registerOnSharedPreferenceChangeListener(materialYouSettingListener)
        applyThemes()
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.addCardButton.setOnClickListener { newCardDialog() }
        cacheHandler = CacheHandler(this)
        // Load the saved data (only the first time; the list survives screen rotations).
        // loadFromDisk() never throws: a damaged file is kept aside and the app still starts.
        createdAt = SystemClock.elapsedRealtime()
        // Loads the data (only the first time) and updates old data to the current format.
        if (Trackers.ensureLoaded(this)) cacheHandler.write()
        // A waiting addiction may have reached its start time while the app was closed.
        if (activateDueAddictions()) cacheHandler.write()

        updatePromptVisibility()

        //Create adapter, and layout manager for recyclerview and attach them
        adapterAddictions = AddictionCardAdapter(this, deleteButtonAction =  {
            val action: () -> Unit = {
                // Remove first, then tell the adapter which position is gone. If the addiction
                // is not in the list anymore (double tap) nothing happens.
                val index = addictions.indexOf(it)
                if (index >= 0) {
                    addictions.removeAt(index)
                    adapterAddictions.notifyItemRemoved(index)
                    updatePromptVisibility()
                    cacheHandler.write()
                }
            }
            showConfirmDialog(
                getString(R.string.delete),
                getString(R.string.delete_confirm, it.name),
                action
            )
        }, relapseButtonAction = {
            // The same button is "relapse" for a running addiction and "start tracking now" for
            // a stopped or waiting one. A relapse opens a bottom sheet where the money and time
            // spent can be entered (optional, Sobriety Neo).
            when (it.status) {
                Addiction.Status.Ongoing -> showRelapseDialog(it)

                Addiction.Status.Stopped, Addiction.Status.Future ->
                    showConfirmDialog(
                        getString(R.string.track_now),
                        getString(R.string.start_tracking_now, it.name)
                    ) {
                        it.relapse()
                        adapterAddictions.notifyItemChanged(addictions.indexOf(it))
                        cacheHandler.write()
                    }
            }
        }, stopButtonAction = {
            when (it.status) {
                Addiction.Status.Ongoing ->
                    showConfirmDialog(
                        getString(R.string.stop),
                        getString(R.string.stop_confirm, it.name)
                    ) {
                        it.stopAbstaining()
                        adapterAddictions.notifyItemChanged(addictions.indexOf(it))
                        cacheHandler.write()
                        showAddNoteAfterRelapseDialogIfEnabled(it)
                    }

                Addiction.Status.Stopped ->
                    Snackbar.make(
                        binding.root,
                        getString(R.string.already_stopped, it.name),
                        BaseTransientBottomBar.LENGTH_SHORT
                    ).show()

                Addiction.Status.Future ->
                    Snackbar.make(
                        binding.root,
                        getString(R.string.not_tracked_yet, it.name),
                        BaseTransientBottomBar.LENGTH_SHORT
                    ).show()
            }
        }, timelineButtonAction = {
            when (it.status) {
                Addiction.Status.Ongoing, Addiction.Status.Stopped ->
                    startActivity(
                        Intent(this@Main, Timeline::class.java)
                            .putExtra(EXTRA_ADDICTION_POSITION, addictions.indexOf(it))
                    )

                Addiction.Status.Future ->
                    Snackbar.make(
                        binding.root,
                        getString(R.string.not_tracked_yet, it.name),
                        BaseTransientBottomBar.LENGTH_SHORT
                    ).show()
            }
        }, priorityTextViewAction = {
            var choice = it.priority.ordinal
            MaterialAlertDialogBuilder(this@Main)
                .setTitle(R.string.edit_priority)
                .setSingleChoiceItems(
                    R.array.priorities,
                    it.priority.ordinal
                ) { _, which -> choice = which }
                .setPositiveButton(R.string.edit) { _, _ ->
                    it.priority = Addiction.Priority.values()[choice]
                    addictions.sortWith { a1, a2 -> a1.priority.compareTo(a2.priority) }
                    cacheHandler.write()
                    adapterAddictions.notifyDataSetChanged()
                    Snackbar.make(
                        binding.root,
                        R.string.edit_priority_success,
                        BaseTransientBottomBar.LENGTH_SHORT
                    ).show()
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }, miscButtonAction = { a ->
            // The "more" button opens a bottom sheet with the notes, savings and milestones screens.
            val dialogViewBinding = DialogMiscBinding.inflate(layoutInflater)
            val dialog = BottomSheetDialog(this@Main)
            dialogViewBinding.dailyNotes.setOnClickListener {
                startActivity(
                    Intent(this@Main, DailyNotes::class.java)
                        .putExtra(EXTRA_ADDICTION_POSITION, addictions.indexOf(a))
                )
                dialog.dismiss()
            }
            dialogViewBinding.savings.setOnClickListener {
                startActivity(
                    Intent(this@Main, Savings::class.java)
                        .putExtra(EXTRA_ADDICTION_POSITION, addictions.indexOf(a))
                )
                dialog.dismiss()
            }
            dialogViewBinding.displayOptions.setOnClickListener {
                dialog.dismiss()
                showDisplayOptionsDialog(a) {
                    cacheHandler.write()
                    adapterAddictions.notifyItemChanged(addictions.indexOf(a))
                }
            }
            dialogViewBinding.milestones.setOnClickListener {
                startActivity(
                    Intent(this@Main, Milestones::class.java)
                        .putExtra(EXTRA_ADDICTION_POSITION, addictions.indexOf(a))
                )
                dialog.dismiss()
            }
            dialog.setContentView(dialogViewBinding.root)
            dialog.show()
        }, cardButtonAction = {
            // Tapping the card itself opens the summary (progress bars).
            when(it.status) {
                Addiction.Status.Ongoing, Addiction.Status.Stopped ->
                    startActivity(
                        Intent(this@Main, Summary::class.java)
                            .putExtra(EXTRA_ADDICTION_POSITION, addictions.indexOf(it))
                    )

                Addiction.Status.Future ->
                    Snackbar.make(
                        binding.root,
                        getString(R.string.not_tracked_yet, it.name),
                        BaseTransientBottomBar.LENGTH_SHORT
                    ).show()
            }

        })
        binding.recyclerAddictions.layoutManager = LinearLayoutManager(this)
        binding.recyclerAddictions.adapter = adapterAddictions
    }

    /**
     * Starts every waiting addiction whose start time has passed.
     *
     * @return true if at least one addiction changed, so the caller has to save the data
     */
    private fun activateDueAddictions(): Boolean {
        val now = System.currentTimeMillis()
        var changed = false
        for (addiction in addictions) {
            if (addiction.activateIfDue(now)) changed = true
        }
        return changed
    }

    /** Relapse bottom sheet (shared with the new UI, see utils/TrackerDialogs.kt). */
    private fun showRelapseDialog(addiction: Addiction) =
        showRelapseSheet(addiction) { adapterAddictions.notifyItemChanged(addictions.indexOf(addiction)) }

    private fun showAddNoteAfterRelapseDialogIfEnabled(addiction: Addiction) =
        showNoteAfterRelapseIfEnabled(addiction)

    /** Shows the "add your first addiction" hint only while the list is empty. */
    private fun updatePromptVisibility() {
        binding.prompt.visibility = if (addictions.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun newCardDialog() {
        //Pass current addiction names to create activity, to prevent creation of elements with identical keys
        val intent = Intent(this, Create::class.java)
            .putStringArrayListExtra(EXTRA_NAMES, addictions.mapTo(arrayListOf()) { it.name })
        addNewAddiction.launch(intent)
    }

    /**
     * This gets called once the Create Activity is closed (Necessary to hide the prompt in case
     * a first addiction was added to the list). It also starts the one-second counter timer.
     */
    override fun onResume() {
        updatePromptVisibility()
        super.onResume()
        // Restart the timer; the first run happens right away so the counters are never stale.
        tickHandler.removeCallbacks(tick)
        tickHandler.post(tick)
        if (!startLogged) {
            startLogged = true
            DiagnosticsLog.event(
                "app_start",
                "ms" to (SystemClock.elapsedRealtime() - createdAt),
                "trackers" to addictions.size
            )
        }
    }

    /** The screen is no longer visible, so stop the timer (saves battery). */
    override fun onPause() {
        tickHandler.removeCallbacks(tick)
        if (tickCount > 0) {
            DiagnosticsLog.event("ticks", "count" to tickCount, "slow" to slowTickCount, "max_ms" to maxTickMs)
            tickCount = 0; slowTickCount = 0; maxTickMs = 0L
        }
        super.onPause()
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        val id = item.itemId
        if (handleInfoMenuItem(id, R.string.app_name, R.string.info_main)) return true
        if (id == R.id.statistics) {
            startActivity(Intent(this, Statistics::class.java))
            return true
        }
        if (id == R.id.go_to_settings) {
            val intent = Intent(this, Settings::class.java)
            goToSettings.launch(intent)
            return true
        }
        return super.onOptionsItemSelected(item)
    }
}
