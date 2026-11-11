package app.flare.auto

import android.app.Activity
import android.app.Dialog
import android.database.sqlite.SQLiteDatabase
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ListView

/** Destination search over the pack's place database (SQLite FTS4), or "latitude, longitude". Fully offline. */
internal object OfflineDestinationPicker {
    data class Place(val name: String, val kind: String, val lat: Double, val lon: Double)

    fun show(a: Activity, pack: NavigationPack.Pack, chosen: (Place) -> Unit): Dialog {
        val ui = UiKit(a)
        val main = Handler(Looper.getMainLooper())
        var token = 0
        var open = true
        val body = ui.column()
        val input = EditText(a).apply { setSingleLine(); hint = "Place, street or latitude, longitude"; setTextColor(ui.t.textPrimary) }
        body.addView(input)
        val status = ui.text("Search ${pack.region} offline · © OpenStreetMap contributors", 12, ui.t.textSecondary)
        body.addView(status)
        val list = ListView(a).apply { divider = null }
        body.addView(list, LinearLayout.LayoutParams(MATCH, (a.resources.displayMetrics.heightPixels * .45f).toInt()))
        var places = emptyList<Place>()
        val dialog = ThemedDialogs.Builder(a).setTitle("Offline destination").setView(body).setNegativeButton("Close", null).create()
        list.setOnItemClickListener { _, _, i, _ -> places.getOrNull(i)?.let { dialog.dismiss(); chosen(it) } }
        val search = Runnable {
            val query = input.text.toString().trim()
            val request = ++token
            if (query.length < 2) { status.text = "Enter at least two characters"; list.adapter = null; return@Runnable }
            status.text = "Searching local places…"
            OfflineRouting.worker.execute {
                val found = runCatching { find(pack, query) }
                main.post {
                    if (!open || request != token || a.isDestroyed) return@post
                    places = found.getOrDefault(emptyList())
                    status.text = when {
                        found.isFailure -> "Search unavailable: ${found.exceptionOrNull()?.message}"
                        places.isEmpty() -> "No local match. Try coordinates or long-press the map."
                        else -> "${places.size} local matches · © OpenStreetMap contributors"
                    }
                    list.adapter = object : BaseAdapter() {
                        override fun getCount() = places.size
                        override fun getItem(i: Int) = places[i]
                        override fun getItemId(i: Int) = i.toLong()
                        override fun getView(i: Int, v: View?, parent: ViewGroup) =
                            ui.text(places[i].name + "\n" + kindLabel(places[i].kind), 14, ui.t.textPrimary).apply {
                                minHeight = ui.dp(54)
                                setPadding(ui.dp(8), ui.dp(6), ui.dp(8), ui.dp(6))
                            }
                    }
                }
            }
        }
        input.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                token++
                main.removeCallbacks(search)
                main.postDelayed(search, 300)
            }
            override fun afterTextChanged(e: Editable?) = Unit
        })
        dialog.setOnDismissListener { open = false; token++; main.removeCallbacks(search) }
        dialog.show()
        return dialog
    }

    /** OSM tag value as a label: "fast_food" -> "Fast food". */
    fun kindLabel(kind: String) = kind.replace('_', ' ').replaceFirstChar { it.uppercase() }

    /** Blocking: coordinates typed as "lat, lon" inside the pack, else up to 40 full-text matches. */
    fun find(pack: NavigationPack.Pack, query: String): List<Place> {
        val parts = query.split(',').map { it.trim().toDoubleOrNull() }
        val lat = parts.getOrNull(0)
        val lon = parts.getOrNull(1)
        if (parts.size == 2 && lat != null && lon != null && pack.covers(lat, lon)) return listOf(Place("Selected coordinates", "coordinate", lat, lon))
        val terms = query.split(Regex("\\s+")).filter { term -> term.any(Char::isLetterOrDigit) }.take(6)
            .joinToString(" ") { "\"" + it.replace("\"", "\"\"") + "\"*" }
        if (terms.isBlank()) return emptyList()
        return SQLiteDatabase.openDatabase(pack.search.absolutePath, null, SQLiteDatabase.OPEN_READONLY).use { db ->
            db.rawQuery("SELECT p.name,p.kind,p.lat,p.lon FROM places p JOIN places_fts f ON p.id=f.rowid WHERE places_fts MATCH ? LIMIT 40", arrayOf(terms)).use { rows ->
                buildList { while (rows.moveToNext()) add(Place(rows.getString(0), rows.getString(1), rows.getDouble(2), rows.getDouble(3))) }
            }
        }
    }
}
