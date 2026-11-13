package app.flare.auto
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
internal object DashboardPages {
    const val KEY = Keys.DASHBOARD_PAGES
    fun encode(pages: List<List<GridTile>>): String {
        val all = JSONArray()
        pages.forEach { page ->
            val rows = JSONArray()
            page.forEach { t ->
                // "style" is optional (older builds ignore it and use the Home clock style).
                rows.put(JSONObject().put("type", t.type).put("x", t.x).put("y", t.y).put("w", t.w).put("h", t.h).apply { t.style?.let { put("style", it) } })
            }
            all.put(rows)
        }
        return all.toString()
    }
    fun decode(raw: String?): MutableList<MutableList<GridTile>>? = try {
        val all = JSONArray(raw ?: "")
        require(all.length() in 1..5)
        MutableList(all.length()) { i ->
            val rows = all.getJSONArray(i)
            require(rows.length() <= 8)
            MutableList(rows.length()) { j ->
                val t = rows.getJSONObject(j)
                GridTile(t.getString("type"), t.getInt("x"), t.getInt("y"), t.getInt("w"), t.getInt("h"),
                    t.optString("style", "").takeIf { it in ClockStyles.ids })
            }.also { require(GridTile.validPage(it)) }
        }
    } catch (_: Exception) { null }
    fun initial(prefs: SharedPreferences): MutableList<MutableList<GridTile>> {
        decode(prefs.getString(KEY, null))?.let { return it }
        val tiles = mutableListOf<GridTile>()
        val left = LayoutPrefs.legacyCard(prefs, true); val right = LayoutPrefs.legacyCard(prefs, false)
        if (left != "hidden") tiles.add(GridTile(left, 0, 0, if (right == "hidden") 8 else 4, 6))
        if (right != "hidden" && right != left) tiles.add(GridTile(right, if (tiles.isEmpty()) 0 else 4, 0, if (tiles.isEmpty()) 8 else 4, 6))
        return mutableListOf(tiles)
    }
}
