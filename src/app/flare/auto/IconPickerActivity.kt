package app.flare.auto

import android.app.Activity
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
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
import java.io.ByteArrayOutputStream
import java.util.ArrayList
import java.util.Locale

class IconPickerActivity : Activity() {
    private lateinit var ui: UiKit
    private val all = ArrayList<Item>()
    private val visible = ArrayList<Item>()
    private val main = Handler(Looper.getMainLooper())
    private lateinit var adapter: BaseAdapter
    private lateinit var packageNameToEdit: String

    class Item(@JvmField val name: String, @JvmField val res: String) {
        val searchName = name.lowercase(Locale.ROOT)
        var resourceId = -1
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ui = UiKit(this)
        window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(ui.t.background))
        window.decorView.systemUiVisibility = UiFlags.IMMERSIVE
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN)
        packageNameToEdit = intent.getStringExtra("package").orEmpty()
        if (packageNameToEdit.isEmpty()) {
            finish()
            return
        }
        val root = ui.column().apply {
            isFocusableInTouchMode = true
            setBackgroundColor(ui.t.background)
        }
        root.addView(ui.header("Choose an icon"), LinearLayout.LayoutParams(-1, ui.dp(56)))
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
        root.addView(list, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
        root.requestFocus()
        adapter = object : BaseAdapter() {
            override fun getCount() = visible.size
            override fun getItem(position: Int): Any = visible[position]
            override fun getItemId(position: Int) = position.toLong()

            override fun getView(position: Int, recycled: View?, parent: ViewGroup): View {
                val row = recycled as? LinearLayout ?: LinearLayout(this@IconPickerActivity).apply {
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(ui.dp(16), 0, ui.dp(16), 0)
                    minimumHeight = ui.dp(56)
                    addView(
                        ImageView(this@IconPickerActivity).apply { id = android.R.id.icon },
                        LinearLayout.LayoutParams(ui.dp(36), ui.dp(36))
                    )
                    val name = ui.text("", 16, ui.t.textPrimary).apply {
                        id = android.R.id.text1
                        setPadding(ui.dp(16), 0, 0, 0)
                    }
                    addView(name, LinearLayout.LayoutParams(0, ui.dp(56), 1f))
                }
                val item = visible[position]
                val icon = row.findViewById<ImageView>(android.R.id.icon)
                try {
                    if (item.resourceId == -1) item.resourceId = resources.getIdentifier(item.res, "drawable", packageName)
                    icon.setImageResource(item.resourceId)
                    icon.setColorFilter(ui.t.textPrimary)
                } catch (_: RuntimeException) {
                    icon.setImageDrawable(null)
                }
                row.findViewById<TextView>(android.R.id.text1).text = item.name
                return row
            }
        }
        list.adapter = adapter
        list.setOnItemClickListener { _, _, position, _ -> chooseColor(visible[position]) }
        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(text: CharSequence, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(text: CharSequence, start: Int, before: Int, count: Int) {
                filter(text.toString())
            }
            override fun afterTextChanged(editable: Editable) = Unit
        })
        Thread {
            try {
                val result = assets.open("simple-icons-picker.json").use { input ->
                    ByteArrayOutputStream().use { output ->
                        input.copyTo(output)
                        val data = JSONArray(output.toString("UTF-8"))
                        ArrayList<Item>().apply {
                            for (index in 0 until data.length()) {
                                val item = data.getJSONObject(index)
                                add(Item(item.getString("name"), item.getString("resource")))
                            }
                        }
                    }
                }
                main.post {
                    if (isFinishing || isDestroyed) return@post
                    all.addAll(result)
                    filter(search.text.toString())
                }
            } catch (_: Exception) {
                main.post {
                    if (!isFinishing && !isDestroyed) Toast.makeText(this, "Icon catalog unavailable", Toast.LENGTH_SHORT).show()
                }
            }
        }.start()
    }

    private fun chooseColor(item: Item) {
        val names = arrayOf(
            "Theme color", "White", "Red", "Orange", "Yellow", "Green", "Cyan", "Blue", "Purple", "Pink",
            "Custom color"
        )
        val colors = intArrayOf(
            ui.t.textPrimary,
            0xffffffff.toInt(),
            0xffef5350.toInt(),
            0xffffa040.toInt(),
            0xffffd15c.toInt(),
            0xff4cce85.toInt(),
            0xff37cdd4.toInt(),
            0xff59aaff.toInt(),
            0xffbd89ff.toInt(),
            0xffff80b5.toInt()
        )
        ThemedDialogs.Builder(this)
            .setTitle("Icon color")
            .setItems(names) { _, index ->
                if (index == 10) customColor(item) else save(item, if (index == 0) null else colors[index])
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
                val raw = input.text.toString().trim()
                try {
                    require(raw.matches(Regex("#[0-9a-fA-F]{6}")))
                    save(item, Color.parseColor(raw))
                } catch (_: Exception) {
                    Toast.makeText(this, "Enter a color like #E60012", Toast.LENGTH_LONG).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun save(item: Item, color: Int?) {
        val edit: SharedPreferences.Editor = getSharedPreferences("home", 0).edit()
            .putString("icon_override_$packageNameToEdit", item.res)
            .remove("icon_image_$packageNameToEdit")
        if (color == null) edit.remove("icon_color_$packageNameToEdit") else edit.putInt("icon_color_$packageNameToEdit", color)
        edit.apply()
        setResult(
            RESULT_OK,
            Intent().putExtra("package", packageNameToEdit).putExtra("icon", item.res)
        )
        finish()
    }

    private fun filter(query: String) {
        val term = query.lowercase(Locale.ROOT).trim()
        visible.clear()
        for (item in all) if (item.searchName.contains(term)) visible.add(item)
        adapter.notifyDataSetChanged()
    }

    override fun onDestroy() {
        main.removeCallbacksAndMessages(null)
        super.onDestroy()
    }
}
