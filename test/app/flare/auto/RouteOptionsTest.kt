package app.flare.auto

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Route option labels, summaries and the via point that keeps a chosen alternative. */
class RouteOptionsTest {
    // Road parts as GraphHopper names them on the Pakistan pack (Lahore -> Islamabad).
    private val gtRoad = listOf("Grand Trunk Road, N-5" to 130000.0, "Wazirabad Bypass, N-5" to 70000.0, "Ravi Road, N-5" to 55000.0, "T-Chowk Flyover" to 23000.0)
    private val motorway = listOf("Toll Plaza M-2" to 176000.0, "M-2" to 129000.0, "Toll Plaza M2 Motorway" to 37000.0, "" to 27000.0)

    private fun route(millis: Long, metres: Double, roads: List<Pair<String, Double>>, points: DoubleArray = doubleArrayOf(0.0, 0.0, 0.0, 0.0)) =
        NavRoute(points, NavGeometry.cumulative(points), emptyList(), metres, millis, roads)

    @Test
    fun refsAreMergedAndOrderedByLength() {
        assertEquals(listOf("N-5"), RouteOptions.refs(gtRoad))
        assertEquals(listOf("M-2"), RouteOptions.refs(motorway)) // "M-2", "M2" and "Toll Plaza M-2" are one road
        assertEquals(listOf("M-4", "M-2"), RouteOptions.refs(listOf("M-4" to 238000.0, "Toll Plaza M2 Motorway" to 105000.0)))
    }

    @Test
    fun labelsDifferBetweenOptions() {
        assertEquals(listOf("via N-5", "via M-2"), RouteOptions.labels(listOf(gtRoad, motorway)))
        // Lahore -> Multan: three options sharing the M-2 near Lahore still read differently.
        val m2 = listOf("Toll Plaza M2 Motorway" to 280000.0)
        val m4 = listOf("M-4" to 238000.0, "Toll Plaza M2 Motorway" to 105000.0)
        val n5 = listOf("National Highway, N-5" to 327000.0)
        assertEquals(listOf("via M-2", "via M-4", "via N-5"), RouteOptions.labels(listOf(m2, m4, n5)))
        // No references: the longest named road, else a number.
        assertEquals(listOf("via Canal Road", "Route 2"),
            RouteOptions.labels(listOf(listOf("Canal Road" to 5000.0, "Jail Road" to 1000.0), listOf("" to 4000.0))))
    }

    @Test
    fun summariesShowExtraTime() {
        val lines = RouteOptions.summaries(listOf(route(212 * 60000L, 293800.0, gtRoad), route(229 * 60000L, 376900.0, motorway)))
        assertEquals("Fastest · 3 h 32 min · 294 km · via N-5", lines[0])
        assertEquals("+17 min · 3 h 49 min · 377 km · via M-2", lines[1])
    }

    @Test
    fun viaPointLiesOnTheDistinctPartOfTheChosenRoute() {
        // Fastest goes straight north; the alternative bows ~20 km east in the middle.
        val fastest = DoubleArray(202) { i -> if (i % 2 == 0) 31.5 + (i / 2) * 0.02 else 74.3 }
        val alternative = DoubleArray(202) { i ->
            val k = i / 2
            if (i % 2 == 0) 31.5 + k * 0.02 else 74.3 + 0.2 * Math.sin(Math.PI * k / 100)
        }
        val via = RouteOptions.viaPoint(alternative, fastest)
        assertNotNull(via)
        assertEquals(32.5, via!!.first, 0.1) // the middle, where the roads are furthest apart
        assertTrue(via.second > 74.45)
        assertNull("a route that never leaves the fastest one needs no via point", RouteOptions.viaPoint(fastest, fastest))
    }

    @Test
    fun corridorsCoverTheMajorCityPairs() {
        val names = RouteCorridors.CITIES.map { it.name }.toSet()
        assertEquals(setOf("Lahore", "Islamabad", "Rawalpindi", "Multan", "Karachi"), names)
        for (c in RouteCorridors.CORRIDORS) {
            assertTrue(c.from in names && c.to in names && c.from != c.to)
            assertTrue(c.lat in 23.35..37.15 && c.lon in 60.8..77.25) // inside the Pakistan pack
        }
        // Both directions between Lahore and Islamabad offer the M-2 (the fastest is the N-5).
        val gulberg = 31.5102 to 74.3441
        val blueArea = 33.7104 to 73.0563
        assertEquals(listOf("M-2"), RouteCorridors.between(gulberg.first, gulberg.second, blueArea.first, blueArea.second).map { it.road })
        assertEquals(listOf("M-2"), RouteCorridors.between(blueArea.first, blueArea.second, gulberg.first, gulberg.second).map { it.road })
        assertEquals("Rawalpindi", RouteCorridors.city(33.60, 73.04)?.name) // nearest of the twin cities
        assertTrue("trips inside one city get no options", RouteCorridors.between(31.51, 74.34, 31.58, 74.31).isEmpty())
        assertTrue("towns outside the list get no options", RouteCorridors.between(31.51, 74.34, 31.42, 73.08).isEmpty()) // Faisalabad
    }

    @Test
    fun viaPointSurvivesSavingAndRejectsJunk() {
        assertEquals(32.1 to 73.4, RouteOptions.decode(RouteOptions.encode(32.1 to 73.4)))
        for (bad in listOf(null, "", "x,y", "1", "1,2,3", "95,10", "NaN,10")) assertNull(bad, RouteOptions.decode(bad))
    }
}
