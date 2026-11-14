package app.flare.auto

import android.content.SharedPreferences
import java.util.WeakHashMap

/**
 * Offline arrival estimates. Every displayed duration comes from here: [remaining] (and [total] = remaining from the
 * start). The route itself (GraphHopper's choice, NavRoute.millis / stepMillis) is never changed; only the time shown.
 *
 * Driving time, from the road of each route instruction (the street name the instruction continues on):
 *  - Canal Road / Canal Bank Road: [CANAL_KMH]
 *  - Ring Road and motorways (names with "Ring Road", "Motorway" or an M-number such as M-2): [FAST_KMH]
 *  - every other road, and unnamed ones: [CITY_KMH]
 *
 * Peak hours ("Peak hours" setting, on by default): inside the morning or evening window on a selected weekday the
 * whole estimate is multiplied by [PEAK_LONG] for journeys over [PEAK_LONG_FROM_M], otherwise by [PEAK_SHORT]. The
 * journey length is the planned trip, fixed when it starts. The multiplier eases in over the first and out over the
 * last [RAMP_MIN] minutes of a window, so estimates do not jump at the boundary. Not live traffic.
 */
internal object TrafficEta {
    const val CANAL_KMH = 50.0
    const val CITY_KMH = 40.0
    const val FAST_KMH = 80.0
    const val PEAK_LONG = 1.8
    const val PEAK_SHORT = 1.2
    const val PEAK_LONG_FROM_M = 6000.0

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

    data class Settings(val enabled: Boolean, val morning: Window, val evening: Window, val days: Int) {
        fun onDay(isoDay: Int) = days and (1 shl (isoDay - 1)) != 0
    }

    val MORNING = Window(7 * 60 + 30, 9 * 60 + 30)
    val EVENING = Window(16 * 60 + 30, 19 * 60 + 30)
    /** Monday..Friday (bit 0 = Monday ... bit 6 = Sunday, ISO day numbers). */
    const val WEEKDAYS = 0b0011111
    const val RAMP_MIN = 15.0

    /** Stored settings; the peak on/off key keeps its name ("offline_eta_traffic"), now on unless switched off. */
    fun read(p: SharedPreferences) = Settings(
        p.getBoolean(Keys.OFFLINE_ETA_TRAFFIC, true),
        Window.parse(p.getString(Keys.OFFLINE_ETA_MORNING, null)) ?: MORNING,
        Window.parse(p.getString(Keys.OFFLINE_ETA_EVENING, null)) ?: EVENING,
        p.getInt(Keys.OFFLINE_ETA_DAYS, WEEKDAYS) and 0b1111111)

    /** Driving speed (km/h) on a road named [road]. */
    fun speedKmh(road: String): Double {
        val name = road.lowercase()
        return when {
            "ring road" in name || "ringroad" in name || "motorway" in name || Regex("""\bm-?\d{1,2}\b""").containsMatchIn(name) -> FAST_KMH
            "canal" in name -> CANAL_KMH
            else -> CITY_KMH
        }
    }

    /** Peak multiplier for a journey of [tripMetres]. */
    fun peakFactor(tripMetres: Double) = if (tripMetres > PEAK_LONG_FROM_M) PEAK_LONG else PEAK_SHORT

    /** 0..1 for [window] at [minute] of the day: ramps over [RAMP_MIN] at both ends, 0 outside. */
    fun weight(window: Window, minute: Double): Double {
        if (minute < window.start || minute >= window.end) return 0.0
        return minOf(1.0, (minute - window.start) / RAMP_MIN, (window.end - minute) / RAMP_MIN)
    }

    /** How far into peak hours it is now (0 = off peak, 1 = full peak); refreshed about every 30 s. */
    data class Schedule(val peak: Double) {
        companion object { val NONE = Schedule(0.0) }
    }

    fun schedule(s: Settings, isoDay: Int, minute: Double): Schedule {
        if (!s.enabled || !s.onDay(isoDay)) return Schedule.NONE
        return Schedule(maxOf(weight(s.morning, minute), weight(s.evening, minute)))
    }

    fun scheduleNow(s: Settings, now: java.time.LocalDateTime = java.time.LocalDateTime.now()) =
        schedule(s, now.dayOfWeek.value, now.hour * 60 + now.minute + now.second / 60.0)

    /** Multiplier for a journey of [tripMetres] at [schedule]; 1.0 off peak. */
    fun factor(schedule: Schedule, tripMetres: Double) = 1.0 + (peakFactor(tripMetres) - 1.0) * schedule.peak

    /** Driving time (ms) from the start to each geometry point of the route, at the road speeds above. */
    class Profile(val base: DoubleArray)

    private val profiles = WeakHashMap<NavRoute, Profile>()

    /** Builds (or returns) the route's profile; called on the routing worker when a route is created. */
    fun profile(route: NavRoute): Profile = synchronized(profiles) { profiles[route] } ?: build(route).also {
        synchronized(profiles) { profiles[route] = it }
    }

    /** Remaining time from [along] metres into [route], with the peak multiplier when [schedule] is in peak hours. */
    fun remaining(route: NavRoute, along: Double, schedule: Schedule, tripMetres: Double): Long {
        val base = profile(route).base
        val rest = base.last() - at(route, base, along)
        return (rest * factor(schedule, tripMetres)).toLong()
    }

    fun total(route: NavRoute, schedule: Schedule, tripMetres: Double) = remaining(route, 0.0, schedule, tripMetres)

    /** Off-peak remaining time. */
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

    /** Time (ms) to drive from the start to [d] metres: each instruction's stretch at the speed of its road. */
    fun baseAt(route: NavRoute, d: Double): Double {
        val total = route.cumulative.last()
        val city = CITY_KMH / 3.6
        if (route.steps.isEmpty()) return d / city * 1000
        // Geometry before the first instruction (normally none) at city speed.
        var sum = minOf(d, route.steps.first().at).coerceAtLeast(0.0) / city * 1000
        for (i in route.steps.indices) {
            val start = route.steps[i].at
            val end = (route.steps.getOrNull(i + 1)?.at ?: total).coerceAtLeast(start)
            val mps = speedKmh(route.steps[i].road) / 3.6
            if (d >= end) { sum += (end - start) / mps * 1000; continue }
            if (d > start) sum += (d - start) / mps * 1000
            break
        }
        return sum
    }

    private fun build(route: NavRoute): Profile {
        val c = route.cumulative
        return Profile(DoubleArray(c.size) { baseAt(route, c[it]) })
    }
}
