package app.flare.auto

import android.content.SharedPreferences

/** The value text shown on the right of settings rows; pure functions of the stored preferences. */
internal object SettingsValues {
    fun colorMode(p: SharedPreferences) = p.getString(Keys.THEME_MODE, "dark") ?: "dark"
    fun weatherLocation(p: SharedPreferences) = if (p.getBoolean(Keys.SOLAR_MANUAL, false)) "Manual" else "Automatic location"
    fun weatherDisplay(p: SharedPreferences) = if (p.getBoolean(Keys.WEATHER_ENABLED, false)) "Forecast" else "Sunrise / sunset"
    fun forecastLimit(p: SharedPreferences) = "${p.getInt(Keys.WEATHER_AGE_DAYS, 2)} days"
    fun saverSlot(p: SharedPreferences, left: Boolean) = p.getString(if (left) Keys.SAVER_SLOT_LEFT else Keys.SAVER_SLOT_RIGHT, "off") ?: "off"
    fun bannerPosition(p: SharedPreferences) = p.getString(Keys.SAVER_BANNER_POSITION, "left") ?: "left"
    fun saverMode(p: SharedPreferences) = if (p.getString(Keys.SAVER_MODE, "clock") == "image") "Wallpaper" else "Clock"
    fun timeout(p: SharedPreferences): String {
        val minutes = p.getInt(Keys.SAVER_TIMEOUT, 0)
        return if (minutes == 0) "Off" else "$minutes min"
    }
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
    fun hiddenApps(p: SharedPreferences) = "${p.getStringSet(Keys.HIDDEN_APPS, emptySet())?.size ?: 0} hidden"
    fun accent(p: SharedPreferences) = if (p.contains(Keys.ACCENT_COLOR)) hexColor(p.getInt(Keys.ACCENT_COLOR, 0)) else "Theme default"
}
