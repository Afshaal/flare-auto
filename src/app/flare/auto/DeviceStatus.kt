package app.flare.auto

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Canvas
import android.graphics.Path
import android.net.NetworkCapabilities
import android.os.Build
import android.telephony.TelephonyManager

/** Bluetooth (off / on / audio device connected) or mobile-network status glyph for the top bar. */
internal class DeviceStatus(context: Context, private val bluetooth: Boolean) : StatusIcon(context) {
    private val glyph = Path()
    private val slash = Path()
    private val adapter: BluetoothAdapter? = (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
    /** 0 off / unavailable, 1 on, 2 connected (Bluetooth) or available (mobile). */
    private var state = -1

    init {
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

    override fun broadcasts() = IntentFilter().apply {
        if (bluetooth) {
            addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
            addAction(BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED)
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
        } else {
            addAction(SIM_STATE_CHANGED)
        }
    }

    override val transport: Int? = if (bluetooth) null else NetworkCapabilities.TRANSPORT_CELLULAR

    override fun refresh() {
        var value = 0
        if (bluetooth) {
            try {
                if (adapter?.isEnabled == true) value = if (profileConnected(adapter)) 2 else 1
            } catch (_: SecurityException) {
            }
        } else {
            try {
                val telephony = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
                if (telephony?.simState == TelephonyManager.SIM_STATE_READY || networkUp(NetworkCapabilities.TRANSPORT_CELLULAR)) value = 2
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
            } else "Mobile network available"
            invalidate()
        }
    }

    @SuppressLint("MissingPermission")
    private fun profileConnected(value: BluetoothAdapter): Boolean {
        // targetSdk 28 uses the declared legacy BLUETOOTH permission. If raised to 31+, the runtime
        // CONNECT grant is required; denied vendor calls are also caught by refresh().
        if (Build.VERSION.SDK_INT >= 31 && context.applicationInfo.targetSdkVersion >= 31 &&
            context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) return false
        return value.getProfileConnectionState(BluetoothProfile.A2DP) == BluetoothAdapter.STATE_CONNECTED ||
            value.getProfileConnectionState(BluetoothProfile.HEADSET) == BluetoothAdapter.STATE_CONNECTED
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.save()
        canvas.glyphSpace()
        if (!bluetooth) canvas.translate(1.5f, 0f)
        paint.strokeWidth = 1.7f
        paint.color = if (state == 2) primary else secondary
        canvas.drawPath(glyph, paint)
        if (bluetooth && state == 0) {
            paint.color = backdrop
            paint.strokeWidth = 4f
            canvas.drawPath(slash, paint)
            paint.color = secondary
            paint.strokeWidth = 1.5f
            canvas.drawPath(slash, paint)
        }
        canvas.restore()
    }

    private companion object {
        /** TelephonyIntents.ACTION_SIM_STATE_CHANGED (not public API). */
        const val SIM_STATE_CHANGED = "android.intent.action.SIM_STATE_CHANGED"
    }
}
