package app.flare.auto

import android.content.Context
import android.provider.Settings

internal object Motion {
    fun enabled(context: Context): Boolean {
        if (!ProfileStore.preferences(context).getBoolean("ui_animations", true)) return false
        return try { Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f }
        catch (_: SecurityException) { true }
    }
}
