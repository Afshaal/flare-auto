package app.flare.auto

import android.app.Activity
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.RemoteViews

class WidgetProbeProvider:AppWidgetProvider() {
    override fun onUpdate(c:Context,m:AppWidgetManager,ids:IntArray) {
        ids.forEach { id->m.updateAppWidget(id,RemoteViews(c.packageName,android.R.layout.simple_list_item_1).apply {
            setTextViewText(android.R.id.text1,"Hosted widget active #$id");setTextColor(android.R.id.text1,android.graphics.Color.WHITE)
            setInt(android.R.id.text1,"setBackgroundColor",0xff26313b.toInt())
            setOnClickPendingIntent(android.R.id.text1,PendingIntent.getActivity(c,id,Intent(c,SettingsActivity::class.java),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
        }) }
    }
}
class WidgetProbeConfig:Activity() {
    override fun onCreate(state:Bundle?) {
        super.onCreate(state);setResult(RESULT_CANCELED)
        val id=intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,-1)
        setContentView(Button(this).apply { text="Complete widget setup";setOnClickListener {
            WidgetProbeProvider().onUpdate(this@WidgetProbeConfig,AppWidgetManager.getInstance(this@WidgetProbeConfig),intArrayOf(id))
            setResult(RESULT_OK,Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,id));finish()
        } })
    }
}
