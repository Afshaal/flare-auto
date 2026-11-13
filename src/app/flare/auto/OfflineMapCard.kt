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
import android.widget.LinearLayout
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

/**
 * Offline map card (Home grid and the full-screen map). Two renderers:
 *
 * * **Vector (MapLibre)** when the navigation pack has a display map, the "Map renderer" setting is MapLibre · 3D ("offline_map_vector") and the device has
 *   OpenGL ES 3: dark style, tilted heading-up or north-up following, blue route, vehicle arrow.
 * * **Mapsforge** otherwise (older packs, downloaded/imported .map regions, the bundled world overview), and as
 *   the fallback when MapLibre cannot start; the credit line then says why.
 *
 * The card owns GPS, the route, attribution and the map controls, so search, guidance and rerouting do not care
 * which renderer is showing.
 *
 * Full map: search, recenter, orientation (3D heading-up / north-up), voice and zoom buttons on the right and a small
 * attribution button; the host places the turn and trip cards. Home widget ([preview]): no controls or gestures,
 * always following GPS, the compact turn indicator top-left and trip bar bottom-centre while navigating; a tap
 * opens the full map.
 */
internal class OfflineMapCard(private val a:Activity,c:Context,private val sample:Boolean=false,
                              /** Home widget: read-only preview (no map gestures); a tap opens the full map. */
                              private val preview:Boolean=false):FrameLayout(c),LocationListener,NavMap {
    private val p=ProfileStore.preferences(c)
    private val t=ThemeTokens.get(c)
    private var map:MapView?=null
    private var vectorMap:VectorMapView?=null
    private var vectorBox:DoubleArray?=null
    private var bounds:org.mapsforge.core.model.BoundingBox?=null
    /** Small status pill (fallback reason, outside coverage); hidden when there is nothing to say. */
    private var statusLine:TextView?=null
    private var creditText=""
    private var marker:Circle?=null
    private var orientation:android.widget.ImageView?=null
    private var voiceButton:android.widget.ImageView?=null
    private var started=false
    private var generation=0
    private var follow=true
    private val heading=HeadingFilter()
    private var lastFix:Location?=null
    /** Installed navigation pack (Home preview: turn indicator and trip bar), null when there is none. */
    private var navPack:NavigationPack.Pack?=null
    private var homeGuidance:OfflineGuidance?=null
    private var downX=0f
    private var downY=0f
    /** Why the vector map is not showing although the pack has one (shown on the credit line). */
    private var fallbackReason:String?=null
    /** Receives every GPS fix (used by the navigation panel on the full-screen map). */
    var onLocation:((Location)->Unit)?=null
    /** Long press on the map (latitude, longitude); null leaves long presses to the map. */
    var onLongPress:((Double,Double)->Unit)?=null
    /** Full map: the search button. */
    var onSearch:(()->Unit)?=null
    /** Full map: spoken directions were switched on or off with the voice button. */
    var onVoiceChanged:(()->Unit)?=null
    private var route:NavRoute?=null
    /** Part of [route] still ahead of the vehicle (null: all of it). */
    private var ahead:DoubleArray?=null
    private var routeLayer:org.mapsforge.map.layer.overlay.Polyline?=null
    private var lastCenter=LatLong(0.0,0.0)
    private var manager:LocationManager?=null
    private val label=TextView(c).apply { gravity=Gravity.CENTER;setTextColor(t.textSecondary);textSize=15f }
    private val main=android.os.Handler(android.os.Looper.getMainLooper())
    private val density=resources.displayMetrics.density
    init {
        // Rounded card on Home; square corners on the full-screen map so it reaches the screen edges.
        val radius=if(a is OfflineMapActivity) 0f else 12*density
        background=if(radius==0f) android.graphics.drawable.ColorDrawable(t.background) else GlassDrawable(t.card,radius,t.cardBorder)
        clipToOutline=true;outlineProvider=object:android.view.ViewOutlineProvider() { override fun getOutline(v:View,o:android.graphics.Outline) { o.setRoundRect(0,0,v.width,v.height,radius) } }
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
        initMapsforge()
        val app=a.applicationContext
        val wantVector=p.getBoolean(Keys.OFFLINE_MAP_VECTOR,true)
        OfflineMapStore.reader.execute {
            val ready=(NavigationPack.status(app) as? NavigationPack.Status.Ready)?.pack
            main.post { if(generation==token) navPack=ready }
            val pack=ready?.takeIf { it.vector!=null }
            val reason=when {
                pack==null -> null
                !wantVector -> null
                !VectorMapView.supported(app) -> "3D map needs OpenGL ES 3.0"
                else -> ""
            }
            if(reason=="" && pack!=null) { main.post { if(started && generation==token) openVector(pack,token) };return@execute }
            loadMapsforge(app,token,reason)
        }
    }
    private fun initMapsforge() {
        if(initialized) return
        org.mapsforge.core.util.Parameters.SQUARE_FRAME_BUFFER=false
        org.mapsforge.core.util.Parameters.NUMBER_OF_THREADS=minOf(2,Runtime.getRuntime().availableProcessors())
        AndroidGraphicFactory.createInstance(a.application);initialized=true
    }
    /** Worker thread. */
    private fun loadMapsforge(app:Context,token:Int,reason:String?) {
        try {
            val file=OfflineMapStore.ensure(app);val source=MapFile(file);val overview=file.name=="world.map"
            main.post { if(!started || generation!=token) source.close() else { fallbackReason=reason;open(source,overview) } }
        } catch(e:Exception) { main.post { if(started && generation==token) label.text="Map unavailable\n${e.message.orEmpty()}" } }
    }
    private fun savedCenter(fallbackLat:Double,fallbackLon:Double):Pair<Double,Double> {
        val lat=p.getString(Keys.OFFLINE_MAP_LAT,"")?.toDoubleOrNull()?.takeIf { it.isFinite() && it in -85.0..85.0 } ?: fallbackLat
        val lon=p.getString(Keys.OFFLINE_MAP_LON,"")?.toDoubleOrNull()?.takeIf { it.isFinite() && it in -180.0..180.0 } ?: fallbackLon
        return lat to lon
    }
    private fun openVector(pack:NavigationPack.Pack,token:Int) {
        val box=pack.bbox
        val (lat0,lon0)=savedCenter((box[0]+box[2])/2,(box[1]+box[3])/2)
        val inside=pack.covers(lat0,lon0)
        val lat=if(inside) lat0 else (box[0]+box[2])/2;val lon=if(inside) lon0 else (box[1]+box[3])/2
        lastCenter=LatLong(lat,lon)
        val zoom=VectorMapView.zoomForSpan(SettingsValues.mapSpanMetres(p).toDouble(),width.takeIf { it>0 } ?: resources.displayMetrics.widthPixels,lat)
        val view=try {
            VectorMapView(context,pack.vector!!,box,org.maplibre.android.geometry.LatLng(lat,lon),zoom,!preview) { message ->
                main.post { if(started && generation==token && vectorMap!=null) fallBack("3D map failed: $message") }
            }
        } catch(e:Throwable) { // UnsatisfiedLinkError (no native library for this CPU), GL setup errors
            fallBack("3D map unavailable: ${e.javaClass.simpleName}");return
        }
        vectorMap=view;vectorBox=box
        if(preview) view.followZoom=VectorMapView.WIDGET_FOLLOW_ZOOM
        view.onUserMove={ follow=false }
        view.onLongPress={ lat2,lon2 -> onLongPress?.invoke(lat2,lon2) }
        // The route may have been set before this view existed (guidance restores it while the map loads).
        view.showRoute(route);ahead?.let { view.showRemaining(it) }
        addView(view,0,LayoutParams(MATCH,MATCH));label.visibility=View.GONE
        addCredit("",if(inside) null else "Map center is outside the saved coverage")
        if(!preview) addButtons(true)
        addPreviewOverlay()
        startGps(true)
    }
    /** Replaces a failed vector map by the Mapsforge renderer, keeping the reason visible. */
    private fun fallBack(reason:String) {
        val token=generation
        vectorMap?.destroy();vectorMap=null;vectorBox=null
        removeAllViews();addView(label,LayoutParams(MATCH,MATCH));label.visibility=View.VISIBLE;label.text="Loading offline map…"
        try { manager?.removeUpdates(this) } catch(_:RuntimeException) { };manager=null
        val app=a.applicationContext
        OfflineMapStore.reader.execute { loadMapsforge(app,token,reason) }
    }
    private fun open(source:MapFile,overview:Boolean) {
        try {
            val view=MapView(context);map=view
            view.model.displayModel.setFixedTileSize(256)
            view.model.displayModel.backgroundColor=t.background
            view.setBuiltInZoomControls(!preview);view.mapScaleBar.isVisible=true
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
            val (lat,lon)=savedCenter(center.latitude,center.longitude)
            val requested=LatLong(lat,lon)
            lastCenter=requested
            view.setCenter(if(bounds!!.contains(requested)) requested else center);view.setZoomLevelMax(if(overview) 5 else 20)
            view.post { if(map===view) {
                val metresPerPixel=SettingsValues.mapSpanMetres(p)/view.width.coerceAtLeast(1).toDouble()
                val z=if(overview && p.getString(Keys.OFFLINE_MAP_LAT,"").isNullOrBlank()) 2 else (ln(40075016.686*cos(Math.toRadians(lat))/(256*metresPerPixel))/ln(2.0)).roundToInt().coerceIn(2,if(overview) 5 else 19)
                view.setZoomLevel(z.toByte())
            } }
            view.addInputListener(object:org.mapsforge.map.view.InputListener { override fun onMoveEvent() { follow=false };override fun onZoomEvent()=Unit })
            addView(view,0,LayoutParams(MATCH,MATCH));label.visibility=View.GONE
            val base=if(overview) "World overview, no street detail" else ""
            addCredit(listOfNotNull(base.ifEmpty { null },fallbackReason).joinToString(" · "),if(bounds!!.contains(requested)) null else "Map center is outside the saved coverage")
            if(!preview) addButtons(false)
            addPreviewOverlay()
            drawRoute()
            startGps(false)
        } catch(e:Exception) { source.close();stop();label.text="Map unavailable\n${e.message.orEmpty()}" }
    }
    /**
     * Attribution and status. Full map: a small "i" button opening the attribution dialog (keeps
     * "© OpenStreetMap contributors" one tap away); Home widget: a tiny "© OSM" mark. Status messages (fallback
     * reason, outside coverage) appear in a small pill at the top centre only while they apply.
     */
    private fun addCredit(text:String,override:String?) {
        val status=TextView(context).apply {
            textSize=11f;setTextColor(NavColors.TEXT);background=NavColors.card(context,NavColors.CARD,10)
            setPadding((8*density).toInt(),(3*density).toInt(),(8*density).toInt(),(3*density).toInt());maxLines=2
        }
        creditText=text;statusLine=status
        setStatus(override ?: text)
        addView(status,LayoutParams(WRAP,WRAP,Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply { topMargin=(8*density).toInt() })
        if(preview) {
            addView(TextView(context).apply {
                this.text="© OSM";textSize=9f;setTextColor(0xccffffff.toInt());setShadowLayer(2f,0f,0f,0xff000000.toInt())
                contentDescription="Map data © OpenStreetMap contributors"
            },LayoutParams(WRAP,WRAP,Gravity.BOTTOM or Gravity.RIGHT).apply { setMargins(0,0,(8*density).toInt(),(4*density).toInt()) })
            return
        }
        val info=context.mapButton(MapIcon("info",NavColors.TEXT),"Map attribution",32) { attribution() }
        addView(info,LayoutParams((32*density).toInt(),(32*density).toInt(),Gravity.BOTTOM or Gravity.RIGHT).apply { setMargins(0,0,(24*density).toInt(),(12*density).toInt()) })
    }
    private fun setStatus(text:String) {
        val s=statusLine ?: return
        s.text=text;s.visibility=if(text.isBlank()) View.GONE else View.VISIBLE
    }
    private fun attribution() {
        ThemedDialogs.Builder(a).setTitle("Map attribution").setMessage("© OpenStreetMap contributors · ODbL 1.0\nhttps://www.openstreetmap.org/copyright\n" +
            (if(vectorMap!=null) "Rendered offline with MapLibre Native (BSD-2-Clause). Labels: Noto Sans, SIL OFL 1.1." else "Rendered offline using Mapsforge.") +
            " Routing: GraphHopper (Apache-2.0). License notices are included with this app.").setPositiveButton("Close",null).show()
    }
    /**
     * Home preview overlay while navigating: the compact turn indicator (arrow and distance) top-left and the trip
     * bar bottom-centre, driven by the same guidance as the full map and kept in step by this card's GPS fixes.
     * Nothing is shown without a destination: a tap opens the full map, which has search.
     */
    private fun addPreviewOverlay() {
        if(!preview) return
        val pack=navPack ?: return
        if(!p.getBoolean(Keys.OFFLINE_NAV_ACTIVE,false)) return
        val margin=(8*density).toInt()
        val panel=OfflineGuidance(a,context,pack,this,compact=true)
        homeGuidance=panel
        addView(panel.turnCard,LayoutParams(WRAP,WRAP,Gravity.TOP or Gravity.START).apply { setMargins(margin,margin,margin,0) })
        addView(panel.tripCard,LayoutParams(WRAP,WRAP,Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply { bottomMargin=margin })
        post { if(homeGuidance===panel && width>0) panel.size(width-2*margin) }
        lastFix?.let { panel.update(it) }
    }
    /** Home preview: the map never sees touches. A tap opens the full map; drags still reach the Home pager. */
    override fun onInterceptTouchEvent(e:android.view.MotionEvent):Boolean = preview && !sample || super.onInterceptTouchEvent(e)
    override fun onTouchEvent(e:android.view.MotionEvent):Boolean {
        if(!preview || sample) return super.onTouchEvent(e)
        when(e.actionMasked) {
            android.view.MotionEvent.ACTION_DOWN -> { downX=e.x;downY=e.y }
            android.view.MotionEvent.ACTION_UP -> {
                val slop=android.view.ViewConfiguration.get(context).scaledTouchSlop
                if(kotlin.math.abs(e.x-downX)<slop && kotlin.math.abs(e.y-downY)<slop) {
                    playSoundEffect(android.view.SoundEffectConstants.CLICK)
                    openFullMap()
                }
            }
        }
        return true
    }
    private fun openFullMap() {
        if(!p.getBoolean(Keys.OFFLINE_MAP_ENABLED,false)) a.startActivity(Intent(a,SettingsActivity::class.java).putExtra(SettingsActivity.EXTRA_CATEGORY,SettingsActivity.OFFLINE_MAPS))
        else a.startActivity(Intent(a,OfflineMapActivity::class.java))
    }
    /**
     * Right-hand controls of the full map: search at the top, then recenter, orientation (vector map) and voice;
     * zoom in / out as one pill above the attribution button at the bottom (Mapsforge keeps its own zoom buttons).
     */
    private fun addButtons(vector:Boolean) {
        val side=(48*density).toInt();val gap=(10*density).toInt();val edge=(16*density).toInt()
        val column=LinearLayout(context).apply { orientation=LinearLayout.VERTICAL;gravity=Gravity.CENTER_HORIZONTAL }
        column.addView(context.mapButton(MapIcon("search",NavColors.TEXT),"Search destination") {
            onSearch?.invoke() ?: android.widget.Toast.makeText(a,"Search needs the offline navigation pack",android.widget.Toast.LENGTH_SHORT).show()
        },LinearLayout.LayoutParams(side,side).apply { bottomMargin=gap*2 })
        column.addView(context.mapButton(MapIcon("recenter",NavColors.TEXT),"Recenter on your position") { recenter() },LinearLayout.LayoutParams(side,side).apply { bottomMargin=gap })
        if(vector) {
            val o=context.mapButton(MapIcon(if(headingUp()) "headingup" else "northup",NavColors.TEXT),"") { toggleOrientation() }
            orientation=o;describeOrientation()
            column.addView(o,LinearLayout.LayoutParams(side,side).apply { bottomMargin=gap })
        }
        val v=context.mapButton(MapIcon(if(voiceOn()) "volume" else "mute",NavColors.TEXT),"") { toggleVoice() }
        voiceButton=v;describeVoice()
        column.addView(v,LinearLayout.LayoutParams(side,side))
        addView(column,LayoutParams(WRAP,WRAP,Gravity.TOP or Gravity.RIGHT).apply { setMargins(0,edge,edge,0) })
        if(vector) {
            val zoom=context.mapButtonPair(context.mapButton(MapIcon("plus",NavColors.TEXT),"Zoom in") { vectorMap?.zoomBy(1.0) },
                context.mapButton(MapIcon("minus",NavColors.TEXT),"Zoom out") { vectorMap?.zoomBy(-1.0) })
            addView(zoom,LayoutParams(WRAP,WRAP,Gravity.BOTTOM or Gravity.RIGHT).apply { setMargins(0,0,edge,(56*density).toInt()) })
        }
    }
    private fun headingUp()=p.getBoolean(Keys.OFFLINE_MAP_HEADING_UP,true)
    private fun voiceOn()=p.getBoolean(Keys.OFFLINE_NAV_VOICE,true)
    private fun describeOrientation() {
        val o=orientation ?: return
        o.setImageDrawable(MapIcon(if(headingUp()) "headingup" else "northup",NavColors.TEXT))
        o.contentDescription=if(headingUp()) "Heading-up 3D view; tap for north-up" else "North-up view; tap for heading-up 3D"
    }
    private fun describeVoice() {
        val v=voiceButton ?: return
        v.setImageDrawable(MapIcon(if(voiceOn()) "volume" else "mute",NavColors.TEXT))
        v.contentDescription=if(voiceOn()) "Spoken directions on; tap to mute" else "Spoken directions muted; tap to unmute"
    }
    private fun toggleVoice() {
        p.edit().putBoolean(Keys.OFFLINE_NAV_VOICE,!voiceOn()).apply()
        describeVoice();onVoiceChanged?.invoke()
        android.widget.Toast.makeText(a,if(voiceOn()) "Spoken directions on" else "Spoken directions muted",android.widget.Toast.LENGTH_SHORT).show()
    }
    private fun toggleOrientation() {
        p.edit().putBoolean(Keys.OFFLINE_MAP_HEADING_UP,!headingUp()).apply()
        describeOrientation()
        val v=vectorMap ?: return
        if(follow && lastFix!=null) v.follow(headingUp(),true) else if(!headingUp()) v.flatten()
    }
    private fun recenter() {
        follow=true
        vectorMap?.let { v -> if(lastFix!=null) v.follow(headingUp(),true);return }
        map?.let { view -> marker?.position?.let { view.setCenter(it) } ?: view.setCenter(lastCenter) }
    }
    private fun startGps(vector:Boolean) {
        // The Home widget always follows the car (it has no gestures or recenter button).
        follow=preview || p.getBoolean(Keys.OFFLINE_MAP_FOLLOW,true)
        if(context.checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION)!=android.content.pm.PackageManager.PERMISSION_GRANTED) return
        manager=context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        try {
            manager?.getLastKnownLocation(LocationManager.GPS_PROVIDER)?.let { onLocationChanged(it) }
            // Heading-up needs every fix for a smooth camera; the Mapsforge dot is fine at 2 s / 5 m.
            if(vector) manager?.requestLocationUpdates(LocationManager.GPS_PROVIDER,1000L,0f,this)
            else manager?.requestLocationUpdates(LocationManager.GPS_PROVIDER,2000L,5f,this)
        } catch(_:RuntimeException) { }
    }
    override fun onLocationChanged(location:Location) {
        if(!location.latitude.isFinite() || !location.longitude.isFinite() || location.longitude !in -180.0..180.0) return
        onLocation?.invoke(location)
        homeGuidance?.update(location)
        if(vectorMap==null) lastFix=location
        vectorMap?.let { v -> vectorFix(v,location);return }
        val view=map ?: return
        val point=LatLong(location.latitude.coerceIn(-85.0,85.0),location.longitude)
        if(bounds?.contains(point)==false) { setStatus("GPS position is outside the saved coverage");return }
        setStatus(creditText)
        if(marker==null) {
            val fill=AndroidGraphicFactory.INSTANCE.createPaint().apply { color=0xff39bed8.toInt() }
            marker=org.mapsforge.map.layer.overlay.FixedPixelCircle(point,7f,fill,null);view.layerManager.layers.add(marker)
        } else marker?.setLatLong(point)
        if(follow) view.setCenter(point)
        marker?.requestRedraw()
    }
    private fun vectorFix(v:VectorMapView,fix:Location) {
        val box=vectorBox ?: return
        if(!(fix.latitude in box[0]..box[2] && fix.longitude in box[1]..box[3])) { setStatus("GPS position is outside the saved coverage");return }
        setStatus(creditText)
        val first=lastFix==null
        lastFix=fix
        val direction=heading.update(fix.latitude,fix.longitude,if(fix.hasBearing()) fix.bearing else null,
            if(fix.hasSpeed()) fix.speed else null,if(fix.hasAccuracy()) fix.accuracy else null)
        v.setVehicle(fix.latitude,fix.longitude,direction)
        if(follow) v.follow(headingUp(),!first)
    }
    @Deprecated("Deprecated in Java") override fun onStatusChanged(provider:String?,status:Int,extras:Bundle?)=Unit
    override fun onProviderEnabled(provider:String)=Unit
    override fun onProviderDisabled(provider:String)=Unit
    fun stop() {
        started=false;generation++
        try { manager?.removeUpdates(this) } catch(_:RuntimeException) { };manager=null
        homeGuidance?.close();homeGuidance=null
        if(preview) { route=null;ahead=null } // the preview shows only the route of the current navigation
        vectorMap?.destroy();vectorMap=null;vectorBox=null;lastFix=null;heading.reset();orientation=null;voiceButton=null
        map?.destroyAll();map=null;marker=null;routeLayer=null;bounds=null;statusLine=null
        removeAllViews();if(label.parent==null) addView(label,LayoutParams(MATCH,MATCH));label.visibility=View.VISIBLE
    }
    /** Shows [path] as a line under the position marker, or removes it (null). Survives map reloads. */
    override fun showRoute(path:NavRoute?) { route=path;ahead=null;drawRoute() }
    /** Removes the travelled part of the line: only the route ahead of the vehicle stays drawn. */
    override fun routeProgress(segment:Int,along:Double) {
        val path=route ?: return
        val pts=NavGeometry.remaining(path,segment,along)
        ahead=pts
        vectorMap?.let { it.showRemaining(pts);return }
        routeLayer?.let { line -> line.setPoints(List(pts.size/2) { LatLong(pts[it*2],pts[it*2+1]) });line.requestRedraw() }
    }
    private fun drawRoute() {
        vectorMap?.let { v -> v.showRoute(route);ahead?.let { v.showRemaining(it) };return }
        val view=map ?: return
        val layers=view.layerManager.layers
        routeLayer?.let { layers.remove(it) };routeLayer=null
        val path=route ?: return
        val stroke=AndroidGraphicFactory.INSTANCE.createPaint().apply {
            color=ROUTE_COLOR;strokeWidth=6*density
            setStyle(org.mapsforge.core.graphics.Style.STROKE);setStrokeCap(org.mapsforge.core.graphics.Cap.ROUND);setStrokeJoin(org.mapsforge.core.graphics.Join.ROUND)
        }
        val line=org.mapsforge.map.layer.overlay.Polyline(stroke,AndroidGraphicFactory.INSTANCE)
        val pts=ahead ?: path.points
        line.setPoints(List(pts.size/2) { LatLong(pts[it*2],pts[it*2+1]) })
        val below=marker?.let { layers.indexOf(it) } ?: -1
        if(below>=0) layers.add(below,line) else layers.add(line)
        routeLayer=line
    }
    /** Current map centre (or the configured centre before the map has opened). */
    override fun center():Pair<Double,Double> = vectorMap?.center() ?: (map?.model?.mapViewPosition?.center ?: lastCenter).let { it.latitude to it.longitude }
    companion object {
        private var initialized=false
        private const val ROUTE_COLOR=0xcc39bed8.toInt()
    }
}
