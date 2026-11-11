package app.flare.auto

import android.app.*
import android.content.*
import android.media.MediaMetadata
import android.media.session.*
import android.os.*
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import org.json.JSONArray
import org.json.JSONObject
import java.lang.ref.WeakReference

internal object RedesignFixture {
    var current=WeakReference<Activity>(null)
    var installed=false
    var session:MediaSession?=null
    fun track(title:String) {
        session?.setMetadata(MediaMetadata.Builder().putString(MediaMetadata.METADATA_KEY_TITLE,title)
            .putString(MediaMetadata.METADATA_KEY_ARTIST,"Phase 2 artist").putLong(MediaMetadata.METADATA_KEY_DURATION,180000).build())
    }
}
class RedesignChecksActivity:Activity() {
    override fun onCreate(state:Bundle?) {
        super.onCreate(state)
        if(!RedesignFixture.installed) {
            application.registerActivityLifecycleCallbacks(object:Application.ActivityLifecycleCallbacks {
                override fun onActivityResumed(a:Activity) { RedesignFixture.current=WeakReference(a) }
                override fun onActivityCreated(a:Activity,b:Bundle?)=Unit
                override fun onActivityStarted(a:Activity)=Unit
                override fun onActivityPaused(a:Activity)=Unit
                override fun onActivityStopped(a:Activity)=Unit
                override fun onActivitySaveInstanceState(a:Activity,b:Bundle)=Unit
                override fun onActivityDestroyed(a:Activity)=Unit
            });RedesignFixture.installed=true
        }
        val results=JSONArray()
        fun test(name:String,block:()->Unit) { try { block();results.put("PASS "+name) } catch(e:Throwable) { results.put("FAIL $name: ${e.message}") } }
        val store=getSharedPreferences("phase2_model",0)
        store.edit().clear().putString("title","old").putInt("count",7).putLong("long",123456789012L).putBoolean("enabled",true).putFloat("float",1.5f).putStringSet("set",setOf("a","b")).commit()
        test("draft edits leave saved settings untouched") {
            val d=EditorDraft(store);d.edit().putString("title","draft").putInt("count",9).apply()
            check(store.getString("title",null)=="old");check(store.getInt("count",0)==7)
        }
        test("bundle round trip preserves every preference type") {
            val a=EditorDraft(store);val b=EditorDraft(store);b.edit().clear().apply();b.restore(a.bundle());check(a.all==b.all)
        }
        test("save preserves concurrent untouched values") {
            val d=EditorDraft(store);d.edit().putString("title","new").apply()
            store.edit().putInt("count",25).putString("background","keep").commit();d.save(store)
            check(store.getString("title",null)=="new");check(store.getInt("count",0)==25);check(store.getString("background",null)=="keep")
        }
        test("undo snapshot restores removed values and sets") {
            val d=EditorDraft(store);val snap=d.bundle();d.edit().remove("set").putString("new","value").apply();d.restore(snap)
            check(d.getStringSet("set",null)==setOf("a","b"));check(!d.contains("new"))
        }
        test("set reads do not mutate draft") { val d=EditorDraft(store);d.getStringSet("set",null)?.clear();check(d.getStringSet("set",null)?.size==2) }
        test("catalog sizes satisfy grid constraints") { WidgetCatalog.styles.forEach { s->s.sizes.forEach { (w,h)->check(GridTile(s.type,0,0,w,h).valid()) } } }
        test("placement does not overlap and respects player group") {
            val page=listOf(GridTile("media",0,0,4,6));check(WidgetCatalog.fit(page,"apps",4,3)?.x==4)
            check(WidgetCatalog.fit(page,"media_wide",4,3)==null)
        }
        test("custom profile edit/delete isolates data and protects built-ins") {
            val registry=getSharedPreferences("drivers",0);val before=registry.all;var id=""
            try {
                ProfileStore.add(this,"Delete check",2);id=ProfileStore.drivers(this).last().id
                ProfileStore.select(this,id);ProfileStore.defaultDriver(this,id)
                ProfileStore.preferences(this).edit().putString("test","owned").commit()
                val image=java.io.File(filesDir,id+"_wallpaper_home").apply { writeText("fixture") }
                ProfileStore.update(this,id,"Renamed check",4)
                check(ProfileStore.drivers(this).first { it.id==id }.let { it.name=="Renamed check" && it.avatar==4 })
                check(ProfileStore.delete(this,id));check(ProfileStore.active(this)=="default");check(!image.exists())
                check(getSharedPreferences("driver_"+id,0).all.isEmpty());check(registry.getString("default",null)=="default")
                check(!ProfileStore.delete(this,"default") && !ProfileStore.delete(this,"guest"))
            } finally {
                if(id.isNotEmpty()) { ProfileStore.delete(this,id);deleteSharedPreferences("driver_"+id) }
                val edit=registry.edit().clear();before.forEach { (k,v)->edit.putString(k,v.toString()) };edit.commit();ThemeTokens.invalidate()
            }
        }
        java.io.File(filesDir,"phase2-model-results.json").writeText(results.toString(2));deleteSharedPreferences("phase2_model")
        if(intent.getStringExtra("screen")=="saver") {
            ProfileStore.preferences(this).edit().putBoolean("saver_music_banner",true).putString("saver_slot_left","weather").putString("saver_slot_right","sun")
                .putBoolean("weather_enabled",false).putBoolean("solar_manual",true).putString("solar_lat","31.5497").putString("solar_lon","74.3436").apply()
            RedesignFixture.session?.release()
            RedesignFixture.session=MediaSession(this,"Phase2").apply { isActive=true;setPlaybackState(PlaybackState.Builder().setState(PlaybackState.STATE_PLAYING,0,1f).build()) }
            RedesignFixture.track("Track A")
        }
        val target=when(intent.getStringExtra("screen")) { "saver"->ScreenSaverActivity::class.java;"profiles"->ProfilesActivity::class.java;else->GridEditorActivity::class.java }
        startActivity(Intent(this,target));finish()
    }
}
class RedesignProbe:BroadcastReceiver() {
    override fun onReceive(c:Context,i:Intent) {
        val a=RedesignFixture.current.get()
        when(i.getStringExtra("action")) {
            "image"->if(a is GridEditorActivity) {
                val method=a.javaClass.getDeclaredMethod("onActivityResult",Int::class.javaPrimitiveType,Int::class.javaPrimitiveType,Intent::class.java)
                method.isAccessible=true
                method.invoke(a,CustomImages.PICK,Activity.RESULT_OK,Intent().setData(android.net.Uri.fromFile(java.io.File(c.cacheDir,"phase2-fixture.png"))))
            }
            "invalidMap"->{ val f=java.io.File(c.cacheDir,"invalid-map-test.map").apply { writeText("not a map") };OfflineMapStore.importMap(c,android.net.Uri.fromFile(f)) }
            "track"->RedesignFixture.track(i.getStringExtra("title") ?: "Track B")
            "recreate"->a?.recreate()
            "dump"->{
                val rows=JSONArray()
                fun visit(v:View) {
                    val pos=IntArray(2);v.getLocationOnScreen(pos)
                    rows.put(JSONObject().put("class",v.javaClass.simpleName).put("text",if(v is TextView) v.text.toString() else "")
                        .put("desc",v.contentDescription?.toString().orEmpty()).put("visible",v.isShown).put("visibility",v.visibility)
                        .put("x",pos[0]).put("y",pos[1]).put("w",v.width).put("h",v.height)
                        .put("tx",v.translationX).put("ty",v.translationY).put("sx",v.scaleX).put("sy",v.scaleY)
                        .put("gridSelection",if(v is GridBoard) v.selection else -1))
                    if(v is ViewGroup) for(j in 0 until v.childCount) visit(v.getChildAt(j))
                }
                a?.window?.decorView?.let { visit(it) }
                java.io.File(c.filesDir,"phase2-view.json").writeText(JSONObject().put("activity",a?.javaClass?.simpleName).put("rows",rows).toString())
            }
        }
    }
}
