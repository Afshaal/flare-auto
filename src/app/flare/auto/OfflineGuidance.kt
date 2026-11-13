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
    /** Gray alternative routes (empty removes them). */
    fun showAlternatives(routes: List<NavRoute>) {}
    /** Flat overview fitting [routes] after a destination is chosen; null returns to following the vehicle. */
    fun overview(routes: List<NavRoute>?) {}
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
    /** Full map: route overview after choosing a destination (options and Start). */
    val optionsCard=RouteListCard(c,"Routes","Start")
    /** Full map while driving: the other routes computed with the chosen one, with their ETA comparison. */
    val alternativesCard=RouteListCard(c,"Other routes",null)
    /** Alternatives still ahead of their split from the chosen route ([NavMotion.divergesAt]). */
    private class Alternative(val route:NavRoute,val label:String,val splitAt:Double)
    private var alternatives:List<Alternative> = emptyList()
    /** Overview waiting for Start: the options, the selected one, and where the car was when they were shown. */
    private var options:List<NavRoute>?=null
    private var optionsDest:OfflineDestinationPicker.Place?=null
    private var optionsPreview=false
    private var selected=0
    private var optionsFrom:Location?=null
    /** Called whenever guidance starts or stops (the host may show or hide related controls). */
    var onActiveChanged:((Boolean)->Unit)?=null
    /**
     * Planned length of the current journey ([TrafficEta] distance scaling): set when the journey's first route is
     * applied, kept on reroutes, restored with the destination (shared by the Home widget and the full map through
     * [Keys.OFFLINE_NAV_TRIP_M]) and cleared when a new destination is chosen or the journey ends.
     */
    private var tripMetres:Double?=p.getString(Keys.OFFLINE_NAV_TRIP_M,null)?.toDoubleOrNull()
    /** Schedule part of the traffic adjustment, refreshed at most every [ETA_REFRESH_MS] (the rest is cached). */
    private var schedule=TrafficEta.Schedule.NONE
    private var scheduleAt=0L
    /** Progress along the current route at the last fix (for the periodic refresh). */
    private var lastAlong=0.0

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
                    tripCard.show(cached.distance,eta(cached,0.0),p.clock24)
                }
            }
        }
        main.postDelayed(object:Runnable { override fun run() { if(closed) return;refreshEta();main.postDelayed(this,ETA_REFRESH_MS) } },ETA_REFRESH_MS)
    }

    /**
     * The one estimate every display uses (overview, trip card on the full map and the Home widget, alternatives):
     * remaining time from [along] metres into [path], with the typical traffic adjustment when it is on. [journey]:
     * the journey length that scales it (the planned trip; an option's own length while choosing).
     */
    private fun eta(path:NavRoute,along:Double,journey:Double?=null):Long {
        val now=SystemClock.elapsedRealtime()
        if(scheduleAt==0L || now-scheduleAt>=ETA_REFRESH_MS) { schedule=TrafficEta.scheduleNow(TrafficEta.read(p));scheduleAt=now }
        return TrafficEta.remaining(path,along,schedule,journey ?: tripMetres ?: path.distance)
    }

    /** Periodic refresh (schedule windows change over time): redraws the shown estimates from cached progress. */
    private fun refreshEta() {
        scheduleAt=0L
        val found=options
        if(found!=null) { optionsCard.show(RouteOptions.summaries(found) { eta(it,0.0,it.distance) },selected);showSelected();return }
        val path=route ?: return
        if(calculating) return
        if(preview) tripCard.show(path.distance,eta(path,0.0),p.clock24)
        else tripCard.show((path.cumulative.last()-lastAlong).coerceAtLeast(0.0),eta(path,lastAlong),p.clock24)
        if(alternatives.isNotEmpty()) showAlternatives()
    }

    private fun startJourney() { tripMetres=null;p.edit().remove(Keys.OFFLINE_NAV_TRIP_M).apply() }

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
        endOverview();clearAlternatives()
        if(!pack.covers(place.lat,place.lon)) { turnCard.message("Outside ${pack.region} driving coverage");return }
        fun begin(lat:Double,lon:Double,asPreview:Boolean) {
            target=place;route=null;lastAlong=0.0;startJourney();map.showRoute(null);tripCard.hide();tripCard.starred(saved.isStarred(place))
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
                result.fold({ found-> if(compact) apply(found.first(),dest,asPreview) else offer(found,dest,asPreview) },failed)
            }
            return
        }
        clearAlternatives() // a recalculated route is compared with nothing (no stale ETAs)
        turnCard.message("Calculating route to ${dest.name}…")
        engine.route(lat,lon,dest.lat,dest.lon,pack,RouteOptions.decode(p.getString(Keys.OFFLINE_NAV_VIA,null))) { result->
            if(closed) return@route
            result.fold({ apply(it,dest,asPreview) },failed)
        }
    }

    /**
     * Route overview: the map shows [found] in a flat view (the selected route blue, the others gray) with the
     * options listed; a tap selects, Start (or setting off: [OVERVIEW_START_M] from where they were shown) begins
     * guidance on the selected route. One route found: the overview shows just that one.
     */
    private fun offer(found:List<NavRoute>,dest:OfflineDestinationPicker.Place,asPreview:Boolean) {
        calculating=false;options=found;optionsDest=dest;optionsPreview=asPreview;selected=0;optionsFrom=location
        turnCard.message(if(found.size>1) "Choose a route to ${dest.name}" else "Route to ${dest.name}")
        showSelected()
        map.overview(found)
        optionsCard.onPick={ i-> if(options===found) { selected=i;showSelected() } }
        optionsCard.onAction={ if(options===found) pick(selected) }
        optionsCard.show(RouteOptions.summaries(found) { eta(it,0.0,it.distance) },0)
    }

    private fun showSelected() {
        val found=options ?: return
        val chosen=found[selected]
        map.showRoute(chosen);map.showAlternatives(found.filterIndexed { i,_->i!=selected })
        tripCard.show(chosen.distance,eta(chosen,0.0,chosen.distance),p.clock24);optionsCard.select(selected)
    }

    /** Starts guidance on option [i] of the overview; the other options stay as gray alternatives until passed. */
    private fun pick(i:Int) {
        val found=options ?: return
        val dest=optionsDest ?: return
        if(closed || target!==dest) return
        val asPreview=optionsPreview
        endOverview()
        val via=if(i==0) null else found[i].via ?: RouteOptions.viaPoint(found[i].points,found[0].points)
        p.edit().apply { if(via==null) remove(Keys.OFFLINE_NAV_VIA) else putString(Keys.OFFLINE_NAV_VIA,RouteOptions.encode(via)) }.apply()
        apply(found[i],dest,asPreview)
        if(asPreview) return
        val labels=RouteOptions.labels(found.map { it.roads })
        alternatives=found.indices.filter { it!=i }.mapNotNull { j->
            NavMotion.divergesAt(found[i],found[j])?.let { split-> Alternative(found[j],labels[j],split) }
        }
        showAlternatives()
    }

    private fun endOverview() {
        if(options==null) return
        options=null;optionsDest=null;optionsFrom=null;optionsCard.hide();map.overview(null)
    }

    private fun clearAlternatives() { if(alternatives.isNotEmpty()) { alternatives=emptyList();showAlternatives() } }

    private fun showAlternatives() {
        map.showAlternatives(alternatives.map { it.route })
        if(compact) return
        val list=alternatives
        alternativesCard.onPick={ i-> list.getOrNull(i)?.let { switchTo(it) } }
        if(list.isEmpty()) { alternativesCard.hide();return }
        val chosen=route ?: return
        alternativesCard.show(list.map { "${it.label} · ${compare(it.route,chosen)}" },-1)
    }

    /**
     * ETA comparison of an alternative with the chosen route, both from the car's current place: the chosen route's
     * remaining time against the alternative's remaining time from the same point (the stretch before the split is
     * shared), so a partly driven trip is never compared with a whole one.
     */
    private fun compare(alt:NavRoute,chosen:NavRoute):String {
        val fix=location
        val altAlong=if(fix!=null && lastAlong>0.0) NavGeometry.nearest(alt,fix.latitude,fix.longitude,0).along else 0.0
        return NavMotion.compare(eta(alt,altAlong),eta(chosen,lastAlong))
    }

    /** Drives the alternative instead: routes from the car through that option's point (one calculation). */
    private fun switchTo(alt:Alternative) {
        val dest=target ?: return
        val via=alt.route.via ?: route?.let { RouteOptions.viaPoint(alt.route.points,it.points) }
        p.edit().apply { if(via==null) remove(Keys.OFFLINE_NAV_VIA) else putString(Keys.OFFLINE_NAV_VIA,RouteOptions.encode(via)) }.apply()
        clearAlternatives()
        val fix=location
        if(fix!=null && recent(fix)) calculate(fix.latitude,fix.longitude,false) else apply(alt.route,dest,false)
    }

    private fun apply(path:NavRoute,dest:OfflineDestinationPicker.Place,asPreview:Boolean) {
        calculating=false
        route=path;segment=0;spoken=-1;offRoute=0;lastAlong=0.0;map.showRoute(path)
        // The journey's planned length is fixed by its first route; reroutes and restored routes keep it.
        if(tripMetres==null) { tripMetres=path.distance;if(!asPreview) p.edit().putString(Keys.OFFLINE_NAV_TRIP_M,path.distance.toString()).apply() }
        if(asPreview) { cachedRoute=null;cachedKey=null } else { cachedRoute=path;cachedKey=key(pack,dest.lat,dest.lon) }
        p.edit().putBoolean(Keys.OFFLINE_NAV_ACTIVE,!asPreview).putString(Keys.OFFLINE_NAV_LAT,dest.lat.toString()).putString(Keys.OFFLINE_NAV_LON,dest.lon.toString()).putString(Keys.OFFLINE_NAV_NAME,dest.name).apply()
        turnCard.message(if(asPreview) "Route preview · ${dest.name}" else "Continue towards ${dest.name}")
        tripCard.show(path.distance,eta(path,0.0),p.clock24)
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
        // Overview open and the car sets off: start with the selected route.
        optionsFrom?.let { from-> if(options!=null && !optionsPreview && from.distanceTo(fix)>OVERVIEW_START_M) { pick(selected);return } }
        if(options!=null) { if(optionsFrom==null) optionsFrom=fix;return }
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
        lastAlong=position.along
        map.routeProgress(position.segment,position.along)
        if(alternatives.any { position.along>it.splitAt }) { alternatives=alternatives.filter { position.along<=it.splitAt };showAlternatives() }
        val remaining=(path.cumulative.last()-position.along).coerceAtLeast(0.0)
        if(remaining<35 && NavGeometry.distance(fix.latitude,fix.longitude,path.points[path.points.size-2],path.points.last())<60) {
            arrived();return
        }
        val next=path.steps.indexOfFirst { it.at>position.along+8 }.let { if(it<0) path.steps.lastIndex else it }
        val step=path.steps.getOrNull(next) ?: return
        val metres=(step.at-position.along).coerceAtLeast(0.0)
        val road=if(step.sign==TurnArrow.FINISH) target?.name.orEmpty() else TripFormat.road(step)
        turnCard.turn(step.sign,metres,road)
        tripCard.show(remaining,eta(path,position.along),p.clock24)
        if(next!=spoken && metres<200) { spoken=next;val said=speakable(step.text);speak(if(metres>50) "In ${metres.toInt()} metres, $said" else said) }
    }

    private fun arrived() {
        val name=target?.name.orEmpty()
        speak("You have arrived near your destination")
        endOverview();clearAlternatives()
        cachedRoute=null;cachedKey=null;route=null;target=null;preview=false;startJourney()
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
        endOverview();clearAlternatives()
        cachedRoute=null;cachedKey=null;engine.close();route=null;target=null;calculating=false;preview=false;startJourney()
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
        /** The car moved this far while the route overview was open: start guidance on the selected route. */
        private const val OVERVIEW_START_M=80f
        /** Typical traffic adjustment: schedule refresh while navigation is shown (progress uses cached data). */
        private const val ETA_REFRESH_MS=30_000L
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
