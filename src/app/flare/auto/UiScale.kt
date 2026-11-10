package app.flare.auto

import android.content.Context
import android.content.res.Configuration

internal object UiScale {
    fun wrap(base: Context, key: String): Context {
        val scale = base.getSharedPreferences("home", 0).getInt(key, 100).coerceIn(80, 120)
        if (scale == 100) return base
        val config = Configuration(base.resources.configuration)
        config.densityDpi = (base.resources.displayMetrics.densityDpi * scale / 100f).toInt().coerceAtLeast(1)
        return base.createConfigurationContext(config)
    }
}
