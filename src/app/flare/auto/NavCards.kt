package app.flare.auto

import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Colours of the cards over the map. The map style is night-only whatever the launcher theme is, so the cards are
 * too: near-black glass with white text keeps them readable in either launcher theme.
 */
internal object NavColors {
    const val CARD = 0xeb11161f.toInt()
    const val BORDER = 0x33ffffff
    const val TEXT = 0xffffffff.toInt()
    const val MUTED = 0xffaab4c3.toInt()
    const val TURN = 0xf0165c46.toInt()       // dark green turn card, like in-car navigation
    const val END = 0xffe5484d.toInt()
    const val STAR = 0xffffc531.toInt()

    fun card(c: Context, color: Int = CARD, radius: Int = 14): GradientDrawable = GradientDrawable().apply {
        setColor(color); cornerRadius = c.dp(radius).toFloat(); setStroke(c.dp(1), BORDER)
    }
}

/**
 * Next-turn card: direction arrow, distance to the turn and (full map only) the road name, cut with "…".
 * [message] replaces the content by one line of status text (calculating, waiting for GPS, arrived…).
 */
internal class TurnCard(context: Context, private val compact: Boolean) : LinearLayout(context) {
    private val arrow = TurnArrow(0, NavColors.TEXT)
    private val icon = ImageView(context).apply { setImageDrawable(arrow) }
    private val distance = TextView(context).apply {
        setTextColor(NavColors.TEXT); textSize = if (compact) 17f else 26f; setTypeface(Typeface.DEFAULT, Typeface.BOLD)
        includeFontPadding = false; isSingleLine = true
    }
    private val road = TextView(context).apply {
        setTextColor(NavColors.TEXT); textSize = 16f; isSingleLine = true; ellipsize = TextUtils.TruncateAt.END
        includeFontPadding = false; alpha = .92f
    }
    private val status = TextView(context).apply {
        setTextColor(NavColors.TEXT); textSize = if (compact) 13f else 15f; maxLines = 2; ellipsize = TextUtils.TruncateAt.END
        visibility = View.GONE
    }

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        background = NavColors.card(context, NavColors.TURN, if (compact) 12 else 16)
        val pad = context.dp(if (compact) 8 else 12)
        setPadding(pad, pad, context.dp(if (compact) 12 else 16), pad)
        val iconSize = context.dp(if (compact) 26 else 44)
        addView(icon, LayoutParams(iconSize, iconSize).apply { rightMargin = context.dp(if (compact) 6 else 12) })
        val text = LinearLayout(context).apply { orientation = VERTICAL }
        text.addView(distance, LayoutParams(WRAP, WRAP))
        if (!compact) text.addView(road, LayoutParams(WRAP, WRAP).apply { topMargin = context.dp(4) })
        addView(text, LayoutParams(WRAP, WRAP))
        addView(status, LayoutParams(WRAP, WRAP))
        visibility = View.GONE
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
    }

    fun setMaxWidth(px: Int) { road.maxWidth = px - context.dp(44 + 12 + 28); status.maxWidth = px - context.dp(24) }

    fun turn(sign: Int, metres: Double, roadName: String) {
        arrow.sign = sign
        icon.visibility = View.VISIBLE; status.visibility = View.GONE
        distance.visibility = View.VISIBLE
        distance.text = TripFormat.distance(metres)
        road.text = roadName
        road.visibility = if (roadName.isEmpty()) View.GONE else View.VISIBLE
        contentDescription = "In ${distance.text}, $roadName"
        visibility = View.VISIBLE
    }

    fun message(text: String) {
        icon.visibility = View.GONE; distance.visibility = View.GONE; road.visibility = View.GONE
        status.visibility = View.VISIBLE; status.text = text; contentDescription = text
        visibility = View.VISIBLE
    }

    fun hide() { visibility = View.GONE }
}

/**
 * Trip summary: arrival time, remaining travel time and distance. Full map: a compact card with three equal,
 * centred columns (value above its label: "11:58 / arrival", "16 / min", "15 / km"), a star for the destination and
 * an End button; Home widget ([compact]): one centred line.
 */
internal class TripCard(context: Context, private val compact: Boolean) : LinearLayout(context) {
    private val eta = value(if (compact) 15f else 21f)
    private val time = value(if (compact) 15f else 21f)
    private val distance = value(if (compact) 15f else 21f)
    private val timeUnit = label("min")
    private val distanceUnit = label("km")
    /** Full map only: star the destination ([onStar]) and end navigation ([onEnd]). */
    val star: ImageView? = if (compact) null else ImageView(context)
    private val starIcon = MapIcon("star", NavColors.TEXT)
    var onStar: (() -> Unit)? = null
    var onEnd: (() -> Unit)? = null

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        background = NavColors.card(context, NavColors.CARD, if (compact) 12 else 16)
        if (compact) {
            setPadding(context.dp(14), context.dp(6), context.dp(14), context.dp(6))
            addView(eta); addView(dot()); addView(time); addView(dot()); addView(distance)
        } else {
            setPadding(context.dp(6), context.dp(6), context.dp(8), context.dp(6))
            // Lighter values than before (regular weight instead of bold); every value shrinks to fit its column.
            for (v in listOf(eta, time, distance)) {
                v.typeface = Typeface.create("sans-serif", Typeface.NORMAL)
                v.gravity = Gravity.CENTER
                v.setAutoSizeTextTypeUniformWithConfiguration(12, 21, 1, android.util.TypedValue.COMPLEX_UNIT_SP)
            }
            val columnWidth = context.dp(62)
            addView(column(eta, label("arrival")), LayoutParams(columnWidth, WRAP))
            addView(column(time, timeUnit), LayoutParams(columnWidth, WRAP))
            addView(column(distance, distanceUnit), LayoutParams(columnWidth, WRAP))
            val size = context.dp(40)
            star!!.apply {
                setImageDrawable(starIcon); contentDescription = "Save destination"
                val p = context.dp(10); setPadding(p, p, p, p)
                setOnClickListener { onStar?.invoke() }
            }
            addView(star, LayoutParams(size, size).apply { leftMargin = context.dp(4) })
            val end = TextView(context).apply {
                text = "End"; textSize = 13f; setTypeface(Typeface.DEFAULT, Typeface.BOLD); setTextColor(NavColors.TEXT)
                gravity = Gravity.CENTER; contentDescription = "End navigation"
                background = GradientDrawable().apply { setColor(NavColors.END); cornerRadius = context.dp(16).toFloat() }
                setOnClickListener { onEnd?.invoke() }
            }
            addView(end, LayoutParams(context.dp(52), context.dp(32)).apply { leftMargin = context.dp(4) })
        }
        visibility = View.GONE
    }

    private fun value(size: Float) = TextView(context).apply {
        setTextColor(NavColors.TEXT); textSize = size; setTypeface(Typeface.DEFAULT, Typeface.BOLD); includeFontPadding = false
        isSingleLine = true
    }

    private fun dot() = TextView(context).apply { text = "  ·  "; setTextColor(NavColors.MUTED); textSize = 15f }

    private fun label(text: String) = TextView(context).apply {
        this.text = text; textSize = 12f; setTextColor(NavColors.MUTED); gravity = Gravity.CENTER; isSingleLine = true
        includeFontPadding = false
    }

    /** Value above its label, both centred in the column. */
    private fun column(value: TextView, label: TextView) = LinearLayout(context).apply {
        orientation = VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        addView(value, LayoutParams(MATCH, context.dp(26)))
        addView(label, LayoutParams(MATCH, WRAP).apply { topMargin = context.dp(2) })
    }

    fun show(metres: Double, millis: Long, clock24: Boolean) {
        if (compact) {
            eta.text = TripFormat.arrival(System.currentTimeMillis(), millis, clock24)
            time.text = TripFormat.duration(millis)
            distance.text = TripFormat.distance(metres)
            contentDescription = "Arrival ${eta.text}, ${time.text}, ${distance.text}"
        } else {
            eta.text = TripFormat.clock(System.currentTimeMillis(), millis, clock24)
            val (t, tu) = TripFormat.durationParts(millis)
            val (d, du) = TripFormat.distanceParts(metres)
            time.text = t; timeUnit.text = tu; distance.text = d; distanceUnit.text = du
            contentDescription = "Arrival ${TripFormat.arrival(System.currentTimeMillis(), millis, clock24)}, $t $tu, $d $du"
        }
        visibility = View.VISIBLE
    }

    fun starred(on: Boolean) {
        val icon = MapIcon(if (on) "starred" else "star", if (on) NavColors.STAR else NavColors.TEXT)
        star?.setImageDrawable(icon)
        star?.contentDescription = if (on) "Remove destination from saved places" else "Save destination"
    }

    fun hide() { visibility = View.GONE }
}

/** Round icon button for the map controls (search, back, recenter, orientation, voice). */
internal fun Context.mapButton(icon: android.graphics.drawable.Drawable, description: String, size: Int = 48, action: () -> Unit): ImageView =
    ImageView(this).apply {
        setImageDrawable(icon)
        contentDescription = description
        val pad = dp(size * 0.26f)
        setPadding(pad, pad, pad, pad)
        background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(NavColors.CARD); setStroke(dp(1), NavColors.BORDER) }
        elevation = dp(2).toFloat()
        setOnClickListener { action() }
    }

/** Two stacked buttons sharing one rounded pill (zoom in / out). */
internal fun Context.mapButtonPair(top: ImageView, bottom: ImageView, size: Int = 48): LinearLayout =
    LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = GradientDrawable().apply { setColor(NavColors.CARD); cornerRadius = dp(size / 2).toFloat(); setStroke(dp(1), NavColors.BORDER) }
        elevation = dp(2).toFloat()
        for (b in listOf(top, bottom)) { b.background = null; addView(b, LinearLayout.LayoutParams(dp(size), dp(size))) }
        addView(View(context).apply { setBackgroundColor(NavColors.BORDER) }, 1, LinearLayout.LayoutParams(dp(size - 18), dp(1)).apply { gravity = Gravity.CENTER_HORIZONTAL })
    }

