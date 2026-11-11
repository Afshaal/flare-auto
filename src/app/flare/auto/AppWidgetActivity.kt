package app.flare.auto

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.ScrollView
import org.json.JSONArray

class AppWidgetActivity:Activity() {
    /** Dialogs take focus; restore full screen when it comes back. */
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) window.immersive()
    }

    private lateinit var ui:UiKit
    override fun onCreate(state:Bundle?) { super.onCreate(state); render() }
    private fun values():MutableList<String> {
        val result=mutableListOf<String>()
        try { val rows=JSONArray(ProfileStore.preferences(this).getString(Keys.LAYOUT_APPS,"[]")); for(i in 0 until minOf(8,rows.length())) rows.optString(i).takeIf { it.isNotEmpty() }?.let { result.add(it) } } catch(_:Exception) { }
        return result
    }
    private fun render() {
        ui=UiKit(this); window.immersive()
        window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(ui.t.background))
        val root=ui.column().apply { setBackgroundColor(ui.t.background) }; root.addView(ui.header("Application widget"))
        val scroll=ScrollView(this); val list=ui.column(); scroll.addView(list); root.addView(scroll,LinearLayout.LayoutParams(MATCH,0,1f))
        val apps=values()
        apps.forEachIndexed { index,target ->
            val name=try { packageManager.getActivityInfo(ComponentName.unflattenFromString(target)!!,0).loadLabel(packageManager).toString() } catch(_:Exception) { "Unavailable application" }
            list.addView(ui.valueRow(name,"Remove",Runnable {
                apps.removeAt(index); ProfileStore.preferences(this).edit().putString(Keys.LAYOUT_APPS,JSONArray(apps).toString()).apply(); render()
            }))
        }
        if(apps.size<8) list.addView(ui.valueRow("Add application","${apps.size}/8",Runnable {
            startActivityForResult(Intent(this,AppPickerActivity::class.java).putExtra(AppPickerActivity.ROLE,"widget"),1)
        }))
        setContentView(root)
    }
    @Deprecated("Deprecated in Android")
    override fun onActivityResult(request:Int,result:Int,data:Intent?) {
        super.onActivityResult(request,result,data)
        if(request!=1 || result!=RESULT_OK) return
        val target=data?.getStringExtra(AppPickerActivity.TARGET).orEmpty()
        val apps=values()
        if(ComponentName.unflattenFromString(target)!=null && target !in apps && apps.size<8) {
            apps.add(target); ProfileStore.preferences(this).edit().putString(Keys.LAYOUT_APPS,JSONArray(apps).toString()).apply()
        }
        render()
    }
}
