package app.flare.auto

import android.content.ComponentName
import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

internal object DockConfig {
    const val KEY = "dock_items"
    @JvmField val NAMES = arrayOf("Audio", "Radio", "Bluetooth", "Screen off", "Apps")
    @JvmField val ICONS = arrayOf("audio", "radio", "bluetooth", "screen", "apps")

    class Item(@JvmField var type: Int, @JvmField var target: String, @JvmField var visible: Boolean)

    @JvmStatic
    fun read(preferences: SharedPreferences): ArrayList<Item> {
        val out = ArrayList<Item>()
        val raw = preferences.getString(KEY, "") ?: ""
        if (raw.isNotEmpty()) {
            try {
                val array = JSONArray(raw)
                var hasApps = false
                for (index in 0 until array.length()) {
                    val item = array.getJSONObject(index)
                    val type = item.getInt("type")
                    if (type !in 0..5) continue
                    if (type == 4) {
                        if (hasApps) continue
                        hasApps = true
                    }
                    out.add(Item(type, item.optString("target", ""), type == 4 || item.optBoolean("visible", true)))
                }
                if (hasApps) return out
            } catch (_: Exception) {
            }
        }
        for (type in DockOrder.read(preferences)) out.add(Item(type, "", true))
        return out
    }

    @JvmStatic
    fun save(preferences: SharedPreferences, items: ArrayList<Item>) {
        val array = JSONArray()
        for (item in items) {
            try {
                array.put(JSONObject().put("type", item.type).put("target", item.target)
                    .put("visible", item.type == 4 || item.visible))
            } catch (_: JSONException) {
            }
        }
        preferences.edit().putString(KEY, array.toString()).apply()
    }

    @JvmStatic
    fun maxSlots(context: Context): Int {
        val metrics = context.resources.displayMetrics
        val width = (metrics.widthPixels / metrics.density).toInt()
        val spacing = context.getSharedPreferences("home", 0).getInt("spacing", 14)
        return maxOf(1, (width - spacing * 2) / 56)
    }

    @JvmStatic
    fun size(context: Context, count: Int, desired: Int): Int {
        val metrics = context.resources.displayMetrics
        val width = (metrics.widthPixels / metrics.density).toInt() -
            2 * context.getSharedPreferences("home", 0).getInt("spacing", 14)
        return maxOf(1, minOf(desired, maxOf(1, width / maxOf(1, count) - 16)))
    }

    @JvmStatic
    fun name(context: Context, item: Item): String {
        if (item.type < 5) return NAMES[item.type]
        return try {
            context.packageManager.getActivityInfo(ComponentName.unflattenFromString(item.target)!!, 0)
                .loadLabel(context.packageManager).toString()
        } catch (_: Exception) {
            "App unavailable"
        }
    }
}
