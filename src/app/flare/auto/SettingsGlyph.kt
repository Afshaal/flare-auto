package app.flare.auto

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.widget.TextView

/**
 * Line icon for a Launcher settings category, drawn on a 24-unit grid. It takes the colour of [label] when it
 * draws, so it follows the selected/unselected text colour and theme changes without extra bookkeeping.
 */
internal class SettingsGlyph(category: String, private val label: TextView) : Drawable() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 1.8f; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
    }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val dots = ArrayList<FloatArray>()
    private val path = Path().apply {
        when (category) {
            "APPEARANCE" -> { // palette
                moveTo(12f, 3f); cubicTo(6.5f, 3f, 3f, 7f, 3f, 12f); cubicTo(3f, 17f, 7f, 21f, 12f, 21f)
                cubicTo(13.5f, 21f, 14f, 20f, 13.5f, 18.8f); cubicTo(13f, 17.5f, 13.8f, 16f, 15.3f, 16f)
                lineTo(17f, 16f); cubicTo(19.5f, 16f, 21f, 14.5f, 21f, 12f); cubicTo(21f, 7f, 17f, 3f, 12f, 3f); close()
                dots += floatArrayOf(7.5f, 11.5f); dots += floatArrayOf(10f, 7.5f); dots += floatArrayOf(14.5f, 7.5f); dots += floatArrayOf(17f, 11.5f)
            }
            "PERSONALIZATION" -> { // sliders
                moveTo(4f, 6f); lineTo(20f, 6f); moveTo(4f, 12f); lineTo(20f, 12f); moveTo(4f, 18f); lineTo(20f, 18f)
                addCircle(9f, 6f, 2.2f, Path.Direction.CW); addCircle(15f, 12f, 2.2f, Path.Direction.CW); addCircle(7f, 18f, 2.2f, Path.Direction.CW)
            }
            "SCREENSAVER" -> { // crescent moon
                moveTo(20f, 14.5f); cubicTo(15f, 16.5f, 9f, 12.5f, 9.5f, 4f)
                cubicTo(5.5f, 5.5f, 3f, 9f, 3.5f, 13f); cubicTo(4f, 17.5f, 8f, 21f, 12.5f, 20.8f); cubicTo(16f, 20.6f, 19f, 18f, 20f, 14.5f); close()
            }
            SettingsActivity.OFFLINE_MAPS -> { // folded map
                moveTo(3f, 6f); lineTo(9f, 4f); lineTo(15f, 6f); lineTo(21f, 4f); lineTo(21f, 18f); lineTo(15f, 20f); lineTo(9f, 18f); lineTo(3f, 20f); close()
                moveTo(9f, 4f); lineTo(9f, 18f); moveTo(15f, 6f); lineTo(15f, 20f)
            }
            "STARTUP" -> { // power
                arcTo(RectF(4f, 5f, 20f, 21f), -50f, 280f, true); moveTo(12f, 3f); lineTo(12f, 11f)
            }
            "APPLICATION SHORTCUTS" -> { // app grid
                for (x in listOf(4f, 13.5f)) for (y in listOf(4f, 13.5f)) addRoundRect(RectF(x, y, x + 6.5f, y + 6.5f), 1.6f, 1.6f, Path.Direction.CW)
            }
            "PERMISSIONS" -> { // shield with tick
                moveTo(12f, 3f); lineTo(19.5f, 6f); lineTo(19.5f, 11f); cubicTo(19.5f, 16f, 16.3f, 19.5f, 12f, 21f)
                cubicTo(7.7f, 19.5f, 4.5f, 16f, 4.5f, 11f); lineTo(4.5f, 6f); close()
                moveTo(8.8f, 12f); lineTo(11f, 14.2f); lineTo(15.4f, 9.6f)
            }
            "DEVICE" -> { // head-unit screen
                addRoundRect(RectF(3f, 4.5f, 21f, 16f), 2f, 2f, Path.Direction.CW); moveTo(12f, 16f); lineTo(12f, 19.5f); moveTo(8f, 19.5f); lineTo(16f, 19.5f)
            }
            else -> { // about: info
                addCircle(12f, 12f, 9f, Path.Direction.CW); moveTo(12f, 11f); lineTo(12f, 16.5f); dots += floatArrayOf(12f, 7.8f)
            }
        }
    }

    override fun draw(canvas: Canvas) {
        val size = minOf(bounds.width(), bounds.height()).toFloat()
        if (size <= 0f) return
        paint.color = label.currentTextColor; fill.color = label.currentTextColor
        canvas.save()
        canvas.translate(bounds.left + (bounds.width() - size) / 2, bounds.top + (bounds.height() - size) / 2)
        canvas.scale(size / 24f, size / 24f)
        canvas.drawPath(path, paint)
        for (d in dots) canvas.drawCircle(d[0], d[1], 1.3f, fill)
        canvas.restore()
    }

    override fun setAlpha(alpha: Int) = Unit
    override fun setColorFilter(colorFilter: ColorFilter?) = Unit
    @Deprecated("Deprecated in Java")
    override fun getOpacity() = PixelFormat.TRANSLUCENT
}
