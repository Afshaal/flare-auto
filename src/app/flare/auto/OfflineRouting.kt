package app.flare.auto

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.graphhopper.GHRequest
import com.graphhopper.GraphHopper
import com.graphhopper.ResponsePath
import com.graphhopper.util.shapes.GHPoint
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

    /**
     * [expected]: the pack the caller shows (map, search, coverage); the route fails instead of silently using a
     * different pack when the driver switched packs meanwhile. Null: whichever pack is selected.
     */
    fun route(fromLat: Double, fromLon: Double, toLat: Double, toLon: Double, expected: NavigationPack.Pack? = null,
              via: Pair<Double, Double>? = null, done: (Result<NavRoute>) -> Unit) {
        val token = ++generation
        worker.execute {
            val result = runCatching { calculate(fromLat, fromLon, toLat, toLon, expected, via) }
            main.post { if (token == generation) done(result) }
        }
    }

    /**
     * The fastest route, plus for trips between the major cities of [RouteCorridors] the routes through each listed
     * alternative's point (e.g. Lahore -> Islamabad by the N-5 or the M-2), fastest first. An option that fails, takes
     * the fastest route's main road anyway or is far slower is left out; elsewhere there is one route.
     */
    fun routes(fromLat: Double, fromLon: Double, toLat: Double, toLon: Double, expected: NavigationPack.Pack?,
               done: (Result<List<NavRoute>>) -> Unit) {
        val token = ++generation
        worker.execute {
            val result = runCatching {
                val fastest = calculate(fromLat, fromLon, toLat, toLon, expected, null)
                val mainRoad = RouteOptions.refs(fastest.roads).firstOrNull()
                val options = RouteCorridors.between(fromLat, fromLon, toLat, toLon).mapNotNull { corridor ->
                    runCatching { calculate(fromLat, fromLon, toLat, toLon, expected, corridor.lat to corridor.lon) }.getOrNull()
                        ?.takeIf { it.millis <= fastest.millis * MAX_OPTION_FACTOR && RouteOptions.refs(it.roads).firstOrNull() != mainRoad }
                }.distinctBy { RouteOptions.refs(it.roads).firstOrNull() }
                (listOf(fastest) + options).sortedBy { it.millis }
            }
            main.post { if (token == generation) done(result) }
        }
    }

    /** Blocking; routing thread only. [via]: route through that point (a chosen route option). */
    private fun calculate(fromLat: Double, fromLon: Double, toLat: Double, toLon: Double, expected: NavigationPack.Pack?,
                          via: Pair<Double, Double>?): NavRoute {
        val pack = when (val status = NavigationPack.verified(app)) {
            is NavigationPack.Status.Ready -> status.pack
            else -> error(status.message)
        }
        check(expected == null || expected.dir == pack.dir) { "Offline navigation pack changed to ${pack.region}; reopen the map" }
        check(pack.covers(fromLat, fromLon) && pack.covers(toLat, toLon)) { "Offline driving coverage is ${pack.region} and its surrounding approaches" }
        val engine = engineFor(pack)
        val through = via?.takeIf { pack.covers(it.first, it.second) }
        val request = if (through != null)
            // The via point is a point of the chosen route's own (one-way) carriageway, so it snaps to the right
            // direction; GraphHopper 1.0 CH does not support "pass_through" (checked on the Pakistan pack: no U-turn).
            GHRequest(listOf(GHPoint(fromLat, fromLon), GHPoint(through.first, through.second), GHPoint(toLat, toLon)))
        else GHRequest(fromLat, fromLon, toLat, toLon)
        request.setProfile(PROFILE).setLocale(Locale.ENGLISH)
        val response = engine.route(request)
        check(!response.hasErrors()) { response.errors.firstOrNull()?.message ?: "No driving route found" }
        return toRoute(response.best, fromLat, fromLon, toLat, toLon, through)
    }

    private fun toRoute(path: ResponsePath, fromLat: Double, fromLon: Double, toLat: Double, toLon: Double, via: Pair<Double, Double>?): NavRoute {
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
            NavStep(instruction.getTurnDescription(path.instructions.tr), instruction.sign, distances[index.coerceAtMost(distances.lastIndex)],
                instruction.name.orEmpty())
                .also { index += instruction.length }
        }
        return NavRoute(points, distances, steps, path.distance, path.time, path.instructions.map { it.name.orEmpty() to it.distance }, via)
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
        /** A route option may take at most this much longer than the fastest route. */
        private const val MAX_OPTION_FACTOR = 1.6
        /** Routing and pack I/O (search, verification) share one thread so they never compete for the graph. */
        val worker = Executors.newSingleThreadExecutor { Thread(it, "flare-routing").apply { isDaemon = true } }
    }
}
