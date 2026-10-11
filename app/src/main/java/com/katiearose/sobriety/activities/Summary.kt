package com.katiearose.sobriety.activities

import com.katiearose.sobriety.R
import com.katiearose.sobriety.utils.handleInfoMenuItem
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.SimpleItemAnimator
import com.katiearose.sobriety.ProgressBarAdapter
import com.katiearose.sobriety.databinding.ActivitySummaryBinding
import com.katiearose.sobriety.shared.Addiction
import com.katiearose.sobriety.utils.*

/**
 * "Summary" screen of one addiction: a progress bar for each unit of the running counter
 * (months, days, hours, ...). Opened by tapping a card on the main screen.
 */
class Summary : AppCompatActivity() {

    private lateinit var binding: ActivitySummaryBinding
    private lateinit var addiction: Addiction
    private lateinit var progressAdapter: ProgressBarAdapter

    // One timer that refreshes the bars once per second, only while this screen is visible.
    // (Previously every adapter started its own endless timer that was never stopped.)
    private val tickHandler = Handler(Looper.getMainLooper())
    private val tick = object : Runnable {
        override fun run() {
            progressAdapter.update()
            // A stopped addiction does not change anymore, so there is no need to keep updating.
            if (addiction.status == Addiction.Status.Ongoing) tickHandler.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        applyThemes()
        super.onCreate(savedInstanceState)
        binding = ActivitySummaryBinding.inflate(layoutInflater)
        setContentView(binding.root)
        addiction = loadAddictionFromIntent() ?: run {
            finish()
            return
        }
        title = addiction.name

        progressAdapter = ProgressBarAdapter(addiction, this)
        binding.progressBarList.layoutManager = LinearLayoutManager(this)
        binding.progressBarList.setHasFixedSize(true)
        binding.progressBarList.adapter = progressAdapter
        // No flashing animation when a bar changes every second.
        (binding.progressBarList.itemAnimator as SimpleItemAnimator).supportsChangeAnimations = false
    }

    override fun onResume() {
        super.onResume()
        // Start (or restart) the one-second timer; the first update happens right away.
        if (::progressAdapter.isInitialized) {
            tickHandler.removeCallbacks(tick)
            tickHandler.post(tick)
        }
    }

    override fun onPause() {
        // Screen is no longer visible: stop the timer.
        tickHandler.removeCallbacks(tick)
        super.onPause()
    }

    // "i" button in the top bar (Sobriety Neo), see utils/InfoUtils.kt.
    override fun onCreateOptionsMenu(menu: android.view.Menu): Boolean {
        menuInflater.inflate(R.menu.menu_info, menu)
        return true
    }

    override fun onOptionsItemSelected(item: android.view.MenuItem): Boolean =
        handleInfoMenuItem(item.itemId, R.string.summary, R.string.info_summary) || super.onOptionsItemSelected(item)
}
