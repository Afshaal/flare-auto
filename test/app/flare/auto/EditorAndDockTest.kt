package app.flare.auto

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Per-widget clock style in saved layouts and the new dock actions, both compatible with existing data. */
class EditorAndDockTest {
    @Test
    fun clockStyleIsStoredPerWidgetAndOptional() {
        val pages = listOf(listOf(GridTile("clock", 0, 0, 4, 6, "modern"), GridTile("media", 4, 0, 4, 6)))
        val back = DashboardPages.decode(DashboardPages.encode(pages))!!
        assertEquals("modern", back[0][0].style)
        assertNull(back[0][1].style)
        // A layout saved before per-widget styles (no "style") still reads; its clocks follow the Home clock style.
        val old = DashboardPages.decode("""[[{"type":"clock","x":0,"y":0,"w":4,"h":6}]]""")!!
        assertNull(old[0][0].style)
        // Unknown style ids are ignored rather than breaking the layout.
        assertNull(DashboardPages.decode("""[[{"type":"clock","x":0,"y":0,"w":4,"h":6,"style":"fancy"}]]""")!![0][0].style)
        // Without a style the stored JSON is unchanged from before.
        assertTrue(!DashboardPages.encode(listOf(listOf(GridTile("media", 0, 0, 8, 6)))).contains("style"))
    }

    @Test
    fun newDockActionsRoundTripAndOlderItemsAreKept() {
        val p = TestPrefs()
        val items = DockConfig.read(p) // legacy five
        assertEquals(5, items.size)
        items.add(DockConfig.Item(DockAction.NAVIGATION, "", true))
        items[0] = DockConfig.Item(DockAction.CARPLAY, "com.example.zlink/.Main", true)
        DockConfig.save(p, items)
        val back = DockConfig.read(p)
        assertEquals(6, back.size)
        assertEquals(DockAction.CARPLAY, back[0].action)
        assertEquals("com.example.zlink/.Main", back[0].target)
        assertEquals(DockAction.NAVIGATION, back[5].action)
        assertTrue(DockAction.CARPLAY.linkable && DockAction.ANDROID_AUTO.linkable && !DockAction.NAVIGATION.linkable)
        assertEquals(6, DockConfig.MAX_ITEMS)
        // Stored ids never change (older builds skip ids they do not know).
        assertEquals(listOf(6, 7, 8), listOf(DockAction.NAVIGATION.id, DockAction.CARPLAY.id, DockAction.ANDROID_AUTO.id))
    }

    @Test
    fun dockPlacementDefaultsToBottom() {
        val p = TestPrefs()
        assertEquals("bottom", HomeChrome.dockSide(p)) // existing installs have no stored placement
        p.edit().putString(Keys.DOCK_POSITION, "left").apply()
        assertEquals("left", HomeChrome.dockSide(p))
        p.edit().putString(Keys.DOCK_POSITION, "right").apply()
        assertEquals("right", HomeChrome.dockSide(p))
        p.edit().putString(Keys.DOCK_POSITION, "top").apply()
        assertEquals("bottom", HomeChrome.dockSide(p))
    }

    @Test
    fun sideDockShowsFirstThreeAndKeepsTheRest() {
        val p = TestPrefs()
        val items = DockConfig.read(p) // legacy five: audio, radio, bluetooth, screen off, apps
        items.add(DockConfig.Item(DockAction.NAVIGATION, "", true))
        items.add(1, DockConfig.Item(DockAction.CARPLAY, "", false)) // hidden items are skipped
        DockConfig.save(p, items)
        val saved = DockConfig.read(p)
        val side = DockConfig.sideShortcuts(saved)
        assertEquals(DockConfig.SIDE_SHORTCUTS, side.size)
        assertTrue(side.none { saved[it].action == DockAction.APPS || !saved[it].visible })
        assertEquals(listOf(DockAction.AUDIO, DockAction.RADIO, DockAction.BLUETOOTH), side.map { saved[it].action })
        // Choosing a side placement changes nothing that is saved: the bottom dock still has every item.
        p.edit().putString(Keys.DOCK_POSITION, "left").apply()
        assertEquals(saved.size, DockConfig.read(p).size)
        p.edit().putString(Keys.DOCK_POSITION, "bottom").apply()
        assertEquals(saved.map { it.action }, DockConfig.read(p).map { it.action })
    }

    @Test
    fun weatherWidgetsAreCatalogued() {
        for (type in listOf("weather_compact", "weather_wide")) {
            val style = WidgetCatalog.styles.single { it.type == type }
            assertTrue(type in GridTile.TYPES)
            for ((w, h) in style.sizes) assertTrue("$type $w x $h", GridTile(type, 0, 0, w, h).valid())
        }
        // Older layouts keep decoding unchanged.
        assertEquals("weather", DashboardPages.decode("""[[{"type":"weather","x":0,"y":0,"w":3,"h":2}]]""")!![0][0].type)
        assertEquals("Partly cloudy", weatherName(2))
        assertEquals("Thunderstorm", weatherName(95))
        assertEquals("Weather", weatherName(-1))
    }
}
