package app.flare.auto

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsAndAppsTest {
    @Test
    fun settingsRowValues() {
        val empty = TestPrefs()
        assertEquals("Automatic location", SettingsValues.weatherLocation(empty))
        assertEquals("Manual", SettingsValues.weatherLocation(TestPrefs(Keys.SOLAR_MANUAL to true)))
        assertEquals("Sunrise / sunset", SettingsValues.weatherDisplay(empty))
        assertEquals("Forecast", SettingsValues.weatherDisplay(TestPrefs(Keys.WEATHER_ENABLED to true)))
        assertEquals("2 days", SettingsValues.forecastLimit(empty))
        assertEquals("off", SettingsValues.saverSlot(empty, true))
        assertEquals("both", SettingsValues.saverSlot(TestPrefs(Keys.SAVER_SLOT_RIGHT to "both"), false))
        assertEquals("left", SettingsValues.bannerPosition(empty))
        assertEquals("Clock", SettingsValues.saverMode(empty))
        assertEquals("Wallpaper", SettingsValues.saverMode(TestPrefs(Keys.SAVER_MODE to "image")))
        assertEquals("Off", SettingsValues.timeout(empty))
        assertEquals("5 min", SettingsValues.timeout(TestPrefs(Keys.SAVER_TIMEOUT to 5)))
        assertEquals("Medium", SettingsValues.clockSize(empty))
        assertEquals("Small", SettingsValues.clockSize(TestPrefs(Keys.SAVER_SIZE to 96)))
        assertEquals("Large", SettingsValues.clockSize(TestPrefs(Keys.SAVER_SIZE to 140)))
        assertEquals(0, SettingsValues.saverClockIndex(TestPrefs(Keys.SAVER_CLOCK_STYLE to "unknown")))
        assertEquals("10 sec", SettingsValues.startupDelay(empty))
        assertEquals("System default", SettingsValues.startupSource(empty))
        assertEquals("Radio", SettingsValues.startupSource(TestPrefs(Keys.STARTUP_SOURCE to "radio")))
        assertEquals("100%", SettingsValues.scale(empty, Keys.UI_SCALE_HOME))
        assertEquals("0 hidden", SettingsValues.hiddenApps(empty))
        assertEquals("2 hidden", SettingsValues.hiddenApps(TestPrefs(Keys.HIDDEN_APPS to setOf("a", "b"))))
        assertEquals("Theme default", SettingsValues.accent(empty))
        assertEquals("#E60012", SettingsValues.accent(TestPrefs(Keys.ACCENT_COLOR to 0xffe60012.toInt())))
    }

    @Test
    fun hexColours() {
        assertEquals("#00000A", hexColor(0xff00000a.toInt()))
        assertEquals(0xffe60012.toInt(), parseHexColor(" #e60012 "))
        assertNull(parseHexColor("#e6001"))
        assertNull(parseHexColor("e60012"))
        assertNull(parseHexColor("#e60012ff"))
    }

    private fun app(label: String, pkg: String) = LaunchableApp(label, pkg, "$pkg.Main")

    @Test
    fun catalogSortsByLabelAndDropsOwnPackage() {
        val apps = listOf(app("Zoom", "z.app"), app("alpha", "a.app"), app("Beta", "b.app"), app("Flare Auto", "app.flare.auto"))
        assertEquals(listOf("alpha", "Beta", "Flare Auto", "Zoom"), AppCatalog.sort(apps).map { it.label })
        assertEquals(3, AppCatalog.withoutPackage(apps, "app.flare.auto").size)
        assertEquals("z.app/z.app.Main", apps[0].target)
    }

    @Test
    fun drawerStylesAndHiddenPackages() {
        assertTrue(KnownApps.hiddenFromDrawer("local.civic.radio"))
        assertFalse(KnownApps.hiddenFromDrawer("com.spotify.music"))
        assertEquals("wifi", KnownApps.drawerStyle("Basic Settings", "x").glyph)
        assertEquals("WiFi Settings", KnownApps.drawerStyle("Basic Settings", "x").label)
        assertEquals("systemsettings", KnownApps.drawerStyle("Settings", "com.android.settings").glyph)
        assertEquals("terminal", KnownApps.drawerStyle("Termux", "com.termux").glyph)
        assertEquals("sim", KnownApps.drawerStyle("SIM Toolkit", "com.android.stk2").glyph)
        val files = KnownApps.drawerStyle("Files", "x.files")
        assertEquals("original" to "file", files.glyph to files.color)
        assertEquals("original", KnownApps.drawerStyle("Spotify", "com.spotify.music").glyph)
        assertTrue(KnownApps.BRAND_ICONS.values.all { it.startsWith("si_") })
        assertEquals(9, KnownApps.DRAWER_SHORTCUTS.size)
        assertEquals(1, KnownApps.DRAWER_SHORTCUTS.count { it.internal })
    }

    @Test
    fun overrideRolesMatchSettingsKeys() {
        assertEquals("audio", KnownApps.overrideRole(KnownApps.AUDIO_SCREEN))
        assertEquals("radio", KnownApps.overrideRole(KnownApps.RADIO_SCREEN))
        assertEquals("bluetooth", KnownApps.overrideRole(KnownApps.PHONE_SCREEN))
        assertEquals(Keys.APP_CARPLAY, KnownApps.projectionOverrideKey(KnownApps.CARPLAY))
        assertEquals(Keys.APP_ANDROIDAUTO, KnownApps.projectionOverrideKey(KnownApps.ANDROID_AUTO))
    }
}
