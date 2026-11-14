package app.flare.auto

import org.junit.Assert.assertEquals
import org.junit.Test

/** Arrival estimates: road speeds off peak, and the peak-hour multiplier by journey length. */
class TrafficEtaTest {
    private val on = TrafficEta.Settings(true, TrafficEta.MORNING, TrafficEta.EVENING, TrafficEta.WEEKDAYS)
    private val monday = 1; private val saturday = 6
    private val morningPeak = 8 * 60.0 + 30 // 08:30, well inside 07:30-09:30
    private val noon = 12 * 60.0

    /** Straight route north, [legs] of (road name, metres), one instruction per leg. */
    private fun route(vararg legs: Pair<String, Double>): NavRoute {
        val total = legs.sumOf { it.second }
        val n = 100
        val metreLat = 1 / 111_320.0
        val pts = DoubleArray((n + 1) * 2) { i -> if (i % 2 == 0) 31.5 + total * metreLat * (i / 2) / n else 74.3 }
        val c = NavGeometry.cumulative(pts)
        var at = 0.0
        val steps = legs.map { (road, m) -> NavStep("Continue onto $road", 0, at, road).also { at += m * c.last() / total } } +
            NavStep("Arrive", 4, c.last())
        return NavRoute(pts, c, steps, c.last(), 1L)
    }

    private fun minutes(ms: Long) = ms / 60_000.0

    @Test
    fun roadSpeeds() {
        assertEquals(50.0, TrafficEta.speedKmh("Canal Bank Road"), 0.0)
        assertEquals(50.0, TrafficEta.speedKmh("East Canal Road"), 0.0)
        assertEquals(80.0, TrafficEta.speedKmh("Lahore Ring Road"), 0.0)
        assertEquals(80.0, TrafficEta.speedKmh("M-2 Motorway"), 0.0)
        assertEquals(80.0, TrafficEta.speedKmh("M2"), 0.0)
        assertEquals(40.0, TrafficEta.speedKmh("Jail Road"), 0.0)
        assertEquals(40.0, TrafficEta.speedKmh(""), 0.0)
        assertEquals(40.0, TrafficEta.speedKmh("Main Boulevard Gulberg"), 0.0)
    }

    @Test
    fun offPeakTimeFollowsEachRoad() {
        // 10 km of canal at 50 km/h = 12 min, 4 km of city road at 40 km/h = 6 min.
        val r = route("Canal Bank Road" to 10_000.0, "Jail Road" to 4_000.0)
        assertEquals(18.0, minutes(TrafficEta.total(r, TrafficEta.Schedule.NONE, r.distance)), 0.05)
        // Halfway along the canal stretch: 6 + 6 min left.
        assertEquals(12.0, minutes(TrafficEta.baseRemaining(r, r.distance * 5 / 14)), 0.05)
        // 16 km of Ring Road at 80 km/h = 12 min.
        val ring = route("Lahore Ring Road" to 16_000.0)
        assertEquals(12.0, minutes(TrafficEta.total(ring, TrafficEta.Schedule.NONE, ring.distance)), 0.05)
    }

    @Test
    fun peakMultiplierByJourneyLength() {
        val peak = TrafficEta.schedule(on, monday, morningPeak)
        assertEquals(1.0, peak.peak, 0.0)
        val long = route("Jail Road" to 8_000.0) // 12 min off peak
        assertEquals(12.0 * 1.8, minutes(TrafficEta.total(long, peak, long.distance)), 0.05)
        val short = route("Jail Road" to 4_000.0) // 6 min off peak
        assertEquals(6.0 * 1.2, minutes(TrafficEta.total(short, peak, short.distance)), 0.05)
        // The planned journey length decides, not what is left of it.
        assertEquals(1.8, TrafficEta.factor(peak, 8_000.0), 0.0)
        assertEquals(1.2, TrafficEta.factor(peak, 6_000.0), 0.0)
    }

    @Test
    fun offPeakWeekendsAndSwitchedOff() {
        val r = route("Jail Road" to 8_000.0)
        val base = TrafficEta.total(r, TrafficEta.Schedule.NONE, r.distance)
        assertEquals(base, TrafficEta.total(r, TrafficEta.schedule(on, monday, noon), r.distance))
        assertEquals(base, TrafficEta.total(r, TrafficEta.schedule(on, saturday, morningPeak), r.distance))
        assertEquals(base, TrafficEta.total(r, TrafficEta.schedule(on.copy(enabled = false), monday, morningPeak), r.distance))
    }

    @Test
    fun windowEdgesEaseIn() {
        val start = TrafficEta.MORNING.start.toDouble()
        assertEquals(0.0, TrafficEta.schedule(on, monday, start).peak, 1e-9)
        assertEquals(0.5, TrafficEta.schedule(on, monday, start + 7.5).peak, 1e-9)
        assertEquals(1.4, TrafficEta.factor(TrafficEta.schedule(on, monday, start + 7.5), 10_000.0), 1e-9)
    }

    @Test
    fun windowsParse() {
        assertEquals(TrafficEta.Window(7 * 60 + 30, 9 * 60 + 30), TrafficEta.Window.parse("07:30-09:30"))
        assertEquals(null, TrafficEta.Window.parse("09:30-07:30"))
    }
}
