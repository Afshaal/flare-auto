package app.flare.auto

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.drawable.Drawable

internal class GlassDrawable(
    private val fill: Int,
    private val radius: Float,
    private val border: Int
) : Drawable() {
    private val paint = Paint(3)
    private val rect = RectF()

    override fun draw(canvas: Canvas) {
        val bounds = bounds
        rect.set(bounds.left + 1f, bounds.top + 1f, bounds.right - 1f, bounds.bottom - 1f)
        paint.shader = null
        paint.style = Paint.Style.FILL
        paint.color = fill
        canvas.drawRoundRect(rect, radius, radius, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1f
        paint.color = border
        canvas.drawRoundRect(rect, radius, radius, paint)
    }

    override fun setAlpha(alpha: Int) = Unit
    override fun setColorFilter(colorFilter: ColorFilter?) = Unit
    override fun getOpacity() = PixelFormat.TRANSLUCENT
}
