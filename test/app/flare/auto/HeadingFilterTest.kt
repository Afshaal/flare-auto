package app.flare.auto

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HeadingFilterTest {
    private val lat = 31.52
    private val lon = 74.35
    private fun north(m: Double) = lat + m / 111_320.0

    @Test
    fun noHeadingUntilTheCarMoves() {
        val f = HeadingFilter()
        assertNull(f.update(lat, lon, null, 0f, 5f))
        assertNull(f.update(north(3.0), lon, 120f, 0.4f, 5f)) // GPS bearing while parked is ignored
    }

    @Test
    fun trustsGpsBearingWhenMoving() {
        val f = HeadingFilter()
        assertEquals(90.0, f.update(lat, lon, 90f, 10f, 5f)!!, 0.01)
    }

    @Test
    fun derivesHeadingFromTrackWithoutBearing() {
        val f = HeadingFilter()
        f.update(lat, lon, null, null, 5f)
        assertNull(f.update(north(5.0), lon, null, null, 5f)) // under the minimum track length
        assertEquals(0.0, f.update(north(20.0), lon, null, null, 5f)!!, 1.0)
    }

    @Test
    fun holdsHeadingWhenStopped() {
        val f = HeadingFilter()
        f.update(lat, lon, 45f, 12f, 5f)
        assertEquals(45.0, f.update(north(1.0), lon, 300f, 0f, 5f)!!, 0.01)
    }

    @Test
    fun turnsTheShortWayAndSmooths() {
        val f = HeadingFilter()
        f.update(lat, lon, 350f, 12f, 5f)
        val next = f.update(lat, lon, 10f, 12f, 5f)!!
        // 20 degree right turn across north, eased: between 350 and 10 going through 0
        assertEquals(HeadingFilter.normalize(350 + 20 * HeadingFilter.SMOOTHING), next, 0.01)
    }

    @Test
    fun ignoresTinyWobble() {
        val f = HeadingFilter()
        f.update(lat, lon, 100f, 12f, 5f)
        assertEquals(100.0, f.update(lat, lon, 102f, 12f, 5f)!!, 0.01)
    }
}
