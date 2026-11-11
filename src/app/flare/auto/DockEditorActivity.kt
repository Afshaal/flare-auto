package app.flare.auto

import android.app.Activity
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.util.ArrayList
import java.util.Collections

class DockEditorActivity : Activity() {
    private lateinit var ui: UiKit
    private lateinit var preferences: SharedPreferences
    private lateinit var items: ArrayList<DockConfig.Item>
    private lateinit var list: LinearLayout
    private lateinit var group: LinearLayout
    private lateinit var add: LinearLayout
    private lateinit var sizeRow: LinearLayout
    private lateinit var labelsRow: LinearLayout
    private lateinit var monoRow: LinearLayout
    private var choosing = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.decorView.systemUiVisibility = UiFlags.IMMERSIVE
        ui = UiKit(this)
        window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(ui.t.background))
        preferences = ProfileStore.preferences(this)
        items = DockConfig.read(preferences)
        if (savedInstanceState != null) choosing = savedInstanceState.getInt("choosing", -1)
        render()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt("choosing", choosing)
        super.onSaveInstanceState(outState)
    }

    private fun render() {
        val root = ui.column().apply { setBackgroundColor(ui.t.background) }
        root.addView(ui.header("Dock configuration"), LinearLayout.LayoutParams(-1, ui.dp(56)))
        val scroll = ScrollView(this)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        list = ui.column().apply { setPadding(ui.dp(16), 0, ui.dp(16), ui.dp(20)) }
        scroll.addView(list)
        setContentView(root)
        group = ui.group()
        list.addView(group)
        populateRows()

        val actions = ui.group()
        list.addView(ui.section("APPEARANCE"))
        list.addView(actions)
        add = ui.valueRow("Add shortcut", "", Runnable {
            items.add(items.size - 1, DockConfig.Item(0, "", true))
            save()
        })
        ui.add(actions, add)
        sizeRow = ui.valueRow("Icon scale", sizeName(), Runnable {
            ThemedDialogs.Builder(this)
                .setTitle("Icon scale")
                .setItems(arrayOf("Small", "Medium", "Large")) { _, index ->
                    preferences.edit().putInt("dock_icons", intArrayOf(20, 26, 32)[index]).apply()
                    sizeRow.getChildAt(1).let { (it as TextView).text = sizeName() }
                }
                .show()
        })
        ui.add(actions, sizeRow)
        labelsRow = ui.toggle("Show labels", preferences.getBoolean("dock_labels", true), Runnable {
            val enabled = !preferences.getBoolean("dock_labels", true)
            preferences.edit().putBoolean("dock_labels", enabled).apply()
            (labelsRow.getChildAt(1) as android.widget.Switch).isChecked = enabled
        })
        ui.add(actions, labelsRow)
        monoRow = ui.toggle("Monochrome app icons", preferences.getBoolean("dock_mono", false), Runnable {
            val enabled = !preferences.getBoolean("dock_mono", false)
            preferences.edit().putBoolean("dock_mono", enabled).apply()
            (monoRow.getChildAt(1) as android.widget.Switch).isChecked = enabled
        })
        ui.add(actions, monoRow)
        ui.add(
            actions,
            ui.valueRow("Restore default shortcuts", "", Runnable {
                ThemedDialogs.Builder(this)
                    .setTitle("Restore default shortcuts?")
                    .setPositiveButton("Restore") { _, _ ->
                        preferences.edit().remove(DockConfig.KEY).remove("dock_order").apply()
                        items = DockConfig.read(preferences)
                        populateRows()
                        updateOptions()
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            })
        )
        updateOptions()
    }

    private fun populateRows() {
        group.removeAllViews()
        for (index in items.indices) {
            val item = items[index]
            val row = LinearLayout(this).apply {
                gravity = Gravity.CENTER_VERTICAL
                minimumHeight = ui.dp(56)
                if (!item.visible) alpha = .45f
            }
            val identity = LinearLayout(this).apply {
                gravity = Gravity.CENTER_VERTICAL
                setPadding(ui.dp(12), 0, 0, 0)
            }
            val icon: View = if (item.type == 5) {
                ImageView(this).apply {
                    try {
                        setImageDrawable(AsyncImages.appIcon(this@DockEditorActivity, item.target))
                    } catch (_: Exception) {
                    }
                }
            } else {
                HomeActivity.NavIcon(this, DockConfig.ICONS[item.type], ui.t.textPrimary)
            }
            identity.addView(icon, LinearLayout.LayoutParams(ui.dp(28), ui.dp(28)))
            val name = ui.text(DockConfig.name(this, item), 16, ui.t.textPrimary).apply {
                setPadding(ui.dp(10), 0, 0, 0)
                setSingleLine()
                ellipsize = TextUtils.TruncateAt.END
            }
            identity.addView(name, LinearLayout.LayoutParams(0, ui.dp(56), 1f))
            row.addView(identity, LinearLayout.LayoutParams(0, ui.dp(56), 1f))
            if (item.type != 4) identity.setOnClickListener { choose(index) }
            button(row, "↑", index > 0) {
                Collections.swap(items, index, index - 1)
                save()
            }
            button(row, "↓", index < items.size - 1) {
                Collections.swap(items, index, index + 1)
                save()
            }
            button(row, if (item.visible) "◉" else "○", item.type != 4) {
                item.visible = !item.visible
                row.alpha = if (item.visible) 1f else .45f
                (row.getChildAt(3) as TextView).text = if (item.visible) "◉" else "○"
                DockConfig.save(preferences, items)
            }
            button(row, "×", item.type != 4) {
                items.removeAt(index)
                save()
            }
            ui.add(group, row)
        }
    }

    private fun updateOptions() {
        (add.getChildAt(1) as TextView).text = "${items.size} / ${DockConfig.maxSlots(this)}"
        val canAdd = items.size < DockConfig.maxSlots(this)
        add.isEnabled = canAdd
        add.alpha = if (canAdd) 1f else .45f
        (sizeRow.getChildAt(1) as TextView).text = sizeName()
        (labelsRow.getChildAt(1) as android.widget.Switch).isChecked = preferences.getBoolean("dock_labels", true)
        (monoRow.getChildAt(1) as android.widget.Switch).isChecked = preferences.getBoolean("dock_mono", false)
    }

    private fun sizeName(): String {
        val size = preferences.getInt("dock_icons", 24)
        return if (size < 23) "Small" else if (size < 30) "Medium" else "Large"
    }

    private fun button(row: LinearLayout, label: String, enabled: Boolean, action: () -> Unit) {
        val button = ui.text(label, 22, ui.t.textPrimary).apply {
            gravity = Gravity.CENTER
            visibility = if (enabled) View.VISIBLE else View.INVISIBLE
        }
        row.addView(button, LinearLayout.LayoutParams(ui.dp(56), ui.dp(56)))
        if (enabled) button.setOnClickListener { action() }
    }

    private fun choose(index: Int) {
        choosing = index
        val choices = arrayOf("Audio", "Radio", "Bluetooth", "Screen off", "Apps", "Application")
        ThemedDialogs.Builder(this)
            .setTitle("Shortcut action")
            .setItems(choices) { _, choice ->
                when (choice) {
                    4 -> Toast.makeText(this, "Apps already has a permanent shortcut", Toast.LENGTH_SHORT).show()
                    5 -> startActivityForResult(
                        Intent(this, AppPickerActivity::class.java)
                            .putExtra(AppPickerActivity.ROLE, "dock")
                            .putExtra(AppPickerActivity.TARGET, items[index].target),
                        1
                    )
                    else -> {
                        items[index] = DockConfig.Item(choice, "", true)
                        save()
                    }
                }
            }
            .show()
    }

    private fun save() {
        DockConfig.save(preferences, items)
        populateRows()
        updateOptions()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != 1 || resultCode != RESULT_OK || data == null ||
            choosing < 0 || choosing >= items.size
        ) return
        val target = data.getStringExtra(AppPickerActivity.TARGET)
        if (!target.isNullOrEmpty()) {
            items[choosing] = DockConfig.Item(5, target, true)
            save()
        }
    }
}
