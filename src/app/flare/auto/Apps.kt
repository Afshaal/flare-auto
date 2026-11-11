package app.flare.auto

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.util.LruCache
import java.text.Collator
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger

/** A launcher activity. [target] is the flattened component string stored in preferences. */
internal class LaunchableApp(val label: String, val packageName: String, val className: String) {
    val target: String = ComponentName(packageName, className).flattenToString()
}

/**
 * Launcher activities installed on the device, queried once and cached until a package is added, removed or
 * changed (or the locale changes). Sorted by label exactly like ResolveInfo.DisplayNameComparator.
 * [apps] does package-manager work: call it off the main thread.
 */
internal object AppCatalog {
    @Volatile private var cached: List<LaunchableApp>? = null
    @Volatile private var cachedLocale: Locale? = null
    private val generation = AtomicInteger()
    @Volatile private var listening = false

    fun apps(context: Context): List<LaunchableApp> {
        val app = context.applicationContext
        listen(app)
        val locale = Locale.getDefault()
        cached?.let { if (cachedLocale == locale) return it }
        val start = generation.get()
        val pm = app.packageManager
        val collator = Collator.getInstance()
        val list = pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
            .map { LaunchableApp(it.loadLabel(pm).toString(), it.activityInfo.packageName, it.activityInfo.name) }
            .sortedWith { a, b -> collator.compare(a.label, b.label) }
        if (generation.get() == start) { cached = list; cachedLocale = locale }
        return list
    }

    /** Launcher activities other than this app's own. */
    fun others(context: Context): List<LaunchableApp> = apps(context).filter { it.packageName != context.packageName }

    fun invalidate() { generation.incrementAndGet(); cached = null }

    @Synchronized private fun listen(app: Context) {
        if (listening) return
        listening = true
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) { invalidate(); AppIcons.clear() }
        }
        app.registerReceiver(receiver, IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED); addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED); addAction(Intent.ACTION_PACKAGE_REPLACED)
            addDataScheme("package")
        })
        app.registerReceiver(receiver, IntentFilter().apply {
            addAction(Intent.ACTION_EXTERNAL_APPLICATIONS_AVAILABLE); addAction(Intent.ACTION_EXTERNAL_APPLICATIONS_UNAVAILABLE)
        })
    }
}

/** App icons by stored component, loaded lazily off the main thread into one small shared cache (~4 MB). */
internal object AppIcons {
    private val cache = object : LruCache<String, Drawable>(4 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Drawable): Int =
            (value as? BitmapDrawable)?.bitmap?.allocationByteCount ?: 16 * 1024
    }

    /** Cached icon, or a placeholder drawable that shows the icon once it has loaded. */
    fun get(context: Context, target: String): Drawable {
        synchronized(cache) { cache.get(target) }?.let { return it.constantState?.newDrawable(context.resources) ?: it }
        return AsyncImages.load(context) { app ->
            AppLookup.component(target)?.let { app.packageManager.getActivityIcon(it) }
                ?.also { icon -> synchronized(cache) { cache.put(target, icon) } }
        }
    }

    fun clear() { synchronized(cache) { cache.evictAll() } }
}

/** Labels and launching for components stored as "package/class" strings. */
internal object AppLookup {
    fun component(target: String?): ComponentName? =
        target?.takeIf { it.isNotEmpty() }?.let { ComponentName.unflattenFromString(it) }

    /** The activity's label, or null when the component is malformed or no longer installed. */
    fun label(context: Context, target: String?): String? {
        val component = component(target) ?: return null
        val pm = context.packageManager
        return try { pm.getActivityInfo(component, 0).loadLabel(pm).toString() } catch (_: Exception) { null }
    }

    fun launcherIntent(component: ComponentName): Intent =
        Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setComponent(component)

    /** Starts a stored component as a launcher would; false when it cannot be started. */
    fun launch(context: Context, target: String?): Boolean =
        component(target)?.let { start(context, launcherIntent(it)) } ?: false

    fun start(context: Context, intent: Intent?): Boolean {
        if (intent == null) return false
        return try { context.startActivity(intent); true } catch (_: Exception) { false }
    }

    /** True when [packageName] (and [className], if given) can be opened from a launcher. */
    fun installed(context: Context, packageName: String, className: String?): Boolean = try {
        if (className != null) { context.packageManager.getActivityInfo(ComponentName(packageName, className), 0); true }
        else context.packageManager.getLaunchIntentForPackage(packageName) != null
    } catch (_: Exception) { false }
}
