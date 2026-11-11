package app.flare.auto

import android.app.Activity
import android.content.res.ColorStateList
import android.graphics.drawable.ColorDrawable
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

/** Chooses which installed apps the drawer hides (one row per package). */
class HiddenAppsActivity : Activity() {
    /** Dialogs take focus; restore full screen when it comes back. */
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) window.immersive()
    }

    private lateinit var ui: UiKit
    private lateinit var preferences: android.content.SharedPreferences
    private lateinit var hidden: MutableSet<String>
    private val apps = ArrayList<LaunchableApp>()
    private lateinit var adapter: BaseAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setBackgroundDrawable(ColorDrawable(ThemeTokens.get(this).background))
        window.immersive()
        ui = UiKit(this)
        preferences = ProfileStore.preferences(this)
        hidden = HashSet(preferences.getStringSet(Keys.HIDDEN_APPS, emptySet()).orEmpty())
        val root = ui.column().apply { setBackgroundColor(ui.t.background) }
        root.addView(ui.header("Hidden apps"), LinearLayout.LayoutParams(MATCH, ui.dp(56)))
        root.addView(ui.text("Show all", 16, ui.t.accent).apply {
            gravity = Gravity.CENTER
            minHeight = ui.dp(56)
            setOnClickListener {
                hidden.clear()
                save()
                adapter.notifyDataSetChanged()
            }
        })
        val list = ListView(this).apply { divider = null }
        root.addView(list, LinearLayout.LayoutParams(MATCH, 0, 1f))
        setContentView(root)
        adapter = object : BaseAdapter() {
            override fun getCount() = apps.size
            override fun getItem(position: Int): Any = apps[position]
            override fun getItemId(position: Int) = position.toLong()

            override fun getView(position: Int, recycled: View?, parent: ViewGroup): View {
                val row = recycled as? Row ?: Row()
                val app = apps[position]
                row.icon.setImageDrawable(AppIcons.get(this@HiddenAppsActivity, app.target))
                row.name.text = app.label
                row.check.isChecked = app.packageName in hidden
                return row
            }
        }
        list.adapter = adapter
        list.setOnItemClickListener { _, _, position, _ ->
            val packageName = apps[position].packageName
            if (!hidden.remove(packageName)) hidden.add(packageName)
            save()
            adapter.notifyDataSetChanged()
        }
        Background.load(this, { AppCatalog.others(this).distinctBy { it.packageName } }) { found ->
            apps.addAll(found)
            adapter.notifyDataSetChanged()
        }
    }

    private inner class Row : LinearLayout(this@HiddenAppsActivity) {
        val icon = ImageView(context)
        val name: TextView = ui.text("", 16, ui.t.textPrimary).apply { setPadding(ui.dp(12), 0, 0, 0) }
        val check = CheckBox(context).apply {
            isClickable = false
            isFocusable = false
            buttonTintList = ColorStateList.valueOf(ui.t.accent)
        }

        init {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(ui.dp(16), 0, ui.dp(16), 0)
            minimumHeight = ui.dp(56)
            addView(icon, LayoutParams(ui.dp(40), ui.dp(40)))
            addView(name, LayoutParams(0, WRAP, 1f))
            addView(check, LayoutParams(ui.dp(56), ui.dp(56)))
        }
    }

    private fun save() = preferences.edit().putStringSet(Keys.HIDDEN_APPS, HashSet(hidden)).apply()
}
