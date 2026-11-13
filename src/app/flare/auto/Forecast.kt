package app.flare.auto

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Handler
import android.os.Looper
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

internal class Forecast(context: Context, private val updated: () -> Unit) {
    private val app = context.applicationContext
    private val cache = app.getSharedPreferences("forecast_cache", 0)
    private val home = ProfileStore.preferences(app)
    @Volatile private var active = false
    @Volatile private var generation = 0
    private var parsedRaw: String? = null
    private var parsedHourly: JSONObject? = null

    data class Value(val temperature: Double, val code: Int, val fetchedAt: Long)
    fun start() { active = true }
    fun stop() { active = false; generation++; requests.leave(this) }
    fun value(now: Long, lat: Double, lon: Double): Value? {
        val fetched = cache.getLong("fetched", 0)
        val maxAge = home.getInt(Keys.WEATHER_AGE_DAYS, 2).coerceIn(1, 7) * 86400000L
        if (fetched <= 0 || now < fetched || now - fetched > maxAge) return null
        if (kotlin.math.abs(cache.getFloat("latitude", 999f) - lat) > .25 ||
            kotlin.math.abs(cache.getFloat("longitude", 999f) - lon) > .25) return null
        return try {
            val raw = cache.getString("data", "") ?: ""
            if (raw != parsedRaw) {
                parsedHourly = JSONObject(raw).getJSONObject("hourly")
                parsedRaw = raw
            }
            parsedHourly?.let { valueAt(it, now, fetched) }
        } catch (_: Exception) { null }
    }
    fun refresh(lat: Double, lon: Double) {
        if (!active || !home.getBoolean(Keys.WEATHER_ENABLED, false) || !lat.isFinite() || !lon.isFinite()) return
        val now = System.currentTimeMillis()
        val samePlace = kotlin.math.abs(cache.getFloat("latitude", 999f) - lat) <= .25 &&
            kotlin.math.abs(cache.getFloat("longitude", 999f) - lon) <= .25
        if (samePlace && cache.getLong("fetched", 0) in (now - 1800000L)..now) return
        val key = String.format(Locale.US, "%.4f,%.4f", lat, lon)
        if (requests.isPending(key)) { requests.join(key, this, generation); return }
        val attempted = attempts[key] ?: 0L
        if (attempted > 0 && now >= attempted && now - attempted < 300000L) return
        val cm = app.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return
        if (cm.getNetworkCapabilities(cm.activeNetwork)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) != true) return
        attempts[key] = now
        if (attempts.size > 16) attempts.remove(attempts.keys.first())
        requests.join(key, this, generation)
        val appContext = app
        Background.execute {
            val result = download(lat, lon, now)
            main.post {
                val listeners = requests.complete(key).filter { (listener, token) -> listener.active && listener.generation == token }
                val settings = ProfileStore.preferences(appContext)
                if (result != null && listeners.isNotEmpty() && settings.getBoolean(Keys.WEATHER_ENABLED, false)) {
                    appContext.getSharedPreferences("forecast_cache", 0).edit().putString("data", result)
                        .putLong("fetched", System.currentTimeMillis()).putFloat("latitude", lat.toFloat()).putFloat("longitude", lon.toFloat()).apply()
                    listeners.forEach { (listener, _) -> listener.updated() }
                }
            }
        }
    }
    companion object {
        private val main = Handler(Looper.getMainLooper())
        private val requests = SharedRequests<String, Forecast>()
        private val attempts = LinkedHashMap<String, Long>()

        private fun download(lat: Double, lon: Double, now: Long): String? {
            return try {
                val address = String.format(Locale.US, "https://api.open-meteo.com/v1/forecast?latitude=%.4f&longitude=%.4f&hourly=temperature_2m,weather_code&timeformat=unixtime&timezone=GMT&forecast_days=8", lat, lon)
                val connection = URL(address).openConnection() as HttpURLConnection
                try {
                    connection.connectTimeout = 10000; connection.readTimeout = 10000
                    if (connection.responseCode != 200) return null
                    val text = connection.inputStream.bufferedReader().use { reader ->
                        val chars = CharArray(8192); val output = StringBuilder()
                        while (output.length <= 200000) {
                            val count = reader.read(chars, 0, minOf(chars.size, 200001 - output.length))
                            if (count <= 0) break
                            output.append(chars, 0, count)
                        }
                        output.toString()
                    }
                    text.takeIf { it.length <= 200000 && parse(it, System.currentTimeMillis(), now) != null }
                } finally { connection.disconnect() }
            } catch (_: Exception) { null }
        }

        fun parse(raw: String, now: Long, fetched: Long): Value? {
            val hourly = JSONObject(raw).getJSONObject("hourly")
            return valueAt(hourly, now, fetched)
        }

        private fun valueAt(hourly: JSONObject, now: Long, fetched: Long): Value? {
            val times = hourly.getJSONArray("time")
            val temps = hourly.getJSONArray("temperature_2m")
            val codes = hourly.getJSONArray("weather_code")
            val count = minOf(times.length(), temps.length(), codes.length())
            var chosen = -1
            for (i in 0 until count) { val seconds = times.optLong(i, -1); if (seconds < 0 || seconds > Long.MAX_VALUE / 1000) break; if (seconds * 1000 <= now) chosen = i else break }
            if (chosen < 0) return null
            val hour = times.optLong(chosen, -1) * 1000
            if (hour < 0 || now - hour >= 3600000L || temps.isNull(chosen) || codes.isNull(chosen)) return null
            val temp = temps.getDouble(chosen)
            if (!temp.isFinite()) return null
            return Value(temp, codes.getInt(chosen), fetched)
        }
    }
}

/** Short condition name for a WMO weather code (the codes the forecast cache stores). */
internal fun weatherName(code: Int): String = when (code) {
    0 -> "Clear"
    1 -> "Mostly clear"
    2 -> "Partly cloudy"
    3 -> "Overcast"
    45, 48 -> "Fog"
    in 51..57 -> "Drizzle"
    in 61..67 -> "Rain"
    in 71..77 -> "Snow"
    in 80..82 -> "Showers"
    85, 86 -> "Snow showers"
    in 95..99 -> "Thunderstorm"
    else -> "Weather"
}
