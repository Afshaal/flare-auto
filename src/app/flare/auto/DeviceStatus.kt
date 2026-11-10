package app.flare.auto

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothProfile
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
import android.telephony.TelephonyManager
import android.view.View

internal class DeviceStatus(context: Context, private val bluetooth: Boolean) : View(context) {
    private val paint = Paint(3)
    private val glyph = Path()
    private val slash = Path()
    private val primary: Int
    private val secondary: Int
    private val background: Int
    private val adapter = BluetoothAdapter.getDefaultAdapter()
    private var connectivityManager: ConnectivityManager? = null
    private var registered = false
    private var receiverRegistered = false
    private var state = -1
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            refresh()
        }
    }
    private val network = object : ConnectivityManager.NetworkCallback() {
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

    init {
        val theme = ThemeTokens.get(context)
        primary = theme.textPrimary
        secondary = theme.textSecondary
        background = theme.background
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.7f
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeJoin = Paint.Join.ROUND
        if (bluetooth) {
            glyph.moveTo(12f, 2f)
            glyph.lineTo(12f, 22f)
            glyph.lineTo(18f, 17f)
            glyph.lineTo(6f, 7f)
            glyph.moveTo(6f, 17f)
            glyph.lineTo(18f, 7f)
            glyph.lineTo(12f, 2f)
        } else {
            for (index in 0 until 4) {
                val x = 3f + index * 5
                glyph.moveTo(x, 21f)
                glyph.lineTo(x, 18f - index * 4)
            }
        }
        slash.moveTo(4f, 20f)
        slash.lineTo(20f, 4f)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        val filter = IntentFilter()
        if (bluetooth) {
            filter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
            filter.addAction(BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED)
            filter.addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
            filter.addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
        } else {
            filter.addAction("android.intent.action.SIM_STATE_CHANGED")
        }
        context.registerReceiver(receiver, filter)
        receiverRegistered = true
        if (!bluetooth) {
            try {
                connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                connectivityManager?.registerNetworkCallback(
                    NetworkRequest.Builder().addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR).build(),
                    network
                )
                registered = connectivityManager != null
            } catch (_: RuntimeException) {
            }
        }
        refresh()
    }

    override fun onDetachedFromWindow() {
        if (registered) {
            connectivityManager?.unregisterNetworkCallback(network)
            registered = false
        }
        if (receiverRegistered) {
            context.unregisterReceiver(receiver)
            receiverRegistered = false
        }
        super.onDetachedFromWindow()
    }

    private fun refresh() {
        var value = 0
        if (bluetooth) {
            try {
                if (adapter?.isEnabled == true) {
                    value = 1
                    if (profileConnected(adapter)) {
                        value = 2
                    }
                }
            } catch (_: SecurityException) {
            }
        } else {
            try {
                val telephony = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
                if (telephony?.simState == TelephonyManager.SIM_STATE_READY) value = 2
                connectivityManager?.allNetworks?.forEach { network ->
                    val capabilities = connectivityManager?.getNetworkCapabilities(network)
                    if (capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true) value = 2
                }
            } catch (_: RuntimeException) {
            }
            visibility = if (value == 0) GONE else VISIBLE
        }
        if (value != state) {
            state = value
            contentDescription = if (bluetooth) {
                when (value) {
                    0 -> "Bluetooth off"
                    1 -> "Bluetooth on"
                    else -> "Bluetooth connected"
                }
            } else {
                "Mobile network available"
            }
            invalidate()
        }
    }

    @android.annotation.SuppressLint("MissingPermission")
    private fun profileConnected(value: BluetoothAdapter): Boolean {
        // targetSdk 28 uses the declared legacy BLUETOOTH permission. If raised to 31+,
        // the runtime CONNECT grant is required; denied vendor calls are also caught by refresh().
        if (android.os.Build.VERSION.SDK_INT >= 31 && context.applicationInfo.targetSdkVersion >= 31 &&
            context.checkSelfPermission("android.permission.BLUETOOTH_CONNECT") != android.content.pm.PackageManager.PERMISSION_GRANTED) return false
        return value.getProfileConnectionState(BluetoothProfile.A2DP) == BluetoothAdapter.STATE_CONNECTED ||
            value.getProfileConnectionState(BluetoothProfile.HEADSET) == BluetoothAdapter.STATE_CONNECTED
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.save()
        val size = minOf(width, height).toFloat()
        canvas.translate((width - size) / 2, (height - size) / 2)
        canvas.scale(size / 24f, size / 24f)
        canvas.translate(2f, 2f)
        canvas.scale(20f / 24f, 20f / 24f)
        if (!bluetooth) canvas.translate(1.5f, 0f)
        paint.strokeWidth = 1.7f
        paint.color = if (state == 2) primary else secondary
        canvas.drawPath(glyph, paint)
        if (bluetooth && state == 0) {
            paint.color = background
            paint.strokeWidth = 4f
            canvas.drawPath(slash, paint)
            paint.color = secondary
            paint.strokeWidth = 1.5f
            canvas.drawPath(slash, paint)
        }
        canvas.restore()
    }
}
