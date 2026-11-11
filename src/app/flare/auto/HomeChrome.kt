package app.flare.auto

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
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

    fun dock(c: Context, p: SharedPreferences, t: ThemeTokens, click: (Int, DockConfig.Item) -> Unit): LinearLayout {
        val radius = c.dp(14).toFloat()
        val nav = LinearLayout(c).apply {
            gravity = Gravity.CENTER_VERTICAL
            background = if (p.glassCards) HomeGlassDrawable(t.card, t.cardBorder, radius) else GlassDrawable(t.card, radius, t.cardBorder)
        }
        val items = DockConfig.read(p)
        val size = c.dp(DockConfig.size(c, items.count { it.visible }, DockConfig.iconCeiling(p.getInt(Keys.DOCK_ICONS, 24))))
        val labels = p.getBoolean(Keys.DOCK_LABELS, true)
        val mono = if (p.getBoolean(Keys.DOCK_MONO, false)) ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(0f) }) else null
        val textSize = 14 * p.textScale
        items.forEachIndexed { index, item ->
            if (!item.visible) return@forEachIndexed
            val name = DockConfig.name(c, item)
            val cell = LinearLayout(c).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                tag = index
                contentDescription = name
            }
            val icon = if (item.action == DockAction.APP) ImageView(c).apply {
                setImageDrawable(AppIcons.get(c, item.target))
                if (mono != null) colorFilter = mono
            } else NavGlyph(c, item.action.glyph, t.textPrimary)
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
            nav.addView(cell, LinearLayout.LayoutParams(0, MATCH, 1f))
        }
        return nav
    }
}
