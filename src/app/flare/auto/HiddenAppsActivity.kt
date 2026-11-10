package app.flare.auto

import android.app.Activity
import android.content.Intent
import android.content.pm.ResolveInfo
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import java.util.ArrayList
import java.util.Collections
import java.util.HashMap
import java.util.HashSet

class HiddenAppsActivity : Activity() {
    private lateinit var ui: UiKit
    private lateinit var preferences: android.content.SharedPreferences
    private lateinit var hidden: MutableSet<String>
    private val apps = ArrayList<ResolveInfo>()
    private val labels = HashMap<String, String>()
    private val icons = HashMap<String, Drawable?>()
    private lateinit var adapter: BaseAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setBackgroundDrawable(ColorDrawable(ThemeTokens.get(this).background))
        window.decorView.systemUiVisibility = UiFlags.IMMERSIVE
        ui = UiKit(this)
        preferences = getSharedPreferences("home", 0)
        hidden = HashSet(preferences.getStringSet("hidden_apps", Collections.emptySet()) ?: emptySet())
        val root = ui.column().apply { setBackgroundColor(ui.t.background) }
        root.addView(ui.header("Hidden apps"), LinearLayout.LayoutParams(-1, ui.dp(56)))
        val show = ui.text("Show all", 16, ui.t.accent).apply {
            gravity = Gravity.CENTER
            minHeight = ui.dp(56)
            setOnClickListener {
                hidden.clear()
                save()
                adapter.notifyDataSetChanged()
            }
        }
        root.addView(show)
        val list = ListView(this).apply { divider = null }
        root.addView(list, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
        adapter = object : BaseAdapter() {
            override fun getCount() = apps.size
            override fun getItem(position: Int): Any = apps[position]
            override fun getItemId(position: Int) = position.toLong()

            override fun getView(position: Int, recycled: View?, parent: ViewGroup): View {
                val row = recycled as? LinearLayout ?: LinearLayout(this@HiddenAppsActivity).apply {
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(ui.dp(16), 0, ui.dp(16), 0)
                    minimumHeight = ui.dp(56)
                    addView(
                        ImageView(this@HiddenAppsActivity).apply { id = android.R.id.icon },
                        LinearLayout.LayoutParams(ui.dp(40), ui.dp(40))
                    )
                    val name = ui.text("", 16, ui.t.textPrimary).apply {
                        id = android.R.id.text1
                        setPadding(ui.dp(12), 0, 0, 0)
                    }
                    addView(name, LinearLayout.LayoutParams(0, -2, 1f))
                    val check = CheckBox(this@HiddenAppsActivity).apply {
                        id = android.R.id.checkbox
                        isClickable = false
                        isFocusable = false
                        buttonTintList = android.content.res.ColorStateList.valueOf(ui.t.accent)
                    }
                    addView(check, LinearLayout.LayoutParams(ui.dp(56), ui.dp(56)))
                }
                val packageName = apps[position].activityInfo.packageName
                row.findViewById<ImageView>(android.R.id.icon).setImageDrawable(icons[packageName])
                row.findViewById<TextView>(android.R.id.text1).text = labels[packageName]
                row.findViewById<CheckBox>(android.R.id.checkbox).isChecked = packageName in hidden
                return row
            }
        }
        list.adapter = adapter
        list.setOnItemClickListener { _, _, position, _ ->
            val packageName = apps[position].activityInfo.packageName
            if (!hidden.remove(packageName)) hidden.add(packageName)
            save()
            adapter.notifyDataSetChanged()
        }

        Thread {
            val packageManager = packageManager
            val all = packageManager.queryIntentActivities(
                Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER),
                0
            )
            Collections.sort(all, ResolveInfo.DisplayNameComparator(packageManager))
            val found = ArrayList<ResolveInfo>()
            val loadedIcons = HashMap<String, Drawable?>()
            val loadedLabels = HashMap<String, String>()
            for (resolveInfo in all) {
                val packageName = resolveInfo.activityInfo.packageName
                if (packageName == this@HiddenAppsActivity.packageName || loadedIcons.containsKey(packageName)) continue
                found.add(resolveInfo)
                loadedLabels[packageName] = resolveInfo.loadLabel(packageManager).toString()
                try {
                    loadedIcons[packageName] = resolveInfo.loadIcon(packageManager)
                } catch (_: RuntimeException) {
                    loadedIcons[packageName] = null
                }
            }
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                apps.addAll(found)
                icons.putAll(loadedIcons)
                labels.putAll(loadedLabels)
                adapter.notifyDataSetChanged()
            }
        }.start()
    }

    private fun save() {
        preferences.edit().putStringSet("hidden_apps", HashSet(hidden)).apply()
    }
}
