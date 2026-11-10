package app.flare.auto

import android.content.ComponentName
import android.content.Context
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import java.lang.ref.WeakReference
import java.util.concurrent.Executors
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

internal object AsyncImages {
    private val executor = (Executors.newFixedThreadPool(2) as ThreadPoolExecutor).apply {
        setKeepAliveTime(30, TimeUnit.SECONDS)
        allowCoreThreadTimeOut(true)
    }
    private val main = Handler(Looper.getMainLooper())

    fun load(context: Context, loader: (Context) -> Drawable?): Drawable {
        val result = Deferred()
        val weak = WeakReference(result)
        val app = context.applicationContext
        executor.execute {
            val image = try { loader(app) } catch (_: Exception) { null }
            main.post { weak.get()?.complete(image) }
        }
        return result
    }

    fun appIcon(context: Context, target: String): Drawable = load(context) { app ->
        ComponentName.unflattenFromString(target)?.let { app.packageManager.getActivityIcon(it) }
    }

    private class Deferred : Drawable() {
        private var image: Drawable? = null
        private var alphaValue = 255
        private var filter: ColorFilter? = null
        fun complete(value: Drawable?) {
            image = value
            value?.alpha = alphaValue
            value?.colorFilter = filter
            invalidateSelf()
        }
        override fun draw(canvas: Canvas) {
            image?.let {
                val w = it.intrinsicWidth; val h = it.intrinsicHeight
                if (w > 0 && h > 0) {
                    val scale = minOf(bounds.width().toFloat() / w, bounds.height().toFloat() / h)
                    val dw = (w * scale).toInt(); val dh = (h * scale).toInt()
                    val left = bounds.left + (bounds.width() - dw) / 2
                    val top = bounds.top + (bounds.height() - dh) / 2
                    it.setBounds(left, top, left + dw, top + dh)
                } else it.bounds = bounds
                it.draw(canvas)
            }
        }
        override fun setAlpha(value: Int) { alphaValue = value; image?.alpha = value; invalidateSelf() }
        override fun setColorFilter(value: ColorFilter?) { filter = value; image?.colorFilter = value; invalidateSelf() }
        override fun getOpacity() = PixelFormat.TRANSLUCENT
    }
}
