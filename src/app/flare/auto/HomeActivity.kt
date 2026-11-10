package app.flare.auto

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import java.lang.ref.WeakReference
import java.text.SimpleDateFormat
import java.util.ArrayList
import java.util.Date
import java.util.HashMap
import java.util.Locale

class HomeActivity : Activity() {
    private var panel = 0
    private var edge = 0
    private var white = 0
    private var muted = 0
    private lateinit var theme: ThemeTokens
    private lateinit var cards: HomeCards
    private var environment: EnvironmentInfo? = null
    private var environment2: EnvironmentInfo? = null
    private var dimOverlay: View? = null
    private var resumed = false
    private lateinit var prefs: SharedPreferences
    private var uiDirty = false
    private var attachedScale = 100
    private var playbackButton: ImageView? = null
    private lateinit var root: LinearLayout
    private lateinit var date: TextView
    private lateinit var startup: StartupActions
    private val handler = Handler(Looper.getMainLooper())
    private val dateFormat = SimpleDateFormat("EEE, MMM d", Locale.getDefault())

    private val screenOff = Runnable {
        if (resumed) startActivity(Intent(this, ScreenSaverActivity::class.java))
    }

    private fun scheduleScreenOff() {
        handler.removeCallbacks(screenOff)
        val minutes = prefs.getInt("saver_timeout", 0)
        if (resumed && minutes > 0) handler.postDelayed(screenOff, minutes * 60000L)
    }

    override fun onUserInteraction() {
        super.onUserInteraction()
        if (::startup.isInitialized) startup.cancel()
        if (::prefs.isInitialized) scheduleScreenOff()
    }

    private val settingsChanged = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == null || key in UI_KEYS) uiDirty = true
    }

    private val tick = object : Runnable {
        override fun run() {
            if (theme !== ThemeTokens.get(this@HomeActivity)) build()
            date.text = dateFormat.format(Date())
            environment?.let {
                it.updateSolar()
                dimOverlay?.visibility =
                    if (prefs.getBoolean("auto_dim", false) && it.night()) View.VISIBLE else View.GONE
            }
            environment2?.updateSolar()
            handler.postDelayed(this, 60000)
        }
    }

    private val progressTick = object : Runnable {
        override fun run() {
            if (!resumed || playbackButton == null) return
            updateProgress()
            if (isPlaying(selectedController) && lastDuration > 0) handler.postDelayed(this, 1000)
        }
    }

    private fun dp(value: Float): Int = (resources.displayMetrics.density * value + .5f).toInt()
    private fun dp(value: Int): Int = dp(value.toFloat())

    private fun shape(color: Int, radius: Int): Drawable =
        if (prefs.getBoolean("glass_cards", true)) {
            HomeGlassDrawable(color, edge, dp(radius).toFloat())
        } else {
            GlassDrawable(color, dp(radius).toFloat(), edge)
        }

    private fun text(value: String, size: Int, color: Int, bold: Boolean): TextView =
        TextView(this).apply {
            text = value
            setTextColor(color)
            gravity = Gravity.CENTER_VERTICAL
            textSize = size * prefs.getInt("text", 100) / 100f
            if (bold) setTypeface(Typeface.DEFAULT, Typeface.BOLD)
        }

    private fun column() = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    private fun row() = LinearLayout(this).apply {
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

    private fun launch(packageName: String, className: String?) {
        val key = when {
            packageName == "com.bw.phone" -> "bluetooth"
            className?.endsWith("RadioActivity") == true -> "radio"
            className?.endsWith("BtActivity") == true -> "audio"
            else -> ""
        }
        val target = prefs.getString("app_$key", "") ?: ""
        if (target.isNotEmpty()) {
            launchTarget(target)
            return
        }
        try {
            val intent = if (className != null) {
                Intent().setClassName(packageName, className)
            } else {
                packageManager.getLaunchIntentForPackage(packageName)
            } ?: throw IllegalStateException()
            startActivity(intent)
        } catch (_: Exception) {
            Toast.makeText(this, "App unavailable on this device", Toast.LENGTH_SHORT).show()
        }
    }

    private fun launchTarget(target: String) {
        try {
            val component = ComponentName.unflattenFromString(target)!!
            startActivity(
                Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setComponent(component)
            )
        } catch (_: Exception) {
            Toast.makeText(this, "Selected app is unavailable", Toast.LENGTH_SHORT).show()
        }
    }

    private fun launchFirst(packages: Array<String>) {
        val key = when (packages[0]) {
            "com.shihab.diplay" -> "app_carplay"
            "org.schabi.newpipe" -> "app_newpipe"
            else -> "app_androidauto"
        }
        val target = prefs.getString(key, "") ?: ""
        if (target.isNotEmpty()) {
            launchTarget(target)
            return
        }
        for (packageName in packages) {
            val intent = packageManager.getLaunchIntentForPackage(packageName)
            if (intent != null) {
                startActivity(intent)
                return
            }
        }
        Toast.makeText(this, "App unavailable on this device", Toast.LENGTH_SHORT).show()
    }

    private fun media(key: Int) {
        val controller = selectedController
        if (controller != null) {
            val controls = controller.transportControls
            when {
                key == 88 -> controls.skipToPrevious()
                key == 87 -> controls.skipToNext()
                controller.playbackState?.state == 3 -> controls.pause()
                else -> controls.play()
            }
            return
        }
        val audio = getSystemService(AUDIO_SERVICE) as AudioManager
        audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, key))
        audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, key))
    }

    override fun attachBaseContext(base: android.content.Context) { super.attachBaseContext(UiScale.wrap(base, "ui_scale_home")) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(ThemeTokens.get(this).background))
        prefs = getSharedPreferences("home", 0)
        attachedScale = prefs.getInt("ui_scale_home", 100)
        startup = StartupActions(this, prefs, handler)
        instance = WeakReference(this)
        window.decorView.systemUiVisibility = UiFlags.IMMERSIVE
        prefs.registerOnSharedPreferenceChangeListener(settingsChanged)
        build()
    }

    private fun build() {
        environment?.stop()
        environment2?.stop()
        environment = null
        environment2 = null
        playbackButton = null
        albumArt = null
        trackTitle = null
        trackArtist = null
        elapsed = null
        total = null
        progress = null
        seekRow = null
        sourceLabel = null
        mediaAccessHint = null
        dragging = false
        mediaNarrow = false
        theme = ThemeTokens.get(this)
        panel = theme.card
        edge = theme.cardBorder
        white = theme.textPrimary
        muted = theme.textSecondary
        resetMediaCache()
        cards = HomeCards(this, prefs, theme,
            onTarget = ::launchTarget, onProjection = ::launchFirst, onMedia = ::media,
            onSeek = { selectedController?.transportControls?.seekTo(it) },
            onDragging = { dragging = it }, onNarrow = { mediaNarrow = it }, duration = { lastDuration })
        root = column()
        root.background = WallpaperPack.home(this, theme)
        pad(root, prefs.getInt("spacing", 14), 10)
        val frame = FrameLayout(this)
        frame.addView(root)
        dimOverlay = View(this).apply {
            setBackgroundColor(0x4d000000)
            isClickable = false
            visibility = View.GONE
        }
        frame.addView(dimOverlay, FrameLayout.LayoutParams(-1, -1))
        setContentView(frame)
        buildTopBar()
        val content = row()
        val contentParams = LinearLayout.LayoutParams(-1, 0, 1f).apply {
            topMargin = dp(12)
            bottomMargin = dp(12)
        }
        root.addView(content, contentParams)
        buildColumns(content)
        if (resumed) cards.environments.forEach { it.start() }
        buildDock()
        if (resumed) refreshPlayback()
    }

    private fun buildTopBar() {
        val top = row()
        date = text("", 19, white, true)
        top.addView(date, LinearLayout.LayoutParams(0, dp(40), 1f))
        top.addView(DeviceStatus(this, false), LinearLayout.LayoutParams(dp(24), dp(24)))
        val wifiParams = LinearLayout.LayoutParams(dp(24), dp(24)).apply { leftMargin = dp(14) }
        top.addView(WifiStatus(this), wifiParams)
        val bluetoothParams = LinearLayout.LayoutParams(dp(24), dp(24)).apply { leftMargin = dp(14) }
        top.addView(DeviceStatus(this, true), bluetoothParams)
        root.addView(top)
        root.addView(View(this).apply { background = accentGradient() }, LinearLayout.LayoutParams(-1, dp(1)))
    }

    private fun buildColumns(content: LinearLayout) {
        val left = card(prefs, true)
        val right = card(prefs, false)
        val showLeft = left != "hidden"
        val showRight = right != "hidden"
        val split = prefs.getInt("split", 58).coerceIn(35, 65)
        if (showLeft) {
            val box = row()
            content.addView(box, LinearLayout.LayoutParams(0, -1, if (showRight) split.toFloat() else 1f))
            buildCard(box, left)
        }
        if (showLeft && showRight) content.addView(View(this), LinearLayout.LayoutParams(dp(12), 1))
        if (showRight) {
            val box = row()
            content.addView(box, LinearLayout.LayoutParams(0, -1, if (showLeft) (100 - split).toFloat() else 1f))
            buildCard(box, right)
        }
    }

    private fun buildCard(content: LinearLayout, card: String) {
        cards.build(content, card)
        environment = cards.environments.getOrNull(0)
        environment2 = cards.environments.getOrNull(1)
        playbackButton = cards.playbackButton
        sourceLabel = cards.sourceLabel
        mediaAccessHint = cards.mediaAccessHint
        albumArt = cards.albumArt
        trackTitle = cards.trackTitle
        trackArtist = cards.trackArtist
        elapsed = cards.elapsed
        total = cards.total
        progress = cards.progress
        seekRow = cards.seekRow
    }

    private fun buildDock() {
        val nav = row().apply { background = shape(panel, 14) }
        root.addView(nav, LinearLayout.LayoutParams(-1, dp(prefs.getInt("dock", 70))))
        val actions = arrayOf<() -> Unit>(
            { launch("com.bw.mediaplayer", "com.bw.mediaplayer.ui.BtActivity") },
            { launch("com.bw.mediaplayer", "com.bw.mediaplayer.ui.RadioActivity") },
            { launch("com.bw.phone", "com.bw.phone.MainActivity") },
            { startActivity(Intent(this, ScreenSaverActivity::class.java)) },
            { apps() }
        )
        val items = DockConfig.read(prefs)
        val count = items.count { it.visible }
        val oldSize = prefs.getInt("dock_icons", 24)
        val ceiling = if (oldSize < 23) 20 else if (oldSize < 30) 26 else 32
        val size = DockConfig.size(this, count, ceiling)
        for (item in items) {
            if (!item.visible) continue
            val cell = column().apply {
                gravity = Gravity.CENTER
                val name = DockConfig.name(this@HomeActivity, item)
                contentDescription = name
                setOnClickListener {
                    if (item.type == 5) launchTarget(item.target) else actions[item.type]()
                }
            }
            val icon: View = if (item.type == 5) {
                ImageView(this).apply {
                    try {
                        setImageDrawable(AsyncImages.appIcon(this@HomeActivity, item.target))
                    } catch (_: Exception) {
                    }
                    if (prefs.getBoolean("dock_mono", false)) {
                        val matrix = android.graphics.ColorMatrix().apply { setSaturation(0f) }
                        setColorFilter(android.graphics.ColorMatrixColorFilter(matrix))
                    }
                }
            } else {
                NavIcon(this, DockConfig.ICONS[item.type], white)
            }
            cell.addView(icon, LinearLayout.LayoutParams(dp(size), dp(size)))
            val label = text(DockConfig.name(this, item), 14, white, false).apply {
                gravity = Gravity.CENTER
                includeFontPadding = false
                isSingleLine = true
                ellipsize = android.text.TextUtils.TruncateAt.END
                visibility = if (prefs.getBoolean("dock_labels", true)) View.VISIBLE else View.GONE
            }
            cell.addView(label, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(4) })
            nav.addView(cell, LinearLayout.LayoutParams(0, -1, 1f))
        }
    }

    private var mediaAccessHint: TextView? = null
    private var sourceLabel: TextView? = null
    private var mediaAccessGranted = false
    private var albumArt: ImageView? = null
    private var trackTitle: TextView? = null
    private var trackArtist: TextView? = null
    private var elapsed: TextView? = null
    private var total: TextView? = null
    private var progress: SeekBar? = null
    private var seekRow: LinearLayout? = null
    private var dragging = false
    private var mediaNarrow = false
    private var sessionManager: MediaSessionManager? = null
    private val sourceLabels = HashMap<String, String>()
    private val controllers = ArrayList<MediaController>()
    private var selectedController: MediaController? = null
    private var preferredToken: android.media.session.MediaSession.Token? = null
    private var artworkToken: android.media.session.MediaSession.Token? = null
    private var lastTitle: String? = null
    private var lastArtist: String? = null
    private var lastSource: String? = null
    private var lastElapsed: String? = null
    private var lastTotal: String? = null
    private var lastArt: Bitmap? = null
    private var lastPlaying: Boolean? = null
    private var lastDuration = -1L
    private var playGlyph: Drawable? = null
    private var pauseGlyph: Drawable? = null
    private var noteGlyph: Drawable? = null

    private val mediaCallback = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadata?) = selectController()
        override fun onPlaybackStateChanged(state: PlaybackState?) = selectController()
        override fun onSessionDestroyed() {
            if (resumed) {
                try {
                    val listener = ComponentName(this@HomeActivity, MediaAccess::class.java)
                    updateSessions(sessionManager?.getActiveSessions(listener))
                } catch (_: SecurityException) {
                    updateSessions(null)
                }
            }
        }
    }

    private val sessionsChanged = MediaSessionManager.OnActiveSessionsChangedListener { list ->
        updateSessions(list)
    }

    private fun isPlaying(controller: MediaController?): Boolean =
        controller?.playbackState?.state == PlaybackState.STATE_PLAYING

    private fun startMedia() {
        sourceLabels.clear()
        sessionManager = getSystemService(MEDIA_SESSION_SERVICE) as MediaSessionManager
        try {
            val listener = ComponentName(this, MediaAccess::class.java)
            sessionManager!!.addOnActiveSessionsChangedListener(sessionsChanged, listener, handler)
            mediaAccessGranted = true
            updateSessions(sessionManager!!.getActiveSessions(listener))
        } catch (_: SecurityException) {
            mediaAccessGranted = false
            updateSessions(null)
        }
    }

    private fun stopMedia() {
        try {
            sessionManager?.removeOnActiveSessionsChangedListener(sessionsChanged)
        } catch (_: SecurityException) {
        }
        controllers.forEach { it.unregisterCallback(mediaCallback) }
        controllers.clear()
        selectedController = null
        handler.removeCallbacks(progressTick)
    }

    private fun updateSessions(list: List<MediaController>?) {
        if (!resumed) return
        val incoming = list.orEmpty()
        val tokens = incoming.mapTo(HashSet()) { it.sessionToken }
        controllers.filter { it.sessionToken !in tokens }.forEach { it.unregisterCallback(mediaCallback) }
        val retained = controllers.associateBy { it.sessionToken }
        controllers.clear()
        incoming.forEach { controller ->
            val existing = retained[controller.sessionToken]
            if (existing != null) controllers.add(existing)
            else {
                controllers.add(controller)
                controller.registerCallback(mediaCallback, handler)
            }
        }
        selectController()
    }

    private fun selectController() {
        if (!resumed) return
        val firstPlaying = controllers.firstOrNull { isPlaying(it) }
        val previous = controllers.firstOrNull { it.sessionToken == preferredToken && isPlaying(it) }
        val firstName = firstPlaying?.let { sourceName(it) } ?: ""
        val sameTrack = previous != null && firstPlaying != null && run {
            val oldTitle = NowPlaying.value(previous.metadata, MediaMetadata.METADATA_KEY_TITLE).trim()
            val newTitle = NowPlaying.value(firstPlaying.metadata, MediaMetadata.METADATA_KEY_TITLE).trim()
            oldTitle.isNotEmpty() && (newTitle.isEmpty() || oldTitle.equals(newTitle, ignoreCase = true))
        }
        val projection = previous?.packageName in PROJECTION_PACKAGES
        selectedController = if (projection && firstName == "BLUETOOTH" && sameTrack) previous else firstPlaying ?: controllers.firstOrNull()
        preferredToken = selectedController?.sessionToken
        refreshPlayback()
    }

    private fun resetMediaCache() {
        lastTitle = null
        lastArtist = null
        lastSource = null
        lastElapsed = null
        lastTotal = null
        lastArt = null
        artworkToken = null
        lastPlaying = null
        lastDuration = -1
        playGlyph = MediaGlyph("play", white)
        pauseGlyph = MediaGlyph("pause", white)
        noteGlyph = MediaGlyph("note", theme.textSecondary)
    }

    private fun sourceName(controller: MediaController?): String {
        if (controller == null) return ""
        val packageName = controller.packageName
        if (packageName == "com.bw.source") {
            return when (Settings.System.getString(contentResolver, "bw_current_source_name")) {
                "RADIO" -> "RADIO"
                "MUSIC_BT" -> "BLUETOOTH"
                else -> ""
            }
        }
        when (packageName) {
            "com.android.bluetooth" -> return "BLUETOOTH"
            "com.andrerinas.headunitrevived" -> return "ANDROID AUTO"
            "com.shihab.diplay", "com.shihab.diplay.hudtest" -> return "DIPLAY"
        }
        return sourceLabels.getOrPut(packageName) {
            try { packageManager.getApplicationLabel(packageManager.getApplicationInfo(packageName, 0)).toString() }
            catch (_: Exception) { "" }
        }
    }

    private fun refreshPlayback() {
        val button = playbackButton ?: run {
            handler.removeCallbacks(progressTick)
            return
        }
        mediaAccessHint?.visibility = if (mediaAccessGranted) View.GONE else View.VISIBLE
        val source = sourceName(selectedController)
        if (source != lastSource) {
            sourceLabel?.text = source
            sourceLabel?.visibility = if (source.isEmpty()) View.GONE else View.VISIBLE
            lastSource = source
        }
        val metadata = selectedController?.metadata
        var title = NowPlaying.value(metadata, MediaMetadata.METADATA_KEY_TITLE)
        if (title.isEmpty()) title = "No media playing"
        var artist = NowPlaying.value(metadata, MediaMetadata.METADATA_KEY_ARTIST)
        if (artist.isEmpty()) artist = NowPlaying.value(metadata, MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
        val previousTitle = lastTitle
        if (title != lastTitle) {
            trackTitle?.text = title
            lastTitle = title
        }
        if (artist != lastArtist) {
            trackArtist?.text = artist
            lastArtist = artist
        }
        val reportedArt = metadata?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: metadata?.getBitmap(MediaMetadata.METADATA_KEY_ART)
            ?: metadata?.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
        val token = selectedController?.sessionToken
        val art = reportedArt ?: if (token != null && token == artworkToken && title == previousTitle && title != "No media playing") lastArt else null
        if (reportedArt != null) artworkToken = token
        if (art == null) artworkToken = null
        if (art !== lastArt || lastDuration < 0) {
            if (art != null) {
                albumArt?.setPadding(0, 0, 0, 0)
                albumArt?.setImageBitmap(art)
            } else {
                albumArt?.setPadding(dp(12), dp(12), dp(12), dp(12))
                albumArt?.setImageDrawable(noteGlyph)
            }
            lastArt = art
        }
        val duration = metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0
        if (duration != lastDuration) {
            seekRow?.visibility = if (duration > 0 && !mediaNarrow) View.VISIBLE else View.GONE
            progress?.max = minOf(duration, Int.MAX_VALUE.toLong()).toInt()
            lastDuration = duration
        }
        val playing = isPlaying(selectedController)
        if (lastPlaying == null || lastPlaying != playing) {
            button.setImageDrawable(if (playing) pauseGlyph else playGlyph)
            button.contentDescription = if (playing) "Pause" else "Play"
            lastPlaying = playing
        }
        handler.removeCallbacks(progressTick)
        updateProgress()
        if (resumed && playing && duration > 0) handler.postDelayed(progressTick, 1000)
    }

    private fun updateProgress() {
        if (lastDuration <= 0 || dragging) return
        val controller = selectedController ?: return
        val state = controller.playbackState
        var position = state?.position ?: 0
        if (state?.state == PlaybackState.STATE_PLAYING && state.lastPositionUpdateTime > 0) {
            position += ((SystemClock.elapsedRealtime() - state.lastPositionUpdateTime) * state.playbackSpeed).toLong()
        }
        position = position.coerceIn(0, lastDuration)
        progress?.progress = minOf(position, Int.MAX_VALUE.toLong()).toInt()
        val elapsedText = formatDuration(position)
        val totalText = formatDuration(lastDuration)
        if (elapsedText != lastElapsed) {
            elapsed?.text = elapsedText
            lastElapsed = elapsedText
        }
        if (totalText != lastTotal) {
            total?.text = totalText
            lastTotal = totalText
        }
    }

    fun invalidateSettings() {
        uiDirty = true
    }

    private fun apps() {
        startActivity(Intent(this, AppsActivity::class.java))
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) window.decorView.systemUiVisibility = UiFlags.IMMERSIVE
    }

    @Suppress("DEPRECATION")
    override fun onBackPressed() = Unit

    override fun onResume() {
        super.onResume()
        if (attachedScale != prefs.getInt("ui_scale_home", 100)) { recreate(); return }
        resumed = true
        if (uiDirty || theme !== ThemeTokens.get(this)) {
            build()
            uiDirty = false
        } else {
            environment?.start()
            environment2?.start()
        }
        handler.removeCallbacks(tick)
        handler.post(tick)
        startMedia()
        startup.onResume()
        startup.checkBoot()
        scheduleScreenOff()
        if (prefs.getBoolean("awake", true)) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    override fun onPause() {
        resumed = false
        handler.removeCallbacks(tick)
        handler.removeCallbacks(screenOff)
        startup.onPause()
        stopMedia()
        environment?.stop()
        environment2?.stop()
        super.onPause()
    }

    override fun onDestroy() {
        prefs.unregisterOnSharedPreferenceChangeListener(settingsChanged)
        startup.cancel()
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    class NavIcon(context: Context, private val kind: String, color: Int) : View(context) {
        private val paint = Paint(3)
        private val glyphPath = Path()
        private var shiftX = 0f
        private var shiftY = 0f

        init {
            paint.color = color
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 1.7f
            paint.strokeCap = Paint.Cap.ROUND
            paint.strokeJoin = Paint.Join.ROUND
            synchronized(centers) {
                val offset = centers[kind] ?: measureCenter().also { centers[kind] = it }
                shiftX = offset[0]
                shiftY = offset[1]
            }
        }

        private fun measureCenter(): FloatArray {
            val bitmap = Bitmap.createBitmap(256, 256, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            canvas.translate(32f, 32f)
            canvas.scale(8f, 8f)
            drawGlyph(canvas)
            val pixels = IntArray(256 * 256)
            bitmap.getPixels(pixels, 0, 256, 0, 0, 256, 256)
            var minX = 256
            var minY = 256
            var maxX = -1
            var maxY = -1
            for (y in 0 until 256) for (x in 0 until 256) {
                if (pixels[y * 256 + x] ushr 24 != 0) {
                    minX = minOf(minX, x)
                    maxX = maxOf(maxX, x)
                    minY = minOf(minY, y)
                    maxY = maxOf(maxY, y)
                }
            }
            bitmap.recycle()
            return if (maxX < 0) floatArrayOf(0f, 0f) else floatArrayOf(
                12 - ((minX + maxX + 1) / 2f - 32) / 8,
                12 - ((minY + maxY + 1) / 2f - 32) / 8
            )
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            canvas.save()
            canvas.scale(width / 24f, height / 24f)
            canvas.translate(shiftX, shiftY)
            drawGlyph(canvas)
            canvas.restore()
        }

        private fun drawGlyph(canvas: Canvas) {
            val path = glyphPath
            path.rewind()
            when (kind) {
                "audio" -> {
                    path.moveTo(8f, 17f); path.lineTo(8f, 5f); path.lineTo(19f, 3f); path.lineTo(19f, 15f)
                    canvas.drawPath(path, paint); canvas.drawOval(3f, 15f, 8f, 20f, paint); canvas.drawOval(14f, 13f, 19f, 18f, paint)
                }
                "youtube" -> {
                    canvas.drawRoundRect(2f, 5f, 22f, 19f, 4f, 4f, paint)
                    path.moveTo(10f, 9f); path.lineTo(16f, 12f); path.lineTo(10f, 15f); path.close(); canvas.drawPath(path, paint)
                }
                "file" -> {
                    path.moveTo(2f, 6f); path.lineTo(9f, 6f); path.lineTo(11f, 9f); path.lineTo(22f, 9f)
                    path.lineTo(22f, 20f); path.lineTo(2f, 20f); path.close(); canvas.drawPath(path, paint)
                }
                "projection" -> {
                    canvas.drawRoundRect(2f, 4f, 22f, 19f, 3f, 3f, paint)
                    path.moveTo(10f, 8f); path.lineTo(16f, 11.5f); path.lineTo(10f, 15f); path.close(); canvas.drawPath(path, paint)
                }
                "radio" -> {
                    canvas.drawRoundRect(3f, 8f, 21f, 20f, 3f, 3f, paint)
                    canvas.drawLine(6f, 7f, 16f, 3f, paint); canvas.drawLine(6f, 12f, 12f, 12f, paint)
                    canvas.drawLine(6f, 15f, 12f, 15f, paint); canvas.drawCircle(17f, 14f, 2f, paint)
                }
                "bluetooth" -> {
                    path.moveTo(12f, 2f); path.lineTo(12f, 22f); path.lineTo(18f, 17f); path.lineTo(6f, 7f)
                    path.moveTo(6f, 17f); path.lineTo(18f, 7f); path.lineTo(12f, 2f); canvas.drawPath(path, paint)
                }
                "screen" -> {
                    canvas.drawRoundRect(3f, 4f, 21f, 19f, 3f, 3f, paint)
                    canvas.drawLine(9f, 22f, 15f, 22f, paint); canvas.drawLine(12f, 19f, 12f, 22f, paint)
                }
                "cellular" -> {
                    paint.style = Paint.Style.FILL
                    for (bar in 0 until 4) {
                        val x = 3f + bar * 5
                        canvas.drawRoundRect(x, 17f - bar * 4, x + 3, 21f, .7f, .7f, paint)
                    }
                    paint.style = Paint.Style.STROKE
                }
                "wifi" -> {
                    canvas.drawArc(-1f, 5f, 25f, 31f, 225f, 90f, false, paint)
                    canvas.drawArc(3f, 9f, 21f, 27f, 225f, 90f, false, paint)
                    canvas.drawArc(7f, 13f, 17f, 23f, 225f, 90f, false, paint)
                    paint.style = Paint.Style.FILL; canvas.drawCircle(12f, 20f, 1.35f, paint); paint.style = Paint.Style.STROKE
                }
                "custom" -> {
                    canvas.drawLine(4f, 6f, 20f, 6f, paint); canvas.drawLine(4f, 12f, 20f, 12f, paint)
                    canvas.drawLine(4f, 18f, 20f, 18f, paint); canvas.drawCircle(8f, 6f, 2f, paint)
                    canvas.drawCircle(16f, 12f, 2f, paint); canvas.drawCircle(10f, 18f, 2f, paint)
                }
                "gallery" -> {
                    canvas.drawRoundRect(2f, 4f, 22f, 20f, 3f, 3f, paint); canvas.drawCircle(8f, 9f, 2f, paint)
                    path.moveTo(4f, 18f); path.lineTo(10f, 12f); path.lineTo(14f, 16f); path.lineTo(17f, 13f)
                    path.lineTo(21f, 18f); canvas.drawPath(path, paint)
                }
                "camera" -> {
                    canvas.drawRoundRect(2f, 6f, 22f, 20f, 3f, 3f, paint); canvas.drawCircle(12f, 13f, 4f, paint)
                    canvas.drawLine(8f, 3f, 16f, 3f, paint)
                }
                "keyboard" -> {
                    canvas.drawRoundRect(2f, 5f, 22f, 19f, 3f, 3f, paint)
                    for (row in 0 until 2) for (column in 0 until 4) canvas.drawPoint(6f + column * 4, 9f + row * 3, paint)
                    canvas.drawLine(8f, 16f, 16f, 16f, paint)
                }
                "browser" -> {
                    canvas.drawCircle(12f, 12f, 9f, paint); canvas.drawOval(8f, 3f, 16f, 21f, paint)
                    canvas.drawLine(3f, 12f, 21f, 12f, paint); canvas.drawArc(4f, 5f, 20f, 14f, 0f, 180f, false, paint)
                }
                "carsettings" -> {
                    canvas.drawRoundRect(3f, 10f, 21f, 17f, 2f, 2f, paint); canvas.drawLine(6f, 10f, 8f, 6f, paint)
                    canvas.drawLine(8f, 6f, 16f, 6f, paint); canvas.drawLine(16f, 6f, 18f, 10f, paint)
                    canvas.drawCircle(7f, 17f, 1.5f, paint); canvas.drawCircle(17f, 17f, 1.5f, paint)
                    canvas.drawLine(10f, 13f, 14f, 13f, paint)
                }
                "systemsettings" -> {
                    canvas.drawCircle(12f, 12f, 7f, paint); canvas.drawCircle(12f, 12f, 2.5f, paint)
                    for (index in 0 until 8) {
                        val angle = index * Math.PI / 4
                        canvas.drawLine(12 + kotlin.math.cos(angle).toFloat() * 8, 12 + kotlin.math.sin(angle).toFloat() * 8,
                            12 + kotlin.math.cos(angle).toFloat() * 10, 12 + kotlin.math.sin(angle).toFloat() * 10, paint)
                    }
                }
                "ipod" -> {
                    canvas.drawRoundRect(5f, 2f, 19f, 22f, 3f, 3f, paint); canvas.drawRect(8f, 5f, 16f, 11f, paint)
                    canvas.drawCircle(12f, 16f, 3f, paint)
                }
                "playpause" -> {
                    path.moveTo(3f, 5f); path.lineTo(11f, 12f); path.lineTo(3f, 19f); path.close()
                    canvas.drawPath(path, paint); canvas.drawLine(16f, 5f, 16f, 19f, paint); canvas.drawLine(21f, 5f, 21f, 19f, paint)
                }
                "sim" -> {
                    path.moveTo(8f, 2f); path.lineTo(20f, 2f); path.lineTo(20f, 22f); path.lineTo(4f, 22f)
                    path.lineTo(4f, 6f); path.close(); canvas.drawPath(path, paint); canvas.drawRect(8f, 10f, 16f, 18f, paint)
                }
                "terminal" -> {
                    canvas.drawRoundRect(2f, 4f, 22f, 20f, 3f, 3f, paint); path.moveTo(6f, 9f); path.lineTo(10f, 12f)
                    path.lineTo(6f, 15f); canvas.drawPath(path, paint); canvas.drawLine(13f, 16f, 18f, 16f, paint)
                }
                "guide" -> {
                    canvas.drawRoundRect(4f, 2f, 20f, 22f, 2f, 2f, paint); canvas.drawLine(8f, 2f, 8f, 22f, paint)
                    canvas.drawLine(11f, 8f, 17f, 8f, paint); canvas.drawLine(11f, 12f, 17f, 12f, paint)
                }
                "phone" -> {
                    path.moveTo(6f, 3f); path.lineTo(9f, 3f); path.lineTo(11f, 7f); path.lineTo(9f, 9f)
                    path.cubicTo(10f, 12f, 12f, 14f, 15f, 15f); path.lineTo(17f, 13f); path.lineTo(21f, 15f)
                    path.lineTo(21f, 18f); path.cubicTo(20f, 22f, 14f, 21f, 8f, 16f); path.cubicTo(3f, 11f, 2f, 5f, 6f, 3f)
                    canvas.drawPath(path, paint)
                }
                "apps" -> for (y in 4 until 19 step 7) for (x in 4 until 19 step 7) {
                    canvas.drawRoundRect(x.toFloat(), y.toFloat(), x + 4f, y + 4f, 1f, 1f, paint)
                }
                else -> {
                    canvas.drawCircle(12f, 12f, 7f, paint); canvas.drawCircle(12f, 12f, 2.5f, paint)
                    for (index in 0 until 8) {
                        val angle = index * Math.PI / 4
                        canvas.drawLine(12 + kotlin.math.cos(angle).toFloat() * 8, 12 + kotlin.math.sin(angle).toFloat() * 8,
                            12 + kotlin.math.cos(angle).toFloat() * 10, 12 + kotlin.math.sin(angle).toFloat() * 10, paint)
                    }
                }
            }
        }

        companion object {
            private val centers = HashMap<String, FloatArray>()
        }
    }

    companion object {
        private val UI_KEYS = setOf(
                "theme_mode", "theme_day_hour", "theme_night_hour", "light_theme_index", "dark_theme_index", "theme", "accent_color", "brand_text", "badge_text", "brand_color", "badge_color", "widget_left", "widget_right", "home_clock_style", "solar_manual", "solar_lat", "solar_lon",
                "custom_revision", "home_wallpaper", "wallpaper_home_v2", "glass_cards", "weather_enabled", "weather_age_days", "weather_fahrenheit", "flip_gradients", "split", "spacing",
                "dock", "dock_order", "dock_items", "dock_mono", "dock_labels", "dock_icons", "text",
                "projection", "media", "clock24", "layout_left", "layout_right", "layout_apps"
        )
        private val PROJECTION_PACKAGES = setOf("com.shihab.diplay", "com.shihab.diplay.hudtest", "com.andrerinas.headunitrevived")

        @JvmField
        var instance = WeakReference<HomeActivity>(null)

        @JvmStatic
        fun card(preferences: SharedPreferences, left: Boolean): String {
            val key = if (left) "layout_left" else "layout_right"
            if (preferences.contains(key)) return preferences.getString(key, if (left) "clock" else "media") ?: ""
            return if (left) {
                if (preferences.getBoolean("projection", true)) "clock" else "hidden"
            } else {
                if (preferences.getBoolean("media", true)) "media" else "hidden"
            }
        }

        @JvmStatic
        fun formatDuration(milliseconds: Long): String {
            val seconds = milliseconds / 1000 % 60
            return "${milliseconds / 60000}:${if (seconds < 10) "0" else ""}$seconds"
        }
    }
}
