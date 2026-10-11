package com.katiearose.sobriety.activities

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.katiearose.sobriety.R
import com.katiearose.sobriety.databinding.ActivityTabsBinding
import com.katiearose.sobriety.databinding.DialogEditTabBinding
import com.katiearose.sobriety.databinding.ListItemTabBinding
import com.katiearose.sobriety.shared.CacheHandler
import com.katiearose.sobriety.utils.TabRule
import com.katiearose.sobriety.utils.TabStore
import com.katiearose.sobriety.utils.TrackerTab
import com.katiearose.sobriety.utils.Trackers
import com.katiearose.sobriety.utils.applyThemes
import com.katiearose.sobriety.utils.handleInfoMenuItem
import com.katiearose.sobriety.utils.isInputEmpty
import com.katiearose.sobriety.utils.showConfirmDialog
import com.katiearose.sobriety.utils.write

/**
 * "Manage tabs" screen of the new UI: add, rename, reorder and delete the tabs of the main
 * screen, choose how each tab is filled automatically, and add trackers to it by hand.
 * The tabs are stored by [TabStore].
 */
class Tabs : AppCompatActivity() {

    private lateinit var binding: ActivityTabsBinding
    private lateinit var tabs: MutableList<TrackerTab>
    private val adapter = TabListAdapter()

    override fun onCreate(savedInstanceState: Bundle?) {
        applyThemes()
        super.onCreate(savedInstanceState)
        binding = ActivityTabsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        if (Trackers.ensureLoaded(this)) CacheHandler(this).write()
        tabs = TabStore.load(this)
        binding.tabList.layoutManager = LinearLayoutManager(this)
        binding.tabList.adapter = adapter
        binding.addTab.setOnClickListener { showEditDialog(null) }
        updateEmpty()
    }

    private fun save() {
        TabStore.save(this, tabs)
        updateEmpty()
    }

    private fun updateEmpty() {
        binding.empty.visibility = if (tabs.isEmpty()) View.VISIBLE else View.GONE
    }

    /** Short text that tells how a tab is filled. */
    private fun describe(tab: TrackerTab): String {
        val rule = resources.getStringArray(R.array.tab_rules)[tab.rule.ordinal]
        val existing = tab.members.count { name -> Trackers.addictions.any { it.name == name } }
        return getString(R.string.tab_description, rule, existing)
    }

    /** Dialog to add a tab (tab == null) or to change one. */
    @SuppressLint("NotifyDataSetChanged")
    private fun showEditDialog(tab: TrackerTab?) {
        val view = DialogEditTabBinding.inflate(layoutInflater)
        val rules = TabRule.values()
        val ruleNames = resources.getStringArray(R.array.tab_rules)
        var rule = tab?.rule ?: TabRule.NONE
        view.tabNameInput.setText(tab?.name)
        view.tabRuleInput.setText(ruleNames[rule.ordinal], false)
        view.tabRuleInput.setOnItemClickListener { _, _, position, _ -> rule = rules[position] }

        // One checkbox per tracker for "added by hand".
        val boxes = Trackers.addictions.map { addiction ->
            CheckBox(this).apply {
                text = addiction.name
                isChecked = tab?.members?.contains(addiction.name) == true
                view.members.addView(this, LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            }
        }

        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(if (tab == null) R.string.add_tab else R.string.edit_tab)
            .setView(view.root)
            .setPositiveButton(R.string.save, null)
            .setNegativeButton(android.R.string.cancel, null)
            .create()
        dialog.setOnShowListener {
            // Set here (not in setPositiveButton) so that the dialog stays open on an error.
            dialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE).setOnClickListener {
                if (view.tabNameInput.isInputEmpty()) {
                    view.tabNameLayout.error = getString(R.string.error_empty_name)
                    return@setOnClickListener
                }
                val members = boxes.filter { it.isChecked }.mapTo(LinkedHashSet()) { it.text.toString() }
                if (tab == null) {
                    tabs.add(TrackerTab(TabStore.newId(tabs), view.tabNameInput.text.toString().trim(), rule, members))
                } else {
                    tab.name = view.tabNameInput.text.toString().trim()
                    tab.rule = rule
                    tab.members.clear()
                    tab.members.addAll(members)
                }
                save()
                adapter.notifyDataSetChanged()
                dialog.dismiss()
            }
        }
        dialog.show()
    }

    private inner class TabHolder(val b: ListItemTabBinding) : RecyclerView.ViewHolder(b.root) {
        @SuppressLint("NotifyDataSetChanged")
        private fun move(by: Int) {
            val from = adapterPosition
            val to = from + by
            if (from == RecyclerView.NO_POSITION || to !in tabs.indices) return
            tabs.add(to, tabs.removeAt(from))
            save()
            adapter.notifyItemMoved(from, to)
        }

        init {
            b.btnUp.setOnClickListener { move(-1) }
            b.btnDown.setOnClickListener { move(1) }
            b.btnEdit.setOnClickListener { tabs.getOrNull(adapterPosition)?.let { showEditDialog(it) } }
            b.btnDelete.setOnClickListener {
                val tab = tabs.getOrNull(adapterPosition) ?: return@setOnClickListener
                showConfirmDialog(getString(R.string.delete), getString(R.string.delete_tab_confirm, tab.name)) {
                    val index = tabs.indexOf(tab)
                    if (index >= 0) {
                        tabs.removeAt(index)
                        save()
                        adapter.notifyItemRemoved(index)
                    }
                }
            }
        }
    }

    private inner class TabListAdapter : RecyclerView.Adapter<TabHolder>() {
        override fun getItemCount() = tabs.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            TabHolder(ListItemTabBinding.inflate(LayoutInflater.from(parent.context), parent, false))

        override fun onBindViewHolder(holder: TabHolder, position: Int) {
            val tab = tabs[position]
            holder.b.tabName.text = tab.name
            holder.b.tabDescription.text = describe(tab)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_info, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean =
        handleInfoMenuItem(item.itemId, R.string.manage_tabs, R.string.info_tabs) || super.onOptionsItemSelected(item)
}
