package app.flare.auto
import android.app.Activity
import android.os.Bundle
import android.content.Intent
import android.widget.TextView
import org.json.JSONObject
class FeatureChecksActivity : Activity() {
    override fun onCreate(state:Bundle?) {
        super.onCreate(state)
        val registry=getSharedPreferences("drivers",0)
        val backup=getSharedPreferences("feature_checks",0)
        if(intent.getBooleanExtra("cleanup",false)) {
            val raw=backup.getString("registry",null)
            if(raw!=null) {
                val obj=JSONObject(raw); val edit=registry.edit().clear()
                obj.keys().forEach { edit.putString(it,obj.getString(it)) }; edit.commit()
                backup.getString("test_id",null)?.let { id->HostedWidgets.ids(getSharedPreferences("driver_"+id,0).getString(DashboardPages.KEY,null)).forEach { HostedWidgets.delete(this,it) };deleteSharedPreferences("driver_"+id) }
                backup.edit().clear().commit(); ThemeTokens.invalidate()
            }
            startActivity(Intent(this,HomeActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)); finish(); return
        }
        if(!backup.contains("registry")) {
            backup.edit().putString("registry",JSONObject(registry.all).toString()).commit()
            val initialDriver=ProfileStore.active(this)
            val original=ProfileStore.preferences(this)
            val before=original.all
            val image=CustomImages.file(this,"home").absolutePath
            ProfileStore.add(this,"Feature test")
            val id=ProfileStore.drivers(this).last().id
            backup.edit().putString("test_id",id).commit()
            ProfileStore.select(this,id)
            val fresh=ProfileStore.preferences(this)
            check(!fresh.contains("brand_text"))
            fresh.edit().putString("brand_text","TEST DRIVER").putBoolean("clock24",true).commit()
            check(original.all==before)
            check(CustomImages.file(this,"home").absolutePath!=image)
            check(DashboardPages.decode("bad")==null)
            check(DashboardPages.decode("[[{\"type\":\"clock\",\"x\":0,\"y\":0,\"w\":9,\"h\":6}]]")==null)
            val layout=mutableListOf(mutableListOf(GridTile("clock",0,0,4,6),GridTile("media",4,0,4,3),GridTile("apps",4,3,4,3)),mutableListOf(GridTile("apps",0,0,8,6)))
            check(DashboardPages.decode(DashboardPages.encode(layout))==layout)
            ProfileStore.startupMode(this,"ask"); registry.edit().remove("boot").commit()
            check(ProfileStore.prepareStartup(this)); check(!ProfileStore.prepareStartup(this))
            ProfileStore.defaultDriver(this,initialDriver); ProfileStore.startupMode(this,"default"); registry.edit().remove("boot").commit()
            check(!ProfileStore.prepareStartup(this)); check(ProfileStore.active(this)==initialDriver)
            ProfileStore.select(this,id); ProfileStore.startupMode(this,"last")
            android.util.Log.i("FlareFeatureChecks","PASS profile settings isolation, image isolation, malformed layout rejection, bounds rejection, page round-trip, once-per-boot picker, default driver selection")
            fresh.edit().putString(DashboardPages.KEY,DashboardPages.encode(layout)).putBoolean("saver_solar",true).putBoolean("saver_banners",true)
                .putStringSet("saver_banner_apps",setOf("com.android.shell")).putBoolean("saver_banner_private",false).commit()
        }
        backup.getString("test_id",null)?.let { ProfileStore.select(this,it) }
        ProfileStore.preferences(this).edit().putStringSet("saver_banner_apps",setOf("local.flare.notifytest"))
            .putBoolean("saver_banner_private",intent.getBooleanExtra("private",false)).putInt("saver_banner_seconds",15).commit()
        val vlc=packageManager.getLaunchIntentForPackage("org.videolan.vlc")?.component?.flattenToString()
        if(vlc!=null) ProfileStore.preferences(this).edit().putString("layout_apps",org.json.JSONArray(listOf(vlc)).toString()).commit()
        if(intent.getStringExtra("screen")=="geometry") checkGeometry()
        val target=when(intent.getStringExtra("screen")) {
            "widget" -> AppWidgetActivity::class.java
            "grid" -> GridEditorActivity::class.java
            "profiles" -> ProfilesActivity::class.java
            "saver" -> ScreenSaverActivity::class.java
            "settings" -> SettingsActivity::class.java
            else -> HomeActivity::class.java
        }
        startActivity(Intent(this,target).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)); finish()
    }
    private fun checkGeometry() {
        val prefs=ProfileStore.preferences(this)
        val old=prefs.getInt("text",100); val existed=prefs.contains("text")
        var cases=0
        try {
            for(size in listOf(85,100,120)) for(kind in GridTile.TYPES) {
                prefs.edit().putInt("text",size).commit()
                val renderer=HomeCards(this,prefs,ThemeTokens.get(this),preview=true,duration={180000L})
                val inner=android.widget.LinearLayout(this)
                renderer.build(inner,kind,true)
                renderer.trackTitle?.text="A long track title for layout checking"
                renderer.trackArtist?.text="Artist name"
                renderer.sourceLabel?.apply { text="DiPlay"; visibility=android.view.View.VISIBLE }
                val frame=CardFrame(this,inner,kind)
                val ws=android.view.View.MeasureSpec.makeMeasureSpec(470,android.view.View.MeasureSpec.EXACTLY)
                val hs=android.view.View.MeasureSpec.makeMeasureSpec(if(kind=="projection") 130 else 195,android.view.View.MeasureSpec.EXACTLY)
                repeat(3) { frame.measure(ws,hs); frame.layout(0,0,470,if(kind=="projection") 130 else 195) }
                var pills=0; var controls=0
                fun inspect(v:android.view.View) {
                    if(v.visibility!=android.view.View.VISIBLE) return
                    if(v.contentDescription=="CarPlay" || v.contentDescription=="Android Auto") { check(v.width>0 && v.height>0); pills++ }
                    if(v is android.widget.ImageView && v.drawable is MediaGlyph) { check(v.width>0 && v.height>0); controls++ }
                    if(v is android.view.ViewGroup) for(j in 0 until v.childCount) {
                        val child=v.getChildAt(j)
                        if(child.visibility==android.view.View.VISIBLE) {
                            check(child.top>=-2 && child.left>=-2 && child.left+child.width*child.scaleX<=v.width+3 && child.top+child.height*child.scaleY<=v.height+3) { "Clipped $kind at $size: ${child.javaClass.simpleName} ${child.left},${child.top} ${child.width}x${child.height} in ${v.width}x${v.height}" }
                            inspect(child)
                        }
                    }
                }
                inspect(frame)
                if(kind=="clock" || kind=="projection") check(pills==2)
                if(kind=="media") check(controls==3)
                renderer.environments.forEach { it.stop() }; cases++
            }
            android.util.Log.i("FlareFeatureChecks","PASS $cases compact geometry cases at 85/100/120%, projection pills and transport controls retained")
        } finally { val edit=prefs.edit(); if(existed) edit.putInt("text",old) else edit.remove("text"); edit.commit() }
    }
}
