package app.flare.auto

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable

/** Magnifier icon (24-unit grid) for the destination search entry points. */
internal class SearchGlyph(color: Int) : Drawable() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 2.4f; strokeCap = Paint.Cap.ROUND; this.color = color
    }

    override fun draw(canvas: Canvas) {
        val size = minOf(bounds.width(), bounds.height()).toFloat()
        if (size <= 0f) return
        canvas.save()
        canvas.translate(bounds.left + (bounds.width() - size) / 2, bounds.top + (bounds.height() - size) / 2)
        canvas.scale(size / 24f, size / 24f)
        canvas.drawCircle(10.5f, 10.5f, 6.5f, paint)
        canvas.drawLine(15.5f, 15.5f, 21f, 21f, paint)
        canvas.restore()
    }

    override fun setAlpha(alpha: Int) = Unit
    override fun setColorFilter(colorFilter: ColorFilter?) = Unit
    @Deprecated("Deprecated in Java")
    override fun getOpacity() = PixelFormat.TRANSLUCENT
}
