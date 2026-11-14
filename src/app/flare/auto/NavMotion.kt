package app.flare.auto

import kotlin.math.abs

/**
 * Pure rules for showing the vehicle on the map (unit tested in NavMotionTest):
 * * [snap]: draw the marker on the route only when the fix clearly belongs to it;
 * * [glide]: how long to animate the marker between two fixes (never beyond the newest fix);
 * * [street]: the road being driven, from the route's instructions;
 * * [compare] / [divergesAt]: the ETA comparison and lifetime of an alternative route during navigation.
 */
internal object NavMotion {
    /** Fixes less accurate than this are never snapped. */
    const val SNAP_ACCURACY_M = 25f
    /** Widest gap between fix and route that may be snapped (also limited by the fix accuracy). */
    const val SNAP_MAX_M = 20.0
    /** Without a trustworthy direction (slow or no bearing) only this close is snapped. */
    const val SNAP_SLOW_M = 6.0
    /** Travel direction and route direction must agree within this many degrees. */
    const val SNAP_ANGLE = 35.0
    /**
     * Once on the route, these wider limits keep it there ([snap] `held`): a fix a few metres outside the normal limits
     * no longer drops the marker off the line for one second and back (sideways hop and map wobble).
     */
    const val HELD_MAX_M = 30.0
    const val HELD_SLOW_M = 12.0
    const val HELD_ANGLE = 50.0
    /** Gaps longer than this, or jumps farther than [GLIDE_MAX_M], are drawn at once. */
    const val GLIDE_MAX_GAP_MS = 5000L
    const val GLIDE_MAX_M = 150.0
    /** Glide frames: at most about 30 per second, and only as many as visibly move or turn the picture. */
    const val MIN_FRAME_MS = 32L
    const val PX_PER_FRAME = 1.5
    const val DEG_PER_FRAME = 1.0
    /** ETA differences under this read "Similar ETA". */
    const val SIMILAR_MS = 3 * 60_000L

    /** A position on the route: [bearing] is the route direction there, [along] metres from its start. */
    class Snapped(val lat: Double, val lon: Double, val segment: Int, val along: Double, val bearing: Double)

    /**
     * The point of [route] nearest the fix when the fix is accurate ([SNAP_ACCURACY_M]), close to the route
     * (within its accuracy, at most [SNAP_MAX_M]) and travelling the route's way (GPS bearing within [SNAP_ANGLE]
     * at [HeadingFilter.TRUSTED_SPEED] or faster; slower or without a bearing, within [SNAP_SLOW_M]). Otherwise null:
     * the raw GPS position is shown. [held]: the previous fix was snapped, so the wider HELD_ limits apply.
     */
    fun snap(route: NavRoute?, lat: Double, lon: Double, accuracy: Float?, bearing: Float?, speed: Float?, previous: Int,
             held: Boolean = false): Snapped? {
        if (route == null || route.points.size < 4 || accuracy == null || accuracy > SNAP_ACCURACY_M) return null
        val position = NavGeometry.nearest(route, lat, lon, previous)
        val count = route.points.size / 2
        val i = position.segment.coerceIn(0, count - 2)
        val p = route.points
        val routeBearing = HeadingFilter.bearingBetween(p[i * 2], p[i * 2 + 1], p[i * 2 + 2], p[i * 2 + 3])
        val moving = speed != null && bearing != null && speed >= HeadingFilter.TRUSTED_SPEED && bearing.isFinite()
        val ok = if (moving) position.away <= (if (held) minOf(HELD_MAX_M, maxOf(15.0, accuracy * 2.0)) else minOf(SNAP_MAX_M, maxOf(8.0, accuracy * 1.2))) &&
            abs(HeadingFilter.shortest(bearing!!.toDouble() - routeBearing)) <= (if (held) HELD_ANGLE else SNAP_ANGLE)
        else position.away <= (if (held) HELD_SLOW_M else SNAP_SLOW_M)
        if (!ok) return null
        val length = route.cumulative[i + 1] - route.cumulative[i]
        val t = if (length <= 0.0) 0.0 else ((position.along - route.cumulative[i]) / length).coerceIn(0.0, 1.0)
        return Snapped(p[i * 2] + t * (p[i * 2 + 2] - p[i * 2]), p[i * 2 + 1] + t * (p[i * 2 + 3] - p[i * 2 + 1]), i, position.along, routeBearing)
    }

    /**
     * Animation time from the shown position to a new fix [gapMs] after the previous one, [metres] away: about the
     * fix interval, so the marker reaches each fix as the next one is due and then waits there (no prediction).
     * Null: draw at once (first fix, long gap, jump).
     */
    fun glide(gapMs: Long, metres: Double): Long? =
        if (gapMs <= 0 || gapMs > GLIDE_MAX_GAP_MS || !metres.isFinite() || metres > GLIDE_MAX_M) null else gapMs.coerceIn(250L, 1000L)

    /**
     * Time between glide frames for a glide of [durationMs] that moves the marker [pixels] on screen and turns it
     * (or, heading-up, the whole map) [degrees]: about one frame per 1.5 px or 1°, at most one per [MIN_FRAME_MS].
     * Null when one step is enough (a wide view where the car moves less than a couple of pixels per fix): the fix is
     * then drawn at once instead of redrawing an unchanged picture ~30 times. Redrawing a wide, tilted map is what
     * made the 3D view lag on the head unit.
     */
    fun glideFrameMs(durationMs: Long, pixels: Double, degrees: Double): Long? {
        if (!pixels.isFinite()) return MIN_FRAME_MS
        val steps = maxOf(pixels / PX_PER_FRAME, abs(degrees) / DEG_PER_FRAME)
        if (steps < 2.0) return null
        return maxOf(MIN_FRAME_MS, (durationMs / steps).toLong())
    }

    /** Heading between [from] and [to] (degrees) at fraction [f], turning the short way. */
    fun turn(from: Double, to: Double, f: Float): Double = HeadingFilter.normalize(from + HeadingFilter.shortest(to - from) * f)

    /**
     * Road being driven [along] metres into [route]: the road of the last instruction already passed, or null when
     * the instruction names none (the label is then hidden rather than guessed).
     */
    fun street(route: NavRoute?, along: Double): String? {
        val step = route?.steps?.lastOrNull { it.at <= along + 1.0 } ?: return null
        return step.road.trim().takeIf { it.isNotEmpty() }
    }

    /**
     * "Similar ETA", "+12 min" or "5 min faster" for an alternative computed together with the chosen route. Both
     * share the road up to their split, so the difference stays valid until the car passes it ([divergesAt]).
     */
    fun compare(alternativeMillis: Long, chosenMillis: Long): String {
        val d = alternativeMillis - chosenMillis
        return when {
            abs(d) < SIMILAR_MS -> "Similar ETA"
            d > 0 -> "+${TripFormat.duration(d)}"
            else -> "${TripFormat.duration(-d)} faster"
        }
    }

    /**
     * Metres along [chosen] where [alternative] leaves it for good (more than [apart] m away for over [detour] m of the
     * alternative), or null if it never does. Shorter departures that rejoin (a parallel street through town) do not
     * count, so the alternative is not dropped before the car reaches its real turn-off.
     */
    fun divergesAt(chosen: NavRoute, alternative: NavRoute, apart: Double = 40.0, detour: Double = 2000.0): Double? {
        val p = alternative.points
        val travelled = alternative.cumulative
        var segment = 0
        var lastShared = 0.0
        var leftAt = -1.0 // metres along the alternative where the current departure began; -1 while on the route
        for (i in 0 until p.size / 2) {
            val position = NavGeometry.nearest(chosen, p[i * 2], p[i * 2 + 1], segment)
            if (position.away > apart) {
                if (leftAt < 0) leftAt = travelled[i]
                if (travelled[i] - leftAt > detour) return lastShared
                continue
            }
            leftAt = -1.0
            segment = position.segment
            lastShared = maxOf(lastShared, position.along)
        }
        return if (leftAt >= 0) lastShared else null
    }
}
