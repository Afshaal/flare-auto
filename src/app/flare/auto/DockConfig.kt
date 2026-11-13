package app.flare.auto

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/** What a dock slot does. [id] is the number stored in preferences: never renumber. */
internal enum class DockAction(val id: Int, val label: String, val glyph: String) {
    AUDIO(0, "Audio", "audio"),
    RADIO(1, "Radio", "radio"),
    BLUETOOTH(2, "Bluetooth", "bluetooth"),
    SCREEN_OFF(3, "Screen off", "screen"),
    /** The permanent app-drawer slot: always visible, exactly one. */
    APPS(4, "Apps", "apps"),
    /** A user-chosen application; [DockConfig.Item.target] holds its component. */
    APP(5, "Application", ""),
    /** Flare Auto's own full navigation map. */
    NAVIGATION(6, "Navigation", "navigation"),
    /**
     * Projection shortcuts with the Home widget's marks. [DockConfig.Item.target] may link a compatible installed app
     * (DiPlay, DiAuto, ZLink...); empty uses the same app the Home widget's button opens.
     */
    CARPLAY(7, "CarPlay", "carplay"),
    ANDROID_AUTO(8, "Android Auto", "androidauto");

    val builtIn get() = this != APP
    /** Actions that can be linked to an installed app while keeping their own icon and name. */
    val linkable get() = this == CARPLAY || this == ANDROID_AUTO

    companion object {
        private val byId = values().associateBy { it.id }
        fun of(id: Int): DockAction? = byId[id]
        /** Built-in actions a slot can be switched to (the drawer slot is permanent). */
        val ASSIGNABLE = listOf(AUDIO, RADIO, BLUETOOTH, SCREEN_OFF, NAVIGATION, CARPLAY, ANDROID_AUTO)
    }
}

internal object DockConfig {
    const val KEY = Keys.DOCK_ITEMS
    /** Icon sizes (dp) offered as small / medium / large. */
    val ICON_SIZES = intArrayOf(20, 26, 32)
    /** Most shortcuts shown in the dock (the app-drawer slot included). Older builds skip actions they do not know. */
    const val MAX_ITEMS = 6
    /** Shortcuts a left or right (side) dock shows, besides its Apps button. */
    const val SIDE_SHORTCUTS = 3

    /**
     * Stored indexes of the shortcuts a side dock shows: the first [SIDE_SHORTCUTS] visible items other than Apps.
     * Later items stay saved (and shown by the bottom dock); nothing is removed.
     */
    fun sideShortcuts(items: List<Item>): List<Int> =
        items.indices.filter { items[it].visible && items[it].action != DockAction.APPS }.take(SIDE_SHORTCUTS)

    class Item(var action: DockAction, var target: String, var visible: Boolean)

    fun read(preferences: SharedPreferences): ArrayList<Item> {
        val out = ArrayList<Item>()
        val raw = preferences.getString(KEY, "") ?: ""
        if (raw.isNotEmpty()) {
            try {
                val array = JSONArray(raw)
                var hasApps = false
                for (index in 0 until array.length()) {
                    val item = array.getJSONObject(index)
                    val action = DockAction.of(item.getInt("type")) ?: continue
                    if (action == DockAction.APPS) {
                        if (hasApps) continue
                        hasApps = true
                    }
                    out.add(Item(action, item.optString("target", ""), action == DockAction.APPS || item.optBoolean("visible", true)))
                }
                if (hasApps) return out
            } catch (_: Exception) {
            }
        }
        // Docks saved without a drawer slot, or from before "dock_items" existed, get the legacy order appended.
        for (id in legacyOrder(preferences)) out.add(Item(DockAction.of(id)!!, "", true))
        return out
    }

    /** The pre-1.0 "dock_order" preference: a permutation of the five built-in actions such as "0,1,2,3,4". */
    private fun legacyOrder(preferences: SharedPreferences): IntArray = try {
        val values = preferences.getString(Keys.DOCK_ORDER, "0,1,2,3,4")!!.split(",")
        val used = BooleanArray(5)
        IntArray(5) { index ->
            val value = values[index].toInt()
            require(value in 0..4 && !used[value])
            used[value] = true
            value
        }
    } catch (_: Exception) {
        intArrayOf(0, 1, 2, 3, 4)
    }

    fun save(preferences: SharedPreferences, items: List<Item>) {
        val array = JSONArray()
        for (item in items) {
            try {
                array.put(JSONObject().put("type", item.action.id).put("target", item.target)
                    .put("visible", item.action == DockAction.APPS || item.visible))
            } catch (_: JSONException) {
            }
        }
        preferences.edit().putString(KEY, array.toString()).apply()
    }

    /** Icon size ceiling (dp) for a stored "dock_icons" value; older builds stored sizes between 20 and 32. */
    fun iconCeiling(stored: Int): Int = when {
        stored < 23 -> ICON_SIZES[0]
        stored < 30 -> ICON_SIZES[1]
        else -> ICON_SIZES[2]
    }

    /** Icon size (dp) that fits [count] slots across the screen, at most [desired]. */
    fun size(context: Context, count: Int, desired: Int): Int {
        val metrics = context.resources.displayMetrics
        val width = (metrics.widthPixels / metrics.density).toInt() -
            2 * ProfileStore.preferences(context).getInt(Keys.SPACING, 14)
        return maxOf(1, minOf(desired, maxOf(1, width / maxOf(1, count) - 16)))
    }

    fun name(context: Context, item: Item): String =
        if (item.action.builtIn) item.action.label else AppLookup.label(context, item.target) ?: "App unavailable"
}
