package app.flare.auto

import android.app.Activity
import android.os.Bundle
import android.widget.TextView
import org.json.JSONObject
import org.json.JSONArray

class OptimizationChecksActivity : Activity() {
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        val results = ArrayList<String>()
        fun run(name: String, block: () -> Unit) {
            try { block(); results.add("PASS $name") }
            catch (e: Throwable) { results.add("FAIL $name: ${e.message}") }
        }
        val prefs = getSharedPreferences("home", 0)
        val cache = getSharedPreferences("forecast_cache", 0)
        val originalPrefs = HashMap(prefs.all)
        val originalCache = HashMap(cache.all)
        fun restore(p: android.content.SharedPreferences, values: Map<String, *>) {
            val edit = p.edit().clear()
            values.forEach { (key, value) -> when (value) {
                is String -> edit.putString(key, value)
                is Int -> edit.putInt(key, value)
                is Long -> edit.putLong(key, value)
                is Float -> edit.putFloat(key, value)
                is Boolean -> edit.putBoolean(key, value)
                is Set<*> -> edit.putStringSet(key, value.filterIsInstance<String>().toSet())
            } }
            edit.commit()
        }
        try {
            val hour = 1800000000L
            val now = hour * 1000 + 1000
            fun data(first: Int, second: Int) = JSONObject().put("hourly", JSONObject()
                .put("time", JSONArray().put(hour).put(hour + 3600))
                .put("temperature_2m", JSONArray().put(first).put(second))
                .put("weather_code", JSONArray().put(0).put(63))).toString()
            prefs.edit().putInt("weather_age_days", 2).commit()
            cache.edit().clear().putLong("fetched", now - 1000).putFloat("latitude", 31f).putFloat("longitude", 74f).putString("data", data(28, 26)).commit()
            val forecast = Forecast(this) { error("No network refresh expected") }
            run("forecast selects current hour") { check(forecast.value(now, 31.0, 74.0)?.temperature == 28.0) }
            run("forecast advances cached hour") { check(forecast.value(now + 3600000, 31.0, 74.0)?.temperature == 26.0) }
            run("forecast invalidates parsed cache") {
                cache.edit().putString("data", data(30, 29)).commit()
                check(forecast.value(now, 31.0, 74.0)?.temperature == 30.0)
            }
            run("forecast location mismatch") { check(forecast.value(now, 40.0, 74.0) == null) }
            run("forecast expires without extending offline lifetime") { check(forecast.value(now + 2 * 86400000L + 1, 31.0, 74.0) == null) }
            run("forecast malformed cache recovers") {
                cache.edit().putString("data", "invalid").commit()
                check(forecast.value(now, 31.0, 74.0) == null)
                cache.edit().putString("data", data(25, 24)).commit()
                check(forecast.value(now, 31.0, 74.0)?.temperature == 25.0)
            }
            run("custom icon role stable SHA-256") {
                check(CustomImages.iconRole("com.spotify.music") == "icon_" + java.security.MessageDigest.getInstance("SHA-256").digest("com.spotify.music".toByteArray()).joinToString("") { "%02x".format(it.toInt() and 255) })
            }
            run("old wallpaper selection and custom index preserved") {
                // WallpaperPack reads the active driver's store, which is not "home" when a custom driver is active.
                val active = ProfileStore.preferences(this)
                val saved = HashMap(active.all)
                try {
                    active.edit().remove("wallpaper_saver_v2").putInt("saver_wallpaper", 20).commit()
                    check(WallpaperPack.index(this, true) == WallpaperPack.CUSTOM_INDEX) { "index ${WallpaperPack.index(this, true)}" }
                    active.edit().putInt("saver_wallpaper", 17).commit()
                    check(WallpaperPack.name(this, true) == "Black to Burgundy") { "name ${WallpaperPack.name(this, true)}" }
                } finally { restore(active, saved) }
            }
        } finally { restore(prefs, originalPrefs); restore(cache, originalCache) }
        android.util.Log.i("FlareChecks", "Optimization " + results.joinToString(" | "))
        setContentView(TextView(this).apply { text = results.joinToString("\n"); textSize = 14f })
    }
}
