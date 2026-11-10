package app.flare.auto

import android.graphics.Typeface

internal object ClockStyles {
    val ids = arrayOf("classic", "modern", "condensed", "bold")
    val labels = arrayOf("Classic", "Modern", "Condensed", "Bold")
    fun typeface(id: String?, saver: Boolean): Typeface = Typeface.create(when (id) {
        "modern" -> "sans-serif"
        "condensed" -> "sans-serif-condensed-light"
        "bold" -> "sans-serif-medium"
        else -> if (saver) "sans-serif-condensed-light" else "sans-serif-light"
    }, Typeface.NORMAL)
}
