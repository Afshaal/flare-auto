package app.flare.auto

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LayoutAndWallpaperTest {
    @Test
    fun splitIsClampedEverywhere() {
        assertEquals(58, LayoutPrefs.split(TestPrefs()))
        assertEquals(50, LayoutPrefs.split(TestPrefs(Keys.SPLIT to 50)))
        assertEquals(35, LayoutPrefs.split(TestPrefs(Keys.SPLIT to 10)))
        assertEquals(65, LayoutPrefs.split(TestPrefs(Keys.SPLIT to 99)))
        assertEquals(8, LayoutPrefs.MAX_APP_SHORTCUTS)
    }

    @Test
    fun legacyTwoCardLayoutMigrates() {
        assertEquals("clock", LayoutPrefs.legacyCard(TestPrefs(), true))
        assertEquals("media", LayoutPrefs.legacyCard(TestPrefs(), false))
        assertEquals("hidden", LayoutPrefs.legacyCard(TestPrefs(Keys.PROJECTION to false), true))
        assertEquals("hidden", LayoutPrefs.legacyCard(TestPrefs(Keys.MEDIA to false), false))
        // An explicit choice wins over the old booleans.
        assertEquals("apps", LayoutPrefs.legacyCard(TestPrefs(Keys.LAYOUT_LEFT to "apps", Keys.PROJECTION to false), true))
    }

    @Test
    fun presetTableKeepsStoredIndexes() {
        assertEquals(listOf("Accent wave", "Black", "Ember", "Wine", "Midnight", "Forest", "Charcoal", "Soft Charcoal",
            "Dark Grey", "Medium Grey", "Warm Grey", "Black to Warm Grey", "Dark Grey to Warm Grey", "Black to Warm Red",
            "Black to Burgundy", "Black to Copper", "Black to Home Ember", "Custom image"), WallpaperPack.HOME_NAMES.toList())
        assertEquals(17, WallpaperPack.CUSTOM_INDEX)
        assertTrue(WallpaperPack.PRESETS[0] is WallpaperPack.Preset.Accent)
        assertEquals(0xff383838.toInt(), (WallpaperPack.PRESETS[9] as WallpaperPack.Preset.Solid).color)
        assertArrayEquals(intArrayOf(0xff242424.toInt(), 0xff393530.toInt(), 0xff393530.toInt()),
            (WallpaperPack.PRESETS[12] as WallpaperPack.Preset.Stops).colors)
    }

    /** Values computed from the pre-refactor formula (32-bit float arithmetic). */
    @Test
    fun glowColoursMatchTheOriginalFormula() {
        val expected = mapOf(
            "Ember" to intArrayOf(0xff5b2e2a.toInt(), 0xff160b0a.toInt(), 0xff050505.toInt()),
            "Wine" to intArrayOf(0xff3f0f20.toInt(), 0xff0f0308.toInt(), 0xff050505.toInt()),
            "Midnight" to intArrayOf(0xff39465b.toInt(), 0xff0e1116.toInt(), 0xff050505.toInt()),
            "Forest" to intArrayOf(0xff365239.toInt(), 0xff0d140e.toInt(), 0xff050505.toInt()),
            "Charcoal" to intArrayOf(0xff3b4555.toInt(), 0xff0e1115.toInt(), 0xff050505.toInt()),
            "Soft Charcoal" to intArrayOf(0xff0c0e11.toInt(), 0xff030304.toInt(), 0xff000000.toInt()))
        for (preset in WallpaperPack.PRESETS.filterIsInstance<WallpaperPack.Preset.Glow>()) {
            assertArrayEquals(preset.name, expected.getValue(preset.name), WallpaperPack.glowColors(preset.base, preset.deep))
        }
    }

    @Test
    fun legacyWallpaperIndexesMigrate() {
        val home = (0..8).map { WallpaperPack.legacyIndex(TestPrefs(Keys.LEGACY_HOME_WALLPAPER to it), false) }
        assertEquals(listOf(0, 2, 3, 4, 5, 6, 17, 0, 0), home)
        val saver = (0..22).map { WallpaperPack.legacyIndex(TestPrefs(Keys.LEGACY_SAVER_WALLPAPER to it), true) }
        assertEquals(listOf(1, 2, 3, 4, 5, 6, 2, 3, 4, 5, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 1, 1), saver)
    }

    @Test
    fun screensaverFlipFollowsHomeUntilSet() {
        assertEquals(false, WallpaperPack.flip(TestPrefs(), "saver"))
        assertEquals(true, WallpaperPack.flip(TestPrefs(Keys.FLIP_GRADIENTS to true), "saver"))
        assertEquals(false, WallpaperPack.flip(TestPrefs(Keys.FLIP_GRADIENTS to true, Keys.SAVER_FLIP to false), "saver"))
        assertEquals(false, WallpaperPack.flip(TestPrefs(Keys.FLIP_GRADIENTS to true), "drawer"))
        assertEquals(true, WallpaperPack.flip(TestPrefs(Keys.FLIP_GRADIENTS to true), "home"))
    }
}
