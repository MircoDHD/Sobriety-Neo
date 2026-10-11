package com.katiearose.sobriety.activities

import android.annotation.SuppressLint
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.SimpleItemAnimator
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.tabs.TabLayoutMediator
import com.katiearose.sobriety.R
import com.katiearose.sobriety.TrackerCardAdapter
import com.katiearose.sobriety.databinding.ActivityMainBinding
import com.katiearose.sobriety.databinding.PageTrackerListBinding
import com.katiearose.sobriety.shared.Addiction
import com.katiearose.sobriety.shared.CacheHandler
import com.katiearose.sobriety.shared.CardButtons
import com.katiearose.sobriety.shared.DisplaySettings
import com.katiearose.sobriety.utils.DefaultMilestones
import com.katiearose.sobriety.utils.DiagnosticsLog
import com.katiearose.sobriety.utils.TabStore
import com.katiearose.sobriety.utils.Trackers
import com.katiearose.sobriety.utils.TrackerTab
import com.katiearose.sobriety.utils.applyThemes
import com.katiearose.sobriety.utils.showConfirmDialog
import com.katiearose.sobriety.utils.showDisplayOptionsDialog
import com.katiearose.sobriety.utils.showInfo
import com.katiearose.sobriety.utils.showNoteAfterRelapseIfEnabled
import com.katiearose.sobriety.utils.showRelapseSheet
import com.katiearose.sobriety.utils.write

/**
 * Main screen of the NEW user interface (flavor "neo"). The classic main screen is in
 * src/classic/.../activities/Main.kt; both share everything in src/main.
 *
 * Layout: swipeable tabs. The first tab "All" shows every tracker (can be hidden in the
 * settings); the other tabs are defined by the user (see [TabStore] and the Tabs screen) and are
 * filled automatically by a rule (e.g. "high priority"), by hand, or both. The cards inside a tab
 * are sorted as chosen in the settings.
 *
 * Like the classic screen it runs one timer that refreshes the visible counters once per second,
 * only while the screen is visible. Nothing runs in the background.
 */
class Main : AppCompatActivity(), TrackerCardAdapter.Actions {

    companion object {
        const val EXTRA_NAMES = "com.katiearose.sobriety.EXTRA_NAMES"
        // Position of the tracker in [addictions]; detail screens use it to find the tracker.
        const val EXTRA_ADDICTION_POSITION = "com.katiearose.sobriety.EXTRA_ADDICTION_POSITION"

        /** All trackers. The list lives in [Trackers] and is shared with every screen. */
        val addictions: ArrayList<Addiction> get() = Trackers.addictions

        private const val SLOW_TICK_MS = 16L
    }

    /** One page of the pager: "All" (tab == null) or a user tab. */
    private data class Page(val title: String, val tab: TrackerTab?)

    private lateinit var binding: ActivityMainBinding
    private lateinit var cacheHandler: CacheHandler
    private val pagesAdapter = PagesAdapter()
    private var pages: List<Page> = emptyList()

    // One timer for the visible counters (see class comment).
    private val tickHandler = Handler(Looper.getMainLooper())
    private val tick = object : Runnable {
        override fun run() {
            val started = SystemClock.elapsedRealtime()
            if (activateDueAddictions()) {
                cacheHandler.write()
                refreshAll()
            } else {
                currentPageAdapter()?.tick()
            }
            tickHandler.postDelayed(this, 1000)
            val took = SystemClock.elapsedRealtime() - started
            tickCount++
            if (took > SLOW_TICK_MS) slowTickCount++
            if (took > maxTickMs) maxTickMs = took
        }
    }

    // Diagnostics (see DiagnosticsLog)
    private var tickCount = 0
    private var slowTickCount = 0
    private var maxTickMs = 0L
    private var createdAt = 0L
    private var startLogged = false

    /** Result of the "create" screen: adds the new tracker (with the default milestones). */
    private val addNewAddiction =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (it.resultCode == RESULT_OK) {
                val data = it.data?.extras ?: return@registerForActivityResult
                val name = data.getString("name") ?: return@registerForActivityResult
                val addiction = Addiction.newInstance(
                    name,
                    data.getLong("instant"),
                    Addiction.Priority.values()[data.getInt("priority")]
                )
                DefaultMilestones.applyIfEnabled(this, addiction)
                addictions.add(addiction)
                Trackers.sortByPriority()
                cacheHandler.write()
                refreshAll()
            }
        }

    /** Result of the settings screen: after an import the data has to be saved. */
    private val goToSettings = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (it.resultCode == RESULT_OK && it.data?.getBooleanExtra("import", false) == true) {
            Trackers.migrate(this, addictions)
            Trackers.sortByPriority()
            cacheHandler.write()
        }
        refreshAll()
    }

    // Theme settings are applied by recreating the screen.
    private val themeSettingListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == "material_you" || key == "amoled_black") recreate()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        PreferenceManager.setDefaultValues(this, R.xml.root_preferences, false)
        val preferences = PreferenceManager.getDefaultSharedPreferences(this)
        // Android 9 and older have no system dark mode, so "system" is replaced by "light".
        if (preferences.getString("theme", "system") == "system" && Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
            preferences.edit { putString("theme", "light") }
        }
        preferences.registerOnSharedPreferenceChangeListener(themeSettingListener)
        applyThemes()
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        cacheHandler = CacheHandler(this)

        createdAt = SystemClock.elapsedRealtime()
        if (Trackers.ensureLoaded(this)) cacheHandler.write()
        if (activateDueAddictions()) cacheHandler.write()

        binding.pager.adapter = pagesAdapter
        TabLayoutMediator(binding.tabs, binding.pager) { tab, position ->
            tab.text = pages.getOrNull(position)?.title
        }.attach()
        binding.addCardButton.setOnClickListener {
            addNewAddiction.launch(
                Intent(this, Create::class.java)
                    .putStringArrayListExtra(EXTRA_NAMES, addictions.mapTo(arrayListOf()) { it.name })
            )
        }
        refreshAll()
    }

    override fun onDestroy() {
        PreferenceManager.getDefaultSharedPreferences(this).unregisterOnSharedPreferenceChangeListener(themeSettingListener)
        super.onDestroy()
    }

    /** Rebuilds the list of pages (tabs may have changed) and redraws every card. */
    @SuppressLint("NotifyDataSetChanged")
    private fun refreshAll() {
        val tabs = TabStore.load(this)
        val newPages = ArrayList<Page>()
        if (TabStore.showAllTab(this) || tabs.isEmpty()) newPages += Page(getString(R.string.tab_all), null)
        tabs.forEach { newPages += Page(it.name, it) }
        pages = newPages
        pagesAdapter.notifyDataSetChanged()
        binding.tabs.visibility = if (pages.size > 1) View.VISIBLE else View.GONE
        binding.prompt.visibility = if (addictions.isEmpty()) View.VISIBLE else View.GONE
    }

    /** The trackers of one page, filtered and sorted. */
    private fun itemsOf(page: Page): List<Addiction> {
        val filtered = if (page.tab == null) addictions.toList() else addictions.filter { page.tab.contains(it) }
        return TabStore.sort(this, filtered, System.currentTimeMillis())
    }

    /** The card adapter of the page that is visible right now (only that one needs ticking). */
    private fun currentPageAdapter(): TrackerCardAdapter? {
        val recycler = binding.pager.getChildAt(0) as? RecyclerView ?: return null
        val holder = recycler.findViewHolderForAdapterPosition(binding.pager.currentItem) as? PageHolder
        return holder?.adapter
    }

    /** Starts waiting trackers whose start time has passed. @return true if one changed */
    private fun activateDueAddictions(): Boolean {
        val now = System.currentTimeMillis()
        var changed = false
        for (addiction in addictions) if (addiction.activateIfDue(now)) changed = true
        return changed
    }

    // ---------------------------------------------------------------------------------------
    // Pages (one RecyclerView of cards per tab)
    // ---------------------------------------------------------------------------------------

    private inner class PageHolder(val page: PageTrackerListBinding) : RecyclerView.ViewHolder(page.root) {
        val adapter = TrackerCardAdapter(this@Main, this@Main)

        init {
            page.list.layoutManager = LinearLayoutManager(this@Main)
            page.list.adapter = adapter
            // No flashing when a counter changes every second.
            (page.list.itemAnimator as? SimpleItemAnimator)?.supportsChangeAnimations = false
        }
    }

    private inner class PagesAdapter : RecyclerView.Adapter<PageHolder>() {
        override fun getItemCount() = pages.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            PageHolder(PageTrackerListBinding.inflate(LayoutInflater.from(parent.context), parent, false))

        override fun onBindViewHolder(holder: PageHolder, position: Int) {
            val items = itemsOf(pages[position])
            holder.adapter.submit(items)
            holder.page.empty.visibility =
                if (items.isEmpty() && addictions.isNotEmpty()) View.VISIBLE else View.GONE
        }
    }

    // ---------------------------------------------------------------------------------------
    // Card actions (TrackerCardAdapter.Actions)
    // ---------------------------------------------------------------------------------------

    private fun positionOf(addiction: Addiction) = addictions.indexOf(addiction)

    private fun notTrackedYet(addiction: Addiction) =
        Snackbar.make(binding.root, getString(R.string.not_tracked_yet, addiction.name), Snackbar.LENGTH_SHORT).show()

    private fun openScreen(cls: Class<*>, addiction: Addiction) =
        startActivity(Intent(this, cls).putExtra(EXTRA_ADDICTION_POSITION, positionOf(addiction)))

    override fun open(addiction: Addiction) {
        if (addiction.status == Addiction.Status.Future) notTrackedYet(addiction)
        else openScreen(Summary::class.java, addiction)
    }

    override fun timeline(addiction: Addiction) {
        if (addiction.status == Addiction.Status.Future) notTrackedYet(addiction)
        else openScreen(Timeline::class.java, addiction)
    }

    override fun notes(addiction: Addiction) = openScreen(DailyNotes::class.java, addiction)
    override fun savings(addiction: Addiction) = openScreen(Savings::class.java, addiction)
    override fun milestones(addiction: Addiction) = openScreen(Milestones::class.java, addiction)

    override fun relapse(addiction: Addiction) {
        when (addiction.status) {
            Addiction.Status.Ongoing -> showRelapseSheet(addiction) { refreshAll() }
            Addiction.Status.Stopped, Addiction.Status.Future ->
                showConfirmDialog(getString(R.string.track_now), getString(R.string.start_tracking_now, addiction.name)) {
                    addiction.relapse()
                    cacheHandler.write()
                    refreshAll()
                }
        }
    }

    override fun stop(addiction: Addiction) {
        when (addiction.status) {
            Addiction.Status.Ongoing ->
                showConfirmDialog(getString(R.string.stop), getString(R.string.stop_confirm, addiction.name)) {
                    addiction.stopAbstaining()
                    cacheHandler.write()
                    refreshAll()
                    showNoteAfterRelapseIfEnabled(addiction)
                }
            Addiction.Status.Stopped ->
                Snackbar.make(binding.root, getString(R.string.already_stopped, addiction.name), Snackbar.LENGTH_SHORT).show()
            Addiction.Status.Future -> notTrackedYet(addiction)
        }
    }

    override fun delete(addiction: Addiction) {
        showConfirmDialog(getString(R.string.delete), getString(R.string.delete_confirm, addiction.name)) {
            if (addictions.remove(addiction)) {
                TabStore.forget(this, addiction.name)
                cacheHandler.write()
                refreshAll()
            }
        }
    }

    override fun priority(addiction: Addiction) {
        var choice = addiction.priority.ordinal
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.edit_priority)
            .setSingleChoiceItems(R.array.priorities, choice) { _, which -> choice = which }
            .setPositiveButton(R.string.edit) { _, _ ->
                addiction.priority = Addiction.Priority.values()[choice]
                Trackers.sortByPriority()
                cacheHandler.write()
                refreshAll()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    /**
     * Display options of one tracker. On top of the options of both UIs (counter / countdown,
     * precision) the new UI adds: which buttons are shown on the card, and to which of the user's
     * tabs the tracker is added by hand.
     */
    override fun options(addiction: Addiction) {
        showDisplayOptionsDialog(addiction, addExtras = { container, current ->
            fun header(text: Int) = TextView(this).apply {
                setText(text)
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                setPadding(0, dp(16), 0, dp(4))
            }.also { container.addView(it) }

            // Visible buttons
            header(R.string.visible_buttons)
            val buttonLabels = mapOf(
                CardButtons.TIMELINE to R.string.timeline,
                CardButtons.NOTES to R.string.daily_notes,
                CardButtons.SAVINGS to R.string.savings,
                CardButtons.MILESTONES to R.string.milestones,
                CardButtons.STOP to R.string.stop,
                CardButtons.DELETE to R.string.delete
            )
            val buttonBoxes = CardButtons.ALL.associateWith { key ->
                CheckBox(this).apply {
                    setText(buttonLabels.getValue(key))
                    isChecked = key !in current.hiddenButtons
                    container.addView(this, LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                }
            }

            // Tabs (added by hand); tabs whose rule already matches are shown as checked + locked.
            val tabs = TabStore.load(this)
            val tabBoxes = if (tabs.isEmpty()) emptyMap() else {
                header(R.string.tabs_for_tracker)
                tabs.associateWith { tab ->
                    CheckBox(this).apply {
                        text = tab.name
                        isChecked = tab.contains(addiction)
                        isEnabled = !tab.rule.matches(addiction)
                        container.addView(this, LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                    }
                }
            }

            // Applied when "Save" is pressed.
            val applyChanges: (DisplaySettings) -> DisplaySettings = { settings ->
                if (tabBoxes.isNotEmpty()) {
                    for ((tab, box) in tabBoxes) {
                        if (!box.isEnabled) continue
                        if (box.isChecked) tab.members.add(addiction.name) else tab.members.remove(addiction.name)
                    }
                    TabStore.save(this, tabs)
                }
                settings.copy(hiddenButtons = buttonBoxes.filterValues { !it.isChecked }.keys)
            }
            applyChanges
        }) {
            cacheHandler.write()
            refreshAll()
        }
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    // ---------------------------------------------------------------------------------------
    // Lifecycle and menu
    // ---------------------------------------------------------------------------------------

    override fun onResume() {
        super.onResume()
        // Tabs, sort order or data may have been changed on another screen.
        refreshAll()
        tickHandler.removeCallbacks(tick)
        tickHandler.post(tick)
        if (!startLogged) {
            startLogged = true
            DiagnosticsLog.event("app_start", "ms" to (SystemClock.elapsedRealtime() - createdAt), "trackers" to addictions.size)
        }
    }

    override fun onPause() {
        tickHandler.removeCallbacks(tick)
        if (tickCount > 0) {
            DiagnosticsLog.event("ticks", "count" to tickCount, "slow" to slowTickCount, "max_ms" to maxTickMs)
            tickCount = 0; slowTickCount = 0; maxTickMs = 0L
        }
        super.onPause()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.info -> showInfo(R.string.app_name, R.string.info_main_neo)
            R.id.statistics -> startActivity(Intent(this, Statistics::class.java))
            R.id.manage_tabs -> startActivity(Intent(this, Tabs::class.java))
            R.id.go_to_settings -> goToSettings.launch(Intent(this, Settings::class.java))
            else -> return super.onOptionsItemSelected(item)
        }
        return true
    }
}
