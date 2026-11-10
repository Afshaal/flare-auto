package app.flare.auto

import android.app.Activity
import android.os.Bundle
import android.graphics.Color
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView

class WeatherPreviewActivity : Activity() {
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(Color.rgb(12, 16, 19)) }
        val names = arrayOf("Sunny", "Clear night", "Partly cloudy", "Partly cloudy night", "Cloudy", "Rain", "Snow", "Thunderstorm", "Sunrise", "Sunset")
        val codes = arrayOf<Int?>(0, 0, 2, 2, 3, 63, 71, 95, null, null)
        for (rowIndex in 0..1) {
            val row = LinearLayout(this).apply { gravity = Gravity.CENTER }
            root.addView(row, LinearLayout.LayoutParams(-1, 0, 1f))
            for (col in 0..4) {
                val i = rowIndex * 5 + col
                val cell = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER }
                row.addView(cell, LinearLayout.LayoutParams(0, -1, 1f))
                val icon = SolarIcon(this, Color.WHITE).apply { setDaytime(i !in listOf(1, 3, 8)); setWeather(codes[i]) }
                cell.addView(icon, LinearLayout.LayoutParams(100, 100))
                cell.addView(TextView(this).apply { text = names[i]; setTextColor(Color.WHITE); gravity = Gravity.CENTER; textSize = 14f })
            }
        }
        setContentView(root)
    }
}
