package app.flare.auto

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import org.json.JSONArray
import org.json.JSONException

class CustomizeActivity : Activity() {
    private lateinit var ui: UiKit
    private lateinit var preferences: SharedPreferences
    private lateinit var previewContext: android.content.Context
    private var root: LinearLayout? = null
    private var body: LinearLayout? = null
    private var preview: LinearLayout? = null
    private var row: LinearLayout? = null
    private var leftBox: FrameLayout? = null
    private var rightBox: FrameLayout? = null
    private lateinit var hint: TextView
    private lateinit var appsButton: TextView
    private var dockPreview: LinearLayout? = null
    private var renderedSettings: Map<String, *>? = null
    private var dockChoosing = -1
    private var split = 58
    private val hintText = StringBuilder(32)
    private var left = "clock"
    private var right = "media"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.decorView.systemUiVisibility = UiFlags.IMMERSIVE
        ui = UiKit(this)
        window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(ui.t.background))
        preferences = getSharedPreferences("home", 0)
        previewContext = UiScale.wrap(this, "ui_scale_home")
        left = HomeActivity.card(preferences, true)
        right = HomeActivity.card(preferences, false)
        split = preferences.getInt("split", 58).coerceIn(35, 65)
        dockChoosing = savedInstanceState?.getInt("dockChoosing", -1) ?: -1
        render()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt("dockChoosing", dockChoosing)
        super.onSaveInstanceState(outState)
    }

    private fun render() {
        if (root == null) {
            val newRoot = ui.column().apply { setBackgroundColor(ui.t.background) }
            newRoot.addView(ui.header("Home layout"), LinearLayout.LayoutParams(-1, ui.dp(56)))
            body = ui.column()
            newRoot.addView(body, LinearLayout.LayoutParams(-1, 0, 1f))
            setContentView(newRoot)
            root = newRoot
            val canvasHost = FrameLayout(this).apply { clipChildren = true }
            body?.addView(canvasHost, LinearLayout.LayoutParams(-1, 0, 1f))
            val screenWidth = resources.displayMetrics.widthPixels
            val screenHeight = resources.displayMetrics.heightPixels
            fun homeDp(n: Int) = (n * previewContext.resources.displayMetrics.density + .5f).toInt()
            val screen = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                pivotX = 0f; pivotY = 0f
                val space = homeDp(preferences.getInt("spacing", 14))
                setPadding(space, homeDp(10), space, homeDp(10))
                background = WallpaperPack.home(this@CustomizeActivity, ui.t)
            }
            canvasHost.addView(screen, FrameLayout.LayoutParams(screenWidth, screenHeight))
            canvasHost.addOnLayoutChangeListener { view, _, _, _, _, _, _, _, _ ->
                if (view.width > 0 && view.height > 0) {
                    val scale = minOf(view.width.toFloat() / screenWidth, view.height.toFloat() / screenHeight)
                    screen.scaleX = scale; screen.scaleY = scale
                    screen.translationX = (view.width - screenWidth * scale) / 2f
                    screen.translationY = (view.height - screenHeight * scale) / 2f
                }
            }
            val top = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
            val date = java.text.SimpleDateFormat("EEE, MMM d", java.util.Locale.getDefault()).format(java.util.Date())
            top.addView(ui.text(date, 19, ui.t.textPrimary), LinearLayout.LayoutParams(0, homeDp(40), 1f))
            top.addView(DeviceStatus(this, false), LinearLayout.LayoutParams(homeDp(24), homeDp(24)))
            top.addView(WifiStatus(this), LinearLayout.LayoutParams(homeDp(24), homeDp(24)).apply { leftMargin = homeDp(14) })
            top.addView(DeviceStatus(this, true), LinearLayout.LayoutParams(homeDp(24), homeDp(24)).apply { leftMargin = homeDp(14) })
            screen.addView(top)
            screen.addView(View(this).apply { setBackgroundColor(ui.t.accent) }, LinearLayout.LayoutParams(-1, homeDp(1)))
            preview = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            screen.addView(preview, LinearLayout.LayoutParams(-1, 0, 1f).apply { topMargin = homeDp(12); bottomMargin = homeDp(12) })
            dockPreview = LinearLayout(this).apply {
                gravity = Gravity.CENTER
                background = GlassDrawable(ui.t.card, homeDp(14).toFloat(), ui.t.cardBorder)
            }
            screen.addView(dockPreview, LinearLayout.LayoutParams(-1, homeDp(preferences.getInt("dock", 70))))
            hint = ui.text("", 13, ui.t.textSecondary).apply { gravity = Gravity.CENTER; minHeight = ui.dp(32) }
            body?.addView(hint)
            row = LinearLayout(this).apply { gravity = Gravity.CENTER }
            body?.addView(row, LinearLayout.LayoutParams(-1, ui.dp(56)))
            button("Top left") { chooseWidget(true) }
            button("Top right") { chooseWidget(false) }
            button("Vehicle & clock") { chooseVehicle() }
            button("Arrange dock") { arrangeDock() }
            button("Reset") {
                ThemedDialogs.Builder(this)
                    .setTitle("Reset layout?")
                    .setPositiveButton("Reset") { _, _ ->
                        preferences.edit().remove("layout_left").remove("layout_right")
                            .remove("split").remove("projection").remove("media").apply()
                        left = "clock"; right = "media"; split = 58; render()
                    }
                    .setNegativeButton("Cancel", null).show()
            }
            appsButton = ui.text("Manage applications", 16, ui.t.accent).apply {
                gravity = Gravity.CENTER; minHeight = ui.dp(56); setOnClickListener { editApps() }
            }
            body?.addView(appsButton)
        }
        val content = preview ?: return
        content.removeAllViews()
        leftBox = cardBox(left, true)
        content.addView(leftBox, LinearLayout.LayoutParams(0, -1, split.toFloat()))
        val divider = ui.text("⋮", 22, ui.t.textSecondary).apply { gravity = Gravity.CENTER }
        content.addView(divider, LinearLayout.LayoutParams((12 * previewContext.resources.displayMetrics.density + .5f).toInt(), -1))
        divider.setOnTouchListener { view, event -> drag(view, event) }
        rightBox = cardBox(right, false)
        content.addView(rightBox, LinearLayout.LayoutParams(0, -1, (100 - split).toFloat()))
        appsButton.visibility = if (left == "apps" || right == "apps") View.VISIBLE else View.GONE
        updateHint()
        renderDock()
        renderedSettings = preferences.all
    }

    override fun onResume() {
        super.onResume()
        if (root != null && renderedSettings != preferences.all) render()
    }

    private fun cardBox(type: String, isLeft: Boolean): FrameLayout {
        val frame = FrameLayout(this)
        if (type == "hidden") frame.addView(ui.text("Hold to choose a card", 14, ui.t.textSecondary).apply { gravity = Gravity.CENTER }, FrameLayout.LayoutParams(-1, -1))
        else {
            val content = LinearLayout(previewContext).apply { orientation = LinearLayout.HORIZONTAL }
            HomeCards(this, preferences, ui.t, preview = true, context = previewContext).build(content, type)
            frame.addView(content, FrameLayout.LayoutParams(-1, -1))
        }
        val overlay = View(this).apply {
            contentDescription = (if (isLeft) "Left" else "Right") + " card, hold to change"
            setOnLongClickListener { choose(isLeft); true }
            setOnClickListener { Toast.makeText(this@CustomizeActivity, "Hold to change this card", Toast.LENGTH_SHORT).show() }
        }
        frame.addView(overlay, FrameLayout.LayoutParams(-1, -1))
        return frame
    }

    private fun renderDock() {
        val bar = dockPreview ?: return
        bar.removeAllViews()
        val items = DockConfig.read(preferences)
        items.forEachIndexed { index, item ->
            if (!item.visible) return@forEachIndexed
            val cell = ui.column().apply { gravity = Gravity.CENTER; contentDescription = DockConfig.name(this@CustomizeActivity, item) }
            if (item.type < 5) cell.addView(HomeActivity.NavIcon(this, DockConfig.ICONS[item.type], ui.t.textPrimary), LinearLayout.LayoutParams(ui.dp(22), ui.dp(22)))
            else cell.addView(ui.text("◉", 20, ui.t.textPrimary).apply { gravity = Gravity.CENTER }, LinearLayout.LayoutParams(ui.dp(22), ui.dp(22)))
            cell.addView(ui.text(DockConfig.name(this, item), 10, ui.t.textSecondary).apply { gravity = Gravity.CENTER; maxLines = 1 })
            cell.setOnClickListener { editDockItem(index) }
            bar.addView(cell, LinearLayout.LayoutParams(0, -1, 1f))
        }
    }

    private fun editDockItem(index: Int) {
        val items = DockConfig.read(preferences)
        if (index !in items.indices) return
        if (items[index].type == 4) arrangeDock() else chooseDockAction(index)
    }

    private fun arrangeDock() {
        val items = DockConfig.read(preferences)
        if (items.isEmpty()) return
        val names = items.mapIndexed { index, item -> "${index + 1}. ${DockConfig.name(this, item)}${if (!item.visible) " (hidden)" else ""}" }.toTypedArray()
        ThemedDialogs.Builder(this).setTitle("Arrange dock: choose shortcut").setItems(names) { _, from ->
            val positions = items.indices.map { index -> "Position ${index + 1}${if (index == from) " (current)" else ""}" }.toTypedArray()
            ThemedDialogs.Builder(this).setTitle("Move ${DockConfig.name(this, items[from])} to").setItems(positions) { _, to ->
                val item = items.removeAt(from)
                items.add(to, item)
                DockConfig.save(preferences, items)
                renderDock()
            }.show()
        }.show()
    }

    private fun chooseDockAction(index: Int) {
        val labels = arrayOf("Audio", "Radio", "Bluetooth", "Screen off", "Choose app")
        ThemedDialogs.Builder(this).setTitle("Change shortcut").setItems(labels) { _, choice ->
            if (choice == 4) {
                dockChoosing = index
                startActivityForResult(Intent(this, AppPickerActivity::class.java).putExtra(AppPickerActivity.ROLE, "dock"), 2)
            } else {
                val items = DockConfig.read(preferences)
                if (index in items.indices) { items[index] = DockConfig.Item(choice, "", true); DockConfig.save(preferences, items); renderDock() }
            }
        }.show()
    }

    private fun chooseWidget(isLeft: Boolean) {
        val key = if (isLeft) "widget_left" else "widget_right"
        val ids = arrayOf("auto", "weather", "sun", "trim", "off")
        val labels = arrayOf("Automatic weather / sun", "Weather (requires forecast enabled)", "Sunrise / sunset", "Trim badge", "Off")
        val current = ids.indexOf(preferences.getString(key, if (isLeft) "auto" else "trim")).coerceAtLeast(0)
        ThemedDialogs.Builder(this).setTitle(if (isLeft) "Top-left widget" else "Top-right widget").setSingleChoiceItems(labels, current) { _, selected ->
            preferences.edit().putString(key, ids[selected]).apply()
            render()
        }.show()
    }

    private fun chooseVehicle() {
        val options = arrayOf("Vehicle name", "Vehicle name color", "Trim badge text", "Trim badge color", "Home clock style")
        ThemedDialogs.Builder(this).setTitle("Vehicle & clock").setItems(options) { _, choice ->
            when (choice) {
                0 -> editText("Vehicle name", "brand_text", "CIVIC")
                1 -> chooseColor("Vehicle name color", "brand_color")
                2 -> editText("Trim badge text", "badge_text", "RS")
                3 -> chooseColor("Trim badge color", "badge_color")
                else -> chooseClock()
            }
        }.show()
    }

    private fun editText(title: String, key: String, fallback: String) {
        val input = android.widget.EditText(this).apply { setSingleLine(); setText(preferences.getString(key, fallback)) }
        ThemedDialogs.Builder(this).setTitle(title).setMessage("Leave empty to hide").setView(input)
            .setPositiveButton("Save") { _, _ -> preferences.edit().putString(key, input.text.toString().trim()).apply(); render() }
            .setNegativeButton("Cancel", null).show()
    }

    private fun chooseColor(title: String, key: String) {
        val labels = arrayOf("Theme accent", "Red", "White", "Silver", "Orange", "Blue", "Green", "Custom color")
        val colors = intArrayOf(0, 0xffe60012.toInt(), -1, 0xffb9c1ca.toInt(), 0xffffa040.toInt(), 0xff82bdff.toInt(), 0xff75d9aa.toInt())
        ThemedDialogs.Builder(this).setTitle(title).setItems(labels) { _, index ->
            if (index == labels.lastIndex) {
                val input = android.widget.EditText(this).apply { hint = "#RRGGBB"; setSingleLine() }
                ThemedDialogs.Builder(this).setTitle(title).setView(input).setPositiveButton("Save") { _, _ ->
                    try { val raw = input.text.toString().trim(); require(raw.matches(Regex("#[0-9a-fA-F]{6}"))); preferences.edit().putInt(key, android.graphics.Color.parseColor(raw)).apply(); render() }
                    catch (_: IllegalArgumentException) { Toast.makeText(this, "Enter a color like #E60012", Toast.LENGTH_LONG).show() }
                }.setNegativeButton("Cancel", null).show()
            } else {
                if (index == 0) preferences.edit().remove(key).apply() else preferences.edit().putInt(key, colors[index]).apply()
                render()
            }
        }.show()
    }

    private fun chooseClock() {
        val current = ClockStyles.ids.indexOf(preferences.getString("home_clock_style", "classic")).coerceAtLeast(0)
        ThemedDialogs.Builder(this).setTitle("Home clock style").setSingleChoiceItems(ClockStyles.labels, current) { _, choice ->
            preferences.edit().putString("home_clock_style", ClockStyles.ids[choice]).apply(); render()
        }.show()
    }

    private fun drag(view: View, event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                preview?.requestDisallowInterceptTouchEvent(true)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val content = preview ?: return true
                val left = leftBox ?: return true
                val right = rightBox ?: return true
                val width = content.width - content.paddingLeft - content.paddingRight - view.width
                if (width > 0) {
                    val next = ((view.left + event.x - content.paddingLeft - view.width / 2f) * 100 / width)
                        .toInt().coerceIn(35, 65)
                    if (next != split) {
                        split = next
                        (left.layoutParams as LinearLayout.LayoutParams).weight = split.toFloat()
                        (right.layoutParams as LinearLayout.LayoutParams).weight = (100 - split).toFloat()
                        left.requestLayout()
                        right.requestLayout()
                        updateHint()
                    }
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                preferences.edit().putInt("split", split).apply()
                preview?.requestDisallowInterceptTouchEvent(false)
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                split = preferences.getInt("split", 58).coerceIn(35, 65)
                val left = leftBox
                val right = rightBox
                if (left != null && right != null) {
                    (left.layoutParams as LinearLayout.LayoutParams).weight = split.toFloat()
                    (right.layoutParams as LinearLayout.LayoutParams).weight = (100 - split).toFloat()
                    left.requestLayout()
                    right.requestLayout()
                    updateHint()
                }
                preview?.requestDisallowInterceptTouchEvent(false)
                return true
            }
            else -> return true
        }
    }

    private fun updateHint() {
        hintText.setLength(0)
        if (leftBox != null && rightBox != null) {
            hintText.append("Hold a card • Tap dock to edit • Drag divider   ").append(split).append("% / ").append(100 - split).append('%')
        } else {
            hintText.append("Choose a card for each side")
        }
        hint.text = hintText
    }

    private fun button(label: String, action: () -> Unit) {
        val button = ui.text(label, 14, ui.t.textPrimary).apply {
            gravity = Gravity.CENTER
            minimumHeight = ui.dp(56)
        }
        row?.addView(button, LinearLayout.LayoutParams(0, ui.dp(56), 1f))
        button.setOnClickListener { action() }
    }

    private fun choose(isLeft: Boolean) {
        ThemedDialogs.Builder(this)
            .setTitle(if (isLeft) "Left card" else "Right card")
            .setItems(LABELS) { _, index ->
                val value = TYPES[index]
                if (value != "hidden" && value != "apps" && value == if (isLeft) right else left) {
                    val oldLeft = left
                    left = right
                    right = oldLeft
                } else if (isLeft) left = value else right = value
                save()
            }
            .show()
    }

    private fun save() {
        preferences.edit().putString("layout_left", left).putString("layout_right", right).apply()
        render()
    }

    private fun editApps() {
        try {
            val apps = JSONArray(preferences.getString("layout_apps", "[]"))
            val options = Array(apps.length() + 1) { "" }
            options[0] = if (apps.length() < 8) "Add app" else "8 apps maximum"
            for (index in 0 until apps.length()) {
                val target = apps.getString(index)
                options[index + 1] = try {
                    val component = ComponentName.unflattenFromString(target)!!
                    "Remove " + packageManager.getActivityInfo(component, 0).loadLabel(packageManager)
                } catch (_: Exception) {
                    "Remove unavailable app"
                }
            }
            ThemedDialogs.Builder(this)
                .setTitle("App shortcuts")
                .setItems(options) { _, index ->
                    if (index == 0) {
                        if (apps.length() < 8) {
                            startActivityForResult(
                                Intent(this, AppPickerActivity::class.java).putExtra(AppPickerActivity.ROLE, "layout"),
                                1
                            )
                        }
                    } else {
                        val next = JSONArray()
                        for (item in 0 until apps.length()) if (item != index - 1) next.put(apps.optString(item))
                        preferences.edit().putString("layout_apps", next.toString()).apply()
                        render()
                    }
                }
                .show()
        } catch (_: JSONException) {
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK || data == null) return
        if (requestCode == 2) {
            val target = data.getStringExtra(AppPickerActivity.TARGET) ?: return
            val items = DockConfig.read(preferences)
            if (dockChoosing in items.indices) { items[dockChoosing] = DockConfig.Item(5, target, true); DockConfig.save(preferences, items); renderDock() }
            return
        }
        if (requestCode != 1) return
        val target = data.getStringExtra(AppPickerActivity.TARGET)
        if (target.isNullOrEmpty()) return
        try {
            val apps = JSONArray(preferences.getString("layout_apps", "[]"))
            if (apps.length() < 8) {
                apps.put(target)
                preferences.edit().putString("layout_apps", apps.toString()).apply()
                render()
            }
        } catch (_: JSONException) {
        }
    }

    companion object {
        private val TYPES = arrayOf("clock", "media", "apps", "hidden")
        private val LABELS = arrayOf("Clock", "Media", "Applications", "Hidden")
    }
}
