package app.flare.auto

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

/** Home's top bar (date and status icons), the accent rule under it, and the dock. Shared with the editor preview. */
internal object HomeChrome {
    /** Top bar row; [date] is the bold date label on the left. */
    class TopBar(context: Context, prefs: SharedPreferences, t: ThemeTokens) : LinearLayout(context) {
        val date: TextView = TextView(context).apply {
            textSize = 19 * prefs.textScale
            setTextColor(t.textPrimary)
            gravity = Gravity.CENTER_VERTICAL
            setTypeface(Typeface.DEFAULT, Typeface.BOLD)
        }

        init {
            gravity = Gravity.CENTER_VERTICAL
            val icon = context.dp(24)
            addView(date, LayoutParams(0, context.dp(40), 1f))
            addView(DeviceStatus(context, false), LayoutParams(icon, icon))
            addView(WifiStatus(context), LayoutParams(icon, icon).apply { leftMargin = context.dp(14) })
            addView(DeviceStatus(context, true), LayoutParams(icon, icon).apply { leftMargin = context.dp(14) })
        }
    }

    /** Accent rule: transparent ends, accent blended 70/30 with the secondary text colour in the middle. */
    fun accent(t: ThemeTokens): GradientDrawable {
        val end = (t.accent and 0x00ffffff) or 0x18000000
        val middle = Color.rgb(
            (Color.red(t.accent) * 7 + Color.red(t.textSecondary) * 3) / 10,
            (Color.green(t.accent) * 7 + Color.green(t.textSecondary) * 3) / 10,
            (Color.blue(t.accent) * 7 + Color.blue(t.textSecondary) * 3) / 10)
        return GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, intArrayOf(end, middle, end))
    }

    /** Stored dock placement: "left", "right", or "bottom" for the default and any unknown value. */
    fun dockSide(p: SharedPreferences): String = when (p.getString(Keys.DOCK_POSITION, "bottom")) {
        "left" -> "left"
        "right" -> "right"
        else -> "bottom"
    }

    /** Side dock: [bar] is the whole edge strip; [shortcuts] holds the reorderable shortcuts (not the Apps button). */
    class SideDock(val bar: LinearLayout, val shortcuts: LinearLayout)

    /**
     * Side dock (left or right placement): one detached, rounded, see-through panel with the clock and the cellular and
     * Wi-Fi status at the top, up to [DockConfig.SIDE_SHORTCUTS] shortcut tiles centred in the middle and a small Apps
     * button anchored at the bottom. No labels. Saved shortcuts beyond the first three are kept for the bottom dock.
     * [height] is the panel height in pixels, used to size the tiles so nothing clips.
     */
    fun sideDock(c: Context, p: SharedPreferences, t: ThemeTokens, height: Int, click: (Int, DockConfig.Item) -> Unit): SideDock {
        val radius = c.dp(18).toFloat()
        val bar = LinearLayout(c).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            // Wallpaper shows through: a light tint of the card colour and a faint rim.
            background = GradientDrawable().apply {
                cornerRadius = radius
                setColor((t.card and 0x00ffffff) or 0x66000000)
                setStroke(maxOf(1, c.dp(1) / 2 + 1), (t.textPrimary and 0x00ffffff) or 0x22000000)
            }
            setPadding(c.dp(6), c.dp(12), c.dp(6), c.dp(8))
        }
        bar.addView(android.widget.TextClock(c).apply {
            val pattern = if (p.clock24) "HH:mm" else "h:mm"
            format12Hour = pattern; format24Hour = pattern
            textSize = 18 * p.textScale
            setTextColor(t.textPrimary)
            setTypeface(Typeface.DEFAULT, Typeface.BOLD)
            gravity = Gravity.CENTER
            includeFontPadding = false
            isSingleLine = true
        }, LinearLayout.LayoutParams(MATCH, WRAP))
        val status = LinearLayout(c).apply { gravity = Gravity.CENTER }
        val icon = c.dp(18)
        status.addView(DeviceStatus(c, false), LinearLayout.LayoutParams(icon, icon))
        status.addView(WifiStatus(c), LinearLayout.LayoutParams(icon, icon).apply { leftMargin = c.dp(6) })
        bar.addView(status, LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = c.dp(6) })

        val items = DockConfig.read(p)
        val shown = DockConfig.sideShortcuts(items)
        val apps = items.indexOfFirst { it.action == DockAction.APPS }
        val width = c.dp(p.getInt(Keys.DOCK, 70)) - c.dp(12)
        val gap = c.dp(10)
        val appsSlot = c.dp(52)
        // Header (clock + status) and the Apps button are fixed; three tiles share what is left.
        val free = height - c.dp(20) - c.dp(30) - c.dp(24) - appsSlot - c.dp(16)
        val tile = minOf(width, free / DockConfig.SIDE_SHORTCUTS - gap).coerceAtLeast(c.dp(24))
        bar.addView(View(c), LinearLayout.LayoutParams(1, 0, 1f))
        val shortcuts = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL }
        for (index in shown) {
            val item = items[index]
            // Each slot includes its share of the gap so dragged tiles move by exactly one slot.
            val slot = android.widget.FrameLayout(c).apply { tag = index; contentDescription = DockConfig.name(c, item) }
            slot.addView(tile(c, t, item, tile), android.widget.FrameLayout.LayoutParams(tile, tile, Gravity.CENTER))
            slot.setOnClickListener { click(index, item) }
            shortcuts.addView(slot, LinearLayout.LayoutParams(MATCH, tile + gap))
        }
        bar.addView(shortcuts, LinearLayout.LayoutParams(MATCH, WRAP))
        bar.addView(View(c), LinearLayout.LayoutParams(1, 0, 1f))
        if (apps >= 0) {
            val item = items[apps]
            val button = android.widget.FrameLayout(c).apply {
                tag = apps; contentDescription = DockConfig.name(c, item)
                setOnClickListener { click(apps, item) }
            }
            val glyph = minOf(c.dp(30), tile * 3 / 5)
            button.addView(NavGlyph(c, "apps_filled", t.textPrimary), android.widget.FrameLayout.LayoutParams(glyph, glyph, Gravity.CENTER))
            bar.addView(button, LinearLayout.LayoutParams(MATCH, appsSlot))
        }
        return SideDock(bar, shortcuts)
    }

    /** Side-dock shortcut: a rounded-square tile in the theme's tint holding the glyph (or the app's own icon). */
    private fun tile(c: Context, t: ThemeTokens, item: DockConfig.Item, size: Int): View {
        val box = android.widget.FrameLayout(c).apply {
            background = GradientDrawable().apply {
                cornerRadius = size * .26f
                setColor((t.textPrimary and 0x00ffffff) or 0x1c000000)
                setStroke(1, (t.textPrimary and 0x00ffffff) or 0x14000000)
            }
        }
        // CarPlay / Android Auto, as a built-in action or as the linked projection app, use the projection marks.
        val app = if (item.action == DockAction.APP) item.target.substringBefore('/') else ""
        val projection = when {
            item.action == DockAction.CARPLAY || app in KnownApps.CARPLAY -> R.drawable.ic_carplay_mark
            item.action == DockAction.ANDROID_AUTO || app in KnownApps.ANDROID_AUTO -> R.drawable.ic_android_auto_mark
            else -> 0
        }
        val icon: View = when {
            projection != 0 -> ImageView(c).apply { setImageResource(projection); setColorFilter(t.textPrimary); scaleType = ImageView.ScaleType.FIT_CENTER }
            item.action == DockAction.APP -> ImageView(c).apply { setImageDrawable(AppIcons.get(c, item.target)) }
            else -> NavGlyph(c, item.action.glyph, t.textPrimary)
        }
        val inner = if (item.action == DockAction.APP && projection == 0) size * 7 / 10 else size * 11 / 20
        box.addView(icon, android.widget.FrameLayout.LayoutParams(inner, inner, Gravity.CENTER))
        return box
    }

    fun dock(c: Context, p: SharedPreferences, t: ThemeTokens, click: (Int, DockConfig.Item) -> Unit): LinearLayout {
        val radius = c.dp(14).toFloat()
        val nav = LinearLayout(c).apply {
            gravity = Gravity.CENTER_VERTICAL
            background = if (p.glassCards) HomeGlassDrawable(t.card, t.cardBorder, radius) else GlassDrawable(t.card, radius, t.cardBorder)
        }
        val items = DockConfig.read(p)
        val size = c.dp(DockConfig.size(c, items.count { it.visible }, DockConfig.iconCeiling(p.getInt(Keys.DOCK_ICONS, 24))))
        val labels = p.getBoolean(Keys.DOCK_LABELS, true)
        items.forEachIndexed { index, item ->
            if (!item.visible) return@forEachIndexed
            nav.addView(cell(c, p, t, index, item, size, labels, click), LinearLayout.LayoutParams(0, MATCH, 1f))
        }
        return nav
    }

    /** One dock button: icon, and the name under it when [labels]; tagged with its index in the stored item list. */
    private fun cell(c: Context, p: SharedPreferences, t: ThemeTokens, index: Int, item: DockConfig.Item, size: Int, labels: Boolean,
                     click: (Int, DockConfig.Item) -> Unit): LinearLayout {
        // Installed icons in their own colours; the old "dock_mono" preference is no longer read (kept, not renamed).
        val textSize = 14 * p.textScale
        val name = DockConfig.name(c, item)
        val cell = LinearLayout(c).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            tag = index
            contentDescription = name
        }
        val icon = when (item.action) {
            DockAction.APP -> ImageView(c).apply { setImageDrawable(AppIcons.get(c, item.target)) }
            // Same marks as the Home widget's projection buttons, in the dock's icon colour.
            DockAction.CARPLAY, DockAction.ANDROID_AUTO -> ImageView(c).apply {
                setImageResource(if (item.action == DockAction.CARPLAY) R.drawable.ic_carplay_mark else R.drawable.ic_android_auto_mark)
                setColorFilter(t.textPrimary); scaleType = ImageView.ScaleType.FIT_CENTER
            }
            else -> NavGlyph(c, item.action.glyph, t.textPrimary)
        }
        cell.addView(icon, LinearLayout.LayoutParams(size, size))
        cell.addView(TextView(c).apply {
            text = name
            this.textSize = textSize
            setTextColor(t.textPrimary)
            gravity = Gravity.CENTER
            includeFontPadding = false
            isSingleLine = true
            ellipsize = TextUtils.TruncateAt.END
            visibility = if (labels) View.VISIBLE else View.GONE
        }, LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = c.dp(4) })
        cell.setOnClickListener { click(index, item) }
        return cell
    }
}
