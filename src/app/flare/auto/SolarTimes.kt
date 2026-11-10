package app.flare.auto

import java.util.Calendar
import java.util.TimeZone
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.tan

internal object SolarTimes {
    @JvmStatic
    fun event(day: Calendar, lat: Double, lon: Double, zone: TimeZone, sunrise: Boolean): Int {
        val n = day.get(Calendar.DAY_OF_YEAR)
        val lng = lon / 15.0
        val t = n + ((if (sunrise) 6 else 18) - lng) / 24.0
        val m = .9856 * t - 3.289
        val l = norm(m + 1.916 * sin(m) + .020 * sin(2 * m) + 282.634)
        var ra = norm(Math.toDegrees(atan(.91764 * tan(Math.toRadians(l)))))
        ra += floor(l / 90) * 90 - floor(ra / 90) * 90
        ra /= 15
        val sinDec = .39782 * sin(l)
        val cosDec = cos(asin(sinDec))
        val cosH = (cos(Math.toRadians(90.833)) - sinDec * sin(lat)) /
            (cosDec * cos(Math.toRadians(lat)))
        if (cosH > 1 || cosH < -1) return -1

        val h = (if (sunrise) 360 - Math.toDegrees(acos(cosH)) else Math.toDegrees(acos(cosH))) / 15
        var utc = (h + ra - .06571 * t - 6.622 - lng) % 24
        if (utc < 0) utc += 24
        val noon = day.clone() as Calendar
        noon.set(Calendar.HOUR_OF_DAY, 12)
        val offset = zone.getOffset(noon.timeInMillis) / 60000
        return ((utc * 60).roundToInt() + offset + 1440) % 1440
    }

    private fun sin(degrees: Double): Double = kotlin.math.sin(Math.toRadians(degrees))

    private fun norm(degrees: Double): Double {
        val normalized = degrees % 360
        return if (normalized < 0) normalized + 360 else normalized
    }
}
