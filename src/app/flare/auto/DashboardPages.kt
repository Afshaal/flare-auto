package app.flare.auto
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
internal object DashboardPages {
    const val KEY = "dashboard_pages"
    fun encode(pages: List<List<GridTile>>): String {
        val all = JSONArray()
        pages.forEach { page ->
            val rows = JSONArray()
            page.forEach { rows.put(JSONObject().put("type", it.type).put("x", it.x).put("y", it.y).put("w", it.w).put("h", it.h)) }
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
                GridTile(t.getString("type"), t.getInt("x"), t.getInt("y"), t.getInt("w"), t.getInt("h"))
            }.also { require(GridTile.validPage(it)) }
        }
    } catch (_: Exception) { null }
    fun initial(prefs: SharedPreferences): MutableList<MutableList<GridTile>> {
        decode(prefs.getString(KEY, null))?.let { return it }
        val tiles = mutableListOf<GridTile>()
        val left = HomeActivity.card(prefs, true); val right = HomeActivity.card(prefs, false)
        if (left != "hidden") tiles.add(GridTile(left, 0, 0, if (right == "hidden") 8 else 4, 6))
        if (right != "hidden" && right != left) tiles.add(GridTile(right, if (tiles.isEmpty()) 0 else 4, 0, if (tiles.isEmpty()) 8 else 4, 6))
        return mutableListOf(tiles)
    }
}
