package app.flare.auto

import android.app.Activity
import android.content.Context
import android.location.Location
import android.os.SystemClock
import android.speech.tts.TextToSpeech
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import java.util.Locale

internal class OfflineGuidance(private val a:Activity,c:Context,private val paintRoute:(NavRoute?)->Unit,private val center:()->Pair<Double,Double>) {
    private val p=ProfileStore.preferences(c)
    private val ui=UiKit(a).apply { t=ThemeTokens.get(c) }
    private val engine=OfflineRouting(c)
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
    private val etaFormat=java.text.SimpleDateFormat(if(p.getBoolean("clock24",false)) "HH:mm" else "h:mm a",Locale.getDefault())
    private val title:TextView=ui.text("Search destination",16,ui.t.textPrimary).apply { maxLines=2;ellipsize=android.text.TextUtils.TruncateAt.END }
    private val detail:TextView=ui.text("Lahore · offline driving",12,ui.t.textSecondary).apply { maxLines=2 }
    val view:LinearLayout=ui.column().apply {
        setPadding(ui.dp(12),ui.dp(8),ui.dp(12),ui.dp(8));background=GlassDrawable(ui.t.card,ui.dp(12).toFloat(),ui.t.cardBorder)
        addView(title);addView(detail);contentDescription="Offline navigation";minimumHeight=ui.dp(56)
        setOnClickListener {
            if(route==null && !calculating) search()
            else ThemedDialogs.Builder(a).setTitle("Navigation").setItems(arrayOf("Search another destination",if(preview) "Start guidance from GPS" else "Stop navigation",if(p.getBoolean("offline_nav_voice",true)) "Mute directions" else "Enable spoken directions")) { _,i->when(i) {
                0->search()
                1->if(preview) target?.let { choose(it) } else stopRoute()
                else->{ p.edit().putBoolean("offline_nav_voice",!p.getBoolean("offline_nav_voice",true)).apply();if(!p.getBoolean("offline_nav_voice",true)) voice?.stop() }
            } }.show()
        }
    }
    init {
        if(p.getBoolean("offline_nav_active",false)) {
            val lat=p.getString("offline_nav_lat","")?.toDoubleOrNull();val lon=p.getString("offline_nav_lon","")?.toDoubleOrNull()
            if(lat!=null && lon!=null && NavigationPack.covers(lat,lon)) { target=OfflineDestinationPicker.Place(p.getString("offline_nav_name","Destination") ?: "Destination","saved",lat,lon);title.text="Waiting for GPS";detail.text=target?.name }
        }
    }
    fun size(width:Int) { view.layoutParams?.width=width;title.maxWidth=width-ui.dp(24);detail.maxWidth=width-ui.dp(24) }
    fun search() { searchDialog=OfflineDestinationPicker.show(a,::choose) }
    fun choose(place:OfflineDestinationPicker.Place) {
        if(closed) return
        if(!NavigationPack.covers(place.lat,place.lon)) { title.text="Outside Lahore driving coverage";return }
        fun begin(lat:Double,lon:Double,asPreview:Boolean) {
            target=place;route=null;paintRoute(null);p.edit().putBoolean("offline_nav_active",false).apply();calculate(lat,lon,asPreview)
        }
        val fix=location
        if(fix!=null && recent(fix)) begin(fix.latitude,fix.longitude,false)
        else ThemedDialogs.Builder(a).setTitle("Waiting for GPS").setMessage("A recent GPS fix is needed for driving guidance. You can preview a route from the map center while waiting.")
            .setPositiveButton("Preview route") { _,_->val start=center();begin(start.first,start.second,true) }.setNegativeButton("Cancel",null).show()
    }
    private fun calculate(lat:Double,lon:Double,asPreview:Boolean) {
        val dest=target ?: return
        calculating=true;preview=asPreview;title.text="Calculating offline route…";detail.text=dest.name
        engine.route(lat,lon,dest.lat,dest.lon) { result->
            if(closed) return@route
            calculating=false
            result.fold({ path->
                route=path;segment=0;spoken=-1;offRoute=0;paintRoute(path)
                p.edit().putBoolean("offline_nav_active",!asPreview).putString("offline_nav_lat",dest.lat.toString()).putString("offline_nav_lon",dest.lon.toString()).putString("offline_nav_name",dest.name).apply()
                title.text=if(asPreview) "Route preview · ${dest.name}" else "Continue towards ${dest.name}"
                detail.text=summary(path.distance,path.millis)
                if(!asPreview) { initVoice();location?.let { update(it) } }
            },{ error->title.text="Route unavailable";detail.text=error.message ?: "No local driving route found" })
        }
    }
    private fun recent(fix:Location):Boolean {
        val age=if(fix.elapsedRealtimeNanos>0) (SystemClock.elapsedRealtimeNanos()-fix.elapsedRealtimeNanos)/1000000 else System.currentTimeMillis()-fix.time
        return age in -1000..120000 && (!fix.hasAccuracy() || fix.accuracy<=100)
    }
    fun update(fix:Location) {
        if(closed || !fix.latitude.isFinite() || !fix.longitude.isFinite()) return
        location=fix
        if(!recent(fix)) { if(route!=null) detail.text="Waiting for an accurate GPS fix";return }
        if(route==null) {
            if(!calculating && target!=null && p.getBoolean("offline_nav_active",false)) calculate(fix.latitude,fix.longitude,false)
            return
        }
        if(preview || calculating) return
        val path=route ?: return
        val position=NavGeometry.nearest(path,fix.latitude,fix.longitude,segment);segment=position.segment
        val threshold=maxOf(50.0,if(fix.hasAccuracy()) fix.accuracy*2.0 else 50.0)
        if(position.away>threshold) {
            offRoute++
            if(offRoute>=2 && SystemClock.elapsedRealtime()-lastReroute>15000) { lastReroute=SystemClock.elapsedRealtime();calculate(fix.latitude,fix.longitude,false) }
            else detail.text="Off route · recalculating locally"
            return
        }
        offRoute=0
        val remaining=(path.cumulative.last()-position.along).coerceAtLeast(0.0)
        if(remaining<35 && NavGeometry.distance(fix.latitude,fix.longitude,path.points[path.points.size-2],path.points.last())<60) {
            title.text="You have arrived";detail.text=target?.name;speak("You have arrived near your destination");p.edit().putBoolean("offline_nav_active",false).apply();preview=true;return
        }
        val next=path.steps.indexOfFirst { it.at>position.along+8 }.let { if(it<0) path.steps.lastIndex else it }
        val step=path.steps.getOrNull(next) ?: return
        val metres=(step.at-position.along).coerceAtLeast(0.0)
        title.text=distance(metres)+" · "+step.text
        detail.text=summary(remaining,(path.millis*(remaining/path.cumulative.last().coerceAtLeast(1.0))).toLong())
        if(next!=spoken && metres<200) { spoken=next;speak(if(metres>50) "In ${metres.toInt()} metres, ${step.text}" else step.text) }
    }
    private fun initVoice() {
        if(voice!=null || !p.getBoolean("offline_nav_voice",true)) return
        voice=TextToSpeech(a.applicationContext) { status->
            val engine=voice
            if(closed || engine==null || status!=TextToSpeech.SUCCESS) return@TextToSpeech
            val offline=engine.voices?.firstOrNull { !it.isNetworkConnectionRequired && it.locale.language=="en" }
            if(offline!=null) { engine.voice=offline;engine.setAudioAttributes(android.media.AudioAttributes.Builder().setUsage(android.media.AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE).setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH).build());voiceReady=true;pendingSpeech?.let { text->pendingSpeech=null;speak(text) } }
            else android.widget.Toast.makeText(a,"No offline voice is installed; visual directions remain available",android.widget.Toast.LENGTH_LONG).show()
        }
    }
    private fun speak(text:String) { if(p.getBoolean("offline_nav_voice",true)) { if(!voiceReady) pendingSpeech=text;initVoice();if(voiceReady) voice?.speak(text,TextToSpeech.QUEUE_FLUSH,null,"flare-navigation") } }
    fun stopRoute() { engine.close();route=null;target=null;calculating=false;p.edit().putBoolean("offline_nav_active",false).apply();paintRoute(null);voice?.stop();voice?.shutdown();voice=null;voiceReady=false;pendingSpeech=null;title.text="Search destination";detail.text="Lahore · offline driving" }
    fun close() { closed=true;searchDialog?.dismiss();searchDialog=null;pendingSpeech=null;engine.close();voice?.stop();voice?.shutdown();voice=null;voiceReady=false }
    private fun summary(metres:Double,millis:Long):String {
        val minutes=maxOf(1,(millis/60000).toInt());val eta=etaFormat.format(java.util.Date(System.currentTimeMillis()+millis))
        return "${distance(metres)} · $minutes min · $eta"
    }
    private fun distance(m:Double)=if(m<1000) "${(m/10).toInt()*10} m" else "%.1f km".format(Locale.getDefault(),m/1000)
}
