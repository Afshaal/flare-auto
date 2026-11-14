package app.flare.auto

/**
 * Choosing between route options (the fastest route and the [RouteCorridors] alternatives, e.g. Lahore → Islamabad
 * by GT Road / N-5 or by the M-2): option labels and the point that keeps a chosen alternative when rerouting.
 * Pure, unit tested (RouteOptionsTest).
 */
internal object RouteOptions {
    /** An alternative must leave the fastest route by at least this much to be kept on reroutes. */
    private const val MIN_DETOUR_M = 2000.0
    /** Motorway / national highway references in Pakistani road names: "M-2", "M2", "N-5", "N 5". */
    private val REF = Regex("\\b([MN])\\s?-?\\s?(\\d{1,3})\\b")

    /** Road references along a route, longest first ("N-5", "M-2"), from (road name, metres) parts. */
    fun refs(roads: List<Pair<String, Double>>): List<String> {
        val metres = LinkedHashMap<String, Double>()
        for ((name, length) in roads) for (m in REF.findAll(name)) {
            val ref = "${m.groupValues[1]}-${m.groupValues[2]}"
            metres[ref] = (metres[ref] ?: 0.0) + length
        }
        return metres.entries.sortedByDescending { it.value }.map { it.key }
    }

    /**
     * "via …" for each route: its longest road reference not already used by an earlier option (so the options read
     * differently), else its longest reference, else its longest named road, else "Route n".
     */
    fun labels(routes: List<List<Pair<String, Double>>>): List<String> {
        val used = HashSet<String>()
        return routes.mapIndexed { i, roads ->
            val refs = refs(roads)
            val pick = refs.firstOrNull { it !in used } ?: refs.firstOrNull()
                ?: roads.filter { it.first.isNotBlank() }.groupBy { it.first }.maxByOrNull { e -> e.value.sumOf { it.second } }?.key
            if (pick != null) { used.add(pick); "via $pick" } else "Route ${i + 1}"
        }
    }

    /** One line per option: "Fastest · 3 h 32 min · 294 km · via N-5", "+17 min · 3 h 49 min · 377 km · via M-2". */
    /** One line per option; durations from [eta] (the displayed estimate, [TrafficEta]; raw time by default). */
    fun summaries(routes: List<NavRoute>, eta: (NavRoute) -> Long = { it.millis }): List<String> {
        val labels = labels(routes.map { it.roads })
        val times = routes.map(eta)
        val fastest = times.minOrNull() ?: 0L
        return routes.mapIndexed { i, r ->
            val extra = times[i] - fastest
            val head = if (extra < 60000) "Fastest" else "+${TripFormat.duration(extra)}"
            "$head · ${TripFormat.duration(times[i])} · ${TripFormat.distance(r.distance)} · ${labels[i]}"
        }
    }

    /**
     * A point on [chosen] (lat, lon pairs) as far as possible from [fastest]: routing through it keeps the chosen
     * road when the driver goes off route. Null when [chosen] never leaves [fastest] by [MIN_DETOUR_M].
     */
    fun viaPoint(chosen: DoubleArray, fastest: DoubleArray): Pair<Double, Double>? {
        val step = maxOf(1, chosen.size / 2 / 400)
        val sample = maxOf(1, fastest.size / 2 / 2000)
        var best: Pair<Double, Double>? = null
        var bestAway = MIN_DETOUR_M
        var i = 0
        while (i < chosen.size / 2) {
            val lat = chosen[i * 2]; val lon = chosen[i * 2 + 1]
            var away = Double.POSITIVE_INFINITY
            var j = 0
            while (j < fastest.size / 2 && away > bestAway) {
                away = minOf(away, NavGeometry.distance(lat, lon, fastest[j * 2], fastest[j * 2 + 1]))
                j += sample
            }
            if (away > bestAway) { bestAway = away; best = lat to lon }
            i += step
        }
        return best
    }

    /** Saved form of a via point ([Keys.OFFLINE_NAV_VIA]): "lat,lon". */
    fun encode(point: Pair<Double, Double>) = "${point.first},${point.second}"
    fun decode(text: String?): Pair<Double, Double>? {
        val parts = text?.split(',') ?: return null
        val lat = parts.getOrNull(0)?.toDoubleOrNull() ?: return null
        val lon = parts.getOrNull(1)?.toDoubleOrNull() ?: return null
        return if (parts.size == 2 && lat.isFinite() && lon.isFinite() && lat in -90.0..90.0 && lon in -180.0..180.0) lat to lon else null
    }

    /**
     * Which of [routes] a map tap at [lat], [lon] hit (within [tolerance] metres of its line), or null. Where routes
     * run together (their shared start) the [selected] one wins unless another is clearly closer, so a tap on a
     * shared road counts as the selected route.
     */
    fun tapped(routes: List<NavRoute>, selected: Int, lat: Double, lon: Double, tolerance: Double): Int? {
        val away = routes.map { NavGeometry.nearest(it, lat, lon, 0, full = true).away }
        val best = away.indices.filter { away[it] <= tolerance }.minByOrNull { away[it] } ?: return null
        return if (selected in away.indices && away[selected] <= tolerance && away[selected] - away[best] < SHARED_M) selected else best
    }

    /** [tapped]: lines closer together than this (metres) at the tap are treated as the same road. */
    private const val SHARED_M = 3.0
}
