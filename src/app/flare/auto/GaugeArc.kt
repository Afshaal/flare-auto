package app.flare.auto

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.View

/**
 * Decorative half-ring behind the "Gauge clock": a thin [track] arc over the top of the card with a short [accent]
 * segment at its centre. It shows no vehicle data (no speed, RPM or similar) and does not glow.
 */
internal class GaugeArc(context: Context, track: Int, accent: Int) : View(context) {
    private val density = context.resources.displayMetrics.density
    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 1.5f * density; strokeCap = Paint.Cap.ROUND
        color = (track and 0x00ffffff) or 0x66000000
    }
    private val accentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 2.5f * density; strokeCap = Paint.Cap.ROUND
        color = (accent and 0x00ffffff) or 0xd9000000.toInt()
    }
    private val oval = RectF()

    init { importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat(); val h = height.toFloat()
        if (w <= 0 || h <= 0) return
        // A circle centred low in the card, so its upper half frames the time and its ends fall at the sides.
        val radius = minOf(w * .42f, h * .88f)
        val cx = w / 2f; val cy = h * .92f
        val inset = accentPaint.strokeWidth
        oval.set(cx - radius, cy - radius + inset, cx + radius, cy + radius + inset)
        canvas.drawArc(oval, 190f, 160f, false, trackPaint)
        canvas.drawArc(oval, 252f, 36f, false, accentPaint)
    }
}
