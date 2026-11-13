package app.flare.auto

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.os.SystemClock
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.TextView
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapLibreMapOptions
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import java.util.Locale

/**
 * Offline vector map (MapLibre Native, OpenGL ES 3) for a navigation pack's display map: dark night style, blue
 * route, a vehicle marker with a direction arrow, and either a heading-up tilted view or a flat north-up view while
 * following GPS. Everything it loads is local (MBTiles, glyphs and style inside the pack); the network is switched
 * off for MapLibre.
 *
 * Lives inside [OfflineMapCard], which owns GPS, attribution and buttons. Rendered into a TextureView so the card's
 * rounded clip and the Home grid's scaling apply to it.
 */
internal class VectorMapView(
    context: Context,
    private val vector: NavigationPack.VectorMap,
    private val bbox: DoubleArray,
    private val start: LatLng,
    private val startZoom: Double,
    /** False for the Home preview: no pan, zoom, rotate or tilt gestures. */
    private val interactive: Boolean,
    private val onFailed: (String) -> Unit
) : FrameLayout(context) {
    private val density = resources.displayMetrics.density
    private val view: MapView
    private var map: MapLibreMap? = null
    private var style: Style? = null
    private var route: NavRoute? = null
    /** Route vertices still ahead (lat,lon pairs); null shows the whole [route]. */
    private var ahead: DoubleArray? = null
    private var position: LatLng? = null
    private var heading: Double? = null
    private var destroyed = false
    /** Remaining route for the fix the marker is still gliding to; drawn when it gets there. */
    private var pendingAhead: DoubleArray? = null
    private var followZoomSet = false
    /** Following the vehicle (set by [follow], cleared by a map gesture, [flatten] or [overview]). */
    private var tracking = false
    private var trackingHeadingUp = true
    /** Gray alternative routes (overview and while navigating). */
    private var alternatives: List<DoubleArray> = emptyList()
    private var glide: ValueAnimator? = null
    private var lastFixAt = 0L
    private var lastFrameAt = 0L
    /** Street name under the marker (shown only when [setStreet] has a reliable one). */
    private val street = TextView(context).apply {
        textSize = 12f; setTextColor(NavColors.TEXT); maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END
        background = NavColors.card(context, NavColors.CARD, 10)
        setPadding((8 * density).toInt(), (2 * density).toInt(), (8 * density).toInt(), (2 * density).toInt())
        maxWidth = (220 * density).toInt(); visibility = GONE
    }
    /** Called when the driver drags the map (the card stops following GPS). */
    var onUserMove: (() -> Unit)? = null
    var onLongPress: ((Double, Double) -> Unit)? = null

    init {
        MapLibre.getInstance(context.applicationContext)
        MapLibre.setConnected(false) // offline only: every URL in the style is local
        val options = MapLibreMapOptions.createFromAttributes(context)
            .textureMode(true).attributionEnabled(false).logoEnabled(false).compassEnabled(false)
        view = MapView(context, options)
        addView(view, LayoutParams(MATCH, MATCH))
        addView(street, LayoutParams(WRAP, WRAP, Gravity.TOP or Gravity.LEFT))
        view.onCreate(null)
        view.addOnDidFailLoadingMapListener { message -> if (!destroyed) onFailed(message ?: "style failed to load") }
        view.getMapAsync { m -> if (!destroyed) ready(m) }
        view.onStart()
        view.onResume()
    }

    private fun ready(m: MapLibreMap) {
        map = m
        m.uiSettings.isAttributionEnabled = false
        m.uiSettings.isLogoEnabled = false
        m.uiSettings.isCompassEnabled = false
        if (!interactive) m.uiSettings.setAllGesturesEnabled(false)
        m.setMinZoomPreference(MIN_ZOOM)
        m.setMaxZoomPreference(MAX_ZOOM)
        m.setLatLngBoundsForCameraTarget(LatLngBounds.from(bbox[2], bbox[3], bbox[0], bbox[1]))
        m.moveCamera(CameraUpdateFactory.newCameraPosition(CameraPosition.Builder().target(start).zoom(startZoom).build()))
        m.addOnCameraMoveStartedListener { reason -> if (reason == MapLibreMap.OnCameraMoveStartedListener.REASON_API_GESTURE) { tracking = false; onUserMove?.invoke() } }
        m.addOnCameraMoveListener { placeStreet() }
        m.addOnMapLongClickListener { at -> onLongPress?.let { it(at.latitude, at.longitude); true } ?: false }
        val json = try {
            vector.style.readText().replace("{pack}", vector.style.parentFile!!.absolutePath.replace('\\', '/'))
        } catch (e: Exception) { onFailed("style unreadable (${e.javaClass.simpleName})"); return }
        m.setStyle(Style.Builder().fromJson(json)) { s -> if (!destroyed) styled(s) }
    }

    private fun styled(s: Style) {
        style = s
        s.addImage(ARROW, marker(true))
        s.addImage(DOT, marker(false))
        s.addSource(GeoJsonSource(ALTERNATIVES, EMPTY))
        s.addSource(GeoJsonSource(ROUTE, EMPTY))
        s.addSource(GeoJsonSource(VEHICLE, EMPTY))
        val below = if (s.getLayer("road-label") != null) "road-label" else null
        val casing = LineLayer("flare-route-casing", ROUTE).withProperties(
            PropertyFactory.lineColor(ROUTE_CASING), PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
            PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
            PropertyFactory.lineWidth(Expression.interpolate(Expression.exponential(1.5f), Expression.zoom(),
                Expression.stop(10, 4f), Expression.stop(14, 10f), Expression.stop(18, 26f))))
        val line = LineLayer("flare-route", ROUTE).withProperties(
            PropertyFactory.lineColor(ROUTE_COLOR), PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
            PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
            PropertyFactory.lineWidth(Expression.interpolate(Expression.exponential(1.5f), Expression.zoom(),
                Expression.stop(10, 2.5f), Expression.stop(14, 7f), Expression.stop(18, 19f))))
        val gray = LineLayer("flare-alternatives", ALTERNATIVES).withProperties(
            PropertyFactory.lineColor(ALTERNATIVE_COLOR), PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
            PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
            PropertyFactory.lineWidth(Expression.interpolate(Expression.exponential(1.5f), Expression.zoom(),
                Expression.stop(10, 2.5f), Expression.stop(14, 6f), Expression.stop(18, 16f))))
        if (below != null) { s.addLayerBelow(gray, below); s.addLayerBelow(casing, below); s.addLayerBelow(line, below) }
        else { s.addLayer(gray); s.addLayer(casing); s.addLayer(line) }
        s.addLayer(SymbolLayer("flare-vehicle", VEHICLE).withProperties(
            PropertyFactory.iconImage(Expression.get("icon")), PropertyFactory.iconRotate(Expression.get("bearing")),
            PropertyFactory.iconRotationAlignment(Property.ICON_ROTATION_ALIGNMENT_MAP),
            PropertyFactory.iconPitchAlignment(Property.ICON_PITCH_ALIGNMENT_MAP),
            PropertyFactory.iconAllowOverlap(true), PropertyFactory.iconIgnorePlacement(true)))
        drawRoute()
        drawAlternatives()
        drawVehicle()
    }

    fun showRoute(path: NavRoute?) { route = path; ahead = null; pendingAhead = null; drawRoute() }

    /** Gray lines for alternative routes (lat,lon pairs each); empty removes them. */
    fun showAlternatives(lines: List<DoubleArray>) { alternatives = lines; drawAlternatives() }

    private fun drawAlternatives() {
        val source = style?.getSourceAs<GeoJsonSource>(ALTERNATIVES) ?: return
        val features = alternatives.filter { it.size >= 4 }.joinToString(",") { points ->
            val coords = StringBuilder()
            for (i in 0 until points.size / 2) {
                if (i > 0) coords.append(',')
                coords.append('[').append(fmt(points[i * 2 + 1])).append(',').append(fmt(points[i * 2])).append(']')
            }
            """{"type":"Feature","properties":{},"geometry":{"type":"LineString","coordinates":[$coords]}}"""
        }
        source.setGeoJson("""{"type":"FeatureCollection","features":[$features]}""")
    }

    /**
     * Flat north-up view fitting [lines] (route overview after choosing a destination); stops following until
     * [follow] is called again. [left]/[bottom]: pixels covered by the host's panels.
     */
    fun overview(lines: List<DoubleArray>, left: Int, bottom: Int) {
        val m = map ?: return
        var minLat = 90.0; var minLon = 180.0; var maxLat = -90.0; var maxLon = -180.0
        for (points in lines) for (i in 0 until points.size / 2) {
            minLat = minOf(minLat, points[i * 2]); maxLat = maxOf(maxLat, points[i * 2])
            minLon = minOf(minLon, points[i * 2 + 1]); maxLon = maxOf(maxLon, points[i * 2 + 1])
        }
        if (minLat > maxLat) return
        tracking = false
        glide?.cancel()
        // Intercity routes need less than the browsing limit (packs have tiles from zoom 4); restored by [endOverview].
        m.setMinZoomPreference(OVERVIEW_MIN_ZOOM)
        val edge = (24 * density).toInt()
        val bounds = LatLngBounds.from(maxLat, maxLon, minLat, minLon)
        val pad = intArrayOf(left + edge, edge, (72 * density).toInt(), bottom + edge)
        // Flat and unpadded first: the fitted zoom must not depend on the following camera's tilt or padding.
        m.moveCamera(CameraUpdateFactory.newCameraPosition(CameraPosition.Builder().target(bounds.center)
            .zoom(m.cameraPosition.zoom).bearing(0.0).tilt(0.0).padding(0.0, 0.0, 0.0, 0.0).build()))
        val zoom = m.getCameraForLatLngBounds(bounds, pad)?.zoom ?: return
        // The padding keeps the routes centred in the part of the map not covered by the route list.
        m.moveCamera(CameraUpdateFactory.newCameraPosition(CameraPosition.Builder().target(bounds.center).zoom(zoom)
            .bearing(0.0).tilt(0.0).padding(pad[0].toDouble(), pad[1].toDouble(), pad[2].toDouble(), pad[3].toDouble()).build()))
    }

    /** Leaves the route overview: the normal minimum zoom applies again. */
    fun endOverview() {
        val m = map ?: return
        m.setMinZoomPreference(MIN_ZOOM)
        followZoomSet = false // settle at the driving zoom again
        m.moveCamera(CameraUpdateFactory.newCameraPosition(CameraPosition.Builder(m.cameraPosition).padding(0.0, 0.0, 0.0, 0.0).build()))
    }

    /** Only the part of the route ahead of the vehicle stays drawn ([NavGeometry.remaining]). */
    fun showRemaining(points: DoubleArray) {
        if (route == null) return
        // While the marker glides to the new fix, keep the line from where it is shown (no gap ahead of the marker).
        // A glide cut short by this fix had nearly reached the previous one: its line goes first (lag of one fix at most).
        if (glide?.isRunning == true) { applyPendingAhead(); pendingAhead = points; return }
        ahead = points; pendingAhead = null; drawRoute()
    }

    private fun applyPendingAhead() { pendingAhead?.let { ahead = it; pendingAhead = null; drawRoute() } }

    private fun drawRoute() {
        val source = style?.getSourceAs<GeoJsonSource>(ROUTE) ?: return
        val points = ahead ?: route?.points
        if (points == null || points.size < 4) { source.setGeoJson(EMPTY); return }
        val coords = StringBuilder()
        for (i in 0 until points.size / 2) {
            if (i > 0) coords.append(',')
            coords.append('[').append(fmt(points[i * 2 + 1])).append(',').append(fmt(points[i * 2])).append(']')
        }
        source.setGeoJson("""{"type":"Feature","properties":{},"geometry":{"type":"LineString","coordinates":[$coords]}}""")
    }

    /**
     * New vehicle position and heading (null: direction unknown, shown as a plain dot). The marker (and the camera
     * while following) glides from where it is shown to the new fix over about the fix interval
     * ([NavMotion.glide]) and then stays there: no movement is predicted when fixes stop. First fixes, long gaps and
     * jumps are drawn at once.
     */
    fun setVehicle(lat: Double, lon: Double, heading: Double?) {
        val target = LatLng(lat, lon)
        val now = SystemClock.elapsedRealtime()
        val gap = if (lastFixAt == 0L) 0L else now - lastFixAt
        lastFixAt = now
        glide?.cancel()
        val from = position
        val fromHeading = this.heading
        val duration = if (from == null || !isShown) null else NavMotion.glide(gap, from.distanceTo(target))
        if (duration == null || from == null) { position = target; this.heading = heading; drawVehicle(); applyPendingAhead(); if (tracking) camera(false); return }
        val origin: LatLng = from
        glide = ValueAnimator.ofFloat(0f, 1f).apply {
            this.duration = duration
            interpolator = android.view.animation.LinearInterpolator()
            addUpdateListener { a ->
                if (destroyed) { a.cancel(); return@addUpdateListener }
                val f = a.animatedValue as Float
                val frame = SystemClock.elapsedRealtime()
                if (f < 1f && frame - lastFrameAt < FRAME_MS) return@addUpdateListener // ~30 fps is enough and spares the GPU
                lastFrameAt = frame
                position = LatLng(origin.latitude + (target.latitude - origin.latitude) * f, origin.longitude + (target.longitude - origin.longitude) * f)
                this@VectorMapView.heading = if (heading == null || fromHeading == null) heading else NavMotion.turn(fromHeading, heading, f)
                drawVehicle()
                if (f >= 1f) applyPendingAhead()
                if (tracking) camera(false)
            }
            start()
        }
    }

    /** Street under the marker, or null to hide the label. */
    fun setStreet(name: String?) {
        street.text = name.orEmpty()
        street.visibility = if (name.isNullOrBlank()) GONE else VISIBLE
        placeStreet()
    }

    private fun placeStreet() {
        if (street.visibility != VISIBLE) return
        val m = map ?: return
        val at = position ?: return
        val point = m.projection.toScreenLocation(at)
        street.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.AT_MOST), MeasureSpec.makeMeasureSpec(height, MeasureSpec.AT_MOST))
        street.translationX = (point.x - street.measuredWidth / 2f).coerceIn(0f, maxOf(0f, (width - street.measuredWidth).toFloat()))
        street.translationY = (point.y + MARKER_DP * density * 0.75f).coerceIn(0f, maxOf(0f, (height - street.measuredHeight).toFloat()))
    }

    private fun drawVehicle() {
        val source = style?.getSourceAs<GeoJsonSource>(VEHICLE) ?: return
        val at = position ?: run { source.setGeoJson(EMPTY); return }
        val icon = if (heading == null) DOT else ARROW
        source.setGeoJson("""{"type":"Feature","properties":{"icon":"$icon","bearing":${fmt(heading ?: 0.0)}},""" +
            """"geometry":{"type":"Point","coordinates":[${fmt(at.longitude)},${fmt(at.latitude)}]}}""")
        placeStreet()
    }

    /**
     * Moves the camera to the vehicle. Heading-up: rotated to [heading], tilted, the car in the lower part of the
     * view with the road ahead visible. North-up: flat, north at the top, car centred.
     */
    /** Zoom the camera settles at when following starts (closer than an overview, like in-car navigation). */
    var followZoom = FOLLOW_ZOOM

    fun follow(headingUp: Boolean, animate: Boolean) {
        tracking = true
        trackingHeadingUp = headingUp
        // While the marker glides, every frame moves the camera with it.
        if (glide?.isRunning == true) return
        camera(animate)
    }

    private fun camera(animate: Boolean) {
        val headingUp = trackingHeadingUp
        val m = map ?: return
        val at = position ?: return
        val current = m.cameraPosition
        val builder = CameraPosition.Builder().target(at)
        if (headingUp) {
            builder.bearing(heading ?: current.bearing).tilt(HEADING_TILT).padding(0.0, height * 0.42, 0.0, 0.0)
        } else {
            builder.bearing(0.0).tilt(0.0).padding(0.0, 0.0, 0.0, 0.0)
        }
        // First follow after opening: settle at the close driving zoom unless the driver is already closer.
        if (!followZoomSet && current.zoom < followZoom - 0.4) builder.zoom(followZoom)
        val settled = followZoomSet // false: first follow since the map opened, placed at once
        followZoomSet = true
        val update = CameraUpdateFactory.newCameraPosition(builder.build())
        // Glide between nearby fixes only; a far jump (first fix after opening, GPS reacquired) is instant.
        val near = current.target?.let { it.distanceTo(at) < JUMP_M } ?: false
        if (animate && near && settled) m.easeCamera(update, FOLLOW_MS, false) else m.moveCamera(update)
    }

    /** Flat north-up view around the current centre (leaving heading-up while not following). */
    fun flatten() {
        tracking = false
        val m = map ?: return
        m.easeCamera(CameraUpdateFactory.newCameraPosition(CameraPosition.Builder(m.cameraPosition).bearing(0.0).tilt(0.0)
            .padding(0.0, 0.0, 0.0, 0.0).build()), 400)
    }

    fun zoomBy(delta: Double) { map?.easeCamera(CameraUpdateFactory.zoomBy(delta), 250) }

    fun center(): Pair<Double, Double>? = map?.cameraPosition?.target?.let { it.latitude to it.longitude }

    fun destroy() {
        if (destroyed) return
        destroyed = true
        glide?.cancel(); glide = null
        onUserMove = null; onLongPress = null; style = null; map = null
        try { view.onPause(); view.onStop(); view.onDestroy() } catch (_: RuntimeException) { }
        removeAllViews()
    }

    fun lowMemory() { view.onLowMemory() }

    /** Blue disc with a white rim; with [arrow], a white navigation arrow pointing up (north before rotation). */
    private fun marker(arrow: Boolean): Bitmap {
        val size = (MARKER_DP * density).toInt()
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val r = size / 2f
        paint.color = 0x55000000
        c.drawCircle(r, r + density * .6f, r - 1f * density, paint)
        paint.color = 0xffffffff.toInt()
        c.drawCircle(r, r, r - 1.4f * density, paint)
        paint.color = MARKER_COLOR
        c.drawCircle(r, r, r - 3f * density, paint)
        paint.color = 0xffffffff.toInt()
        if (arrow) {
            // The arrow fills most of the smaller disc so the direction stays readable at a glance.
            val s = r * 0.66f
            val p = Path().apply {
                moveTo(r, r - s); lineTo(r + s * 0.72f, r + s * 0.78f); lineTo(r, r + s * 0.38f); lineTo(r - s * 0.72f, r + s * 0.78f); close()
            }
            c.drawPath(p, paint)
        } else c.drawCircle(r, r, r * 0.28f, paint)
        return bitmap
    }

    private fun fmt(v: Double) = String.format(Locale.US, "%.6f", v)

    companion object {
        private const val ROUTE = "flare-route-source"
        private const val ALTERNATIVES = "flare-alternatives-source"
        private const val ALTERNATIVE_COLOR = 0xff7d8794.toInt()
        /** Minimum time between glide frames (about 30 fps). */
        private const val FRAME_MS = 32L
        private const val VEHICLE = "flare-vehicle-source"
        private const val ARROW = "flare-vehicle-arrow"
        private const val DOT = "flare-vehicle-dot"
        private const val EMPTY = """{"type":"FeatureCollection","features":[]}"""
        private const val ROUTE_COLOR = 0xff4c8dff.toInt()
        private const val ROUTE_CASING = 0xff1f4fae.toInt()
        private const val MARKER_COLOR = 0xff2f7dff.toInt()
        const val HEADING_TILT = 55.0
        /** Closest zoom (tiles stop at z15 and are over-zoomed; streets stay sharp as vectors). */
        const val MAX_ZOOM = 20.5
        private const val MIN_ZOOM = 8.0
        private const val OVERVIEW_MIN_ZOOM = 4.0
        /** Default following zoom: full map; the Home widget uses [WIDGET_FOLLOW_ZOOM] (smaller view, a bit wider). */
        const val FOLLOW_ZOOM = 17.4
        const val WIDGET_FOLLOW_ZOOM = 16.9
        /** Vehicle marker diameter (44 dp in r2, 32 dp in r3, 23 dp in r4, 27 dp since the glide update). */
        const val MARKER_DP = 27
        private const val FOLLOW_MS = 950
        /** Camera moves longer than this are not animated. */
        private const val JUMP_M = 1500.0

        /** MapLibre needs OpenGL ES 3.0. */
        fun supported(context: Context): Boolean {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? android.app.ActivityManager ?: return false
            return am.deviceConfigurationInfo.reqGlEsVersion >= 0x30000
        }

        /** Why this unit cannot run the MapLibre renderer (shown in settings), or null when it can. */
        fun unsupportedReason(context: Context): String? = when {
            !supported(context) -> "needs OpenGL ES 3.0"
            // Native MapLibre is packaged for these ABIs only (app/build.gradle.kts abiFilters).
            android.os.Build.SUPPORTED_ABIS.none { it == "arm64-v8a" || it == "x86_64" } -> "not available for this processor"
            else -> null
        }

        /** MapLibre zoom (512 px tiles) showing [metres] across [widthPx] at [lat]. */
        fun zoomForSpan(metres: Double, widthPx: Int, lat: Double): Double {
            val metresPerPixel = metres / widthPx.coerceAtLeast(1)
            return (Math.log(40075016.686 * Math.cos(Math.toRadians(lat)) / (512 * metresPerPixel)) / Math.log(2.0)).coerceIn(8.0, MAX_ZOOM - 0.5)
        }
    }
}
