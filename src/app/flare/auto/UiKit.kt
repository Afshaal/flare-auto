package app.flare.auto

import android.app.Activity
import android.content.Intent
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView

internal class UiKit(@JvmField val a: Activity) {
    @JvmField var t = ThemeTokens.get(a)

    enum class Role {
        PRIMARY, SECONDARY, ACCENT, GROUP, DIVIDER
    }

    fun dp(value: Int): Int = (value * a.resources.displayMetrics.density + .5f).toInt()

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

    fun header(title: String): LinearLayout {
        val row = LinearLayout(a).apply { gravity = Gravity.CENTER_VERTICAL }
        val back = text("‹", 28, t.textPrimary).apply {
            gravity = Gravity.CENTER
            contentDescription = "Back"
        }
        row.addView(back, LinearLayout.LayoutParams(dp(56), dp(56)))
        back.setOnClickListener { a.finish() }

        val label = text(title, 20, t.textPrimary).apply {
            setTypeface(Typeface.DEFAULT, Typeface.BOLD)
            letterSpacing = 0.015f
        }
        row.addView(label, LinearLayout.LayoutParams(0, dp(56), 1f))

        val home = text("⌂", 25, t.textPrimary).apply {
            gravity = Gravity.CENTER
            contentDescription = "Home"
        }
        row.addView(home, LinearLayout.LayoutParams(dp(56), dp(56)))
        home.setOnClickListener {
            val intent = Intent(a, HomeActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
            a.startActivity(intent)
            a.finish()
        }
        return row
    }

    fun section(value: String): TextView = text(value, 13, t.accent).apply {
        setTypeface(Typeface.DEFAULT, Typeface.BOLD)
        letterSpacing = 0.055f
        setPadding(dp(8), dp(18), dp(8), dp(9))
    }

    fun group(): LinearLayout = column().apply {
        setBackground(GlassDrawable(t.card, dp(12).toFloat(), t.cardBorder))
        tag = Role.GROUP
    }

    fun divider(): View = View(a).apply {
        setBackgroundColor(t.cardBorder)
        tag = Role.DIVIDER
    }

    fun add(group: LinearLayout, row: View) {
        if (group.childCount > 0) {
            val params = LinearLayout.LayoutParams(-1, dp(1)).apply {
                leftMargin = dp(16)
                rightMargin = dp(16)
            }
            group.addView(divider(), params)
        }
        group.addView(row, LinearLayout.LayoutParams(-1, -2))
    }

    fun valueRow(title: String, value: String, action: Runnable?): LinearLayout {
        val row = LinearLayout(a).apply {
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(56)
            setPadding(dp(16), 0, dp(16), 0)
        }
        val left = text(title, 16, t.textPrimary).apply { maxLines = 2; setPadding(0, dp(5), dp(12), dp(5)) }
        row.addView(left, LinearLayout.LayoutParams(0, -2, 1f))
        val right = text(value, 14, t.textSecondary).apply {
            gravity = Gravity.RIGHT or Gravity.CENTER_VERTICAL
            maxLines = 2
        }
        row.addView(right, LinearLayout.LayoutParams(-2, -2))
        if (action != null) row.setOnClickListener { action.run() }
        return row
    }

    fun recolor(root: View) {
        t = ThemeTokens.get(a)
        a.window.setBackgroundDrawable(ColorDrawable(t.background))
        root.setBackgroundColor(t.background)
        recolorTree(root)
    }

    fun recolorTree(view: View) {
        when (view.tag) {
            Role.GROUP -> view.setBackground(GlassDrawable(t.card, dp(12).toFloat(), t.cardBorder))
            Role.DIVIDER -> view.setBackgroundColor(t.cardBorder)
            Role.PRIMARY -> if (view is TextView) view.setTextColor(t.textPrimary)
            Role.SECONDARY -> if (view is TextView) view.setTextColor(t.textSecondary)
            Role.ACCENT -> if (view is TextView) view.setTextColor(t.accent)
        }
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) recolorTree(view.getChildAt(index))
        }
    }

    fun toggle(title: String, value: Boolean, action: Runnable): LinearLayout {
        val row = valueRow(title, "", null)
        row.removeViewAt(1)
        val control = android.widget.Switch(a).apply {
            isChecked = value
            isClickable = false
            isFocusable = false
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            thumbTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.WHITE)
            trackTintList = android.content.res.ColorStateList(
                arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
                intArrayOf(t.accent, t.textSecondary)
            )
            contentDescription = title
        }
        row.addView(control, LinearLayout.LayoutParams(dp(58), dp(48)))
        row.isFocusable = true
        row.setOnClickListener { action.run() }
        return row
    }

    fun stepper(title: String, value: String, minus: Runnable, plus: Runnable): LinearLayout {
        val row = valueRow(title, "", null)
        val negative = text("−", 24, t.textPrimary).apply {
            gravity = Gravity.CENTER
            contentDescription = "Decrease $title"
        }
        row.addView(negative, LinearLayout.LayoutParams(dp(56), dp(56)))
        negative.setOnClickListener { minus.run() }
        val valueView = text(value, 14, t.textSecondary).apply { gravity = Gravity.CENTER }
        row.addView(valueView, LinearLayout.LayoutParams(dp(44), dp(56)))
        val positive = text("+", 24, t.textPrimary).apply {
            gravity = Gravity.CENTER
            contentDescription = "Increase $title"
        }
        row.addView(positive, LinearLayout.LayoutParams(dp(56), dp(56)))
        positive.setOnClickListener { plus.run() }
        return row
    }
}
