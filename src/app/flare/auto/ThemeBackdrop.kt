package app.flare.auto

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.Drawable

internal class ThemeBackdrop(@JvmField val theme: ThemeTokens) : Drawable() {
    private val line = Path()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

    override fun onBoundsChange(bounds: Rect) {
        val width = bounds.width().toFloat()
        val height = bounds.height().toFloat()
        line.rewind()
        line.moveTo(-10f, height * .61f)
        line.cubicTo(width * .2f, height * .50f, width * .35f, height * .72f, width * .6f, height * .56f)
        line.cubicTo(width * .8f, height * .38f, width * .9f, height * .32f, width + 10f, height * .43f)
    }

    override fun draw(canvas: Canvas) {
        canvas.save()
        canvas.clipRect(bounds)
        canvas.drawColor(theme.background)
        paint.style = Paint.Style.STROKE
        for (width in 18 downTo 2 step 2) {
            paint.strokeWidth = width.toFloat()
            paint.color = Color.argb(5, Color.red(theme.accent), Color.green(theme.accent), Color.blue(theme.accent))
            canvas.drawPath(line, paint)
        }
        paint.strokeWidth = 1.5f
        paint.color = theme.accent
        canvas.drawPath(line, paint)
        canvas.restore()
    }

    override fun setAlpha(alpha: Int) = Unit
    override fun setColorFilter(colorFilter: android.graphics.ColorFilter?) = Unit
    @Deprecated("Deprecated in Java")
    override fun getOpacity() = PixelFormat.OPAQUE
}
