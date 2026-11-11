package app.flare.auto

import android.content.Context
import android.content.IntentFilter
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager

/** Wi-Fi glyph: solid when a Wi-Fi network is up or the hotspot is on, crossed out otherwise. */
internal class WifiStatus(context: Context) : StatusIcon(context) {
    private val signal = Path().apply {
        moveTo(2f, 8f)
        cubicTo(7f, 3f, 17f, 3f, 22f, 8f)
        moveTo(5f, 11f)
        cubicTo(9f, 7f, 15f, 7f, 19f, 11f)
        moveTo(8f, 14f)
        cubicTo(10f, 12f, 14f, 12f, 16f, 14f)
    }
    private var connected = false

    override fun broadcasts() = IntentFilter(WIFI_AP_STATE_CHANGED)

    override val transport: Int? = NetworkCapabilities.TRANSPORT_WIFI

    override fun refresh() {
        var active = false
        try {
            active = networkUp(NetworkCapabilities.TRANSPORT_WIFI) || hotspotEnabled()
        } catch (_: Exception) {
        }
        contentDescription = if (active) "Wi-Fi connected or hotspot active" else "Wi-Fi disconnected"
        if (connected != active) {
            connected = active
            invalidate()
        }
    }

    /** WifiManager.getWifiApState() is hidden API; reached by reflection, as before. */
    private fun hotspotEnabled(): Boolean {
        val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        return wifi.javaClass.getMethod("getWifiApState").invoke(wifi) as? Int == WIFI_AP_STATE_ENABLED
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.save()
        canvas.glyphSpace()
        paint.color = if (connected) primary else secondary
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.8f
        canvas.drawPath(signal, paint)
        paint.style = Paint.Style.FILL
        canvas.drawCircle(12f, 17.5f, 1.1f, paint)
        if (!connected) {
            paint.style = Paint.Style.STROKE
            paint.color = backdrop
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

    private companion object {
        /** WifiManager.WIFI_AP_STATE_CHANGED_ACTION and WIFI_AP_STATE_ENABLED (hidden API). */
        const val WIFI_AP_STATE_CHANGED = "android.net.wifi.WIFI_AP_STATE_CHANGED"
        const val WIFI_AP_STATE_ENABLED = 13
    }
}
