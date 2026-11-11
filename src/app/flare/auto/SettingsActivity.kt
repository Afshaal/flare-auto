package app.flare.auto

import android.Manifest
import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.View
import android.view.animation.PathInterpolator
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.util.Locale

class SettingsActivity : Activity() {
    /** Dialogs take focus; restore full screen when it comes back. */
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) window.immersive()
    }

    private lateinit var prefs: SharedPreferences
    private lateinit var ui: UiKit
    private lateinit var list: LinearLayout
    private lateinit var root: LinearLayout
    /** Each row re-reads its own value; run them all after any change. */
    private val refreshers = ArrayList<() -> Unit>()
    private var offlineMaps: OfflineMapPanel? = null
    private lateinit var updater: UpdateChecker
    private val sections = LinkedHashMap<String, LinearLayout>()
    private val navigation = LinkedHashMap<String, TextView>()
    private lateinit var contentScroll: ScrollView
    private var categoryPicker: TextView? = null
    private var selectedCategory = "APPEARANCE"

    override fun attachBaseContext(base: Context) { super.attachBaseContext(UiScale.wrap(base, Keys.UI_SCALE_SETTINGS)) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.immersive()
        if (intent.getStringExtra(EXTRA_CATEGORY) == OFFLINE_MAPS) selectedCategory = OFFLINE_MAPS
        prefs = ProfileStore.preferences(this)
        updater = UpdateChecker(this)
        window.setBackgroundDrawable(ColorDrawable(ThemeTokens.get(this).background))
        render()
    }

    override fun onResume() {
        super.onResume()
        if (::updater.isInitialized) updater.onResume()
        offlineMaps?.resume()
        if (::root.isInitialized) {
            if (ui.t !== ThemeTokens.get(this)) ui.recolor(root)
            refreshValues()
            updateNavigation()
        }
    }

    override fun onPause() {
        offlineMaps?.pause()
        super.onPause()
    }

    override fun onDestroy() {
        if (::updater.isInitialized) updater.dispose()
        super.onDestroy()
    }

    private fun render() {
        ui = UiKit(this)
        root = ui.column()
        root.setBackgroundColor(ui.t.background)
        root.addView(ui.header("Launcher settings"), LinearLayout.LayoutParams(MATCH, ui.dp(56)))
        val compact = resources.configuration.screenWidthDp < 620
        val body = ui.column().apply { orientation = if (compact) LinearLayout.VERTICAL else LinearLayout.HORIZONTAL }
        root.addView(body, LinearLayout.LayoutParams(MATCH, 0, 1f))
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
            body.addView(categoryPicker, LinearLayout.LayoutParams(MATCH, ui.dp(52)))
        } else {
            val navScroll = ScrollView(this).apply { isFillViewport = false }
            body.addView(navScroll, LinearLayout.LayoutParams(ui.dp(190), MATCH))
            val nav = ui.column().apply { setPadding(ui.dp(12), ui.dp(8), ui.dp(8), ui.dp(16)) }
            navScroll.addView(nav)
            for (name in CATEGORIES) {
                val item = ui.text(categoryName(name), 14, ui.t.textSecondary).apply {
                    minHeight = ui.dp(48)
                    setPadding(ui.dp(14), 0, ui.dp(8), 0)
                    setOnClickListener { showCategory(name, true) }
                }
                navigation[name] = item
                nav.addView(item, LinearLayout.LayoutParams(MATCH, ui.dp(48)).apply { bottomMargin = ui.dp(4) })
            }
        }
        contentScroll = ScrollView(this)
        body.addView(contentScroll, if (compact) LinearLayout.LayoutParams(MATCH, 0, 1f) else LinearLayout.LayoutParams(0, MATCH, 1f))
        list = ui.column()
        list.setPadding(ui.dp(16), 0, ui.dp(20), ui.dp(20))
        contentScroll.addView(list)
        setContentView(root)

        var group = section("APPEARANCE")
        row(group, "Color mode", { ThemeTokens.mode(this) }) {
            ThemedDialogs.Builder(this).setTitle("Color mode")
                .setItems(arrayOf("Dark", "Light", "Automatic")) { _, selected ->
                    prefs.edit().putString(Keys.THEME_MODE, arrayOf("dark", "light", "auto")[selected]).apply()
                    ThemeTokens.invalidate()
                    ui.recolor(root)
                }.show()
        }
        step(group, "Day mode starts (hour)", Keys.THEME_DAY_HOUR, 0, 23, 7, 1)
        step(group, "Night mode starts (hour)", Keys.THEME_NIGHT_HOUR, 0, 23, 19, 1)
        row(group, "Theme", { ThemeTokens.NAMES[ThemeTokens.activeIndex(this)] }) { appearance("theme") }
        row(group, "Home wallpaper", { WallpaperPack.name(this, false) }) { appearance("home") }
        row(group, "App drawer wallpaper", { WallpaperPack.drawerName(this) }) { appearance("drawer") }
        toggle(group, "Glass cards", Keys.GLASS_CARDS, true)
        toggle(group, "Animations", Keys.UI_ANIMATIONS, true)
        toggle(group, "Flip Home wallpaper", Keys.FLIP_GRADIENTS, false)
        toggle(group, "Flip app drawer wallpaper", Keys.DRAWER_FLIP, false)
        toggle(group, "Dim after sunset", Keys.AUTO_DIM, false)
        step(group, "Text size (%)", Keys.TEXT, 85, 120, 100, 5)
        step(group, "Layout spacing", Keys.SPACING, 8, 24, 14, 1)
        scaleRow(group, "Home scale", Keys.UI_SCALE_HOME)
        scaleRow(group, "App drawer scale", Keys.UI_SCALE_DRAWER)
        scaleRow(group, "Settings scale", Keys.UI_SCALE_SETTINGS)

        section("PERSONALIZATION")
        group = section("WEATHER")
        row(group, "Weather location", { SettingsValues.weatherLocation(prefs) }) { editSolarLocation() }
        row(group, "Weather display", { SettingsValues.weatherDisplay(prefs) }) {
            ThemedDialogs.Builder(this).setTitle("Weather display")
                .setItems(arrayOf("Sunrise / sunset", "Weather forecast")) { _, selected ->
                    if (selected == 0) { prefs.edit().putBoolean(Keys.WEATHER_ENABLED, false).apply(); refreshValues() }
                    else ThemedDialogs.Builder(this).setTitle("Enable weather?")
                        .setMessage("Downloads forecasts from Open-Meteo when internet is available. Your selected location is sent to the provider. Cached forecasts are used offline, then sunrise/sunset is shown after the age limit.")
                        .setPositiveButton("Enable") { _, _ -> prefs.edit().putBoolean(Keys.WEATHER_ENABLED, true).apply(); refreshValues() }
                        .setNegativeButton("Cancel", null).show()
                }.show()
        }
        row(group, "Offline forecast limit", { SettingsValues.forecastLimit(prefs) }) {
            chooseInt("Offline forecast limit", arrayOf("1 day", "2 days", "3 days", "7 days"), intArrayOf(1, 2, 3, 7), Keys.WEATHER_AGE_DAYS, 2)
        }
        toggle(group, "Fahrenheit", Keys.WEATHER_FAHRENHEIT, false)

        group = section("HOME")
        row(group, "Accent color", { SettingsValues.accent(prefs) }) { chooseAccent() }
        row(group, "Customize home screen", { "" }) { startActivity(Intent(this, GridEditorActivity::class.java)) }
        toggle(group, "24-hour clock", Keys.CLOCK24, false)
        toggle(group, "Keep display active", Keys.AWAKE, true)

        group = section("SCREENSAVER")
        for (left in listOf(true, false)) {
            val key = if (left) Keys.SAVER_SLOT_LEFT else Keys.SAVER_SLOT_RIGHT
            row(group, if (left) "Top-left information" else "Top-right information", { SettingsValues.saverSlot(prefs, left) }) {
                ThemedDialogs.Builder(this).setTitle("Corner information").setItems(arrayOf("None", "Weather", "Sunrise / sunset", "Both")) { _, i ->
                    prefs.edit().putString(key, arrayOf("off", "weather", "sun", "both")[i]).apply(); refreshValues()
                }.show()
            }
        }
        toggle(group, "Song-change banner", Keys.SAVER_MUSIC_BANNER, false)
        toggle(group, "Information over image wallpaper", Keys.SAVER_IMAGE_INFO, false)
        toggle(group, "Notification banners", Keys.SAVER_BANNERS, false)
        toggle(group, "Hide notification contents", Keys.SAVER_BANNER_PRIVATE, true)
        row(group, "Notification apps", { "Choose allowed apps" }) {
            startActivity(Intent(this, AppPickerActivity::class.java).putExtra(AppPickerActivity.ROLE, "banners"))
        }
        row(group, "Notification access", { if (mediaAllowed()) "Allowed" else "Enable" }) {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }
        row(group, "Banner position", { SettingsValues.bannerPosition(prefs) }) {
            ThemedDialogs.Builder(this).setTitle("Banner position").setItems(arrayOf("Top left", "Top right", "Alternate corners")) { _, i ->
                prefs.edit().putString(Keys.SAVER_BANNER_POSITION, arrayOf("left", "right", "alternate")[i]).apply(); refreshValues()
            }.show()
        }
        step(group, "Banner duration (seconds)", Keys.SAVER_BANNER_SECONDS, 2, 15, 5, 1)
        step(group, "Banner size (%)", Keys.SAVER_BANNER_SIZE, 85, 120, 100, 5)
        row(group, "Display mode", { SettingsValues.saverMode(prefs) }) { chooseSaverMode() }
        row(group, "Screensaver wallpaper", { WallpaperPack.name(this, true) }) { appearance("saver") }
        toggleSaverFlip(group)
        row(group, "Idle timeout", { SettingsValues.timeout(prefs) }) {
            chooseInt("Idle timeout", arrayOf("Off", "1 minute", "5 minutes", "10 minutes", "15 minutes"), intArrayOf(0, 1, 5, 10, 15), Keys.SAVER_TIMEOUT, 0)
        }
        row(group, "Clock style", { ClockStyles.labels[SettingsValues.saverClockIndex(prefs)] }) {
            ThemedDialogs.Builder(this).setTitle("Screensaver clock style").setSingleChoiceItems(ClockStyles.labels, SettingsValues.saverClockIndex(prefs)) { _, selected ->
                prefs.edit().putString(Keys.SAVER_CLOCK_STYLE, ClockStyles.ids[selected]).apply(); refreshValues()
            }.show()
        }
        row(group, "Clock size", { SettingsValues.clockSize(prefs) }) {
            chooseInt("Clock size", arrayOf("Small", "Medium", "Large"), intArrayOf(96, 120, 140), Keys.SAVER_SIZE, 120)
        }
        toggle(group, "Show date", Keys.SAVER_DATE, true)
        toggle(group, "Show vehicle label", Keys.SAVER_VEHICLE_LABEL, true)

        group = section(OFFLINE_MAPS)
        offlineMaps = OfflineMapPanel(this, ui, group)

        group = section("STARTUP")
        row(group, "Driver profiles", { "Choose driver & startup behavior" }) { startActivity(Intent(this, ProfilesActivity::class.java)) }
        row(group, "Launch application", ::startupName) {
            startActivityForResult(Intent(this, AppPickerActivity::class.java)
                .putExtra(AppPickerActivity.ROLE, "startup")
                .putExtra(AppPickerActivity.TARGET, prefs.getString(Keys.STARTUP_APP, "")), REQUEST_PICK)
        }
        row(group, "Launch delay", { SettingsValues.startupDelay(prefs) }) {
            chooseInt("Launch delay", arrayOf("5 seconds", "10 seconds", "20 seconds", "30 seconds"), intArrayOf(5, 10, 20, 30), Keys.STARTUP_DELAY, 10)
        }
        toggle(group, "Start in screensaver", Keys.STARTUP_SAVER, false)
        row(group, "Initial audio source", { SettingsValues.startupSource(prefs) }) { chooseStartupSource() }

        group = section("APPLICATION SHORTCUTS")
        for ((label, role) in SHORTCUT_ROLES) {
            row(group, label, { appName(role) }) {
                startActivityForResult(Intent(this, AppPickerActivity::class.java)
                    .putExtra(AppPickerActivity.ROLE, role)
                    .putExtra(AppPickerActivity.TARGET, prefs.getString(Keys.APP_PREFIX + role, "")), REQUEST_PICK)
            }
        }

        group = section("APP DRAWER")
        row(group, "Icon pack", { IconPacks.name(this) }) { chooseIconPack() }
        step(group, "Grid columns", Keys.COLUMNS, 4, 6, 5, 1)
        row(group, "Manage hidden apps", { SettingsValues.hiddenApps(prefs) }) {
            startActivity(Intent(this, HiddenAppsActivity::class.java))
        }

        group = section("PERMISSIONS")
        row(group, "Media access", { if (mediaAllowed()) "Granted" else "Not granted" }) {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }
        row(group, "Location access", { if (locationGranted()) "Granted" else "Not granted" }) {
            requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), REQUEST_LOCATION)
        }

        group = section("DEVICE")
        row(group, "Android settings", { "" }) { startActivity(Intent(Settings.ACTION_SETTINGS)) }
        row(group, "Default launcher", { if (isDefaultLauncher()) "Active" else "Configure" }) {
            AppLookup.start(this, Intent(Settings.ACTION_HOME_SETTINGS))
        }
        row(group, "Reset preferences", { "" }) {
            ThemedDialogs.Builder(this)
                .setTitle("Reset launcher settings?")
                .setMessage("Your installed apps are not changed.")
                .setPositiveButton("Reset") { _, _ ->
                    prefs.edit().clear().apply()
                    ThemeTokens.invalidate()
                    HomeActivity.requestRebuild()
                    ui.recolor(root)
                    refreshValues()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        group = section("ABOUT")
        val version = versionText()
        row(group, "Version", { version }, null)
        row(group, "Check for updates", { "GitHub" }) { updater.check() }
        row(group, "Open-source credits", { "Dashline · maps · icons" }) {
            ThemedDialogs.Builder(this)
                .setTitle("Open-source credits")
                .setMessage(Credits.SUMMARY)
                .setPositiveButton("Close", null)
                .show()
        }
        showCategory(selectedCategory, false)
    }

    private fun versionText(): String = try {
        val info = packageManager.getPackageInfo(packageName, 0)
        val code = if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else @Suppress("DEPRECATION") info.versionCode.toLong()
        "${info.versionName ?: "Not set"} · $code"
    } catch (_: Exception) {
        "Not available"
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
            view.setTypeface(Typeface.DEFAULT, if (active) Typeface.BOLD else Typeface.NORMAL)
        }
    }

    private fun showCategory(name: String, animate: Boolean) {
        if (!sections.containsKey(name)) return
        if (animate && selectedCategory == name) return
        selectedCategory = name
        sections.forEach { (key, view) -> view.visibility = if (key == name) View.VISIBLE else View.GONE }
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
            panel.animate().alpha(1f).translationX(0f).setDuration(300).setInterpolator(PathInterpolator(.2f, .8f, .2f, 1f)).start()
            navigation[name]?.let { item ->
                item.animate().cancel()
                item.scaleX = .97f; item.scaleY = .97f
                item.animate().scaleX(1f).scaleY(1f).setDuration(300).setInterpolator(PathInterpolator(.2f, .8f, .2f, 1f)).start()
            }
        }
    }

    /** Top-level categories get their own panel; WEATHER, HOME, DOCK and APP DRAWER are sub-sections of PERSONALIZATION. */
    private fun section(name: String): LinearLayout {
        if (name in NESTED) {
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

    private fun row(group: LinearLayout, name: String, value: () -> String, action: (() -> Unit)? = null) {
        val view = ui.valueRow(name, value(), action?.let { Runnable(it) })
        ui.add(group, view)
        refreshers.add { view.setValue(value()) }
    }

    private fun refreshValues() = refreshers.forEach { it() }

    private fun toggleRow(group: LinearLayout, name: String, current: () -> Boolean, flip: () -> Unit) {
        lateinit var view: UiKit.ToggleRow
        val refresh = { view.setChecked(current(), name) }
        view = ui.toggle(name, current(), Runnable { flip(); refresh() })
        refreshers.add(refresh)
        refresh()
        ui.add(group, view)
    }

    private fun toggle(group: LinearLayout, name: String, key: String, default: Boolean) =
        toggleRow(group, name, { prefs.getBoolean(key, default) }) { prefs.edit().putBoolean(key, !prefs.getBoolean(key, default)).apply() }

    /** The screensaver follows "Flip Home wallpaper" until it is set on its own. */
    private fun toggleSaverFlip(group: LinearLayout) =
        toggleRow(group, "Flip screensaver wallpaper", { WallpaperPack.flip(prefs, "saver") }) {
            prefs.edit().putBoolean(Keys.SAVER_FLIP, !WallpaperPack.flip(prefs, "saver")).apply()
        }

    private fun step(group: LinearLayout, name: String, key: String, min: Int, max: Int, default: Int, amount: Int) {
        lateinit var view: UiKit.StepperRow
        val refresh = { view.value.text = prefs.getInt(key, default).toString() }
        view = ui.stepper(name, prefs.getInt(key, default).toString(),
            Runnable { prefs.edit().putInt(key, maxOf(min, prefs.getInt(key, default) - amount)).apply(); refresh() },
            Runnable { prefs.edit().putInt(key, minOf(max, prefs.getInt(key, default) + amount)).apply(); refresh() })
        refreshers.add(refresh)
        ui.add(group, view)
    }

    private fun scaleRow(group: LinearLayout, name: String, key: String) =
        row(group, name, { SettingsValues.scale(prefs, key) }) { chooseScale(name, key) }

    private fun startupName(): String {
        val target = prefs.getString(Keys.STARTUP_APP, "") ?: ""
        if (target.isEmpty()) return "Off"
        return AppLookup.label(this, target) ?: "App unavailable"
    }

    private fun appName(role: String): String {
        val target = prefs.getString(Keys.APP_PREFIX + role, "") ?: ""
        if (target.isEmpty()) return "Default application"
        return AppLookup.label(this, target) ?: "App unavailable"
    }

    private fun chooseScale(title: String, key: String) {
        val values = intArrayOf(80, 90, 100, 110, 120)
        val current = values.indexOf(prefs.getInt(key, 100)).coerceAtLeast(0)
        ThemedDialogs.Builder(this).setTitle(title)
            .setSingleChoiceItems(arrayOf("80%", "90%", "100%", "110%", "120%"), current) { _, selected ->
                prefs.edit().putInt(key, values[selected]).apply()
                refreshValues()
                if (key == Keys.UI_SCALE_SETTINGS) Toast.makeText(this, "Reopen settings to apply", Toast.LENGTH_SHORT).show()
            }.show()
    }

    private fun chooseSaverMode() {
        val current = if (prefs.getString(Keys.SAVER_MODE, "clock") == "image") 1 else 0
        ThemedDialogs.Builder(this).setTitle("Display mode")
            .setSingleChoiceItems(arrayOf("Clock", "Wallpaper"), current) { dialog, selected ->
                dialog.dismiss()
                prefs.edit().putString(Keys.SAVER_MODE, if (selected == 1) "image" else "clock").apply()
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
        val current = values.indexOf(prefs.getString(Keys.STARTUP_SOURCE, "none")).coerceAtLeast(0)
        ThemedDialogs.Builder(this).setTitle("Startup media source (may play)")
            .setSingleChoiceItems(names, current) { dialog, selected ->
                dialog.dismiss()
                prefs.edit().putString(Keys.STARTUP_SOURCE, values[selected]).apply()
                refreshValues()
            }.setNegativeButton("Cancel", null).show()
    }

    private fun chooseAccent() {
        val labels = arrayOf("Theme default", "Red", "White", "Orange", "Green", "Blue", "Purple", "Custom color")
        val colors = intArrayOf(0, 0xffe60012.toInt(), Color.WHITE, 0xffffa040.toInt(), 0xff36ca87.toInt(), 0xff56a8ff.toInt(), 0xffb184ff.toInt())
        ThemedDialogs.Builder(this).setTitle("Accent color").setItems(labels) { _, selected ->
            if (selected == labels.lastIndex) {
                editAccent()
                return@setItems
            }
            if (selected == 0) prefs.edit().remove(Keys.ACCENT_COLOR).apply()
            else prefs.edit().putInt(Keys.ACCENT_COLOR, colors[selected]).apply()
            ThemeTokens.invalidate()
            ui.recolor(root)
            refreshValues()
        }.show()
    }

    private fun editAccent() {
        val input = EditText(this).apply {
            setSingleLine(true)
            hint = "#RRGGBB"
            if (prefs.contains(Keys.ACCENT_COLOR)) setText(SettingsValues.accent(prefs))
        }
        ThemedDialogs.Builder(this).setTitle("Accent color").setView(input)
            .setPositiveButton("Save") { _, _ ->
                val color = parseHexColor(input.text.toString())
                if (color == null) {
                    Toast.makeText(this, "Enter a color like #E60012", Toast.LENGTH_LONG).show()
                    return@setPositiveButton
                }
                prefs.edit().putInt(Keys.ACCENT_COLOR, color).apply()
                ThemeTokens.invalidate()
                ui.recolor(root)
                refreshValues()
            }.setNegativeButton("Cancel", null).show()
    }

    private fun editSolarLocation() {
        val fields = ui.column()
        fun coordinate(hint: String, key: String) = EditText(this).apply {
            setSingleLine(true)
            this.hint = hint
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL or InputType.TYPE_NUMBER_FLAG_SIGNED
            setText(prefs.getString(key, ""))
        }
        val latitude = coordinate("Latitude", Keys.SOLAR_LAT)
        val longitude = coordinate("Longitude", Keys.SOLAR_LON)
        fields.addView(latitude)
        fields.addView(longitude)
        ThemedDialogs.Builder(this).setTitle("Weather location")
            .setMessage("Used for weather and sunrise/sunset. Automatic uses the last known device location; manual coordinates use the device time zone.")
            .setView(fields)
            .setPositiveButton("Save") { _, _ ->
                val lat = latitude.text.toString().toDoubleOrNull()
                val lon = longitude.text.toString().toDoubleOrNull()
                if (lat == null || lon == null || !lat.isFinite() || !lon.isFinite() || lat !in -90.0..90.0 || lon !in -180.0..180.0) {
                    Toast.makeText(this, "Enter valid latitude and longitude", Toast.LENGTH_LONG).show()
                    return@setPositiveButton
                }
                prefs.edit().putBoolean(Keys.SOLAR_MANUAL, true).putString(Keys.SOLAR_LAT, lat.toString())
                    .putString(Keys.SOLAR_LON, lon.toString()).apply()
                refreshValues()
            }
            .setNeutralButton("Automatic location") { _, _ ->
                prefs.edit().putBoolean(Keys.SOLAR_MANUAL, false).apply()
                refreshValues()
            }
            .setNegativeButton("Cancel", null).show()
    }

    private fun chooseIconPack() {
        Background.load(this, {
            val packs = IconPacks.installed(this)
            Pair(arrayOf("Simple Icons (default)") + packs.map { it.loadLabel(packageManager).toString() },
                arrayOf("") + packs.map { it.activityInfo.packageName })
        }) { (labels, ids) ->
            val current = ids.indexOf(prefs.getString(Keys.ICON_PACK, "") ?: "").coerceAtLeast(0)
            ThemedDialogs.Builder(this).setTitle("Icon pack")
                .setSingleChoiceItems(labels, current) { _, index ->
                    prefs.edit().putString(Keys.ICON_PACK, ids[index]).apply()
                    refreshValues()
                }.setNegativeButton("Cancel", null).show()
        }
    }

    private fun appearance(mode: String) {
        startActivity(Intent(this, AppearancePickerActivity::class.java).putExtra("mode", mode))
    }

    private fun mediaAllowed(): Boolean {
        val value = Settings.Secure.getString(contentResolver, ENABLED_LISTENERS) ?: return false
        val expected = ComponentName(this, MediaAccess::class.java)
        return value.split(":").any { expected == ComponentName.unflattenFromString(it) }
    }

    private fun locationGranted() = checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    private fun isDefaultLauncher(): Boolean {
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        return packageManager.resolveActivity(home, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName == packageName
    }

    @Deprecated("Deprecated in Android")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == OfflineMapPanel.PICK_MAP && resultCode == RESULT_OK) {
            data?.data?.let { OfflineMapStore.importMap(this, it) }
            return
        }
        if (requestCode == REQUEST_PICK && resultCode == RESULT_OK && data != null) {
            val role = data.getStringExtra(AppPickerActivity.ROLE)
            val target = data.getStringExtra(AppPickerActivity.TARGET)
            if (role != null && target != null) {
                prefs.edit().putString(if (role == "startup") Keys.STARTUP_APP else Keys.APP_PREFIX + role, target).apply()
            }
        }
    }

    companion object {
        const val EXTRA_CATEGORY = "category"
        const val OFFLINE_MAPS = "OFFLINE MAPS"
        private const val REQUEST_PICK = 100
        private const val REQUEST_LOCATION = 42
        /** Settings.Secure key listing enabled notification listeners (not a public constant). */
        private const val ENABLED_LISTENERS = "enabled_notification_listeners"
        private val CATEGORIES = arrayOf("APPEARANCE", "PERSONALIZATION", "SCREENSAVER", OFFLINE_MAPS, "STARTUP",
            "APPLICATION SHORTCUTS", "PERMISSIONS", "DEVICE", "ABOUT")
        private val NESTED = setOf("WEATHER", "HOME", "DOCK", "APP DRAWER")
        private val SHORTCUT_ROLES = listOf("CarPlay" to "carplay", "Android Auto" to "androidauto",
            "Audio" to "audio", "Radio" to "radio", "Bluetooth" to "bluetooth")
    }
}

/** "#RRGGBB" for the RGB part of [color]. */
internal fun hexColor(color: Int): String = "#" + (color and 0xffffff).toString(16).uppercase(Locale.US).padStart(6, '0')

/** Parses "#RRGGBB" (exactly six hex digits) to an opaque colour, or null. Same result as Color.parseColor. */
internal fun parseHexColor(raw: String): Int? {
    val text = raw.trim()
    if (!text.matches(Regex("#[0-9a-fA-F]{6}"))) return null
    return 0xff000000.toInt() or text.substring(1).toInt(16)
}
