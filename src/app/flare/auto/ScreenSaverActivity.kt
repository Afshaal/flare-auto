package app.flare.auto

import android.app.Activity
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ScreenSaverActivity : Activity() {
    private var clock: TextView? = null
    private var date: TextView? = null
    private var box: LinearLayout? = null
    private lateinit var preferences: android.content.SharedPreferences
    private var lastMinute = -1L
    private val handler = Handler(Looper.getMainLooper())
    private var clockFormat: SimpleDateFormat? = null
    private val dateFormat = SimpleDateFormat("EEE, MMM d", Locale.getDefault())
    private val tick = object : Runnable {
        override fun run() {
            val now = Date()
            val minute = now.time / 60000
            if (minute != lastMinute) {
                lastMinute = minute
                val step = (minute % 9).toInt()
                box?.translationX = dp((step % 3 - 1) * 4).toFloat()
                box?.translationY = dp((step / 3 - 1) * 4).toFloat()
                clock?.text = clockFormat?.format(now)
                date?.text = dateFormat.format(now).uppercase(Locale.getDefault())
            }
            handler.postDelayed(this, 60000 - System.currentTimeMillis() % 60000)
        }
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density + .5f).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val theme = ThemeTokens.get(this)
        window.setBackgroundDrawable(ColorDrawable(theme.background))
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.decorView.systemUiVisibility = UiFlags.IMMERSIVE
        preferences = getSharedPreferences("home", 0)
        val root = FrameLayout(this)
        root.setOnClickListener { finish() }
        root.background = WallpaperPack.saver(this, theme)
        if (preferences.getString("saver_mode", "clock") == "image") {
            setContentView(root)
            return
        }

        box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
        }
        val civic = TextView(this)
        val brand = preferences.getString("brand_text", "CIVIC") ?: "CIVIC"
        civic.text = if (brand == "CIVIC") "C I V I C" else brand
        civic.setTextColor(0xffadadb0.toInt())
        civic.textSize = 17f
        civic.gravity = Gravity.CENTER
        civic.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        civic.visibility =
            if (preferences.getBoolean("saver_civic", true) && brand.isNotEmpty()) View.VISIBLE else View.GONE
        box?.addView(civic, LinearLayout.LayoutParams(-1, dp(30)))

        val clockView = TextView(this)
        clockView.setTextColor(theme.textPrimary)
        val size = preferences.getInt("saver_size", 120)
        clockView.textSize = size.toFloat()
        clockView.typeface = ClockStyles.typeface(preferences.getString("saver_clock_style", "classic"), true)
        clockView.gravity = Gravity.CENTER
        clockView.includeFontPadding = false
        box?.addView(clockView, LinearLayout.LayoutParams(-1, dp(size + 25)))
        clock = clockView

        val dateView = TextView(this)
        dateView.setTextColor(0xff888b90.toInt())
        dateView.textSize = 17f
        dateView.gravity = Gravity.CENTER
        dateView.letterSpacing = .12f
        dateView.visibility = if (preferences.getBoolean("saver_date", true)) View.VISIBLE else View.GONE
        box?.addView(dateView, LinearLayout.LayoutParams(-1, dp(34)))
        date = dateView

        root.addView(box, FrameLayout.LayoutParams(-1, -1))
        setContentView(root)
    }

    override fun onResume() {
        super.onResume()
        if (box != null) {
            clockFormat = SimpleDateFormat(
                if (preferences.getBoolean("clock24", false)) "HH:mm" else "h:mm",
                Locale.getDefault()
            )
            lastMinute = -1
            handler.removeCallbacks(tick)
            handler.post(tick)
        }
    }

    override fun onPause() {
        handler.removeCallbacks(tick)
        super.onPause()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }
}
