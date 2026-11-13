package app.flare.auto

import android.app.Activity
import android.app.Dialog
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView

/**
 * Full-screen offline destination search: an empty field, the Home and Work slots, starred places and recent
 * destinations; while typing, ranked results from the navigation pack ([OfflineSearch]) or "latitude, longitude".
 * Results and saved places carry a star; holding a result sets it as Home or Work. Fully offline; places are
 * stored per driver profile ([SavedPlaces]).
 */
internal object OfflineDestinationPicker {
    /** A destination. [detail] is the second line shown for it (kind · street · area), never coordinates. */
    data class Place(val name: String, val kind: String, val lat: Double, val lon: Double, val detail: String = "")

    fun show(a: Activity, pack: NavigationPack.Pack, reference: Pair<Double, Double>?, current: Place?, chosen: (Place) -> Unit): Dialog {
        val ui = UiKit(a)
        val t = ui.t
        val saved = SavedPlaces(ProfileStore.preferences(a))
        val main = Handler(Looper.getMainLooper())
        var token = 0
        var open = true
        val dialog = Dialog(a, android.R.style.Theme_Material_NoActionBar_Fullscreen)

        val root = LinearLayout(a).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(t.background) }
        val frame = FrameLayout(a).apply { setBackgroundColor(t.background) }
        frame.addView(root, FrameLayout.LayoutParams(minOf(ui.dp(820), a.resources.displayMetrics.widthPixels), MATCH, Gravity.CENTER_HORIZONTAL))

        // Top bar: back, rounded field with magnifier and clear button.
        val bar = LinearLayout(a).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(ui.dp(12), ui.dp(12), ui.dp(16), ui.dp(8)) }
        val back = ImageView(a).apply {
            setImageDrawable(MapIcon("back", t.textPrimary)); contentDescription = "Back"
            val pad = ui.dp(12); setPadding(pad, pad, pad, pad)
            setOnClickListener { dialog.dismiss() }
        }
        bar.addView(back, LinearLayout.LayoutParams(ui.dp(52), ui.dp(52)))
        val field = LinearLayout(a).apply {
            gravity = Gravity.CENTER_VERTICAL
            background = GradientDrawable().apply { setColor(t.card); cornerRadius = ui.dp(26).toFloat(); setStroke(ui.dp(1), t.cardBorder) }
            setPadding(ui.dp(16), 0, ui.dp(6), 0)
        }
        field.addView(ImageView(a).apply { setImageDrawable(SearchGlyph(t.textSecondary)) }, LinearLayout.LayoutParams(ui.dp(20), ui.dp(20)))
        val input = EditText(a).apply {
            setSingleLine(); hint = "Search places, streets or areas"; textSize = 18f
            setTextColor(t.textPrimary); setHintTextColor(t.textSecondary); background = null
            imeOptions = EditorInfo.IME_ACTION_SEARCH or EditorInfo.IME_FLAG_NO_EXTRACT_UI
            setPadding(ui.dp(12), 0, ui.dp(8), 0)
        }
        field.addView(input, LinearLayout.LayoutParams(0, ui.dp(52), 1f))
        val clear = ImageView(a).apply {
            setImageDrawable(MapIcon("close", t.textSecondary)); contentDescription = "Clear search"
            val pad = ui.dp(12); setPadding(pad, pad, pad, pad); visibility = View.GONE
            setOnClickListener { input.setText("") }
        }
        field.addView(clear, LinearLayout.LayoutParams(ui.dp(44), ui.dp(44)))
        bar.addView(field, LinearLayout.LayoutParams(0, ui.dp(52), 1f).apply { leftMargin = ui.dp(4) })
        root.addView(bar, LinearLayout.LayoutParams(MATCH, WRAP))

        val scroll = ScrollView(a).apply { isVerticalScrollBarEnabled = false }
        val list = LinearLayout(a).apply { orientation = LinearLayout.VERTICAL; setPadding(ui.dp(16), 0, ui.dp(16), ui.dp(16)) }
        scroll.addView(list)
        root.addView(scroll, LinearLayout.LayoutParams(MATCH, 0, 1f))
        val status = ui.text("", 12, t.textSecondary).apply { setPadding(ui.dp(24), ui.dp(4), ui.dp(24), ui.dp(8)) }
        root.addView(status, LinearLayout.LayoutParams(MATCH, WRAP))
        // A composite index names all of its sources (pack manifest), not only OpenStreetMap.
        val credit = "${pack.region} offline · " + (pack.searchCredit?.let { "Search data: $it" } ?: "© OpenStreetMap contributors")

        fun pick(place: Place) {
            saved.addRecent(place)
            dialog.dismiss()
            chosen(place)
        }

        lateinit var refresh: () -> Unit

        fun header(title: String, action: String? = null, onAction: (() -> Unit)? = null) {
            val row = LinearLayout(a).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(ui.dp(8), ui.dp(16), ui.dp(4), ui.dp(6)) }
            row.addView(ui.text(title, 13, t.accent).apply { setTypeface(Typeface.DEFAULT, Typeface.BOLD); letterSpacing = .05f }, LinearLayout.LayoutParams(0, WRAP, 1f))
            if (action != null) row.addView(ui.text(action, 14, t.accent).apply {
                setPadding(ui.dp(12), ui.dp(6), ui.dp(12), ui.dp(6)); setOnClickListener { onAction?.invoke() }
            })
            list.addView(row)
        }

        fun row(icon: Drawable, title: String, subtitle: String, distance: String, place: Place?, onClick: () -> Unit, onLong: (() -> Unit)? = null) {
            val item = LinearLayout(a).apply {
                gravity = Gravity.CENTER_VERTICAL; minimumHeight = ui.dp(64)
                setPadding(ui.dp(14), ui.dp(6), ui.dp(4), ui.dp(6))
                background = GradientDrawable().apply { setColor(t.card); cornerRadius = ui.dp(12).toFloat() }
                setOnClickListener { onClick() }
                if (onLong != null) setOnLongClickListener { onLong(); true }
                contentDescription = listOf(title, subtitle, distance).filter { it.isNotEmpty() }.joinToString(", ")
            }
            item.addView(ImageView(a).apply { setImageDrawable(icon) }, LinearLayout.LayoutParams(ui.dp(24), ui.dp(24)).apply { rightMargin = ui.dp(14) })
            val text = LinearLayout(a).apply { orientation = LinearLayout.VERTICAL }
            text.addView(ui.text(title, 17, t.textPrimary).apply { isSingleLine = true; ellipsize = TextUtils.TruncateAt.END })
            if (subtitle.isNotEmpty()) text.addView(ui.text(subtitle, 13, t.textSecondary).apply { isSingleLine = true; ellipsize = TextUtils.TruncateAt.END },
                LinearLayout.LayoutParams(WRAP, WRAP).apply { topMargin = ui.dp(2) })
            item.addView(text, LinearLayout.LayoutParams(0, WRAP, 1f))
            if (distance.isNotEmpty()) item.addView(ui.text(distance, 14, t.textSecondary).apply { setPadding(ui.dp(8), 0, ui.dp(4), 0) })
            if (place != null) {
                val on = saved.isStarred(place)
                item.addView(ImageView(a).apply {
                    setImageDrawable(MapIcon(if (on) "starred" else "star", if (on) NavColors.STAR else t.textSecondary))
                    contentDescription = if (on) "Remove ${place.name} from saved places" else "Save ${place.name}"
                    val pad = ui.dp(13); setPadding(pad, pad, pad, pad)
                    setOnClickListener { saved.toggleStar(place); refresh() }
                }, LinearLayout.LayoutParams(ui.dp(50), ui.dp(50)))
            } else item.addView(View(a), LinearLayout.LayoutParams(ui.dp(12), 1))
            list.addView(item, LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = ui.dp(6) })
        }

        fun distanceTo(place: Place) = reference?.let { OfflineSearch.distanceLabel(NavGeometry.distance(it.first, it.second, place.lat, place.lon)) } ?: ""

        fun slotMenu(place: Place) {
            ThemedDialogs.Builder(a).setTitle(place.name).setItems(arrayOf("Set as Home", "Set as Work")) { _, i ->
                if (i == 0) saved.setHome(place) else saved.setWork(place)
                android.widget.Toast.makeText(a, "${place.name} saved as ${if (i == 0) "Home" else "Work"}", android.widget.Toast.LENGTH_SHORT).show()
                refresh()
            }.show()
        }

        fun slot(name: String, icon: String, place: Place?, set: (Place?) -> Unit) {
            if (place != null) row(MapIcon(icon, t.textPrimary), name, place.name, distanceTo(place), null, { pick(place) }) {
                ThemedDialogs.Builder(a).setTitle(name).setItems(arrayOf("Remove $name")) { _, _ -> set(null); refresh() }.show()
            } else row(MapIcon(icon, t.textSecondary), name, "Not set · search, then hold a result to set $name", "", null, {
                input.requestFocus()
                (a.getSystemService(Activity.INPUT_METHOD_SERVICE) as? InputMethodManager)?.showSoftInput(input, 0)
            })
        }

        fun emptyState() {
            list.removeAllViews()
            header("PLACES")
            slot("Home", "home", saved.home()) { saved.setHome(it) }
            slot("Work", "work", saved.work()) { saved.setWork(it) }
            if (current != null) row(MapIcon("pin", t.textPrimary), current.name, "Current destination", distanceTo(current), current, { pick(current) })
            val starred = saved.starred()
            if (starred.isNotEmpty()) {
                header("SAVED")
                for (place in starred) row(MapIcon("starred", NavColors.STAR), place.name, place.detail, distanceTo(place), place, { pick(place) }) { slotMenu(place) }
            }
            val recents = saved.recents()
            if (recents.isNotEmpty()) {
                header("RECENT", "Clear") { saved.clearRecents(); refresh() }
                for (place in recents) row(MapIcon("recent", t.textSecondary), place.name, place.detail, distanceTo(place), place, { pick(place) }) { slotMenu(place) }
            }
            if (starred.isEmpty() && recents.isEmpty()) list.addView(ui.text("Tap the star on a result to save it here. Places you drive to appear under Recent.", 14, t.textSecondary).apply {
                setPadding(ui.dp(8), ui.dp(18), ui.dp(8), 0)
            })
            status.text = credit
        }

        fun results(found: List<OfflineSearch.Ranked>) {
            list.removeAllViews()
            for (r in found) {
                val hit = r.hit
                val place = Place(hit.displayName, hit.kind, hit.lat, hit.lon, OfflineSearch.detail(hit))
                row(MapIcon("pin", t.textSecondary), place.name, place.detail, OfflineSearch.distanceLabel(r.metres), place, { pick(place) }) { slotMenu(place) }
            }
        }

        val search = Runnable {
            val query = input.text.toString().trim()
            val request = ++token
            if (query.isEmpty()) { emptyState(); return@Runnable }
            val parts = query.split(',').map { it.trim().toDoubleOrNull() }
            val lat = parts.getOrNull(0)
            val lon = parts.getOrNull(1)
            if (parts.size == 2 && lat != null && lon != null) {
                list.removeAllViews()
                if (pack.covers(lat, lon)) {
                    val place = Place("Selected coordinates", "coordinate", lat, lon)
                    row(MapIcon("pin", t.textSecondary), "Drive to these coordinates", "", distanceTo(place), null, { pick(place) })
                    status.text = credit
                } else status.text = "Those coordinates are outside ${pack.region} offline coverage"
                return@Runnable
            }
            if (OfflineSearch.normalize(query).isEmpty()) return@Runnable
            status.text = "Searching…"
            OfflineRouting.worker.execute {
                val found = runCatching { OfflineSearch.find(pack.search, query, reference?.first, reference?.second) }
                main.post {
                    if (!open || request != token || a.isDestroyed) return@post
                    val places = found.getOrDefault(emptyList())
                    results(places)
                    status.text = when {
                        found.isFailure -> "Search unavailable: ${found.exceptionOrNull()?.message}"
                        places.isEmpty() -> "No match in ${pack.region}. Try another spelling, or hold the map to pick a point."
                        else -> credit
                    }
                    scroll.scrollTo(0, 0)
                }
            }
        }
        refresh = { if (input.text.isNullOrBlank()) emptyState() else { main.removeCallbacks(search); main.post(search) } }
        input.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                token++
                clear.visibility = if (s.isNullOrEmpty()) View.GONE else View.VISIBLE
                main.removeCallbacks(search)
                main.postDelayed(search, if (s.isNullOrBlank()) 0 else 250)
            }
            override fun afterTextChanged(e: Editable?) = Unit
        })
        input.setOnEditorActionListener { _, action, _ ->
            if (action == EditorInfo.IME_ACTION_SEARCH) {
                (a.getSystemService(Activity.INPUT_METHOD_SERVICE) as? InputMethodManager)?.hideSoftInputFromWindow(input.windowToken, 0)
                true
            } else false
        }
        emptyState()
        dialog.setContentView(frame)
        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(t.background))
            setLayout(MATCH, MATCH)
            setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE or WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN)
            immersive()
        }
        dialog.setOnDismissListener { open = false; token++; main.removeCallbacks(search) }
        dialog.show()
        return dialog
    }

    /** OSM tag value as a label: "fast_food" -> "Fast food". */
    fun kindLabel(kind: String) = OfflineSearch.kindLabel(kind)
}
