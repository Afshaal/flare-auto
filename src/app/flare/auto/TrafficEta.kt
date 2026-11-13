package app.flare.auto

import android.content.SharedPreferences
import java.util.WeakHashMap
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot

/**
 * Optional "Typical traffic adjustment" of offline arrival estimates. It is a delay the driver configures
 * (a peak multiplier, time windows and weekdays), applied only where the route runs through two local areas; it is
 * not live or measured traffic, and it never changes which route GraphHopper picks.
 *
 * Every displayed duration comes from here: [remaining] (and [total] = remaining from the start) on top of the
 * route's raw base time, which stays as GraphHopper returned it (NavRoute.millis / stepMillis are never modified).
 *
 * Model, per eligible stretch of the route (time spent inside an area):
 *   scale  = clamp((D km - 5) / 5, 0, 1)                  D = planned length of the journey, fixed when it starts
 *   weight = 0..1 inside a window and on a selected weekday, ramping up over the first and down over the last
 *            [RAMP_MIN] minutes of the window (so estimates do not jump at the boundary); 0 outside
 *   factor = 1 + (peak - 1) * scale * weight
 * Overlapping windows or areas never multiply: the highest factor applies.
 *
 * Areas (reviewable, built from the Lahore pack's OSM data, 2026-11-09 extract):
 *  - [WAPDA_BARKAT]: Wapda Town (31.437, 74.270) to Barkat Market (31.500, 74.321) with the roads between them
 *    (Johar Town, Maulana Shaukat Ali Road, Akbar Chowk, Faisal Town, Model Town Link Road, New Garden Town to
 *    Kalma Chowk), morning and evening windows. Its west edge stays east of longitude 74.262, so the M-2 motorway,
 *    N-5 and the Thokar Niaz Baig flyover (all at or west of 74.262 in OSM) are outside; no Ring Road is nearby.
 *    The Canal Road (trunk) stretch inside the polygon counts as a connecting road.
 *  - [JAIL_ROAD]: the mapped Jail Road ways (OSM ways 10116340, 10116342, 177851776, 178064717-19, 178064724,
 *    315799480/83, 660882070/71/75/80, 660883267, 660888033/34, 1167175850-53, 1318164201/02, 1392176880/81,
 *    1545842558; Qurtaba Chowk to Sherpao Bridge, each simplified to within 5 m), school-dismissal window.
 *    A stretch counts when it lies within [CORRIDOR_M] of these ways and runs along them (within 30 degrees), so
 *    crossing streets do not. "Kacha Jail Road" and "Link Jail Road" are different roads and are not included.
 * Routes elsewhere (other parts of Lahore, other packs) keep the base estimate.
 */
internal object TrafficEta {
    /** A daily window in minutes after midnight, [start] < [end] (no window across midnight). */
    data class Window(val start: Int, val end: Int) {
        val label: String get() = "%02d:%02d–%02d:%02d".format(start / 60, start % 60, end / 60, end % 60)
        val stored: String get() = "%02d:%02d-%02d:%02d".format(start / 60, start % 60, end / 60, end % 60)

        companion object {
            /** "07:30-09:30" (also with an en dash); null when malformed or not increasing. */
            fun parse(text: String?): Window? {
                val m = Regex("""^\s*(\d{1,2}):(\d{2})\s*[-–]\s*(\d{1,2}):(\d{2})\s*$""").find(text ?: return null) ?: return null
                val (h1, m1, h2, m2) = m.destructured.toList().map { it.toInt() }
                if (h1 > 23 || h2 > 24 || m1 > 59 || m2 > 59) return null
                val start = h1 * 60 + m1; val end = (h2 * 60 + m2).coerceAtMost(24 * 60)
                return if (start < end) Window(start, end) else null
            }
        }
    }

    data class Settings(val enabled: Boolean, val peak: Double, val morning: Window, val evening: Window,
                        val schoolEnabled: Boolean, val school: Window, val days: Int) {
        fun onDay(isoDay: Int) = days and (1 shl (isoDay - 1)) != 0
    }

    val MORNING = Window(7 * 60 + 30, 9 * 60 + 30)
    val EVENING = Window(16 * 60 + 30, 19 * 60 + 30)
    val SCHOOL = Window(13 * 60 + 30, 15 * 60 + 30)
    /** Monday..Friday (bit 0 = Monday ... bit 6 = Sunday, ISO day numbers). */
    const val WEEKDAYS = 0b0011111
    const val DEFAULT_PEAK_TENTHS = 20
    const val RAMP_MIN = 15.0
    const val CORRIDOR_M = 40.0
    /** Peak multipliers offered in settings (1.8 and the default 2.0 included). */
    val PEAK_CHOICES = (11..30).map { it / 10.0 }

    fun read(p: SharedPreferences) = Settings(
        p.getBoolean(Keys.OFFLINE_ETA_TRAFFIC, false),
        p.getInt(Keys.OFFLINE_ETA_PEAK, DEFAULT_PEAK_TENTHS).coerceIn(10, 30) / 10.0,
        Window.parse(p.getString(Keys.OFFLINE_ETA_MORNING, null)) ?: MORNING,
        Window.parse(p.getString(Keys.OFFLINE_ETA_EVENING, null)) ?: EVENING,
        p.getBoolean(Keys.OFFLINE_ETA_SCHOOL, true),
        Window.parse(p.getString(Keys.OFFLINE_ETA_SCHOOL_TIME, null)) ?: SCHOOL,
        p.getInt(Keys.OFFLINE_ETA_DAYS, WEEKDAYS) and 0b1111111)

    /** Distance scaling: no adjustment up to 5 km, full from 10 km. */
    fun scale(tripMetres: Double) = ((tripMetres / 1000.0 - 5.0) / 5.0).coerceIn(0.0, 1.0)

    /** 0..1 for [window] at [minute] of the day: ramps over [RAMP_MIN] at both ends, 0 outside. */
    fun weight(window: Window, minute: Double): Double {
        if (minute < window.start || minute >= window.end) return 0.0
        return minOf(1.0, (minute - window.start) / RAMP_MIN, (window.end - minute) / RAMP_MIN)
    }

    /** Schedule part of the adjustment (refreshed about every 30 s while navigation is shown). */
    data class Schedule(val peak: Double, val area: Double, val school: Double) {
        companion object { val NONE = Schedule(1.0, 0.0, 0.0) }
    }

    fun schedule(s: Settings, isoDay: Int, minute: Double): Schedule {
        if (!s.enabled || !s.onDay(isoDay)) return Schedule.NONE
        return Schedule(s.peak, maxOf(weight(s.morning, minute), weight(s.evening, minute)),
            if (s.schoolEnabled) weight(s.school, minute) else 0.0)
    }

    fun scheduleNow(s: Settings, now: java.time.LocalDateTime = java.time.LocalDateTime.now()) =
        schedule(s, now.dayOfWeek.value, now.hour * 60 + now.minute + now.second / 60.0)

    /** Multipliers for a journey of [tripMetres]: (area, school); 1.0 = no adjustment. */
    fun factors(schedule: Schedule, tripMetres: Double): Pair<Double, Double> {
        val k = (schedule.peak - 1.0) * scale(tripMetres)
        return (1.0 + k * schedule.area) to (1.0 + k * schedule.school)
    }

    /**
     * Precomputed per route (cumulative milliseconds at each geometry point): base time, and the base time spent
     * in stretches that are only in the area, only on the school corridor, or both.
     */
    class Profile(val base: DoubleArray, val area: DoubleArray, val school: DoubleArray, val both: DoubleArray)

    private val profiles = WeakHashMap<NavRoute, Profile>()

    /** Builds (or returns) the route's profile; called on the routing worker when a route is created. */
    fun profile(route: NavRoute): Profile = synchronized(profiles) { profiles[route] } ?: build(route).also {
        synchronized(profiles) { profiles[route] = it }
    }

    /** Remaining time from [along] metres into [route], adjusted (base when [schedule] is NONE). */
    fun remaining(route: NavRoute, along: Double, schedule: Schedule, tripMetres: Double): Long {
        val p = profile(route)
        val (fa, fs) = factors(schedule, tripMetres)
        fun rest(a: DoubleArray) = a.last() - at(route, a, along)
        val base = rest(p.base)
        if (fa == 1.0 && fs == 1.0) return base.toLong()
        return (base + rest(p.area) * (fa - 1) + rest(p.school) * (fs - 1) + rest(p.both) * (maxOf(fa, fs) - 1)).toLong()
    }

    fun total(route: NavRoute, schedule: Schedule, tripMetres: Double) = remaining(route, 0.0, schedule, tripMetres)

    /** Unadjusted remaining time (GraphHopper's instruction times, interpolated inside the current instruction). */
    fun baseRemaining(route: NavRoute, along: Double): Long = remaining(route, along, Schedule.NONE, 0.0)

    private fun at(route: NavRoute, values: DoubleArray, along: Double): Double {
        val c = route.cumulative
        if (along <= 0.0) return values[0]
        if (along >= c.last()) return values.last()
        var lo = 0; var hi = c.lastIndex
        while (hi - lo > 1) { val mid = (lo + hi) / 2; if (c[mid] <= along) lo = mid else hi = mid }
        val span = c[hi] - c[lo]
        val f = if (span <= 0) 0.0 else (along - c[lo]) / span
        return values[lo] + (values[hi] - values[lo]) * f
    }

    /**
     * Base time at distance [d] from the start: whole instructions before it plus the part of the current one,
     * spread evenly over that instruction's length (speed differs between instructions, not inside one). Routes
     * without instruction times (older caches) fall back to the whole route's average speed.
     */
    fun baseAt(route: NavRoute, d: Double): Double {
        val ms = route.stepMillis
        val total = route.cumulative.last()
        if (ms == null || ms.size != route.steps.size || route.steps.isEmpty()) return if (total <= 0) 0.0 else route.millis * (d / total)
        var sum = 0.0
        for (i in route.steps.indices) {
            val start = route.steps[i].at
            val end = route.steps.getOrNull(i + 1)?.at ?: total
            if (d >= end) { sum += ms[i]; continue } // passed (a zero-length step's turn time counts once reached)
            if (d > start) sum += ms[i] * (d - start) / (end - start)
            break
        }
        return sum
    }

    private fun build(route: NavRoute): Profile {
        val pts = route.points; val c = route.cumulative; val n = c.size
        val base = DoubleArray(n) { baseAt(route, c[it]) }
        val area = DoubleArray(n); val school = DoubleArray(n); val both = DoubleArray(n)
        for (i in 1 until n) {
            val la = pts[(i - 1) * 2]; val lo = pts[(i - 1) * 2 + 1]; val lb = pts[i * 2]; val ob = pts[i * 2 + 1]
            val inArea = inside(WAPDA_BARKAT, (la + lb) / 2, (lo + ob) / 2)
            val onRoad = c[i] > c[i - 1] && alongCorridor(la, lo, lb, ob)
            val t = base[i] - base[i - 1]
            area[i] = area[i - 1] + if (inArea && !onRoad) t else 0.0
            school[i] = school[i - 1] + if (onRoad && !inArea) t else 0.0
            both[i] = both[i - 1] + if (inArea && onRoad) t else 0.0
        }
        return Profile(base, area, school, both)
    }

    fun inside(polygon: DoubleArray, lat: Double, lon: Double): Boolean {
        var odd = false
        var j = polygon.size / 2 - 1
        for (i in 0 until polygon.size / 2) {
            val ai = polygon[i * 2]; val oi = polygon[i * 2 + 1]; val aj = polygon[j * 2]; val oj = polygon[j * 2 + 1]
            if ((ai > lat) != (aj > lat) && lon < (oj - oi) * (lat - ai) / (aj - ai) + oi) odd = !odd
            j = i
        }
        return odd
    }

    /** Route stretch [a]-[b] lies on Jail Road: its midpoint within [CORRIDOR_M] of a mapped way, parallel to it. */
    fun alongCorridor(la: Double, lo: Double, lb: Double, ob: Double): Boolean {
        val lat = (la + lb) / 2; val lon = (lo + ob) / 2
        if (lat < 31.515 || lat > 31.555 || lon < 74.31 || lon > 74.375) return false
        val kx = 111_320.0 * cos(Math.toRadians(lat)); val ky = 111_320.0
        val heading = atan2((ob - lo) * kx, (lb - la) * ky)
        for (way in JAIL_ROAD) for (i in 0 until way.size / 2 - 1) {
            val ax = (way[i * 2 + 1] - lon) * kx; val ay = (way[i * 2] - lat) * ky
            val bx = (way[i * 2 + 3] - lon) * kx; val by = (way[i * 2 + 2] - lat) * ky
            val dx = bx - ax; val dy = by - ay; val len = dx * dx + dy * dy
            val t = if (len == 0.0) 0.0 else (-(ax * dx + ay * dy) / len).coerceIn(0.0, 1.0)
            if (hypot(ax + t * dx, ay + t * dy) > CORRIDOR_M) continue
            var diff = abs(Math.toDegrees(heading - atan2(dx, dy))) % 180.0
            if (diff > 90) diff = 180 - diff
            if (diff <= 30) return true
        }
        return false
    }

    /** Wapda Town – Barkat Market band (lat, lon pairs); see the class comment for its extent and exclusions. */
    val WAPDA_BARKAT = doubleArrayOf(
        31.4280, 74.2620,  // south-west of Wapda Town (east of M-2 / N-5 / Thokar, which lie at or west of 74.262)
        31.4280, 74.2900,  // south-east of Wapda Town
        31.4550, 74.3180,  // south of Akbar Chowk / Model Town Link Road
        31.4900, 74.3380,  // east of New Garden Town
        31.5090, 74.3380,  // north-east, past Kalma Chowk
        31.5090, 74.3120,  // north-west of Barkat Market
        31.4800, 74.2800,  // west of Faisal Town
        31.4550, 74.2620)  // west of Johar Town

    /** Mapped Jail Road ways (lat, lon pairs), see the class comment for the OSM way ids. */
    val JAIL_ROAD: List<DoubleArray> = listOf(
            doubleArrayOf(31.54817, 74.31553, 31.54698, 74.31688, 31.54651, 74.31754, 31.54521, 74.31980, 31.54371, 74.32126, 31.54361, 74.32158, 31.54324, 74.32419, 31.54278, 74.32947, 31.54240, 74.33198, 31.54207, 74.33265, 31.54008, 74.33450, 31.53927, 74.33557),
            doubleArrayOf(31.52609, 74.36288, 31.52796, 74.36168, 31.52833, 74.36134),
            doubleArrayOf(31.52946, 74.35935, 31.53057, 74.35660),
            doubleArrayOf(31.52228, 74.36858, 31.52238, 74.36644, 31.52262, 74.36590, 31.52415, 74.36430, 31.52609, 74.36288),
            doubleArrayOf(31.52612, 74.36297, 31.52412, 74.36448, 31.52272, 74.36600, 31.52259, 74.36623, 31.52247, 74.36683, 31.52238, 74.36858),
            doubleArrayOf(31.52838, 74.36142, 31.52800, 74.36175, 31.52612, 74.36297),
            doubleArrayOf(31.53059, 74.35675, 31.52956, 74.35935, 31.52872, 74.36094, 31.52838, 74.36142),
            doubleArrayOf(31.53517, 74.34169, 31.53482, 74.34230, 31.53468, 74.34274, 31.53466, 74.34335, 31.53487, 74.34530, 31.53476, 74.34619, 31.53445, 74.34721),
            doubleArrayOf(31.53593, 74.34024, 31.53727, 74.33830, 31.53864, 74.33600, 31.53920, 74.33532),
            doubleArrayOf(31.53112, 74.35540, 31.53059, 74.35675),
            doubleArrayOf(31.53185, 74.35219, 31.53208, 74.35246, 31.53215, 74.35291, 31.53140, 74.35474),
            doubleArrayOf(31.53427, 74.34701, 31.53465, 74.34593, 31.53472, 74.34548, 31.53472, 74.34479, 31.53451, 74.34326, 31.53463, 74.34233, 31.53498, 74.34155, 31.53593, 74.34024),
            doubleArrayOf(31.53057, 74.35660, 31.53208, 74.35283),
            doubleArrayOf(31.53208, 74.35283, 31.53232, 74.35228),
            doubleArrayOf(31.53820, 74.33715, 31.53517, 74.34169),
            doubleArrayOf(31.53920, 74.33532, 31.54027, 74.33411, 31.54197, 74.33254, 31.54223, 74.33215, 31.54237, 74.33161, 31.54255, 74.32898, 31.54290, 74.32621, 31.54333, 74.32171, 31.54369, 74.32103, 31.54456, 74.32029, 31.54512, 74.31970, 31.54642, 74.31744, 31.54739, 74.31611, 31.54742, 74.31593, 31.54724, 74.31581),
            doubleArrayOf(31.53420, 74.34722, 31.53427, 74.34701),
            doubleArrayOf(31.53337, 74.34961, 31.53420, 74.34722),
            doubleArrayOf(31.53294, 74.35109, 31.53236, 74.35240, 31.53224, 74.35249),
            doubleArrayOf(31.53445, 74.34721, 31.53435, 74.34747),
            doubleArrayOf(31.53900, 74.33595, 31.53820, 74.33715),
            doubleArrayOf(31.53927, 74.33557, 31.53900, 74.33595),
            doubleArrayOf(31.53435, 74.34747, 31.53294, 74.35109),
            doubleArrayOf(31.53232, 74.35228, 31.53337, 74.34961),
            doubleArrayOf(31.53140, 74.35474, 31.53112, 74.35540))
}
