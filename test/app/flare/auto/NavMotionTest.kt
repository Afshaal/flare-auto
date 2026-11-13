package app.flare.auto

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Marker snapping, gliding, street name, alternative ETA comparison and split point. */
class NavMotionTest {
    /** A straight road due north from (31.50, 74.30), 1 km, with two named legs. */
    private fun north(): NavRoute {
        val points = DoubleArray(22) { i -> if (i % 2 == 0) 31.50 + (i / 2) * 0.0009 else 74.30 }
        return NavRoute(points, NavGeometry.cumulative(points),
            listOf(NavStep("Continue onto Canal Road", 0, 0.0, "Canal Road"), NavStep("Continue onto Mall Road", 0, 500.0, "Mall Road"),
                NavStep("Arrive", 4, 999.0, "")), 1000.0, 60_000)
    }
    private val metreLon = 1 / (111_320 * Math.cos(Math.toRadians(31.5)))

    @Test
    fun snapsOnlyConfidentFixes() {
        val r = north()
        // 10 m east of the road, driving north at 10 m/s, accuracy 10 m: on the line.
        val on = NavMotion.snap(r, 31.5045, 74.30 + 10 * metreLon, 10f, 2f, 10f, 0)
        assertNotNull(on)
        assertEquals(74.30, on!!.lon, 1e-7)
        assertEquals(0.0, on.bearing, 1.0)
        // Driving the other way, too far, inaccurate, or no accuracy: raw position.
        assertNull(NavMotion.snap(r, 31.5045, 74.30 + 10 * metreLon, 10f, 180f, 10f, 0))
        assertNull(NavMotion.snap(r, 31.5045, 74.30 + 25 * metreLon, 10f, 0f, 10f, 0))
        assertNull(NavMotion.snap(r, 31.5045, 74.30 + 5 * metreLon, 40f, 0f, 10f, 0))
        assertNull(NavMotion.snap(r, 31.5045, 74.30 + 5 * metreLon, null, 0f, 10f, 0))
        // Stopped (no trustworthy direction): only very close fixes.
        assertNotNull(NavMotion.snap(r, 31.5045, 74.30 + 4 * metreLon, 8f, null, 0f, 0))
        assertNull(NavMotion.snap(r, 31.5045, 74.30 + 9 * metreLon, 8f, null, 0f, 0))
        assertNull(NavMotion.snap(null, 31.5045, 74.30, 5f, 0f, 10f, 0))
    }

    @Test
    fun glidesBetweenFixesButNeverPredicts() {
        assertEquals(1000L, NavMotion.glide(1000, 12.0))
        assertEquals(1000L, NavMotion.glide(2000, 20.0)) // capped: the marker then waits at the fix
        assertEquals(250L, NavMotion.glide(100, 1.0))
        assertNull("first fix", NavMotion.glide(0, 5.0))
        assertNull("GPS pause", NavMotion.glide(6000, 5.0))
        assertNull("jump", NavMotion.glide(1000, 400.0))
        assertEquals(350.0, NavMotion.turn(10.0, 330.0, 0.5f), 1e-9) // the short way across north
    }

    @Test
    fun glideFramesOnlyWhenThePictureVisiblyChanges() {
        // Close view: 12 m/s is ~60 px per fix, so the full ~30 fps.
        assertEquals(32L, NavMotion.glideFrameMs(1000, 60.0, 0.0))
        // Wide tilted view (zoom ~12.5): under a pixel per fix and no turn, drawn once per fix.
        assertNull(NavMotion.glideFrameMs(1000, 0.5, 0.0))
        // Same view while turning 10° heading-up: the map rotates visibly, about one frame per degree.
        assertEquals(100L, NavMotion.glideFrameMs(1000, 0.5, 10.0))
        assertEquals(200L, NavMotion.glideFrameMs(1000, 7.5, 0.0))
        assertEquals(32L, NavMotion.glideFrameMs(1000, Double.POSITIVE_INFINITY, 0.0))
    }

    @Test
    fun streetComesFromTheRouteOrNotAtAll() {
        val r = north()
        assertEquals("Canal Road", NavMotion.street(r, 120.0))
        assertEquals("Mall Road", NavMotion.street(r, 640.0))
        assertNull(NavMotion.street(r, 999.5)) // unnamed final step
        assertNull(NavMotion.street(null, 10.0))
    }

    @Test
    fun alternativeEtaComparison() {
        assertEquals("Similar ETA", NavMotion.compare(61 * 60_000L, 60 * 60_000L))
        assertEquals("+16 min", NavMotion.compare(228 * 60_000L, 212 * 60_000L))
        assertEquals("5 min faster", NavMotion.compare(55 * 60_000L, 60 * 60_000L))
    }

    @Test
    fun alternativeSplitPoint() {
        val chosen = north()
        // Same road for 400 m, then bends east.
        val pts = DoubleArray(22) { i ->
            val k = i / 2
            if (i % 2 == 0) 31.50 + k * 0.0009 else 74.30 + (if (k > 4) (k - 4) * 0.001 else 0.0)
        }
        val alternative = NavRoute(pts, NavGeometry.cumulative(pts), emptyList(), 1100.0, 66_000)
        val split = NavMotion.divergesAt(chosen, alternative)
        assertNotNull(split)
        assertTrue("split near 400 m: $split", split!! in 380.0..520.0)
        assertNull(NavMotion.divergesAt(chosen, chosen))
        // A 300 m jog onto a parallel street that rejoins is not the split: the real turn-off (at 800 m) is.
        val jog = DoubleArray(22) { i ->
            val k = i / 2
            if (i % 2 == 0) 31.50 + k * 0.0009 else 74.30 + (if (k in 3..5) 100 * metreLon else 0.0) + (if (k > 8) (k - 8) * 0.01 else 0.0)
        }
        val late = NavMotion.divergesAt(chosen, NavRoute(jog, NavGeometry.cumulative(jog), emptyList(), 1300.0, 70_000))
        assertNotNull(late)
        assertTrue("split near 800 m: $late", late!! in 780.0..920.0)
    }
}
