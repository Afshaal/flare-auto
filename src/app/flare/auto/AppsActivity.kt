package app.flare.auto

import android.app.Activity
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ResolveInfo
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.ArrayList
import java.util.Collections
import java.util.HashMap
import java.util.Locale

class AppsActivity : Activity() {
    private lateinit var page: LinearLayout
    private var settingsSnapshot: Map<String, *> = emptyMap<String, Any>()
    @Volatile private var loadGeneration = 0
    private lateinit var grid: LinearLayout
    private lateinit var gridContainer: FrameLayout
    private var outgoingGrid: LinearLayout? = null
    private var initialPage = 0
    private var pageDirection = 0
    private var settlingPage = false
    private var startY = 0f
    private var pagingAnimations = false
    private lateinit var pageIndicator: TextView
    private lateinit var theme: ThemeTokens
    private var loaded = false
    private var activePack: IconPacks.Pack? = null
    private var pendingImagePackage: String? = null
    private val main = Handler(Looper.getMainLooper())
    private var pageNo = 0
    private var startX = 0f
    private var entries = ArrayList<Entry>()
    private val iconCatalog by lazy { loadIconCatalog() }

    class Entry(
        @JvmField var label: String,
        @JvmField var icon: String,
        @JvmField val pkg: String?,
        @JvmField val cls: String?,
        @JvmField var color: Int
    ) {
        @JvmField var autoIcon = icon
        @JvmField var baseIcon = icon
        @JvmField var internal = false
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density + .5f).toInt()

    private fun text(value: String, size: Int): TextView = TextView(this).apply {
        text = value
        setTextColor(theme.textPrimary)
        textSize = size.toFloat()
        gravity = Gravity.CENTER
    }

    override fun attachBaseContext(base: android.content.Context) { super.attachBaseContext(UiScale.wrap(base, "ui_scale_drawer")) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pendingImagePackage = savedInstanceState?.getString("pendingIconImage")
        window.setBackgroundDrawable(ColorDrawable(ThemeTokens.get(this).background))
        theme = ThemeTokens.get(this)
        window.decorView.systemUiVisibility = UiFlags.IMMERSIVE
        page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(26), dp(14), dp(26), dp(20))
        }
        page.background = WallpaperPack.drawer(this, theme)
        setContentView(page)
        val ui = UiKit(this)
        page.addView(ui.header("Apps"), LinearLayout.LayoutParams(-1, dp(56)))
        gridContainer = FrameLayout(this).apply { clipChildren = true }
        page.addView(gridContainer, LinearLayout.LayoutParams(-1, 0, 1f))
        grid = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        gridContainer.addView(grid, FrameLayout.LayoutParams(-1, -1))
        pageIndicator = text("", 12).apply { setTextColor(theme.textSecondary) }
        page.addView(pageIndicator, LinearLayout.LayoutParams(-1, dp(22)))
        reloadApps()
    }

    private fun reloadApps() {
        finishPaging()
        loaded = false
        val generation = ++loadGeneration
        settingsSnapshot = HashMap(ProfileStore.preferences(this).all)
        Thread { loadApps(generation) }.start()
    }

    private fun loadApps(generation: Int) {
        val loadedEntries = ArrayList<Entry>()
        val catalog = iconCatalog
        loadedEntries.add(Entry("CarPlay", "carplay", "com.shihab.diplay", null, color("carplay")))
        loadedEntries.add(Entry("Android Auto", "androidauto", "com.andrerinas.headunitrevived", null, color("androidauto")))
        loadedEntries.add(Entry("Audio", "audio", "com.bw.mediaplayer", "com.bw.mediaplayer.ui.BtActivity", color("audio")))
        loadedEntries.add(Entry("Radio", "radio", "com.bw.mediaplayer", "com.bw.mediaplayer.ui.RadioActivity", color("radio")))
        loadedEntries.add(Entry("Bluetooth", "bluetooth", "com.bw.phone", "com.bw.phone.MainActivity", color("bluetooth")))
        loadedEntries.add(Entry("Files", "file", "me.zhanghai.android.files", null, color("file")))
        loadedEntries.add(Entry("Browser", "browser", "org.mozilla.focus", null, color("browser")))
        loadedEntries.add(Entry("Launcher Settings", "settings", null, null, color("settings")).apply { internal = true })
        loadedEntries.add(Entry("Car settings", "carsettings", "com.bw.carsetting", null, color("carsettings")))

        val packageManager = packageManager
        val apps = packageManager.queryIntentActivities(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER),
            0
        )
        Collections.sort(apps, ResolveInfo.DisplayNameComparator(packageManager))
        for (resolveInfo in apps) {
            if (generation != loadGeneration) return
            val packageName = resolveInfo.activityInfo.packageName
            if (packageName == this.packageName || packageName == "local.civic.dash" ||
                packageName == "local.civic.btmusic" || packageName == "local.civic.radio"
            ) continue
            if (loadedEntries.any { packageName == it.pkg }) continue

            var label = resolveInfo.loadLabel(packageManager).toString()
            var icon = "original"
            var iconColor = color("default")
            when {
                label.equals("Basic Settings", ignoreCase = true) -> {
                    label = "WiFi Settings"
                    icon = "wifi"
                    iconColor = color("wifi")
                }
                packageName == "com.android.settings" -> {
                    label = "System Settings"
                    icon = "systemsettings"
                }
                label.equals("Customization", ignoreCase = true) -> {
                    icon = "custom"
                    iconColor = color("custom")
                }
                packageName == "local.civic.btmusic" -> {
                    icon = "audio"
                    iconColor = color("audio")
                }
                packageName == "local.civic.radio" -> {
                    icon = "radio"
                    iconColor = color("radio")
                }
                packageName == "com.android.camera2" || packageName == "com.bw.adas" -> {
                    icon = "camera"
                    iconColor = color("camera")
                }
                packageName == "com.android.gallery3d" ||
                    label.equals("Gallery", true) || label.equals("Photos", true) -> {
                    icon = "gallery"
                    iconColor = color("camera")
                }
                packageName == "rkr.simplekeyboard.inputmethod" -> {
                    label = "Simple Keyboard"
                    icon = "keyboard"
                    iconColor = color("keyboard")
                }
                label.equals("Files", true) -> iconColor = color("file")
                packageName == "com.bw.ipod" -> {
                    icon = "ipod"
                    iconColor = color("ipod")
                }
                packageName == "local.civic.button" -> {
                    icon = "playpause"
                    iconColor = color("playpause")
                }
                packageName.startsWith("com.android.stk") -> {
                    icon = "sim"
                    iconColor = color("sim")
                }
                packageName == "com.termux" -> {
                    icon = "terminal"
                    iconColor = color("terminal")
                }
                label.equals("User Guide", true) -> {
                    icon = "guide"
                    iconColor = color("guide")
                }
            }
            val brand = brandIcon(packageName)
            if (brand != null) icon = brand else if (icon == "original") {
                icon = findCatalogIcon(catalog, label, packageName) ?: icon
            }
            if (loadedEntries.any { it.label.equals(label, true) }) label = "$label ($packageName)"
            loadedEntries.add(Entry(label, icon, packageName, resolveInfo.activityInfo.name, iconColor))
        }

        val saved = ProfileStore.preferences(this)
        val pack = IconPacks.load(this)
        applyPack(saved, loadedEntries, pack)
        val hidden = saved.getStringSet("hidden_apps", Collections.emptySet()) ?: emptySet()
        loadedEntries.removeAll { !it.internal && hidden.contains(it.pkg) }
        main.post {
            if (isFinishing || isDestroyed || generation != loadGeneration) return@post
            entries = loadedEntries
            activePack = pack
            loaded = true
            if (appListChanged(settingsSnapshot, ProfileStore.preferences(this).all)) {
                reloadApps()
                return@post
            }
            settingsSnapshot = ProfileStore.preferences(this).all
            theme = ThemeTokens.get(this)
            UiKit(this).recolor(page)
            page.background = WallpaperPack.drawer(this, theme)
            pageIndicator.setTextColor(theme.textSecondary)
            render()
        }
    }

    private fun applyPack(saved: SharedPreferences, items: ArrayList<Entry>, pack: IconPacks.Pack?) {
        for (entry in items) {
            entry.autoIcon = entry.baseIcon
            val protectedIcon = entry.internal || entry.pkg == null ||
                entry.baseIcon == "carplay" || entry.baseIcon == "androidauto"
            if (!protectedIcon && !entry.pkg!!.startsWith("com.bw.") && pack != null) {
                val match = pack.lookup(this, entry.pkg!!, entry.cls)
                if (match != null && pack.drawableId(match) != 0) entry.autoIcon = "pack:$match"
            }
            entry.icon = entry.autoIcon
            if (!protectedIcon) {
                val override = saved.getString("icon_override_${entry.pkg}", "") ?: ""
                if (override.startsWith("si_") && resources.getIdentifier(override, "drawable", packageName) != 0) {
                    entry.icon = override
                }
            }
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
            theme = ThemeTokens.get(this)
            UiKit(this).recolor(page)
            page.background = WallpaperPack.drawer(this, theme)
            pageIndicator.setTextColor(theme.textSecondary)
            render()
        }
    }

    private fun appListChanged(before: Map<String, *>, after: Map<String, *>): Boolean {
        fun relevant(key: String) = key == IconPacks.KEY || key == "hidden_apps" || key.startsWith("icon_override_")
        return before.keys.any { relevant(it) && before[it] != after[it] } ||
            after.keys.any { relevant(it) && before[it] != after[it] }
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
        main.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("pendingIconImage", pendingImagePackage)
        super.onSaveInstanceState(outState)
    }

    private fun pageCount(): Int {
        val columns = ProfileStore.preferences(this).getInt("columns", 5).coerceIn(4, 6)
        return maxOf(1, (entries.size + columns * 2 - 1) / (columns * 2))
    }

    private fun updatePageIndicator() {
        pageIndicator.text = (0 until pageCount()).joinToString(" ") { if (it == pageNo) "●" else "○" }
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
                if (outgoingGrid == null && pagingAnimations &&
                    startY >= gridContainer.top && startY < gridContainer.bottom &&
                    kotlin.math.abs(dx) > dp(12) && kotlin.math.abs(dx) > kotlin.math.abs(event.y - startY) * 1.3f) {
                    val direction = if (dx < 0) 1 else -1
                    val target = pageNo + direction
                    if (target in 0 until pageCount()) {
                        val cancel = MotionEvent.obtain(event).apply { action = MotionEvent.ACTION_CANCEL }
                        super.dispatchTouchEvent(cancel)
                        cancel.recycle()
                        initialPage = pageNo
                        pageDirection = direction
                        outgoingGrid = grid
                        grid = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
                        gridContainer.addView(grid, FrameLayout.LayoutParams(-1, -1))
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
                    val fraction = (kotlin.math.abs(distance) / gridContainer.width.coerceAtLeast(1)).coerceIn(0f, 1f)
                    old.alpha = 1f - .35f * fraction
                    grid.alpha = .65f + .35f * fraction
                    return true
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                val old = outgoingGrid
                if (old != null) {
                    val distance = (event.x - startX) * pageDirection
                    val commit = event.actionMasked == MotionEvent.ACTION_UP && distance < -minOf(dp(70), gridContainer.width / 4)
                    val incoming = grid
                    val width = gridContainer.width.toFloat()
                    pageNo = if (commit) initialPage + pageDirection else initialPage
                    updatePageIndicator()
                    settlingPage = true
                    old.animate().translationX(if (commit) -pageDirection * width else 0f).alpha(if (commit) .65f else 1f).setDuration(340)
                        .setInterpolator(android.view.animation.DecelerateInterpolator()).start()
                    incoming.animate().translationX(if (commit) 0f else pageDirection * width).alpha(if (commit) 1f else .65f).setDuration(340)
                        .setInterpolator(android.view.animation.DecelerateInterpolator()).withEndAction {
                            if (commit) gridContainer.removeView(old) else {
                                gridContainer.removeView(incoming)
                                grid = old
                                old.translationX = 0f
                                old.alpha = 1f
                            }
                            outgoingGrid = null
                            settlingPage = false
                        }.start()
                    return true
                }
                if (event.actionMasked == MotionEvent.ACTION_UP && kotlin.math.abs(event.x - startX) > dp(70)) {
                    val target = (pageNo + if (event.x < startX) 1 else -1).coerceIn(0, pageCount() - 1)
                    val cancel = MotionEvent.obtain(event).apply { action = MotionEvent.ACTION_CANCEL }
                    super.dispatchTouchEvent(cancel)
                    cancel.recycle()
                    if (target != pageNo) { pageNo = target; render() }
                    return true
                }
            }
        }
        return super.dispatchTouchEvent(event)
    }

    private fun render() {
        grid.removeAllViews()
        val preferences = ProfileStore.preferences(this)
        val columns = preferences.getInt("columns", 5).coerceIn(4, 6)
        val pages = maxOf(1, (entries.size + columns * 2 - 1) / (columns * 2))
        pageNo = pageNo.coerceIn(0, pages - 1)
        pageIndicator.text = (0 until pages).joinToString(" ") { if (it == pageNo) "●" else "○" }
        for (y in 0 until 2) {
            val row = LinearLayout(this)
            grid.addView(row, LinearLayout.LayoutParams(-1, 0, 1f))
            for (x in 0 until columns) {
                val index = pageNo * columns * 2 + y * columns + x
                val cell = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER
                }
                row.addView(cell, LinearLayout.LayoutParams(0, -1, 1f))
                if (index >= entries.size) continue
                val entry = entries[index]
                val square = FrameLayout(this)
                val background = GradientDrawable(
                    GradientDrawable.Orientation.TL_BR,
                    intArrayOf(entry.color, shade(entry.color))
                ).apply { cornerRadius = dp(21).toFloat() }
                square.background = background
                val iconParams = FrameLayout.LayoutParams(dp(37), dp(37), Gravity.CENTER)
                val imported = entry.pkg?.let {
                    if (preferences.getBoolean("icon_image_$it", false)) CustomImages.load(this, CustomImages.iconRole(it)) else null
                }
                val packedName = entry.icon.takeIf { it.startsWith("pack:") }?.substring(5)
                val packed = if (packedName != null) activePack?.let { pack ->
                    AsyncImages.load(this) { pack.drawable(packedName) }
                } else null
                when {
                    entry.internal -> {
                        square.background = null
                        val icon = ImageView(this).apply { setImageResource(R.drawable.ic_launcher) }
                        square.addView(icon, FrameLayout.LayoutParams(-1, -1, Gravity.CENTER))
                    }
                    imported != null || packed != null -> {
                        val icon = ImageView(this).apply { setImageDrawable(imported ?: packed) }
                        square.addView(icon, iconParams)
                    }
                    entry.icon == "carplay" || entry.icon == "androidauto" ||
                        entry.icon.startsWith("brand_") || entry.icon.startsWith("si_") -> {
                        val icon = ImageView(this)
                        val resource = when (entry.icon) {
                            "carplay" -> "ic_carplay_mark"
                            "androidauto" -> "ic_android_auto_mark"
                            else -> entry.icon
                        }
                        icon.setImageResource(resources.getIdentifier(resource, "drawable", packageName))
                        val tint = if (entry.icon == "carplay" || entry.icon == "androidauto") {
                            theme.textPrimary
                        } else {
                            preferences.getInt("icon_color_${entry.pkg}", theme.textPrimary)
                        }
                        icon.setColorFilter(tint)
                        square.addView(icon, iconParams)
                    }
                    entry.icon == "original" -> {
                        val monogram = text(entry.label.take(1).ifEmpty { "?" }.uppercase(Locale.getDefault()), 24)
                        monogram.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                        monogram.setTextColor(theme.textPrimary)
                        square.addView(monogram, iconParams)
                    }
                    else -> square.addView(HomeActivity.NavIcon(this, entry.icon, theme.textPrimary), iconParams)
                }
                cell.addView(square, LinearLayout.LayoutParams(dp(78), dp(78)))
                val label = text(entry.label, 15)
                val labelParams = LinearLayout.LayoutParams(-1, dp(42)).apply { topMargin = dp(4) }
                cell.addView(label, labelParams)
                cell.contentDescription = entry.label
                cell.setOnClickListener { open(entry) }
                if (!entry.internal && entry.pkg != null && entry.icon != "carplay" && entry.icon != "androidauto") {
                    cell.setOnLongClickListener {
                        ThemedDialogs.Builder(this)
                            .setTitle(entry.label)
                            .setItems(arrayOf("Choose from Simple Icons", "Choose image", "Use automatic icon")) { _, choice ->
                                when (choice) {
                                    0 -> startActivityForResult(
                                        Intent(this, IconPickerActivity::class.java).putExtra("package", entry.pkg), 413
                                    )
                                    1 -> {
                                        pendingImagePackage = entry.pkg
                                        CustomImages.choose(this)
                                    }
                                    else -> {
                                        preferences.edit().remove("icon_override_${entry.pkg}")
                                            .remove("icon_color_${entry.pkg}").remove("icon_image_${entry.pkg}").apply()
                                        entries.filter { it.pkg == entry.pkg }.forEach { it.icon = it.autoIcon }
                                        render()
                                    }
                                }
                            }.show()
                        true
                    }
                }
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == CustomImages.PICK && resultCode == RESULT_OK && data?.data != null &&
            pendingImagePackage != null
        ) {
            val packageName = pendingImagePackage!!
            CustomImages.importImage(this, data.data!!, CustomImages.iconRole(packageName), Runnable {
                ProfileStore.preferences(this).edit().putBoolean("icon_image_$packageName", true).apply()
                pendingImagePackage = null
                render()
            })
            return
        }
        if (requestCode == 413 && resultCode == RESULT_OK && data != null) {
            val packageName = data.getStringExtra("package")
            val icon = data.getStringExtra("icon")
            if (packageName != null && icon != null) {
                entries.filter { it.pkg == packageName }.forEach { it.icon = icon }
                render()
            }
        }
    }

    private fun loadIconCatalog(): JSONObject {
        return try {
            assets.open("simple-icons-index.json").use { input ->
                ByteArrayOutputStream().use { output ->
                    input.copyTo(output)
                    JSONObject(output.toString("UTF-8"))
                }
            }
        } catch (_: Exception) {
            JSONObject()
        }
    }

    private fun findCatalogIcon(catalog: JSONObject, label: String, packageName: String): String? {
        val name = label.lowercase(Locale.ROOT).replace(Regex("[^a-z0-9]"), "")
        catalog.optString(name, "").takeIf { it.isNotEmpty() }?.let { return it }
        for (part in packageName.lowercase(Locale.ROOT).split(".")) {
            if (part.length < 5 || part == "android" || part == "google") continue
            catalog.optString(part.replace(Regex("[^a-z0-9]"), ""), "").takeIf { it.isNotEmpty() }?.let { return it }
        }
        return null
    }

    private fun brandIcon(packageName: String): String? = when (packageName) {
        "com.android.vending" -> "brand_googleplay"
        "com.google.android.apps.photos" -> "brand_googlephotos"
        "com.google.android.apps.docs" -> "brand_googledrive"
        "com.google.android.gm" -> "brand_gmail"
        "com.google.android.googlequicksearchbox" -> "brand_google"
        "com.google.android.calendar" -> "brand_googlecalendar"
        "com.apple.android.music" -> "brand_applemusic"
        "deezer.android.app" -> "brand_deezer"
        "com.aspiro.tidal" -> "brand_tidal"
        "com.shazam.android" -> "brand_shazam"
        "au.com.shiftyjelly.pocketcasts" -> "brand_pocketcasts"
        "com.plexapp.android" -> "brand_plex"
        "com.discord" -> "brand_discord"
        "com.spotify.music" -> "brand_spotify"
        "com.google.android.youtube" -> "brand_youtube"
        "com.google.android.apps.youtube.music" -> "brand_youtubemusic"
        "com.google.android.apps.maps" -> "brand_googlemaps"
        "com.waze" -> "brand_waze"
        "com.android.chrome" -> "brand_googlechrome"
        "org.mozilla.firefox" -> "brand_firefox"
        "com.whatsapp" -> "brand_whatsapp"
        "org.telegram.messenger" -> "brand_telegram"
        "com.netflix.mediaclient" -> "brand_netflix"
        "org.videolan.vlc" -> "brand_vlcmediaplayer"
        "com.soundcloud.android" -> "brand_soundcloud"
        else -> null
    }

    private fun shade(color: Int): Int =
        Color.rgb((Color.red(color) * .65).toInt(), (Color.green(color) * .65).toInt(), (Color.blue(color) * .65).toInt())

    private fun open(entry: Entry) {
        if (entry.internal) {
            startActivity(Intent(this, SettingsActivity::class.java))
            return
        }
        try {
            val launchIntent = if (entry.cls != null) {
                Intent(Intent.ACTION_MAIN).setClassName(entry.pkg!!, entry.cls!!)
            } else {
                packageManager.getLaunchIntentForPackage(entry.pkg!!)
            } ?: throw IllegalStateException()
            startActivity(launchIntent)
        } catch (_: Exception) {
            Toast.makeText(this, "${entry.label} is not installed on this device", Toast.LENGTH_SHORT).show()
        }
    }

    companion object {
        private val COLORS = hashMapOf(
            "carplay" to 0xffa64350.toInt(), "androidauto" to 0xff3b7e70.toInt(),
            "audio" to 0xff7c5e91.toInt(), "radio" to 0xffa26443.toInt(), "bluetooth" to 0xff456d92.toInt(),
            "file" to 0xff957640.toInt(), "browser" to 0xff78609b.toInt(), "settings" to 0xff5f636b.toInt(),
            "carsettings" to 0xff655c63.toInt(), "default" to 0xff52545b.toInt(), "wifi" to 0xff467c78.toInt(),
            "custom" to 0xff986079.toInt(), "camera" to 0xff527b74.toInt(), "keyboard" to 0xff526b79.toInt(),
            "ipod" to 0xff886c89.toInt(), "playpause" to 0xff9c555b.toInt(), "sim" to 0xff71804c.toInt(),
            "terminal" to 0xff52616c.toInt(), "guide" to 0xff90754f.toInt()
        )

        @JvmStatic
        fun color(key: String): Int = COLORS[key] ?: COLORS.getValue("default")
    }
}
