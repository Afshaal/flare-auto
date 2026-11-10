package app.flare.auto

import android.content.Context
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable

internal object WallpaperPack {
    @JvmField val HOME_NAMES = arrayOf(
        "Original red wave", "Black", "Ember", "Wine", "Midnight", "Forest", "Charcoal",
        "Soft Charcoal", "Dark Grey", "Medium Grey", "Warm Grey", "Black to Warm Grey",
        "Dark Grey to Warm Grey", "Black to Warm Red", "Black to Burgundy", "Black to Copper",
        "Black to Home Ember", "Custom image"
    )
    @JvmField val SAVER_NAMES = HOME_NAMES
    private const val KEY_HOME = "wallpaper_home_v2"
    private const val KEY_SAVER = "wallpaper_saver_v2"
    private const val KEY_DRAWER = "wallpaper_drawer_v2"

    private fun legacyIndex(context: Context, saver: Boolean): Int {
        val p = context.getSharedPreferences("home", 0)
        if (!saver) return when (p.getInt("home_wallpaper", 0)) {
            0 -> 0; 1 -> 2; 2 -> 3; 3 -> 4; 4 -> 5; 5 -> 6; 6 -> HOME_NAMES.lastIndex; else -> 0
        }
        return when (val old = p.getInt("saver_wallpaper", 0)) {
            0 -> 1; in 1..5 -> old + 1; in 6..9 -> old - 4
            in 10..19 -> old - 3; 20 -> HOME_NAMES.lastIndex; else -> 1
        }
    }

    fun select(context: Context, role: String, index: Int) {
        val key = when (role) { "saver" -> KEY_SAVER; "drawer" -> KEY_DRAWER; else -> KEY_HOME }
        context.getSharedPreferences("home", 0).edit().putInt(key, index).apply()
    }

    fun drawerSelection(context: Context): Int {
        val p = context.getSharedPreferences("home", 0)
        if (p.contains(KEY_DRAWER)) return p.getInt(KEY_DRAWER, -2).coerceIn(-2, HOME_NAMES.lastIndex)
        return if (p.getBoolean("drawer_custom", false)) HOME_NAMES.lastIndex
            else if (p.getBoolean("drawer_wallpaper", false)) -1 else -2
    }

    fun drawerName(context: Context): String = when (val selection = drawerSelection(context)) {
        -2 -> "Theme background"; -1 -> "Match Home"; else -> HOME_NAMES[selection]
    }

    fun drawer(context: Context, theme: ThemeTokens): Drawable = when (val selection = drawerSelection(context)) {
        -2 -> ColorDrawable(theme.background)
        -1 -> home(context, theme)
        else -> previewRole(context, theme, "drawer", selection)
    }

    private val source = intArrayOf(
        0,
        0xffff8177.toInt(),
        0xffb12a5b.toInt(),
        0xffa1c4fd.toInt(),
        0xff96e6a1.toInt(),
        0xffa6c1ee.toInt()
    )

    @JvmStatic
    fun index(context: Context, saver: Boolean): Int {
        val prefs = context.getSharedPreferences("home", 0)
        val key = if (saver) KEY_SAVER else KEY_HOME
        return if (prefs.contains(key)) prefs.getInt(key, 0).coerceIn(0, HOME_NAMES.lastIndex) else legacyIndex(context, saver)
    }

    @JvmStatic fun name(context: Context, saver: Boolean): String = HOME_NAMES[index(context, saver)]
    @JvmStatic fun home(context: Context, theme: ThemeTokens): Drawable = previewRole(context, theme, "home", index(context, false))
    @JvmStatic fun saver(context: Context, theme: ThemeTokens): Drawable = previewRole(context, theme, "saver", index(context, true))
    @JvmStatic fun preview(context: Context, theme: ThemeTokens, saver: Boolean, index: Int): Drawable =
        previewRole(context, theme, if (saver) "saver" else "home", index)

    fun previewRole(context: Context, theme: ThemeTokens, role: String, index: Int): Drawable {
        val id = index.coerceIn(0, HOME_NAMES.lastIndex)
        if (id == 0) return ThemeBackdrop(theme)
        if (id == HOME_NAMES.lastIndex) return CustomImages.load(context, when (role) {
            "saver" -> "saverwall"; "drawer" -> "drawer"; else -> "home"
        }) ?: ColorDrawable(theme.background)
        if (id == 1) return ColorDrawable(0xff000000.toInt())
        if (id in 2..6) return gradient(context, id - 1, role)
        val legacy = id + 3
        return saverAt(context, legacy, role)
    }

    private fun flip(context: Context, role: String): Boolean {
        val p = context.getSharedPreferences("home", 0)
        return when (role) {
            "saver" -> p.getBoolean("saver_flip", p.getBoolean("flip_gradients", false))
            "drawer" -> p.getBoolean("drawer_flip", false)
            else -> p.getBoolean("flip_gradients", false)
        }
    }

    private fun saverAt(context: Context, index: Int, role: String): Drawable {
        if (index == 0) return ColorDrawable(0xff000000.toInt())
        if (index == 11) return ColorDrawable(0xff242424.toInt())
        if (index == 12) return ColorDrawable(0xff383838.toInt())
        if (index == 13) return ColorDrawable(0xff393530.toInt())
        val colors = when (index) {
            14 -> intArrayOf(0xff000000.toInt(), 0xff000000.toInt(), 0xff050505.toInt(), 0xff121110.toInt(), 0xff282521.toInt(), 0xff393530.toInt())
            15 -> intArrayOf(0xff242424.toInt(), 0xff393530.toInt(), 0xff393530.toInt())
            16 -> intArrayOf(0xff000000.toInt(), 0xff000000.toInt(), 0xff080505.toInt(), 0xff191110.toInt(), 0xff362421.toInt(), 0xff523532.toInt())
            17 -> intArrayOf(0xff000000.toInt(), 0xff000000.toInt(), 0xff070506.toInt(), 0xff171013.toInt(), 0xff302126.toInt(), 0xff49333a.toInt())
            18 -> intArrayOf(0xff000000.toInt(), 0xff000000.toInt(), 0xff080605.toInt(), 0xff19130f.toInt(), 0xff35281f.toInt(), 0xff503a30.toInt())
            19 -> intArrayOf(0xff000000.toInt(), 0xff000000.toInt(), 0xff080404.toInt(), 0xff1c0e0d.toInt(), 0xff3c1d1b.toInt(), 0xff5b2e2a.toInt())
            else -> return gradient(context, 5, role, true)
        }
        return GradientDrawable(if (flip(context, role)) GradientDrawable.Orientation.BOTTOM_TOP else GradientDrawable.Orientation.TOP_BOTTOM, colors)
    }

    private fun gradient(context: Context, index: Int, role: String, deep: Boolean = false): Drawable {
        val base = source[index]
        val strength = if (deep) .075f else .36f
        val red = (((base shr 16) and 255) * strength).toInt()
        val green = (((base shr 8) and 255) * strength).toInt()
        val blue = ((base and 255) * strength).toInt()
        val color = 0xff000000.toInt() or (red shl 16) or (green shl 8) or blue
        val middle = 0xff000000.toInt() or ((red / 4) shl 16) or ((green / 4) shl 8) or (blue / 4)
        return GradientDrawable(
            if (flip(context, role)) GradientDrawable.Orientation.TOP_BOTTOM else GradientDrawable.Orientation.BOTTOM_TOP,
            intArrayOf(color, middle, if (deep) 0xff000000.toInt() else 0xff050505.toInt())
        )
    }
}
