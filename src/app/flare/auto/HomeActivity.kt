package app.flare.auto

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class HomeActivity : Activity() {
    private lateinit var theme: ThemeTokens
    private lateinit var cards: HomeCards
    private var environment: EnvironmentInfo? = null
    private var dimOverlay: View? = null
    private var resumed = false
    private lateinit var prefs: SharedPreferences
    private var driverId = "default"
    private var askDriver = false
    private var currentPage = 0
    private var renderedPages: String? = null
    private var uiDirty = false
    private var seenRebuildRequests = rebuildRequests
    private var attachedScale = 100
    private var mapRevision = 0L
    private lateinit var root: LinearLayout
    private lateinit var date: TextView
    private lateinit var startup: StartupActions
    private val handler = Handler(Looper.getMainLooper())
    private val dateFormat = SimpleDateFormat("EEE, MMM d", Locale.getDefault())

    private val screenOff = Runnable { if (resumed) startActivity(Intent(this, ScreenSaverActivity::class.java)) }

    private fun scheduleScreenOff() {
        handler.removeCallbacks(screenOff)
        val minutes = prefs.getInt(Keys.SAVER_TIMEOUT, 0)
        if (resumed && minutes > 0) handler.postDelayed(screenOff, minutes * 60_000L)
    }

    override fun onUserInteraction() {
        super.onUserInteraction()
        if (::startup.isInitialized) startup.cancel()
        if (::prefs.isInitialized) scheduleScreenOff()
    }

    private val settingsChanged = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == null || key in Keys.HOME_UI) uiDirty = true
    }

    private val tick = object : Runnable {
        override fun run() {
            if (theme !== ThemeTokens.get(this@HomeActivity)) build()
            date.text = dateFormat.format(Date())
            environment?.let {
                it.updateSolar()
                dimOverlay?.visibility = if (prefs.getBoolean(Keys.AUTO_DIM, false) && it.night()) View.VISIBLE else View.GONE
            }
            val environments = cards.environments
            for (index in 1 until environments.size) environments[index].updateSolar()
            handler.postDelayed(this, 60_000)
        }
    }

    private val progressTick = object : Runnable {
        override fun run() {
            if (!resumed || playbackButton == null) return
            updateProgress()
            if (isPlaying(selectedController) && lastDuration > 0) handler.postDelayed(this, 1000)
        }
    }

    private fun column() = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    private fun row() = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
    }

    /** Opens a stock head-unit screen, or the app the driver chose for that role in settings. */
    private fun launchScreen(screen: KnownApps.Screen) {
        val chosen = prefs.getString(Keys.APP_PREFIX + KnownApps.overrideRole(screen), "") ?: ""
        if (chosen.isNotEmpty()) {
            launchTarget(chosen)
            return
        }
        if (!AppLookup.start(this, Intent().setClassName(screen.packageName, screen.className))) {
            Toast.makeText(this, "App unavailable on this device", Toast.LENGTH_SHORT).show()
        }
    }

    private fun launchTarget(target: String) {
        if (!AppLookup.launch(this, target)) Toast.makeText(this, "Selected app is unavailable", Toast.LENGTH_SHORT).show()
    }

    /** Projection buttons: the app chosen in settings, else the first installed package of [packages]. */
    private fun launchFirst(packages: Array<String>) {
        val chosen = prefs.getString(KnownApps.projectionOverrideKey(packages), "") ?: ""
        if (chosen.isNotEmpty()) {
            launchTarget(chosen)
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
                key == KeyEvent.KEYCODE_MEDIA_PREVIOUS -> controls.skipToPrevious()
                key == KeyEvent.KEYCODE_MEDIA_NEXT -> controls.skipToNext()
                controller.playbackState?.state == PlaybackState.STATE_PLAYING -> controls.pause()
                else -> controls.play()
            }
            return
        }
        val audio = getSystemService(AUDIO_SERVICE) as AudioManager
        audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, key))
        audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, key))
    }

    override fun attachBaseContext(base: Context) {
        askDriver = ProfileStore.prepareStartup(base)
        super.attachBaseContext(UiScale.wrap(base, Keys.UI_SCALE_HOME))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        driverId = ProfileStore.active(this)
        window.setBackgroundDrawable(ColorDrawable(ThemeTokens.get(this).background))
        prefs = ProfileStore.preferences(this)
        attachedScale = prefs.getInt(Keys.UI_SCALE_HOME, 100)
        currentPage = savedInstanceState?.getInt(STATE_PAGE) ?: prefs.getInt(Keys.DASHBOARD_DEFAULT, 0)
        renderedPages = prefs.getString(Keys.DASHBOARD_PAGES, null)
        startup = StartupActions(this, prefs, handler)
        window.immersive()
        prefs.registerOnSharedPreferenceChangeListener(settingsChanged)
        build()
    }

    private fun build() {
        if (::cards.isInitialized) {
            cards.environments.forEach { it.stop() }
            cards.maps.forEach { it.stop() }
        }
        environment = null
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
        resetMediaCache()
        cards = HomeCards(this, prefs, theme,
            onTarget = ::launchTarget, onProjection = ::launchFirst, onMedia = ::media,
            onSeek = { selectedController?.transportControls?.seekTo(it) },
            onDragging = { dragging = it }, onNarrow = { mediaNarrow = it }, duration = { lastDuration })
        root = column()
        root.background = WallpaperPack.home(this, theme)
        val spacing = dp(prefs.getInt(Keys.SPACING, 14))
        root.setPadding(spacing, dp(10), spacing, dp(10))
        val frame = FrameLayout(this)
        frame.addView(root)
        dimOverlay = View(this).apply {
            setBackgroundColor(DIM_COLOR)
            isClickable = false
            visibility = View.GONE
        }
        frame.addView(dimOverlay, FrameLayout.LayoutParams(MATCH, MATCH))
        setContentView(frame)
        buildTopBar()
        val content = row()
        root.addView(content, LinearLayout.LayoutParams(MATCH, 0, 1f).apply {
            topMargin = dp(12)
            bottomMargin = dp(12)
        })
        buildColumns(content)
        if (resumed) cards.environments.forEach { it.start() }
        buildDock()
        if (resumed) refreshPlayback()
    }

    private fun buildTopBar() {
        val top = HomeChrome.TopBar(this, prefs, theme)
        date = top.date
        date.setOnLongClickListener { startActivity(Intent(this, ProfilesActivity::class.java)); true }
        root.addView(top)
        root.addView(View(this).apply { background = HomeChrome.accent(theme) }, LinearLayout.LayoutParams(MATCH, dp(1)))
    }

    private fun buildColumns(content: LinearLayout) {
        val raw = prefs.getString(Keys.DASHBOARD_PAGES, null)
        val pages = DashboardPages.decode(raw)
        if (pages != null) {
            if (renderedPages != raw) {
                currentPage = prefs.getInt(Keys.DASHBOARD_DEFAULT, 0)
                renderedPages = raw
            }
            currentPage = currentPage.coerceIn(0, pages.lastIndex)
            val strip = CardPages(this, currentPage, 0, pages.lastIndex, { index, host ->
                val board = GridBoard(this, pages[index])
                val active = index == currentPage
                val renderer = if (active) cards else HomeCards(this, prefs, theme, preview = true)
                pages[index].forEach { tile ->
                    val cell = row()
                    if (active) buildCard(cell, tile.type, tile.h < 5) else renderer.build(cell, tile.type, tile.h < 5)
                    board.addView(CardFrame(this, cell, tile.type))
                }
                if (!active) mirrorMedia(renderer)
                host.connect(board)
                board
            }, { next -> currentPage = next; build() })
            content.addView(strip, LinearLayout.LayoutParams(MATCH, MATCH))
            if (pages.size > 1) {
                val dots = TextView(this).apply {
                    text = pages.indices.joinToString("  ") { if (it == currentPage) "●" else "○" }
                    setTextColor(theme.textSecondary)
                    textSize = 13 * prefs.textScale
                    gravity = Gravity.CENTER
                    contentDescription = "Page ${currentPage + 1} of ${pages.size}"
                }
                root.addView(dots, LinearLayout.LayoutParams(MATCH, dp(28)))
            }
            return
        }

        // Installs that never saved dashboard pages: the original two-card split.
        val left = LayoutPrefs.legacyCard(prefs, true)
        val right = LayoutPrefs.legacyCard(prefs, false)
        val showLeft = left != "hidden"
        val showRight = right != "hidden"
        val split = LayoutPrefs.split(prefs)
        if (showLeft) buildCard(content, left, weight = if (showRight) split.toFloat() else 1f)
        if (showLeft && showRight) content.addView(View(this), LinearLayout.LayoutParams(dp(12), 1))
        if (showRight) buildCard(content, right, weight = if (showLeft) (100 - split).toFloat() else 1f)
    }

    /** Shows the current track on a page that is being swiped in but is not the live one. */
    private fun mirrorMedia(renderer: HomeCards) {
        val metadata = selectedController?.metadata
        renderer.trackTitle?.text = trackTitle?.text ?: metadata?.getString(MediaMetadata.METADATA_KEY_TITLE) ?: NO_MEDIA
        renderer.trackArtist?.text = trackArtist?.text ?: metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: ""
        renderer.albumArt?.setImageDrawable(albumArt?.drawable?.constantState?.newDrawable()
            ?: (metadata?.getBitmap(MediaMetadata.METADATA_KEY_ART) ?: metadata?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART))
                ?.let { BitmapDrawable(resources, it) })
        renderer.playbackButton?.setImageDrawable(MediaGlyph(if (isPlaying(selectedController)) "pause" else "play", theme.textPrimary))
    }

    private fun buildCard(content: LinearLayout, card: String, compact: Boolean = false, weight: Float = 1f) {
        cards.build(content, card, compact, weight)
        environment = cards.environments.getOrNull(0)
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
        root.addView(HomeChrome.dock(this, prefs, theme) { _, item ->
            when (item.action) {
                DockAction.AUDIO -> launchScreen(KnownApps.AUDIO_SCREEN)
                DockAction.RADIO -> launchScreen(KnownApps.RADIO_SCREEN)
                DockAction.BLUETOOTH -> launchScreen(KnownApps.PHONE_SCREEN)
                DockAction.SCREEN_OFF -> startActivity(Intent(this, ScreenSaverActivity::class.java))
                DockAction.APPS -> startActivity(Intent(this, AppsActivity::class.java))
                DockAction.APP -> launchTarget(item.target)
            }
        }, LinearLayout.LayoutParams(MATCH, dp(prefs.getInt(Keys.DOCK, 70))))
    }

    // ---- media card -------------------------------------------------------------------------------------------

    private var playbackButton: ImageView? = null
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
    private var preferredToken: MediaSession.Token? = null
    private var artworkToken: MediaSession.Token? = null
    private var lastTitle: String? = null
    private var lastArtist: String? = null
    private var lastSource: String? = null
    private var lastElapsedSecond = -1L
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
                    updateSessions(sessionManager?.getActiveSessions(ComponentName(this@HomeActivity, MediaAccess::class.java)))
                } catch (_: SecurityException) {
                    updateSessions(null)
                }
            }
        }
    }

    private val sessionsChanged = MediaSessionManager.OnActiveSessionsChangedListener { list -> updateSessions(list) }

    private fun isPlaying(controller: MediaController?): Boolean =
        controller?.playbackState?.state == PlaybackState.STATE_PLAYING

    private fun startMedia() {
        sourceLabels.clear()
        val manager = getSystemService(MEDIA_SESSION_SERVICE) as MediaSessionManager
        sessionManager = manager
        try {
            val listener = ComponentName(this, MediaAccess::class.java)
            manager.addOnActiveSessionsChangedListener(sessionsChanged, listener, handler)
            mediaAccessGranted = true
            updateSessions(manager.getActiveSessions(listener))
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

    /**
     * Prefers what is playing. While projection keeps reporting the same track that Bluetooth audio also
     * plays, the projection session stays selected so the card does not flicker between the two.
     */
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
        val projection = previous?.packageName in KnownApps.PROJECTION
        selectedController = if (projection && firstName == "BLUETOOTH" && sameTrack) previous else firstPlaying ?: controllers.firstOrNull()
        preferredToken = selectedController?.sessionToken
        refreshPlayback()
    }

    private fun resetMediaCache() {
        lastTitle = null
        lastArtist = null
        lastSource = null
        lastElapsedSecond = -1
        lastArt = null
        artworkToken = null
        lastPlaying = null
        lastDuration = -1
        playGlyph = MediaGlyph("play", theme.textPrimary)
        pauseGlyph = MediaGlyph("pause", theme.textPrimary)
        noteGlyph = MediaGlyph("note", theme.textSecondary)
    }

    private fun sourceName(controller: MediaController?): String {
        val packageName = controller?.packageName ?: return ""
        KnownApps.mediaSourceName(contentResolver, packageName)?.let { return it }
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
        val controller = selectedController
        val source = sourceName(controller)
        if (source != lastSource) {
            sourceLabel?.text = source
            sourceLabel?.visibility = if (source.isEmpty()) View.GONE else View.VISIBLE
            lastSource = source
        }
        val metadata = controller?.metadata
        val title = NowPlaying.value(metadata, MediaMetadata.METADATA_KEY_TITLE).ifEmpty { NO_MEDIA }
        val artist = NowPlaying.value(metadata, MediaMetadata.METADATA_KEY_ARTIST)
            .ifEmpty { NowPlaying.value(metadata, MediaMetadata.METADATA_KEY_ALBUM_ARTIST) }
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
        val token = controller?.sessionToken
        // Some sources send artwork only once per track: keep it while the same session plays the same title.
        val art = reportedArt ?: if (token != null && token == artworkToken && title == previousTitle && title != NO_MEDIA) lastArt else null
        if (reportedArt != null) artworkToken = token
        if (art == null) artworkToken = null
        if (art !== lastArt || lastDuration < 0) {
            if (art != null) {
                albumArt?.setPadding(0, 0, 0, 0)
                albumArt?.setImageBitmap(art)
            } else {
                val inset = dp(12)
                albumArt?.setPadding(inset, inset, inset, inset)
                albumArt?.setImageDrawable(noteGlyph)
            }
            lastArt = art
        }
        val duration = metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0
        if (duration != lastDuration) {
            seekRow?.visibility = if (duration > 0 && !mediaNarrow) View.VISIBLE else View.GONE
            progress?.max = minOf(duration, Int.MAX_VALUE.toLong()).toInt()
            if (duration > 0) total?.text = formatDuration(duration)
            lastDuration = duration
        }
        val playing = isPlaying(controller)
        if (lastPlaying != playing) {
            button.setImageDrawable(if (playing) pauseGlyph else playGlyph)
            button.contentDescription = if (playing) "Pause" else "Play"
            lastPlaying = playing
        }
        handler.removeCallbacks(progressTick)
        updateProgress()
        if (resumed && playing && duration > 0) handler.postDelayed(progressTick, 1000)
    }

    /** Once per second while playing: seek bar position and elapsed time (text only when the second changes). */
    private fun updateProgress() {
        if (lastDuration <= 0 || dragging) return
        val state = (selectedController ?: return).playbackState
        var position = state?.position ?: 0L
        if (state?.state == PlaybackState.STATE_PLAYING && state.lastPositionUpdateTime > 0) {
            position += ((SystemClock.elapsedRealtime() - state.lastPositionUpdateTime) * state.playbackSpeed).toLong()
        }
        position = position.coerceIn(0, lastDuration)
        progress?.progress = minOf(position, Int.MAX_VALUE.toLong()).toInt()
        val second = position / 1000
        if (second != lastElapsedSecond) {
            elapsed?.text = formatDuration(position)
            lastElapsedSecond = second
        }
    }

    override fun onSaveInstanceState(out: Bundle) {
        out.putInt(STATE_PAGE, currentPage)
        super.onSaveInstanceState(out)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) window.immersive()
    }

    /** Home is the root of the task: Back does nothing. */
    @Deprecated("Deprecated in Android 13; this launcher targets API 28")
    override fun onBackPressed() = Unit

    override fun onResume() {
        super.onResume()
        if (driverId != ProfileStore.active(this)) { recreate(); return }
        if (askDriver) { askDriver = false; startActivity(Intent(this, ProfilesActivity::class.java)); return }
        if (attachedScale != prefs.getInt(Keys.UI_SCALE_HOME, 100)) { recreate(); return }
        resumed = true
        val revision = OfflineMapStore.revision(this)
        if (revision != mapRevision) { mapRevision = revision; uiDirty = true }
        if (seenRebuildRequests != rebuildRequests) { seenRebuildRequests = rebuildRequests; uiDirty = true }
        if (uiDirty || theme !== ThemeTokens.get(this)) {
            build()
            uiDirty = false
        } else {
            cards.environments.forEach { it.start() }
        }
        cards.maps.forEach { it.start() }
        handler.removeCallbacks(tick)
        handler.post(tick)
        startMedia()
        if (HostedWidgets.ids(prefs.getString(Keys.DASHBOARD_PAGES, null)).isNotEmpty()) HostedWidgets.start(this)
        startup.onResume()
        startup.checkBoot()
        scheduleScreenOff()
        if (prefs.getBoolean(Keys.AWAKE, true)) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    override fun onPause() {
        resumed = false
        handler.removeCallbacks(tick)
        handler.removeCallbacks(screenOff)
        startup.onPause()
        stopMedia()
        HostedWidgets.stop(this)
        cards.environments.forEach { it.stop() }
        cards.maps.forEach { it.stop() }
        super.onPause()
    }

    override fun onDestroy() {
        prefs.unregisterOnSharedPreferenceChangeListener(settingsChanged)
        startup.cancel()
        HostedWidgets.destroy(this)
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    companion object {
        private const val STATE_PAGE = "dashboard_page"
        private const val NO_MEDIA = "No media playing"
        private const val DIM_COLOR = 0x4d000000
        @Volatile private var rebuildRequests = 0

        /** Asks Home to rebuild when it next resumes (for changes no preference listener reports, e.g. a full reset). */
        fun requestRebuild() { rebuildRequests++ }

        /** m:ss for a media position in milliseconds. */
        fun formatDuration(milliseconds: Long): String {
            val seconds = milliseconds / 1000 % 60
            return "${milliseconds / 60000}:${if (seconds < 10) "0" else ""}$seconds"
        }
    }
}
