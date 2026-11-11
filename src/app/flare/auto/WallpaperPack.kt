package app.flare.auto

import android.content.Context
import android.content.SharedPreferences
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable

/**
 * Wallpaper presets shared by Home, the app drawer and the screensaver. The list index is what preferences
 * store ("wallpaper_home_v2", "wallpaper_saver_v2", "wallpaper_drawer_v2"): append only, never reorder.
 */
internal object WallpaperPack {
    sealed class Preset(val name: String) {
        /** The theme backdrop with its accent-coloured wave. */
        class Accent(name: String) : Preset(name)
        class Solid(name: String, val color: Int) : Preset(name)
        /** Dark glow of [base], bright at the bottom (top when flipped). */
        class Glow(name: String, val base: Int, val deep: Boolean) : Preset(name)
        /** Vertical stops from the top (bottom when flipped). */
        class Stops(name: String, val colors: IntArray) : Preset(name)
        class Custom(name: String) : Preset(name)
    }

    val PRESETS: List<Preset> = listOf(
        Preset.Accent("Accent wave"),
        Preset.Solid("Black", 0xff000000.toInt()),
        Preset.Glow("Ember", 0xffff8177.toInt(), false),
        Preset.Glow("Wine", 0xffb12a5b.toInt(), false),
        Preset.Glow("Midnight", 0xffa1c4fd.toInt(), false),
        Preset.Glow("Forest", 0xff96e6a1.toInt(), false),
        Preset.Glow("Charcoal", 0xffa6c1ee.toInt(), false),
        Preset.Glow("Soft Charcoal", 0xffa6c1ee.toInt(), true),
        Preset.Solid("Dark Grey", 0xff242424.toInt()),
        Preset.Solid("Medium Grey", 0xff383838.toInt()),
        Preset.Solid("Warm Grey", 0xff393530.toInt()),
        Preset.Stops("Black to Warm Grey", stops(0x000000, 0x000000, 0x050505, 0x121110, 0x282521, 0x393530)),
        Preset.Stops("Dark Grey to Warm Grey", stops(0x242424, 0x393530, 0x393530)),
        Preset.Stops("Black to Warm Red", stops(0x000000, 0x000000, 0x080505, 0x191110, 0x362421, 0x523532)),
        Preset.Stops("Black to Burgundy", stops(0x000000, 0x000000, 0x070506, 0x171013, 0x302126, 0x49333a)),
        Preset.Stops("Black to Copper", stops(0x000000, 0x000000, 0x080605, 0x19130f, 0x35281f, 0x503a30)),
        Preset.Stops("Black to Home Ember", stops(0x000000, 0x000000, 0x080404, 0x1c0e0d, 0x3c1d1b, 0x5b2e2a)),
        Preset.Custom("Custom image"))

    /** Index of the user's own image; its file lives in CustomImages under the role's name. */
    val CUSTOM_INDEX = PRESETS.indexOfFirst { it is Preset.Custom }
    @JvmField val HOME_NAMES: Array<String> = PRESETS.map { it.name }.toTypedArray()
    @JvmField val SAVER_NAMES = HOME_NAMES
    /** Drawer selections below 0: the theme background (-2) or "same as Home" (-1). */
    private const val DRAWER_THEME = -2
    private const val DRAWER_MATCH_HOME = -1

    private fun stops(vararg rgb: Int) = IntArray(rgb.size) { rgb[it] or 0xff000000.toInt() }

    /** Colours of a [Preset.Glow]: [base] scaled down, a quarter of that in the middle, near-black at the end. */
    fun glowColors(base: Int, deep: Boolean): IntArray {
        val strength = if (deep) .075f else .36f
        val red = (((base shr 16) and 255) * strength).toInt()
        val green = (((base shr 8) and 255) * strength).toInt()
        val blue = ((base and 255) * strength).toInt()
        val color = 0xff000000.toInt() or (red shl 16) or (green shl 8) or blue
        val middle = 0xff000000.toInt() or ((red / 4) shl 16) or ((green / 4) shl 8) or (blue / 4)
        return intArrayOf(color, middle, if (deep) 0xff000000.toInt() else 0xff050505.toInt())
    }

    /** Whether gradients for [role] ("home", "saver", "drawer") are drawn upside down. The screensaver follows Home unless set. */
    fun flip(prefs: SharedPreferences, role: String): Boolean = when (role) {
        "saver" -> prefs.getBoolean(Keys.SAVER_FLIP, prefs.getBoolean(Keys.FLIP_GRADIENTS, false))
        "drawer" -> prefs.getBoolean(Keys.DRAWER_FLIP, false)
        else -> prefs.getBoolean(Keys.FLIP_GRADIENTS, false)
    }

    /** Index for installs that predate "wallpaper_*_v2" (older numbering of the Home and screensaver lists). */
    internal fun legacyIndex(p: SharedPreferences, saver: Boolean): Int {
        if (!saver) return when (p.getInt(Keys.LEGACY_HOME_WALLPAPER, 0)) {
            0 -> 0; 1 -> 2; 2 -> 3; 3 -> 4; 4 -> 5; 5 -> 6; 6 -> CUSTOM_INDEX; else -> 0
        }
        return when (val old = p.getInt(Keys.LEGACY_SAVER_WALLPAPER, 0)) {
            0 -> 1; in 1..5 -> old + 1; in 6..9 -> old - 4
            in 10..19 -> old - 3; 20 -> CUSTOM_INDEX; else -> 1
        }
    }

    fun select(context: Context, role: String, index: Int) {
        val key = when (role) { "saver" -> Keys.WALLPAPER_SAVER; "drawer" -> Keys.WALLPAPER_DRAWER; else -> Keys.WALLPAPER_HOME }
        ProfileStore.preferences(context).edit().putInt(key, index).apply()
    }

    fun drawerSelection(context: Context): Int {
        val p = ProfileStore.preferences(context)
        if (p.contains(Keys.WALLPAPER_DRAWER)) return p.getInt(Keys.WALLPAPER_DRAWER, DRAWER_THEME).coerceIn(DRAWER_THEME, CUSTOM_INDEX)
        return if (p.getBoolean(Keys.LEGACY_DRAWER_CUSTOM, false)) CUSTOM_INDEX
            else if (p.getBoolean(Keys.LEGACY_DRAWER_WALLPAPER, false)) DRAWER_MATCH_HOME else DRAWER_THEME
    }

    fun drawerName(context: Context): String = when (val selection = drawerSelection(context)) {
        DRAWER_THEME -> "Theme background"; DRAWER_MATCH_HOME -> "Match Home"; else -> HOME_NAMES[selection]
    }

    fun drawer(context: Context, theme: ThemeTokens): Drawable = when (val selection = drawerSelection(context)) {
        DRAWER_THEME -> ColorDrawable(theme.background)
        DRAWER_MATCH_HOME -> home(context, theme)
        else -> previewRole(context, theme, "drawer", selection)
    }

    fun index(context: Context, saver: Boolean): Int {
        val prefs = ProfileStore.preferences(context)
        val key = if (saver) Keys.WALLPAPER_SAVER else Keys.WALLPAPER_HOME
        return if (prefs.contains(key)) prefs.getInt(key, 0).coerceIn(0, CUSTOM_INDEX) else legacyIndex(prefs, saver)
    }

    fun name(context: Context, saver: Boolean): String = HOME_NAMES[index(context, saver)]
    fun home(context: Context, theme: ThemeTokens): Drawable = previewRole(context, theme, "home", index(context, false))
    fun saver(context: Context, theme: ThemeTokens): Drawable = previewRole(context, theme, "saver", index(context, true))
    fun preview(context: Context, theme: ThemeTokens, saver: Boolean, index: Int): Drawable =
        previewRole(context, theme, if (saver) "saver" else "home", index)

    fun previewRole(context: Context, theme: ThemeTokens, role: String, index: Int): Drawable =
        when (val preset = PRESETS[index.coerceIn(0, CUSTOM_INDEX)]) {
            is Preset.Accent -> ThemeBackdrop(theme)
            is Preset.Solid -> ColorDrawable(preset.color)
            is Preset.Glow -> GradientDrawable(
                if (flip(ProfileStore.preferences(context), role)) GradientDrawable.Orientation.TOP_BOTTOM else GradientDrawable.Orientation.BOTTOM_TOP,
                glowColors(preset.base, preset.deep))
            is Preset.Stops -> GradientDrawable(
                if (flip(ProfileStore.preferences(context), role)) GradientDrawable.Orientation.BOTTOM_TOP else GradientDrawable.Orientation.TOP_BOTTOM,
                preset.colors)
            is Preset.Custom -> CustomImages.load(context, when (role) {
                "saver" -> "saverwall"; "drawer" -> "drawer"; else -> "home"
            }) ?: ColorDrawable(theme.background)
        }
}
