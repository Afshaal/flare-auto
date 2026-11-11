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

    /** 1.2.0 release against the installed 1.1.0, as UpdateChecker calls it: compare(tag, installedVersionName). */
    @Test fun release120AgainstInstalled110() {
        assertTrue(UpdateVersion.compare("v1.2.0", "1.1.0")!! > 0)   // offered as an update
        assertTrue(UpdateVersion.compare("1.2.0", "1.1.0")!! > 0)    // tag without "v"
        assertEquals(0, UpdateVersion.compare("v1.2.0", "1.2.0"))    // after updating: "No newer release"
        assertEquals(0, UpdateVersion.compare("v1.2", "1.2.0"))
        assertTrue(UpdateVersion.compare("v1.1.0", "1.2.0")!! < 0)   // old tag never offered as a downgrade
        assertTrue(UpdateVersion.compare("v1.2.0-beta.1", "1.1.0")!! > 0)
        assertTrue(UpdateVersion.compare("v1.2.0-beta.1", "1.2.0")!! < 0)
        assertTrue(UpdateVersion.compare("v1.10.0", "1.2.0")!! > 0)  // numeric, not text, ordering
        assertNull(UpdateVersion.compare("Flare Auto 1.2.0", "1.1.0")) // unparseable tag: "Open release" only
    }
}
