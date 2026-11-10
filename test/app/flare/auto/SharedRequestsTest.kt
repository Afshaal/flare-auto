package app.flare.auto

import org.junit.Assert.*
import org.junit.Test

class SharedRequestsTest {
    @Test fun coalescesAndNotifiesEverySubscriberOnce() {
        val requests = SharedRequests<String, Any>()
        val first = Any(); val second = Any()
        assertTrue(requests.join("same location", first, 1))
        assertFalse(requests.join("same location", second, 2))
        assertEquals(setOf(first to 1, second to 2), requests.complete("same location").toSet())
        assertFalse(requests.isPending("same location"))
        assertTrue(requests.complete("same location").isEmpty())
    }
    @Test fun leavingAndRejoiningUsesCurrentGeneration() {
        val requests = SharedRequests<String, Any>(); val listener = Any()
        requests.join("place", listener, 1); requests.leave(listener)
        assertTrue(requests.isPending("place"))
        assertFalse(requests.join("place", listener, 3))
        assertEquals(listOf(listener to 3), requests.complete("place"))
    }
}
