package app.flare.auto

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.PointF
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
import org.maplibre.android.style.layers.FillExtrusionLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.android.style.sources.VectorSource
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
    /** Light map look ([MapAppearance]), decided when the view is created (changing it reloads the map). */
    private val light = MapAppearance.light(context)
    private val view: MapView
    private var map: MapLibreMap? = null
    private var style: Style? = null
    private var route: NavRoute? = null
    /** Route vertices still ahead (lat,lon pairs); null shows the whole [route]. */
    private var ahead: DoubleArray? = null
    private var position: LatLng? = null
    private var heading: Double? = null
    private var destroyed = false
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
    /** Called once, the first time the map has fully rendered (its surface then shows real map content). */
    var onRendered: (() -> Unit)? = null

    /** The last frame drawn on the map surface, or null before the surface has content. */
    fun snapshot(): Bitmap? {
        fun texture(v: android.view.View): android.view.TextureView? = when (v) {
            is android.view.TextureView -> v
            is android.view.ViewGroup -> (0 until v.childCount).firstNotNullOfOrNull { texture(v.getChildAt(it)) }
            else -> null
        }
        if (destroyed || map == null) return null
        return try { texture(view)?.takeIf { it.isAvailable }?.bitmap } catch (_: RuntimeException) { null }
    }

    /**
     * The vehicle marker while following: an ordinary view pinned where the camera keeps the car. The map draws
     * its own symbols a frame after the camera moves, so the map-drawn marker trailed and caught up with every glide
     * step; pinned to the camera's own anchor it stays perfectly still. The map's marker is used when not following.
     */
    private val pinned = android.widget.ImageView(context).apply { visibility = GONE }
    private val arrowBitmap by lazy { marker(true) }
    private val dotBitmap by lazy { marker(false) }
    /** The map-drawn marker is hidden (see [pinned]). */
    private var mapMarkerHidden = false

    /** Called when the driver drags the map (the card stops following GPS). */
    var onUserMove: (() -> Unit)? = null
    var onLongPress: ((Double, Double) -> Unit)? = null
    /** Map tap: latitude, longitude and the metres a finger's width covers there; true when the tap was used. */
    var onTap: ((Double, Double, Double) -> Boolean)? = null

    init {
        MapLibre.getInstance(context.applicationContext)
        MapLibre.setConnected(false) // offline only: every URL in the style is local
        val options = MapLibreMapOptions.createFromAttributes(context)
            .textureMode(true).attributionEnabled(false).logoEnabled(false).compassEnabled(false)
        view = MapView(context, options)
        addView(view, LayoutParams(MATCH, MATCH))
        addView(pinned, LayoutParams((MARKER_DP * density).toInt(), (MARKER_DP * density).toInt(), Gravity.TOP or Gravity.START))
        addView(street, LayoutParams(WRAP, WRAP, Gravity.TOP or Gravity.START))
        view.onCreate(null)
        view.addOnDidFailLoadingMapListener { message -> if (!destroyed) onFailed(message ?: "style failed to load") }
        view.addOnDidFinishRenderingMapListener { fully -> if (fully && !destroyed) { onRendered?.invoke(); onRendered = null } }
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
        m.addOnCameraMoveStartedListener { reason -> if (reason == MapLibreMap.OnCameraMoveStartedListener.REASON_API_GESTURE) { tracking = false; drawVehicle(); onUserMove?.invoke() } }
        m.addOnCameraMoveListener { pinMarker(); placeStreet() }
        m.addOnMapLongClickListener { at -> onLongPress?.let { it(at.latitude, at.longitude); true } ?: false }
        m.addOnMapClickListener { at ->
            val tap = onTap ?: return@addOnMapClickListener false
            tap(at.latitude, at.longitude, m.projection.getMetersPerPixelAtLatitude(at.latitude) * TAP_DP * density)
        }
        val json = try {
            vector.style.readText().replace("{pack}", vector.style.parentFile!!.absolutePath.replace('\\', '/'))
                .let { if (light) MapAppearance.lightStyle(it) else it }
        } catch (e: Exception) { onFailed("style unreadable (${e.javaClass.simpleName})"); return }
        m.setStyle(Style.Builder().fromJson(json)) { s -> if (!destroyed) styled(s) }
    }

    private fun styled(s: Style) {
        style = s
        if (ProfileStore.preferences(context).getBoolean(Keys.OFFLINE_MAP_BUILDINGS,false)) {
            vector.buildings?.let { file ->
                s.addSource(VectorSource("flare-buildings-source", "mbtiles://${file.absolutePath}"))
                val buildings = FillExtrusionLayer("flare-buildings", "flare-buildings-source").apply {
                    sourceLayer = "building"
                    minZoom = 15.5f
                    setProperties(PropertyFactory.fillExtrusionColor(if (light) MapAppearance.LIGHT_BUILDINGS else "#26374e"),
                        PropertyFactory.fillExtrusionHeight(Expression.get("height")),
                        PropertyFactory.fillExtrusionBase(Expression.get("min_height")),
                        PropertyFactory.fillExtrusionOpacity(0.85f))
                }
                // Keep navigation lines and road labels above the simple building layer.
                if (s.getLayer("road-casing") != null) s.addLayerBelow(buildings,"road-casing") else s.addLayer(buildings)
            }
        }
        s.addImage(ARROW, marker(true))
        s.addImage(DOT, marker(false))
        s.addSource(GeoJsonSource(ALTERNATIVES, EMPTY))
        s.addSource(GeoJsonSource(ROUTE, EMPTY))
        s.addSource(GeoJsonSource(LEAD, EMPTY))
        s.addSource(GeoJsonSource(VEHICLE, EMPTY))
        val below = if (s.getLayer("road-label") != null) "road-label" else null
        val casingStyle = arrayOf<org.maplibre.android.style.layers.PropertyValue<*>>(
            PropertyFactory.lineColor(ROUTE_CASING), PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
            PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
            PropertyFactory.lineWidth(Expression.interpolate(Expression.exponential(1.5f), Expression.zoom(),
                Expression.stop(10, 4f), Expression.stop(14, 10f), Expression.stop(18, 26f))))
        val lineStyle = arrayOf<org.maplibre.android.style.layers.PropertyValue<*>>(
            PropertyFactory.lineColor(ROUTE_COLOR), PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
            PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
            PropertyFactory.lineWidth(Expression.interpolate(Expression.exponential(1.5f), Expression.zoom(),
                Expression.stop(10, 2.5f), Expression.stop(14, 7f), Expression.stop(18, 19f))))
        val casing = LineLayer("flare-route-casing", ROUTE).withProperties(*casingStyle)
        val line = LineLayer("flare-route", ROUTE).withProperties(*lineStyle)
        val gray = LineLayer("flare-alternatives", ALTERNATIVES).withProperties(
            PropertyFactory.lineColor(ALTERNATIVE_COLOR), PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
            PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
            PropertyFactory.lineWidth(Expression.interpolate(Expression.exponential(1.5f), Expression.zoom(),
                Expression.stop(10, 2.5f), Expression.stop(14, 6f), Expression.stop(18, 16f))))
        // The lead (marker to the start of the remaining route) is drawn exactly like the route.
        val leadCasing = LineLayer("flare-route-lead-casing", LEAD).withProperties(*casingStyle)
        val leadLine = LineLayer("flare-route-lead", LEAD).withProperties(*lineStyle)
        if (below != null) {
            s.addLayerBelow(gray, below); s.addLayerBelow(casing, below); s.addLayerBelow(leadCasing, below)
            s.addLayerBelow(line, below); s.addLayerBelow(leadLine, below)
        } else { s.addLayer(gray); s.addLayer(casing); s.addLayer(leadCasing); s.addLayer(line); s.addLayer(leadLine) }
        s.addLayer(SymbolLayer("flare-vehicle", VEHICLE).withProperties(
            PropertyFactory.iconImage(Expression.get("icon")), PropertyFactory.iconRotate(Expression.get("bearing")),
            PropertyFactory.iconRotationAlignment(Property.ICON_ROTATION_ALIGNMENT_MAP),
            PropertyFactory.iconPitchAlignment(Property.ICON_PITCH_ALIGNMENT_MAP),
            PropertyFactory.iconAllowOverlap(true), PropertyFactory.iconIgnorePlacement(true)))
        drawRoute()
        drawAlternatives()
        drawVehicle()
        // Following was requested before the map was ready (e.g. only the last known fix, no new ones yet).
        if (tracking && !followZoomSet) camera(false)
    }

    fun showRoute(path: NavRoute?) { route = path; ahead = null; drawRoute(); drawLead() }

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
        drawVehicle()
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
        // Drawn at once from the new fix; the marker is still gliding towards it, and the lead joins the two, so the
        // line always starts at the marker (it used to start at the previous fix, then jump when the glide ended).
        ahead = points; drawRoute(); drawLead()
    }

    /**
     * Short segment from the marker as drawn to the start of the remaining route. Redrawn with every glide frame
     * (two points, cheap), so the visible line neither trails behind the marker nor leaves a gap in front of it.
     */
    private fun drawLead() {
        val source = style?.getSourceAs<GeoJsonSource>(LEAD) ?: return
        val points = ahead
        val at = position
        if (points == null || points.size < 2 || at == null) { source.setGeoJson(EMPTY); return }
        source.setGeoJson("""{"type":"Feature","properties":{},"geometry":{"type":"LineString","coordinates":[""" +
            "[${fmt(at.longitude)},${fmt(at.latitude)}],[${fmt(points[1])},${fmt(points[0])}]]}}")
    }

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
    /**
     * True while the Home page holding this map is being swiped: fixes are kept, not drawn, so the map surface does not
     * redraw under the moving page; the latest one is drawn when the swipe ends ([paused] = false).
     */
    var paused = false
        set(value) {
            if (field == value) return
            field = value
            if (value) { glide?.cancel(); return }
            pendingFix?.let { (lat, lon, heading) -> pendingFix = null; setVehicle(lat, lon, heading) }
        }
    private var pendingFix: Triple<Double, Double, Double?>? = null

    fun setVehicle(lat: Double, lon: Double, heading: Double?) {
        if (paused) { pendingFix = Triple(lat, lon, heading); return }
        val target = LatLng(lat, lon)
        val now = SystemClock.elapsedRealtime()
        val gap = if (lastFixAt == 0L) 0L else now - lastFixAt
        lastFixAt = now
        glide?.cancel()
        val from = position
        val fromHeading = this.heading
        // Parked (same position and heading): nothing to animate, so the GPU stays idle between fixes.
        if (from != null && from.distanceTo(target) < 0.3 && heading == this.heading) { drawLead(); return }
        val glideMs = if (from == null || !isShown) null else NavMotion.glide(gap, from.distanceTo(target))
        // Frames only as often as the picture visibly changes: wide views get one step per fix (see glideFrameMs).
        val frameMs = if (glideMs == null || from == null) null else {
            val pixels = map?.projection?.let { p ->
                val a = p.toScreenLocation(from); val b = p.toScreenLocation(target)
                Math.hypot((a.x - b.x).toDouble(), (a.y - b.y).toDouble())
            } ?: Double.POSITIVE_INFINITY
            val turn = if (heading == null || this.heading == null) 0.0 else HeadingFilter.shortest(heading - this.heading!!)
            NavMotion.glideFrameMs(glideMs, pixels, turn)
        }
        val duration = if (frameMs == null) null else glideMs
        if (duration == null || from == null) { position = target; this.heading = heading; if (tracking) camera(false); drawVehicle(); drawLead(); return }
        val origin: LatLng = from
        glide = ValueAnimator.ofFloat(0f, 1f).apply {
            this.duration = duration
            interpolator = android.view.animation.LinearInterpolator()
            addUpdateListener { a ->
                if (destroyed) { a.cancel(); return@addUpdateListener }
                val f = a.animatedValue as Float
                val frame = SystemClock.elapsedRealtime()
                if (f < 1f && frame - lastFrameAt < frameMs!!) return@addUpdateListener // only visible steps (glideFrameMs)
                lastFrameAt = frame
                position = LatLng(origin.latitude + (target.latitude - origin.latitude) * f, origin.longitude + (target.longitude - origin.longitude) * f)
                this@VectorMapView.heading = if (heading == null || fromHeading == null) heading else NavMotion.turn(fromHeading, heading, f)
                if (tracking) camera(false)
                drawVehicle()
                drawLead()
            }
            start()
        }
    }

    /** Street under the marker, or null to hide the label. */
    fun setStreet(name: String?) {
        if (street.text.toString() != name.orEmpty()) {
            street.text = name.orEmpty()
            // Measured once per new name, not on every camera move (placeStreet runs each glide frame).
            fun spec(size: Int) = if (size > 0) MeasureSpec.makeMeasureSpec(size, MeasureSpec.AT_MOST) else MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
            street.measure(spec(width), spec(height))
            labelPlaced = false
        }
        val visibility = if (name.isNullOrBlank()) GONE else VISIBLE
        if (street.visibility != visibility) { street.visibility = visibility; labelPlaced = false }
        placeStreet()
    }

    /** False until the label has a position for its current name (then it is placed without the steadying below). */
    private var labelPlaced = false

    private fun placeStreet() {
        if (street.visibility != VISIBLE) return
        val m = map ?: return
        val at = position ?: return
        // Following: under the pinned marker (the camera's anchor), so the label is as still as the marker.
        val point = if (pinned.visibility == VISIBLE) PointF(pinned.translationX + MARKER_DP * density / 2, pinned.translationY + MARKER_DP * density / 2)
            else m.projection.toScreenLocation(at)
        val x = Math.round((point.x - street.measuredWidth / 2f).coerceIn(0f, maxOf(0f, (width - street.measuredWidth).toFloat()))).toFloat()
        val y = Math.round((point.y + MARKER_DP * density * 0.75f).coerceIn(0f, maxOf(0f, (height - street.measuredHeight).toFloat()))).toFloat()
        // Following, the marker stays put on screen, but projecting the moving position against the eased camera
        // wobbles by a pixel or two each frame; ignore that so the text sits still. Real moves (panning) still apply.
        val steady = 4 * density
        if (labelPlaced && tracking && kotlin.math.abs(x - street.translationX) < steady && kotlin.math.abs(y - street.translationY) < steady) return
        street.translationX = x; street.translationY = y; labelPlaced = true
    }

    /**
     * Shows [pinned] at the camera's anchor while following (hiding the map's marker), or the map's marker otherwise.
     * Heading-up, the map is turned to the heading so the arrow points straight up; north-up it turns with the heading.
     */
    private fun pinMarker() {
        val m = map ?: return
        val pin = tracking && position != null && !destroyed
        if (pin != mapMarkerHidden) {
            mapMarkerHidden = pin
            style?.getLayer("flare-vehicle")?.setProperties(PropertyFactory.visibility(if (pin) Property.NONE else Property.VISIBLE))
        }
        if (!pin) { pinned.visibility = GONE; return }
        val target = m.cameraPosition.target ?: return
        val anchor = m.projection.toScreenLocation(target)
        val camera = m.cameraPosition
        pinned.setImageBitmap(if (heading == null) dotBitmap else arrowBitmap)
        val size = MARKER_DP * density
        pinned.translationX = Math.round(anchor.x - size / 2).toFloat()
        pinned.translationY = Math.round(anchor.y - size / 2).toFloat()
        pinned.rotation = if (heading == null) 0f else HeadingFilter.shortest((heading ?: 0.0) - camera.bearing).toFloat()
        // Lying on the tilted road like the map-drawn marker: foreshortened by the camera tilt.
        pinned.scaleY = Math.cos(Math.toRadians(camera.tilt)).toFloat().coerceIn(.5f, 1f) * PIN_PERSPECTIVE
        pinned.visibility = VISIBLE
    }

    private fun drawVehicle() {
        val source = style?.getSourceAs<GeoJsonSource>(VEHICLE) ?: return
        val at = position ?: run { source.setGeoJson(EMPTY); pinMarker(); return }
        val icon = if (heading == null) DOT else ARROW
        pinMarker()
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
        // followZoomSet: the camera has been placed since the map opened (fixes can arrive before the style loads).
        val started = !tracking || trackingHeadingUp != headingUp || !followZoomSet
        tracking = true
        trackingHeadingUp = headingUp
        if (started) drawVehicle()
        // While following, [setVehicle] already moves the camera with each fix (glide frames or one step). Easing
        // towards the same place again on every fix kept the map redrawing almost continuously, even when parked,
        // which is what made the tilted 3D view lag; only starting to follow or switching orientation moves it here.
        if (!started || glide?.isRunning == true) return
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
        drawVehicle()
        val m = map ?: return
        m.easeCamera(CameraUpdateFactory.newCameraPosition(CameraPosition.Builder(m.cameraPosition).bearing(0.0).tilt(0.0)
            .padding(0.0, 0.0, 0.0, 0.0).build()), 400)
    }

    fun zoomBy(delta: Double) {
        val m = map ?: return
        // While following, the marker glide moves the camera every frame and would cut an eased zoom short.
        if (tracking) m.moveCamera(CameraUpdateFactory.zoomBy(delta)) else m.easeCamera(CameraUpdateFactory.zoomBy(delta), 250)
    }

    fun center(): Pair<Double, Double>? = map?.cameraPosition?.target?.let { it.latitude to it.longitude }

    fun destroy() {
        if (destroyed) return
        destroyed = true
        glide?.cancel(); glide = null
        onUserMove = null; onLongPress = null; onTap = null; style = null; map = null
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
        private const val LEAD = "flare-route-lead-source"
        /** A tap this close (dp) to a line counts as on it. */
        private const val TAP_DP = 24f
        private const val ALTERNATIVES = "flare-alternatives-source"
        private const val ALTERNATIVE_COLOR = 0xff7d8794.toInt()
        private const val VEHICLE = "flare-vehicle-source"
        private const val ARROW = "flare-vehicle-arrow"
        private const val DOT = "flare-vehicle-dot"
        private const val EMPTY = """{"type":"FeatureCollection","features":[]}"""
        private const val ROUTE_COLOR = 0xff4c8dff.toInt()
        private const val ROUTE_CASING = 0xff1f4fae.toInt()
        private const val MARKER_COLOR = 0xff2f7dff.toInt()
        const val HEADING_TILT = 55.0
        /**
         * Closest zoom (tiles stop at z15 and are over-zoomed). Two zoom-button steps short of the old 20.5: closer
         * than this the map showed little but the route.
         */
        const val MAX_ZOOM = 18.5
        private const val MIN_ZOOM = 8.0
        private const val OVERVIEW_MIN_ZOOM = 4.0
        /** Default following zoom: full map; the Home widget uses [WIDGET_FOLLOW_ZOOM] (smaller view, a bit wider). */
        const val FOLLOW_ZOOM = 17.4
        const val WIDGET_FOLLOW_ZOOM = 16.9
        /** Vehicle marker diameter (44 dp in r2, 32 dp in r3, 23 dp in r4, 27 dp since the glide update). */
        const val MARKER_DP = 27
        /** [pinMarker]: extra height factor so the pinned marker matches the map-drawn one under the camera tilt. */
        private const val PIN_PERSPECTIVE = 1.12f
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
