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
    /** Light theme: weather glyphs use their deeper colours (see [SolarIcon]). */
    private val lightTheme = android.graphics.Color.luminance(theme.background) > .5f
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

    /** Clock style of the card being built ([GridTile.style]); null uses the Home clock style preference. */
    private var clockStyle: String? = null

    /** Adds the card of type [card] to [content] with layout [weight]; [style]: a clock card's own clock style. */
    fun build(content: LinearLayout, card: String, compact: Boolean = false, weight: Float = 1f, style: String? = null) {
        clockStyle = style
        val params = LinearLayout.LayoutParams(0, MATCH, weight)
        when (card) {
            "clock", "clock_only", "clock_minimal" -> buildClockCard(content, params, compact, card)
            "projection" -> buildProjectionCard(content, params)
            "media", "media_wide", "media_compact" -> buildMediaCard(content, params, compact || card != "media", card == "media_compact")
            "media_album" -> buildAlbumCard(content, params)
            "clock_date" -> buildDateClockCard(content, params)
            "clock_gauge" -> buildGaugeClockCard(content, params)
            "clock_weather" -> buildWeatherClockCard(content, params)
            "clock_split" -> buildSplitClockCard(content, params)
            "weather", "weather_solar" -> buildWeatherCard(content, params, card == "weather_solar")
            "weather_compact" -> buildCompactWeatherCard(content, params)
            "weather_wide" -> buildWideWeatherCard(content, params)
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

    /** Text that shrinks (between [min] and [max] sp) to fit its box instead of clipping on small cards. */
    private fun fitted(view: TextView, min: Int, max: Int): TextView = view.apply {
        // maxLines, not singleLine: single-line mode scrolls horizontally, which turns auto-sizing off (text clipped).
        if (maxLines !in 2..10) maxLines = 1
        val low = (min * textScale).toInt().coerceAtLeast(1)
        setAutoSizeTextTypeUniformWithConfiguration(low, (max * textScale).toInt().coerceAtLeast(low + 1), 1,
            android.util.TypedValue.COMPLEX_UNIT_SP)
    }

    /**
     * "Compact weather": condition icon and temperature from the cached forecast. Without one it shows the next sunrise
     * or sunset (useful offline) and a short line saying why: weather off, no location, or no recent forecast.
     */
    private fun buildCompactWeatherCard(content: LinearLayout, params: LinearLayout.LayoutParams) {
        val box = column().apply { gravity = Gravity.CENTER; background = shape(12); pad(this, 12, 8) }
        content.addView(box, params)
        val line = row().apply { gravity = Gravity.CENTER }
        val icon = SolarIcon(context, muted, lightTheme)
        val temperature = fitted(text("", 40, white, true), 12, 44)
        line.addView(icon, LinearLayout.LayoutParams(dp(44), dp(44)))
        line.addView(temperature, LinearLayout.LayoutParams(0, MATCH, 1f).apply { leftMargin = dp(6) })
        box.addView(line, LinearLayout.LayoutParams(MATCH, 0, 1f))
        val status = fitted(text("", 12, muted, false).apply { maxLines = 2; gravity = Gravity.CENTER }, 9, 13)
        box.addView(status, LinearLayout.LayoutParams(MATCH, dp(34)))
        if (sampleData) { sample(temperature, icon, "auto"); status.text = weatherName(2) }
        else environments.add(EnvironmentInfo(context, temperature, icon, "auto", condition = status))
    }

    /**
     * "Wide weather": icon and temperature on the left; condition (or why there is no forecast) and the next sunrise
     * or sunset on the right, with the theme accent between them. Same cached data as the other weather cards.
     */
    private fun buildWideWeatherCard(content: LinearLayout, params: LinearLayout.LayoutParams) {
        val card = row().apply { background = shape(12); pad(this, 14, 8); gravity = Gravity.CENTER_VERTICAL }
        content.addView(card, params)
        val icon = SolarIcon(context, muted, lightTheme)
        val temperature = fitted(text("", 40, white, true), 14, 46)
        card.addView(icon, LinearLayout.LayoutParams(dp(48), dp(48)))
        card.addView(temperature, LinearLayout.LayoutParams(0, dp(56), .9f).apply { leftMargin = dp(8) })
        card.addView(View(context).apply { setBackgroundColor((theme.accent and 0x00ffffff) or 0x99000000.toInt()) },
            LinearLayout.LayoutParams(dp(2), MATCH).apply { setMargins(dp(10), dp(12), dp(12), dp(12)) })
        val right = column().apply { gravity = Gravity.CENTER_VERTICAL }
        card.addView(right, LinearLayout.LayoutParams(0, MATCH, 1.3f))
        val condition = fitted(text("", 17, white, true).apply { maxLines = 2 }, 10, 18)
        right.addView(condition, LinearLayout.LayoutParams(MATCH, dp(44)))
        val sunLine = row()
        val sunIcon = SolarIcon(context, theme.accent, lightTheme)
        val sun = fitted(text("", 14, muted, false), 9, 15)
        sunLine.addView(sunIcon, LinearLayout.LayoutParams(dp(22), dp(22)))
        sunLine.addView(sun, LinearLayout.LayoutParams(0, dp(24), 1f).apply { leftMargin = dp(6) })
        right.addView(sunLine, LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = dp(4) })
        if (sampleData) { sample(temperature, icon, "auto"); condition.text = weatherName(2); sun.text = "Sunset 05:42 PM" }
        else {
            // Without a forecast the temperature slot already shows the next sunrise / sunset; do not repeat it.
            environments.add(EnvironmentInfo(context, temperature, icon, "auto", condition = condition,
                forecastShown = { shown -> sunLine.visibility = if (shown) View.VISIBLE else View.GONE }))
            environments.add(EnvironmentInfo(context, sun, sunIcon, "sun", eventName = true))
        }
    }

    private fun buildWeatherCard(content: LinearLayout, params: LinearLayout.LayoutParams, both: Boolean) {
        val box = column().apply { gravity = Gravity.CENTER; background = shape(12); pad(this, 16, 12) }
        content.addView(box, params)
        for (mode in if (both) listOf("auto", "sun") else listOf("auto")) {
            val line = row().apply { gravity = Gravity.CENTER }
            val icon = SolarIcon(context, muted, lightTheme)
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
            val holder = row().apply { gravity = (if (alignRight) Gravity.END else Gravity.START) or Gravity.CENTER_VERTICAL }
            if (kind == "trim") {
                val badgeText = (prefs.getString(Keys.BADGE_TEXT, Defaults.TRIM_BADGE) ?: Defaults.TRIM_BADGE).trim()
                val badgeColor = prefs.getInt(Keys.BADGE_COLOR, theme.accent)
                if (badgeText.equals(Defaults.TRIM_BADGE_GLYPH, true)) holder.addView(TrimBadge(context, badgeColor), LinearLayout.LayoutParams(dp(37), dp(27)))
                else if (badgeText.isNotEmpty()) holder.addView(text(badgeText, 13, badgeColor, true))
                return holder
            }
            val solar = text("", 11, muted, false)
            val solarIcon = SolarIcon(context, muted, lightTheme)
            holder.addView(solarIcon, LinearLayout.LayoutParams(dp(24), dp(38)))
            holder.addView(solar, LinearLayout.LayoutParams(WRAP, dp(38)).apply { leftMargin = dp(5) })
            if (sampleData) sample(solar, solarIcon, kind) else environments.add(EnvironmentInfo(context, solar, solarIcon, kind))
            return holder
        }
        badges.addView(corner(prefs.getString(Keys.WIDGET_LEFT, "auto") ?: "auto", false), LinearLayout.LayoutParams(0, dp(38), 1f))
        badges.addView(corner(prefs.getString(Keys.WIDGET_RIGHT, "trim") ?: "trim", true), LinearLayout.LayoutParams(0, dp(38), 1f))
        card.addView(badges, LinearLayout.LayoutParams(MATCH, dp(38)))
        // Home dims after sunset from the first environment; keep one even when no corner shows solar data.
        if (environments.isEmpty() && !sampleData) environments.add(EnvironmentInfo(context, TextView(context), SolarIcon(context, muted, lightTheme), "sun"))

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
            typeface = ClockStyles.typeface(clockStyle ?: prefs.getString(Keys.HOME_CLOCK_STYLE, "classic"), false)
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

    /**
     * "Artwork player" (tall), "Wide player" (and the artwork player on short cards) and "Compact player". Each fills
     * its card: the tall one stacks large square artwork, centred title and artist, progress and controls; the wide
     * one puts artwork as tall as the card beside the title, artist, progress and controls; the compact one keeps the
     * title and artist beside the controls with a slim progress bar below. All three share the views Home fills
     * (artwork or note glyph, marquee title, seek row only when the track has a duration) and the same playback
     * callbacks; the play button is emphasised.
     */
    private fun buildMediaCard(content: LinearLayout, params: LinearLayout.LayoutParams, compact: Boolean, controlsOnly: Boolean = false) {
        val tall = !compact
        val audio = column().apply {
            if (tall) pad(this, 16, 14) else pad(this, 14, 10)
            background = shape(12)
        }
        content.addView(audio, params)
        val source = text("", 11, muted, false).apply {
            gravity = Gravity.START or Gravity.CENTER_VERTICAL
            visibility = View.GONE; isSingleLine = true; ellipsize = TextUtils.TruncateAt.END
        }
        sourceLabel = source
        val hint = text("Enable media access", 12, theme.accent, false).apply {
            if (preview) visibility = View.GONE
            gravity = Gravity.CENTER
            setOnClickListener { activity.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
        }
        mediaAccessHint = hint
        audio.addView(hint, LinearLayout.LayoutParams(MATCH, WRAP))
        val art = ImageView(context).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            background = GradientDrawable().apply { setColor(theme.background); cornerRadius = dp(if (tall) 12 else 10).toFloat() }
            clipToOutline = true
        }
        albumArt = art
        val title = text("No media playing", if (tall) 17 else 16, white, true).apply {
            isSingleLine = true
            ellipsize = TextUtils.TruncateAt.MARQUEE
            marqueeRepeatLimit = -1
            isSelected = true
            gravity = if (tall) Gravity.CENTER else Gravity.START or Gravity.CENTER_VERTICAL
        }
        trackTitle = title
        val artist = text("", if (tall) 13 else 12, muted, false).apply {
            isSingleLine = true; ellipsize = TextUtils.TruncateAt.END
            gravity = if (tall) Gravity.CENTER else Gravity.START or Gravity.CENTER_VERTICAL
        }
        trackArtist = artist
        val seek = seekRow(if (compact && controlsOnly) 24 else if (tall) 32 else 28)
        val controls = mediaControls(if (tall) 56 else if (controlsOnly) 44 else 46, wide = !controlsOnly)
        when {
            tall -> {
                audio.addView(source, LinearLayout.LayoutParams(MATCH, WRAP))
                val artArea = FrameLayout(context)
                audio.addView(artArea, LinearLayout.LayoutParams(MATCH, 0, 1f).apply { topMargin = dp(4) })
                artArea.addView(art, FrameLayout.LayoutParams(dp(96), dp(96), Gravity.CENTER))
                squareArt(artArea, art, 1f)
                audio.addView(title, LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = dp(10) })
                audio.addView(artist, LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = dp(2) })
                audio.addView(seek, LinearLayout.LayoutParams(MATCH, dp(32)).apply { topMargin = dp(4) })
                audio.addView(controls, LinearLayout.LayoutParams(MATCH, dp(60)))
            }
            controlsOnly -> {
                val line = row()
                audio.addView(line, LinearLayout.LayoutParams(MATCH, 0, 1f))
                val labels = column().apply { gravity = Gravity.CENTER_VERTICAL }
                line.addView(labels, LinearLayout.LayoutParams(0, MATCH, 1f))
                labels.addView(source, LinearLayout.LayoutParams(MATCH, WRAP))
                labels.addView(title, LinearLayout.LayoutParams(MATCH, WRAP))
                labels.addView(artist, LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = dp(1) })
                line.addView(controls, LinearLayout.LayoutParams(WRAP, MATCH).apply { leftMargin = dp(10) })
                audio.addView(seek, LinearLayout.LayoutParams(MATCH, dp(24)))
            }
            else -> {
                val line = row().apply { gravity = Gravity.CENTER_VERTICAL }
                audio.addView(line, LinearLayout.LayoutParams(MATCH, 0, 1f))
                val artArea = FrameLayout(context)
                line.addView(artArea, LinearLayout.LayoutParams(dp(96), MATCH))
                artArea.addView(art, FrameLayout.LayoutParams(dp(96), dp(96), Gravity.CENTER_VERTICAL or Gravity.START))
                // Artwork as tall as the card (at most 42 % of its width); the area follows so the text sits beside it.
                line.addOnLayoutChangeListener { _, left, top, right, bottom, _, _, _, _ ->
                    val size = maxOf(1, minOf(bottom - top, (right - left) * 42 / 100))
                    val area = artArea.layoutParams
                    if (area.width != size) { area.width = size; artArea.post { artArea.layoutParams = area } }
                    art.layoutParams?.let { if (it.width != size || it.height != size) { it.width = size; it.height = size; art.post { art.layoutParams = it } } }
                }
                val labels = column().apply { setPadding(dp(14), 0, 0, 0) }
                line.addView(labels, LinearLayout.LayoutParams(0, MATCH, 1f))
                labels.addView(source, LinearLayout.LayoutParams(MATCH, WRAP))
                labels.addView(title, LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = dp(2) })
                labels.addView(artist, LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = dp(1) })
                labels.addView(View(context), LinearLayout.LayoutParams(MATCH, 0, 1f))
                labels.addView(seek, LinearLayout.LayoutParams(MATCH, dp(28)))
                labels.addView(controls, LinearLayout.LayoutParams(MATCH, dp(48)))
            }
        }
        audio.addOnLayoutChangeListener { _, left, _, right, _, oldLeft, _, oldRight, _ ->
            if (right - left != oldRight - oldLeft) updateMediaWidth(right - left, controls, seek, title, if (tall) 17 else 16)
        }
    }

    /** Elapsed time, progress bar and total time (shown by Home only when the track has a duration). */
    private fun seekRow(height: Int): LinearLayout {
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
        seek.addView(bar, LinearLayout.LayoutParams(0, dp(height), 1f))
        seek.addView(totalText)
        bar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, value: Int, fromUser: Boolean) = Unit
            override fun onStartTrackingTouch(seekBar: SeekBar) = onDragging(true)
            override fun onStopTrackingTouch(seekBar: SeekBar) {
                onDragging(false)
                onSeek(seekBar.progress.toLong())
            }
        })
        return seek
    }

    /**
     * Previous / play-pause / next. [wide]: spread evenly across the row; otherwise packed together. The play button
     * sits on a soft accent disc so it reads first.
     */
    private fun mediaControls(height: Int, wide: Boolean): LinearLayout {
        val controls = row().apply { gravity = Gravity.CENTER; minimumHeight = dp(height) }
        for (key in MEDIA_KEYS) {
            val play = key == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
            val control = ImageView(context).apply {
                val pad = if (play) dp(11) else dp(9)
                setPadding(pad, pad, pad, pad)
                setImageDrawable(MediaGlyph(if (play) "play" else if (key == KeyEvent.KEYCODE_MEDIA_PREVIOUS) "previous" else "next", white))
                if (play) background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor((theme.accent and 0x00ffffff) or 0x33000000) }
                contentDescription = if (play) "Play or pause" else if (key == KeyEvent.KEYCODE_MEDIA_PREVIOUS) "Previous track" else "Next track"
                setOnClickListener { onMedia(key) }
            }
            if (play) playbackButton = control
            val size = if (play) dp(height) else dp(height - 8)
            controls.addView(control, if (wide) LinearLayout.LayoutParams(0, size, 1f).let { lp -> lp.apply { if (play) { width = size; weight = 0f; leftMargin = dp(18); rightMargin = dp(18) } } }
                else LinearLayout.LayoutParams(size, size).apply { if (play) { leftMargin = dp(6); rightMargin = dp(6) } })
        }
        return controls
    }

    /** Keeps [art] square inside [area]: as large as [fraction] of the area's smaller side allows. */
    private fun squareArt(area: FrameLayout, art: ImageView, fraction: Float) {
        area.addOnLayoutChangeListener { _, left, top, right, bottom, _, _, _, _ ->
            val size = maxOf(1, (minOf(right - left, bottom - top) * fraction).toInt())
            art.layoutParams?.let { if (it.width != size || it.height != size) { it.width = size; it.height = size; art.post { art.layoutParams = it } } }
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
        // No source label on this card ([sourceLabel] stays null; Home skips it).
        sourceLabel = null
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
        if (environments.isEmpty() && !sampleData) environments.add(EnvironmentInfo(context, TextView(context), SolarIcon(context, muted, lightTheme), "sun"))
        val format = if (prefs.clock24) "HH:mm" else "h:mm"
        val clock = TextClock(context).apply {
            format12Hour = format
            format24Hour = format
            textSize = 84f * textScale
            setAutoSizeTextTypeUniformWithConfiguration(24, maxOf(24, (84 * textScale).toInt()), 1, TypedValue.COMPLEX_UNIT_SP)
            maxLines = 1
            includeFontPadding = false
            setTextColor(white)
            typeface = ClockStyles.typeface(clockStyle ?: prefs.getString(Keys.HOME_CLOCK_STYLE, "classic"), false)
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

    /** Time in the card's clock style and the 12/24-hour setting, as large as its space allows (up to [maxSp]). */
    private fun clockText(maxSp: Int, pattern12: String = "h:mm", pattern24: String = "HH:mm"): TextClock = TextClock(context).apply {
        val format = if (prefs.clock24) pattern24 else pattern12
        format12Hour = format
        format24Hour = format
        val top = maxOf(24, (maxSp * textScale).toInt())
        textSize = top.toFloat()
        setAutoSizeTextTypeUniformWithConfiguration(20, top, 1, TypedValue.COMPLEX_UNIT_SP)
        maxLines = 1
        includeFontPadding = false
        setTextColor(white)
        typeface = ClockStyles.typeface(clockStyle ?: prefs.getString(Keys.HOME_CLOCK_STYLE, "classic"), false)
        gravity = Gravity.CENTER
    }

    private fun dateText(pattern: String, size: Int, color: Int = muted): TextClock = TextClock(context).apply {
        format12Hour = pattern
        format24Hour = pattern
        textSize = size * textScale
        setTextColor(color)
        maxLines = 1
        ellipsize = TextUtils.TruncateAt.END
        gravity = Gravity.CENTER
    }

    /** Vehicle name and trim badge from the existing preferences (each hidden when empty). */
    private fun vehicleLabel(size: Int): LinearLayout {
        val label = row().apply { gravity = Gravity.CENTER }
        val brand = (prefs.getString(Keys.BRAND_TEXT, Defaults.VEHICLE_NAME) ?: "").trim()
        val badge = (prefs.getString(Keys.BADGE_TEXT, Defaults.TRIM_BADGE) ?: "").trim()
        if (brand.isNotEmpty()) label.addView(text(brand, size, prefs.getInt(Keys.BRAND_COLOR, theme.accent), true).apply {
            letterSpacing = .3f; includeFontPadding = false; isSingleLine = true
        })
        val badgeColor = prefs.getInt(Keys.BADGE_COLOR, theme.accent)
        if (badge.equals(Defaults.TRIM_BADGE_GLYPH, true)) label.addView(TrimBadge(context, badgeColor), LinearLayout.LayoutParams(dp(size * 2), dp((size * 1.45f).toInt())).apply { leftMargin = dp(6) })
        else if (badge.isNotEmpty()) label.addView(text(badge, size, badgeColor, true).apply { includeFontPadding = false }, LinearLayout.LayoutParams(WRAP, WRAP).apply { leftMargin = dp(6) })
        return label
    }

    /** Home dims after sunset from the first environment (as the other clock cards do). */
    private fun keepEnvironment() {
        if (environments.isEmpty() && !sampleData) environments.add(EnvironmentInfo(context, TextView(context), SolarIcon(context, muted, lightTheme), "sun"))
    }

    /**
     * "Gauge clock" (wide): large centred time under a thin decorative half-ring with a short accent segment, the date
     * below and the vehicle name / trim badge between two hairlines. The ring is decoration only.
     */
    private fun buildGaugeClockCard(content: LinearLayout, params: LinearLayout.LayoutParams) {
        val card = FrameLayout(context).apply { background = shape(12) }
        content.addView(card, params)
        keepEnvironment()
        card.addView(GaugeArc(context, muted, theme.accent), FrameLayout.LayoutParams(MATCH, MATCH))
        val body = column().apply { gravity = Gravity.CENTER; pad(this, 24, 10) }
        card.addView(body, FrameLayout.LayoutParams(MATCH, MATCH))
        body.addView(View(context), LinearLayout.LayoutParams(MATCH, 0, .35f))
        body.addView(clockText(72), LinearLayout.LayoutParams(MATCH, 0, 1.6f))
        body.addView(dateText("EEEE, d MMMM", 14), LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = dp(2) })
        val brandLine = row().apply { gravity = Gravity.CENTER }
        fun hairline() = View(context).apply { setBackgroundColor((muted and 0x00ffffff) or 0x55000000) }
        brandLine.addView(hairline(), LinearLayout.LayoutParams(dp(36), 1).apply { rightMargin = dp(10) })
        brandLine.addView(vehicleLabel(11))
        brandLine.addView(hairline(), LinearLayout.LayoutParams(dp(36), 1).apply { leftMargin = dp(10) })
        body.addView(brandLine, LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = dp(6) })
        body.addView(View(context), LinearLayout.LayoutParams(MATCH, 0, .25f))
    }

    /**
     * "Clock with weather" (wide): time, accent line and date on the left; on the right the current weather and the
     * next sunrise or sunset, from the existing weather / solar data (no extra requests).
     */
    private fun buildWeatherClockCard(content: LinearLayout, params: LinearLayout.LayoutParams) {
        val card = row().apply { background = shape(12); pad(this, 18, 10) }
        content.addView(card, params)
        val left = column().apply { gravity = Gravity.CENTER }
        card.addView(left, LinearLayout.LayoutParams(0, MATCH, 1.25f))
        left.addView(clockText(64), LinearLayout.LayoutParams(MATCH, 0, 1f))
        left.addView(View(context).apply { background = HomeChrome.accent(theme) },
            LinearLayout.LayoutParams(dp(48), dp(2)).apply { topMargin = dp(4); bottomMargin = dp(6); gravity = Gravity.CENTER_HORIZONTAL })
        left.addView(dateText("EEEE, d MMMM", 13), LinearLayout.LayoutParams(MATCH, WRAP))
        card.addView(View(context).apply { setBackgroundColor((theme.accent and 0x00ffffff) or 0x80000000.toInt()) },
            LinearLayout.LayoutParams(dp(1), MATCH).apply { setMargins(dp(14), dp(8), dp(14), dp(8)) })
        val right = column().apply { gravity = Gravity.CENTER_VERTICAL }
        card.addView(right, LinearLayout.LayoutParams(0, MATCH, 1f))
        for (mode in listOf("auto", "sun")) {
            val line = row()
            val icon = SolarIcon(context, if (mode == "sun") theme.accent else muted, lightTheme)
            val label = text("", 16, white, false).apply { isSingleLine = true; ellipsize = TextUtils.TruncateAt.END }
            line.addView(icon, LinearLayout.LayoutParams(dp(30), dp(30)))
            line.addView(label, LinearLayout.LayoutParams(0, WRAP, 1f).apply { leftMargin = dp(8) })
            right.addView(line, LinearLayout.LayoutParams(MATCH, 0, 1f))
            if (sampleData) sample(label, icon, mode) else environments.add(EnvironmentInfo(context, label, icon, mode))
        }
    }

    /**
     * "Split clock" (wide): hour over minutes on the left, an accent rule, the day and date on the right, and the
     * vehicle name / trim badge in the upper right.
     */
    private fun buildSplitClockCard(content: LinearLayout, params: LinearLayout.LayoutParams) {
        val card = FrameLayout(context).apply { background = shape(12) }
        content.addView(card, params)
        keepEnvironment()
        val body = row().apply { pad(this, 22, 10) }
        card.addView(body, FrameLayout.LayoutParams(MATCH, MATCH))
        val time = column().apply { gravity = Gravity.CENTER }
        body.addView(time, LinearLayout.LayoutParams(0, MATCH, .8f))
        time.addView(clockText(54, "h", "HH"), LinearLayout.LayoutParams(MATCH, 0, 1f))
        time.addView(clockText(54, "mm", "mm"), LinearLayout.LayoutParams(MATCH, 0, 1f))
        body.addView(View(context).apply { setBackgroundColor(theme.accent) },
            LinearLayout.LayoutParams(dp(2), MATCH).apply { setMargins(dp(10), dp(14), dp(18), dp(14)) })
        val date = column().apply { gravity = Gravity.CENTER_VERTICAL }
        body.addView(date, LinearLayout.LayoutParams(0, MATCH, 1.4f))
        date.addView(dateText("EEEE,", 18, white).apply { gravity = Gravity.START or Gravity.CENTER_VERTICAL })
        date.addView(dateText("d MMMM", 18, white).apply { gravity = Gravity.START or Gravity.CENTER_VERTICAL })
        card.addView(vehicleLabel(10), FrameLayout.LayoutParams(WRAP, WRAP, Gravity.TOP or Gravity.END).apply { setMargins(0, dp(10), dp(16), 0) })
    }

    private fun updateMediaWidth(pixels: Int, controls: LinearLayout, seek: LinearLayout, title: TextView, base: Int = 16) {
        val width = (pixels / context.resources.displayMetrics.density).toInt()
        if (!preview) onNarrow(false)
        seek.visibility = if (duration() > 0) View.VISIBLE else View.GONE
        title.textSize = (if (width < 230) base - 2 else base) * textScale
        controls.visibility = View.VISIBLE
    }

    private companion object {
        val MEDIA_KEYS = intArrayOf(KeyEvent.KEYCODE_MEDIA_PREVIOUS, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_MEDIA_NEXT)
    }
}
