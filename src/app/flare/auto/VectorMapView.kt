package app.flare.auto

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.widget.FrameLayout
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
    private val onFailed: (String) -> Unit
) : FrameLayout(context) {
    private val density = resources.displayMetrics.density
    private val view: MapView
    private var map: MapLibreMap? = null
    private var style: Style? = null
    private var route: NavRoute? = null
    private var position: LatLng? = null
    private var heading: Double? = null
    private var destroyed = false
    private var followZoomSet = false
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
        m.setMinZoomPreference(8.0)
        m.setMaxZoomPreference(19.5)
        m.setLatLngBoundsForCameraTarget(LatLngBounds.from(bbox[2], bbox[3], bbox[0], bbox[1]))
        m.moveCamera(CameraUpdateFactory.newCameraPosition(CameraPosition.Builder().target(start).zoom(startZoom).build()))
        m.addOnCameraMoveStartedListener { reason -> if (reason == MapLibreMap.OnCameraMoveStartedListener.REASON_API_GESTURE) onUserMove?.invoke() }
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
        if (below != null) { s.addLayerBelow(casing, below); s.addLayerBelow(line, below) } else { s.addLayer(casing); s.addLayer(line) }
        s.addLayer(SymbolLayer("flare-vehicle", VEHICLE).withProperties(
            PropertyFactory.iconImage(Expression.get("icon")), PropertyFactory.iconRotate(Expression.get("bearing")),
            PropertyFactory.iconRotationAlignment(Property.ICON_ROTATION_ALIGNMENT_MAP),
            PropertyFactory.iconPitchAlignment(Property.ICON_PITCH_ALIGNMENT_MAP),
            PropertyFactory.iconAllowOverlap(true), PropertyFactory.iconIgnorePlacement(true)))
        drawRoute()
        drawVehicle()
    }

    fun showRoute(path: NavRoute?) { route = path; drawRoute() }

    private fun drawRoute() {
        val source = style?.getSourceAs<GeoJsonSource>(ROUTE) ?: return
        val path = route
        if (path == null || path.points.size < 4) { source.setGeoJson(EMPTY); return }
        val coords = StringBuilder()
        for (i in 0 until path.points.size / 2) {
            if (i > 0) coords.append(',')
            coords.append('[').append(fmt(path.points[i * 2 + 1])).append(',').append(fmt(path.points[i * 2])).append(']')
        }
        source.setGeoJson("""{"type":"Feature","properties":{},"geometry":{"type":"LineString","coordinates":[$coords]}}""")
    }

    /** New GPS position and smoothed heading (null: direction unknown, shown as a plain dot). */
    fun setVehicle(lat: Double, lon: Double, heading: Double?) {
        position = LatLng(lat, lon); this.heading = heading; drawVehicle()
    }

    private fun drawVehicle() {
        val source = style?.getSourceAs<GeoJsonSource>(VEHICLE) ?: return
        val at = position ?: run { source.setGeoJson(EMPTY); return }
        val icon = if (heading == null) DOT else ARROW
        source.setGeoJson("""{"type":"Feature","properties":{"icon":"$icon","bearing":${fmt(heading ?: 0.0)}},""" +
            """"geometry":{"type":"Point","coordinates":[${fmt(at.longitude)},${fmt(at.latitude)}]}}""")
    }

    /**
     * Moves the camera to the vehicle. Heading-up: rotated to [heading], tilted, the car in the lower part of the
     * view with the road ahead visible. North-up: flat, north at the top, car centred.
     */
    fun follow(headingUp: Boolean, animate: Boolean) {
        val m = map ?: return
        val at = position ?: return
        val current = m.cameraPosition
        val builder = CameraPosition.Builder().target(at)
        if (headingUp) {
            builder.bearing(heading ?: current.bearing).tilt(HEADING_TILT).padding(0.0, height * 0.42, 0.0, 0.0)
            if (!followZoomSet && current.zoom < 15.5) builder.zoom(16.5)
        } else {
            builder.bearing(0.0).tilt(0.0).padding(0.0, 0.0, 0.0, 0.0)
        }
        followZoomSet = true
        val update = CameraUpdateFactory.newCameraPosition(builder.build())
        if (animate) m.easeCamera(update, FOLLOW_MS, false) else m.moveCamera(update)
    }

    /** Flat north-up view around the current centre (leaving heading-up while not following). */
    fun flatten() {
        val m = map ?: return
        m.easeCamera(CameraUpdateFactory.newCameraPosition(CameraPosition.Builder(m.cameraPosition).bearing(0.0).tilt(0.0)
            .padding(0.0, 0.0, 0.0, 0.0).build()), 400)
    }

    fun zoomBy(delta: Double) { map?.easeCamera(CameraUpdateFactory.zoomBy(delta), 250) }

    fun center(): Pair<Double, Double>? = map?.cameraPosition?.target?.let { it.latitude to it.longitude }

    fun destroy() {
        if (destroyed) return
        destroyed = true
        onUserMove = null; onLongPress = null; style = null; map = null
        try { view.onPause(); view.onStop(); view.onDestroy() } catch (_: RuntimeException) { }
        removeAllViews()
    }

    fun lowMemory() { view.onLowMemory() }

    /** Blue disc with a white rim; with [arrow], a white navigation arrow pointing up (north before rotation). */
    private fun marker(arrow: Boolean): Bitmap {
        val size = (44 * density).toInt()
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val r = size / 2f
        paint.color = 0x55000000
        c.drawCircle(r, r + density, r - 2 * density, paint)
        paint.color = 0xffffffff.toInt()
        c.drawCircle(r, r, r - 3 * density, paint)
        paint.color = MARKER_COLOR
        c.drawCircle(r, r, r - 6 * density, paint)
        paint.color = 0xffffffff.toInt()
        if (arrow) {
            val s = r * 0.62f
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
        private const val VEHICLE = "flare-vehicle-source"
        private const val ARROW = "flare-vehicle-arrow"
        private const val DOT = "flare-vehicle-dot"
        private const val EMPTY = """{"type":"FeatureCollection","features":[]}"""
        private const val ROUTE_COLOR = 0xff4c8dff.toInt()
        private const val ROUTE_CASING = 0xff1f4fae.toInt()
        private const val MARKER_COLOR = 0xff2f7dff.toInt()
        const val HEADING_TILT = 55.0
        private const val FOLLOW_MS = 950

        /** MapLibre needs OpenGL ES 3.0. */
        fun supported(context: Context): Boolean {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? android.app.ActivityManager ?: return false
            return am.deviceConfigurationInfo.reqGlEsVersion >= 0x30000
        }

        /** MapLibre zoom (512 px tiles) showing [km] across [widthPx] at [lat]. */
        fun zoomForSpan(km: Int, widthPx: Int, lat: Double): Double {
            val metresPerPixel = km * 1000.0 / widthPx.coerceAtLeast(1)
            return (Math.log(40075016.686 * Math.cos(Math.toRadians(lat)) / (512 * metresPerPixel)) / Math.log(2.0)).coerceIn(8.0, 19.0)
        }
    }
}
