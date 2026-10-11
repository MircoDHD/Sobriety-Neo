package com.katiearose.sobriety

import android.app.Activity
import android.app.Application
import android.content.ComponentCallbacks2
import android.os.Bundle
import android.os.SystemClock
import com.katiearose.sobriety.utils.DiagnosticsLog

/**
 * Application class of Sobriety Neo (registered in AndroidManifest.xml with android:name).
 *
 * It only exists for the optional diagnostics log (see [DiagnosticsLog]):
 *  - it initializes the log,
 *  - records how long each screen takes to appear,
 *  - records crashes before Android shows the "app has stopped" message,
 *  - records when Android asks the app to free memory.
 * If diagnostics are turned off (the default), all of this does nothing.
 */
class SobrietyApp : Application() {

    override fun onCreate() {
        super.onCreate()
        DiagnosticsLog.init(this)

        // Record crashes, then let Android handle them as usual.
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                DiagnosticsLog.crash(throwable)
            } catch (ignored: Throwable) {
                // Never hide the original crash.
            }
            previous?.uncaughtException(thread, throwable)
        }

        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            // Time at which each screen was created, to measure how long it took to appear.
            private val created = HashMap<Activity, Long>()

            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
                created[activity] = SystemClock.elapsedRealtime()
            }

            override fun onActivityResumed(activity: Activity) {
                val start = created.remove(activity) ?: return
                DiagnosticsLog.event(
                    "screen_" + activity.javaClass.simpleName.lowercase(),
                    "ms" to (SystemClock.elapsedRealtime() - start)
                )
            }

            override fun onActivityDestroyed(activity: Activity) {
                created.remove(activity)
            }

            override fun onActivityStarted(activity: Activity) {}
            override fun onActivityPaused(activity: Activity) {}
            override fun onActivityStopped(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
        })
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        // Only the levels that mean "memory is getting low while the app runs" are interesting.
        if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW && level < ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN)
            DiagnosticsLog.event("trim_memory", "level" to level)
    }
}
