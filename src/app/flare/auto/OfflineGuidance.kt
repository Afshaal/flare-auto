package app.flare.auto

import android.app.Activity
import android.content.Context
import android.location.Location
import android.os.SystemClock
import android.speech.tts.TextToSpeech

/** What guidance needs from the map it runs on ([OfflineMapCard]). */
internal interface NavMap {
    /** Shows [path] (null removes the line). */
    fun showRoute(path: NavRoute?)
    /** The vehicle is [along] metres from the start, on [segment]: hide the travelled part of the line. */
    fun routeProgress(segment: Int, along: Double)
    /** Current map centre (route previews without GPS, search reference point). */
    fun center(): Pair<Double, Double>
}

/**
 * Turn-by-turn guidance over an offline map: route calculation, progress along the route from GPS fixes
 * ([update]), off-route recalculation and optional offline spoken directions. It drives two views the host
 * places over the map: [turnCard] (next turn: arrow, distance, road name; or a status line) and [tripCard]
 * (arrival time, remaining time and distance). [compact] is the Home widget: no road name, one-line trip bar.
 */
internal class OfflineGuidance(private val a:Activity,c:Context,private val pack:NavigationPack.Pack,private val map:NavMap,private val compact:Boolean=false) {
    private val p=ProfileStore.preferences(c)
    private val engine=OfflineRouting(c)
    private val saved=SavedPlaces(p)
    private var location:Location?=null
    private var route:NavRoute?=null
    private var segment=0
    private var target:OfflineDestinationPicker.Place?=null
    private var calculating=false
    private var preview=false
    private var closed=false
    private var lastReroute=0L
    private var offRoute=0
    private var spoken=-1
    private var voice:TextToSpeech?=null
    private var voiceReady=false
    private var pendingSpeech:String?=null
    private var searchDialog:android.app.Dialog?=null
    private val main=android.os.Handler(android.os.Looper.getMainLooper())
    val turnCard=TurnCard(c,compact)
    val tripCard=TripCard(c,compact)
    /** Called whenever guidance starts or stops (the host may show or hide related controls). */
    var onActiveChanged:((Boolean)->Unit)?=null

    init {
        tripCard.onEnd={ stopRoute() }
        // Star the destination; a restored destination only knows its name, so reuse the details of the recent search.
        tripCard.onStar={ target?.let { t-> tripCard.starred(saved.toggleStar(saved.recents().firstOrNull { SavedPlaces.same(it,t) } ?: t)) } }
        if(!compact) turnCard.setOnClickListener {
            if(preview && route!=null && target!=null) ThemedDialogs.Builder(a).setTitle("Route preview")
                .setMessage("Start guidance to ${target?.name} from your GPS position?")
                .setPositiveButton("Start") { _,_->target?.let { choose(it) } }.setNegativeButton("Cancel",null).show()
            // A destination without a route yet (waiting for GPS, route unavailable): the trip card and its End
            // button are hidden, so the turn card offers to stop.
            else if(target!=null && route==null) ThemedDialogs.Builder(a).setTitle("Navigation")
                .setMessage("Stop navigation to ${target?.name}?")
                .setPositiveButton("Stop") { _,_->stopRoute() }.setNegativeButton("Cancel",null).show()
        }
        if(p.getBoolean(Keys.OFFLINE_NAV_ACTIVE,false)) {
            val lat=p.getString(Keys.OFFLINE_NAV_LAT,"")?.toDoubleOrNull();val lon=p.getString(Keys.OFFLINE_NAV_LON,"")?.toDoubleOrNull()
            if(lat!=null && lon!=null && pack.covers(lat,lon)) {
                val place=OfflineDestinationPicker.Place(p.getString(Keys.OFFLINE_NAV_NAME,"Destination") ?: "Destination","saved",lat,lon)
                target=place;turnCard.message("Waiting for GPS · ${place.name}");tripCard.starred(saved.isStarred(place))
                // Same destination as the route shown elsewhere in this process (full map <-> Home widget): reuse it.
                val cached=cachedRoute
                if(cached!=null && cachedKey==key(pack,lat,lon)) {
                    route=cached;map.showRoute(cached);turnCard.message("Continue towards ${place.name}")
                    tripCard.show(cached.distance,cached.millis,p.clock24)
                }
            }
        }
    }

    /** True while a destination is set (guiding, calculating, previewing or waiting for GPS). */
    val active:Boolean get() = target!=null

    fun size(width:Int) { turnCard.setMaxWidth(width) }

    /** Opens destination search; distances are measured from the GPS fix, or the map centre without one. */
    fun search() {
        if(closed) return
        val fix=location?.takeIf { recent(it) }
        val reference=fix?.let { it.latitude to it.longitude } ?: map.center()
        searchDialog=OfflineDestinationPicker.show(a,pack,reference,target,::choose)
    }

    fun choose(place:OfflineDestinationPicker.Place) {
        if(closed) return
        if(!pack.covers(place.lat,place.lon)) { turnCard.message("Outside ${pack.region} driving coverage");return }
        fun begin(lat:Double,lon:Double,asPreview:Boolean) {
            target=place;route=null;map.showRoute(null);tripCard.hide();tripCard.starred(saved.isStarred(place))
            p.edit().putBoolean(Keys.OFFLINE_NAV_ACTIVE,false).remove(Keys.OFFLINE_NAV_VIA).apply();calculate(lat,lon,asPreview,true);onActiveChanged?.invoke(true)
        }
        val fix=location
        if(fix!=null && recent(fix)) begin(fix.latitude,fix.longitude,false)
        else ThemedDialogs.Builder(a).setTitle("Waiting for GPS").setMessage("A recent GPS fix is needed for driving guidance. You can preview a route from the map center while waiting.")
            .setPositiveButton("Preview route") { _,_->val start=map.center();begin(start.first,start.second,true) }.setNegativeButton("Cancel",null).show()
    }

    /**
     * [options]: a new destination; long trips then offer the fastest route and its alternatives to choose from.
     * Otherwise (reroute, restore) the route goes through the saved point of the chosen option, if any.
     */
    private fun calculate(lat:Double,lon:Double,asPreview:Boolean,options:Boolean=false) {
        val dest=target ?: return
        calculating=true;preview=asPreview
        val failed={ error:Throwable->
            calculating=false;turnCard.message("Route unavailable · "+(error.message ?: "no local driving route found"));tripCard.hide()
        }
        if(options) {
            turnCard.message("Finding routes to ${dest.name}…")
            engine.routes(lat,lon,dest.lat,dest.lon,pack) { result->
                if(closed || target!==dest) return@routes
                result.fold({ found-> if(found.size<2) apply(found.first(),dest,asPreview) else offer(found,dest,asPreview) },failed)
            }
            return
        }
        turnCard.message("Calculating route to ${dest.name}…")
        engine.route(lat,lon,dest.lat,dest.lon,pack,RouteOptions.decode(p.getString(Keys.OFFLINE_NAV_VIA,null))) { result->
            if(closed) return@route
            result.fold({ apply(it,dest,asPreview) },failed)
        }
    }

    /** Lets the driver pick one of [found] (fastest first); the fastest is shown meanwhile and used if dismissed. */
    private fun offer(found:List<NavRoute>,dest:OfflineDestinationPicker.Place,asPreview:Boolean) {
        map.showRoute(found.first());turnCard.message("Choose a route to ${dest.name}")
        var picked=false
        fun pick(i:Int) {
            if(picked || closed || target!==dest) return
            picked=true
            val via=if(i==0) null else found[i].via ?: RouteOptions.viaPoint(found[i].points,found[0].points)
            p.edit().apply { if(via==null) remove(Keys.OFFLINE_NAV_VIA) else putString(Keys.OFFLINE_NAV_VIA,RouteOptions.encode(via)) }.apply()
            apply(found[i],dest,asPreview)
        }
        ThemedDialogs.Builder(a).setTitle("Routes to ${dest.name}")
            .setItems(RouteOptions.summaries(found).toTypedArray()) { _,i->pick(i) }
            .setOnDismissListener { main.post { pick(0) } }.show() // after any item click: back or outside = fastest
    }

    private fun apply(path:NavRoute,dest:OfflineDestinationPicker.Place,asPreview:Boolean) {
        calculating=false
        route=path;segment=0;spoken=-1;offRoute=0;map.showRoute(path)
        if(asPreview) { cachedRoute=null;cachedKey=null } else { cachedRoute=path;cachedKey=key(pack,dest.lat,dest.lon) }
        p.edit().putBoolean(Keys.OFFLINE_NAV_ACTIVE,!asPreview).putString(Keys.OFFLINE_NAV_LAT,dest.lat.toString()).putString(Keys.OFFLINE_NAV_LON,dest.lon.toString()).putString(Keys.OFFLINE_NAV_NAME,dest.name).apply()
        turnCard.message(if(asPreview) "Route preview · ${dest.name}" else "Continue towards ${dest.name}")
        tripCard.show(path.distance,path.millis,p.clock24)
        if(!asPreview) { initVoice();location?.let { update(it) } }
    }

    private fun recent(fix:Location):Boolean {
        val age=if(fix.elapsedRealtimeNanos>0) (SystemClock.elapsedRealtimeNanos()-fix.elapsedRealtimeNanos)/1000000 else System.currentTimeMillis()-fix.time
        return age in -1000..120000 && (!fix.hasAccuracy() || fix.accuracy<=100)
    }

    fun update(fix:Location) {
        if(closed || !fix.latitude.isFinite() || !fix.longitude.isFinite()) return
        location=fix
        if(!recent(fix)) { if(route!=null && !preview) turnCard.message("Waiting for an accurate GPS fix");return }
        if(route==null) {
            if(!calculating && target!=null && p.getBoolean(Keys.OFFLINE_NAV_ACTIVE,false)) calculate(fix.latitude,fix.longitude,false)
            return
        }
        if(preview || calculating) return
        // Past the chosen option's distinctive stretch: reroutes may take the fastest way from here on.
        RouteOptions.decode(p.getString(Keys.OFFLINE_NAV_VIA,null))?.let { via->
            if(NavGeometry.distance(fix.latitude,fix.longitude,via.first,via.second)<VIA_PASSED_M) p.edit().remove(Keys.OFFLINE_NAV_VIA).apply()
        }
        val path=route ?: return
        val position=NavGeometry.nearest(path,fix.latitude,fix.longitude,segment);segment=position.segment
        val threshold=maxOf(50.0,if(fix.hasAccuracy()) fix.accuracy*2.0 else 50.0)
        if(position.away>threshold) {
            offRoute++
            if(offRoute>=2 && SystemClock.elapsedRealtime()-lastReroute>15000) { lastReroute=SystemClock.elapsedRealtime();calculate(fix.latitude,fix.longitude,false) }
            else turnCard.message("Off route · recalculating")
            return
        }
        offRoute=0
        map.routeProgress(position.segment,position.along)
        val remaining=(path.cumulative.last()-position.along).coerceAtLeast(0.0)
        if(remaining<35 && NavGeometry.distance(fix.latitude,fix.longitude,path.points[path.points.size-2],path.points.last())<60) {
            arrived();return
        }
        val next=path.steps.indexOfFirst { it.at>position.along+8 }.let { if(it<0) path.steps.lastIndex else it }
        val step=path.steps.getOrNull(next) ?: return
        val metres=(step.at-position.along).coerceAtLeast(0.0)
        val road=if(step.sign==TurnArrow.FINISH) target?.name.orEmpty() else TripFormat.road(step)
        turnCard.turn(step.sign,metres,road)
        tripCard.show(remaining,(path.millis*(remaining/path.cumulative.last().coerceAtLeast(1.0))).toLong(),p.clock24)
        if(next!=spoken && metres<200) { spoken=next;val said=speakable(step.text);speak(if(metres>50) "In ${metres.toInt()} metres, $said" else said) }
    }

    private fun arrived() {
        val name=target?.name.orEmpty()
        speak("You have arrived near your destination")
        cachedRoute=null;cachedKey=null;route=null;target=null;preview=false
        p.edit().putBoolean(Keys.OFFLINE_NAV_ACTIVE,false).remove(Keys.OFFLINE_NAV_VIA).apply()
        map.showRoute(null);tripCard.hide();turnCard.message("You have arrived · $name")
        onActiveChanged?.invoke(false)
        main.postDelayed({ if(!closed && target==null) turnCard.hide() },10000)
    }

    /** Spoken directions were switched on or off (map control): stop talking at once when muted. */
    fun voiceChanged() {
        if(!p.getBoolean(Keys.OFFLINE_NAV_VOICE,true)) { voice?.stop();pendingSpeech=null }
        else if(route!=null && !preview) initVoice()
    }

    private fun initVoice() {
        if(voice!=null || !p.getBoolean(Keys.OFFLINE_NAV_VOICE,true)) return
        voice=TextToSpeech(a.applicationContext) { status->
            val engine=voice
            if(closed || engine==null || status!=TextToSpeech.SUCCESS) return@TextToSpeech
            val offline=engine.voices?.firstOrNull { !it.isNetworkConnectionRequired && it.locale.language=="en" }
            if(offline!=null) { engine.voice=offline;engine.setAudioAttributes(android.media.AudioAttributes.Builder().setUsage(android.media.AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE).setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH).build());voiceReady=true;pendingSpeech?.let { text->pendingSpeech=null;speak(text) } }
            else if(!compact) android.widget.Toast.makeText(a,"No offline voice is installed; visual directions remain available",android.widget.Toast.LENGTH_LONG).show()
        }
    }

    private fun speak(text:String) { if(p.getBoolean(Keys.OFFLINE_NAV_VOICE,true)) { if(!voiceReady) pendingSpeech=text;initVoice();if(voiceReady) voice?.speak(text,TextToSpeech.QUEUE_FLUSH,null,"flare-navigation") } }

    fun stopRoute() {
        cachedRoute=null;cachedKey=null;engine.close();route=null;target=null;calculating=false;preview=false
        p.edit().putBoolean(Keys.OFFLINE_NAV_ACTIVE,false).remove(Keys.OFFLINE_NAV_VIA).apply();map.showRoute(null)
        voice?.stop();voice?.shutdown();voice=null;voiceReady=false;pendingSpeech=null
        turnCard.hide();tripCard.hide();onActiveChanged?.invoke(false)
    }

    fun close() { closed=true;main.removeCallbacksAndMessages(null);searchDialog?.dismiss();searchDialog=null;pendingSpeech=null;engine.close();voice?.stop();voice?.shutdown();voice=null;voiceReady=false }

    companion object {
        /** Last active route of this process, so the Home widget and the full map share it without recomputing. */
        private var cachedRoute:NavRoute?=null
        private var cachedKey:String?=null
        /** Within this distance of the chosen option's via point, it counts as passed. */
        private const val VIA_PASSED_M=2000.0
        /** Includes the pack, so a route from one pack is never reused after switching to another. */
        private fun key(pack:NavigationPack.Pack,lat:Double,lon:Double)="${pack.dir.absolutePath}|${pack.id}|$lat,$lon"
        /** Arabic-script ranges (Urdu street names): the English voice cannot read them. */
        private val NON_LATIN=Regex("[\\u0600-\\u06FF\\u0750-\\u077F\\uFB50-\\uFDFF\\uFE70-\\uFEFF]")
        /** Instruction for the voice: drops an Urdu-only street name ("turn left onto <name>" -> "turn left"). */
        fun speakable(text:String):String {
            if(!NON_LATIN.containsMatchIn(text)) return text
            for(joiner in listOf(" onto "," on "," toward ")) { val at=text.indexOf(joiner);if(at>0 && NON_LATIN.containsMatchIn(text.substring(at))) return text.substring(0,at) }
            return NON_LATIN.replace(text,"").replace(Regex("\\s+")," ").trim()
        }
    }
}
