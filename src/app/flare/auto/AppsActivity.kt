package app.flare.auto

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.MotionEvent
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import org.json.JSONObject
import java.util.Locale
import kotlin.math.abs

class AppsActivity : Activity() {
    /** Dialogs take focus; restore full screen when it comes back. */
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) window.immersive()
    }

    private lateinit var page: LinearLayout
    private var settingsSnapshot: Map<String, *> = emptyMap<String, Any>()
    @Volatile private var loadGeneration = 0
    private lateinit var grid: LinearLayout
    private lateinit var gridContainer: FrameLayout
    private var outgoingGrid: LinearLayout? = null
    private var initialPage = 0
    private var pageDirection = 0
    private var settlingPage = false
    private var startX = 0f
    private var startY = 0f
    private var pagingAnimations = false
    private lateinit var pageIndicator: TextView
    private lateinit var theme: ThemeTokens
    private var loaded = false
    private var activePack: IconPacks.Pack? = null
    private var pendingImagePackage: String? = null
    private var pageNo = 0
    private var entries = ArrayList<Entry>()
    private val iconCatalog by lazy { loadIconCatalog() }

    /** A drawer tile. Icon fields are resolved when the list loads, never while drawing a page. */
    class Entry(var label: String, var icon: String, val pkg: String?, val cls: String?, val color: Int) {
        var autoIcon = icon
        val baseIcon = icon
        var internal = false
        /** Drawable for "carplay", "androidauto", "si_*" icons (0 when the icon is not a resource). */
        var iconRes = 0
        /** Driver imported an image for this app. */
        var imported = false
        /** Tint chosen with the icon, or null for the theme colour. */
        var tint: Int? = null
    }

    override fun attachBaseContext(base: Context) { super.attachBaseContext(UiScale.wrap(base, Keys.UI_SCALE_DRAWER)) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pendingImagePackage = savedInstanceState?.getString(STATE_PENDING_IMAGE)
        window.setBackgroundDrawable(ColorDrawable(ThemeTokens.get(this).background))
        theme = ThemeTokens.get(this)
        window.immersive()
        page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(26), dp(14), dp(26), dp(20))
        }
        page.background = WallpaperPack.drawer(this, theme)
        setContentView(page)
        page.addView(UiKit(this).header("Apps"), LinearLayout.LayoutParams(MATCH, dp(56)))
        gridContainer = FrameLayout(this).apply { clipChildren = true }
        page.addView(gridContainer, LinearLayout.LayoutParams(MATCH, 0, 1f))
        grid = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        gridContainer.addView(grid, FrameLayout.LayoutParams(MATCH, MATCH))
        pageIndicator = label("", 12).apply { setTextColor(theme.textSecondary) }
        page.addView(pageIndicator, LinearLayout.LayoutParams(MATCH, dp(22)))
        reloadApps()
    }

    private fun label(value: String, size: Int): TextView = TextView(this).apply {
        text = value
        setTextColor(theme.textPrimary)
        textSize = size.toFloat()
        gravity = Gravity.CENTER
    }

    private fun reloadApps() {
        finishPaging()
        loaded = false
        val generation = ++loadGeneration
        settingsSnapshot = HashMap(ProfileStore.preferences(this).all)
        Background.load(this, { loadApps(generation) }) { result -> if (result != null) deliver(generation, result) }
    }

    /** Off the main thread. Returns null when a newer load has started. */
    private fun loadApps(generation: Int): Pair<ArrayList<Entry>, IconPacks.Pack?>? {
        val loadedEntries = ArrayList<Entry>()
        for (shortcut in KnownApps.DRAWER_SHORTCUTS) {
            if (!shortcut.internal && !AppLookup.installed(this, shortcut.packageName!!, shortcut.className)) continue
            loadedEntries.add(Entry(shortcut.label, shortcut.glyph, shortcut.packageName, shortcut.className, color(shortcut.glyph))
                .apply { internal = shortcut.internal })
        }
        val catalog = iconCatalog
        for (app in AppCatalog.others(this)) {
            if (generation != loadGeneration) return null
            val packageName = app.packageName
            if (KnownApps.hiddenFromDrawer(packageName) || loadedEntries.any { packageName == it.pkg }) continue
            val style = KnownApps.drawerStyle(app.label, packageName)
            var label = style.label
            val icon = KnownApps.BRAND_ICONS[packageName]
                ?: if (style.glyph == "original") findCatalogIcon(catalog, label, packageName) ?: "original" else style.glyph
            if (loadedEntries.any { it.label.equals(label, true) }) label = "$label ($packageName)"
            loadedEntries.add(Entry(label, icon, packageName, app.className, color(style.color)))
        }
        val saved = ProfileStore.preferences(this)
        val pack = IconPacks.load(this)
        applyPack(saved, loadedEntries, pack)
        val hidden = saved.getStringSet(Keys.HIDDEN_APPS, emptySet()) ?: emptySet()
        loadedEntries.removeAll { !it.internal && hidden.contains(it.pkg) }
        return loadedEntries to pack
    }

    private fun deliver(generation: Int, result: Pair<ArrayList<Entry>, IconPacks.Pack?>) {
        if (generation != loadGeneration) return
        entries = result.first
        activePack = result.second
        loaded = true
        val current = ProfileStore.preferences(this).all
        if (appListChanged(settingsSnapshot, current)) {
            reloadApps()
            return
        }
        settingsSnapshot = current
        retheme()
        render()
    }

    private fun retheme() {
        theme = ThemeTokens.get(this)
        UiKit(this).recolor(page)
        page.background = WallpaperPack.drawer(this, theme)
        pageIndicator.setTextColor(theme.textSecondary)
    }

    private fun resolveIcon(entry: Entry) {
        entry.iconRes = when {
            entry.icon == "carplay" -> R.drawable.ic_carplay_mark
            entry.icon == "androidauto" -> R.drawable.ic_android_auto_mark
            entry.icon.startsWith("si_") -> drawableId(entry.icon)
            else -> 0
        }
    }

    private fun drawableId(name: String) = resources.getIdentifier(name, "drawable", packageName)

    private fun readIconPrefs(saved: SharedPreferences, entry: Entry) {
        val pkg = entry.pkg ?: return
        entry.imported = saved.getBoolean(Keys.ICON_IMAGE_PREFIX + pkg, false)
        entry.tint = if (saved.contains(Keys.ICON_COLOR_PREFIX + pkg)) saved.getInt(Keys.ICON_COLOR_PREFIX + pkg, 0) else null
    }

    private fun applyPack(saved: SharedPreferences, items: List<Entry>, pack: IconPacks.Pack?) {
        for (entry in items) {
            entry.autoIcon = entry.baseIcon
            val pkg = entry.pkg
            val protectedIcon = entry.internal || pkg == null || entry.baseIcon == "carplay" || entry.baseIcon == "androidauto"
            if (!protectedIcon && !pkg.startsWith(KnownApps.VENDOR_PREFIX) && pack != null) {
                val match = pack.lookup(this, pkg, entry.cls)
                if (match != null && pack.drawableId(match) != 0) entry.autoIcon = "pack:$match"
            }
            entry.icon = entry.autoIcon
            if (!protectedIcon) {
                val override = saved.getString(Keys.ICON_OVERRIDE_PREFIX + pkg, "") ?: ""
                if (override.startsWith("si_") && drawableId(override) != 0) entry.icon = override
            }
            resolveIcon(entry)
            readIconPrefs(saved, entry)
        }
    }

    override fun onResume() {
        super.onResume()
        if (!loaded) return
        val current = ProfileStore.preferences(this).all
        if (appListChanged(settingsSnapshot, current)) reloadApps()
        else if (settingsSnapshot != current) {
            finishPaging()
            settingsSnapshot = current
            val saved = ProfileStore.preferences(this)
            entries.forEach { readIconPrefs(saved, it) }
            retheme()
            render()
        }
    }

    private fun appListChanged(before: Map<String, *>, after: Map<String, *>): Boolean {
        fun relevant(key: String) = key == Keys.ICON_PACK || key == Keys.HIDDEN_APPS || key.startsWith(Keys.ICON_OVERRIDE_PREFIX)
        return before.keys.any { relevant(it) && before[it] != after[it] } || after.keys.any { relevant(it) && before[it] != after[it] }
    }

    private fun finishPaging() {
        val old = outgoingGrid ?: return
        old.animate().cancel()
        grid.animate().cancel()
        if (settlingPage && pageNo != initialPage) gridContainer.removeView(old)
        else {
            gridContainer.removeView(grid)
            grid = old
            pageNo = initialPage
        }
        grid.translationX = 0f
        grid.alpha = 1f
        outgoingGrid = null
        settlingPage = false
        updatePageIndicator()
    }

    override fun onPause() {
        finishPaging()
        super.onPause()
    }

    override fun onDestroy() {
        loadGeneration++
        super.onDestroy()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString(STATE_PENDING_IMAGE, pendingImagePackage)
        super.onSaveInstanceState(outState)
    }

    private fun columns() = ProfileStore.preferences(this).getInt(Keys.COLUMNS, 5).coerceIn(4, 6)

    /** Two rows per page. */
    private fun pageCount(pageSize: Int = columns() * ROWS): Int = maxOf(1, (entries.size + pageSize - 1) / pageSize)

    private fun updatePageIndicator(pages: Int = pageCount()) {
        pageIndicator.text = (0 until pages).joinToString(" ") { if (it == pageNo) "●" else "○" }
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (!loaded) return super.dispatchTouchEvent(event)
        if (settlingPage) return true
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                startX = event.x
                startY = event.y
                pagingAnimations = Motion.enabled(this)
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.x - startX
                if (outgoingGrid == null && pagingAnimations && startY >= gridContainer.top && startY < gridContainer.bottom &&
                    abs(dx) > dp(12) && abs(dx) > abs(event.y - startY) * 1.3f) {
                    val direction = if (dx < 0) 1 else -1
                    val target = pageNo + direction
                    if (target in 0 until pageCount()) {
                        cancelChildTouch(event)
                        initialPage = pageNo
                        pageDirection = direction
                        outgoingGrid = grid
                        grid = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
                        gridContainer.addView(grid, FrameLayout.LayoutParams(MATCH, MATCH))
                        pageNo = target
                        render()
                        pageNo = initialPage
                        updatePageIndicator()
                    }
                }
                outgoingGrid?.let { old ->
                    val distance = (dx * pageDirection).coerceIn(-gridContainer.width.toFloat(), 0f) * pageDirection
                    old.translationX = distance
                    grid.translationX = distance + pageDirection * gridContainer.width
                    val fraction = (abs(distance) / gridContainer.width.coerceAtLeast(1)).coerceIn(0f, 1f)
                    old.alpha = 1f - .35f * fraction
                    grid.alpha = .65f + .35f * fraction
                    return true
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                val old = outgoingGrid
                if (old != null) {
                    settle(old, event)
                    return true
                }
                if (event.actionMasked == MotionEvent.ACTION_UP && abs(event.x - startX) > dp(70)) {
                    val target = (pageNo + if (event.x < startX) 1 else -1).coerceIn(0, pageCount() - 1)
                    cancelChildTouch(event)
                    if (target != pageNo) { pageNo = target; render() }
                    return true
                }
            }
        }
        return super.dispatchTouchEvent(event)
    }

    private fun cancelChildTouch(event: MotionEvent) {
        val cancel = MotionEvent.obtain(event).apply { action = MotionEvent.ACTION_CANCEL }
        super.dispatchTouchEvent(cancel)
        cancel.recycle()
    }

    private fun settle(old: LinearLayout, event: MotionEvent) {
        val distance = (event.x - startX) * pageDirection
        val commit = event.actionMasked == MotionEvent.ACTION_UP && distance < -minOf(dp(70), gridContainer.width / 4)
        val incoming = grid
        val width = gridContainer.width.toFloat()
        pageNo = if (commit) initialPage + pageDirection else initialPage
        updatePageIndicator()
        settlingPage = true
        old.animate().translationX(if (commit) -pageDirection * width else 0f).alpha(if (commit) .65f else 1f).setDuration(340)
            .setInterpolator(DecelerateInterpolator()).start()
        incoming.animate().translationX(if (commit) 0f else pageDirection * width).alpha(if (commit) 1f else .65f).setDuration(340)
            .setInterpolator(DecelerateInterpolator()).withEndAction {
                if (commit) gridContainer.removeView(old) else {
                    gridContainer.removeView(incoming)
                    grid = old
                    old.translationX = 0f
                    old.alpha = 1f
                }
                outgoingGrid = null
                settlingPage = false
            }.start()
    }

    private fun render() {
        grid.removeAllViews()
        val columns = columns()
        val pageSize = columns * ROWS
        val pages = pageCount(pageSize)
        pageNo = pageNo.coerceIn(0, pages - 1)
        updatePageIndicator(pages)
        val tileSize = dp(78)
        val iconSize = dp(37)
        val labelHeight = dp(42)
        val labelGap = dp(4)
        val corner = dp(21).toFloat()
        for (y in 0 until ROWS) {
            val row = LinearLayout(this)
            grid.addView(row, LinearLayout.LayoutParams(MATCH, 0, 1f))
            for (x in 0 until columns) {
                val index = pageNo * pageSize + y * columns + x
                val cell = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER
                }
                row.addView(cell, LinearLayout.LayoutParams(0, MATCH, 1f))
                if (index >= entries.size) continue
                val entry = entries[index]
                val square = FrameLayout(this)
                square.background = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(entry.color, shade(entry.color)))
                    .apply { cornerRadius = corner }
                addIcon(square, entry, FrameLayout.LayoutParams(iconSize, iconSize, Gravity.CENTER))
                cell.addView(square, LinearLayout.LayoutParams(tileSize, tileSize))
                cell.addView(label(entry.label, 15), LinearLayout.LayoutParams(MATCH, labelHeight).apply { topMargin = labelGap })
                cell.contentDescription = entry.label
                cell.setOnClickListener { open(entry) }
                if (!entry.internal && entry.pkg != null && entry.icon != "carplay" && entry.icon != "androidauto") {
                    cell.setOnLongClickListener { iconMenu(entry); true }
                }
            }
        }
    }

    private fun addIcon(square: FrameLayout, entry: Entry, iconParams: FrameLayout.LayoutParams) {
        val pkg = entry.pkg
        val imported = if (pkg != null && entry.imported) CustomImages.load(this, CustomImages.iconRole(pkg)) else null
        val packed = entry.icon.takeIf { it.startsWith("pack:") }?.substring(5)?.let { name ->
            activePack?.let { pack -> AsyncImages.load(this) { pack.drawable(name) } }
        }
        when {
            entry.internal -> {
                square.background = null
                square.addView(ImageView(this).apply { setImageResource(R.drawable.ic_launcher) }, FrameLayout.LayoutParams(MATCH, MATCH, Gravity.CENTER))
            }
            imported != null || packed != null -> square.addView(ImageView(this).apply { setImageDrawable(imported ?: packed) }, iconParams)
            entry.icon == "carplay" || entry.icon == "androidauto" || entry.icon.startsWith("si_") -> {
                val projection = entry.icon == "carplay" || entry.icon == "androidauto"
                square.addView(ImageView(this).apply {
                    setImageResource(entry.iconRes)
                    setColorFilter(if (projection) theme.textPrimary else entry.tint ?: theme.textPrimary)
                }, iconParams)
            }
            entry.icon == "original" -> square.addView(label(entry.label.take(1).ifEmpty { "?" }.uppercase(Locale.getDefault()), 24).apply {
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            }, iconParams)
            else -> square.addView(NavGlyph(this, entry.icon, theme.textPrimary), iconParams)
        }
    }

    private fun iconMenu(entry: Entry) {
        val pkg = entry.pkg ?: return
        ThemedDialogs.Builder(this)
            .setTitle(entry.label)
            .setItems(arrayOf("Choose from Simple Icons", "Choose image", "Use automatic icon")) { _, choice ->
                when (choice) {
                    0 -> startActivityForResult(Intent(this, IconPickerActivity::class.java).putExtra(IconPickerActivity.EXTRA_PACKAGE, pkg), REQUEST_ICON)
                    1 -> {
                        pendingImagePackage = pkg
                        CustomImages.choose(this)
                    }
                    else -> {
                        ProfileStore.preferences(this).edit().remove(Keys.ICON_OVERRIDE_PREFIX + pkg)
                            .remove(Keys.ICON_COLOR_PREFIX + pkg).remove(Keys.ICON_IMAGE_PREFIX + pkg).apply()
                        entries.filter { it.pkg == pkg }.forEach {
                            it.icon = it.autoIcon
                            it.imported = false
                            it.tint = null
                            resolveIcon(it)
                        }
                        render()
                    }
                }
            }.show()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        val uri = data?.data
        val pending = pendingImagePackage
        if (requestCode == CustomImages.PICK && resultCode == RESULT_OK && uri != null && pending != null) {
            CustomImages.importImage(this, uri, CustomImages.iconRole(pending), Runnable {
                ProfileStore.preferences(this).edit().putBoolean(Keys.ICON_IMAGE_PREFIX + pending, true).apply()
                entries.filter { it.pkg == pending }.forEach { it.imported = true }
                pendingImagePackage = null
                render()
            })
            return
        }
        if (requestCode == REQUEST_ICON && resultCode == RESULT_OK && data != null) {
            val packageName = data.getStringExtra(IconPickerActivity.EXTRA_PACKAGE)
            val icon = data.getStringExtra(IconPickerActivity.EXTRA_ICON)
            if (packageName != null && icon != null) {
                val saved = ProfileStore.preferences(this)
                entries.filter { it.pkg == packageName }.forEach {
                    it.icon = icon
                    resolveIcon(it)
                    readIconPrefs(saved, it)
                }
                render()
            }
        }
    }

    private fun loadIconCatalog(): JSONObject = try {
        JSONObject(assets.open("simple-icons-index.json").bufferedReader(Charsets.UTF_8).use { it.readText() })
    } catch (_: Exception) {
        JSONObject()
    }

    /** Simple Icons match by app label, then by a distinctive package-name segment. */
    private fun findCatalogIcon(catalog: JSONObject, label: String, packageName: String): String? {
        catalog.optString(normalise(label), "").takeIf { it.isNotEmpty() }?.let { return it }
        for (part in packageName.lowercase(Locale.ROOT).split(".")) {
            if (part.length < 5 || part == "android" || part == "google") continue
            catalog.optString(normalise(part), "").takeIf { it.isNotEmpty() }?.let { return it }
        }
        return null
    }

    private fun normalise(value: String) = value.lowercase(Locale.ROOT).replace(NON_ALPHANUMERIC, "")

    private fun shade(color: Int): Int =
        Color.rgb((Color.red(color) * .65).toInt(), (Color.green(color) * .65).toInt(), (Color.blue(color) * .65).toInt())

    private fun open(entry: Entry) {
        if (entry.internal) {
            startActivity(Intent(this, SettingsActivity::class.java))
            return
        }
        val pkg = entry.pkg ?: return
        val intent = if (entry.cls != null) Intent(Intent.ACTION_MAIN).setClassName(pkg, entry.cls) else packageManager.getLaunchIntentForPackage(pkg)
        if (!AppLookup.start(this, intent)) Toast.makeText(this, "${entry.label} is not installed on this device", Toast.LENGTH_SHORT).show()
    }

    companion object {
        private const val ROWS = 2
        private const val REQUEST_ICON = 413
        private const val STATE_PENDING_IMAGE = "pendingIconImage"
        private val NON_ALPHANUMERIC = Regex("[^a-z0-9]")
        private val COLORS = hashMapOf(
            "carplay" to 0xffa64350.toInt(), "androidauto" to 0xff3b7e70.toInt(),
            "audio" to 0xff7c5e91.toInt(), "radio" to 0xffa26443.toInt(), "bluetooth" to 0xff456d92.toInt(),
            "file" to 0xff957640.toInt(), "browser" to 0xff78609b.toInt(), "settings" to 0xff5f636b.toInt(),
            "carsettings" to 0xff655c63.toInt(), "default" to 0xff52545b.toInt(), "wifi" to 0xff467c78.toInt(),
            "custom" to 0xff986079.toInt(), "camera" to 0xff527b74.toInt(), "keyboard" to 0xff526b79.toInt(),
            "ipod" to 0xff886c89.toInt(), "playpause" to 0xff9c555b.toInt(), "sim" to 0xff71804c.toInt(),
            "terminal" to 0xff52616c.toInt(), "guide" to 0xff90754f.toInt()
        )

        fun color(key: String): Int = COLORS[key] ?: COLORS.getValue("default")
    }
}
