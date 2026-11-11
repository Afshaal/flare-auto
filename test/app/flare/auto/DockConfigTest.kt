package app.flare.auto

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DockConfigTest {
    private fun actions(prefs: TestPrefs) = DockConfig.read(prefs).map { it.action }

    @Test
    fun storedIdsAreStable() {
        assertEquals(listOf(0, 1, 2, 3, 4, 5), DockAction.values().map { it.id })
        assertEquals(DockAction.APP, DockAction.of(5))
        assertEquals(null, DockAction.of(9))
        assertEquals(listOf(DockAction.AUDIO, DockAction.RADIO, DockAction.BLUETOOTH, DockAction.SCREEN_OFF), DockAction.ASSIGNABLE)
    }

    @Test
    fun freshInstallUsesDefaultOrder() {
        assertEquals(listOf(DockAction.AUDIO, DockAction.RADIO, DockAction.BLUETOOTH, DockAction.SCREEN_OFF, DockAction.APPS), actions(TestPrefs()))
    }

    @Test
    fun legacyDockOrderIsMigrated() {
        assertEquals(listOf(DockAction.APPS, DockAction.SCREEN_OFF, DockAction.AUDIO, DockAction.RADIO, DockAction.BLUETOOTH),
            actions(TestPrefs(Keys.DOCK_ORDER to "4,3,0,1,2")))
        // Not a permutation of 0..4: fall back to the default order.
        for (bad in listOf("0,0,1,2,3", "0,1,2", "a,b,c,d,e", "0,1,2,3,9"))
            assertEquals(bad, DockAction.AUDIO, actions(TestPrefs(Keys.DOCK_ORDER to bad)).first())
    }

    @Test
    fun savedItemsRoundTrip() {
        val prefs = TestPrefs()
        val items = arrayListOf(
            DockConfig.Item(DockAction.APP, "com.example/.Main", true),
            DockConfig.Item(DockAction.RADIO, "", false),
            DockConfig.Item(DockAction.APPS, "", false))
        DockConfig.save(prefs, items)
        val read = DockConfig.read(prefs)
        assertEquals(listOf(DockAction.APP, DockAction.RADIO, DockAction.APPS), read.map { it.action })
        assertEquals("com.example/.Main", read[0].target)
        assertEquals(false, read[1].visible)
        assertTrue("the drawer slot is always visible", read[2].visible)
    }

    @Test
    fun malformedEntriesAreSkippedAndDrawerSlotIsUnique() {
        val raw = """[{"type":4},{"type":9},{"type":1,"visible":false},{"type":4}]"""
        assertEquals(listOf(DockAction.APPS, DockAction.RADIO), actions(TestPrefs(Keys.DOCK_ITEMS to raw)))
    }

    @Test
    fun savedDockWithoutDrawerSlotGetsLegacyOrderAppended() {
        // Existing behaviour kept as is: the saved items stay first, then the five built-in slots follow.
        val read = actions(TestPrefs(Keys.DOCK_ITEMS to """[{"type":2}]"""))
        assertEquals(6, read.size)
        assertEquals(DockAction.BLUETOOTH, read[0])
        assertEquals(DockAction.APPS, read.last())
    }

    @Test
    fun iconSizeCeiling() {
        assertEquals(20, DockConfig.iconCeiling(20))
        assertEquals(20, DockConfig.iconCeiling(22))
        assertEquals(26, DockConfig.iconCeiling(23))
        assertEquals(26, DockConfig.iconCeiling(24)) // default stored value
        assertEquals(26, DockConfig.iconCeiling(29))
        assertEquals(32, DockConfig.iconCeiling(30))
        assertEquals(32, DockConfig.iconCeiling(40))
    }
}
