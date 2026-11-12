package app.flare.auto

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Preference keys are the on-device storage format: renaming one silently drops a saved setting. */
class KeysTest {
    private fun constants(): Map<String, String> = Keys::class.java.declaredFields
        .filter { java.lang.reflect.Modifier.isStatic(it.modifiers) && it.type == String::class.java }
        .associate { it.name to it.get(null) as String }

    @Test
    fun storedKeyNamesNeverChange() {
        val expected = setOf(
            "theme", "theme_mode", "theme_day_hour", "theme_night_hour", "light_theme_index", "dark_theme_index",
            "accent_color", "glass_cards", "ui_animations", "auto_dim", "text", "spacing", "ui_scale_home",
            "ui_scale_drawer", "ui_scale_settings", "wallpaper_home_v2", "wallpaper_saver_v2", "wallpaper_drawer_v2",
            "flip_gradients", "drawer_flip", "saver_flip", "custom_revision", "home_wallpaper", "saver_wallpaper",
            "drawer_custom", "drawer_wallpaper", "dashboard_pages", "dashboard_default", "layout_left", "layout_right",
            "layout_apps", "split", "projection", "media", "widget_left", "widget_right", "brand_text", "brand_color",
            "badge_text", "badge_color", "home_clock_style", "clock24", "awake", "dock", "dock_items", "dock_order",
            "dock_icons", "dock_labels", "dock_mono", "columns", "hidden_apps", "icon_pack", "icon_override_",
            "icon_color_", "icon_image_", "app_", "app_carplay", "app_androidauto", "startup_app", "startup_delay",
            "startup_saver", "startup_source", "startup_last_boot", "weather_enabled", "weather_age_days",
            "weather_fahrenheit", "solar_manual", "solar_lat", "solar_lon", "saver_timeout", "saver_mode", "saver_size",
            "saver_clock_style", "saver_date", "saver_civic", "saver_image_info", "saver_slot_left", "saver_slot_right",
            "saver_weather", "saver_solar", "saver_music_banner", "saver_banners", "saver_banner_private",
            "saver_banner_apps", "saver_banner_position", "saver_banner_seconds", "saver_banner_size",
            "offline_map_enabled", "offline_map_lat", "offline_map_lon", "offline_map_span", "offline_map_follow",
            "offline_map_url", "offline_map_vector", "offline_map_heading_up", "offline_nav_active", "offline_nav_lat", "offline_nav_lon", "offline_nav_name",
            "offline_nav_voice", "_editor_image",
            // 1.2.0 maplibre-r3: offline search places and the sub-minute screensaver timeout (new keys only).
            "offline_places_starred", "offline_places_recent", "offline_place_home", "offline_place_work",
            "saver_timeout_seconds")
        val values = constants().values
        assertEquals("a key constant was added, removed or renamed", expected, values.toSet())
        assertEquals("two constants share one key", values.size, values.toSet().size)
    }

    @Test
    fun homeRebuildKeysAreRealKeys() {
        val all = constants().values.toSet()
        assertEquals(48, Keys.HOME_UI.size)
        assertTrue(Keys.HOME_UI.minus(all).toString(), all.containsAll(Keys.HOME_UI))
        // Keys that change what Home draws must trigger a rebuild.
        for (key in listOf(Keys.DASHBOARD_PAGES, Keys.WALLPAPER_HOME, Keys.DOCK_ITEMS, Keys.TEXT, Keys.BRAND_TEXT, Keys.THEME_MODE))
            assertTrue(key, key in Keys.HOME_UI)
    }

    @Test
    fun sharedTextScale() {
        assertEquals(1f, TestPrefs().textScale)
        assertEquals(1.2f, TestPrefs(Keys.TEXT to 120).textScale, 1e-6f)
    }
}
