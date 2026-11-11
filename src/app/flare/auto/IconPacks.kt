package app.flare.auto

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ResolveInfo
import android.content.res.Resources
import android.graphics.drawable.Drawable
import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.InputStream
import java.util.ArrayList
import java.util.Collections
import java.util.HashMap
import java.util.LinkedHashMap

internal object IconPacks {
    const val KEY = Keys.ICON_PACK

    @JvmField
    val ACTIONS = arrayOf(
        "com.novalauncher.THEME",
        "org.adw.launcher.THEMES",
        "com.anddoes.launcher.THEME",
        "com.gau.go.launcherex.theme",
        "com.teslacoilsw.launcher.THEME"
    )

    @JvmStatic
    fun installed(context: Context): ArrayList<ResolveInfo> {
        val packageManager = context.packageManager
        val found = LinkedHashMap<String, ResolveInfo>()
        for (action in ACTIONS) {
            for (resolveInfo in packageManager.queryIntentActivities(Intent(action), 0)) {
                resolveInfo.activityInfo?.let { found[it.packageName] = resolveInfo }
            }
        }
        val installed = ArrayList(found.values)
        Collections.sort(installed, ResolveInfo.DisplayNameComparator(packageManager))
        return installed
    }

    @JvmStatic
    fun name(context: Context): String {
        val packageName = ProfileStore.preferences(context).getString(KEY, "") ?: ""
        if (packageName.isEmpty()) return "Simple Icons"
        return try {
            context.packageManager.getApplicationLabel(
                context.packageManager.getApplicationInfo(packageName, 0)
            ).toString()
        } catch (_: Exception) {
            "Pack unavailable"
        }
    }

    class Pack(private val resources: Resources, private val packageName: String) {
        private val drawableIds = HashMap<String, Int>()
        private val components = HashMap<String, String>()
        private val packages = HashMap<String, String>()

        fun lookup(context: Context, app: String, className: String?): String? {
            var activityName = className
            if (activityName == null) {
                try {
                    activityName = context.packageManager.getLaunchIntentForPackage(app)?.component?.className
                } catch (_: Exception) {
                }
            }
            val exact = activityName?.let {
                components[ComponentName(app, it).flattenToString()]
            }
            return exact ?: packages[app]
        }

        @Synchronized
        fun drawableId(name: String): Int {
            drawableIds[name]?.let { return it }
            var id = resources.getIdentifier(name, "drawable", packageName)
            if (id == 0) id = resources.getIdentifier(name, "mipmap", packageName)
            drawableIds[name] = id
            return id
        }

        fun drawable(name: String): Drawable? {
            return try {
                val id = drawableId(name)
                if (id == 0) null else resources.getDrawable(id, null)
            } catch (_: RuntimeException) {
                null
            }
        }

        internal fun addMapping(component: String, packageName: String, drawableName: String) {
            components[component] = drawableName
            if (!packages.containsKey(packageName)) packages[packageName] = drawableName
        }
    }

    @JvmStatic
    fun load(context: Context): Pack? {
        val packageName = ProfileStore.preferences(context).getString(KEY, "") ?: ""
        if (packageName.isEmpty()) return null
        return try {
            val resources = context.packageManager.getResourcesForApplication(packageName)
            val pack = Pack(resources, packageName)
            val resourceId = resources.getIdentifier("appfilter", "xml", packageName)
            var stream: InputStream? = null
            val parser: XmlPullParser = if (resourceId != 0) {
                resources.getXml(resourceId)
            } else {
                stream = resources.assets.open("appfilter.xml")
                Xml.newPullParser().apply { setInput(stream, "UTF-8") }
            }
            try {
                var event = parser.eventType
                while (event != XmlPullParser.END_DOCUMENT) {
                    if (event == XmlPullParser.START_TAG && parser.name == "item") {
                        val component = parser.getAttributeValue(null, "component")
                        val drawable = parser.getAttributeValue(null, "drawable")
                        if (component != null && drawable != null &&
                            component.startsWith("ComponentInfo{") && component.endsWith("}")
                        ) {
                            val name = ComponentName.unflattenFromString(
                                component.substring(14, component.length - 1)
                            )
                            if (name != null) pack.addMapping(name.flattenToString(), name.packageName, drawable)
                        }
                    }
                    event = parser.next()
                }
            } finally {
                stream?.close()
                if (parser is android.content.res.XmlResourceParser) parser.close()
            }
            pack
        } catch (_: Exception) {
            null
        }
    }
}
