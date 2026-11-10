// SPDX-License-Identifier: GPL-3.0-only
// Playback-control geometry based on Dashline.
// Copyright 2026 Metehan Kaygisiz. Modified for Flare Auto, 2026-11-10.
// https://github.com/metehankaygsz/dashline

package app.flare.auto

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.Drawable
import kotlin.math.min

internal class MediaGlyph(private val kind: String, private val color: Int) : Drawable() {
    private val paint = Paint(3)
    private val path = Path()

    override fun draw(canvas: Canvas) {
        canvas.save()
        val bounds: Rect = bounds
        val size = min(bounds.width(), bounds.height()).toFloat()
        canvas.translate(bounds.left + (bounds.width() - size) / 2, bounds.top + (bounds.height() - size) / 2)
        canvas.scale(size / 24f, size / 24f)
        paint.color = color
        path.rewind()
        when (kind) {
            "pause" -> {
                canvas.drawRect(6f, 4f, 10f, 20f, paint)
                canvas.drawRect(14f, 4f, 18f, 20f, paint)
            }
            "note" -> {
                path.moveTo(12f, 3f)
                path.lineTo(12f, 14f)
                path.cubicTo(6f, 11f, 4f, 20f, 10f, 21f)
                path.cubicTo(13f, 22f, 14f, 19f, 14f, 17f)
                path.lineTo(14f, 7f)
                path.lineTo(18f, 7f)
                path.lineTo(18f, 3f)
                path.close()
                canvas.drawPath(path, paint)
            }
            else -> {
                if (kind == "previous") {
                    canvas.translate(24f, 0f)
                    canvas.scale(-1f, 1f)
                }
                path.moveTo(6f, 4f)
                path.lineTo(18f, 12f)
                path.lineTo(6f, 20f)
                path.close()
                canvas.drawPath(path, paint)
                if (kind != "play") canvas.drawRect(18f, 4f, 21f, 20f, paint)
            }
        }
        canvas.restore()
    }

    override fun setAlpha(alpha: Int) = Unit
    override fun setColorFilter(colorFilter: ColorFilter?) = Unit
    override fun getOpacity() = PixelFormat.TRANSLUCENT
}
