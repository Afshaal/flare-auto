package app.flare.auto

import android.app.Activity
import android.content.Intent
import android.content.SharedPreferences
import android.os.Handler
import java.io.BufferedReader
import java.io.FileReader

internal class StartupActions(
    @JvmField val activity: Activity,
    @JvmField val prefs: SharedPreferences,
    @JvmField val handler: Handler
) {
    @JvmField var running = false
    @JvmField var ownTransition = false
    @JvmField val source = Runnable { runSource() }
    @JvmField val app = Runnable { runApp() }
    @JvmField val saver = Runnable { runSaver() }

    fun checkBoot() {
        val id = bootId()
        if (id.isEmpty()) return
        val device = activity.applicationContext.getSharedPreferences("startup_device", 0)
        val previous = device.getString(Keys.STARTUP_LAST_BOOT, prefs.getString(Keys.STARTUP_LAST_BOOT, "")) ?: ""
        if (id == previous) return
        device.edit().putString(Keys.STARTUP_LAST_BOOT, id).apply()
        if (previous.isEmpty()) return
        val source = prefs.getString(Keys.STARTUP_SOURCE, "none") ?: "none"
        val app = prefs.getString(Keys.STARTUP_APP, "") ?: ""
        val saver = prefs.getBoolean(Keys.STARTUP_SAVER, false)
        if (source == "none" && app.isEmpty() && !saver) return
        running = true
        val delay = (prefs.getInt(Keys.STARTUP_DELAY, 10).coerceIn(5, 30) * 1000L)
        handler.postDelayed(this.source, delay)
    }

    private fun runSource() {
        if (!running) return
        val source = prefs.getString(Keys.STARTUP_SOURCE, "none") ?: "none"
        if (source == "radio" || source == "bluetooth") {
            val screen = if (source == "radio") KnownApps.RADIO_SCREEN else KnownApps.AUDIO_SCREEN
            val component = AppLookup.component(prefs.getString(Keys.APP_PREFIX + KnownApps.overrideRole(screen), ""))
            launch(if (component != null) AppLookup.launcherIntent(component) else Intent().setClassName(screen.packageName, screen.className))
            handler.postDelayed(app, 1200)
        } else {
            runApp()
        }
    }

    private fun runApp() {
        if (!running) return
        val target = prefs.getString(Keys.STARTUP_APP, "") ?: ""
        if (target.isNotEmpty()) {
            AppLookup.component(target)?.let { launch(AppLookup.launcherIntent(it)) }
            handler.postDelayed(saver, 1200)
        } else {
            runSaver()
        }
    }

    private fun runSaver() {
        if (!running) return
        if (prefs.getBoolean(Keys.STARTUP_SAVER, false)) launch(Intent(activity, ScreenSaverActivity::class.java))
        running = false
        ownTransition = false
    }

    private fun launch(intent: Intent) {
        try {
            ownTransition = true
            activity.startActivity(intent)
        } catch (_: Exception) {
            ownTransition = false
        }
    }

    fun onPause() {
        if (!ownTransition) cancel()
    }

    fun onResume() {
        ownTransition = false
    }

    fun cancel() {
        running = false
        ownTransition = false
        handler.removeCallbacks(source)
        handler.removeCallbacks(app)
        handler.removeCallbacks(saver)
    }

    companion object {
        @JvmStatic
        fun bootId(): String = try {
            BufferedReader(FileReader("/proc/sys/kernel/random/boot_id")).use { it.readLine()?.trim() ?: "" }
        } catch (_: Exception) {
            ""
        }
    }
}
