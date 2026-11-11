package app.flare.auto

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.widget.FrameLayout
import kotlin.math.abs

internal class PagePreview(c:Context,private val enabled:()->Boolean,private val drag:(Float,Boolean,Boolean)->Unit):FrameLayout(c) {
    private val slop=ViewConfiguration.get(c).scaledTouchSlop
    private var x=0f;private var y=0f;private var vertical=false;private var horizontal=false
    override fun onInterceptTouchEvent(e:MotionEvent):Boolean {
        when(e.actionMasked) {
            MotionEvent.ACTION_DOWN->{ x=e.x;y=e.y;vertical=false;horizontal=false }
            MotionEvent.ACTION_MOVE->if(enabled() && !horizontal) {
                val dx=abs(e.x-x);val dy=e.y-y
                if(dx>slop && dx>abs(dy)*1.3f) horizontal=true
                if(dy>slop && dy>dx*1.3f) vertical=true
            }
        }
        return vertical
    }
    override fun onTouchEvent(e:MotionEvent):Boolean {
        if(!enabled()) return false
        when(e.actionMasked) {
            MotionEvent.ACTION_DOWN->{ x=e.x;y=e.y;vertical=false;horizontal=false }
            MotionEvent.ACTION_MOVE->{ if(!horizontal && e.y-y>slop) vertical=true;if(vertical) drag((e.y-y).coerceAtLeast(0f),false,false) }
            MotionEvent.ACTION_UP->{ if(vertical) drag((e.y-y).coerceAtLeast(0f),true,false) else performClick();vertical=false }
            MotionEvent.ACTION_CANCEL->{ if(vertical) drag(0f,true,true);vertical=false }
        }
        return true
    }
    override fun performClick():Boolean { super.performClick();return true }
}

internal class PageTrash(c:Context):View(c) {
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply { style=Paint.Style.STROKE;strokeWidth=1.8f;strokeCap=Paint.Cap.ROUND;strokeJoin=Paint.Join.ROUND }
    var armed=false
        set(value) { if(field!=value) { field=value;invalidate() } }
    init { contentDescription="Delete page" }
    override fun onDraw(c:Canvas) {
        val s=minOf(width,height)/32f;c.save();c.translate((width-24*s)/2,(height-24*s)/2);c.scale(s,s)
        paint.color=if(armed) 0xffff3b30.toInt() else ThemeTokens.get(context).textSecondary
        c.drawLine(4f,6f,20f,6f,paint);c.drawRoundRect(6f,6f,18f,21f,2f,2f,paint)
        c.drawLine(9f,3f,15f,3f,paint);c.drawLine(9f,3f,9f,6f,paint);c.drawLine(15f,3f,15f,6f,paint)
        c.drawLine(10f,10f,10f,17f,paint);c.drawLine(14f,10f,14f,17f,paint);c.restore()
    }
}
