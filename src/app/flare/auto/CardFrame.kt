package app.flare.auto

import android.content.Context
import android.view.View
import android.widget.FrameLayout

internal class CardFrame(context:Context,child:View,type:String):FrameLayout(context) {
    private val native=type=="offline_map" || HostedWidgets.id(type)!=null
    private val minW=resources.displayMetrics.density*(if(type=="apps") 150 else 225)
    private val minH=resources.displayMetrics.density*when(type) { "clock" -> 245; "media", "media_wide", "media_compact" -> 180; "projection" -> 76; "apps" -> 160; else -> 120 }
    private var scale=1f
    init { addView(child); child.pivotX=0f; child.pivotY=0f; clipChildren=true }
    override fun onMeasure(ws:Int,hs:Int) {
        val w=MeasureSpec.getSize(ws); val h=MeasureSpec.getSize(hs)
        setMeasuredDimension(w,h)
        scale=if(native) 1f else minOf(1f,w/minW,h/minH).coerceAtLeast(.01f)
        getChildAt(0).measure(MeasureSpec.makeMeasureSpec((w/scale).toInt(),MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec((h/scale).toInt(),MeasureSpec.EXACTLY))
    }
    override fun onLayout(changed:Boolean,l:Int,t:Int,r:Int,b:Int) {
        getChildAt(0).apply { layout(0,0,measuredWidth,measuredHeight); scaleX=scale; scaleY=scale }
    }
}
