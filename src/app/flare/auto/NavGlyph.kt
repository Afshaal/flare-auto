package app.flare.auto

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.view.View
import kotlin.math.cos
import kotlin.math.sin

/**
 * Line-art glyphs (24-unit grid) for the dock and drawer. Each glyph is optically centred once per kind
 * (measured from its rendered pixels) and the offset is shared by every view of that kind.
 * Kinds: audio, radio, bluetooth, screen, apps, file, browser, carsettings, systemsettings, wifi, custom,
 * camera, gallery, keyboard, ipod, playpause, sim, terminal, guide; anything else draws the settings gear.
 */
internal class NavGlyph(context: Context, private val kind: String, color: Int) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
        this.color = color
        style = Paint.Style.STROKE
        strokeWidth = 1.7f
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val glyphPath = Path()
    private val shiftX: Float
    private val shiftY: Float

    init {
        val offset = synchronized(centers) { centers.getOrPut(kind) { measureCenter() } }
        shiftX = offset[0]
        shiftY = offset[1]
    }

    private fun measureCenter(): FloatArray {
        val bitmap = Bitmap.createBitmap(256, 256, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.translate(32f, 32f)
        canvas.scale(8f, 8f)
        drawGlyph(canvas)
        val pixels = IntArray(256 * 256)
        bitmap.getPixels(pixels, 0, 256, 0, 0, 256, 256)
        bitmap.recycle()
        var minX = 256
        var minY = 256
        var maxX = -1
        var maxY = -1
        for (y in 0 until 256) for (x in 0 until 256) {
            if (pixels[y * 256 + x] ushr 24 != 0) {
                minX = minOf(minX, x); maxX = maxOf(maxX, x)
                minY = minOf(minY, y); maxY = maxOf(maxY, y)
            }
        }
        return if (maxX < 0) floatArrayOf(0f, 0f) else floatArrayOf(
            12 - ((minX + maxX + 1) / 2f - 32) / 8,
            12 - ((minY + maxY + 1) / 2f - 32) / 8
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.save()
        canvas.scale(width / 24f, height / 24f)
        canvas.translate(shiftX, shiftY)
        drawGlyph(canvas)
        canvas.restore()
    }

    private fun gear(canvas: Canvas) {
        canvas.drawCircle(12f, 12f, 7f, paint)
        canvas.drawCircle(12f, 12f, 2.5f, paint)
        for (index in 0 until 8) {
            val c = COS[index]
            val s = SIN[index]
            canvas.drawLine(12 + c * 8, 12 + s * 8, 12 + c * 10, 12 + s * 10, paint)
        }
    }

    private fun drawGlyph(canvas: Canvas) {
        val path = glyphPath
        path.rewind()
        when (kind) {
            "audio" -> {
                path.moveTo(8f, 17f); path.lineTo(8f, 5f); path.lineTo(19f, 3f); path.lineTo(19f, 15f)
                canvas.drawPath(path, paint); canvas.drawOval(3f, 15f, 8f, 20f, paint); canvas.drawOval(14f, 13f, 19f, 18f, paint)
            }
            "file" -> {
                path.moveTo(2f, 6f); path.lineTo(9f, 6f); path.lineTo(11f, 9f); path.lineTo(22f, 9f)
                path.lineTo(22f, 20f); path.lineTo(2f, 20f); path.close(); canvas.drawPath(path, paint)
            }
            "radio" -> {
                canvas.drawRoundRect(3f, 8f, 21f, 20f, 3f, 3f, paint)
                canvas.drawLine(6f, 7f, 16f, 3f, paint); canvas.drawLine(6f, 12f, 12f, 12f, paint)
                canvas.drawLine(6f, 15f, 12f, 15f, paint); canvas.drawCircle(17f, 14f, 2f, paint)
            }
            "bluetooth" -> {
                path.moveTo(12f, 2f); path.lineTo(12f, 22f); path.lineTo(18f, 17f); path.lineTo(6f, 7f)
                path.moveTo(6f, 17f); path.lineTo(18f, 7f); path.lineTo(12f, 2f); canvas.drawPath(path, paint)
            }
            "screen" -> {
                canvas.drawRoundRect(3f, 4f, 21f, 19f, 3f, 3f, paint)
                canvas.drawLine(9f, 22f, 15f, 22f, paint); canvas.drawLine(12f, 19f, 12f, 22f, paint)
            }
            "wifi" -> {
                canvas.drawArc(-1f, 5f, 25f, 31f, 225f, 90f, false, paint)
                canvas.drawArc(3f, 9f, 21f, 27f, 225f, 90f, false, paint)
                canvas.drawArc(7f, 13f, 17f, 23f, 225f, 90f, false, paint)
                paint.style = Paint.Style.FILL; canvas.drawCircle(12f, 20f, 1.35f, paint); paint.style = Paint.Style.STROKE
            }
            "custom" -> {
                canvas.drawLine(4f, 6f, 20f, 6f, paint); canvas.drawLine(4f, 12f, 20f, 12f, paint)
                canvas.drawLine(4f, 18f, 20f, 18f, paint); canvas.drawCircle(8f, 6f, 2f, paint)
                canvas.drawCircle(16f, 12f, 2f, paint); canvas.drawCircle(10f, 18f, 2f, paint)
            }
            "gallery" -> {
                canvas.drawRoundRect(2f, 4f, 22f, 20f, 3f, 3f, paint); canvas.drawCircle(8f, 9f, 2f, paint)
                path.moveTo(4f, 18f); path.lineTo(10f, 12f); path.lineTo(14f, 16f); path.lineTo(17f, 13f)
                path.lineTo(21f, 18f); canvas.drawPath(path, paint)
            }
            "camera" -> {
                canvas.drawRoundRect(2f, 6f, 22f, 20f, 3f, 3f, paint); canvas.drawCircle(12f, 13f, 4f, paint)
                canvas.drawLine(8f, 3f, 16f, 3f, paint)
            }
            "keyboard" -> {
                canvas.drawRoundRect(2f, 5f, 22f, 19f, 3f, 3f, paint)
                for (row in 0 until 2) for (column in 0 until 4) canvas.drawPoint(6f + column * 4, 9f + row * 3, paint)
                canvas.drawLine(8f, 16f, 16f, 16f, paint)
            }
            "browser" -> {
                canvas.drawCircle(12f, 12f, 9f, paint); canvas.drawOval(8f, 3f, 16f, 21f, paint)
                canvas.drawLine(3f, 12f, 21f, 12f, paint); canvas.drawArc(4f, 5f, 20f, 14f, 0f, 180f, false, paint)
            }
            "carsettings" -> {
                canvas.drawRoundRect(3f, 10f, 21f, 17f, 2f, 2f, paint); canvas.drawLine(6f, 10f, 8f, 6f, paint)
                canvas.drawLine(8f, 6f, 16f, 6f, paint); canvas.drawLine(16f, 6f, 18f, 10f, paint)
                canvas.drawCircle(7f, 17f, 1.5f, paint); canvas.drawCircle(17f, 17f, 1.5f, paint)
                canvas.drawLine(10f, 13f, 14f, 13f, paint)
            }
            "ipod" -> {
                canvas.drawRoundRect(5f, 2f, 19f, 22f, 3f, 3f, paint); canvas.drawRect(8f, 5f, 16f, 11f, paint)
                canvas.drawCircle(12f, 16f, 3f, paint)
            }
            "playpause" -> {
                path.moveTo(3f, 5f); path.lineTo(11f, 12f); path.lineTo(3f, 19f); path.close()
                canvas.drawPath(path, paint); canvas.drawLine(16f, 5f, 16f, 19f, paint); canvas.drawLine(21f, 5f, 21f, 19f, paint)
            }
            "sim" -> {
                path.moveTo(8f, 2f); path.lineTo(20f, 2f); path.lineTo(20f, 22f); path.lineTo(4f, 22f)
                path.lineTo(4f, 6f); path.close(); canvas.drawPath(path, paint); canvas.drawRect(8f, 10f, 16f, 18f, paint)
            }
            "terminal" -> {
                canvas.drawRoundRect(2f, 4f, 22f, 20f, 3f, 3f, paint); path.moveTo(6f, 9f); path.lineTo(10f, 12f)
                path.lineTo(6f, 15f); canvas.drawPath(path, paint); canvas.drawLine(13f, 16f, 18f, 16f, paint)
            }
            "guide" -> {
                canvas.drawRoundRect(4f, 2f, 20f, 22f, 2f, 2f, paint); canvas.drawLine(8f, 2f, 8f, 22f, paint)
                canvas.drawLine(11f, 8f, 17f, 8f, paint); canvas.drawLine(11f, 12f, 17f, 12f, paint)
            }
            "apps" -> for (y in 4 until 19 step 7) for (x in 4 until 19 step 7) {
                canvas.drawRoundRect(x.toFloat(), y.toFloat(), x + 4f, y + 4f, 1f, 1f, paint)
            }
            else -> gear(canvas) // "systemsettings" and any unknown kind
        }
    }

    companion object {
        private val centers = HashMap<String, FloatArray>()
        private val COS = FloatArray(8) { cos(it * Math.PI / 4).toFloat() }
        private val SIN = FloatArray(8) { sin(it * Math.PI / 4).toFloat() }
    }
}
