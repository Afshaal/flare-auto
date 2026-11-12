package app.flare.auto

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.TimeZone

/** Navigation cards: distance / time / arrival formatting and the remaining part of the route line. */
class TripFormatTest {
    @Test
    fun distances() {
        assertEquals("40 m", TripFormat.distance(43.0))
        assertEquals("0 m", TripFormat.distance(2.0))
        assertEquals("350 m", TripFormat.distance(361.0))
        assertEquals("1.0 km", TripFormat.distance(990.0))
        assertEquals("1.2 km", TripFormat.distance(1240.0))
        assertEquals("12 km", TripFormat.distance(12400.0))
    }

    @Test
    fun durationsAndArrival() {
        assertEquals("1 min", TripFormat.duration(10_000))
        assertEquals("45 min", TripFormat.duration(45 * 60_000L))
        assertEquals("1 h 05 min", TripFormat.duration(65 * 60_000L))
        val utc = TimeZone.getTimeZone("UTC")
        val noon = 12 * 3600_000L
        assertEquals("12:25", TripFormat.arrival(noon, 25 * 60_000L, true, utc))
        assertEquals("12:25 PM", TripFormat.arrival(noon, 25 * 60_000L, false, utc))
        // Full-map trip card: no AM/PM, 12/24-hour setting respected; value and unit separate.
        assertEquals("11:58", TripFormat.clock(0, (23 * 60 + 58) * 60_000L, false, utc))
        assertEquals("23:58", TripFormat.clock(0, (23 * 60 + 58) * 60_000L, true, utc))
        assertEquals("16" to "min", TripFormat.durationParts(16 * 60_000L))
        assertEquals("1:05" to "hr", TripFormat.durationParts(65 * 60_000L))
        assertEquals("850" to "m", TripFormat.distanceParts(843.0))
        assertEquals("1.0" to "km", TripFormat.distanceParts(960.0))
        assertEquals("4.3" to "km", TripFormat.distanceParts(4280.0))
        assertEquals("15" to "km", TripFormat.distanceParts(15200.0))
    }

    @Test
    fun roadNameFallsBackToInstruction() {
        assertEquals("Main Boulevard", TripFormat.road(NavStep("Turn left onto Main Boulevard", -2, 10.0, "Main Boulevard")))
        assertEquals("Continue", TripFormat.road(NavStep("continue", 0, 10.0)))
    }

    @Test
    fun remainingRouteStartsAtTheVehicle() {
        val points = doubleArrayOf(31.0, 74.0, 31.0, 74.01, 31.0, 74.02)
        val route = NavRoute(points, NavGeometry.cumulative(points), emptyList(), 0.0, 0)
        assertArrayEquals(points, NavGeometry.remaining(route, 0, 0.0), 1e-9)
        val half = route.cumulative[1] / 2
        val ahead = NavGeometry.remaining(route, 0, half)
        assertEquals(6, ahead.size)
        assertEquals(74.005, ahead[1], 1e-6)
        assertArrayEquals(doubleArrayOf(31.0, 74.02), ahead.copyOfRange(4, 6), 1e-9)
        val end = NavGeometry.remaining(route, 1, route.cumulative[2])
        assertArrayEquals(doubleArrayOf(31.0, 74.02, 31.0, 74.02), end, 1e-9)
        // Snapping from GPS gives the same segment/along pair the guidance uses.
        val at = NavGeometry.nearest(route, 31.0001, 74.015, 0)
        assertEquals(1, at.segment)
        assertEquals(4, NavGeometry.remaining(route, at.segment, at.along).size)
    }
}
