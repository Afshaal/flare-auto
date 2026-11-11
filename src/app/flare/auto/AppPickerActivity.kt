package app.flare.auto

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.BaseAdapter
import android.widget.EditText
import android.widget.GridView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import java.util.Locale

/**
 * Picks an installed app and returns its component as [TARGET]. With role "banners" it instead toggles which
 * apps may show screensaver notification banners.
 */
class AppPickerActivity : Activity() {
    private lateinit var ui: UiKit
    private var role = ""
    private var current = ""
    private val all = ArrayList<Entry>()
    private val visible = ArrayList<Entry>()
    private lateinit var adapter: Adapter

    class Entry(val label: String, val target: String)

    private val banners get() = role == "banners"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setBackgroundDrawable(ColorDrawable(ThemeTokens.get(this).background))
        window.immersive()
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN)
        ui = UiKit(this)
        role = intent.getStringExtra(ROLE).orEmpty()
        current = intent.getStringExtra(TARGET).orEmpty()

        val root = ui.column().apply {
            setBackgroundColor(ui.t.background)
            isFocusableInTouchMode = true
        }
        root.addView(ui.header(if (banners) "Notification apps · tap to toggle" else "Choose app"), LinearLayout.LayoutParams(MATCH, ui.dp(56)))
        val search = EditText(this).apply {
            setSingleLine()
            hint = "Search apps"
            setTextColor(ui.t.textPrimary)
            setHintTextColor(ui.t.textSecondary)
            textSize = 16f
            minHeight = ui.dp(56)
        }
        root.addView(search)
        val grid = GridView(this).apply {
            numColumns = 5
            verticalSpacing = ui.dp(8)
            horizontalSpacing = ui.dp(8)
            stretchMode = GridView.STRETCH_COLUMN_WIDTH
        }
        root.addView(grid, LinearLayout.LayoutParams(MATCH, 0, 1f))
        setContentView(root)
        root.requestFocus()
        adapter = Adapter()
        grid.adapter = adapter
        grid.setOnItemClickListener { _, _, position, _ ->
            if (banners) {
                val pkg = ComponentName.unflattenFromString(visible[position].target)?.packageName ?: return@setOnItemClickListener
                val prefs = ProfileStore.preferences(this)
                val allowed = HashSet(prefs.getStringSet(Keys.SAVER_BANNER_APPS, emptySet()).orEmpty())
                if (!allowed.add(pkg)) allowed.remove(pkg)
                prefs.edit().putStringSet(Keys.SAVER_BANNER_APPS, allowed).apply()
                adapter.notifyDataSetChanged()
                return@setOnItemClickListener
            }
            setResult(RESULT_OK, Intent().putExtra(ROLE, role).putExtra(TARGET, visible[position].target))
            finish()
        }
        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(text: CharSequence, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(text: CharSequence, start: Int, before: Int, count: Int) = filter(text.toString())
            override fun afterTextChanged(editable: Editable) = Unit
        })

        Background.load(this, {
            val result = ArrayList<Entry>()
            if (!banners) result.add(Entry(if (role == "startup") "Do not open an app" else "Use launcher default", ""))
            AppCatalog.apps(this).mapTo(result) { Entry(it.label, it.target) }
        }) { result ->
            all.clear()
            all.addAll(result)
            filter(search.text.toString())
        }
    }

    private fun filter(query: String) {
        visible.clear()
        val term = query.lowercase(Locale.getDefault())
        all.filterTo(visible) { it.target.isEmpty() || it.label.lowercase(Locale.getDefault()).contains(term) }
        adapter.notifyDataSetChanged()
    }

    private inner class Adapter : BaseAdapter() {
        override fun getCount() = visible.size
        override fun getItem(position: Int): Any = visible[position]
        override fun getItemId(position: Int) = position.toLong()

        override fun getView(position: Int, recycled: View?, parent: ViewGroup): View {
            val cell = recycled as? Cell ?: Cell()
            val entry = visible[position]
            cell.icon.setImageDrawable(if (entry.target.isEmpty()) null else AppIcons.get(this@AppPickerActivity, entry.target))
            val chosen = if (banners) {
                ProfileStore.preferences(this@AppPickerActivity).getStringSet(Keys.SAVER_BANNER_APPS, emptySet()).orEmpty()
                    .contains(ComponentName.unflattenFromString(entry.target)?.packageName)
            } else entry.target == current
            cell.title.text = if (chosen) entry.label + " ✓" else entry.label
            return cell
        }
    }

    private inner class Cell : LinearLayout(this@AppPickerActivity) {
        val icon = ImageView(context)
        val title: TextView = ui.text("", 14, ui.t.textPrimary).apply {
            gravity = Gravity.CENTER
            maxLines = 2
        }

        init {
            orientation = VERTICAL
            gravity = Gravity.CENTER
            minimumHeight = ui.dp(96)
            addView(icon, LayoutParams(ui.dp(42), ui.dp(42)))
            addView(title, LayoutParams(MATCH, WRAP))
        }
    }

    companion object {
        const val ROLE = "role"
        const val TARGET = "target"
    }
}
