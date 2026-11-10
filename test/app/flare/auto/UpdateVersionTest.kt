package app.flare.auto

import org.junit.Assert.*
import org.junit.Test

class UpdateVersionTest {
    @Test fun compareStableAndPrerelease() {
        assertTrue(UpdateVersion.compare("v1.0", "1.1.0-beta.1")!! < 0)
        assertTrue(UpdateVersion.compare("v1.1.0", "1.1.0-beta.1")!! > 0)
        assertEquals(0, UpdateVersion.compare("v1.0", "1.0.0"))
        assertTrue(UpdateVersion.compare("v1.1.0-beta.10", "1.1.0-beta.2")!! > 0)
        assertNull(UpdateVersion.compare("latest", "1.0"))
    }
}
