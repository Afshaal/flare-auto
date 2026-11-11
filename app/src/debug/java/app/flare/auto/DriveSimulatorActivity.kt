package app.flare.auto

import android.app.Activity
import android.content.Context
import android.location.Criteria
import android.location.Location
import android.location.LocationManager
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * Debug builds only, for emulators: drives a simulated car through the "gps" test provider so offline guidance
 * can be watched on the map. The app must be allowed to mock locations:
 *
 *     adb shell appops set app.flare.auto android:mock_location allow
 *     adb shell am start -n app.flare.auto/.DriveSimulatorActivity [--es to "lat,lon"] [--ei delay 25] [--ei speed 12] [--ez detour true]
 *
 * The car waits [delay] seconds at the start (time to choose the same destination on the map), then follows the
 * route the app itself computes. With detour it leaves the route halfway and drives 300 m sideways, then stops.
 * Progress is logged with tag FlareSim.
 */
class DriveSimulatorActivity : Activity() {
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        val from = coords(intent.getStringExtra("from")) ?: doubleArrayOf(31.52037, 74.358747)
        val to = coords(intent.getStringExtra("to")) ?: doubleArrayOf(31.5112797, 74.3450076) // Liberty Market
        val delay = intent.getIntExtra("delay", 25)
        val speed = intent.getIntExtra("speed", 12).toDouble()
        val detour = intent.getBooleanExtra("detour", false)
        val manager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        try {
            try { manager.removeTestProvider(GPS) } catch (_: Exception) { }
            manager.addTestProvider(GPS, false, true, false, false, true, true, true, Criteria.POWER_LOW, Criteria.ACCURACY_FINE)
            manager.setTestProviderEnabled(GPS, true)
        } catch (e: SecurityException) {
            Log.e(TAG, "Not allowed to mock locations: adb shell appops set $packageName android:mock_location allow")
            finish(); return
        }
        OfflineRouting(applicationContext).route(from[0], from[1], to[0], to[1]) { result ->
            result.fold({ route ->
                Log.i(TAG, "route ${route.distance.toInt()} m, ${route.steps.size} steps; starting in $delay s")
                Thread { drive(manager, route, delay, speed, detour) }.apply { isDaemon = true }.start()
            }, { Log.e(TAG, "route failed: ${it.message}") })
        }
        finish()
    }

    private fun drive(manager: LocationManager, route: NavRoute, delay: Int, speed: Double, detour: Boolean) {
        val total = route.cumulative.last()
        var lastBearing = 0f
        for (second in 0..10_000) {
            val travelled = maxOf(0.0, (second - delay) * speed)
            var point = pointAt(route, minOf(travelled, total))
            if (detour && travelled > total / 2) {
                val half = pointAt(route, total / 2)
                val side = minOf(travelled - total / 2, 300.0)
                val ahead = pointAt(route, minOf(total, total / 2 + 20))
                val heading = atan2(ahead[1] - half[1], ahead[0] - half[0]) + Math.PI / 2
                point = doubleArrayOf(half[0] + cos(heading) * side / 111_000, half[1] + sin(heading) * side / (111_000 * cos(Math.toRadians(half[0]))))
                if (side >= 300 && second % 10 == 0) Log.i(TAG, "detour reached, parked at ${point[0]},${point[1]}")
            } else if (travelled > 0) {
                val next = pointAt(route, minOf(travelled + 10, total))
                lastBearing = Math.toDegrees(atan2(next[1] - point[1], next[0] - point[0])).toFloat()
            }
            val fix = Location(GPS).apply {
                latitude = point[0]; longitude = point[1]; accuracy = 5f; bearing = lastBearing
                this.speed = if (travelled in 0.1..total) speed.toFloat() else 0f
                time = System.currentTimeMillis(); elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
            }
            try { manager.setTestProviderLocation(GPS, fix) } catch (e: Exception) { Log.e(TAG, "stopped: ${e.message}"); return }
            if (second % 5 == 0) Log.i(TAG, "t=${second}s along=${travelled.toInt()}/${total.toInt()} m at ${"%.6f".format(point[0])},${"%.6f".format(point[1])}")
            if (!detour && travelled > total + 60) { Log.i(TAG, "arrived; holding final position"); }
            if (second > delay + total / speed + 120) return
            SystemClock.sleep(1000)
        }
    }

    /** Point [metres] along the route. */
    private fun pointAt(route: NavRoute, metres: Double): DoubleArray {
        val c = route.cumulative
        var i = 1
        while (i < c.size - 1 && c[i] < metres) i++
        val span = (c[i] - c[i - 1]).takeIf { it > 0 } ?: 1.0
        val f = ((metres - c[i - 1]) / span).coerceIn(0.0, 1.0)
        val p = route.points
        return doubleArrayOf(p[(i - 1) * 2] + (p[i * 2] - p[(i - 1) * 2]) * f, p[(i - 1) * 2 + 1] + (p[i * 2 + 1] - p[(i - 1) * 2 + 1]) * f)
    }

    private fun coords(raw: String?): DoubleArray? = raw?.split(',')?.mapNotNull { it.trim().toDoubleOrNull() }?.takeIf { it.size == 2 }?.toDoubleArray()

    private companion object {
        const val TAG = "FlareSim"
        const val GPS = LocationManager.GPS_PROVIDER
    }
}
