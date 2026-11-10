package app.flare.auto

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiManager
import android.view.View

internal class WifiStatus(context: Context) : View(context) {
    private val paint = Paint(3)
    private val signal = Path()
    private val primary: Int
    private val background: Int
    private val secondary: Int
    private var connected = false
    private var connectivityManager: ConnectivityManager? = null
    private var registered = false
    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            post { refresh() }
        }

        override fun onLost(network: Network) {
            post { refresh() }
        }

        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
            post { refresh() }
        }
    }
    private val hotspot = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = refresh()
    }

    init {
        val theme = ThemeTokens.get(context)
        primary = theme.textPrimary
        background = theme.background
        secondary = theme.textSecondary
        signal.moveTo(2f, 8f)
        signal.cubicTo(7f, 3f, 17f, 3f, 22f, 8f)
        signal.moveTo(5f, 11f)
        signal.cubicTo(9f, 7f, 15f, 7f, 19f, 11f)
        signal.moveTo(8f, 14f)
        signal.cubicTo(10f, 12f, 14f, 12f, 16f, 14f)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        try {
            connectivityManager?.registerNetworkCallback(
                NetworkRequest.Builder().addTransportType(NetworkCapabilities.TRANSPORT_WIFI).build(),
                callback
            )
            registered = connectivityManager != null
        } catch (_: RuntimeException) {
        }
        context.registerReceiver(hotspot, IntentFilter("android.net.wifi.WIFI_AP_STATE_CHANGED"))
        refresh()
    }

    override fun onDetachedFromWindow() {
        if (registered) {
            connectivityManager?.unregisterNetworkCallback(callback)
            registered = false
        }
        context.unregisterReceiver(hotspot)
        super.onDetachedFromWindow()
    }

    private fun refresh() {
        var active = false
        try {
            connectivityManager?.allNetworks?.forEach { network ->
                val capabilities = connectivityManager?.getNetworkCapabilities(network)
                if (capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true) active = true
            }
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            active = active || wifiManager.javaClass.getMethod("getWifiApState").invoke(wifiManager) as? Int == 13
        } catch (_: Exception) {
        }
        contentDescription = if (active) "Wi-Fi connected or hotspot active" else "Wi-Fi disconnected"
        if (connected != active) {
            connected = active
            invalidate()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.save()
        val size = minOf(width, height).toFloat()
        canvas.translate((width - size) / 2, (height - size) / 2)
        canvas.scale(size / 24f, size / 24f)
        canvas.translate(2f, 2f)
        canvas.scale(20f / 24f, 20f / 24f)
        paint.color = if (connected) primary else secondary
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.8f
        paint.strokeCap = Paint.Cap.ROUND
        canvas.drawPath(signal, paint)
        paint.style = Paint.Style.FILL
        canvas.drawCircle(12f, 17.5f, 1.1f, paint)
        if (!connected) {
            paint.style = Paint.Style.STROKE
            paint.color = background
            paint.strokeWidth = 4f
            canvas.drawLine(16f, 16f, 22f, 22f, paint)
            canvas.drawLine(22f, 16f, 16f, 22f, paint)
            paint.color = secondary
            paint.strokeWidth = 1.6f
            canvas.drawLine(16f, 16f, 22f, 22f, paint)
            canvas.drawLine(22f, 16f, 16f, 22f, paint)
        }
        canvas.restore()
    }
}
