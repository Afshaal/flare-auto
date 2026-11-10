package app.flare.auto

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.Drawable

internal class HomeGlassDrawable(color: Int, private val border: Int, private val radius: Float) : Drawable() {
    private val fill = (0xe6000000.toInt() or
        ((Color.red(color) * 3 / 4) shl 16) or
        ((Color.green(color) * 3 / 4) shl 8) or
        (Color.blue(color) * 3 / 4))
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    private var alpha = 255
    private var wash: LinearGradient? = null
    private var rim: LinearGradient? = null

    override fun onBoundsChange(bounds: Rect) {
        rect.set(bounds.left + 1f, bounds.top + 1f, bounds.right - 1f, bounds.bottom - 1f)
        wash = LinearGradient(0f, rect.top, 0f, rect.bottom, 0x08ffffff, 0x00ffffff, Shader.TileMode.CLAMP)
        rim = LinearGradient(0f, rect.top, 0f, rect.bottom, 0x28ffffff, 0x06ffffff, Shader.TileMode.CLAMP)
    }

    override fun draw(canvas: Canvas) {
        val bounds = bounds
        if (bounds.width() <= 2 || bounds.height() <= 2) return
        rect.set(bounds.left + 1f, bounds.top + 1f, bounds.right - 1f, bounds.bottom - 1f)
        paint.style = Paint.Style.FILL
        paint.shader = null
        paint.color = fill
        paint.alpha = Color.alpha(fill) * alpha / 255
        canvas.drawRoundRect(rect, radius, radius, paint)
        paint.shader = wash
        paint.alpha = alpha
        canvas.drawRoundRect(rect, radius, radius, paint)
        paint.shader = null
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1f
        paint.color = border
        paint.alpha = Color.alpha(border) * alpha / 255
        canvas.drawRoundRect(rect, radius, radius, paint)
        paint.shader = rim
        paint.alpha = alpha
        canvas.drawRoundRect(rect, radius, radius, paint)
        paint.shader = null
    }

    override fun setAlpha(value: Int) {
        alpha = value
        invalidateSelf()
    }

    override fun setColorFilter(filter: ColorFilter?) {
        paint.colorFilter = filter
        invalidateSelf()
    }

    override fun getOpacity() = PixelFormat.TRANSLUCENT
}
