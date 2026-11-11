package app.flare.auto

import android.content.Context

internal class ThemeTokens private constructor(values: IntArray, chosenAccent: Int) {
    @JvmField val background = values[0]
    @JvmField val card = values[1]
    @JvmField val cardBorder = values[2]
    @JvmField val textPrimary = values[3]
    @JvmField val textSecondary = values[4]
    @JvmField val accent = chosenAccent

    companion object {
        @JvmField
        val NAMES = arrayOf("Pitch Black", "Dark Navy", "Warm Charcoal", "Burgundy Black", "Dark Forest", "Daylight Silver", "Warm Ivory", "Cool Pearl")

        @JvmField
        val VALUES = arrayOf(
            intArrayOf(0xff08090a.toInt(), 0xff121417.toInt(), 0xff1f2228.toInt(), -1, 0xff8a909a.toInt(), 0xffe60012.toInt()),
            intArrayOf(0xff0a0e16.toInt(), 0xff121826.toInt(), 0x1affffff, 0xfff2f5fa.toInt(), 0xff8a909a.toInt(), 0xffe60012.toInt()),
            intArrayOf(0xff100e0e.toInt(), 0xff1a1717.toInt(), 0x1affffff, 0xfff2f5fa.toInt(), 0xff8a909a.toInt(), 0xffe60012.toInt()),
            intArrayOf(0xff120a0c.toInt(), 0xff1d1013.toInt(), 0x1affffff, 0xfff2f5fa.toInt(), 0xff8a909a.toInt(), 0xffe60012.toInt()),
            intArrayOf(0xff08100e.toInt(), 0xff101b18.toInt(), 0x1affffff, 0xfff2f5fa.toInt(), 0xff8a909a.toInt(), 0xff2dd4bf.toInt()),
            intArrayOf(0xffe5e7eb.toInt(), 0xfff7f8fa.toInt(), 0xffc6cbd2.toInt(), 0xff202226.toInt(), 0xff606773.toInt(), 0xffc5162e.toInt()),
            intArrayOf(0xffe7e3dc.toInt(), 0xfff6f2eb.toInt(), 0xffcec6bb.toInt(), 0xff292621.toInt(), 0xff70685d.toInt(), 0xffb53632.toInt()),
            intArrayOf(0xffe1e8ec.toInt(), 0xfff2f7fa.toInt(), 0xffbecbd4.toInt(), 0xff202c34.toInt(), 0xff5e707d.toInt(), 0xff32647d.toInt())
        )

        fun preview(p:android.content.SharedPreferences):ThemeTokens {
            val index=NAMES.indexOf(p.getString(Keys.THEME,NAMES[0])).coerceAtLeast(0)
            return ThemeTokens(VALUES[index],p.getInt(Keys.ACCENT_COLOR,VALUES[index][5]))
        }
        private var cached: ThemeTokens? = null
        private var cachedMinute = -1L
        private var cachedIndex = -1

        fun mode(context: Context): String = ProfileStore.preferences(context).getString(Keys.THEME_MODE, "dark") ?: "dark"

        fun activeIndex(context: Context): Int = effectiveIndex(context)

        private fun effectiveIndex(context: Context): Int {
            val prefs = ProfileStore.preferences(context)
            val light = when (mode(context)) {
                "light" -> true
                "auto" -> {
                    val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
                    val start = prefs.getInt(Keys.THEME_DAY_HOUR, 7)
                    val end = prefs.getInt(Keys.THEME_NIGHT_HOUR, 19)
                    if (start < end) hour in start until end else if (start > end) hour >= start || hour < end else false
                }
                else -> false
            }
            return if (light) prefs.getInt(Keys.LIGHT_THEME_INDEX, 5).coerceIn(5, 7)
                else prefs.getInt(Keys.DARK_THEME_INDEX, index(context).coerceIn(0, 4)).coerceIn(0, 4)
        }

        @JvmStatic
        fun index(context: Context): Int {
            val name = ProfileStore.preferences(context).getString(Keys.THEME, NAMES[0])
            return NAMES.indexOf(name).takeIf { it >= 0 } ?: 0
        }

        @JvmStatic
        @Synchronized
        fun get(context: Context): ThemeTokens {
            if(context is EditorContext) return preview(context.draft)
            val minute = System.currentTimeMillis() / 60000
            if (cached != null && cachedMinute == minute) return cached!!
            val selected = effectiveIndex(context)
            cachedMinute = minute
            if (cached != null && cachedIndex == selected) return cached!!
            cachedIndex = selected
            val values = VALUES[selected]
            return ThemeTokens(
                values,
                ProfileStore.preferences(context).getInt(Keys.ACCENT_COLOR, values[5])
            ).also { cached = it }
        }

        @JvmStatic
        @Synchronized
        fun invalidate() {
            cached = null
        }

        @JvmStatic
        @Synchronized
        fun select(context: Context, index: Int) {
            ProfileStore.preferences(context).edit()
                .putString(Keys.THEME, NAMES[Math.floorMod(index, NAMES.size)])
                .putInt(if (index >= 5) "light_theme_index" else "dark_theme_index", index)
                .putString(Keys.THEME_MODE, if (mode(context) == "auto") "auto" else if (index >= 5) "light" else "dark")
                .apply()
            invalidate()
        }
    }
}
