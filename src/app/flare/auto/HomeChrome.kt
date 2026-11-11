package app.flare.auto

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

internal object HomeChrome {
    private fun dp(c:Context,n:Int)=(n*c.resources.displayMetrics.density+.5f).toInt()
    private fun text(c:Context,p:SharedPreferences,value:String,size:Int,color:Int)=TextView(c).apply {
        text=value;textSize=size*p.getInt("text",100)/100f;setTextColor(color);gravity=Gravity.CENTER_VERTICAL
    }
    fun top(c:Context,p:SharedPreferences,t:ThemeTokens):LinearLayout = LinearLayout(c).apply {
        gravity=Gravity.CENTER_VERTICAL
        addView(text(c,p,"",19,t.textPrimary).apply { setTypeface(Typeface.DEFAULT,Typeface.BOLD) },LinearLayout.LayoutParams(0,dp(c,40),1f))
        addView(DeviceStatus(c,false),LinearLayout.LayoutParams(dp(c,24),dp(c,24)))
        addView(WifiStatus(c),LinearLayout.LayoutParams(dp(c,24),dp(c,24)).apply { leftMargin=dp(c,14) })
        addView(DeviceStatus(c,true),LinearLayout.LayoutParams(dp(c,24),dp(c,24)).apply { leftMargin=dp(c,14) })
    }
    fun accent(t:ThemeTokens):GradientDrawable {
        val end=(t.accent and 0x00ffffff) or 0x18000000
        val middle=Color.rgb((Color.red(t.accent)*7+Color.red(t.textSecondary)*3)/10,
            (Color.green(t.accent)*7+Color.green(t.textSecondary)*3)/10,(Color.blue(t.accent)*7+Color.blue(t.textSecondary)*3)/10)
        return GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,intArrayOf(end,middle,end))
    }
    fun dock(c:Context,p:SharedPreferences,t:ThemeTokens,click:(Int,DockConfig.Item)->Unit):LinearLayout {
        val nav=LinearLayout(c).apply {
            gravity=Gravity.CENTER_VERTICAL
            background=if(p.getBoolean("glass_cards",true)) HomeGlassDrawable(t.card,t.cardBorder,dp(c,14).toFloat())
                else GlassDrawable(t.card,dp(c,14).toFloat(),t.cardBorder)
        }
        val items=DockConfig.read(p);val oldSize=p.getInt("dock_icons",24)
        val size=DockConfig.size(c,items.count { it.visible },if(oldSize<23) 20 else if(oldSize<30) 26 else 32)
        items.forEachIndexed { index,item->if(item.visible) {
            val cell=LinearLayout(c).apply { orientation=LinearLayout.VERTICAL;gravity=Gravity.CENTER;tag=index;contentDescription=DockConfig.name(c,item) }
            val icon=if(item.type==5) ImageView(c).apply {
                try { setImageDrawable(AsyncImages.appIcon(c,item.target)) } catch(_:Exception) { }
                if(p.getBoolean("dock_mono",false)) setColorFilter(android.graphics.ColorMatrixColorFilter(android.graphics.ColorMatrix().apply { setSaturation(0f) }))
            } else HomeActivity.NavIcon(c,DockConfig.ICONS[item.type],t.textPrimary)
            cell.addView(icon,LinearLayout.LayoutParams(dp(c,size),dp(c,size)))
            cell.addView(text(c,p,DockConfig.name(c,item),14,t.textPrimary).apply {
                gravity=Gravity.CENTER;includeFontPadding=false;isSingleLine=true;ellipsize=android.text.TextUtils.TruncateAt.END
                visibility=if(p.getBoolean("dock_labels",true)) View.VISIBLE else View.GONE
            },LinearLayout.LayoutParams(-1,-2).apply { topMargin=dp(c,4) })
            cell.setOnClickListener { click(index,item) };nav.addView(cell,LinearLayout.LayoutParams(0,-1,1f))
        } }
        return nav
    }
}
