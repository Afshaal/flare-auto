package app.flare.auto

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.content.pm.ResolveInfo
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.util.LruCache
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
import java.util.ArrayList
import java.util.Collections
import java.util.Locale
import java.util.concurrent.Executors

class AppPickerActivity : Activity() {
    private lateinit var ui: UiKit
    private var role = ""
    private var current = ""
    private lateinit var grid: GridView
    private val all = ArrayList<Entry>()
    private val visible = ArrayList<Entry>()
    private val icons = LruCache<String, Drawable>(48)
    private val pendingIcons = HashSet<String>()
    private val main = Handler(Looper.getMainLooper())
    private val iconLoader = Executors.newSingleThreadExecutor()
    private lateinit var adapter: Adapter

    class Entry(@JvmField val label: String, @JvmField val target: String)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(ThemeTokens.get(this).background))
        window.decorView.systemUiVisibility = UiFlags.IMMERSIVE
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN)
        ui = UiKit(this)
        role = intent.getStringExtra(ROLE).orEmpty()
        current = intent.getStringExtra(TARGET).orEmpty()

        val root = ui.column().apply {
            setBackgroundColor(ui.t.background)
            isFocusableInTouchMode = true
        }
        root.addView(ui.header("Choose app"), LinearLayout.LayoutParams(-1, ui.dp(56)))
        val search = EditText(this).apply {
            setSingleLine()
            hint = "Search apps"
            setTextColor(ui.t.textPrimary)
            setHintTextColor(ui.t.textSecondary)
            textSize = 16f
            minHeight = ui.dp(56)
        }
        root.addView(search)
        grid = GridView(this).apply {
            numColumns = 5
            verticalSpacing = ui.dp(8)
            horizontalSpacing = ui.dp(8)
            stretchMode = GridView.STRETCH_COLUMN_WIDTH
        }
        root.addView(grid, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
        root.requestFocus()
        adapter = Adapter()
        grid.adapter = adapter
        grid.setOnItemClickListener { _, _, position, _ ->
            val data = Intent().putExtra(ROLE, role).putExtra(TARGET, visible[position].target)
            setResult(RESULT_OK, data)
            finish()
        }
        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(text: CharSequence, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(text: CharSequence, start: Int, before: Int, count: Int) {
                filter(text.toString())
            }
            override fun afterTextChanged(editable: Editable) = Unit
        })

        Thread {
            val packageManager = packageManager
            val apps = packageManager.queryIntentActivities(
                Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER),
                0
            )
            Collections.sort(apps, ResolveInfo.DisplayNameComparator(packageManager))
            val result = ArrayList<Entry>()
            result.add(Entry(if (role == "startup") "Do not open an app" else "Use launcher default", ""))
            for (resolveInfo in apps) {
                val target = ComponentName(
                    resolveInfo.activityInfo.packageName,
                    resolveInfo.activityInfo.name
                ).flattenToString()
                result.add(Entry(resolveInfo.loadLabel(packageManager).toString(), target))
            }
            main.post {
                if (isFinishing || isDestroyed) return@post
                all.clear()
                all.addAll(result)
                filter(search.text.toString())
            }
        }.start()
    }

    override fun onDestroy() {
        iconLoader.shutdownNow()
        main.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    private fun filter(query: String) {
        visible.clear()
        val term = query.lowercase(Locale.getDefault())
        for (entry in all) {
            if (entry.target.isEmpty() || entry.label.lowercase(Locale.getDefault()).contains(term)) {
                visible.add(entry)
            }
        }
        adapter.notifyDataSetChanged()
    }

    private inner class Adapter : BaseAdapter() {
        override fun getCount() = visible.size
        override fun getItem(position: Int): Any = visible[position]
        override fun getItemId(position: Int) = position.toLong()

        override fun getView(position: Int, recycled: View?, parent: ViewGroup): View {
            val cell = recycled as? LinearLayout ?: ui.column().apply {
                gravity = Gravity.CENTER
                minimumHeight = ui.dp(96)
                val image = ImageView(this@AppPickerActivity).apply { id = android.R.id.icon }
                addView(image, LinearLayout.LayoutParams(ui.dp(42), ui.dp(42)))
                val title = ui.text("", 14, ui.t.textPrimary).apply {
                    id = android.R.id.text1
                    gravity = Gravity.CENTER
                    maxLines = 2
                }
                addView(title, LinearLayout.LayoutParams(-1, -2))
            }
            val entry = visible[position]
            val image = cell.findViewById<ImageView>(android.R.id.icon)
            image.tag = entry.target
            val cached = synchronized(icons) { icons.get(entry.target) }
            image.setImageDrawable(cached)
            if (cached == null && entry.target.isNotEmpty() && pendingIcons.add(entry.target)) {
                val target = entry.target
                iconLoader.execute {
                    try {
                        val drawable = packageManager.getActivityIcon(ComponentName.unflattenFromString(target)!!)
                        synchronized(icons) { icons.put(target, drawable) }
                        main.post {
                            pendingIcons.remove(target)
                            if (!isFinishing && !isDestroyed) {
                                for (index in 0 until grid.childCount) {
                                    val visibleIcon = grid.getChildAt(index).findViewById<ImageView>(android.R.id.icon)
                                    if (visibleIcon.tag == target) visibleIcon.setImageDrawable(drawable)
                                }
                            }
                        }
                    } catch (_: Exception) {
                        main.post { pendingIcons.remove(target) }
                    }
                }
            }
            cell.findViewById<TextView>(android.R.id.text1).text =
                entry.label + if (entry.target == current) " ✓" else ""
            return cell
        }
    }

    companion object {
        const val ROLE = "role"
        const val TARGET = "target"
    }
}
