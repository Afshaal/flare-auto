package app.flare.auto

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.view.View
import kotlin.math.min

internal class CivicBadge(context: Context, rs: Boolean, private val badgeColor: Int = ThemeTokens.get(context).accent) : View(context) {
    private val paint = Paint(3)
    private val theme = ThemeTokens.get(context)
    private val letters = Path()

    init {
        contentDescription = "RS"
    }

    init {
        letters.rewind()
        letters.moveTo(6f, 4f)
        letters.lineTo(19f, 4f)
        letters.cubicTo(24f, 4f, 25f, 7f, 23f, 11f)
        letters.cubicTo(22f, 14f, 20f, 15f, 17f, 16f)
        letters.lineTo(21f, 24f)
        letters.lineTo(15f, 24f)
        letters.lineTo(12f, 17f)
        letters.lineTo(9f, 17f)
        letters.lineTo(7f, 24f)
        letters.lineTo(2f, 24f)
        letters.close()
        letters.moveTo(11f, 8f)
        letters.lineTo(10f, 13f)
        letters.lineTo(16f, 13f)
        letters.cubicTo(19f, 13f, 20f, 11f, 20f, 9f)
        letters.lineTo(19f, 8f)
        letters.close()
        letters.moveTo(30f, 4f)
        letters.lineTo(44f, 4f)
        letters.lineTo(43f, 8f)
        letters.lineTo(32f, 8f)
        letters.cubicTo(29f, 8f, 29f, 11f, 32f, 11f)
        letters.lineTo(36f, 11f)
        letters.cubicTo(45f, 11f, 42f, 24f, 33f, 24f)
        letters.lineTo(23f, 24f)
        letters.lineTo(24f, 20f)
        letters.lineTo(34f, 20f)
        letters.cubicTo(37f, 20f, 38f, 16f, 34f, 16f)
        letters.lineTo(30f, 16f)
        letters.cubicTo(23f, 16f, 24f, 4f, 30f, 4f)
        letters.close()
        letters.fillType = Path.FillType.EVEN_ODD
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.save()
        val scale = min(width / 46f, height / 28f)
        canvas.translate((width - 46 * scale) / 2, (height - 28 * scale) / 2)
        canvas.scale(scale, scale)
        paint.style = Paint.Style.FILL
        paint.color = theme.background
        canvas.drawPath(letters, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeJoin = Paint.Join.ROUND
        paint.strokeWidth = 1.15f
        paint.color = badgeColor
        canvas.drawPath(letters, paint)
        canvas.restore()
    }
}
