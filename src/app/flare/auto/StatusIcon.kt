package app.flare.auto

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Canvas
import android.graphics.Paint
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.view.View

/**
 * Base for the top-bar status glyphs. Registers a broadcast receiver and (optionally) a network callback
 * while attached, unregisters both safely when detached, and refreshes on every change.
 */
internal abstract class StatusIcon(context: Context) : View(context) {
    protected val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    protected val primary: Int
    protected val secondary: Int
    protected val backdrop: Int
    protected var connectivity: ConnectivityManager? = null
        private set
    private var receiver: BroadcastReceiver? = null
    private var callback: ConnectivityManager.NetworkCallback? = null

    init {
        val theme = ThemeTokens.get(context)
        primary = theme.textPrimary
        secondary = theme.textSecondary
        backdrop = theme.background
    }

    /** Broadcasts that should trigger [refresh]. */
    protected abstract fun broadcasts(): IntentFilter

    /** Network transport to watch (NetworkCapabilities.TRANSPORT_*), or null for none. */
    protected open val transport: Int? = null

    /** Re-reads the state; call [invalidate] when it changed. Runs on the main thread. */
    protected abstract fun refresh()

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        val listener = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) = refresh()
        }
        context.registerReceiver(listener, broadcasts())
        receiver = listener
        transport?.let { type ->
            val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return@let
            connectivity = manager
            val networkCallback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) { post { refresh() } }
                override fun onLost(network: Network) { post { refresh() } }
                override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) { post { refresh() } }
            }
            try {
                manager.registerNetworkCallback(NetworkRequest.Builder().addTransportType(type).build(), networkCallback)
                callback = networkCallback
            } catch (_: RuntimeException) {
            }
        }
        refresh()
    }

    override fun onDetachedFromWindow() {
        callback?.let { try { connectivity?.unregisterNetworkCallback(it) } catch (_: RuntimeException) { } }
        callback = null
        receiver?.let { try { context.unregisterReceiver(it) } catch (_: IllegalArgumentException) { } }
        receiver = null
        super.onDetachedFromWindow()
    }

    /**
     * True if any current network uses [type]. A synchronous snapshot is needed so a freshly built Home shows
     * the right glyph at once; `allNetworks` has no synchronous replacement below API 31 (the callback above
     * keeps it fresh afterwards).
     */
    @Suppress("DEPRECATION")
    protected fun networkUp(type: Int): Boolean {
        val manager = connectivity ?: return false
        return manager.allNetworks.any { manager.getNetworkCapabilities(it)?.hasTransport(type) == true }
    }

    /** Centres a square, 24-unit glyph in the view, inset to 20/24. Call between canvas save/restore. */
    protected fun Canvas.glyphSpace() {
        val size = minOf(width, height).toFloat()
        translate((width - size) / 2, (height - size) / 2)
        scale(size / 24f, size / 24f)
        translate(2f, 2f)
        scale(20f / 24f, 20f / 24f)
    }
}
