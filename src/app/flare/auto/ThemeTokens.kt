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
        val NAMES = arrayOf("Pitch Black", "Dark Navy", "Warm Charcoal", "Burgundy Black", "Dark Forest")

        @JvmField
        val VALUES = arrayOf(
            intArrayOf(0xff08090a.toInt(), 0xff121417.toInt(), 0xff1f2228.toInt(), -1, 0xff8a909a.toInt(), 0xffe60012.toInt()),
            intArrayOf(0xff0a0e16.toInt(), 0xff121826.toInt(), 0x1affffff, 0xfff2f5fa.toInt(), 0xff8a909a.toInt(), 0xffe60012.toInt()),
            intArrayOf(0xff100e0e.toInt(), 0xff1a1717.toInt(), 0x1affffff, 0xfff2f5fa.toInt(), 0xff8a909a.toInt(), 0xffe60012.toInt()),
            intArrayOf(0xff120a0c.toInt(), 0xff1d1013.toInt(), 0x1affffff, 0xfff2f5fa.toInt(), 0xff8a909a.toInt(), 0xffe60012.toInt()),
            intArrayOf(0xff08100e.toInt(), 0xff101b18.toInt(), 0x1affffff, 0xfff2f5fa.toInt(), 0xff8a909a.toInt(), 0xff2dd4bf.toInt())
        )

        private var cached: ThemeTokens? = null

        @JvmStatic
        fun index(context: Context): Int {
            val name = context.getSharedPreferences("home", 0).getString("theme", NAMES[0])
            return NAMES.indexOf(name).takeIf { it >= 0 } ?: 0
        }

        @JvmStatic
        @Synchronized
        fun get(context: Context): ThemeTokens {
            cached?.let { return it }
            val values = VALUES[index(context)]
            return ThemeTokens(
                values,
                context.getSharedPreferences("home", 0).getInt("accent_color", values[5])
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
            context.getSharedPreferences("home", 0).edit()
                .putString("theme", NAMES[Math.floorMod(index, NAMES.size)])
                .apply()
            invalidate()
        }
    }
}
