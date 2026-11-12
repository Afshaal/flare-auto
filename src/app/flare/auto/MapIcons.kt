package app.flare.auto

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.drawable.Drawable
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/** Base for the line icons below: draws on a 24-unit grid, centred and scaled into the bounds. */
internal abstract class GridIcon(color: Int, stroke: Float) : Drawable() {
    protected val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = stroke; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND; this.color = color
    }
    protected val path = Path()

    var color: Int
        get() = paint.color
        set(value) { paint.color = value; invalidateSelf() }

    final override fun draw(canvas: Canvas) {
        val size = minOf(bounds.width(), bounds.height()).toFloat()
        if (size <= 0f) return
        canvas.save()
        canvas.translate(bounds.left + (bounds.width() - size) / 2, bounds.top + (bounds.height() - size) / 2)
        canvas.scale(size / 24f, size / 24f)
        path.rewind()
        drawGrid(canvas)
        canvas.restore()
    }

    protected abstract fun drawGrid(canvas: Canvas)

    protected fun fill(block: () -> Unit) { paint.style = Paint.Style.FILL; block(); paint.style = Paint.Style.STROKE }

    override fun setAlpha(alpha: Int) { paint.alpha = alpha }
    override fun setColorFilter(colorFilter: ColorFilter?) { paint.colorFilter = colorFilter }
    @Deprecated("Deprecated in Java")
    override fun getOpacity() = PixelFormat.TRANSLUCENT
}

/**
 * Map, search and navigation icons. Kinds: back, recenter, headingup, northup, volume, mute, plus, minus, info,
 * star, starred, close, home, work, recent, pin, search.
 */
internal class MapIcon(private val kind: String, color: Int) : GridIcon(color, 2.1f) {
    override fun drawGrid(canvas: Canvas) {
        val p = path
        when (kind) {
            "back" -> { p.moveTo(14.5f, 5f); p.lineTo(7.5f, 12f); p.lineTo(14.5f, 19f); canvas.drawPath(p, paint) }
            "recenter" -> {
                // Navigation arrow, outlined: "back to my position, following".
                p.moveTo(12f, 3.5f); p.lineTo(19f, 20f); p.lineTo(12f, 16.2f); p.lineTo(5f, 20f); p.close()
                canvas.drawPath(p, paint)
            }
            "headingup" -> {
                // Arrow in perspective over a tilted ground plane: heading-up 3D.
                p.moveTo(3f, 19.5f); p.lineTo(21f, 19.5f); p.lineTo(17f, 12.5f); p.lineTo(7f, 12.5f); p.close()
                canvas.drawPath(p, paint)
                p.rewind(); p.moveTo(12f, 3f); p.lineTo(15.2f, 10f); p.lineTo(12f, 8.6f); p.lineTo(8.8f, 10f); p.close()
                fill { canvas.drawPath(p, paint) }
            }
            "northup" -> {
                // Compass needle with the north half filled, "N" above.
                p.moveTo(12f, 8f); p.lineTo(15f, 15.5f); p.lineTo(12f, 23f); p.lineTo(9f, 15.5f); p.close(); canvas.drawPath(p, paint)
                p.rewind(); p.moveTo(12f, 8f); p.lineTo(15f, 15.5f); p.lineTo(9f, 15.5f); p.close(); fill { canvas.drawPath(p, paint) }
                p.rewind(); p.moveTo(9.8f, 6f); p.lineTo(9.8f, 1f); p.lineTo(14.2f, 6f); p.lineTo(14.2f, 1f)
                val w = paint.strokeWidth; paint.strokeWidth = 1.6f; canvas.drawPath(p, paint); paint.strokeWidth = w
            }
            "volume", "mute" -> {
                p.moveTo(3.5f, 9f); p.lineTo(7.5f, 9f); p.lineTo(12.5f, 4.5f); p.lineTo(12.5f, 19.5f); p.lineTo(7.5f, 15f)
                p.lineTo(3.5f, 15f); p.close(); canvas.drawPath(p, paint)
                if (kind == "volume") {
                    canvas.drawArc(RectF(10.5f, 8.5f, 17.5f, 15.5f), -55f, 110f, false, paint)
                    canvas.drawArc(RectF(10f, 4.5f, 21.5f, 19.5f), -55f, 110f, false, paint)
                } else { canvas.drawLine(16f, 9f, 21f, 15f, paint); canvas.drawLine(21f, 9f, 16f, 15f, paint) }
            }
            "plus" -> { canvas.drawLine(12f, 5f, 12f, 19f, paint); canvas.drawLine(5f, 12f, 19f, 12f, paint) }
            "minus" -> canvas.drawLine(5f, 12f, 19f, 12f, paint)
            "close" -> { canvas.drawLine(6f, 6f, 18f, 18f, paint); canvas.drawLine(18f, 6f, 6f, 18f, paint) }
            "info" -> {
                canvas.drawCircle(12f, 12f, 9f, paint); canvas.drawLine(12f, 11f, 12f, 17f, paint)
                fill { canvas.drawCircle(12f, 7.4f, 1.3f, paint) }
            }
            "star", "starred" -> {
                for (i in 0 until 10) {
                    val r = if (i % 2 == 0) 9.5f else 4.1f
                    val a = Math.toRadians(-90.0 + i * 36.0)
                    val x = 12f + r * cos(a).toFloat(); val y = 12.6f + r * sin(a).toFloat()
                    if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
                }
                p.close()
                if (kind == "starred") fill { canvas.drawPath(p, paint) } else canvas.drawPath(p, paint)
            }
            "home" -> {
                p.moveTo(3.5f, 11.5f); p.lineTo(12f, 4f); p.lineTo(20.5f, 11.5f); canvas.drawPath(p, paint)
                p.rewind(); p.moveTo(6f, 10f); p.lineTo(6f, 20f); p.lineTo(18f, 20f); p.lineTo(18f, 10f); canvas.drawPath(p, paint)
                canvas.drawLine(10.5f, 20f, 10.5f, 15f, paint); canvas.drawLine(10.5f, 15f, 13.5f, 15f, paint); canvas.drawLine(13.5f, 15f, 13.5f, 20f, paint)
            }
            "work" -> {
                canvas.drawRoundRect(3.5f, 8f, 20.5f, 20f, 2.5f, 2.5f, paint)
                p.moveTo(9f, 8f); p.lineTo(9f, 5f); p.lineTo(15f, 5f); p.lineTo(15f, 8f); canvas.drawPath(p, paint)
                canvas.drawLine(3.5f, 13f, 20.5f, 13f, paint)
            }
            "recent" -> {
                canvas.drawCircle(12f, 12f, 8.5f, paint)
                p.moveTo(12f, 7f); p.lineTo(12f, 12f); p.lineTo(15.5f, 14f); canvas.drawPath(p, paint)
            }
            "pin" -> {
                p.moveTo(12f, 21.5f); p.cubicTo(7f, 15.5f, 5f, 12.5f, 5f, 9.5f); p.cubicTo(5f, 5.5f, 8f, 2.5f, 12f, 2.5f)
                p.cubicTo(16f, 2.5f, 19f, 5.5f, 19f, 9.5f); p.cubicTo(19f, 12.5f, 17f, 15.5f, 12f, 21.5f); p.close()
                canvas.drawPath(p, paint); canvas.drawCircle(12f, 9.5f, 2.6f, paint)
            }
            "search" -> { canvas.drawCircle(10.5f, 10.5f, 6.5f, paint); canvas.drawLine(15.5f, 15.5f, 21f, 21f, paint) }
        }
    }
}

/**
 * Turn arrow for a GraphHopper instruction sign: continue, slight / normal / sharp turns, keep left / right,
 * U-turns, roundabouts and arrival. Right turns are left turns mirrored.
 */
internal class TurnArrow(sign: Int, color: Int) : GridIcon(color, 2.8f) {
    var sign: Int = sign
        set(value) { field = value; invalidateSelf() }

    override fun drawGrid(canvas: Canvas) {
        val s = sign
        val mirror = s > 0 && s != FINISH && s != VIA && s != ROUNDABOUT
        if (mirror) { canvas.save(); canvas.scale(-1f, 1f, 12f, 12f) }
        val k = if (mirror) -s else s
        when (k) {
            FINISH, VIA -> {
                path.moveTo(12f, 22f); path.cubicTo(7f, 16f, 5f, 13f, 5f, 9.5f); path.cubicTo(5f, 5.5f, 8f, 2.5f, 12f, 2.5f)
                path.cubicTo(16f, 2.5f, 19f, 5.5f, 19f, 9.5f); path.cubicTo(19f, 13f, 17f, 16f, 12f, 22f); path.close()
                canvas.drawPath(path, paint); fill { canvas.drawCircle(12f, 9.5f, 2.6f, paint) }
            }
            ROUNDABOUT, LEAVE_ROUNDABOUT -> {
                canvas.drawCircle(12f, 10.5f, 4.5f, paint)
                canvas.drawLine(12f, 22f, 12f, 15f, paint)
                line(canvas, floatArrayOf(15.2f, 7.3f, 20f, 3f))
            }
            -1, -7 -> line(canvas, floatArrayOf(15f, 22f, 15f, 14f, 7.5f, 5.5f))           // slight left / keep left
            -2 -> line(canvas, floatArrayOf(16f, 22f, 16f, 13f, 13.5f, 10f, 4.5f, 10f))     // left
            -3 -> line(canvas, floatArrayOf(15f, 22f, 15f, 6f, 6f, 15f))                    // sharp left
            -8, U_TURN_UNKNOWN, 8 -> line(canvas, floatArrayOf(16f, 22f, 16f, 9f, 15f, 5.5f, 12f, 4f, 9f, 5.5f, 8f, 9f, 8f, 15f))
            else -> line(canvas, floatArrayOf(12f, 22f, 12f, 3.5f))                         // continue and anything unknown
        }
        if (mirror) canvas.restore()
    }

    /** Polyline through [xy] with a filled arrowhead at the end. */
    private fun line(canvas: Canvas, xy: FloatArray) {
        val n = xy.size / 2
        val tx = xy[xy.size - 2]; val ty = xy[xy.size - 1]
        val angle = atan2(ty - xy[xy.size - 3], tx - xy[xy.size - 4])
        // Stop the shaft short of the tip so the round cap does not poke through the arrowhead.
        path.moveTo(xy[0], xy[1])
        for (i in 1 until n - 1) path.lineTo(xy[i * 2], xy[i * 2 + 1])
        path.lineTo(tx - 3.5f * cos(angle), ty - 3.5f * sin(angle))
        canvas.drawPath(path, paint)
        val head = Path()
        val len = 7f; val spread = 0.62f
        head.moveTo(tx + 1.2f * cos(angle), ty + 1.2f * sin(angle))
        head.lineTo(tx - len * cos(angle - spread), ty - len * sin(angle - spread))
        head.lineTo(tx - len * cos(angle + spread), ty - len * sin(angle + spread))
        head.close()
        paint.style = Paint.Style.FILL_AND_STROKE; paint.strokeWidth = 1.2f
        canvas.drawPath(head, paint)
        paint.style = Paint.Style.STROKE; paint.strokeWidth = 2.8f
    }

    companion object {
        const val U_TURN_UNKNOWN = -98
        const val LEAVE_ROUNDABOUT = -6
        const val FINISH = 4
        const val VIA = 5
        const val ROUNDABOUT = 6
    }
}
