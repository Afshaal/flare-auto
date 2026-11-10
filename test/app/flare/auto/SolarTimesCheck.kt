package app.flare.auto

import java.util.Calendar
import java.util.TimeZone
import org.junit.Assert.assertTrue
import org.junit.Test

class SolarTimesCheck {
    @Test
    fun lahoreSolarTimesAreReasonable() {
        val zone = TimeZone.getTimeZone("Asia/Karachi")
        val calendar = Calendar.getInstance(zone)
        calendar.clear()
        calendar.set(2026, Calendar.NOVEMBER, 8, 12, 0)
        val rise = SolarTimes.event(calendar, 31.5497, 74.3436, zone, true)
        val set = SolarTimes.event(calendar, 31.5497, 74.3436, zone, false)
        assertTrue("Unexpected Lahore sunrise: $rise", rise in 370..410)
        assertTrue("Unexpected Lahore sunset: $set", set in 1000..1040)
    }
}
