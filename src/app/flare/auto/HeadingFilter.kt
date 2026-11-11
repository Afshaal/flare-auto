package app.flare.auto

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sqrt

/**
 * Direction of travel for a heading-up map, from GPS fixes.
 *
 * * Moving at [TRUSTED_SPEED] or faster with a GPS bearing: that bearing.
 * * Otherwise, once the car has moved [MIN_TRACK] metres (or the fix accuracy, if larger) from the last anchor:
 *   the bearing of that movement. This covers receivers that report no bearing.
 * * Stopped, crawling or jittering in place: the last heading is held (null until the first one is known).
 *
 * New headings are eased in ([SMOOTHING] of the shortest turn per fix) so the map does not snap; turns
 * smaller than [DEAD_BAND] degrees are ignored to keep a parked map still.
 */
internal class HeadingFilter {
    /** Smoothed heading in degrees clockwise from north, or null when the direction is not known yet. */
    var heading: Double? = null
        private set
    private var anchorLat = Double.NaN
    private var anchorLon = Double.NaN

    fun update(lat: Double, lon: Double, bearing: Float?, speed: Float?, accuracy: Float?): Double? {
        if (!lat.isFinite() || !lon.isFinite()) return heading
        if (anchorLat.isNaN()) { anchorLat = lat; anchorLon = lon }
        var raw: Double? = null
        if (bearing != null && speed != null && speed >= TRUSTED_SPEED && bearing.isFinite()) {
            raw = bearing.toDouble()
            anchorLat = lat; anchorLon = lon
        } else {
            val moved = metres(anchorLat, anchorLon, lat, lon)
            val needed = maxOf(MIN_TRACK, (accuracy ?: 0f).toDouble())
            val crawling = speed != null && speed < CRAWL_SPEED
            if (moved >= needed && !crawling) {
                raw = bearingBetween(anchorLat, anchorLon, lat, lon)
                anchorLat = lat; anchorLon = lon
            }
        }
        if (raw != null) {
            val current = heading
            heading = if (current == null) normalize(raw) else {
                val turn = shortest(raw - current)
                if (abs(turn) < DEAD_BAND) current else normalize(current + turn * SMOOTHING)
            }
        }
        return heading
    }

    fun reset() { heading = null; anchorLat = Double.NaN; anchorLon = Double.NaN }

    companion object {
        /** m/s (~9 km/h): GPS bearings below this are mostly noise. */
        const val TRUSTED_SPEED = 2.5f
        /** m/s: below this, track-derived bearings are ignored too. */
        const val CRAWL_SPEED = 1.0f
        const val MIN_TRACK = 12.0
        const val SMOOTHING = 0.55
        const val DEAD_BAND = 3.0

        fun normalize(deg: Double): Double = ((deg % 360) + 360) % 360

        /** Signed turn in (-180, 180]. */
        fun shortest(deg: Double): Double { val d = normalize(deg); return if (d > 180) d - 360 else d }

        fun metres(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
            val dy = (lat2 - lat1) * 111_320.0
            val dx = (lon2 - lon1) * 111_320.0 * cos(Math.toRadians((lat1 + lat2) / 2))
            return sqrt(dx * dx + dy * dy)
        }

        fun bearingBetween(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
            val dy = lat2 - lat1
            val dx = (lon2 - lon1) * cos(Math.toRadians((lat1 + lat2) / 2))
            return normalize(Math.toDegrees(atan2(dx, dy)))
        }
    }
}
