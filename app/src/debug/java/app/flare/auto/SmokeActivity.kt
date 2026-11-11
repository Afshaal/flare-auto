package app.flare.auto

import android.app.Activity
import android.content.Intent
import android.os.Bundle

/**
 * Debug builds only: opens one internal screen so it can be smoke-tested with
 * `adb shell am start -W -n app.flare.auto/.SmokeActivity --es open <name>`.
 */
class SmokeActivity : Activity() {
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        val target = when (intent.getStringExtra("open")) {
            "apps" -> AppsActivity::class.java
            "settings" -> SettingsActivity::class.java
            "editor" -> GridEditorActivity::class.java
            "saver" -> ScreenSaverActivity::class.java
            "picker" -> AppPickerActivity::class.java
            "hidden" -> HiddenAppsActivity::class.java
            "appearance" -> AppearancePickerActivity::class.java
            "widgets" -> AppWidgetActivity::class.java
            "map" -> OfflineMapActivity::class.java
            "nav" -> NavigationCheckActivity::class.java
            else -> HomeActivity::class.java
        }
        startActivity(Intent(this, target).apply { intent.extras?.let { putExtras(it) } })
        finish()
    }
}
