package app.flare.auto

import android.content.SharedPreferences

internal object DockOrder {
    @JvmField val NAMES = arrayOf("Audio", "Radio", "Bluetooth", "Screen off", "Apps")

    @JvmStatic
    fun read(preferences: SharedPreferences): IntArray {
        val order = IntArray(5) { it }
        return try {
            val values = preferences.getString("dock_order", "0,1,2,3,4")!!.split(",")
            val used = BooleanArray(5)
            for (index in 0 until 5) {
                val value = values[index].toInt()
                if (value !in 0..4 || used[value]) throw IllegalArgumentException()
                used[value] = true
                order[index] = value
            }
            order
        } catch (_: Exception) {
            intArrayOf(0, 1, 2, 3, 4)
        }
    }

    @JvmStatic
    fun save(preferences: SharedPreferences, order: IntArray) {
        preferences.edit().putString("dock_order", order.joinToString(",")).apply()
    }
}
