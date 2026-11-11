package app.flare.auto

import android.app.Activity
import android.content.Intent
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.LinearLayout
import android.widget.FrameLayout
import android.widget.ListView
import android.widget.TextView

class AppearancePickerActivity : Activity() {
    /** Dialogs take focus; restore full screen when it comes back. */
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) window.immersive()
    }

    private lateinit var ui: UiKit
    private lateinit var mode: String
    private lateinit var names: Array<String>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setBackgroundDrawable(ColorDrawable(ThemeTokens.get(this).background))
        window.immersive()
        ui = UiKit(this)
        mode = intent.getStringExtra("mode") ?: "theme"
        val isTheme = mode == "theme"
        val isSaver = mode == "saver"
        val isDrawer = mode == "drawer"
        names = when {
            isTheme -> ThemeTokens.NAMES
            isDrawer -> arrayOf("Theme background", "Match Home") + WallpaperPack.HOME_NAMES
            isSaver -> WallpaperPack.SAVER_NAMES
            else -> WallpaperPack.HOME_NAMES
        }
        val selected = when {
            isTheme -> ThemeTokens.activeIndex(this)
            isDrawer -> WallpaperPack.drawerSelection(this) + 2
            else -> WallpaperPack.index(this, isSaver)
        }
        val customPosition = if (isTheme) -1 else if (isDrawer) WallpaperPack.CUSTOM_INDEX + 2 else WallpaperPack.CUSTOM_INDEX
        val root = ui.column().apply { setBackgroundColor(ui.t.background) }
        val title = when {
            isTheme -> "Theme"
            isDrawer -> "App drawer wallpaper"
            isSaver -> "Screensaver wallpaper"
            else -> "Home wallpaper"
        }
        root.addView(ui.header(title), LinearLayout.LayoutParams(MATCH, ui.dp(56)))
        val list = ListView(this).apply { divider = null }
        run {
            val current = LinearLayout(this).apply {
                gravity = Gravity.CENTER_VERTICAL
                setPadding(ui.dp(16), ui.dp(6), ui.dp(16), ui.dp(8))
            }
            val swatch = thumbnail()
            swatch.background = when {
                isTheme -> ColorDrawable(ui.t.card)
                isDrawer -> WallpaperPack.drawer(this, ui.t)
                else -> WallpaperPack.preview(this, ui.t, isSaver, selected)
            }
            current.addView(swatch, LinearLayout.LayoutParams(ui.dp(96), ui.dp(54)))
            current.addView(ui.text("Current: ${names[selected]}", 14, ui.t.textSecondary).apply {
                setPadding(ui.dp(16), 0, 0, 0)
            }, LinearLayout.LayoutParams(0, WRAP, 1f))
            root.addView(current, LinearLayout.LayoutParams(MATCH, ui.dp(70)))
        }
        root.addView(list, LinearLayout.LayoutParams(MATCH, 0, 1f))
        setContentView(root)
        list.adapter = object : BaseAdapter() {
            override fun getCount() = names.size
            override fun getItem(position: Int): Any = names[position]
            override fun getItemId(position: Int) = position.toLong()

            override fun getView(position: Int, recycled: View?, parent: ViewGroup): View {
                val row = recycled as? LinearLayout ?: LinearLayout(this@AppearancePickerActivity).apply {
                    setPadding(ui.dp(16), ui.dp(5), ui.dp(16), ui.dp(5))
                    gravity = Gravity.CENTER_VERTICAL
                    minimumHeight = ui.dp(64)
                    addView(
                        thumbnail().apply { id = android.R.id.icon },
                        LinearLayout.LayoutParams(ui.dp(80), ui.dp(48))
                    )
                    val label = ui.text("", 16, ui.t.textPrimary).apply {
                        setPadding(ui.dp(16), 0, 0, 0)
                        id = android.R.id.text1
                    }
                    addView(label, LinearLayout.LayoutParams(0, WRAP, 1f))
                }
                val preview = row.findViewById<Thumbnail>(android.R.id.icon)
                val customChoice = position == customPosition
                preview.custom.visibility = if (customChoice) View.VISIBLE else View.GONE
                when {
                    customChoice -> preview.background = GradientDrawable(
                        GradientDrawable.Orientation.TL_BR,
                        intArrayOf(0xff404752.toInt(), 0xff20242a.toInt())
                    )
                    isTheme -> {
                        val drawable = GradientDrawable().apply {
                            setColor(ThemeTokens.VALUES[position][1])
                            cornerRadius = ui.dp(8).toFloat()
                        }
                        preview.background = drawable
                    }
                    isDrawer -> {
                        val drawable = when (position) {
                            0 -> ColorDrawable(ui.t.background)
                            1 -> WallpaperPack.home(this@AppearancePickerActivity, ui.t)
                            else -> WallpaperPack.previewRole(this@AppearancePickerActivity, ui.t, "drawer", position - 2)
                        }
                        preview.background = drawable ?: ColorDrawable(ui.t.background)
                    }
                    else -> preview.background =
                        WallpaperPack.preview(this@AppearancePickerActivity, ui.t, isSaver, position)
                }
                row.findViewById<TextView>(android.R.id.text1).text =
                    names[position] + if (position == selected) " ✓" else ""
                return row
            }
        }
        list.setOnItemClickListener { _, _, position, _ ->
            if (position == customPosition) {
                CustomImages.choose(this)
                return@setOnItemClickListener
            }
            when {
                isTheme -> ThemeTokens.select(this, position)
                isDrawer -> WallpaperPack.select(this, "drawer", position - 2)
                else -> WallpaperPack.select(this, if (isSaver) "saver" else "home", position)
            }
            finish()
        }
    }

    /** Wallpaper swatch; [custom] is the "CUSTOM" label shown on the custom-image entry. */
    private inner class Thumbnail : FrameLayout(this@AppearancePickerActivity) {
        val custom = TextView(context).apply {
            text = "CUSTOM"
            gravity = Gravity.CENTER
            setTextColor(android.graphics.Color.WHITE)
            textSize = 10f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            visibility = View.GONE
        }

        init {
            foreground = GradientDrawable().apply {
                setColor(android.graphics.Color.TRANSPARENT)
                cornerRadius = ui.dp(6).toFloat()
                setStroke(ui.dp(1), 0xff858b96.toInt())
            }
            addView(custom, LayoutParams(MATCH, MATCH))
        }
    }

    private fun thumbnail() = Thumbnail()

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != CustomImages.PICK || resultCode != RESULT_OK || data?.data == null) return
        val isDrawer = mode == "drawer"
        val isSaver = mode == "saver"
        val role = if (isDrawer) "drawer" else if (isSaver) "saverwall" else "home"
        CustomImages.importImage(this, data.data!!, role) {
            val preferences = ProfileStore.preferences(this)
            val editor = preferences.edit()
                .putInt(Keys.CUSTOM_REVISION, preferences.getInt(Keys.CUSTOM_REVISION, 0) + 1)
            editor.apply()
            WallpaperPack.select(this, if (isDrawer) "drawer" else if (isSaver) "saver" else "home", WallpaperPack.CUSTOM_INDEX)
            finish()
        }
    }
}
