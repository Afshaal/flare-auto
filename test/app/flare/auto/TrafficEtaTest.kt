package app.flare.auto

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Typical traffic adjustment: scaling, schedule, areas, timing. Multipliers are user settings, not calibrated data. */
class TrafficEtaTest {
    private val on = TrafficEta.Settings(true, 2.0, TrafficEta.MORNING, TrafficEta.EVENING, true, TrafficEta.SCHOOL, TrafficEta.WEEKDAYS)
    private val monday = 1; private val saturday = 6
    private val morningPeak = 8 * 60.0 + 30 // 08:30, well inside 07:30-09:30
    private val noon = 12 * 60.0

    /** Straight route from (lat0, lon0) to (lat1, lon1) in [n] pieces; one instruction of [millis]. */
    private fun line(lat0: Double, lon0: Double, lat1: Double, lon1: Double, millis: Long, n: Int = 40): NavRoute {
        val pts = DoubleArray((n + 1) * 2) { i -> val k = i / 2; if (i % 2 == 0) lat0 + (lat1 - lat0) * k / n else lon0 + (lon1 - lon0) * k / n }
        val c = NavGeometry.cumulative(pts)
        return NavRoute(pts, c, listOf(NavStep("Head", 0, 0.0), NavStep("Arrive", 4, c.last())), c.last(), millis,
            stepMillis = longArrayOf(millis, 0))
    }

    /** Wapda Town to Barkat Market (inside the area). */
    private fun commute(millis: Long = 1_200_000) = line(31.43688, 74.26998, 31.49997, 74.32109, millis)
    /** DHA, outside both areas. */
    private fun elsewhere() = line(31.47, 74.40, 31.47, 74.50, 900_000)

    @Test
    fun distanceScaling() {
        val full = TrafficEta.Schedule(2.0, 1.0, 1.0)
        assertEquals(1.0, TrafficEta.factors(full, 5_000.0).first, 1e-9)
        assertEquals(1.0, TrafficEta.factors(full, 3_000.0).first, 1e-9)
        assertEquals(1.5, TrafficEta.factors(full, 7_500.0).first, 1e-9)
        assertEquals(2.0, TrafficEta.factors(full, 10_000.0).first, 1e-9)
        assertEquals(2.0, TrafficEta.factors(full, 40_000.0).first, 1e-9)
        assertEquals(1.4, TrafficEta.factors(TrafficEta.Schedule(1.8, 1.0, 0.0), 7_500.0).first, 1e-9)
    }

    @Test
    fun offNonPeakAndOutsideAreaKeepTheBaseEstimate() {
        val r = commute()
        assertTrue(r.distance > 8_000)
        val base = TrafficEta.baseRemaining(r, 0.0)
        assertEquals(r.millis, base)
        // Off.
        assertEquals(base, TrafficEta.total(r, TrafficEta.schedule(on.copy(enabled = false), monday, morningPeak), r.distance))
        // On, but outside every window (no extra off-peak factor).
        assertEquals(base, TrafficEta.total(r, TrafficEta.schedule(on, monday, noon), r.distance))
        // On and at peak, but the route is outside both areas.
        val dha = elsewhere()
        assertEquals(dha.millis, TrafficEta.total(dha, TrafficEta.schedule(on, monday, morningPeak), dha.distance))
        // On and at peak inside the area: adjusted.
        assertTrue(TrafficEta.total(r, TrafficEta.schedule(on, monday, morningPeak), r.distance) > base)
    }

    @Test
    fun weekdaysAndWindowBoundaries() {
        val w = TrafficEta.MORNING // 450..570 minutes
        assertEquals(0.0, TrafficEta.weight(w, 449.9), 1e-9)
        assertEquals(0.0, TrafficEta.weight(w, 450.0), 1e-9) // ramps up from the start
        assertEquals(0.5, TrafficEta.weight(w, 457.5), 1e-9)
        assertEquals(1.0, TrafficEta.weight(w, 465.0), 1e-9)
        assertEquals(1.0, TrafficEta.weight(w, 555.0), 1e-9)
        assertEquals(0.5, TrafficEta.weight(w, 562.5), 1e-9)
        assertEquals(0.0, TrafficEta.weight(w, 570.0), 1e-9) // ends at the window end
        assertEquals(TrafficEta.Schedule.NONE, TrafficEta.schedule(on, saturday, morningPeak))
        val weekend = on.copy(days = TrafficEta.WEEKDAYS or (1 shl 5))
        assertEquals(1.0, TrafficEta.schedule(weekend, saturday, morningPeak).area, 1e-9)
        // School window only on the school corridor, and only when that part is switched on.
        val school = 14 * 60.0
        assertEquals(0.0, TrafficEta.schedule(on, monday, school).area, 1e-9)
        assertEquals(1.0, TrafficEta.schedule(on, monday, school).school, 1e-9)
        assertEquals(0.0, TrafficEta.schedule(on.copy(schoolEnabled = false), monday, school).school, 1e-9)
        assertEquals(TrafficEta.Window(450, 570), TrafficEta.Window.parse("07:30-09:30"))
        assertEquals(TrafficEta.Window(990, 1170), TrafficEta.Window.parse(" 16:30 – 19:30 "))
        assertNull(TrafficEta.Window.parse("09:30-07:30"))
        assertNull(TrafficEta.Window.parse("7:3-9"))
    }

    @Test
    fun overlappingWindowsNeverCompound() {
        // Evening moved to overlap the morning: still one 2.0x factor, not 4.0x.
        val overlap = on.copy(evening = TrafficEta.Window(8 * 60, 10 * 60))
        val s = TrafficEta.schedule(overlap, monday, morningPeak)
        assertEquals(1.0, s.area, 1e-9)
        val r = commute()
        val single = TrafficEta.total(r, TrafficEta.schedule(on, monday, morningPeak), r.distance)
        assertEquals(single, TrafficEta.total(r, s, r.distance))
        assertTrue(single <= r.millis * 2)
    }

    @Test
    fun onlyTheStretchInsideTheAreaIsAdjusted() {
        // Half in DHA (outside), half along the commute (inside): same speed on both halves.
        val pts = DoubleArray(82)
        val outside = elsewhere().points; val inside = commute().points
        for (i in 0..40) { val src = if (i <= 20) outside else inside; val k = if (i <= 20) i * 2 else (i - 20) * 2
            pts[i * 2] = src[k]; pts[i * 2 + 1] = src[k + 1] }
        val c = NavGeometry.cumulative(pts)
        val r = NavRoute(pts, c, listOf(NavStep("Head", 0, 0.0)), c.last(), 3_000_000, stepMillis = longArrayOf(3_000_000))
        val s = TrafficEta.schedule(on, monday, morningPeak)
        val adjusted = TrafficEta.total(r, s, 20_000.0)
        val profile = TrafficEta.profile(r)
        val inArea = profile.area.last()
        assertTrue("part of the route in the area: $inArea", inArea > 0 && inArea < r.millis)
        assertEquals(r.millis + inArea, adjusted.toDouble(), 2.0) // only that part doubled
        assertTrue(adjusted < r.millis * 2)
    }

    @Test
    fun remainingTimeFollowsTheSpeedOfEachInstruction() {
        // 1 km at 60 s, then 1 km at 300 s (slow street): not one average speed.
        val pts = DoubleArray(42) { i -> if (i % 2 == 0) 31.47 + (i / 2) * 0.0009 else 74.45 }
        val c = NavGeometry.cumulative(pts)
        val half = c[10]
        val r = NavRoute(pts, c, listOf(NavStep("A", 0, 0.0), NavStep("B", 2, half)), c.last(), 360_000, stepMillis = longArrayOf(60_000, 300_000))
        assertEquals(360_000L, TrafficEta.baseRemaining(r, 0.0))
        assertEquals(300_000L, TrafficEta.baseRemaining(r, half))
        assertEquals(330_000.0, TrafficEta.baseRemaining(r, half / 2).toDouble(), 5.0)
        assertEquals(150_000.0, TrafficEta.baseRemaining(r, half + (c.last() - half) / 2).toDouble(), 5.0)
        // Old routes without instruction times: average speed, as before.
        val old = r.copy(stepMillis = null)
        assertEquals(180_000.0, TrafficEta.baseRemaining(old, half).toDouble(), 5.0)
    }

    @Test
    fun turnTimeGoesToTheManoeuvres() {
        val ms = OfflineRouting.stepMillis(listOf(10_000L, 20_000L, 30_000L), 70_000L)
        assertEquals(70_000L, ms.sum())
        assertEquals(listOf(10_000L, 25_000L, 35_000L), ms.toList()) // 10 s of turning shared by the two manoeuvres
        val over = OfflineRouting.stepMillis(listOf(30_000L, 30_000L), 50_000L)
        assertEquals(50_000L, over.sum())
    }

    @Test
    fun journeyLengthNotRemainingLengthDecidesTheScaling() {
        val r = commute()
        val s = TrafficEta.schedule(on, monday, morningPeak)
        // 8.6 km route; near the end only ~1 km remains. With the journey length kept, the stretch left keeps the
        // journey's factor; judging by the shrinking remaining distance would drop the adjustment.
        val near = r.cumulative.last() - 1_000
        val base = TrafficEta.baseRemaining(r, near)
        val kept = TrafficEta.remaining(r, near, s, r.distance)
        val shrinking = TrafficEta.remaining(r, near, s, 1_000.0)
        assertEquals(base, shrinking)
        assertTrue(kept > base)
        // A reroute (new, shorter route) of a 12 km journey keeps the full factor.
        val reroute = commute(600_000)
        assertEquals(reroute.millis * 2.0, TrafficEta.total(reroute, s, 12_000.0).toDouble(), 2.0)
    }

    @Test
    fun previewSummaryAndNavigationUseTheSameEstimate() {
        val r = commute()
        val s = TrafficEta.schedule(on, monday, morningPeak)
        val total = TrafficEta.total(r, s, r.distance)
        assertEquals(total, TrafficEta.remaining(r, 0.0, s, r.distance))
        val line = RouteOptions.summaries(listOf(r)) { TrafficEta.total(it, s, it.distance) }.single()
        assertTrue(line, line.contains(TripFormat.duration(total)))
        assertFalse(line.contains(TripFormat.duration(r.millis)))
        // Without adjustment the summaries are unchanged.
        assertEquals(RouteOptions.summaries(listOf(r)), RouteOptions.summaries(listOf(r)) { TrafficEta.total(it, TrafficEta.Schedule.NONE, it.distance) })
    }

    @Test
    fun jailRoadCountsOnlyAlongTheRoad() {
        // Along a mapped Jail Road way vs across it.
        assertTrue(TrafficEta.alongCorridor(31.53517, 74.34169, 31.53445, 74.34721))
        assertFalse(TrafficEta.alongCorridor(31.5300, 74.3440, 31.5400, 74.3445))
        // The Wapda Town - Barkat band excludes the M-2 / Thokar side and DHA.
        assertTrue(TrafficEta.inside(TrafficEta.WAPDA_BARKAT, 31.43688, 74.26998))
        assertTrue(TrafficEta.inside(TrafficEta.WAPDA_BARKAT, 31.49997, 74.32109))
        assertFalse(TrafficEta.inside(TrafficEta.WAPDA_BARKAT, 31.4700, 74.2400))
        assertFalse(TrafficEta.inside(TrafficEta.WAPDA_BARKAT, 31.4700, 74.4000))
    }
}
