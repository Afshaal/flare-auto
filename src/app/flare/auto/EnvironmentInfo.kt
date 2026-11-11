package app.flare.auto

import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.widget.TextView
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

internal class EnvironmentInfo(
    private val context: Context,
    private val sun: TextView,
    private val icon: SolarIcon,
    private val displayMode: String = "auto"
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
        if (preferences.getBoolean("solar_manual", false)) {
            try {
                val latitude = preferences.getString("solar_lat", "")!!.toDouble()
                val longitude = preferences.getString("solar_lon", "")!!.toDouble()
                if (latitude.isFinite() && longitude.isFinite() &&
                    latitude in -90.0..90.0 && longitude in -180.0..180.0
                ) {
                    lat = latitude
                    lon = longitude
                    return true
                }
            } catch (_: RuntimeException) {
            }
        } else if (
            context.checkSelfPermission("android.permission.ACCESS_FINE_LOCATION") == PackageManager.PERMISSION_GRANTED ||
            context.checkSelfPermission("android.permission.ACCESS_COARSE_LOCATION") == PackageManager.PERMISSION_GRANTED
        ) {
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
        if (displayMode != "sun" && preferences.getBoolean("weather_enabled", false) && lat.isFinite() && lon.isFinite()) {
            forecast.refresh(lat, lon)
            val weather = forecast.value(System.currentTimeMillis(), lat, lon)
            if (weather != null) {
                val fahrenheit = preferences.getBoolean("weather_fahrenheit", false)
                val value = if (fahrenheit) weather.temperature * 9 / 5 + 32 else weather.temperature
                val temperature = kotlin.math.round(value).toInt()
                sun.setTextColor(ThemeTokens.get(context).textPrimary)
                sun.textSize = 16f * preferences.getInt("text", 100) / 100f
                sun.text = "$temperature\u00b0"
                icon.setDaytime(!night())
                sun.contentDescription = "Forecast ${sun.text}; downloaded " + downloadedFormat.apply { timeZone = zone }.format(java.util.Date(weather.fetchedAt))
                icon.setWeather(weather.code)
                return
            }
        }
        icon.setWeather(null)
        sun.setTextColor(ThemeTokens.get(context).textSecondary)
        sun.textSize = 11f * preferences.getInt("text", 100) / 100f
        if (lat.isNaN()) {
            sun.text = "--:--"
            sun.contentDescription = "Sunrise and sunset unavailable; set a location"
            icon.setDaytime(false)
            return
        }
        val state = SolarState(minute, sunrise, sunset, nextRise)
        val event = if (state.day) "Sunset" else "Sunrise"
        sun.text = format(state.next)
        sun.contentDescription = if (state.next < 0) {
            "Sunrise or sunset unavailable"
        } else {
            "$event ${format(state.next)}"
        }
        icon.setDaytime(state.day)
    }

    fun format(minutes: Int): String {
        if (minutes < 0) return "--:--"
        val hour = minutes / 60
        if (preferences.getBoolean("clock24", false)) {
            return String.format(Locale.US, "%02d:%02d", hour, minutes % 60)
        }
        return String.format(Locale.US, "%02d:%02d %s", (hour + 11) % 12 + 1, minutes % 60, if (hour < 12) "AM" else "PM")
    }

    fun night(): Boolean {
        if (sunrise < 0 || sunset < 0) return false
        val now = Calendar.getInstance(zone)
        val minute = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
        return minute >= sunset || minute < sunrise
    }
}
