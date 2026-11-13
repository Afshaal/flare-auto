package app.flare.auto

import android.app.Activity
import android.os.Bundle
import android.os.Debug
import android.os.SystemClock
import android.util.Log
import android.widget.ScrollView
import android.widget.TextView
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Debug builds only. Switches between every installed navigation pack (e.g. lahore and pakistan) and checks that
 * search, routing and coverage follow the selected pack, then restores the previous choice:
 *
 *     adb shell am start -n app.flare.auto/.SmokeActivity --es open packs
 *     adb logcat -s FlarePacks
 *
 * Lines start with PASS / FAIL / INFO.
 */
class PackSwitchCheckActivity : Activity() {
    private lateinit var output: TextView
    private val routing by lazy { OfflineRouting(this) }

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        output = TextView(this).apply { textSize = 14f; setPadding(dp(16), dp(16), dp(16), dp(16)) }
        setContentView(ScrollView(this).apply { addView(output) })
        Thread({ run() }, "pack-switch-check").start()
    }

    private fun run() {
        val before = NavigationPack.selected(this)
        val packs = NavigationPack.installed(this)
        info("Installed: ${packs.joinToString { it.name }} · selected before: $before")
        var previous: NavigationPack.Pack? = null
        try {
            for (dir in packs) {
                NavigationPack.select(this, dir.name)
                val started = SystemClock.elapsedRealtime()
                val status = NavigationPack.verified(this)
                info("${dir.name}: ${status.message} (status + checksums ${SystemClock.elapsedRealtime() - started} ms)")
                val pack = (status as? NavigationPack.Status.Ready)?.pack ?: continue
                check(pack.dir == dir, "${dir.name}: selected pack is used (${pack.dir.name})")
                check(NavigationPack.status(this).let { it is NavigationPack.Status.Ready && it.pack.dir == dir }, "${dir.name}: status() agrees")
                for ((query, expect) in SEARCHES) {
                    val t0 = SystemClock.elapsedRealtime()
                    val hits = OfflineSearch.find(pack.search, query, LAHORE[0], LAHORE[1])
                    val ms = SystemClock.elapsedRealtime() - t0
                    val top = hits.firstOrNull()
                    val label = top?.let { "${it.hit.name} [${OfflineSearch.detail(it.hit)}] ${it.hit.osm} ${OfflineSearch.distanceLabel(it.metres)}" } ?: "no result"
                    val covered = expect.second.let { pack.covers(it[0], it[1]) }
                    if (!covered) info("${dir.name}: search '$query' (${ms} ms) -> $label (outside coverage, not checked)")
                    else check(hits.take(5).any { OfflineSearch.normalize(it.hit.name).contains(expect.first) },
                        "${dir.name}: search '$query' (${ms} ms) -> $label")
                }
                for ((name, trip) in TRIPS) {
                    val inside = pack.covers(trip[0], trip[1]) && pack.covers(trip[2], trip[3])
                    val (result, ms) = route(trip, pack)
                    val text = result.fold({ "%.1f km, %d steps".format(it.distance / 1000, it.steps.size) }, { "error: ${it.message}" })
                    check(result.isSuccess == inside, "${dir.name}: $name ${if (inside) "routes" else "is refused (outside coverage)"} — $text, $ms ms")
                }
                previous?.let { old ->
                    val (stale, _) = route(TRIPS[0].second, old)
                    check(stale.isFailure && stale.exceptionOrNull()?.message.orEmpty().contains("changed"),
                        "${dir.name}: a route requested for the previous pack (${old.dir.name}) is refused, not mixed")
                }
                previous = pack
                info("${dir.name}: Java heap ${(Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / 1048576} MB · PSS ${Debug.getPss() / 1024} MB")
            }
        } finally {
            if (before != null) NavigationPack.select(this, before)
            info("Selection restored to: ${NavigationPack.selected(this)} · DONE")
        }
    }

    private fun route(trip: DoubleArray, pack: NavigationPack.Pack): Pair<Result<NavRoute>, Long> {
        val latch = CountDownLatch(1)
        var outcome: Result<NavRoute> = Result.failure(IllegalStateException("timeout"))
        val t0 = SystemClock.elapsedRealtime()
        runOnUiThread { routing.route(trip[0], trip[1], trip[2], trip[3], pack) { outcome = it; latch.countDown() } }
        latch.await(120, TimeUnit.SECONDS)
        return outcome to SystemClock.elapsedRealtime() - t0
    }

    private fun check(ok: Boolean, line: String) = report((if (ok) "PASS " else "FAIL ") + line)
    private fun info(line: String) = report("INFO $line")
    private fun report(line: String) {
        Log.i("FlarePacks", line)
        runOnUiThread { output.append(line + "\n") }
    }

    override fun onDestroy() {
        routing.close()
        super.onDestroy()
    }

    private companion object {
        val LAHORE = doubleArrayOf(31.5204, 74.3587)
        /** Query to (normalized text the top five must contain, a point that must be covered for the check). */
        val SEARCHES = listOf(
            "liberty" to ("liberty" to doubleArrayOf(31.51, 74.34)),
            "data darbar" to ("data darbar" to doubleArrayOf(31.58, 74.30)),
            "dha phase 6" to ("dha phase 6" to doubleArrayOf(31.47, 74.45)),
            "داتا دربار" to ("دربار" to doubleArrayOf(31.58, 74.30)),
            "quaid e azam university" to ("quaid e azam university" to doubleArrayOf(33.75, 73.14)),
            "clifton" to ("clifton" to doubleArrayOf(24.81, 67.03)))
        val TRIPS = listOf(
            "Liberty → Badshahi Mosque" to doubleArrayOf(31.5106, 74.3446, 31.5880, 74.3107),
            "Lahore → Islamabad" to doubleArrayOf(31.5204, 74.3587, 33.6844, 73.0479),
            "Karachi Clifton → Saddar" to doubleArrayOf(24.8145, 67.0305, 24.8560, 67.0120))
    }
}
