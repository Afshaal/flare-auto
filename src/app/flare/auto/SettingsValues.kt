package app.flare.auto

import android.content.SharedPreferences

/**
 * The value text shown on the right of settings rows; pure functions of the stored preferences. Displayed values
 * start with a capital letter ("Dark", "Off", "Top left"); the stored values ("dark", "off", "left") never change.
 */
internal object SettingsValues {
    fun colorMode(p: SharedPreferences) = when (p.getString(Keys.THEME_MODE, "dark")) {
        "light" -> "Light"
        "auto" -> "Automatic"
        else -> "Dark"
    }
    fun weatherLocation(p: SharedPreferences) = if (p.getBoolean(Keys.SOLAR_MANUAL, false)) "Manual" else "Automatic location"
    fun weatherDisplay(p: SharedPreferences) = if (p.getBoolean(Keys.WEATHER_ENABLED, false)) "Forecast" else "Sunrise / sunset"
    fun forecastLimit(p: SharedPreferences) = p.getInt(Keys.WEATHER_AGE_DAYS, 2).let { if (it == 1) "1 day" else "$it days" }
    fun saverSlot(p: SharedPreferences, left: Boolean) = when (p.getString(if (left) Keys.SAVER_SLOT_LEFT else Keys.SAVER_SLOT_RIGHT, "off")) {
        "weather" -> "Weather"
        "sun" -> "Sunrise / sunset"
        "both" -> "Both"
        else -> "Off"
    }
    fun bannerPosition(p: SharedPreferences) = when (p.getString(Keys.SAVER_BANNER_POSITION, "left")) {
        "right" -> "Top right"
        "alternate" -> "Alternate corners"
        else -> "Top left"
    }
    fun saverMode(p: SharedPreferences) = if (p.getString(Keys.SAVER_MODE, "clock") == "image") "Wallpaper" else "Clock"

    /**
     * Screensaver idle timeout in milliseconds, 0 = off. "saver_timeout_seconds" (15 or 30) takes precedence when
     * set; otherwise "saver_timeout" keeps its meaning (minutes: 0, 1, 5, 10, 15).
     */
    fun timeoutMillis(p: SharedPreferences): Long {
        val seconds = p.getInt(Keys.SAVER_TIMEOUT_SECONDS, 0)
        if (seconds > 0) return seconds * 1000L
        return p.getInt(Keys.SAVER_TIMEOUT, 0).coerceAtLeast(0) * 60_000L
    }
    fun timeout(p: SharedPreferences): String {
        val seconds = p.getInt(Keys.SAVER_TIMEOUT_SECONDS, 0)
        if (seconds > 0) return "$seconds sec"
        val minutes = p.getInt(Keys.SAVER_TIMEOUT, 0)
        return if (minutes == 0) "Off" else "$minutes min"
    }
    /** Idle timeout choices: label, minutes ("saver_timeout"), seconds ("saver_timeout_seconds", 0 = unset). */
    val TIMEOUTS = listOf(Triple("Off", 0, 0), Triple("15 seconds", 1, 15), Triple("30 seconds", 1, 30),
        Triple("1 minute", 1, 0), Triple("5 minutes", 5, 0), Triple("10 minutes", 10, 0), Triple("15 minutes", 15, 0))
    /** Index in [TIMEOUTS] of the stored timeout (the first entry for unknown values). */
    fun timeoutIndex(p: SharedPreferences): Int {
        val seconds = p.getInt(Keys.SAVER_TIMEOUT_SECONDS, 0)
        val minutes = p.getInt(Keys.SAVER_TIMEOUT, 0)
        return TIMEOUTS.indexOfFirst { if (seconds > 0) it.third == seconds else it.second == minutes && it.third == 0 }.coerceAtLeast(0)
    }
    /** Visible map width in metres: "offline_map_span_m" (500 / 1000) when set, else "offline_map_span" km (2–200). */
    fun mapSpanMetres(p: SharedPreferences): Int {
        val metres = p.getInt(Keys.OFFLINE_MAP_SPAN_METRES, 0)
        if (metres > 0) return metres.coerceIn(200, 2000)
        return p.getInt(Keys.OFFLINE_MAP_SPAN, 10).coerceIn(2, 200) * 1000
    }
    fun mapSpan(p: SharedPreferences) = mapSpanMetres(p).let { if (it < 1000) "$it m" else "${it / 1000} km" }
    /** Map view span choices: label, km ("offline_map_span"), metres ("offline_map_span_m", 0 = unset). */
    val SPANS = listOf(Triple("500 m across", 2, 500), Triple("1 km across", 2, 1000)) +
        listOf(2, 5, 10, 20, 50, 100, 200).map { Triple("$it km across", it, 0) }

    fun clockSize(p: SharedPreferences) = when (p.getInt(Keys.SAVER_SIZE, 120)) {
        96 -> "Small"
        140 -> "Large"
        else -> "Medium"
    }
    fun saverClockIndex(p: SharedPreferences) = ClockStyles.ids.indexOf(p.getString(Keys.SAVER_CLOCK_STYLE, "classic")).coerceAtLeast(0)
    fun startupDelay(p: SharedPreferences) = "${p.getInt(Keys.STARTUP_DELAY, 10)} sec"
    fun startupSource(p: SharedPreferences) = when (p.getString(Keys.STARTUP_SOURCE, "none")) {
        "radio" -> "Radio"
        "bluetooth" -> "Bluetooth"
        else -> "System default"
    }
    fun scale(p: SharedPreferences, key: String) = "${p.getInt(key, 100)}%"
    fun hiddenApps(p: SharedPreferences) = p.getStringSet(Keys.HIDDEN_APPS, emptySet())?.size.let { n ->
        if (n == null || n == 0) "None hidden" else "$n hidden"
    }
    fun accent(p: SharedPreferences) = if (p.contains(Keys.ACCENT_COLOR)) hexColor(p.getInt(Keys.ACCENT_COLOR, 0)) else "Theme default"
}
