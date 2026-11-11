package app.flare.auto
import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
internal object ProfileStore {
    data class Driver(val id: String, val name: String, val avatar:Int=0)
    private fun registry(c: Context) = c.applicationContext.getSharedPreferences("drivers", 0)
    fun active(c: Context): String = registry(c).getString("active", "default") ?: "default"
    fun preferences(c: Context): SharedPreferences = if(c is EditorContext) c.draft else c.applicationContext.getSharedPreferences(
        if (active(c) == "default") "home" else "driver_" + active(c), 0)
    fun drivers(c: Context): List<Driver> {
        val result = arrayListOf(Driver("default", "Family"), Driver("guest", "Guest"))
        try {
            val rows = JSONArray(registry(c).getString("list", "[]"))
            for (i in 0 until rows.length()) {
                val row = rows.getJSONObject(i); val id = row.getString("id")
                if (id.matches(Regex("[0-9a-f-]{36}"))) result.add(Driver(id, row.getString("name"),row.optInt("avatar",0)))
            }
        } catch (_: Exception) { }
        return result
    }
    fun add(c: Context, name: String, avatar:Int=0) {
        val rows = JSONArray()
        drivers(c).filter { it.id != "default" && it.id != "guest" }.forEach { rows.put(JSONObject().put("id", it.id).put("name", it.name).put("avatar",it.avatar)) }
        rows.put(JSONObject().put("id", java.util.UUID.randomUUID().toString()).put("name", name.take(32)).put("avatar",avatar))
        registry(c).edit().putString("list", rows.toString()).apply()
    }
    fun update(c:Context,id:String,name:String,avatar:Int) {
        if(id=="default" || id=="guest" || drivers(c).none { it.id==id }) return
        val rows=JSONArray()
        drivers(c).filter { it.id!="default" && it.id!="guest" }.forEach {
            rows.put(JSONObject().put("id",it.id).put("name",if(it.id==id) name.trim().take(32) else it.name).put("avatar",if(it.id==id) avatar.coerceIn(0,5) else it.avatar))
        }
        registry(c).edit().putString("list",rows.toString()).apply()
    }
    fun delete(c:Context,id:String):Boolean {
        if(id=="default" || id=="guest" || drivers(c).none { it.id==id }) return false
        if(active(c)==id) select(c,"default")
        val rows=JSONArray()
        drivers(c).filter { it.id!="default" && it.id!="guest" && it.id!=id }.forEach {
            rows.put(JSONObject().put("id",it.id).put("name",it.name).put("avatar",it.avatar))
        }
        val edit=registry(c).edit().putString("list",rows.toString())
        if(registry(c).getString("default",null)==id) edit.putString("default","default")
        val old=c.getSharedPreferences("driver_"+id,0)
        HostedWidgets.ids(old.getString(DashboardPages.KEY,null)).forEach { HostedWidgets.delete(c,it) }
        edit.apply();c.deleteSharedPreferences("driver_"+id)
        c.filesDir.listFiles()?.filter { it.isFile && it.name.startsWith(id+"_wallpaper_") }?.forEach { it.delete() }
        ThemeTokens.invalidate();CustomImages.cache.evictAll();return true
    }
    fun select(c: Context, id: String) {
        if (drivers(c).none { it.id == id }) return
        registry(c).edit().putString("active", id).apply()
        ThemeTokens.invalidate(); CustomImages.cache.evictAll()
    }
    fun startupMode(c: Context) = registry(c).getString("startup", "ask") ?: "ask"
    fun startupMode(c: Context, value: String) { registry(c).edit().putString("startup", value).apply() }
    fun defaultDriver(c: Context, id: String) { registry(c).edit().putString("default", id).apply() }
    private var prepared=false
    fun prepareStartup(c: Context): Boolean {
        val boot = StartupActions.bootId().ifEmpty { "process" }; val r = registry(c)
        if (prepared && r.getString("boot", "") == boot) return false
        prepared=true
        r.edit().putString("boot", boot).apply()
        if (startupMode(c) == "default") select(c, r.getString("default", "default") ?: "default")
        return drivers(c).size > 1 && startupMode(c) == "ask"
    }
}
