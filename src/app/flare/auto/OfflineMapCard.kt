package app.flare.auto

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import org.mapsforge.core.model.LatLong
import org.mapsforge.map.android.graphics.AndroidGraphicFactory
import org.mapsforge.map.android.view.MapView
import org.mapsforge.map.layer.cache.InMemoryTileCache
import org.mapsforge.map.layer.overlay.Circle
import org.mapsforge.map.layer.renderer.TileRendererLayer
import org.mapsforge.map.reader.MapFile
import org.mapsforge.map.rendertheme.StreamRenderTheme
import org.mapsforge.map.rendertheme.internal.MapsforgeThemes
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.roundToInt

internal class OfflineMapCard(private val a:Activity,c:Context,private val sample:Boolean=false):FrameLayout(c),LocationListener {
    private val p=ProfileStore.preferences(c)
    private val t=ThemeTokens.get(c)
    private var map:MapView?=null
    private var bounds:org.mapsforge.core.model.BoundingBox?=null
    private var statusLine:TextView?=null
    private var creditText=""
    private var marker:Circle?=null
    private var started=false
    private var generation=0
    private var follow=true
    /** Receives every GPS fix (used by the navigation panel on the full-screen map). */
    var onLocation:((Location)->Unit)?=null
    /** Long press on the map (latitude, longitude); null leaves long presses to the map. */
    var onLongPress:((Double,Double)->Unit)?=null
    private var route:NavRoute?=null
    private var routeLayer:org.mapsforge.map.layer.overlay.Polyline?=null
    private var lastCenter=LatLong(0.0,0.0)
    private var manager:LocationManager?=null
    private val label=TextView(c).apply { gravity=Gravity.CENTER;setTextColor(t.textSecondary);textSize=15f }
    private val main=android.os.Handler(android.os.Looper.getMainLooper())
    init {
        background=GlassDrawable(t.card,12*resources.displayMetrics.density,t.cardBorder)
        clipToOutline=true;outlineProvider=object:android.view.ViewOutlineProvider() { override fun getOutline(v:View,o:android.graphics.Outline) { o.setRoundRect(0,0,v.width,v.height,12*resources.displayMetrics.density) } }
        addView(label,LayoutParams(MATCH,MATCH))
        label.text=if(sample) "Offline map\nLocal vector map + GPS" else if(!p.getBoolean(Keys.OFFLINE_MAP_ENABLED,false)) "Offline maps disabled\nEnable in Launcher settings" else "Loading offline map…"
        label.setOnClickListener {
            if(!sample) {
                if(!p.getBoolean(Keys.OFFLINE_MAP_ENABLED,false)) a.startActivity(Intent(a,SettingsActivity::class.java).putExtra(SettingsActivity.EXTRA_CATEGORY,SettingsActivity.OFFLINE_MAPS))
                else if(a !is OfflineMapActivity) a.startActivity(Intent(a,OfflineMapActivity::class.java))
            }
        }
    }
    override fun onAttachedToWindow() { super.onAttachedToWindow();start() }
    override fun onDetachedFromWindow() { stop();super.onDetachedFromWindow() }
    fun start() {
        if(started || sample || !isAttachedToWindow || !p.getBoolean(Keys.OFFLINE_MAP_ENABLED,false)) return
        started=true;val token=++generation
        if(!initialized) {
            org.mapsforge.core.util.Parameters.SQUARE_FRAME_BUFFER=false
            org.mapsforge.core.util.Parameters.NUMBER_OF_THREADS=minOf(2,Runtime.getRuntime().availableProcessors())
            AndroidGraphicFactory.createInstance(a.application);initialized=true
        }
        val app=a.applicationContext
        OfflineMapStore.reader.execute {
            try {
                val file=OfflineMapStore.ensure(app);val source=MapFile(file);val overview=file.name=="world.map"
                main.post { if(!started || generation!=token) source.close() else open(source,overview) }
            } catch(e:Exception) { main.post { if(started && generation==token) label.text="Map unavailable\n${e.message.orEmpty()}" } }
        }
    }
    private fun open(source:MapFile,overview:Boolean) {
        try {
            val view=MapView(context);map=view
            view.model.displayModel.setFixedTileSize(256)
            view.model.displayModel.backgroundColor=t.background
            view.setBuiltInZoomControls(true);view.mapScaleBar.isVisible=true
            val cache=InMemoryTileCache(64)
            view.addOnLayoutChangeListener { _,l,t,r,b,_,_,_,_->
                val overdraw=view.model.frameBufferModel.overdrawFactor
                val tiles=((kotlin.math.ceil((r-l)*overdraw/256).toInt()+2)*(kotlin.math.ceil((b-t)*overdraw/256).toInt()+2)).coerceIn(16,96)
                if(cache.capacity!=tiles) cache.setCapacity(tiles)
            }
            val layer=TileRendererLayer(cache,source,view.model.mapViewPosition,AndroidGraphicFactory.INSTANCE)
            val dark=android.graphics.Color.red(t.background)<128
            layer.setXmlRenderTheme(if(dark) StreamRenderTheme("",context.assets.open("maps/dark.xml")) else MapsforgeThemes.DEFAULT)
            view.layerManager.layers.add(layer)
            // Invisible layer that turns a long press on the map into a destination (navigation panel only).
            view.layerManager.layers.add(object:org.mapsforge.map.layer.Layer() {
                override fun draw(b:org.mapsforge.core.model.BoundingBox?,z:Byte,c:org.mapsforge.core.graphics.Canvas?,o:org.mapsforge.core.model.Point?,r:org.mapsforge.core.model.Rotation?)=Unit
                // Mapsforge passes null for positions it did not compute (seen: layerXY), so every parameter is nullable.
                override fun onLongPress(at:LatLong?,layerXY:org.mapsforge.core.model.Point?,tapXY:org.mapsforge.core.model.Point?):Boolean {
                    val pick=onLongPress ?: return false
                    if(at==null) return false
                    post { pick(at.latitude,at.longitude) };return true
                }
            })
            bounds=source.boundingBox()
            val center=bounds!!.centerPoint
            val lat=p.getString(Keys.OFFLINE_MAP_LAT,"")?.toDoubleOrNull()?.takeIf { it.isFinite() && it in -85.0..85.0 } ?: center.latitude
            val lon=p.getString(Keys.OFFLINE_MAP_LON,"")?.toDoubleOrNull()?.takeIf { it.isFinite() && it in -180.0..180.0 } ?: center.longitude
            val requested=LatLong(lat,lon)
            lastCenter=requested
            view.setCenter(if(bounds!!.contains(requested)) requested else center);view.setZoomLevelMax(if(overview) 5 else 19)
            view.post { if(map===view) {
                val metresPerPixel=p.getInt(Keys.OFFLINE_MAP_SPAN,10).coerceIn(2,200)*1000.0/view.width.coerceAtLeast(1)
                val z=if(overview && p.getString(Keys.OFFLINE_MAP_LAT,"").isNullOrBlank()) 2 else (ln(40075016.686*cos(Math.toRadians(lat))/(256*metresPerPixel))/ln(2.0)).roundToInt().coerceIn(2,if(overview) 5 else 19)
                view.setZoomLevel(z.toByte())
            } }
            view.addInputListener(object:org.mapsforge.map.view.InputListener { override fun onMoveEvent() { follow=false };override fun onZoomEvent()=Unit })
            addView(view,0,LayoutParams(MATCH,MATCH));label.visibility=View.GONE
            val credit=TextView(context).apply {
                text=if(overview) "© OpenStreetMap contributors · World overview (no street detail)" else "© OpenStreetMap contributors · Offline"
                textSize=10f;setTextColor(t.textPrimary);setBackgroundColor((t.background and 0xffffff) or 0xdd000000.toInt())
            }
            credit.setOnClickListener { ThemedDialogs.Builder(a).setTitle("Map attribution").setMessage("© OpenStreetMap contributors · ODbL 1.0\nhttps://www.openstreetmap.org/copyright\nRendered offline using Mapsforge. License notices are included with this app.").setPositiveButton("Close",null).show() }
            creditText=credit.text.toString();statusLine=credit
            if(!bounds!!.contains(requested)) credit.text="© OpenStreetMap contributors · Center outside saved coverage"
            addView(credit,LayoutParams(WRAP,WRAP,Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL)) // scale bar bottom-left, zoom buttons bottom-right
            val recenter=TextView(context).apply {
                text="◎";textSize=24f;gravity=Gravity.CENTER;setTextColor(t.textPrimary);contentDescription="Recenter map"
                setBackgroundColor((t.card and 0xffffff) or 0xdd000000.toInt())
                setOnClickListener { follow=true;marker?.position?.let { view.setCenter(it) } ?: view.setCenter(LatLong(lat,lon)) }
            }
            val side=(40*resources.displayMetrics.density).toInt();addView(recenter,LayoutParams(side,side,Gravity.TOP or Gravity.RIGHT))
            drawRoute()
            follow=p.getBoolean(Keys.OFFLINE_MAP_FOLLOW,true)
            if(context.checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION)==android.content.pm.PackageManager.PERMISSION_GRANTED) {
                manager=context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
                try {
                    manager?.getLastKnownLocation(LocationManager.GPS_PROVIDER)?.let { onLocationChanged(it) }
                    manager?.requestLocationUpdates(LocationManager.GPS_PROVIDER,2000L,5f,this)
                } catch(_:RuntimeException) { }
            }
        } catch(e:Exception) { source.close();stop();label.text="Map unavailable\n${e.message.orEmpty()}" }
    }
    override fun onLocationChanged(location:Location) {
        if(!location.latitude.isFinite() || !location.longitude.isFinite() || location.longitude !in -180.0..180.0) return
        onLocation?.invoke(location)
        val view=map ?: return
        val point=LatLong(location.latitude.coerceIn(-85.0,85.0),location.longitude)
        if(bounds?.contains(point)==false) { statusLine?.text="© OpenStreetMap contributors · GPS outside saved coverage";return }
        statusLine?.text=creditText
        if(marker==null) {
            val fill=AndroidGraphicFactory.INSTANCE.createPaint().apply { color=0xff39bed8.toInt() }
            marker=org.mapsforge.map.layer.overlay.FixedPixelCircle(point,7f,fill,null);view.layerManager.layers.add(marker)
        } else marker?.setLatLong(point)
        if(follow) view.setCenter(point)
        marker?.requestRedraw()
    }
    @Deprecated("Deprecated in Java") override fun onStatusChanged(provider:String?,status:Int,extras:Bundle?)=Unit
    override fun onProviderEnabled(provider:String)=Unit
    override fun onProviderDisabled(provider:String)=Unit
    fun stop() {
        started=false;generation++
        try { manager?.removeUpdates(this) } catch(_:RuntimeException) { };manager=null
        map?.destroyAll();map=null;marker=null;routeLayer=null;bounds=null;statusLine=null
        removeAllViews();if(label.parent==null) addView(label,LayoutParams(MATCH,MATCH));label.visibility=View.VISIBLE
    }
    /** Shows [path] as a line under the position marker, or removes it (null). Survives map reloads. */
    fun showRoute(path:NavRoute?) { route=path;drawRoute() }
    private fun drawRoute() {
        val view=map ?: return
        val layers=view.layerManager.layers
        routeLayer?.let { layers.remove(it) };routeLayer=null
        val path=route ?: return
        val stroke=AndroidGraphicFactory.INSTANCE.createPaint().apply {
            color=ROUTE_COLOR;strokeWidth=6*resources.displayMetrics.density
            setStyle(org.mapsforge.core.graphics.Style.STROKE);setStrokeCap(org.mapsforge.core.graphics.Cap.ROUND);setStrokeJoin(org.mapsforge.core.graphics.Join.ROUND)
        }
        val line=org.mapsforge.map.layer.overlay.Polyline(stroke,AndroidGraphicFactory.INSTANCE)
        line.setPoints(List(path.points.size/2) { LatLong(path.points[it*2],path.points[it*2+1]) })
        val below=marker?.let { layers.indexOf(it) } ?: -1
        if(below>=0) layers.add(below,line) else layers.add(line)
        routeLayer=line
    }
    /** Current map centre (or the configured centre before the map has opened). */
    fun center():Pair<Double,Double> = (map?.model?.mapViewPosition?.center ?: lastCenter).let { it.latitude to it.longitude }
    companion object {
        private var initialized=false
        private const val ROUTE_COLOR=0xcc39bed8.toInt()
    }
}
