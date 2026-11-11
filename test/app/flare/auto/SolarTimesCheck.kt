package app.flare.auto

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone
import kotlin.math.abs

/**
 * Sunrise / sunset against NOAA general solar-position values (independent algorithm) for fixed places and
 * dates: both hemispheres, both solstices, an equinox at the equator, a DST change and polar day / night.
 */
class SolarTimesCheck {
    private data class Case(val name: String, val lat: Double, val lon: Double, val zone: String,
                            val year: Int, val month: Int, val day: Int, val rise: Int, val set: Int)

    private val cases = listOf(
        Case("London, summer solstice (BST)", 51.5074, -0.1278, "Europe/London", 2026, Calendar.JUNE, 21, 283, 1281),
        Case("London, winter solstice", 51.5074, -0.1278, "Europe/London", 2026, Calendar.DECEMBER, 21, 483, 953),
        Case("Sydney, southern winter", -33.8688, 151.2093, "Australia/Sydney", 2026, Calendar.JUNE, 21, 420, 1013),
        Case("Sydney, southern summer (AEDT)", -33.8688, 151.2093, "Australia/Sydney", 2026, Calendar.DECEMBER, 21, 341, 1205),
        Case("Quito, equinox on the equator", -0.1807, -78.4678, "America/Guayaquil", 2026, Calendar.MARCH, 20, 379, 1105),
        Case("New York, day after DST ends", 40.7128, -74.0060, "America/New_York", 2026, Calendar.NOVEMBER, 8, 394, 1006))

    private fun day(zone: TimeZone, year: Int, month: Int, day: Int) = Calendar.getInstance(zone).apply {
        clear()
        set(year, month, day, 12, 0)
    }

    @Test
    fun matchesReferenceWithinSixMinutes() {
        for (c in cases) {
            val zone = TimeZone.getTimeZone(c.zone)
            val date = day(zone, c.year, c.month, c.day)
            val rise = SolarTimes.event(date, c.lat, c.lon, zone, true)
            val set = SolarTimes.event(date, c.lat, c.lon, zone, false)
            assertTrue("${c.name}: sunrise $rise, expected ${c.rise}", abs(rise - c.rise) <= 6)
            assertTrue("${c.name}: sunset $set, expected ${c.set}", abs(set - c.set) <= 6)
        }
    }

    @Test
    fun polarDayAndNightHaveNoEvents() {
        val zone = TimeZone.getTimeZone("Europe/Oslo")
        for ((month, label) in listOf(Calendar.JUNE to "midnight sun", Calendar.DECEMBER to "polar night")) {
            val date = day(zone, 2026, month, 21)
            assertEquals("Tromsø $label sunrise", -1, SolarTimes.event(date, 69.6492, 18.9553, zone, true))
            assertEquals("Tromsø $label sunset", -1, SolarTimes.event(date, 69.6492, 18.9553, zone, false))
        }
    }

    @Test
    fun clockTextMatchesSettings() {
        assertEquals("--:--", formatMinutes(-1, true))
        assertEquals("04:43", formatMinutes(283, true))
        assertEquals("21:21", formatMinutes(1281, true))
        assertEquals("04:43 AM", formatMinutes(283, false))
        assertEquals("12:05 PM", formatMinutes(725, false))
        assertEquals("12:00 AM", formatMinutes(0, false))
        assertEquals("09:21 PM", formatMinutes(1281, false))
    }
}
