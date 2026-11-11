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


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val theme = ThemeTokens.get(this)
        window.setBackgroundDrawable(ColorDrawable(theme.background))
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.immersive()
        preferences = ProfileStore.preferences(this)
        val root = FrameLayout(this)
        root.setOnClickListener { finish() }
        root.background = WallpaperPack.saver(this, theme)
        val imageMode = preferences.getString(Keys.SAVER_MODE, "clock") == "image"
        val slots=ArrayList<LinearLayout>()
        for(left in listOf(true,false)) {
            val info=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL;gravity=if(left) Gravity.START else Gravity.END }
            val fallback=if(left) { if(preferences.getBoolean(Keys.LEGACY_SAVER_WEATHER,false) && preferences.getBoolean(Keys.LEGACY_SAVER_SOLAR,false)) "both" else if(preferences.getBoolean(Keys.LEGACY_SAVER_WEATHER,false)) "weather" else if(preferences.getBoolean(Keys.LEGACY_SAVER_SOLAR,false)) "sun" else "off" } else "off"
            val mode=preferences.getString(if(left) Keys.SAVER_SLOT_LEFT else Keys.SAVER_SLOT_RIGHT,fallback)
            if(!imageMode || preferences.getBoolean(Keys.SAVER_IMAGE_INFO,false)) {
                for(kind in when(mode) { "both"->listOf("auto","sun");"weather"->listOf("auto");"sun"->listOf("sun");else->emptyList() }) {
                    val row=LinearLayout(this).apply { gravity=Gravity.CENTER_VERTICAL }
                    val icon=SolarIcon(this,theme.textSecondary);val label=TextView(this).apply { setTextColor(theme.textSecondary) }
                    row.addView(icon,LinearLayout.LayoutParams(dp(28),dp(28)));row.addView(label,LinearLayout.LayoutParams(WRAP,dp(36)).apply { leftMargin=dp(8) });info.addView(row)
                    environments.add(EnvironmentInfo(this,label,icon,kind))
                }
            }
            slots.add(info);root.addView(info,FrameLayout.LayoutParams(WRAP,WRAP,Gravity.TOP or if(left) Gravity.START else Gravity.END).apply { topMargin=dp(20);leftMargin=dp(24);rightMargin=dp(24) })
        }
        banners=SaverBanners(this,root) { left,hidden->slots[if(left) 0 else 1].visibility=if(hidden) View.INVISIBLE else View.VISIBLE }
        if(preferences.getBoolean(Keys.SAVER_MUSIC_BANNER,false)) music=SaverMusic(this) { pkg,title,artist->banners?.song(pkg,title,artist) }
        if (imageMode) {
            setContentView(root)
            return
        }

        box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
        }
        val brand = preferences.getString(Keys.BRAND_TEXT, Defaults.VEHICLE_NAME) ?: Defaults.VEHICLE_NAME
        val vehicle = TextView(this).apply {
            text = brand
            letterSpacing = VEHICLE_LETTER_SPACING
            setTextColor(LABEL_GREY)
            textSize = 17f
            gravity = Gravity.CENTER
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            visibility = if (preferences.getBoolean(Keys.SAVER_VEHICLE_LABEL, true) && brand.isNotEmpty()) View.VISIBLE else View.GONE
        }
        box?.addView(vehicle, LinearLayout.LayoutParams(MATCH, dp(30)))

        val clockView = TextView(this)
        clockView.setTextColor(theme.textPrimary)
        val size = preferences.getInt(Keys.SAVER_SIZE, 120)
        clockView.textSize = size.toFloat()
        clockView.typeface = ClockStyles.typeface(preferences.getString(Keys.SAVER_CLOCK_STYLE, "classic"), true)
        clockView.gravity = Gravity.CENTER
        clockView.includeFontPadding = false
        box?.addView(clockView, LinearLayout.LayoutParams(MATCH, dp(size + 25)))
        clock = clockView

        val dateView = TextView(this)
        dateView.setTextColor(0xff888b90.toInt())
        dateView.textSize = 17f
        dateView.gravity = Gravity.CENTER
        dateView.letterSpacing = .12f
        dateView.visibility = if (preferences.getBoolean(Keys.SAVER_DATE, true)) View.VISIBLE else View.GONE
        box?.addView(dateView, LinearLayout.LayoutParams(MATCH, dp(34)))
        date = dateView

        root.addView(box, FrameLayout.LayoutParams(MATCH, MATCH))
        setContentView(root)
    }

    override fun onResume() {
        super.onResume()
        window.immersive()
        banners?.start()
        music?.start()
        environments.forEach { it.start() }
        if (box != null || environments.isNotEmpty()) {
            clockFormat = SimpleDateFormat(
                if (preferences.getBoolean(Keys.CLOCK24, false)) "HH:mm" else "h:mm",
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
        if (hasFocus) window.immersive()
    }

    override fun onDestroy() {
        music?.stop()
        banners?.stop()
        environments.forEach { it.stop() }
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    private companion object {
        /** About one space width between letters (the label used to be spelled with spaces, e.g. "C I V I C"). */
        const val VEHICLE_LETTER_SPACING = .25f
        const val LABEL_GREY = 0xffadadb0.toInt()
    }
}
