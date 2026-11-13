package app.flare.auto

/**
 * Route options between major cities (Pakistan pack). GraphHopper's alternative route search takes 5-25 s on a
 * head unit, so the alternatives are found once on the PC (flare-lahore-tools/tools/java/RouteCorridors.java) and
 * kept here as one point on each alternative's own carriageway: the app routes the fastest way plus "through that
 * point" (cheap CH queries). Regenerate the table when the pack's road data changes a lot.
 */
internal object RouteCorridors {
    class City(val name: String, val lat: Double, val lon: Double)
    class Corridor(val from: String, val to: String, val lat: Double, val lon: Double, val road: String)

    /** A trip end within this distance of a city centre counts as that city. */
    const val CITY_RADIUS_M = 40000.0

    val CITIES = listOf(
        City("Lahore", 31.5204, 74.3587), City("Islamabad", 33.6844, 73.0479), City("Rawalpindi", 33.5651, 73.0169),
        City("Multan", 30.1575, 71.5249), City("Karachi", 24.8607, 67.0011))

    /** Generated from the Pakistan pack r3 graph ("<road>": the alternative's main road; times are GraphHopper estimates). */
    val CORRIDORS = listOf(
        Corridor("Lahore", "Islamabad", 32.041734, 73.080408, "M-2"), // 377 km 229 min; fastest 294 km 212 min
        Corridor("Lahore", "Rawalpindi", 32.041734, 73.080408, "M-2"), // 361 km 224 min; fastest 288 km 211 min
        Corridor("Lahore", "Multan", 30.785360, 73.488585, "N-5"), // 350 km 242 min; fastest 337 km 203 min
        Corridor("Lahore", "Multan", 31.922612, 73.332401, "M-4"), // 400 km 261 min; fastest 337 km 203 min
        Corridor("Islamabad", "Multan", 32.605783, 74.035142, "M-3"), // 612 km 394 min; fastest 538 km 342 min
        Corridor("Islamabad", "Multan", 31.890453, 72.301395, "N-5"), // 521 km 397 min; fastest 538 km 342 min
        Corridor("Islamabad", "Karachi", 32.605783, 74.035142, "M-3"), // 1492 km 922 min; fastest 1418 km 871 min
        Corridor("Rawalpindi", "Multan", 32.605783, 74.035142, "M-3"), // 606 km 391 min; fastest 526 km 342 min
        Corridor("Rawalpindi", "Multan", 31.890453, 72.301395, "N-5"), // 509 km 398 min; fastest 526 km 342 min
        Corridor("Rawalpindi", "Karachi", 32.605783, 74.035142, "M-3"), // 1486 km 920 min; fastest 1406 km 871 min
        Corridor("Multan", "Lahore", 30.785671, 73.488968, "N-5"), // 346 km 239 min; fastest 336 km 203 min
        Corridor("Multan", "Lahore", 31.928179, 73.325302, "M-4"), // 399 km 261 min; fastest 336 km 203 min
        Corridor("Multan", "Islamabad", 32.596665, 74.042184, "M-3"), // 608 km 391 min; fastest 538 km 345 min
        Corridor("Multan", "Islamabad", 32.074157, 72.195096, "M-2"), // 533 km 407 min; fastest 538 km 345 min
        Corridor("Multan", "Rawalpindi", 32.596665, 74.042184, "M-3"), // 602 km 391 min; fastest 523 km 340 min
        Corridor("Multan", "Rawalpindi", 32.074157, 72.195096, "M-2"), // 517 km 402 min; fastest 523 km 340 min
        Corridor("Karachi", "Islamabad", 32.596665, 74.042184, "M-3"), // 1498 km 925 min; fastest 1421 km 874 min
        Corridor("Karachi", "Rawalpindi", 32.596665, 74.042184, "M-3"), // 1492 km 925 min; fastest 1405 km 869 min
        Corridor("Islamabad", "Lahore", 32.042019, 73.080460, "M-2"), // 372 km 222 min; fastest 295 km 214 min
        Corridor("Rawalpindi", "Lahore", 32.042019, 73.080460, "M-2"), // 360 km 222 min; fastest 289 km 212 min
    )

    /** Nearest listed city within [CITY_RADIUS_M], or null. */
    fun city(lat: Double, lon: Double): City? =
        CITIES.map { it to NavGeometry.distance(lat, lon, it.lat, it.lon) }.filter { it.second <= CITY_RADIUS_M }.minByOrNull { it.second }?.first

    /** Alternatives for a trip between two listed cities (empty otherwise). */
    fun between(fromLat: Double, fromLon: Double, toLat: Double, toLon: Double): List<Corridor> {
        val a = city(fromLat, fromLon) ?: return emptyList()
        val b = city(toLat, toLon) ?: return emptyList()
        return CORRIDORS.filter { it.from == a.name && it.to == b.name }
    }
}
