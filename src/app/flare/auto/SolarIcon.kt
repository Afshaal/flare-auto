package app.flare.auto

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.view.View
import kotlin.math.min

internal class SolarIcon(context: Context, private val color: Int) : View(context) {
    private val paint = Paint(3).apply {
        this.color = this@SolarIcon.color
        style = Paint.Style.STROKE
        strokeWidth = 1.3f
        strokeCap = Paint.Cap.ROUND
    }
    private val cloud = Path().apply {
        moveTo(6.5f, 18.5f)
        cubicTo(3.6f, 18.5f, 2.2f, 16.7f, 2.2f, 14.4f)
        cubicTo(2.2f, 12f, 4f, 10.2f, 6.4f, 10.2f)
        cubicTo(7.2f, 7.1f, 9.5f, 5.5f, 12.2f, 5.5f)
        cubicTo(15.3f, 5.5f, 17.7f, 7.8f, 17.9f, 10.5f)
        cubicTo(20.2f, 10.8f, 21.8f, 12.4f, 21.8f, 14.5f)
        cubicTo(21.8f, 16.8f, 20.1f, 18.5f, 17.5f, 18.5f)
        lineTo(6.5f, 18.5f)
    }
    private val moon = Path().apply {
        moveTo(17.7f, 3.1f)
        cubicTo(14.6f, 4.1f, 12.7f, 6.9f, 12.7f, 10f)
        cubicTo(12.7f, 14.4f, 16.1f, 17.8f, 20.5f, 17.8f)
        cubicTo(18.8f, 20.1f, 16f, 21.4f, 13f, 21.4f)
        cubicTo(7.6f, 21.4f, 3.3f, 17.1f, 3.3f, 11.7f)
        cubicTo(3.3f, 6.5f, 7.6f, 2.3f, 12.9f, 2.3f)
        cubicTo(14.6f, 2.3f, 16.2f, 2.6f, 17.7f, 3.1f)
        close()
    }
    private var daytime = false
    private var weatherCode: Int? = null

    fun setWeather(code: Int?) { if (weatherCode != code) { weatherCode = code; invalidate() } }

    fun setDaytime(value: Boolean) {
        if (daytime != value) {
            daytime = value
            invalidate()
        }
    }

    private fun drawCloud(canvas: Canvas, scale: Float = 1f, x: Float = 0f, y: Float = 0f) {
        canvas.save()
        canvas.translate(x, y)
        canvas.scale(scale, scale, 12f, 12f)
        paint.color = 0xffd7e4ea.toInt()
        canvas.drawPath(cloud, paint)
        canvas.restore()
    }

    private fun drawSun(canvas: Canvas, x: Float, y: Float, radius: Float = 4f) {
        paint.color = 0xffffc65a.toInt()
        canvas.drawCircle(x, y, radius, paint)
        for (i in 0..7) {
            val angle = i * Math.PI / 4
            val cos = kotlin.math.cos(angle).toFloat()
            val sin = kotlin.math.sin(angle).toFloat()
            canvas.drawLine(x + cos * (radius + 1.8f), y + sin * (radius + 1.8f),
                x + cos * (radius + 3.4f), y + sin * (radius + 3.4f), paint)
        }
    }

    private fun drawMoon(canvas: Canvas, x: Float = 0f, y: Float = 0f, scale: Float = 1f) {
        canvas.save()
        canvas.translate(x, y)
        canvas.scale(scale, scale)
        paint.color = 0xffb9d5e5.toInt()
        canvas.drawPath(moon, paint)
        canvas.restore()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.save()
        val scale = min(width / 24f, height / 24f)
        canvas.translate((width - 24 * scale) / 2, (height - 24 * scale) / 2)
        canvas.scale(scale, scale)
        paint.color = color
        val weather = weatherCode
        if (weather != null) {
            when {
                weather in 95..99 -> {
                    drawCloud(canvas, .86f, -1f, -2f)
                    paint.color = 0xffffc65a.toInt()
                    canvas.drawLine(13f, 16f, 10f, 20f, paint)
                    canvas.drawLine(10f, 20f, 13f, 20f, paint)
                    canvas.drawLine(13f, 20f, 11f, 23f, paint)
                }
                weather in 51..67 || weather in 80..82 -> {
                    drawCloud(canvas, .86f, -1f, -2f)
                    paint.color = 0xff91d1e6.toInt()
                    canvas.drawLine(7f, 18f, 6f, 21f, paint)
                    canvas.drawLine(12f, 18f, 11f, 21f, paint)
                    canvas.drawLine(17f, 18f, 16f, 21f, paint)
                }
                weather in 71..77 || weather in 85..86 -> {
                    drawCloud(canvas, .86f, -1f, -2f)
                    paint.color = 0xffa8d7e9.toInt()
                    canvas.drawLine(9f, 18f, 9f, 22f, paint)
                    canvas.drawLine(7f, 20f, 11f, 20f, paint)
                    canvas.drawLine(15f, 18f, 15f, 22f, paint)
                    canvas.drawLine(13f, 20f, 17f, 20f, paint)
                }
                weather == 0 || weather == 1 -> if (daytime) drawSun(canvas, 12f, 12f) else drawMoon(canvas)
                weather == 2 -> {
                    if (daytime) drawSun(canvas, 17f, 7f, 2.7f) else drawMoon(canvas, 9f, 0f, .58f)
                    drawCloud(canvas, .78f, -3f, 2f)
                }
                else -> drawCloud(canvas)
            }
            canvas.restore(); return
        }
        canvas.drawArc(7f, 10f, 17f, 20f, 180f, 180f, false, paint)
        canvas.drawLine(2.5f, 15.5f, 21.5f, 15.5f, paint)
        canvas.drawLine(12f, 2f, 12f, 7f, paint)
        val tip = if (daytime) 7f else 2f
        val tail = 4.5f
        canvas.drawLine(10f, tail, 12f, tip, paint)
        canvas.drawLine(14f, tail, 12f, tip, paint)
        canvas.drawLine(5.5f, 7.5f, 7.2f, 9.2f, paint)
        canvas.drawLine(18.5f, 7.5f, 16.8f, 9.2f, paint)
        canvas.restore()
    }
}
