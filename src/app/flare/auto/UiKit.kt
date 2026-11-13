package app.flare.auto

import android.app.Activity
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView

/** Builders for the settings-style screens. Text views are tagged with a [Role] so [recolor] can re-theme them. */
internal class UiKit(@JvmField val a: Activity) {
    @JvmField var t = ThemeTokens.get(a)

    enum class Role { PRIMARY, SECONDARY, ACCENT, GROUP, DIVIDER }

    fun dp(value: Int): Int = a.dp(value)

    fun text(value: String, size: Int, color: Int): TextView =
        TextView(a).apply {
            text = value
            textSize = size.toFloat()
            setTextColor(color)
            tag = when (color) {
                t.accent -> Role.ACCENT
                t.textSecondary -> Role.SECONDARY
                else -> Role.PRIMARY
            }
            gravity = Gravity.CENTER_VERTICAL
        }

    fun column(): LinearLayout = LinearLayout(a).apply { orientation = LinearLayout.VERTICAL }

    /**
     * Screen header: a round back button (drawn chevron in a 44 dp circle, vertically centred on the title), the
     * bold title and, unless [home] is false, a Home button on the right. [inset] is the back button's distance
     * from the row's left edge; the app drawer passes 0 so the button lines up with the page's content edge.
     */
    fun header(title: String, home: Boolean = true, inset: Int = dp(10)): LinearLayout {
        val row = LinearLayout(a).apply { gravity = Gravity.CENTER_VERTICAL }
        val back = android.widget.ImageView(a).apply {
            setImageDrawable(MapIcon("back", t.textPrimary))
            contentDescription = "Back"
            // The chevron's visual centre sits left of its box centre; shift it right by 1 dp.
            setPadding(dp(12), dp(11), dp(10), dp(11))
            background = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.OVAL; setColor(t.card); setStroke(dp(1), t.cardBorder)
            }
            setOnClickListener { a.finish() }
        }
        row.addView(back, LinearLayout.LayoutParams(dp(44), dp(44)).apply { leftMargin = inset; rightMargin = dp(14) })
        val label = text(title, 20, t.textPrimary).apply {
            setTypeface(Typeface.DEFAULT, Typeface.BOLD)
            letterSpacing = 0.015f
        }
        row.addView(label, LinearLayout.LayoutParams(0, dp(56), 1f))
        if (!home) return row
        val homeButton = text("⌂", 25, t.textPrimary).apply {
            gravity = Gravity.CENTER
            contentDescription = "Home"
            setOnClickListener {
                a.startActivity(Intent(a, HomeActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP))
                a.finish()
            }
        }
        row.addView(homeButton, LinearLayout.LayoutParams(dp(56), dp(56)))
        return row
    }

    fun section(value: String): TextView = text(value, 13, t.accent).apply {
        setTypeface(Typeface.DEFAULT, Typeface.BOLD)
        letterSpacing = 0.055f
        setPadding(dp(8), dp(18), dp(8), dp(9))
    }

    fun group(): LinearLayout = column().apply {
        background = GlassDrawable(t.card, dp(12).toFloat(), t.cardBorder)
        tag = Role.GROUP
    }

    fun divider(): View = View(a).apply {
        setBackgroundColor(t.cardBorder)
        tag = Role.DIVIDER
    }

    /** Adds [row] to a settings [group], with a divider after the first row. */
    fun add(group: LinearLayout, row: View) {
        if (group.childCount > 0) {
            group.addView(divider(), LinearLayout.LayoutParams(MATCH, dp(1)).apply {
                leftMargin = dp(16)
                rightMargin = dp(16)
            })
        }
        group.addView(row, LinearLayout.LayoutParams(MATCH, WRAP))
    }

    /** Title on the left, [ValueRow.value] text on the right. */
    inner class ValueRow(title: String, value: String, action: Runnable?) : LinearLayout(a) {
        val title: TextView = text(title, 16, t.textPrimary).apply { maxLines = 2; setPadding(0, dp(5), dp(12), dp(5)) }
        val value: TextView = text(value, 14, t.textSecondary).apply {
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
            maxLines = 2
        }

        init {
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(56)
            setPadding(dp(16), 0, dp(16), 0)
            addView(this.title, LayoutParams(0, WRAP, 1f))
            addView(this.value, LayoutParams(WRAP, WRAP))
            if (action != null) setOnClickListener { action.run() }
        }

        /** Updates the value text only when it changed (avoids relayout on refresh). */
        fun setValue(text: CharSequence) { if (value.text.toString() != text.toString()) value.text = text }
    }

    fun valueRow(title: String, value: String, action: Runnable?): ValueRow = ValueRow(title, value, action)

    /** Title with an on/off switch; the whole row toggles. */
    inner class ToggleRow(title: String, checked: Boolean, action: Runnable) : LinearLayout(a) {
        val title: TextView = text(title, 16, t.textPrimary).apply { maxLines = 2; setPadding(0, dp(5), dp(12), dp(5)) }
        val switch = Switch(a).apply {
            isChecked = checked
            isClickable = false
            isFocusable = false
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            thumbTintList = ColorStateList.valueOf(Color.WHITE)
            trackTintList = ColorStateList(arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()), intArrayOf(t.accent, t.textSecondary))
            contentDescription = title
        }

        init {
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(56)
            setPadding(dp(16), 0, dp(16), 0)
            addView(this.title, LayoutParams(0, WRAP, 1f))
            addView(switch, LayoutParams(dp(58), dp(48)))
            isFocusable = true
            setOnClickListener { action.run() }
        }

        /** Sets the switch; without animation when the driver turned animations off. */
        fun setChecked(checked: Boolean, label: String) {
            switch.isChecked = checked
            if (!Motion.enabled(a)) switch.jumpDrawablesToCurrentState()
            contentDescription = "$label, ${if (checked) "on" else "off"}"
        }
    }

    fun toggle(title: String, value: Boolean, action: Runnable): ToggleRow = ToggleRow(title, value, action)

    /** Title with − value + buttons. */
    inner class StepperRow(title: String, value: String, minus: Runnable, plus: Runnable) : LinearLayout(a) {
        val value: TextView

        init {
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(56)
            setPadding(dp(16), 0, dp(16), 0)
            addView(text(title, 16, t.textPrimary).apply { maxLines = 2; setPadding(0, dp(5), dp(12), dp(5)) }, LayoutParams(0, WRAP, 1f))
            addView(text("", 14, t.textSecondary).apply { gravity = Gravity.END or Gravity.CENTER_VERTICAL; maxLines = 2 }, LayoutParams(WRAP, WRAP))
            addView(text("−", 24, t.textPrimary).apply {
                gravity = Gravity.CENTER
                contentDescription = "Decrease $title"
                setOnClickListener { minus.run() }
            }, LayoutParams(dp(56), dp(56)))
            this.value = text(value, 14, t.textSecondary).apply { gravity = Gravity.CENTER }
            addView(this.value, LayoutParams(dp(44), dp(56)))
            addView(text("+", 24, t.textPrimary).apply {
                gravity = Gravity.CENTER
                contentDescription = "Increase $title"
                setOnClickListener { plus.run() }
            }, LayoutParams(dp(56), dp(56)))
        }
    }

    fun stepper(title: String, value: String, minus: Runnable, plus: Runnable): StepperRow = StepperRow(title, value, minus, plus)

    fun recolor(root: View) {
        t = ThemeTokens.get(a)
        a.window.setBackgroundDrawable(ColorDrawable(t.background))
        root.setBackgroundColor(t.background)
        recolorTree(root)
    }

    fun recolorTree(view: View) {
        when (view.tag) {
            Role.GROUP -> view.background = GlassDrawable(t.card, dp(12).toFloat(), t.cardBorder)
            Role.DIVIDER -> view.setBackgroundColor(t.cardBorder)
            Role.PRIMARY -> if (view is TextView) view.setTextColor(t.textPrimary)
            Role.SECONDARY -> if (view is TextView) view.setTextColor(t.textSecondary)
            Role.ACCENT -> if (view is TextView) view.setTextColor(t.accent)
        }
        if (view is ViewGroup) for (index in 0 until view.childCount) recolorTree(view.getChildAt(index))
    }
}
