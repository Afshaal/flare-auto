package app.flare.auto

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SolarStateCheck {
    @Test
    fun solarStateBoundaryChecks() {
        checkState(383, 384, 1028, 385, false, 384)
        checkState(384, 384, 1028, 385, true, 1028)
        checkState(706, 384, 1028, 385, true, 1028)
        checkState(1027, 384, 1028, 385, true, 1028)
        checkState(1028, 384, 1028, 385, false, 385)
        checkState(0, 384, 1028, 385, false, 384)
    }

    private fun checkState(minute: Int, rise: Int, set: Int, tomorrow: Int, day: Boolean, next: Int) {
        val state = SolarState(minute, rise, set, tomorrow)
        assertEquals("minute=$minute", day, state.day)
        assertEquals("minute=$minute", next, state.next)
        assertTrue("minute=$minute", state.progress in 0f..1f)
    }
}
