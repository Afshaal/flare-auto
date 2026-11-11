package app.flare.auto

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Toast

class WidgetPickerActivity:Activity() {
    /** Dialogs take focus; restore full screen when it comes back. */
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) window.immersive()
    }

    private var widget=-1
    private var delivered=false
    override fun onCreate(state:Bundle?) {
        super.onCreate(state);widget=state?.getInt("widget",-1) ?: -1
        val ui=UiKit(this);window.immersive()
        window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(ui.t.background))
        val root=ui.column().apply { setBackgroundColor(ui.t.background) };root.addView(ui.header("Installed app widgets"))
        val scroll=ScrollView(this);val list=ui.column();scroll.addView(list);root.addView(scroll,LinearLayout.LayoutParams(MATCH,0,1f));setContentView(root)
        if(!packageManager.hasSystemFeature("android.software.app_widgets")) { list.addView(ui.text("App widgets are not supported by this firmware",16,ui.t.textSecondary));return }
        val providers=AppWidgetManager.getInstance(this).installedProviders.filter { it.widgetCategory and android.appwidget.AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN!=0 }
            .sortedBy { it.loadLabel(packageManager).toString().lowercase(java.util.Locale.getDefault()) }
        if(providers.isEmpty()) list.addView(ui.text("No installed applications provide Home widgets",16,ui.t.textSecondary))
        providers.forEach { info->
            val preview=android.widget.ImageView(this).apply { setImageDrawable(info.loadPreviewImage(this@WidgetPickerActivity,0) ?: info.loadIcon(this@WidgetPickerActivity,0));scaleType=android.widget.ImageView.ScaleType.FIT_CENTER }
            list.addView(preview,LinearLayout.LayoutParams(MATCH,ui.dp(130)))
            list.addView(ui.valueRow(info.loadLabel(packageManager).toString(),"Add",Runnable {
                if(widget>0) return@Runnable
                try {
                    widget=HostedWidgets.host(this).allocateAppWidgetId()
                    if(AppWidgetManager.getInstance(this).bindAppWidgetIdIfAllowed(widget,info.provider)) configure()
                    else startActivityForResult(Intent(AppWidgetManager.ACTION_APPWIDGET_BIND).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,widget).putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER,info.provider),1)
                } catch(_:RuntimeException) { failed() }
            }))
        }
    }
    private fun configure() {
        val info=AppWidgetManager.getInstance(this).getAppWidgetInfo(widget)
        if(info==null) { failed();return }
        if(info.configure==null) done() else try { HostedWidgets.host(this).startAppWidgetConfigureActivityForResult(this,widget,0,2,null) } catch(_:RuntimeException) { failed() }
    }
    private fun done() { delivered=true;setResult(RESULT_OK,Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,widget));finish() }
    private fun failed() { if(widget>0) HostedWidgets.delete(this,widget);widget=-1;Toast.makeText(this,"Widget was not added",Toast.LENGTH_SHORT).show() }
    override fun onSaveInstanceState(out:Bundle) { out.putInt("widget",widget);super.onSaveInstanceState(out) }
    @Deprecated("Deprecated in Android") override fun onActivityResult(request:Int,result:Int,data:Intent?) {
        super.onActivityResult(request,result,data)
        if(request !in 1..2) return
        if(result!=RESULT_OK) failed() else if(request==1) configure() else done()
    }
    override fun onDestroy() { if(isFinishing && !delivered && widget>0) HostedWidgets.delete(this,widget);HostedWidgets.destroy(this);super.onDestroy() }
}
