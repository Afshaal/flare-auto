package app.flare.auto

import android.app.Activity
import android.app.Dialog
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.abs

/**
 * Full-screen picker for the Home editor's "Add widget" and "Layouts" buttons, laid out like an app drawer:
 * category tabs on the left and a fixed grid of cards on the right (live preview, name, action chips). A category
 * with more cards than one grid page is paged (swipe or the arrows) instead of scrolled, so every card keeps its
 * full size on a landscape head unit. Sizes are proportional to the screen, not fixed.
 */
internal class EditorDrawer(private val a: Activity, private val ui: UiKit, title: String) {
    /**
     * A tab of cards. [variants]: optional horizontal style tabs above the cards (e.g. clock styles); [onVariant] is
     * told the chosen one and the cards are rebuilt (they read it when built).
     */
    class Category(val name: String, val cards: List<() -> View>, val variants: List<String> = emptyList(),
                   var variant: Int = 0, val onVariant: ((Int) -> Unit)? = null)
    /** One action on a card; [primary] chips are filled with the accent colour. */
    class Chip(val label: String, val enabled: Boolean = true, val primary: Boolean = false, val run: () -> Unit)

    private val t = ui.t
    private val dialog = Dialog(a, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
    private val root = LinearLayout(a).apply {
        orientation = LinearLayout.VERTICAL
        setBackgroundColor(t.background)
        setPadding(ui.dp(20), ui.dp(12), ui.dp(20), ui.dp(14))
    }
    private val tabs = ui.column()
    private val grid = ui.column()
    private val variantBar = LinearLayout(a).apply { gravity = Gravity.CENTER_VERTICAL }
    private val headerEnd = LinearLayout(a).apply { gravity = Gravity.CENTER_VERTICAL }
    private val pager = LinearLayout(a).apply { gravity = Gravity.CENTER }
    private var categories: List<Category> = emptyList()
    private var category = 0
    private var page = 0
    private var columns = 2
    private var rows = 2

    init {
        val header = LinearLayout(a).apply { gravity = Gravity.CENTER_VERTICAL }
        header.addView(EditorIcon(a, "back", t.textPrimary).apply {
            contentDescription = "Close"
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(t.card); setStroke(ui.dp(1), t.cardBorder) }
            val pad = ui.dp(12); setPadding(pad, pad, pad, pad)
            setOnClickListener { dismiss() }
        }, LinearLayout.LayoutParams(ui.dp(48), ui.dp(48)))
        header.addView(ui.text(title, 22, t.textPrimary).apply { typeface = android.graphics.Typeface.DEFAULT_BOLD; setPadding(ui.dp(16), 0, 0, 0) },
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        header.addView(headerEnd, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT))
        root.addView(header, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ui.dp(56)))
        val body = LinearLayout(a)
        val screen = a.resources.displayMetrics
        val tabWidth = minOf(ui.dp(180), (screen.widthPixels * 0.2f).toInt())
        body.addView(tabs, LinearLayout.LayoutParams(tabWidth, ViewGroup.LayoutParams.MATCH_PARENT).apply { rightMargin = ui.dp(16) })
        val content = ui.column()
        content.addView(variantBar, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ui.dp(44)).apply { bottomMargin = ui.dp(8) })
        content.addView(grid, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        content.addView(pager, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ui.dp(36)))
        body.addView(content, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f))
        root.addView(body, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f).apply { topMargin = ui.dp(10) })
        dialog.setContentView(root)
        swipeToPage()
    }

    fun show(categories: List<Category>, initial: Int, columns: Int, rows: Int) {
        this.categories = categories; this.columns = columns; this.rows = rows
        category = initial.coerceIn(0, maxOf(0, categories.lastIndex)); page = 0
        renderTabs(); renderPage()
        dialog.show()
        dialog.window?.let { w ->
            w.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT)
            w.immersive()
        }
        if (Motion.enabled(a)) { root.alpha = 0f; root.translationY = ui.dp(16).toFloat(); root.animate().alpha(1f).translationY(0f).setDuration(220).start() }
    }

    fun dismiss() = dialog.dismiss()

    /** Rebuilds the visible cards (after something they show changed, e.g. the placement target). */
    fun refresh() { if (dialog.isShowing) renderPage() }

    /** A control at the right of the title bar (e.g. where a widget is added). */
    fun headerAction(view: View) { headerEnd.removeAllViews(); headerEnd.addView(view) }

    /** Pill tabs in one row; [chosen] is selected. [pick] gets the tapped index. */
    fun segmented(labels: List<String>, chosen: Int, pick: (Int) -> Unit): LinearLayout {
        val row = LinearLayout(a).apply { gravity = Gravity.CENTER_VERTICAL }
        labels.forEachIndexed { i, label ->
            val on = i == chosen
            row.addView(ui.text(label, 14, if (on) 0xffffffff.toInt() else t.textPrimary).apply {
                gravity = Gravity.CENTER; isSingleLine = true
                setPadding(ui.dp(16), 0, ui.dp(16), 0)
                background = GradientDrawable().apply {
                    cornerRadius = ui.dp(18).toFloat()
                    if (on) setColor(t.accent) else { setColor(t.card); setStroke(ui.dp(1), t.cardBorder) }
                }
                contentDescription = if (on) "$label, selected" else label
                isSelected = on
                setOnClickListener { if (!on) pick(i) }
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ui.dp(36)).apply { marginEnd = ui.dp(8) })
        }
        return row
    }

    /**
     * Card: preview on top (tapping it runs the first enabled chip), name, an optional [note] (e.g. why no size fits),
     * then the chips in one row.
     */
    fun card(preview: View, name: String, chips: List<Chip>, note: String? = null): View {
        val card = ui.column().apply {
            background = GradientDrawable().apply { setColor(t.card); cornerRadius = ui.dp(18).toFloat(); setStroke(ui.dp(1), t.cardBorder) }
            setPadding(ui.dp(12), ui.dp(12), ui.dp(12), ui.dp(10))
        }
        // The preview is a picture: a cover takes its touches (no media buttons or map gestures in the drawer).
        val frame = FrameLayout(a).apply { clipToOutline = true; background = GradientDrawable().apply { setColor(t.background); cornerRadius = ui.dp(12).toFloat() } }
        frame.addView(preview, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        frame.addView(View(a).apply {
            contentDescription = name
            setOnClickListener { chips.firstOrNull { it.enabled }?.run?.invoke() }
        }, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        card.addView(frame, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        card.addView(ui.text(name, 15, t.textPrimary).apply { maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END; setPadding(ui.dp(2), ui.dp(8), 0, if (note == null) ui.dp(6) else 0) })
        if (note != null) card.addView(ui.text(note, 12, t.textSecondary).apply { maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END; setPadding(ui.dp(2), ui.dp(2), 0, ui.dp(6)) })
        val row = LinearLayout(a).apply { gravity = Gravity.CENTER_VERTICAL }
        chips.forEach { chip ->
            row.addView(ui.text(chip.label, 13, if (chip.primary) 0xffffffff.toInt() else t.textPrimary).apply {
                gravity = Gravity.CENTER; isSingleLine = true; ellipsize = android.text.TextUtils.TruncateAt.END
                setPadding(ui.dp(6), 0, ui.dp(6), 0)
                background = GradientDrawable().apply {
                    cornerRadius = ui.dp(16).toFloat()
                    if (chip.primary) setColor(t.accent) else { setColor(t.background); setStroke(ui.dp(1), t.cardBorder) }
                }
                isEnabled = chip.enabled; alpha = if (chip.enabled) 1f else .35f
                contentDescription = "${chip.label}, $name"
                setOnClickListener { chip.run() }
            }, LinearLayout.LayoutParams(0, ui.dp(34), 1f).apply { marginEnd = ui.dp(6) })
        }
        card.addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        return card
    }

    private fun renderTabs() {
        tabs.removeAllViews()
        categories.forEachIndexed { i, c ->
            val on = i == category
            tabs.addView(ui.text(c.name, 15, if (on) t.textPrimary else t.textSecondary).apply {
                gravity = Gravity.CENTER_VERTICAL; maxLines = 1
                setPadding(ui.dp(16), 0, ui.dp(8), 0)
                if (on) typeface = android.graphics.Typeface.DEFAULT_BOLD
                background = GradientDrawable().apply {
                    cornerRadius = ui.dp(20).toFloat()
                    if (on) { setColor((t.accent and 0x00ffffff) or 0x33000000); setStroke(ui.dp(1), t.accent) } else setColor(0)
                }
                contentDescription = "${c.name}, ${c.cards.size} ${if (c.cards.size == 1) "item" else "items"}"
                setOnClickListener { if (category != i) { category = i; page = 0; renderTabs(); renderPage() } }
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ui.dp(40)).apply { bottomMargin = ui.dp(4) })
        }
    }

    private fun pages(): Int {
        val n = categories.getOrNull(category)?.cards?.size ?: 0
        return maxOf(1, (n + columns * rows - 1) / (columns * rows))
    }

    private fun renderVariants() {
        variantBar.removeAllViews()
        val c = categories.getOrNull(category)
        variantBar.visibility = if (c == null || c.variants.isEmpty()) View.GONE else View.VISIBLE
        if (c == null || c.variants.isEmpty()) return
        variantBar.addView(segmented(c.variants, c.variant) { i -> c.variant = i; c.onVariant?.invoke(i); renderPage() })
    }

    private fun renderPage() {
        renderVariants()
        grid.removeAllViews()
        val cards = categories.getOrNull(category)?.cards ?: emptyList()
        val per = columns * rows
        page = page.coerceIn(0, pages() - 1)
        for (r in 0 until rows) {
            val line = LinearLayout(a)
            for (c in 0 until columns) {
                val i = page * per + r * columns + c
                val cell = cards.getOrNull(i)?.invoke() ?: View(a)
                line.addView(cell, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f).apply {
                    if (c < columns - 1) marginEnd = ui.dp(14)
                })
            }
            grid.addView(line, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f).apply { if (r < rows - 1) bottomMargin = ui.dp(14) })
        }
        renderPager()
    }

    private fun renderPager() {
        pager.removeAllViews()
        val count = pages()
        pager.visibility = if (count < 2) View.GONE else View.VISIBLE
        if (count < 2) return
        fun arrow(kind: String, label: String, enabled: Boolean, step: Int) = EditorIcon(a, kind, t.textPrimary).apply {
            contentDescription = label; isEnabled = enabled; alpha = if (enabled) 1f else .3f
            val pad = ui.dp(6); setPadding(pad, pad, pad, pad)
            setOnClickListener { turn(step) }
        }
        pager.addView(arrow("left", "Previous page", page > 0, -1), LinearLayout.LayoutParams(ui.dp(36), ui.dp(36)))
        for (i in 0 until count) pager.addView(View(a).apply {
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(if (i == page) t.accent else t.cardBorder) }
        }, LinearLayout.LayoutParams(ui.dp(8), ui.dp(8)).apply { setMargins(ui.dp(5), 0, ui.dp(5), 0) })
        pager.addView(arrow("right", "Next page", page < count - 1, 1), LinearLayout.LayoutParams(ui.dp(36), ui.dp(36)))
    }

    private fun turn(step: Int) {
        val next = (page + step).coerceIn(0, pages() - 1)
        if (next != page) { page = next; renderPage() }
    }

    /**
     * A horizontal swipe anywhere in the drawer turns pages. Seen at window level so swipes starting on a card work
     * too; a swipe's final touch is consumed, so the card under it is not also tapped.
     */
    private fun swipeToPage() {
        var downX = 0f; var downY = 0f
        val window = dialog.window ?: return
        val base = window.callback
        window.callback = object : android.view.Window.Callback by base {
            override fun dispatchTouchEvent(event: MotionEvent): Boolean {
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> { downX = event.rawX; downY = event.rawY }
                    MotionEvent.ACTION_UP -> {
                        val dx = event.rawX - downX
                        if (pages() > 1 && abs(dx) > ui.dp(60) && abs(dx) > abs(event.rawY - downY) * 1.5f) {
                            event.action = MotionEvent.ACTION_CANCEL; base.dispatchTouchEvent(event)
                            turn(if (dx < 0) 1 else -1); return true
                        }
                    }
                }
                return base.dispatchTouchEvent(event)
            }
        }
    }
}
