package com.katiearose.sobriety.activities

import com.katiearose.sobriety.utils.handleInfoMenuItem
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.datepicker.MaterialDatePicker
import com.katiearose.sobriety.NoteAdapter
import com.katiearose.sobriety.R
import com.katiearose.sobriety.databinding.ActivityDailyNotesBinding
import com.katiearose.sobriety.databinding.DialogAddNoteBinding
import com.katiearose.sobriety.shared.Addiction
import com.katiearose.sobriety.shared.CacheHandler
import com.katiearose.sobriety.utils.applyThemes
import com.katiearose.sobriety.utils.loadAddictionFromIntent
import com.katiearose.sobriety.utils.getDateFormatPattern
import com.katiearose.sobriety.utils.getSharedPref
import com.katiearose.sobriety.utils.isInputEmpty
import com.katiearose.sobriety.utils.showConfirmDialog
import com.katiearose.sobriety.utils.write
import kotlinx.datetime.*
import java.time.format.DateTimeFormatter

/**
 * "Daily notes" screen of one addiction: one free-text note per calendar day (a small diary).
 * Opened from the "more" button on a card.
 */
class DailyNotes : AppCompatActivity() {

    private lateinit var binding: ActivityDailyNotesBinding
    private lateinit var adapter: NoteAdapter
    private lateinit var dateFormat: DateTimeFormatter
    private lateinit var addiction: Addiction
    private lateinit var cacheHandler: CacheHandler

    override fun onCreate(savedInstanceState: Bundle?) {
        applyThemes()
        super.onCreate(savedInstanceState)
        binding = ActivityDailyNotesBinding.inflate(layoutInflater)
        setContentView(binding.root)
        dateFormat = DateTimeFormatter.ofPattern(getSharedPref().getDateFormatPattern())
        cacheHandler = CacheHandler(this)

        addiction = loadAddictionFromIntent() ?: run {
            finish()
            return
        }
        adapter = NoteAdapter(addiction, this, { showAddNoteDialog(true, it.first) },
            {
                val action: () -> Unit = {
                    addiction.dailyNotes.remove(it.first)
                    updateNotesList()
                }
                showConfirmDialog(getString(R.string.delete), getString(R.string.delete_note_confirm, dateFormat.format(it.first.toJavaLocalDate())), action)
            })
        binding.notesList.layoutManager = LinearLayoutManager(this)
        binding.notesList.adapter = adapter

        binding.addNoteFab.setOnClickListener { showAddNoteDialog(false, Clock.System.now().toLocalDateTime(
            TimeZone.currentSystemDefault()).date) }
    }

    /**
     * Shows the bottom sheet to write a note.
     *
     * @param isEdit true to edit the existing note of [date] (the date can not be changed then),
     *   false to write a new note, where the date can be picked (default: today)
     * @param date the date of the note
     */
    private fun showAddNoteDialog(isEdit: Boolean, date: LocalDate) {
        var pickedDate = date
        val dialogViewBinding = DialogAddNoteBinding.inflate(layoutInflater)
        val dialog = BottomSheetDialog(this)
        dialog.setContentView(dialogViewBinding.root)
        if (isEdit) {
            dialogViewBinding.dateStr.visibility = View.GONE
            dialogViewBinding.noteDate.visibility = View.GONE
            dialogViewBinding.noteInput.setText(addiction.dailyNotes[date])
        } else {
            dialogViewBinding.noteDate.text = dateFormat.format(pickedDate.toJavaLocalDate())
            dialogViewBinding.noteDate.setOnClickListener {
                with(MaterialDatePicker.Builder.datePicker().build()) {
                    addOnPositiveButtonClickListener {
                        // The date picker returns midnight in UTC, so convert it in UTC as well.
                        // Using the local zone here could shift the date by one day.
                        pickedDate = Instant.fromEpochMilliseconds(it).toLocalDateTime(TimeZone.UTC).date
                        dialogViewBinding.noteDate.text = dateFormat.format(pickedDate.toJavaLocalDate())
                        if (addiction.dailyNotes.contains(pickedDate)) {
                            dialogViewBinding.noteInput.setText(addiction.dailyNotes[pickedDate])
                        }
                    }
                    show(supportFragmentManager, null)
                }
            }
        }
        dialogViewBinding.btnSave.setOnClickListener {
            if (dialogViewBinding.noteInput.isInputEmpty()) {
                dialogViewBinding.noteInputLayout.error = getString(R.string.error_empty_note)
            } else {
                addiction.dailyNotes[pickedDate] = dialogViewBinding.noteInput.text.toString()
                updateNotesList()
                dialog.dismiss()
            }
        }
        dialog.show()
    }

    /** Saves the notes and refreshes the list. */
    private fun updateNotesList() {
        cacheHandler.write()
        adapter.update()
    }

    // "i" button in the top bar (Sobriety Neo), see utils/InfoUtils.kt.
    override fun onCreateOptionsMenu(menu: android.view.Menu): Boolean {
        menuInflater.inflate(R.menu.menu_info, menu)
        return true
    }

    override fun onOptionsItemSelected(item: android.view.MenuItem): Boolean =
        handleInfoMenuItem(item.itemId, R.string.daily_notes, R.string.info_daily_notes) || super.onOptionsItemSelected(item)
}
