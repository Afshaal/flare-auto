package app.flare.auto

import android.app.Activity
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.content.Context
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import java.util.WeakHashMap

internal object HostedWidgets {
    const val HOST_ID=1079
    private val hosts=WeakHashMap<Activity,AppWidgetHost>()
    fun host(a:Activity)=hosts.getOrPut(a) { AppWidgetHost(a,HOST_ID) }
    fun id(type:String)=if(type.startsWith("widget:")) type.substringAfter(':').toIntOrNull()?.takeIf { it>0 } else null
    fun ids(raw:String?)=DashboardPages.decode(raw)?.flatten()?.mapNotNull { id(it.type) }?.toSet().orEmpty()
    fun delete(c:Context,id:Int) { try { AppWidgetHost(c.applicationContext,HOST_ID).deleteAppWidgetId(id) } catch(_:RuntimeException) { } }
    fun start(a:Activity) { try { host(a).startListening() } catch(_:RuntimeException) { } }
    fun stop(a:Activity) { try { hosts[a]?.stopListening() } catch(_:RuntimeException) { } }
    fun destroy(a:Activity) { stop(a);hosts.remove(a) }
    fun view(a:Activity,c:Context,type:String,preview:Boolean):View {
        val id=id(type) ?: return TextView(c)
        val info=AppWidgetManager.getInstance(c).getAppWidgetInfo(id)
        if(info!=null && !preview) {
            try {
                return host(a).createView(c,id,info).apply {
                    setPadding(0,0,0,0)
                    addOnLayoutChangeListener { _,l,t,r,b,ol,ot,or,ob->
                        if(r-l!=or-ol || b-t!=ob-ot) {
                            val d=resources.displayMetrics.density
                            val w=((r-l)/d).toInt(); val h=((b-t)/d).toInt()
                            if(android.os.Build.VERSION.SDK_INT>=31) updateAppWidgetSize(android.os.Bundle(),listOf(android.util.SizeF(w.toFloat(),h.toFloat())))
                            else @Suppress("DEPRECATION") updateAppWidgetSize(null,w,h,w,h)
                        }
                    }
                }
            } catch(_:RuntimeException) { }
        }
        val theme=ThemeTokens.get(c)
        return LinearLayout(c).apply {
            orientation=LinearLayout.VERTICAL;gravity=Gravity.CENTER
            background=GlassDrawable(theme.card,12*resources.displayMetrics.density,theme.cardBorder)
            if(info!=null) {
                addView(ImageView(c).apply { setImageDrawable(info.loadPreviewImage(c,0) ?: info.loadIcon(c,0));scaleType=ImageView.ScaleType.FIT_CENTER },LinearLayout.LayoutParams(MATCH,0,1f))
            }
            addView(TextView(c).apply { text=info?.loadLabel(c.packageManager) ?: "Widget unavailable";setTextColor(theme.textPrimary);textSize=14f;gravity=Gravity.CENTER },LinearLayout.LayoutParams(MATCH,WRAP))
        }
    }
}
