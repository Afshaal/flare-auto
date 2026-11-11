package app.flare.auto

import android.app.Activity
import android.content.Intent
import android.graphics.Color
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
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import org.json.JSONArray
import java.util.Locale

/** Picks a Simple Icons drawable and colour for one app ([EXTRA_PACKAGE]); returns it as [EXTRA_ICON]. */
class IconPickerActivity : Activity() {
    /** Dialogs take focus; restore full screen when it comes back. */
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) window.immersive()
    }

    private lateinit var ui: UiKit
    private val all = ArrayList<Item>()
    private val visible = ArrayList<Item>()
    private lateinit var adapter: BaseAdapter
    private lateinit var packageNameToEdit: String

    /** A catalog icon; its drawable id is resolved the first time its row is shown. */
    class Item(val name: String, val res: String) {
        val searchName = name.lowercase(Locale.ROOT)
        var resourceId = -1
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ui = UiKit(this)
        window.setBackgroundDrawable(ColorDrawable(ui.t.background))
        window.immersive()
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN)
        packageNameToEdit = intent.getStringExtra(EXTRA_PACKAGE).orEmpty()
        if (packageNameToEdit.isEmpty()) {
            finish()
            return
        }
        val root = ui.column().apply {
            isFocusableInTouchMode = true
            setBackgroundColor(ui.t.background)
        }
        root.addView(ui.header("Choose an icon"), LinearLayout.LayoutParams(MATCH, ui.dp(56)))
        val search = EditText(this).apply {
            setSingleLine(true)
            hint = "Search icons"
            textSize = 16f
            setTextColor(ui.t.textPrimary)
            setHintTextColor(ui.t.textSecondary)
            minHeight = ui.dp(56)
        }
        root.addView(search)
        val list = ListView(this).apply { divider = null }
        root.addView(list, LinearLayout.LayoutParams(MATCH, 0, 1f))
        setContentView(root)
        root.requestFocus()
        adapter = object : BaseAdapter() {
            override fun getCount() = visible.size
            override fun getItem(position: Int): Any = visible[position]
            override fun getItemId(position: Int) = position.toLong()

            override fun getView(position: Int, recycled: View?, parent: ViewGroup): View {
                val row = recycled as? Row ?: Row()
                val item = visible[position]
                try {
                    if (item.resourceId == -1) item.resourceId = resources.getIdentifier(item.res, "drawable", packageName)
                    row.icon.setImageResource(item.resourceId)
                    row.icon.setColorFilter(ui.t.textPrimary)
                } catch (_: RuntimeException) {
                    row.icon.setImageDrawable(null)
                }
                row.name.text = item.name
                return row
            }
        }
        list.adapter = adapter
        list.setOnItemClickListener { _, _, position, _ -> chooseColor(visible[position]) }
        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(text: CharSequence, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(text: CharSequence, start: Int, before: Int, count: Int) = filter(text.toString())
            override fun afterTextChanged(editable: Editable) = Unit
        })
        Background.load(this, {
            try {
                val data = JSONArray(assets.open("simple-icons-picker.json").bufferedReader(Charsets.UTF_8).use { it.readText() })
                List(data.length()) { index -> data.getJSONObject(index).let { Item(it.getString("name"), it.getString("resource")) } }
            } catch (_: Exception) {
                null
            }
        }) { result ->
            if (result == null) {
                Toast.makeText(this, "Icon catalog unavailable", Toast.LENGTH_SHORT).show()
                return@load
            }
            all.addAll(result)
            filter(search.text.toString())
        }
    }

    private inner class Row : LinearLayout(this@IconPickerActivity) {
        val icon = ImageView(context)
        val name: TextView = ui.text("", 16, ui.t.textPrimary).apply { setPadding(ui.dp(16), 0, 0, 0) }

        init {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(ui.dp(16), 0, ui.dp(16), 0)
            minimumHeight = ui.dp(56)
            addView(icon, LayoutParams(ui.dp(36), ui.dp(36)))
            addView(name, LayoutParams(0, ui.dp(56), 1f))
        }
    }

    private fun chooseColor(item: Item) {
        val names = arrayOf("Theme color", "White", "Red", "Orange", "Yellow", "Green", "Cyan", "Blue", "Purple", "Pink", "Custom color")
        val colors = intArrayOf(ui.t.textPrimary, Color.WHITE, 0xffef5350.toInt(), 0xffffa040.toInt(), 0xffffd15c.toInt(),
            0xff4cce85.toInt(), 0xff37cdd4.toInt(), 0xff59aaff.toInt(), 0xffbd89ff.toInt(), 0xffff80b5.toInt())
        ThemedDialogs.Builder(this)
            .setTitle("Icon color")
            .setItems(names) { _, index ->
                if (index == names.lastIndex) customColor(item) else save(item, if (index == 0) null else colors[index])
            }
            .show()
    }

    private fun customColor(item: Item) {
        val input = EditText(this).apply {
            setSingleLine(true)
            hint = "#RRGGBB"
        }
        ThemedDialogs.Builder(this)
            .setTitle("Custom icon color")
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                val color = parseHexColor(input.text.toString())
                if (color == null) Toast.makeText(this, "Enter a color like #E60012", Toast.LENGTH_LONG).show()
                else save(item, color)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun save(item: Item, color: Int?) {
        val edit = ProfileStore.preferences(this).edit()
            .putString(Keys.ICON_OVERRIDE_PREFIX + packageNameToEdit, item.res)
            .remove(Keys.ICON_IMAGE_PREFIX + packageNameToEdit)
        if (color == null) edit.remove(Keys.ICON_COLOR_PREFIX + packageNameToEdit) else edit.putInt(Keys.ICON_COLOR_PREFIX + packageNameToEdit, color)
        edit.apply()
        setResult(RESULT_OK, Intent().putExtra(EXTRA_PACKAGE, packageNameToEdit).putExtra(EXTRA_ICON, item.res))
        finish()
    }

    private fun filter(query: String) {
        val term = query.lowercase(Locale.ROOT).trim()
        visible.clear()
        all.filterTo(visible) { it.searchName.contains(term) }
        adapter.notifyDataSetChanged()
    }

    companion object {
        const val EXTRA_PACKAGE = "package"
        const val EXTRA_ICON = "icon"
    }
}
