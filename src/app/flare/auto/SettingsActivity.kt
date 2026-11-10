package app.flare.auto

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.os.Bundle
import android.provider.Settings
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import java.util.Collections
import java.util.Locale

class SettingsActivity : Activity() {
    private lateinit var prefs: android.content.SharedPreferences
    private lateinit var ui: UiKit
    private lateinit var list: LinearLayout
    private lateinit var root: LinearLayout
    private val refreshers = ArrayList<() -> Unit>()
    private lateinit var updater: UpdateChecker
    private val sections = LinkedHashMap<String, LinearLayout>()
    private val navigation = LinkedHashMap<String, TextView>()
    private lateinit var contentScroll: android.widget.ScrollView
    private var categoryPicker: TextView? = null
    private var selectedCategory = "APPEARANCE"

    override fun attachBaseContext(base: android.content.Context) { super.attachBaseContext(UiScale.wrap(base, "ui_scale_settings")) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.decorView.systemUiVisibility = UiFlags.IMMERSIVE
        prefs = getSharedPreferences("home", 0)
        updater = UpdateChecker(this)
        window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(ThemeTokens.get(this).background))
        render()
    }

    override fun onResume() {
        super.onResume()
        if (::updater.isInitialized) updater.onResume()
        if (::root.isInitialized) {
            if (ui.t !== ThemeTokens.get(this)) ui.recolor(root)
            refreshValues()
            updateNavigation()
        }
    }

    override fun onDestroy() {
        if (::updater.isInitialized) updater.dispose()
        super.onDestroy()
    }

    private fun render() {
        ui = UiKit(this)
        root = ui.column()
        root.setBackgroundColor(ui.t.background)
        root.addView(ui.header("Launcher settings"), LinearLayout.LayoutParams(-1, ui.dp(56)))
        val compact = resources.configuration.screenWidthDp < 620
        val body = ui.column().apply { orientation = if (compact) LinearLayout.VERTICAL else LinearLayout.HORIZONTAL }
        root.addView(body, LinearLayout.LayoutParams(-1, 0, 1f))
        if (compact) {
            categoryPicker = ui.text("", 16, ui.t.textPrimary).apply {
                setPadding(ui.dp(20), 0, ui.dp(20), 0)
                minimumHeight = ui.dp(52)
                setOnClickListener {
                    val names = sections.keys.toTypedArray()
                    ThemedDialogs.Builder(this@SettingsActivity).setTitle("Settings category")
                        .setSingleChoiceItems(names.map { categoryName(it) }.toTypedArray(), names.indexOf(selectedCategory)) { _, index -> showCategory(names[index], true) }.show()
                }
            }
            body.addView(categoryPicker, LinearLayout.LayoutParams(-1, ui.dp(52)))
        } else {
            val navScroll = android.widget.ScrollView(this).apply { isFillViewport = false }
            body.addView(navScroll, LinearLayout.LayoutParams(ui.dp(190), -1))
            val nav = ui.column().apply { setPadding(ui.dp(12), ui.dp(8), ui.dp(8), ui.dp(16)) }
            navScroll.addView(nav)
            val names = arrayOf("APPEARANCE", "PERSONALIZATION", "SCREENSAVER", "STARTUP", "APPLICATION SHORTCUTS", "PERMISSIONS", "DEVICE", "ABOUT")
            names.forEach { name ->
                val item = ui.text(categoryName(name), 14, ui.t.textSecondary).apply {
                    minHeight = ui.dp(48)
                    setPadding(ui.dp(14), 0, ui.dp(8), 0)
                    setOnClickListener { showCategory(name, true) }
                }
                navigation[name] = item
                nav.addView(item, LinearLayout.LayoutParams(-1, ui.dp(48)).apply { bottomMargin = ui.dp(4) })
            }
        }
        contentScroll = android.widget.ScrollView(this)
        body.addView(contentScroll, if (compact) LinearLayout.LayoutParams(-1, 0, 1f) else LinearLayout.LayoutParams(0, -1, 1f))
        list = ui.column()
        list.setPadding(ui.dp(16), 0, ui.dp(20), ui.dp(20))
        contentScroll.addView(list)
        setContentView(root)

        var group = section("APPEARANCE")
        row(group, "Theme", ThemeTokens.NAMES[ThemeTokens.index(this)]) { appearance("theme") }
        row(group, "Home wallpaper", WallpaperPack.name(this, false)) { appearance("home") }
        row(group, "App drawer wallpaper", drawerWallpaperName()) { appearance("drawer") }
        toggle(group, "Glass cards", "glass_cards", true)
        toggle(group, "Animations", "ui_animations", true)
        toggle(group, "Flip Home wallpaper", "flip_gradients", false)
        toggle(group, "Flip app drawer wallpaper", "drawer_flip", false)
        toggle(group, "Dim after sunset", "auto_dim", false)
        step(group, "Text size (%)", "text", 85, 120, 100, 5)
        step(group, "Layout spacing", "spacing", 8, 24, 14, 1)
        row(group, "Home scale", scaleName("ui_scale_home")) { chooseScale("Home scale", "ui_scale_home") }
        row(group, "App drawer scale", scaleName("ui_scale_drawer")) { chooseScale("App drawer scale", "ui_scale_drawer") }
        row(group, "Settings scale", scaleName("ui_scale_settings")) { chooseScale("Settings scale", "ui_scale_settings") }


        group = section("PERSONALIZATION")
        group = section("WEATHER")
        row(group, "Weather location", if (prefs.getBoolean("solar_manual", false)) "Manual" else "Automatic location") {
            editSolarLocation()
        }
        row(group, "Weather display", if (prefs.getBoolean("weather_enabled", false)) "Forecast" else "Sunrise / sunset") {
            ThemedDialogs.Builder(this).setTitle("Weather display")
                .setItems(arrayOf("Sunrise / sunset", "Weather forecast")) { _, selected ->
                    if (selected == 0) { prefs.edit().putBoolean("weather_enabled", false).apply(); refreshValues() }
                    else ThemedDialogs.Builder(this).setTitle("Enable weather?")
                        .setMessage("Downloads forecasts from Open-Meteo when internet is available. Your selected location is sent to the provider. Cached forecasts are used offline, then sunrise/sunset is shown after the age limit.")
                        .setPositiveButton("Enable") { _, _ -> prefs.edit().putBoolean("weather_enabled", true).apply(); refreshValues() }
                        .setNegativeButton("Cancel", null).show()
                }.show()
        }
        row(group, "Offline forecast limit", "${prefs.getInt("weather_age_days", 2)} days") {
            chooseInt("Offline forecast limit", arrayOf("1 day", "2 days", "3 days", "7 days"), intArrayOf(1, 2, 3, 7), "weather_age_days", 2)
        }
        toggle(group, "Fahrenheit", "weather_fahrenheit", false)

        group = section("HOME")
        row(group, "Accent color", accentName()) { chooseAccent() }
        row(group, "Customize home screen", "") { startActivity(Intent(this, CustomizeActivity::class.java)) }
        toggle(group, "24-hour clock", "clock24", false)
        toggle(group, "Keep display active", "awake", true)

        group = section("SCREENSAVER")
        row(group, "Display mode", saverModeName()) { chooseSaverMode() }
        row(group, "Screensaver wallpaper", WallpaperPack.name(this, true)) { appearance("saver") }
        toggleSaverFlip(group)
        row(group, "Idle timeout", timeoutName()) {
            chooseInt(
                "Idle timeout",
                arrayOf("Off", "1 minute", "5 minutes", "10 minutes", "15 minutes"),
                intArrayOf(0, 1, 5, 10, 15), "saver_timeout", 0
            )
        }
        row(group, "Clock style", ClockStyles.labels[ClockStyles.ids.indexOf(prefs.getString("saver_clock_style", "classic")).coerceAtLeast(0)]) {
            val current = ClockStyles.ids.indexOf(prefs.getString("saver_clock_style", "classic")).coerceAtLeast(0)
            ThemedDialogs.Builder(this).setTitle("Screensaver clock style").setSingleChoiceItems(ClockStyles.labels, current) { _, selected ->
                prefs.edit().putString("saver_clock_style", ClockStyles.ids[selected]).apply(); refreshValues()
            }.show()
        }
        row(group, "Clock size", clockSizeName()) {
            chooseInt("Clock size", arrayOf("Small", "Medium", "Large"), intArrayOf(96, 120, 140), "saver_size", 120)
        }
        toggle(group, "Show date", "saver_date", true)
        toggle(group, "Show vehicle label", "saver_civic", true)

        group = section("STARTUP")
        row(group, "Launch application", startupName()) {
            startActivityForResult(
                Intent(this, AppPickerActivity::class.java)
                    .putExtra(AppPickerActivity.ROLE, "startup")
                    .putExtra(AppPickerActivity.TARGET, prefs.getString("startup_app", "")),
                PICK
            )
        }
        row(group, "Launch delay", startupDelayName()) {
            chooseInt("Launch delay", arrayOf("5 seconds", "10 seconds", "20 seconds", "30 seconds"),
                intArrayOf(5, 10, 20, 30), "startup_delay", 10)
        }
        toggle(group, "Start in screensaver", "startup_saver", false)
        row(group, "Initial audio source", startupSourceName()) { chooseStartupSource() }

        group = section("APPLICATION SHORTCUTS")
        for (role in arrayOf(
            arrayOf("CarPlay", "carplay"), arrayOf("Android Auto", "androidauto"),
            arrayOf("Audio", "audio"), arrayOf("Radio", "radio"), arrayOf("Bluetooth", "bluetooth")
        )) {
            val key = role[1]
            row(group, role[0], appName(key)) {
                startActivityForResult(
                    Intent(this, AppPickerActivity::class.java)
                        .putExtra(AppPickerActivity.ROLE, key)
                        .putExtra(AppPickerActivity.TARGET, prefs.getString("app_$key", "")),
                    PICK
                )
            }
        }

        group = section("APP DRAWER")
        row(group, "Icon pack", IconPacks.name(this)) { chooseIconPack() }
        step(group, "Grid columns", "columns", 4, 6, 5, 1)
        row(group, "Manage hidden apps", "${prefs.getStringSet("hidden_apps", Collections.emptySet())?.size ?: 0} hidden") {
            startActivity(Intent(this, HiddenAppsActivity::class.java))
        }

        group = section("PERMISSIONS")
        row(group, "Media access", if (mediaAllowed()) "Granted" else "Not granted") {
            startActivity(Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"))
        }
        row(group, "Location access", if (locationGranted()) "Granted" else "Not granted") {
            requestPermissions(arrayOf("android.permission.ACCESS_FINE_LOCATION"), 42)
        }

        group = section("DEVICE")
        row(group, "Android settings", "") { startActivity(Intent(Settings.ACTION_SETTINGS)) }
        row(group, "Default launcher", if (isDefaultLauncher()) "Active" else "Configure") {
            try {
                startActivity(Intent(Settings.ACTION_HOME_SETTINGS))
            } catch (_: Exception) {
            }
        }
        row(group, "Reset preferences", "") {
            ThemedDialogs.Builder(this)
                .setTitle("Reset launcher settings?")
                .setMessage("Your installed apps are not changed.")
                .setPositiveButton("Reset") { _, _ ->
                    prefs.edit().clear().apply()
                    ThemeTokens.invalidate()
                    HomeActivity.instance.get()?.invalidateSettings()
                    ui.recolor(root)
                    refreshValues()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        group = section("ABOUT")
        val version = try {
            val info = packageManager.getPackageInfo(packageName, 0)
            "${info.versionName ?: "Not set"} · ${info.versionCode}"
        } catch (_: Exception) {
            "Not available"
        }
        row(group, "Version", version, null)
        row(group, "Check for updates", "GitHub") { updater.check() }
        row(group, "Open-source credits", "Dashline · gradients") {
            ThemedDialogs.Builder(this)
                .setTitle("Open-source credits")
                .setMessage(
                    "Dashline MediaMonitor: GPL-3.0-only.\n" +
                        "Gradient palette: 720pixel free-gradient-tools, MIT license.\n" +
                        "See the included gradient attribution file."
                )
                .setPositiveButton("Close", null)
                .show()
        }
        showCategory(selectedCategory, false)
    }

    private fun categoryName(name: String): String = when (name) {
        "APPLICATION SHORTCUTS" -> "App shortcuts"
        "APP DRAWER" -> "App drawer"
        else -> name.lowercase(Locale.ROOT).replaceFirstChar { it.titlecase(Locale.ROOT) }
    }

    private fun updateNavigation() {
        categoryPicker?.text = categoryName(selectedCategory) + "  ▾"
        navigation.forEach { (name, view) ->
            val active = name == selectedCategory
            view.setTextColor(if (active) ui.t.textPrimary else ui.t.textSecondary)
            view.tag = if (active) UiKit.Role.PRIMARY else UiKit.Role.SECONDARY
            view.background = if (active) GlassDrawable(ui.t.card, ui.dp(10).toFloat(), ui.t.cardBorder) else null
            view.setTypeface(android.graphics.Typeface.DEFAULT, if (active) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
        }
    }

    private fun showCategory(name: String, animate: Boolean) {
        if (!sections.containsKey(name)) return
        if (animate && selectedCategory == name) return
        selectedCategory = name
        sections.forEach { (key, view) -> view.visibility = if (key == name) android.view.View.VISIBLE else android.view.View.GONE }
        contentScroll.scrollTo(0, 0)
        updateNavigation()
        val panel = sections[name] ?: return
        panel.animate().cancel()
        panel.alpha = 1f
        panel.translationY = 0f
        panel.translationX = 0f
        if (animate && Motion.enabled(this)) {
            panel.alpha = .72f
            panel.translationX = ui.dp(12).toFloat()
            panel.animate().alpha(1f).translationX(0f).setDuration(300)
                .setInterpolator(android.view.animation.PathInterpolator(.2f, .8f, .2f, 1f)).start()
            navigation[name]?.let { item ->
                item.animate().cancel()
                item.scaleX = .97f; item.scaleY = .97f
                item.animate().scaleX(1f).scaleY(1f).setDuration(300)
                    .setInterpolator(android.view.animation.PathInterpolator(.2f, .8f, .2f, 1f)).start()
            }
        }
    }

    private fun section(name: String): LinearLayout {
        val nested = name in setOf("WEATHER", "HOME", "DOCK", "APP DRAWER")
        if (nested) {
            val parent = sections["PERSONALIZATION"] ?: error("Personalization must be created first")
            parent.addView(ui.section(categoryName(name).uppercase(Locale.ROOT)))
            return ui.group().also { parent.addView(it) }
        }
        val container = ui.column()
        if (name != "PERSONALIZATION") container.addView(ui.section(categoryName(name).uppercase(Locale.ROOT)))
        val group = ui.group()
        container.addView(group)
        sections[name] = container
        list.addView(container)
        return group
    }

    private fun row(group: LinearLayout, name: String, value: String, action: (() -> Unit)? = null) {
        val view = ui.valueRow(name, value, action?.let { Runnable(it) })
        ui.add(group, view)
        val target = view.getChildAt(1) as TextView
        refreshers.add {
            val value = when (name) {
                "Theme" -> ThemeTokens.NAMES[ThemeTokens.index(this)]
                "Home scale" -> scaleName("ui_scale_home")
                "App drawer scale" -> scaleName("ui_scale_drawer")
                "Settings scale" -> scaleName("ui_scale_settings")
                "Weather display" -> if (prefs.getBoolean("weather_enabled", false)) "Forecast" else "Sunrise / sunset"
                "Offline forecast limit" -> "${prefs.getInt("weather_age_days", 2)} days"
                "Home wallpaper" -> WallpaperPack.name(this, false)
                "Accent color" -> accentName()
                "Weather location" -> if (prefs.getBoolean("solar_manual", false)) "Manual" else "Automatic location"
                "Display mode" -> saverModeName()
                "Screensaver wallpaper", "Screen Off wallpaper" -> WallpaperPack.name(this, true)
                "Idle timeout" -> timeoutName()
                "Clock style" -> ClockStyles.labels[ClockStyles.ids.indexOf(prefs.getString("saver_clock_style", "classic")).coerceAtLeast(0)]
                "Clock size" -> clockSizeName()
                "Launch application" -> startupName()
                "Launch delay" -> startupDelayName()
                "Initial audio source" -> startupSourceName()
                "App drawer wallpaper" -> drawerWallpaperName()
                "Icon pack" -> IconPacks.name(this)
                "Manage hidden apps" -> "${prefs.getStringSet("hidden_apps", Collections.emptySet())?.size ?: 0} hidden"
                "CarPlay" -> appName("carplay")
                "Android Auto" -> appName("androidauto")
                "Audio" -> appName("audio")
                "Radio" -> appName("radio")
                "Bluetooth" -> appName("bluetooth")
                "Media access" -> if (mediaAllowed()) "Granted" else "Not granted"
                "Location access" -> if (locationGranted()) "Granted" else "Not granted"
                "Default launcher" -> if (isDefaultLauncher()) "Active" else "Configure"
                else -> target.text
            }
            if (target.text.toString() != value.toString()) target.text = value
        }
    }

    private fun refreshValues() {
        refreshers.forEach { it() }
    }

    private fun toggle(group: LinearLayout, name: String, key: String, default: Boolean) {
        lateinit var view: LinearLayout
        val refresh = {
            val enabled = prefs.getBoolean(key, default)
            val switch = view.getChildAt(1) as android.widget.Switch
            switch.isChecked = enabled
            if (!Motion.enabled(this)) switch.jumpDrawablesToCurrentState()
            view.contentDescription = "$name, ${if (enabled) "on" else "off"}"
        }
        view = ui.toggle(name, prefs.getBoolean(key, default), Runnable {
            prefs.edit().putBoolean(key, !prefs.getBoolean(key, default)).apply()
            refresh()
        })
        refreshers.add(refresh)
        refresh()
        ui.add(group, view)
    }

    private fun step(group: LinearLayout, name: String, key: String, min: Int, max: Int, default: Int, amount: Int) {
        lateinit var view: LinearLayout
        val refresh = { (view.getChildAt(3) as TextView).text = prefs.getInt(key, default).toString() }
        view = ui.stepper(
            name, prefs.getInt(key, default).toString(),
            Runnable { prefs.edit().putInt(key, maxOf(min, prefs.getInt(key, default) - amount)).apply(); refresh() },
            Runnable { prefs.edit().putInt(key, minOf(max, prefs.getInt(key, default) + amount)).apply(); refresh() }
        )
        refreshers.add(refresh)
        ui.add(group, view)
    }

    private fun toggleSaverFlip(group: LinearLayout) {
        lateinit var view: LinearLayout
        fun current() = prefs.getBoolean("saver_flip", prefs.getBoolean("flip_gradients", false))
        val refresh = {
            val enabled = current()
            (view.getChildAt(1) as android.widget.Switch).apply {
                isChecked = enabled
                if (!Motion.enabled(this@SettingsActivity)) jumpDrawablesToCurrentState()
            }
            view.contentDescription = "Flip screensaver wallpaper, ${if (enabled) "on" else "off"}"
        }
        view = ui.toggle("Flip screensaver wallpaper", current(), Runnable {
            prefs.edit().putBoolean("saver_flip", !current()).apply()
            refresh()
        })
        refreshers.add(refresh)
        refresh()
        ui.add(group, view)
    }

    private fun saverModeName() = if (prefs.getString("saver_mode", "clock") == "image") "Wallpaper" else "Clock"
    private fun timeoutName(): String {
        val minutes = prefs.getInt("saver_timeout", 0)
        return if (minutes == 0) "Off" else "$minutes min"
    }
    private fun clockSizeName() = when (prefs.getInt("saver_size", 120)) {
        96 -> "Small"
        140 -> "Large"
        else -> "Medium"
    }
    private fun startupDelayName() = "${prefs.getInt("startup_delay", 10)} sec"
    private fun startupSourceName() = when (prefs.getString("startup_source", "none")) {
        "radio" -> "Radio"
        "bluetooth" -> "Bluetooth"
        else -> "System default"
    }

    private fun startupName(): String {
        val target = prefs.getString("startup_app", "") ?: ""
        if (target.isEmpty()) return "Off"
        return try {
            packageManager.getActivityInfo(ComponentName.unflattenFromString(target)!!, 0)
                .loadLabel(packageManager).toString()
        } catch (_: Exception) {
            "App unavailable"
        }
    }

    private fun scaleName(key: String): String = "${prefs.getInt(key, 100)}%"
    private fun chooseScale(title: String, key: String) {
        val values = intArrayOf(80, 90, 100, 110, 120)
        val current = values.indexOf(prefs.getInt(key, 100)).coerceAtLeast(0)
        ThemedDialogs.Builder(this).setTitle(title)
            .setSingleChoiceItems(arrayOf("80%", "90%", "100%", "110%", "120%"), current) { _, selected ->
                prefs.edit().putInt(key, values[selected]).apply()
                refreshValues()
                if (key == "ui_scale_settings") Toast.makeText(this, "Reopen settings to apply", Toast.LENGTH_SHORT).show()
            }.show()
    }

    private fun chooseSaverMode() {
        val names = arrayOf("Clock", "Wallpaper")
        val current = if (prefs.getString("saver_mode", "clock") == "image") 1 else 0
        ThemedDialogs.Builder(this).setTitle("Display mode")
            .setSingleChoiceItems(names, current) { dialog, selected ->
                dialog.dismiss()
                prefs.edit().putString("saver_mode", if (selected == 1) "image" else "clock").apply()
                refreshValues()
            }.setNegativeButton("Cancel", null).show()
    }

    private fun chooseInt(title: String, names: Array<String>, values: IntArray, key: String, default: Int) {
        val current = values.indexOf(prefs.getInt(key, default)).coerceAtLeast(0)
        ThemedDialogs.Builder(this).setTitle(title)
            .setSingleChoiceItems(names, current) { dialog, selected ->
                dialog.dismiss()
                prefs.edit().putInt(key, values[selected]).apply()
                refreshValues()
            }.setNegativeButton("Cancel", null).show()
    }

    private fun chooseStartupSource() {
        val names = arrayOf("System default", "Radio", "Bluetooth")
        val values = arrayOf("none", "radio", "bluetooth")
        val current = values.indexOf(prefs.getString("startup_source", "none")).coerceAtLeast(0)
        ThemedDialogs.Builder(this).setTitle("Startup media source (may play)")
            .setSingleChoiceItems(names, current) { dialog, selected ->
                dialog.dismiss()
                prefs.edit().putString("startup_source", values[selected]).apply()
                refreshValues()
            }.setNegativeButton("Cancel", null).show()
    }

    private fun accentName() =
        if (prefs.contains("accent_color")) String.format(Locale.US, "#%06X", prefs.getInt("accent_color", 0) and 0xffffff)
        else "Theme default"

    private fun chooseAccent() {
        val labels = arrayOf("Theme default", "Red", "White", "Orange", "Green", "Blue", "Purple", "Custom color")
        val colors = intArrayOf(
            0, 0xffe60012.toInt(), -1, 0xffffa040.toInt(), 0xff36ca87.toInt(),
            0xff56a8ff.toInt(), 0xffb184ff.toInt()
        )
        ThemedDialogs.Builder(this).setTitle("Accent color").setItems(labels) { _, selected ->
            if (selected == 7) {
                editAccent()
                return@setItems
            }
            if (selected == 0) prefs.edit().remove("accent_color").apply()
            else prefs.edit().putInt("accent_color", colors[selected]).apply()
            ThemeTokens.invalidate()
            ui.recolor(root)
            refreshValues()
        }.show()
    }

    private fun editAccent() {
        val input = EditText(this).apply {
            setSingleLine(true)
            hint = "#RRGGBB"
            if (prefs.contains("accent_color")) setText(accentName())
        }
        ThemedDialogs.Builder(this).setTitle("Accent color").setView(input)
            .setPositiveButton("Save") { _, _ ->
                try {
                    val raw = input.text.toString().trim()
                    require(raw.matches(Regex("#[0-9a-fA-F]{6}")))
                    prefs.edit().putInt("accent_color", android.graphics.Color.parseColor(raw)).apply()
                    ThemeTokens.invalidate()
                    ui.recolor(root)
                    refreshValues()
                } catch (_: Exception) {
                    Toast.makeText(this, "Enter a color like #E60012", Toast.LENGTH_LONG).show()
                }
            }.setNegativeButton("Cancel", null).show()
    }

    private fun editSolarLocation() {
        val fields = ui.column()
        val latitude = EditText(this).apply {
            setSingleLine(true)
            hint = "Latitude"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL or
                android.text.InputType.TYPE_NUMBER_FLAG_SIGNED
            setText(prefs.getString("solar_lat", ""))
        }
        val longitude = EditText(this).apply {
            setSingleLine(true)
            hint = "Longitude"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL or
                android.text.InputType.TYPE_NUMBER_FLAG_SIGNED
            setText(prefs.getString("solar_lon", ""))
        }
        fields.addView(latitude)
        fields.addView(longitude)
        ThemedDialogs.Builder(this).setTitle("Weather location")
            .setMessage("Used for weather and sunrise/sunset. Automatic uses the last known device location; manual coordinates use the device time zone.")
            .setView(fields)
            .setPositiveButton("Save") { _, _ ->
                try {
                    val lat = latitude.text.toString().toDouble()
                    val lon = longitude.text.toString().toDouble()
                    require(lat.isFinite() && lon.isFinite() && lat in -90.0..90.0 && lon in -180.0..180.0)
                    prefs.edit().putBoolean("solar_manual", true).putString("solar_lat", lat.toString())
                        .putString("solar_lon", lon.toString()).apply()
                    refreshValues()
                } catch (_: Exception) {
                    Toast.makeText(this, "Enter valid latitude and longitude", Toast.LENGTH_LONG).show()
                }
            }
            .setNeutralButton("Automatic location") { _, _ ->
                prefs.edit().putBoolean("solar_manual", false).apply()
                refreshValues()
            }
            .setNegativeButton("Cancel", null).show()
    }

    private fun chooseIconPack() {
        Thread {
            val packs = IconPacks.installed(this)
            val labels = arrayOf("Simple Icons (default)") + packs.map { it.loadLabel(packageManager).toString() }
            val ids = arrayOf("") + packs.map { it.activityInfo.packageName }
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                val selected = prefs.getString(IconPacks.KEY, "") ?: ""
                val current = ids.indexOf(selected).coerceAtLeast(0)
                ThemedDialogs.Builder(this).setTitle("Icon pack")
                    .setSingleChoiceItems(labels, current) { _, index ->
                        prefs.edit().putString(IconPacks.KEY, ids[index]).apply()
                        refreshValues()
                    }.setNegativeButton("Cancel", null).show()
            }
        }.start()
    }

    private fun drawerWallpaperName() = WallpaperPack.drawerName(this)

    private fun appearance(mode: String) {
        startActivity(Intent(this, AppearancePickerActivity::class.java).putExtra("mode", mode))
    }

    private fun mediaAllowed(): Boolean {
        val value = Settings.Secure.getString(contentResolver, "enabled_notification_listeners") ?: return false
        val expected = ComponentName(this, MediaAccess::class.java)
        return value.split(":").any { expected == ComponentName.unflattenFromString(it) }
    }

    private fun locationGranted() =
        checkSelfPermission("android.permission.ACCESS_FINE_LOCATION") == PackageManager.PERMISSION_GRANTED

    private fun isDefaultLauncher(): Boolean {
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val current: ResolveInfo? = packageManager.resolveActivity(home, PackageManager.MATCH_DEFAULT_ONLY)
        return current != null && packageName == current.activityInfo.packageName
    }

    private fun appName(role: String): String {
        val target = prefs.getString("app_$role", "") ?: ""
        if (target.isEmpty()) return "Default application"
        return try {
            val component = ComponentName.unflattenFromString(target)!!
            packageManager.getActivityInfo(component, 0).loadLabel(packageManager).toString()
        } catch (_: Exception) {
            "App unavailable"
        }
    }

    @Deprecated("Deprecated in Android")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == PICK && resultCode == RESULT_OK && data != null) {
            val role = data.getStringExtra(AppPickerActivity.ROLE)
            val target = data.getStringExtra(AppPickerActivity.TARGET)
            if (role != null && target != null) {
                prefs.edit().putString(if (role == "startup") "startup_app" else "app_$role", target).apply()
            }
        }
    }

    companion object {
        private const val PICK = 100
    }
}
