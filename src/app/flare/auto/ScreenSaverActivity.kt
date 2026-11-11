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
    private val environments = ArrayList<EnvironmentInfo>()
    private var music:SaverMusic?=null
    private var banners: SaverBanners? = null
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
                environments.forEach { it.updateSolar() }
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
        preferences = ProfileStore.preferences(this)
        val root = FrameLayout(this)
        root.setOnClickListener { finish() }
        root.background = WallpaperPack.saver(this, theme)
        val imageMode = preferences.getString("saver_mode", "clock") == "image"
        val slots=ArrayList<LinearLayout>()
        for(left in listOf(true,false)) {
            val info=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL;gravity=if(left) Gravity.START else Gravity.END }
            val fallback=if(left) { if(preferences.getBoolean("saver_weather",false) && preferences.getBoolean("saver_solar",false)) "both" else if(preferences.getBoolean("saver_weather",false)) "weather" else if(preferences.getBoolean("saver_solar",false)) "sun" else "off" } else "off"
            val mode=preferences.getString(if(left) "saver_slot_left" else "saver_slot_right",fallback)
            if(!imageMode || preferences.getBoolean("saver_image_info",false)) {
                for(kind in when(mode) { "both"->listOf("auto","sun");"weather"->listOf("auto");"sun"->listOf("sun");else->emptyList() }) {
                    val row=LinearLayout(this).apply { gravity=Gravity.CENTER_VERTICAL }
                    val icon=SolarIcon(this,theme.textSecondary);val label=TextView(this).apply { setTextColor(theme.textSecondary) }
                    row.addView(icon,LinearLayout.LayoutParams(dp(28),dp(28)));row.addView(label,LinearLayout.LayoutParams(-2,dp(36)).apply { leftMargin=dp(8) });info.addView(row)
                    environments.add(EnvironmentInfo(this,label,icon,kind))
                }
            }
            slots.add(info);root.addView(info,FrameLayout.LayoutParams(-2,-2,Gravity.TOP or if(left) Gravity.START else Gravity.END).apply { topMargin=dp(20);leftMargin=dp(24);rightMargin=dp(24) })
        }
        banners=SaverBanners(this,root) { left,hidden->slots[if(left) 0 else 1].visibility=if(hidden) View.INVISIBLE else View.VISIBLE }
        if(preferences.getBoolean("saver_music_banner",false)) music=SaverMusic(this) { pkg,title,artist->banners?.song(pkg,title,artist) }
        if (imageMode) {
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
        window.decorView.systemUiVisibility = UiFlags.IMMERSIVE
        banners?.start()
        music?.start()
        environments.forEach { it.start() }
        if (box != null || environments.isNotEmpty()) {
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
        music?.stop()
        banners?.stop()
        environments.forEach { it.stop() }
        super.onPause()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) window.decorView.systemUiVisibility = UiFlags.IMMERSIVE
    }

    override fun onDestroy() {
        music?.stop()
        banners?.stop()
        environments.forEach { it.stop() }
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }
}
