package app.flare.auto

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.provider.Settings
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextClock
import android.widget.TextView
import org.json.JSONArray

/**
 * Builds Home cards. One instance renders the live Home page (and keeps references to the views that update);
 * editors and off-screen pages use their own instances with [preview] or [sampleData] set.
 */
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
    private val textScale = prefs.textScale
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

    private fun dp(value: Int) = context.dp(value)

    private fun shape(radius: Int): Drawable =
        if (prefs.glassCards) HomeGlassDrawable(theme.card, theme.cardBorder, dp(radius).toFloat())
        else GlassDrawable(theme.card, dp(radius).toFloat(), theme.cardBorder)

    private fun text(value: String, size: Int, color: Int, bold: Boolean): TextView =
        TextView(context).apply {
            text = value
            setTextColor(color)
            gravity = Gravity.CENTER_VERTICAL
            textSize = size * textScale
            if (bold) setTypeface(Typeface.DEFAULT, Typeface.BOLD)
        }

    private fun column() = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
    private fun row() = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
    }

    private fun pad(view: View, horizontal: Int, vertical: Int) =
        view.setPadding(dp(horizontal), dp(vertical), dp(horizontal), dp(vertical))

    private fun projectionButton(label: String, icon: Int, action: () -> Unit): View {
        val pill = row().apply {
            gravity = Gravity.CENTER
            minimumHeight = dp(56)
            contentDescription = label
            background = GradientDrawable().apply {
                setColor((theme.background and 0x00ffffff) or 0xb3000000.toInt())
                cornerRadius = dp(7).toFloat()
                setStroke(1, theme.cardBorder)
            }
        }
        pill.addView(ImageView(context).apply { setImageResource(icon); setColorFilter(white) }, LinearLayout.LayoutParams(dp(18), dp(18)))
        val name = text(label, 12, white, false).apply {
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            gravity = Gravity.CENTER
        }
        pill.addView(name, LinearLayout.LayoutParams(WRAP, MATCH).apply { leftMargin = dp(5) })
        pill.setOnClickListener { action() }
        return pill
    }

    private fun projectionButtons(into: LinearLayout) {
        into.addView(projectionButton("CarPlay", R.drawable.ic_carplay_mark) { onProjection(KnownApps.CARPLAY) },
            LinearLayout.LayoutParams(0, dp(56), 1f).apply { rightMargin = dp(5) })
        into.addView(projectionButton("Android Auto", R.drawable.ic_android_auto_mark) { onProjection(KnownApps.ANDROID_AUTO) },
            LinearLayout.LayoutParams(0, dp(56), 1f).apply { leftMargin = dp(5) })
    }

    /** Adds the card of type [card] to [content] with layout [weight]. */
    fun build(content: LinearLayout, card: String, compact: Boolean = false, weight: Float = 1f) {
        val params = LinearLayout.LayoutParams(0, MATCH, weight)
        when (card) {
            "clock", "clock_only", "clock_minimal" -> buildClockCard(content, params, compact, card)
            "projection" -> buildProjectionCard(content, params)
            "media", "media_wide", "media_compact" -> buildMediaCard(content, params, compact || card != "media", card == "media_compact")
            "media_album" -> buildAlbumCard(content, params)
            "clock_date" -> buildDateClockCard(content, params)
            "weather", "weather_solar" -> buildWeatherCard(content, params, card == "weather_solar")
            "apps" -> buildAppCard(content, params)
            "offline_map" -> OfflineMapCard(activity, context, sampleData, preview = true).also { maps.add(it); content.addView(it, params) }
            else -> if (HostedWidgets.id(card) != null) content.addView(HostedWidgets.view(activity, context, card, preview), params)
        }
        if (preview) environments.forEach { it.updateSolar() }
    }

    private fun sample(label: TextView, icon: SolarIcon, mode: String) {
        label.text = if (mode == "sun") "06:24 AM" else "28°"
        icon.setWeather(if (mode == "sun") null else 2)
    }

    private fun buildWeatherCard(content: LinearLayout, params: LinearLayout.LayoutParams, both: Boolean) {
        val box = column().apply { gravity = Gravity.CENTER; background = shape(12); pad(this, 16, 12) }
        content.addView(box, params)
        for (mode in if (both) listOf("auto", "sun") else listOf("auto")) {
            val line = row().apply { gravity = Gravity.CENTER }
            val icon = SolarIcon(context, muted)
            val label = text("", 18, white, false)
            line.addView(icon, LinearLayout.LayoutParams(dp(38), dp(38)))
            line.addView(label)
            box.addView(line)
            if (sampleData) sample(label, icon, mode) else environments.add(EnvironmentInfo(context, label, icon, mode))
        }
    }

    private fun buildProjectionCard(content: LinearLayout, params: LinearLayout.LayoutParams) {
        val card = row().apply { background = shape(12); pad(this, 10, 8); gravity = Gravity.CENTER }
        content.addView(card, params)
        projectionButtons(card)
    }

    private fun buildAppCard(content: LinearLayout, params: LinearLayout.LayoutParams) {
        val card = column().apply { background = shape(12) }
        content.addView(card, params)
        val editor = Intent(activity, AppWidgetActivity::class.java)
        card.setOnLongClickListener { activity.startActivity(editor); true }
        val apps = try { JSONArray(prefs.getString(Keys.LAYOUT_APPS, "[]")) } catch (_: Exception) { JSONArray() }
        if (apps.length() == 0) {
            card.addView(text("+\nAdd application shortcuts", 16, muted, false).apply {
                gravity = Gravity.CENTER
                setOnClickListener { activity.startActivity(editor) }
            }, LinearLayout.LayoutParams(MATCH, MATCH))
            return
        }
        val grid = GridLayout(context).apply { columnCount = 4 }
        card.addView(grid, LinearLayout.LayoutParams(MATCH, MATCH))
        for (index in 0 until minOf(LayoutPrefs.MAX_APP_SHORTCUTS, apps.length())) {
            val target = apps.optString(index)
            val cell = column().apply { gravity = Gravity.CENTER }
            cell.addView(ImageView(context).apply { setImageDrawable(AppIcons.get(context, target)) }, LinearLayout.LayoutParams(dp(36), dp(36)))
            cell.addView(text(AppLookup.label(context, target) ?: "App", 12, white, false).apply {
                gravity = Gravity.CENTER
                isSingleLine = true
            })
            cell.setOnClickListener { onTarget(target) }
            grid.addView(cell, GridLayout.LayoutParams().apply {
                width = 0
                height = 0
                columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                rowSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
            })
        }
    }

    private fun buildClockCard(content: LinearLayout, params: LinearLayout.LayoutParams, compact: Boolean, kind: String) {
        val card = column().apply {
            pad(this, 22, 12)
            background = shape(12)
        }
        content.addView(card, params)
        val badges = row()
        fun corner(kind: String, alignRight: Boolean): View {
            if (kind == "off") return View(context)
            val holder = row().apply { gravity = (if (alignRight) Gravity.RIGHT else Gravity.LEFT) or Gravity.CENTER_VERTICAL }
            if (kind == "trim") {
                val badgeText = (prefs.getString(Keys.BADGE_TEXT, Defaults.TRIM_BADGE) ?: Defaults.TRIM_BADGE).trim()
                val badgeColor = prefs.getInt(Keys.BADGE_COLOR, theme.accent)
                if (badgeText.equals(Defaults.TRIM_BADGE_GLYPH, true)) holder.addView(TrimBadge(context, badgeColor), LinearLayout.LayoutParams(dp(37), dp(27)))
                else if (badgeText.isNotEmpty()) holder.addView(text(badgeText, 13, badgeColor, true))
                return holder
            }
            val solar = text("", 11, muted, false)
            val solarIcon = SolarIcon(context, muted)
            holder.addView(solarIcon, LinearLayout.LayoutParams(dp(24), dp(38)))
            holder.addView(solar, LinearLayout.LayoutParams(WRAP, dp(38)).apply { leftMargin = dp(5) })
            if (sampleData) sample(solar, solarIcon, kind) else environments.add(EnvironmentInfo(context, solar, solarIcon, kind))
            return holder
        }
        badges.addView(corner(prefs.getString(Keys.WIDGET_LEFT, "auto") ?: "auto", false), LinearLayout.LayoutParams(0, dp(38), 1f))
        badges.addView(corner(prefs.getString(Keys.WIDGET_RIGHT, "trim") ?: "trim", true), LinearLayout.LayoutParams(0, dp(38), 1f))
        card.addView(badges, LinearLayout.LayoutParams(MATCH, dp(38)))
        // Home dims after sunset from the first environment; keep one even when no corner shows solar data.
        if (environments.isEmpty() && !sampleData) environments.add(EnvironmentInfo(context, TextView(context), SolarIcon(context, muted), "sun"))

        val center = column().apply { gravity = Gravity.CENTER }
        card.addView(center, LinearLayout.LayoutParams(MATCH, 0, 1f))
        val brand = prefs.getString(Keys.BRAND_TEXT, Defaults.VEHICLE_NAME) ?: Defaults.VEHICLE_NAME
        center.addView(text(brand, 13, prefs.getInt(Keys.BRAND_COLOR, theme.accent), true).apply {
            visibility = if (brand.isEmpty() || kind == "clock_minimal") View.GONE else View.VISIBLE
            if (compact) { maxLines = 1; ellipsize = TextUtils.TruncateAt.END }
            letterSpacing = .35f
            includeFontPadding = false
            gravity = Gravity.CENTER
        })
        val format = if (prefs.clock24) "HH:mm" else "h:mm"
        val clock = TextClock(context).apply {
            format12Hour = format
            format24Hour = format
            textSize = 76f
            setAutoSizeTextTypeUniformWithConfiguration(32, 76, 1, TypedValue.COMPLEX_UNIT_SP)
            maxLines = 1
            includeFontPadding = false
            setTextColor(white)
            typeface = ClockStyles.typeface(prefs.getString(Keys.HOME_CLOCK_STYLE, "classic"), false)
            gravity = Gravity.CENTER
        }
        center.addView(clock, LinearLayout.LayoutParams(MATCH, 0, 1f))
        center.addView(View(context).apply { background = HomeChrome.accent(theme) },
            LinearLayout.LayoutParams(dp(68), dp(2)).apply { topMargin = dp(3); bottomMargin = dp(5) })

        val projection = row().apply {
            if (kind != "clock") visibility = View.GONE
            gravity = Gravity.CENTER
            setPadding(dp(12), 0, dp(12), 0)
            minimumHeight = dp(56)
        }
        card.addView(projection, LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = dp(14) })
        projectionButtons(projection)
    }

    private fun buildMediaCard(content: LinearLayout, params: LinearLayout.LayoutParams, compact: Boolean, controlsOnly: Boolean = false) {
        val audio = column().apply {
            pad(this, 16, 10)
            background = shape(12)
        }
        content.addView(audio, params)
        val source = text("", 11, muted, false).apply {
            gravity = Gravity.START or Gravity.CENTER_VERTICAL
            visibility = View.GONE
        }
        sourceLabel = source
        audio.addView(source, LinearLayout.LayoutParams(MATCH, WRAP))
        val hint = text("Enable media access", 12, theme.accent, false).apply {
            if (preview) visibility = View.GONE
            gravity = Gravity.CENTER
            setOnClickListener { activity.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
        }
        mediaAccessHint = hint
        audio.addView(hint, LinearLayout.LayoutParams(MATCH, WRAP))
        val metadata = (if (compact) row() else column()).apply { gravity = Gravity.CENTER }
        audio.addView(metadata, LinearLayout.LayoutParams(MATCH, 0, 1f))
        val art = ImageView(context).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            background = GradientDrawable().apply {
                setColor(theme.background)
                cornerRadius = dp(8).toFloat()
            }
            clipToOutline = true
        }
        albumArt = art
        val artArea = FrameLayout(context)
        if (controlsOnly) artArea.visibility = View.GONE
        metadata.addView(artArea, if (compact) LinearLayout.LayoutParams(0, MATCH, .42f) else LinearLayout.LayoutParams(MATCH, 0, 1f))
        artArea.addView(art, FrameLayout.LayoutParams(MATCH, MATCH, Gravity.CENTER))
        // Square artwork: as large as the area allows, minus a small margin.
        artArea.addOnLayoutChangeListener { _, left, top, right, bottom, _, _, _, _ ->
            val size = maxOf(1, minOf(right - left, bottom - top) - dp(10))
            art.layoutParams?.let {
                if (it.width != size || it.height != size) {
                    it.width = size
                    it.height = size
                    art.layoutParams = it
                }
            }
        }
        val labels = column().apply { setPadding(0, dp(2), 0, dp(3)); if (compact) gravity = Gravity.CENTER }
        metadata.addView(labels, if (compact) LinearLayout.LayoutParams(0, MATCH, .58f) else LinearLayout.LayoutParams(MATCH, WRAP))
        val title = text("No media playing", 16, white, true).apply {
            isSingleLine = true
            ellipsize = TextUtils.TruncateAt.MARQUEE
            marqueeRepeatLimit = -1
            isSelected = true
            gravity = Gravity.CENTER
        }
        trackTitle = title
        labels.addView(title)
        trackArtist = text("", 12, muted, false).apply {
            isSingleLine = true
            gravity = Gravity.CENTER
        }.also { labels.addView(it) }
        val seek = row()
        seekRow = seek
        val elapsedText = text("0:00", 11, muted, false).also { elapsed = it }
        val totalText = text("0:00", 11, muted, false).also { total = it }
        val bar = SeekBar(context).apply {
            progressTintList = ColorStateList.valueOf(theme.accent)
            thumbTintList = ColorStateList.valueOf(theme.accent)
            progressBackgroundTintList = ColorStateList.valueOf(theme.textSecondary)
        }
        progress = bar
        seek.addView(elapsedText)
        seek.addView(bar, LinearLayout.LayoutParams(0, dp(40), 1f))
        seek.addView(totalText)
        seek.minimumHeight = dp(40)
        (if (compact) labels else audio).addView(seek)
        bar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, value: Int, fromUser: Boolean) = Unit
            override fun onStartTrackingTouch(seekBar: SeekBar) = onDragging(true)
            override fun onStopTrackingTouch(seekBar: SeekBar) {
                onDragging(false)
                onSeek(seekBar.progress.toLong())
            }
        })
        val controls = row().apply { gravity = Gravity.CENTER; minimumHeight = dp(56) }
        (if (compact) labels else audio).addView(controls, LinearLayout.LayoutParams(MATCH, dp(56)))
        audio.addOnLayoutChangeListener { _, left, _, right, _, oldLeft, _, oldRight, _ ->
            if (right - left != oldRight - oldLeft) updateMediaWidth(right - left, controls, seek, title)
        }
        for (key in MEDIA_KEYS) {
            val control = ImageView(context).apply {
                setPadding(dp(9), dp(9), dp(9), dp(9))
                setImageDrawable(MediaGlyph(when (key) {
                    KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> "play"
                    KeyEvent.KEYCODE_MEDIA_PREVIOUS -> "previous"
                    else -> "next"
                }, white))
                setOnClickListener { onMedia(key) }
            }
            if (key == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) playbackButton = control
            controls.addView(control, LinearLayout.LayoutParams(0, MATCH, 1f).apply { rightMargin = dp(6) })
        }
    }

    /**
     * "Album player": artwork upper left with the title and artist beside it, the progress bar with elapsed and total
     * times below, and previous / play-pause / next in one evenly spaced row. Uses the same views as the other media
     * cards, so Home fills them the same way (artwork or the note glyph, marquee title, seek row only when the track
     * has a duration).
     */
    private fun buildAlbumCard(content: LinearLayout, params: LinearLayout.LayoutParams) {
        val card = column().apply {
            pad(this, 16, 12)
            background = shape(12)
        }
        content.addView(card, params)
        mediaAccessHint = text("Enable media access", 12, theme.accent, false).apply {
            if (preview) visibility = View.GONE
            gravity = Gravity.CENTER
            setOnClickListener { activity.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
        }.also { card.addView(it, LinearLayout.LayoutParams(MATCH, WRAP)) }
        val top = row().apply { gravity = Gravity.TOP }
        card.addView(top, LinearLayout.LayoutParams(MATCH, 0, 1f))
        val art = ImageView(context).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            background = GradientDrawable().apply { setColor(theme.background); cornerRadius = dp(10).toFloat() }
            clipToOutline = true
        }
        albumArt = art
        top.addView(art, LinearLayout.LayoutParams(dp(96), dp(96)))
        // Square artwork in the upper left, as tall as the row (at most 45% of its width); the text sits right beside it.
        top.addOnLayoutChangeListener { _, left, top2, right, bottom, _, _, _, _ ->
            val size = maxOf(1, minOf(bottom - top2, (right - left) * 45 / 100))
            art.layoutParams?.let { if (it.width != size || it.height != size) { it.width = size; it.height = size; art.post { art.layoutParams = it } } }
        }
        val labels = column().apply { setPadding(dp(14), dp(2), 0, 0) }
        top.addView(labels, LinearLayout.LayoutParams(0, MATCH, 1f))
        sourceLabel = text("", 11, muted, false).apply { visibility = View.GONE; isSingleLine = true; ellipsize = TextUtils.TruncateAt.END }
            .also { labels.addView(it) }
        trackTitle = text("No media playing", 17, white, true).apply {
            isSingleLine = true
            ellipsize = TextUtils.TruncateAt.MARQUEE
            marqueeRepeatLimit = -1
            isSelected = true
        }.also { labels.addView(it, LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = dp(2) }) }
        trackArtist = text("", 13, muted, false).apply {
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
        }.also { labels.addView(it, LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = dp(3) }) }
        val seek = row()
        seekRow = seek
        val elapsedText = text("0:00", 11, muted, false).also { elapsed = it }
        val totalText = text("0:00", 11, muted, false).also { total = it }
        val bar = SeekBar(context).apply {
            progressTintList = ColorStateList.valueOf(theme.accent)
            thumbTintList = ColorStateList.valueOf(theme.accent)
            progressBackgroundTintList = ColorStateList.valueOf(theme.textSecondary)
        }
        progress = bar
        seek.addView(elapsedText)
        seek.addView(bar, LinearLayout.LayoutParams(0, dp(36), 1f))
        seek.addView(totalText)
        card.addView(seek, LinearLayout.LayoutParams(MATCH, dp(36)).apply { topMargin = dp(6) })
        bar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, value: Int, fromUser: Boolean) = Unit
            override fun onStartTrackingTouch(seekBar: SeekBar) = onDragging(true)
            override fun onStopTrackingTouch(seekBar: SeekBar) {
                onDragging(false)
                onSeek(seekBar.progress.toLong())
            }
        })
        val controls = row().apply { gravity = Gravity.CENTER }
        card.addView(controls, LinearLayout.LayoutParams(MATCH, dp(52)))
        card.addOnLayoutChangeListener { _, left, _, right, _, oldLeft, _, oldRight, _ ->
            if (right - left != oldRight - oldLeft) {
                if (!preview) onNarrow(false)
                seek.visibility = if (duration() > 0) View.VISIBLE else View.GONE
            }
        }
        for (key in MEDIA_KEYS) {
            val control = ImageView(context).apply {
                setPadding(dp(10), dp(10), dp(10), dp(10))
                setImageDrawable(MediaGlyph(when (key) {
                    KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> "play"
                    KeyEvent.KEYCODE_MEDIA_PREVIOUS -> "previous"
                    else -> "next"
                }, white))
                contentDescription = when (key) {
                    KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> "Play or pause"
                    KeyEvent.KEYCODE_MEDIA_PREVIOUS -> "Previous track"
                    else -> "Next track"
                }
                setOnClickListener { onMedia(key) }
            }
            if (key == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) playbackButton = control
            controls.addView(control, LinearLayout.LayoutParams(0, MATCH, 1f))
        }
    }

    /**
     * "Clock with date": the time as large as the card allows (12/24-hour setting, Home clock style), the date
     * below it and a short accent line. Text sizes follow the launcher text-size setting.
     */
    private fun buildDateClockCard(content: LinearLayout, params: LinearLayout.LayoutParams) {
        val card = column().apply {
            pad(this, 18, 12)
            gravity = Gravity.CENTER
            background = shape(12)
        }
        content.addView(card, params)
        // Home dims after sunset from the first environment (as the other clock cards do).
        if (environments.isEmpty() && !sampleData) environments.add(EnvironmentInfo(context, TextView(context), SolarIcon(context, muted), "sun"))
        val format = if (prefs.clock24) "HH:mm" else "h:mm"
        val clock = TextClock(context).apply {
            format12Hour = format
            format24Hour = format
            textSize = 84f * textScale
            setAutoSizeTextTypeUniformWithConfiguration(24, maxOf(24, (84 * textScale).toInt()), 1, TypedValue.COMPLEX_UNIT_SP)
            maxLines = 1
            includeFontPadding = false
            setTextColor(white)
            typeface = ClockStyles.typeface(prefs.getString(Keys.HOME_CLOCK_STYLE, "classic"), false)
            gravity = Gravity.CENTER
        }
        card.addView(clock, LinearLayout.LayoutParams(MATCH, 0, 1f))
        card.addView(View(context).apply { background = HomeChrome.accent(theme) },
            LinearLayout.LayoutParams(dp(56), dp(2)).apply { topMargin = dp(4); bottomMargin = dp(6); gravity = Gravity.CENTER_HORIZONTAL })
        val date = TextClock(context).apply {
            format12Hour = "EEEE, d MMMM"
            format24Hour = "EEEE, d MMMM"
            textSize = 15f * textScale
            setTextColor(muted)
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            gravity = Gravity.CENTER
        }
        card.addView(date, LinearLayout.LayoutParams(MATCH, WRAP))
    }

    private fun updateMediaWidth(pixels: Int, controls: LinearLayout, seek: LinearLayout, title: TextView) {
        val width = (pixels / context.resources.displayMetrics.density).toInt()
        if (!preview) onNarrow(false)
        seek.visibility = if (duration() > 0) View.VISIBLE else View.GONE
        title.textSize = (if (width < 230) 14 else 16) * textScale
        controls.visibility = View.VISIBLE
    }

    private companion object {
        val MEDIA_KEYS = intArrayOf(KeyEvent.KEYCODE_MEDIA_PREVIOUS, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_MEDIA_NEXT)
    }
}
