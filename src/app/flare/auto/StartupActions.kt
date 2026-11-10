package app.flare.auto

import android.app.Activity
import android.content.ComponentName
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
        val previous = prefs.getString("startup_last_boot", "") ?: ""
        if (id == previous) return
        prefs.edit().putString("startup_last_boot", id).apply()
        if (previous.isEmpty()) return
        val source = prefs.getString("startup_source", "none") ?: "none"
        val app = prefs.getString("startup_app", "") ?: ""
        val saver = prefs.getBoolean("startup_saver", false)
        if (source == "none" && app.isEmpty() && !saver) return
        running = true
        val delay = (prefs.getInt("startup_delay", 10).coerceIn(5, 30) * 1000L)
        handler.postDelayed(this.source, delay)
    }

    private fun runSource() {
        if (!running) return
        val source = prefs.getString("startup_source", "none") ?: "none"
        if (source == "radio" || source == "bluetooth") {
            val target = prefs.getString(if (source == "radio") "app_radio" else "app_audio", "") ?: ""
            val component = if (target.isEmpty()) null else ComponentName.unflattenFromString(target)
            if (component != null) {
                launch(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setComponent(component))
            } else {
                val className = if (source == "radio") "com.bw.mediaplayer.ui.RadioActivity" else "com.bw.mediaplayer.ui.BtActivity"
                launch(Intent().setClassName("com.bw.mediaplayer", className))
            }
            handler.postDelayed(app, 1200)
        } else {
            runApp()
        }
    }

    private fun runApp() {
        if (!running) return
        val target = prefs.getString("startup_app", "") ?: ""
        if (target.isNotEmpty()) {
            val component = ComponentName.unflattenFromString(target)
            if (component != null) launch(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setComponent(component))
            handler.postDelayed(saver, 1200)
        } else {
            runSaver()
        }
    }

    private fun runSaver() {
        if (!running) return
        if (prefs.getBoolean("startup_saver", false)) launch(Intent(activity, ScreenSaverActivity::class.java))
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
