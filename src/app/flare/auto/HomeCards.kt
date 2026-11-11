package app.flare.auto

import android.app.Activity
import android.content.Context
import android.content.ComponentName
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import org.json.JSONArray

internal class HomeCards(
    private val activity: Activity,
    private val prefs: SharedPreferences,
    private val theme: ThemeTokens,
    private val preview: Boolean = false,
    private val sampleData: Boolean = false,
    private val context: Context = activity,
    private val onTarget: (String) -> Unit = {},
    private val onProjection: (Array<String>) -> Unit = {},
    private val onMedia: (Int) -> Unit = {},
    private val onSeek: (Long) -> Unit = {},
    private val onDragging: (Boolean) -> Unit = {},
    private val onNarrow: (Boolean) -> Unit = {},
    private val duration: () -> Long = { -1L }
) {
    private val resources get() = context.resources
    private val packageManager get() = activity.packageManager
    private val packageName get() = activity.packageName
    private val panel = theme.card
    private val edge = theme.cardBorder
    private val white = theme.textPrimary
    private val muted = theme.textSecondary
    val environments = ArrayList<EnvironmentInfo>()
    val maps = ArrayList<OfflineMapCard>()
    var playbackButton: ImageView? = null
    var sourceLabel: TextView? = null
    var mediaAccessHint: TextView? = null
    var albumArt: ImageView? = null
    var trackTitle: TextView? = null
    var trackArtist: TextView? = null
    var elapsed: TextView? = null
    var total: TextView? = null
    var progress: SeekBar? = null
    var seekRow: LinearLayout? = null
    private fun launchTarget(target: String) = onTarget(target)
    private fun launchFirst(packages: Array<String>) = onProjection(packages)
    private fun media(key: Int) = onMedia(key)

    private fun dp(value: Float): Int = (resources.displayMetrics.density * value + .5f).toInt()
    private fun dp(value: Int): Int = dp(value.toFloat())

    private fun shape(color: Int, radius: Int): Drawable =
        if (prefs.getBoolean("glass_cards", true)) {
            HomeGlassDrawable(color, edge, dp(radius).toFloat())
        } else {
            GlassDrawable(color, dp(radius).toFloat(), edge)
        }

    private fun text(value: String, size: Int, color: Int, bold: Boolean): TextView =
        TextView(context).apply {
            text = value
            setTextColor(color)
            gravity = Gravity.CENTER_VERTICAL
            textSize = size * prefs.getInt("text", 100) / 100f
            if (bold) setTypeface(Typeface.DEFAULT, Typeface.BOLD)
        }

    private fun column() = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
    private fun row() = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
    }

    private fun pad(view: View, horizontal: Int, vertical: Int) {
        view.setPadding(dp(horizontal), dp(vertical), dp(horizontal), dp(vertical))
    }

    private fun accentGradient(): GradientDrawable {
        val end = (theme.accent and 0x00ffffff) or 0x18000000
        val middle = Color.rgb(
            (Color.red(theme.accent) * 7 + Color.red(muted) * 3) / 10,
            (Color.green(theme.accent) * 7 + Color.green(muted) * 3) / 10,
            (Color.blue(theme.accent) * 7 + Color.blue(muted) * 3) / 10
        )
        return GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, intArrayOf(end, middle, end))
    }

    private fun projectionButton(label: String, icon: String, action: () -> Unit): View {
        val pill = row().apply {
            gravity = Gravity.CENTER
            minimumHeight = dp(56)
            contentDescription = label
            val background = GradientDrawable().apply {
                setColor((theme.background and 0x00ffffff) or 0xb3000000.toInt())
                cornerRadius = dp(7).toFloat()
                setStroke(1, theme.cardBorder)
            }
            setBackground(background)
        }
        val mark = ImageView(context)
        val id = resources.getIdentifier(icon, "drawable", packageName)
        if (id != 0) {
            mark.setImageResource(id)
            mark.setColorFilter(white)
            pill.addView(mark, LinearLayout.LayoutParams(dp(18), dp(18)))
        }
        val name = text(label, 12, white, false).apply {
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            gravity = Gravity.CENTER
        }
        val labelParams = LinearLayout.LayoutParams(-2, -1).apply { leftMargin = dp(5) }
        pill.addView(name, labelParams)
        pill.setOnClickListener { action() }
        return pill
    }

    fun build(content: LinearLayout, card: String, compact: Boolean = false) {
        when (card) {
            "clock", "clock_only", "clock_minimal" -> buildClockCard(content, compact, card)
            "projection" -> buildProjectionCard(content)
            "media", "media_wide", "media_compact" -> buildMediaCard(content, compact || card != "media", card == "media_compact")
            "weather", "weather_solar" -> buildWeatherCard(content,card=="weather_solar")
            "apps" -> buildAppCard(content)
            "offline_map" -> { val map=OfflineMapCard(activity,context,sampleData);maps.add(map);content.addView(map,LinearLayout.LayoutParams(0,-1,1f)) }
            else ->if(HostedWidgets.id(card)!=null) content.addView(HostedWidgets.view(activity,context,card,preview),LinearLayout.LayoutParams(0,-1,1f))
        }
        if (preview) environments.forEach { it.updateSolar() }
    }

    private fun buildWeatherCard(content:LinearLayout,both:Boolean) {
        val box=column().apply { gravity=Gravity.CENTER; background=shape(panel,12); pad(this,16,12) }
        content.addView(box,LinearLayout.LayoutParams(0,-1,1f))
        for(mode in if(both) listOf("auto","sun") else listOf("auto")) {
            val row=row().apply { gravity=Gravity.CENTER }
            val icon=SolarIcon(context,muted); val label=text("",18,white,false)
            row.addView(icon,LinearLayout.LayoutParams(dp(38),dp(38))); row.addView(label)
            box.addView(row);if(sampleData) { label.text=if(mode=="sun") "06:24 AM" else "28°";icon.setWeather(if(mode=="sun") null else 2) } else environments.add(EnvironmentInfo(context,label,icon,mode))
        }
    }

    private fun buildProjectionCard(content:LinearLayout) {
        val card=row().apply { background=shape(panel,12); pad(this,10,8); gravity=Gravity.CENTER }
        content.addView(card,LinearLayout.LayoutParams(0,-1,1f))
        card.addView(projectionButton("CarPlay","ic_carplay_mark") { launchFirst(arrayOf("com.shihab.diplay","com.shihab.diplay.hudtest")) },LinearLayout.LayoutParams(0,dp(56),1f).apply { rightMargin=dp(5) })
        card.addView(projectionButton("Android Auto","ic_android_auto_mark") { launchFirst(arrayOf("com.andrerinas.headunitrevived")) },LinearLayout.LayoutParams(0,dp(56),1f).apply { leftMargin=dp(5) })
    }

    private fun buildAppCard(content: LinearLayout) {
        val card = column().apply { background = shape(panel, 12) }
        content.addView(card, LinearLayout.LayoutParams(0, -1, 1f))
        card.setOnLongClickListener { activity.startActivity(Intent(activity,AppWidgetActivity::class.java)); true }
        val apps = try { JSONArray(prefs.getString("layout_apps", "[]")) } catch (_: Exception) { JSONArray() }
        if (apps.length() == 0) {
            card.addView(text("+\nAdd application shortcuts",16,theme.textSecondary,false).apply {
                gravity=Gravity.CENTER; setOnClickListener { activity.startActivity(Intent(activity,AppWidgetActivity::class.java)) }
            },LinearLayout.LayoutParams(-1,-1))
            return
        }
        val grid = android.widget.GridLayout(context).apply { columnCount = 4 }
        card.addView(grid, LinearLayout.LayoutParams(-1, -1))
        try {
            val apps = JSONArray(prefs.getString("layout_apps", "[]"))
            for (index in 0 until minOf(8, apps.length())) {
                val target = apps.getString(index)
                val cell = column().apply { gravity = Gravity.CENTER }
                val image = ImageView(context)
                try {
                    image.setImageDrawable(AsyncImages.appIcon(context, target))
                } catch (_: Exception) {
                }
                cell.addView(image, LinearLayout.LayoutParams(dp(36), dp(36)))
                var name = "App"
                try {
                    name = packageManager.getActivityInfo(ComponentName.unflattenFromString(target)!!, 0)
                        .loadLabel(packageManager).toString()
                } catch (_: Exception) {
                }
                cell.addView(text(name, 12, white, false).apply {
                    gravity = Gravity.CENTER
                    isSingleLine = true
                })
                cell.setOnClickListener { launchTarget(target) }
                val params = android.widget.GridLayout.LayoutParams().apply {
                    width = 0
                    height = 0
                    columnSpec = android.widget.GridLayout.spec(android.widget.GridLayout.UNDEFINED, 1f)
                    rowSpec = android.widget.GridLayout.spec(android.widget.GridLayout.UNDEFINED, 1f)
                }
                grid.addView(cell, params)
            }
        } catch (_: Exception) {
        }
    }

    private fun buildClockCard(content: LinearLayout, compact: Boolean, kind: String) {
        val card = column().apply {
            pad(this, 22, 12)
            background = shape(panel, 12)
        }
        content.addView(card, LinearLayout.LayoutParams(0, -1, 1f))
        val badges = row()
        val leftWidget = prefs.getString("widget_left", "auto") ?: "auto"
        val rightWidget = prefs.getString("widget_right", "trim") ?: "trim"
        fun widget(kind: String, alignRight: Boolean): View {
            if (kind == "trim") {
                val holder = row().apply { gravity = if (alignRight) Gravity.RIGHT or Gravity.CENTER_VERTICAL else Gravity.LEFT or Gravity.CENTER_VERTICAL }
                val badgeText = (prefs.getString("badge_text", "RS") ?: "RS").trim()
                val badgeColor = prefs.getInt("badge_color", theme.accent)
                if (badgeText.equals("RS", true)) holder.addView(CivicBadge(context, true, badgeColor), LinearLayout.LayoutParams(dp(37), dp(27)))
                else if (badgeText.isNotEmpty()) holder.addView(text(badgeText, 13, badgeColor, true))
                return holder
            }
            if (kind == "off") return View(context)
            val holder = row().apply { gravity = if (alignRight) Gravity.RIGHT or Gravity.CENTER_VERTICAL else Gravity.LEFT or Gravity.CENTER_VERTICAL }
            val solar = text("", 11, muted, false)
            val solarIcon = SolarIcon(context, muted)
            holder.addView(solarIcon, LinearLayout.LayoutParams(dp(24), dp(38)))
            holder.addView(solar, LinearLayout.LayoutParams(-2, dp(38)).apply { leftMargin = dp(5) })
            val info = EnvironmentInfo(context, solar, solarIcon, kind)
            if(sampleData) { solar.text=if(kind=="sun") "06:24 AM" else "28°";solarIcon.setWeather(if(kind=="sun") null else 2) } else environments.add(info)
            return holder
        }
        badges.addView(widget(leftWidget, false), LinearLayout.LayoutParams(0, dp(38), 1f))
        badges.addView(widget(rightWidget, true), LinearLayout.LayoutParams(0, dp(38), 1f))
        if (kind != "clock_minimal") card.addView(badges, LinearLayout.LayoutParams(-1, dp(38)))
        if (environments.isEmpty() && !sampleData) environments.add(EnvironmentInfo(context, TextView(context), SolarIcon(context, muted), "sun"))

        val center = column().apply { gravity = Gravity.CENTER }
        card.addView(center, LinearLayout.LayoutParams(-1, 0, 1f))
        val brand = prefs.getString("brand_text", "CIVIC") ?: "CIVIC"
        center.addView(text(brand, 13, prefs.getInt("brand_color", theme.accent), true).apply {
            visibility = if (brand.isEmpty() || kind == "clock_minimal") View.GONE else View.VISIBLE
            if (compact) { maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END }
            letterSpacing = .35f
            includeFontPadding = false
            gravity = Gravity.CENTER
        })
        val clock = android.widget.TextClock(context)
        val format = if (prefs.getBoolean("clock24", false)) "HH:mm" else "h:mm"
        clock.format12Hour = format
        clock.format24Hour = format
        clock.textSize = 76f
        clock.setAutoSizeTextTypeUniformWithConfiguration(32, 76, 1, android.util.TypedValue.COMPLEX_UNIT_SP)
        clock.maxLines = 1
        clock.includeFontPadding = false
        clock.setTextColor(white)
        clock.typeface = ClockStyles.typeface(prefs.getString("home_clock_style", "classic"), false)
        clock.gravity = Gravity.CENTER
        center.addView(clock, LinearLayout.LayoutParams(-1, 0, 1f))
        center.addView(View(context).apply { background = accentGradient() },
            LinearLayout.LayoutParams(dp(68), dp(2)).apply { topMargin = dp(3); bottomMargin = dp(5) })

        val projection = row().apply {
            if (kind != "clock") visibility = View.GONE
            gravity = Gravity.CENTER
            setPadding(dp(12), 0, dp(12), 0)
            minimumHeight = dp(56)
        }
        card.addView(projection, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(14) })
        projection.addView(
            projectionButton("CarPlay", "ic_carplay_mark") {
                launchFirst(arrayOf("com.shihab.diplay", "com.shihab.diplay.hudtest"))
            },
            LinearLayout.LayoutParams(0, dp(56), 1f).apply { rightMargin = dp(5) }
        )
        projection.addView(
            projectionButton("Android Auto", "ic_android_auto_mark") {
                launchFirst(arrayOf("com.andrerinas.headunitrevived"))
            },
            LinearLayout.LayoutParams(0, dp(56), 1f).apply { leftMargin = dp(5) }
        )
    }

    private fun buildMediaCard(content: LinearLayout, compact: Boolean, controlsOnly:Boolean=false) {
        val right = column()
        content.addView(right, LinearLayout.LayoutParams(0, -1, 1f))
        val audio = column().apply {
            pad(this, 16, 10)
            background = shape(panel, 12)
        }
        right.addView(audio, LinearLayout.LayoutParams(-1, 0, 1f))
        sourceLabel = text("", 11, muted, false).apply {
            gravity = Gravity.START or Gravity.CENTER_VERTICAL
            visibility = View.GONE
        }
        audio.addView(sourceLabel, LinearLayout.LayoutParams(-1, -2))
        mediaAccessHint = text("Enable media access", 12, theme.accent, false).apply {
            if (preview) visibility = View.GONE
            gravity = Gravity.CENTER
            setOnClickListener { activity.startActivity(Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")) }
        }
        audio.addView(mediaAccessHint, LinearLayout.LayoutParams(-1, -2))
        val metadata = (if (compact) row() else column()).apply { gravity = Gravity.CENTER }
        audio.addView(metadata, LinearLayout.LayoutParams(-1, 0, 1f))
        albumArt = ImageView(context).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            background = GradientDrawable().apply {
                setColor(theme.background)
                cornerRadius = dp(8).toFloat()
            }
            clipToOutline = true
        }
        val artArea = FrameLayout(context)
        if(controlsOnly) artArea.visibility=View.GONE
        metadata.addView(artArea, if (compact) LinearLayout.LayoutParams(0, -1, .42f) else LinearLayout.LayoutParams(-1, 0, 1f))
        artArea.addView(albumArt, FrameLayout.LayoutParams(-1, -1, Gravity.CENTER))
        val cardArt = albumArt!!
        artArea.addOnLayoutChangeListener { _, left, top, rightEdge, bottom, oldLeft, oldTop, oldRight, oldBottom ->
            val size = maxOf(1, minOf(rightEdge - left, bottom - top) - dp(10))
            cardArt.layoutParams?.let {
                if (it.width != size || it.height != size) {
                    it.width = size
                    it.height = size
                    cardArt.layoutParams = it
                }
            }
        }
        val labels = column().apply { setPadding(0, dp(2), 0, dp(3)); if (compact) gravity = Gravity.CENTER }
        metadata.addView(labels, if (compact) LinearLayout.LayoutParams(0, -1, .58f) else LinearLayout.LayoutParams(-1, -2))
        trackTitle = text("No media playing", 16, white, true).apply {
            isSingleLine = true
            ellipsize = android.text.TextUtils.TruncateAt.MARQUEE
            marqueeRepeatLimit = -1
            isSelected = true
            gravity = Gravity.CENTER
        }
        labels.addView(trackTitle)
        trackArtist = text("", 12, muted, false).apply {
            isSingleLine = true
            gravity = Gravity.CENTER
        }
        labels.addView(trackArtist)
        seekRow = row()
        elapsed = text("0:00", 11, muted, false)
        total = text("0:00", 11, muted, false)
        progress = SeekBar(context).apply {
            progressTintList = android.content.res.ColorStateList.valueOf(theme.accent)
            thumbTintList = android.content.res.ColorStateList.valueOf(theme.accent)
            progressBackgroundTintList = android.content.res.ColorStateList.valueOf(theme.textSecondary)
        }
        seekRow!!.addView(elapsed)
        seekRow!!.addView(progress, LinearLayout.LayoutParams(0, dp(40), 1f))
        seekRow!!.addView(total)
        seekRow!!.minimumHeight = dp(40)
        (if (compact) labels else audio).addView(seekRow)
        progress!!.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, value: Int, fromUser: Boolean) = Unit
            override fun onStartTrackingTouch(seekBar: SeekBar) {
                onDragging(true)
            }
            override fun onStopTrackingTouch(seekBar: SeekBar) {
                onDragging(false)
                onSeek(seekBar.progress.toLong())
            }
        })
        val controls = row().apply { gravity = Gravity.CENTER; minimumHeight = dp(56) }
        (if (compact) labels else audio).addView(controls, LinearLayout.LayoutParams(-1, dp(56)))
        val cardSeek = seekRow!!
        val cardTitle = trackTitle!!
        audio.addOnLayoutChangeListener { _, left, _, rightEdge, _, oldLeft, _, oldRight, _ ->
            if (rightEdge - left != oldRight - oldLeft) {
                updateMediaWidth(rightEdge - left, controls, cardSeek, cardTitle)
            }
        }
        for (key in intArrayOf(KeyEvent.KEYCODE_MEDIA_PREVIOUS, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_MEDIA_NEXT)) {
            val control = ImageView(context).apply {
                setPadding(dp(9), dp(9), dp(9), dp(9))
                setImageDrawable(MediaGlyph(
                    when (key) {
                        KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> "play"
                        KeyEvent.KEYCODE_MEDIA_PREVIOUS -> "previous"
                        else -> "next"
                    }, white
                ))
                setOnClickListener { media(key) }
            }
            if (key == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) playbackButton = control
            controls.addView(control, LinearLayout.LayoutParams(0, -1, 1f).apply { rightMargin = dp(6) })
        }
    }

    private fun updateMediaWidth(pixels: Int, controls: LinearLayout, cardSeek: LinearLayout, cardTitle: TextView) {
        val width = (pixels / resources.displayMetrics.density).toInt()
        if (!preview) onNarrow(false)
        cardSeek.visibility = if (duration() > 0) View.VISIBLE else View.GONE
        cardTitle.textSize = (if (width < 230) 14 else 16) * prefs.getInt("text", 100) / 100f
        controls.visibility = View.VISIBLE
    }

}
