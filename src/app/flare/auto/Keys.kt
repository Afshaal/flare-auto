package app.flare.auto

import android.content.SharedPreferences

/**
 * Per-profile preference keys (store "home" for the Family profile, "driver_<id>" for others; see ProfileStore).
 * The string values are the on-device storage format: never change one, or saved settings are lost.
 * KeysTest pins the full list.
 */
internal object Keys {
    // Appearance and theme
    const val THEME = "theme"
    const val THEME_MODE = "theme_mode"
    const val THEME_DAY_HOUR = "theme_day_hour"
    const val THEME_NIGHT_HOUR = "theme_night_hour"
    const val LIGHT_THEME_INDEX = "light_theme_index"
    const val DARK_THEME_INDEX = "dark_theme_index"
    const val ACCENT_COLOR = "accent_color"
    const val GLASS_CARDS = "glass_cards"
    const val UI_ANIMATIONS = "ui_animations"
    const val AUTO_DIM = "auto_dim"
    const val TEXT = "text"
    const val SPACING = "spacing"
    const val UI_SCALE_HOME = "ui_scale_home"
    const val UI_SCALE_DRAWER = "ui_scale_drawer"
    const val UI_SCALE_SETTINGS = "ui_scale_settings"

    // Wallpapers
    const val WALLPAPER_HOME = "wallpaper_home_v2"
    const val WALLPAPER_SAVER = "wallpaper_saver_v2"
    const val WALLPAPER_DRAWER = "wallpaper_drawer_v2"
    const val FLIP_GRADIENTS = "flip_gradients"
    const val DRAWER_FLIP = "drawer_flip"
    const val SAVER_FLIP = "saver_flip"
    const val CUSTOM_REVISION = "custom_revision"
    const val LEGACY_HOME_WALLPAPER = "home_wallpaper"
    const val LEGACY_SAVER_WALLPAPER = "saver_wallpaper"
    const val LEGACY_DRAWER_CUSTOM = "drawer_custom"
    const val LEGACY_DRAWER_WALLPAPER = "drawer_wallpaper"

    // Home layout
    const val DASHBOARD_PAGES = "dashboard_pages"
    const val DASHBOARD_DEFAULT = "dashboard_default"
    const val LAYOUT_LEFT = "layout_left"
    const val LAYOUT_RIGHT = "layout_right"
    const val LAYOUT_APPS = "layout_apps"
    const val SPLIT = "split"
    const val PROJECTION = "projection"
    const val MEDIA = "media"
    const val WIDGET_LEFT = "widget_left"
    const val WIDGET_RIGHT = "widget_right"
    const val BRAND_TEXT = "brand_text"
    const val BRAND_COLOR = "brand_color"
    const val BADGE_TEXT = "badge_text"
    const val BADGE_COLOR = "badge_color"
    const val HOME_CLOCK_STYLE = "home_clock_style"
    const val CLOCK24 = "clock24"
    const val AWAKE = "awake"

    // Dock
    const val DOCK = "dock"
    const val DOCK_ITEMS = "dock_items"
    const val DOCK_ORDER = "dock_order"
    const val DOCK_ICONS = "dock_icons"
    const val DOCK_LABELS = "dock_labels"
    const val DOCK_MONO = "dock_mono"
    /** Dock placement: "bottom" (default and for any unknown value), "left" or "right". */
    const val DOCK_POSITION = "dock_position"

    // App drawer
    const val COLUMNS = "columns"
    const val HIDDEN_APPS = "hidden_apps"
    const val ICON_PACK = "icon_pack"
    const val ICON_OVERRIDE_PREFIX = "icon_override_"
    const val ICON_COLOR_PREFIX = "icon_color_"
    const val ICON_IMAGE_PREFIX = "icon_image_"

    // App shortcuts ("app_<role>") and startup
    const val APP_PREFIX = "app_"
    const val APP_CARPLAY = "app_carplay"
    const val APP_ANDROIDAUTO = "app_androidauto"
    const val STARTUP_APP = "startup_app"
    const val STARTUP_DELAY = "startup_delay"
    const val STARTUP_SAVER = "startup_saver"
    const val STARTUP_SOURCE = "startup_source"
    const val STARTUP_LAST_BOOT = "startup_last_boot"

    // Weather and solar
    const val WEATHER_ENABLED = "weather_enabled"
    const val WEATHER_AGE_DAYS = "weather_age_days"
    const val WEATHER_FAHRENHEIT = "weather_fahrenheit"
    const val SOLAR_MANUAL = "solar_manual"
    const val SOLAR_LAT = "solar_lat"
    const val SOLAR_LON = "solar_lon"

    // Screensaver
    /** Idle timeout in minutes (0 = off). Unchanged meaning; see [SAVER_TIMEOUT_SECONDS]. */
    const val SAVER_TIMEOUT = "saver_timeout"
    /** Sub-minute idle timeout (15 or 30 s). When set (> 0) it takes precedence over [SAVER_TIMEOUT]. */
    const val SAVER_TIMEOUT_SECONDS = "saver_timeout_seconds"
    const val SAVER_MODE = "saver_mode"
    const val SAVER_SIZE = "saver_size"
    const val SAVER_CLOCK_STYLE = "saver_clock_style"
    const val SAVER_DATE = "saver_date"
    const val SAVER_VEHICLE_LABEL = "saver_civic"
    const val SAVER_IMAGE_INFO = "saver_image_info"
    const val SAVER_SLOT_LEFT = "saver_slot_left"
    const val SAVER_SLOT_RIGHT = "saver_slot_right"
    const val LEGACY_SAVER_WEATHER = "saver_weather"
    const val LEGACY_SAVER_SOLAR = "saver_solar"
    const val SAVER_MUSIC_BANNER = "saver_music_banner"
    const val SAVER_BANNERS = "saver_banners"
    const val SAVER_BANNER_PRIVATE = "saver_banner_private"
    const val SAVER_BANNER_APPS = "saver_banner_apps"
    const val SAVER_BANNER_POSITION = "saver_banner_position"
    const val SAVER_BANNER_SECONDS = "saver_banner_seconds"
    const val SAVER_BANNER_SIZE = "saver_banner_size"

    // Offline maps and navigation
    const val OFFLINE_MAP_ENABLED = "offline_map_enabled"
    const val OFFLINE_MAP_LAT = "offline_map_lat"
    const val OFFLINE_MAP_LON = "offline_map_lon"
    /** Map view span in km (2–200). Unchanged meaning; see [OFFLINE_MAP_SPAN_METRES]. */
    const val OFFLINE_MAP_SPAN = "offline_map_span"
    /** Sub-kilometre map view span (500 or 1000 m). When set (> 0) it takes precedence over [OFFLINE_MAP_SPAN]. */
    const val OFFLINE_MAP_SPAN_METRES = "offline_map_span_m"
    const val OFFLINE_MAP_FOLLOW = "offline_map_follow"
    const val OFFLINE_MAP_URL = "offline_map_url"
    /** Use the MapLibre vector map when the navigation pack has one (default on). */
    const val OFFLINE_MAP_VECTOR = "offline_map_vector"
    /** Optional extruded buildings on the MapLibre map when the pack has them (default off). */
    const val OFFLINE_MAP_BUILDINGS = "offline_map_buildings"
    /** MapLibre map appearance: "follow" (launcher light/dark theme, default), "dark" or "light" ([MapAppearance]). */
    const val OFFLINE_MAP_STYLE = "offline_map_style"
    /** Vector map orientation while following GPS: true = heading-up 3D, false = north-up. */
    const val OFFLINE_MAP_HEADING_UP = "offline_map_heading_up"
    const val OFFLINE_NAV_ACTIVE = "offline_nav_active"
    const val OFFLINE_NAV_LAT = "offline_nav_lat"
    const val OFFLINE_NAV_LON = "offline_nav_lon"
    const val OFFLINE_NAV_NAME = "offline_nav_name"
    const val OFFLINE_NAV_VOICE = "offline_nav_voice"
    /** Point ("lat,lon") on the route option the driver chose, kept when rerouting; absent for the fastest route. */
    const val OFFLINE_NAV_VIA = "offline_nav_via"
    /** Planned length (metres) of the current journey, fixed when it starts and kept on reroutes ([TrafficEta]). */
    const val OFFLINE_NAV_TRIP_M = "offline_nav_trip_m"
    /** Peak hours in arrival estimates ([TrafficEta]): on/off (default on since 1.2.2), "HH:MM-HH:MM" windows and the
     *  weekday bitmask (bit 0 = Monday). The peak multiplier and school keys are no longer read (kept, not renamed). */
    const val OFFLINE_ETA_TRAFFIC = "offline_eta_traffic"
    const val OFFLINE_ETA_PEAK = "offline_eta_peak"
    const val OFFLINE_ETA_MORNING = "offline_eta_morning"
    const val OFFLINE_ETA_EVENING = "offline_eta_evening"
    const val OFFLINE_ETA_SCHOOL = "offline_eta_school"
    const val OFFLINE_ETA_SCHOOL_TIME = "offline_eta_school_time"
    const val OFFLINE_ETA_DAYS = "offline_eta_days"
    /** Offline search (JSON, per driver profile): starred places, recent destinations, the Home and Work slots. */
    const val OFFLINE_PLACES_STARRED = "offline_places_starred"
    const val OFFLINE_PLACES_RECENT = "offline_places_recent"
    const val OFFLINE_PLACE_HOME = "offline_place_home"
    const val OFFLINE_PLACE_WORK = "offline_place_work"

    /** Editor-only scratch key (lives in the editor's draft, never saved). */
    const val EDITOR_IMAGE = "_editor_image"

    /** Keys whose change requires Home to be rebuilt the next time it resumes. */
    val HOME_UI: Set<String> = setOf(
        DASHBOARD_PAGES, DASHBOARD_DEFAULT, OFFLINE_MAP_ENABLED, OFFLINE_MAP_LAT, OFFLINE_MAP_LON, OFFLINE_MAP_SPAN, OFFLINE_MAP_SPAN_METRES,
        OFFLINE_MAP_FOLLOW, OFFLINE_MAP_VECTOR, OFFLINE_MAP_BUILDINGS, OFFLINE_MAP_STYLE, THEME_MODE, THEME_DAY_HOUR, THEME_NIGHT_HOUR, LIGHT_THEME_INDEX, DARK_THEME_INDEX, THEME,
        ACCENT_COLOR, BRAND_TEXT, BADGE_TEXT, BRAND_COLOR, BADGE_COLOR, WIDGET_LEFT, WIDGET_RIGHT, HOME_CLOCK_STYLE,
        SOLAR_MANUAL, SOLAR_LAT, SOLAR_LON, CUSTOM_REVISION, LEGACY_HOME_WALLPAPER, WALLPAPER_HOME, GLASS_CARDS,
        WEATHER_ENABLED, WEATHER_AGE_DAYS, WEATHER_FAHRENHEIT, FLIP_GRADIENTS, SPLIT, SPACING, DOCK, DOCK_ORDER,
        DOCK_ITEMS, DOCK_MONO, DOCK_LABELS, DOCK_ICONS, DOCK_POSITION, TEXT, PROJECTION, MEDIA, CLOCK24, LAYOUT_LEFT, LAYOUT_RIGHT,
        LAYOUT_APPS)
}

/** Text size multiplier chosen in settings (85–120 %). */
internal val SharedPreferences.textScale: Float get() = getInt(Keys.TEXT, 100) / 100f
internal val SharedPreferences.clock24: Boolean get() = getBoolean(Keys.CLOCK24, false)
internal val SharedPreferences.glassCards: Boolean get() = getBoolean(Keys.GLASS_CARDS, true)
