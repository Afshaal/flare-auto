package app.flare.auto

import android.content.ContentResolver
import android.provider.Settings

/**
 * Every package / activity name the launcher knows about: the stock head-unit firmware it was built on (the
 * "bw" family on the Civic unit), projection apps and a few third-party apps it pre-seeds or styles.
 * Nothing else in the code base names a package. On a device without these apps the launcher simply
 * does not show or use them.
 */
internal object KnownApps {
    // Stock head-unit firmware
    const val VENDOR_PREFIX = "com.bw."
    private const val MEDIA_PLAYER = "com.bw.mediaplayer"
    private const val BT_AUDIO = "com.bw.mediaplayer.ui.BtActivity"
    private const val RADIO = "com.bw.mediaplayer.ui.RadioActivity"
    private const val PHONE = "com.bw.phone"
    private const val PHONE_MAIN = "com.bw.phone.MainActivity"
    private const val CAR_SETTINGS = "com.bw.carsetting"
    private const val IPOD = "com.bw.ipod"
    private const val ADAS_CAMERA = "com.bw.adas"
    private const val SOURCE_SERVICE = "com.bw.source"
    /** Settings.System key in which the firmware publishes the active audio source. */
    private const val CURRENT_SOURCE_SETTING = "bw_current_source_name"

    // Projection
    val CARPLAY = arrayOf("com.shihab.diplay", "com.shihab.diplay.hudtest")
    val ANDROID_AUTO = arrayOf("com.andrerinas.headunitrevived")
    val PROJECTION: Set<String> = (CARPLAY + ANDROID_AUTO).toSet()

    // Android / third-party packages the drawer styles
    private const val FILES = "me.zhanghai.android.files"
    private const val BROWSER = "org.mozilla.focus"
    private const val KEYBOARD = "rkr.simplekeyboard.inputmethod"
    private const val TERMINAL = "com.termux"
    private const val SIM_TOOLKIT_PREFIX = "com.android.stk"
    private const val SYSTEM_SETTINGS = "com.android.settings"
    private const val BLUETOOTH_STACK = "com.android.bluetooth"
    private const val GALLERY = "com.android.gallery3d"
    private val CAMERAS = setOf("com.android.camera2", ADAS_CAMERA)
    /** Single-purpose apps from the earlier multi-app version; never listed in the drawer. */
    private val LEGACY_HIDDEN = setOf("local.civic.dash", "local.civic.btmusic", "local.civic.radio")
    private const val LEGACY_BUTTON = "local.civic.button"

    /** A stock-firmware screen opened by explicit class. */
    class Screen(val packageName: String, val className: String)
    val AUDIO_SCREEN = Screen(MEDIA_PLAYER, BT_AUDIO)
    val RADIO_SCREEN = Screen(MEDIA_PLAYER, RADIO)
    val PHONE_SCREEN = Screen(PHONE, PHONE_MAIN)

    /** Drawer tiles listed before the installed apps (shown only when installed, except the launcher's own settings). */
    class Shortcut(val label: String, val glyph: String, val packageName: String?, val className: String?, val internal: Boolean = false)
    val DRAWER_SHORTCUTS = listOf(
        Shortcut("CarPlay", "carplay", CARPLAY[0], null),
        Shortcut("Android Auto", "androidauto", ANDROID_AUTO[0], null),
        Shortcut("Audio", "audio", MEDIA_PLAYER, BT_AUDIO),
        Shortcut("Radio", "radio", MEDIA_PLAYER, RADIO),
        Shortcut("Bluetooth", "bluetooth", PHONE, PHONE_MAIN),
        Shortcut("Files", "file", FILES, null),
        Shortcut("Browser", "browser", BROWSER, null),
        Shortcut("Launcher Settings", "settings", null, null, internal = true),
        Shortcut("Car settings", "carsettings", CAR_SETTINGS, null))

    fun hiddenFromDrawer(packageName: String) = packageName in LEGACY_HIDDEN

    /**
     * Drawer label, glyph and colour key for an installed app. A built-in glyph is drawn for the stock head-unit
     * screens and system tools; "original" means the app's own installed icon (or icon pack / custom icon).
     */
    class Style(val label: String, val glyph: String, val color: String)
    fun drawerStyle(label: String, packageName: String): Style = when {
        label.equals("Basic Settings", ignoreCase = true) -> Style("WiFi Settings", "wifi", "wifi")
        packageName == SYSTEM_SETTINGS -> Style("System Settings", "systemsettings", "default")
        label.equals("Customization", ignoreCase = true) -> Style(label, "custom", "custom")
        packageName in CAMERAS -> Style(label, "camera", "camera")
        packageName == GALLERY || label.equals("Gallery", true) || label.equals("Photos", true) -> Style(label, "gallery", "camera")
        packageName == KEYBOARD -> Style("Simple Keyboard", "keyboard", "keyboard")
        label.equals("Files", true) -> Style(label, "original", "file")
        packageName == IPOD -> Style(label, "ipod", "ipod")
        packageName == LEGACY_BUTTON -> Style(label, "playpause", "playpause")
        packageName.startsWith(SIM_TOOLKIT_PREFIX) -> Style(label, "sim", "sim")
        packageName == TERMINAL -> Style(label, "terminal", "terminal")
        label.equals("User Guide", true) -> Style(label, "guide", "guide")
        else -> Style(label, "original", "default")
    }

    /** Preference role ("app_<role>") that may redirect a stock screen to a user-chosen app. */
    fun overrideRole(screen: Screen): String = when (screen) {
        PHONE_SCREEN -> "bluetooth"
        RADIO_SCREEN -> "radio"
        AUDIO_SCREEN -> "audio"
        else -> ""
    }

    /** Preference key that may redirect a projection button to a user-chosen app. */
    fun projectionOverrideKey(packages: Array<String>): String =
        if (packages[0] == CARPLAY[0]) Keys.APP_CARPLAY else Keys.APP_ANDROIDAUTO

    /** Short source name shown above the media card, or null when the package has no special name. */
    fun mediaSourceName(resolver: ContentResolver, packageName: String): String? = when (packageName) {
        SOURCE_SERVICE -> when (Settings.System.getString(resolver, CURRENT_SOURCE_SETTING)) {
            "RADIO" -> "RADIO"
            "MUSIC_BT" -> "BLUETOOTH"
            else -> ""
        }
        BLUETOOTH_STACK -> "BLUETOOTH"
        ANDROID_AUTO[0] -> "ANDROID AUTO"
        in CARPLAY -> "DIPLAY"
        else -> null
    }
}

/**
 * Vehicle label defaults. Empty since 1.2.0 (generic build): a new install shows no vehicle name or badge until the
 * driver sets one. Installs from before keep what they showed (see [ProfileStore.keepVehicleLabels]).
 */
internal object Defaults {
    /** Vehicle name on Home and the screensaver (preference "brand_text"). */
    const val VEHICLE_NAME = ""
    /** Trim badge text (preference "badge_text"). */
    const val TRIM_BADGE = ""
    /** What 1.1.0 and earlier showed when nothing was saved; written into existing profiles once. */
    const val LEGACY_VEHICLE_NAME = "CIVIC"
    const val LEGACY_TRIM_BADGE = "RS"
    /** Badge text that is drawn with the stylised badge glyph instead of plain text. */
    const val TRIM_BADGE_GLYPH = "RS"
}

/** Home layout limits shared by Home and the editors. */
internal object LayoutPrefs {
    const val SPLIT_MIN = 35
    const val SPLIT_MAX = 65
    const val SPLIT_DEFAULT = 58
    const val MAX_APP_SHORTCUTS = 8

    fun split(prefs: android.content.SharedPreferences): Int =
        prefs.getInt(Keys.SPLIT, SPLIT_DEFAULT).coerceIn(SPLIT_MIN, SPLIT_MAX)

    /** Card for the left/right half in the pre-grid layout (older installs that never saved dashboard pages). */
    fun legacyCard(prefs: android.content.SharedPreferences, left: Boolean): String {
        val key = if (left) Keys.LAYOUT_LEFT else Keys.LAYOUT_RIGHT
        if (prefs.contains(key)) return prefs.getString(key, if (left) "clock" else "media") ?: ""
        return if (left) { if (prefs.getBoolean(Keys.PROJECTION, true)) "clock" else "hidden" }
        // No card chosen yet: the default media card is the Album player (an explicit "media" choice is kept above).
        else { if (prefs.getBoolean(Keys.MEDIA, true)) "media_album" else "hidden" }
    }
}
