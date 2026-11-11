package app.flare.auto

import android.app.Activity
import android.os.Bundle
import android.os.Debug
import android.os.SystemClock
import android.util.Log
import android.widget.ScrollView
import android.widget.TextView

/**
 * Debug builds only. Checks the offline navigation pack on this device and times a few routes:
 *
 *     adb shell am start -n app.flare.auto/.SmokeActivity --es open nav
 *     adb logcat -s FlareNav
 */
class NavigationCheckActivity : Activity() {
    private lateinit var output: TextView
    private val routing by lazy { OfflineRouting(this) }

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        output = TextView(this).apply { textSize = 15f; setPadding(dp(16), dp(16), dp(16), dp(16)) }
        setContentView(ScrollView(this).apply { addView(output) })
        report("Pack folder: ${NavigationPack.root(this)?.absolutePath}")
        val started = SystemClock.elapsedRealtime()
        OfflineRouting.worker.execute {
            val status = NavigationPack.verified(this)
            val ms = SystemClock.elapsedRealtime() - started
            runOnUiThread {
                if (isDestroyed) return@runOnUiThread
                report("${status.message} (status + checksums: $ms ms)")
                if (status is NavigationPack.Status.Ready) next(0, SystemClock.elapsedRealtime())
            }
        }
    }

    private fun next(index: Int, started: Long) {
        val trip = TRIPS.getOrNull(index) ?: run { report("Java heap in use: ${heapMb()} MB · total PSS ${Debug.getPss() / 1024} MB"); return }
        routing.route(trip.second[0], trip.second[1], trip.second[2], trip.second[3]) { result ->
            val ms = SystemClock.elapsedRealtime() - started
            result.fold(
                { report("${trip.first}: ${"%.1f".format(it.distance / 1000)} km, ${it.millis / 60000} min, ${it.steps.size} steps — $ms ms${if (index == 0) " (includes graph load)" else ""}") },
                { report("${trip.first}: FAILED ${it.message} — $ms ms") })
            next(index + 1, SystemClock.elapsedRealtime())
        }
    }

    private fun heapMb() = (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / 1048576

    private fun report(line: String) {
        Log.i("FlareNav", line)
        output.append(line + "\n")
    }

    override fun onDestroy() {
        routing.close()
        super.onDestroy()
    }

    private companion object {
        /** From flare-lahore-tools/tests/regression-routes.json. */
        val TRIPS = listOf(
            "Liberty → Badshahi Mosque" to doubleArrayOf(31.5106, 74.3446, 31.5880, 74.3107),
            "Airport → Railway station" to doubleArrayOf(31.5216, 74.4036, 31.5770, 74.3368),
            "Model Town → Charing Cross" to doubleArrayOf(31.4835, 74.3260, 31.5547, 74.3289))
    }
}
