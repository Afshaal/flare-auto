package app.flare.auto

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.widget.TextView
import java.util.Calendar
import java.util.TimeZone

internal class EnvironmentInfo(
    private val context: Context,
    private val sun: TextView,
    private val icon: SolarIcon,
    private val displayMode: String = "auto",
    /** Weather widgets: the condition name, or why no forecast is shown (weather off, no location, none recent). */
    private val condition: TextView? = null,
    /** "sun" mode: name the event ("Sunset 5:42 PM") instead of the time alone. */
    private val eventName: Boolean = false,
    /** Told whether a forecast is shown (true) or the sunrise / sunset fallback (false). */
    private val forecastShown: ((Boolean) -> Unit)? = null
) {
    private val preferences = ProfileStore.preferences(context)
    private val downloadedFormat by lazy { java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.SHORT, java.text.DateFormat.SHORT) }
    private var lastMinute = -1
    private var lastDay = -1
    private var sunrise = -1
    private var sunset = -1
    private var nextRise = -1
    private var lat = Double.NaN
    private var lon = Double.NaN
    private var zone = TimeZone.getDefault()
    private val forecast = Forecast(context) { lastMinute = -1; updateSolar() }

    fun start() {
        lastDay = -1
        forecast.start()
        updateSolar()
    }

    fun stop() { forecast.stop() }

    private fun locate(): Boolean {
        if (preferences.getBoolean(Keys.SOLAR_MANUAL, false)) {
            val latitude = preferences.getString(Keys.SOLAR_LAT, "")?.toDoubleOrNull()
            val longitude = preferences.getString(Keys.SOLAR_LON, "")?.toDoubleOrNull()
            if (latitude != null && longitude != null && latitude.isFinite() && longitude.isFinite() &&
                latitude in -90.0..90.0 && longitude in -180.0..180.0
            ) {
                lat = latitude
                lon = longitude
                return true
            }
        } else if (context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            if (locationManager != null) {
                for (provider in arrayOf(
                    LocationManager.GPS_PROVIDER,
                    LocationManager.PASSIVE_PROVIDER,
                    LocationManager.NETWORK_PROVIDER
                )) {
                    try {
                        val location: Location? = locationManager.getLastKnownLocation(provider)
                        if (location != null) {
                            lat = location.latitude
                            lon = location.longitude
                            return true
                        }
                    } catch (_: IllegalArgumentException) {
                    } catch (_: SecurityException) {
                    }
                }
            }
        }
        lat = Double.NaN
        lon = Double.NaN
        return false
    }

    fun updateSolar() {
        val currentZone = TimeZone.getDefault()
        if (currentZone != zone) {
            zone = currentZone
            lastDay = -1
        }
        val date = Calendar.getInstance(zone)
        val key = date.get(Calendar.YEAR) * 1000 + date.get(Calendar.DAY_OF_YEAR)
        val minute = date.get(Calendar.HOUR_OF_DAY) * 60 + date.get(Calendar.MINUTE)
        if (key != lastDay || lat.isNaN()) {
            lastDay = key
            lastMinute = -1
            if (locate()) {
                sunrise = SolarTimes.event(date, lat, lon, zone, true)
                sunset = SolarTimes.event(date, lat, lon, zone, false)
                val tomorrow = date.clone() as Calendar
                tomorrow.add(Calendar.DAY_OF_YEAR, 1)
                nextRise = SolarTimes.event(tomorrow, lat, lon, zone, true)
            } else {
                sunrise = -1
                sunset = -1
                nextRise = -1
            }
        }
        if (lastMinute == minute) return
        lastMinute = minute
        if (displayMode != "sun" && preferences.getBoolean(Keys.WEATHER_ENABLED, false) && lat.isFinite() && lon.isFinite()) {
            forecast.refresh(lat, lon)
            val weather = forecast.value(System.currentTimeMillis(), lat, lon)
            if (weather != null) {
                val fahrenheit = preferences.getBoolean(Keys.WEATHER_FAHRENHEIT, false)
                val value = if (fahrenheit) weather.temperature * 9 / 5 + 32 else weather.temperature
                val temperature = kotlin.math.round(value).toInt()
                sun.setTextColor(ThemeTokens.get(context).textPrimary)
                sun.textSize = 16f * preferences.getInt(Keys.TEXT, 100) / 100f
                sun.text = "$temperature\u00b0"
                icon.setDaytime(!night())
                sun.contentDescription = "Forecast ${sun.text}; downloaded " + downloadedFormat.apply { timeZone = zone }.format(java.util.Date(weather.fetchedAt))
                icon.setWeather(weather.code)
                condition?.apply { text = weatherName(weather.code); setTextColor(ThemeTokens.get(context).textPrimary) }
                forecastShown?.invoke(true)
                return
            }
        }
        forecastShown?.invoke(false)
        condition?.apply {
            text = when {
                !preferences.getBoolean(Keys.WEATHER_ENABLED, false) -> "Weather off \u00b7 turn on in Launcher settings"
                lat.isNaN() -> "No location for weather"
                else -> "No recent forecast \u00b7 updates when online"
            }
            setTextColor(ThemeTokens.get(context).textSecondary)
        }
        icon.setWeather(null)
        sun.setTextColor(ThemeTokens.get(context).textSecondary)
        sun.textSize = 11f * preferences.getInt(Keys.TEXT, 100) / 100f
        if (lat.isNaN()) {
            sun.text = "--:--"
            sun.contentDescription = "Sunrise and sunset unavailable; set a location"
            icon.setDaytime(false)
            return
        }
        val state = SolarState(minute, sunrise, sunset, nextRise)
        val event = if (state.day) "Sunset" else "Sunrise"
        sun.text = if (eventName && state.next >= 0) "$event ${format(state.next)}" else format(state.next)
        sun.contentDescription = if (state.next < 0) {
            "Sunrise or sunset unavailable"
        } else {
            "$event ${format(state.next)}"
        }
        icon.setDaytime(state.day)
    }

    fun format(minutes: Int): String = formatMinutes(minutes, preferences.clock24)

    fun night(): Boolean {
        if (sunrise < 0 || sunset < 0) return false
        val now = Calendar.getInstance(zone)
        val minute = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
        return minute >= sunset || minute < sunrise
    }
}

/** "HH:mm", or "hh:mm AM/PM" when [clock24] is off; "--:--" for a missing time (minutes < 0). */
internal fun formatMinutes(minutes: Int, clock24: Boolean): String {
    if (minutes < 0) return "--:--"
    val hour = minutes / 60
    val minute = minutes % 60
    fun two(value: Int) = if (value < 10) "0$value" else value.toString()
    return if (clock24) "${two(hour)}:${two(minute)}" else "${two((hour + 11) % 12 + 1)}:${two(minute)} ${if (hour < 12) "AM" else "PM"}"
}
