package app.flare.auto

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.graphhopper.GHRequest
import com.graphhopper.GraphHopper
import com.graphhopper.config.CHProfile
import com.graphhopper.config.Profile
import java.io.File
import java.util.Locale
import java.util.concurrent.Executors

/**
 * Offline driving routes from the navigation pack ([NavigationPack]).
 *
 * The graph is opened lazily on the first request, memory-mapped (GraphHopper "forMobile"; the PC benchmark in
 * flare-lahore-tools/reports measured ~9 MB of Java heap after load versus ~131 MB fully in RAM) and released by
 * [close]. All work runs on one routing thread; results are delivered on the main thread. Any failure, including
 * a missing pack or running out of memory, is delivered as a failed [Result], never thrown.
 *
 * Keep the load configuration identical to flare-lahore-tools/tools/java/LahoreTool.java#loadLikeApp.
 */
internal class OfflineRouting(c: Context) {
    private val app = c.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private var hopper: GraphHopper? = null
    private var loadedFrom: File? = null
    private var generation = 0

    fun route(fromLat: Double, fromLon: Double, toLat: Double, toLon: Double, done: (Result<NavRoute>) -> Unit) {
        val token = ++generation
        worker.execute {
            val result = runCatching { calculate(fromLat, fromLon, toLat, toLon) }
            main.post { if (token == generation) done(result) }
        }
    }

    /** Blocking; routing thread only. */
    private fun calculate(fromLat: Double, fromLon: Double, toLat: Double, toLon: Double): NavRoute {
        val pack = when (val status = NavigationPack.verified(app)) {
            is NavigationPack.Status.Ready -> status.pack
            else -> error(status.message)
        }
        check(pack.covers(fromLat, fromLon) && pack.covers(toLat, toLon)) { "Offline driving coverage is ${pack.region} and its surrounding approaches" }
        val engine = engineFor(pack)
        val response = engine.route(GHRequest(fromLat, fromLon, toLat, toLon).setProfile(PROFILE).setLocale(Locale.ENGLISH))
        check(!response.hasErrors()) { response.errors.firstOrNull()?.message ?: "No driving route found" }
        val path = response.best
        val geometry = path.points
        check(geometry.size() >= 2) { "No driving route found" }
        check(NavGeometry.distance(fromLat, fromLon, geometry.getLat(0), geometry.getLon(0)) < SNAP_LIMIT_M &&
            NavGeometry.distance(toLat, toLon, geometry.getLat(geometry.size() - 1), geometry.getLon(geometry.size() - 1)) < SNAP_LIMIT_M) {
            "No nearby drivable road for the start or destination"
        }
        val points = DoubleArray(geometry.size() * 2)
        for (i in 0 until geometry.size()) {
            points[i * 2] = geometry.getLat(i)
            points[i * 2 + 1] = geometry.getLon(i)
        }
        val distances = NavGeometry.cumulative(points)
        var index = 0
        val steps = path.instructions.map { instruction ->
            NavStep(instruction.getTurnDescription(path.instructions.tr), instruction.sign, distances[index.coerceAtMost(distances.lastIndex)])
                .also { index += instruction.length }
        }
        return NavRoute(points, distances, steps, path.distance, path.time)
    }

    private fun engineFor(pack: NavigationPack.Pack): GraphHopper {
        hopper?.let { if (loadedFrom == pack.graph) return it }
        hopper?.close()
        hopper = null
        val engine = GraphHopper().forMobile()
            .setProfiles(Profile(PROFILE).setVehicle("car").setWeighting("fastest").setTurnCosts(true))
        engine.chPreparationHandler.setCHProfiles(CHProfile(PROFILE))
        engine.setAllowWrites(false)
        check(engine.load(pack.graph.absolutePath)) { "${pack.region} routing data could not be loaded" }
        hopper = engine
        loadedFrom = pack.graph
        return engine
    }

    /** Cancels pending results and releases the graph. */
    fun close() {
        generation++
        worker.execute {
            hopper?.close()
            hopper = null
            loadedFrom = null
        }
    }

    companion object {
        private const val PROFILE = "car"
        /** Start and destination must be this close to a drivable road. */
        private const val SNAP_LIMIT_M = 1000
        /** Routing and pack I/O (search, verification) share one thread so they never compete for the graph. */
        val worker = Executors.newSingleThreadExecutor { Thread(it, "flare-routing").apply { isDaemon = true } }
    }
}
