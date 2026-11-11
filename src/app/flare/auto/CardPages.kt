package app.flare.auto

import android.app.Activity
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import kotlin.math.abs

internal class CardPages(private val activity:Activity,private val page:Int,private val first:Int,private val last:Int,
    private val factory:(Int,CardPages)->View,private val committed:(Int)->Unit):FrameLayout(activity) {
    private var old:View?=null
    private var incoming:View?=null
    private var direction=0
    private var settling=false
    init { clipChildren=true; old=factory(page,this); addView(old,LayoutParams(MATCH,MATCH)) }
    fun drag(dx:Float) {
        if(settling || width==0) return
        val dir=if(dx<0) 1 else -1
        if(incoming==null) {
            if(page+dir !in first..last || abs(dx)<resources.displayMetrics.density*12) return
            direction=dir; incoming=factory(page+dir,this).also { addView(it,LayoutParams(MATCH,MATCH)) }
        }
        val distance=(dx*direction).coerceIn(-width.toFloat(),0f)*direction
        val fraction=(abs(distance)/width).coerceIn(0f,1f)
        if(Motion.enabled(activity)) {
            old?.translationX=distance; old?.alpha=1f-.35f*fraction
            incoming?.translationX=distance+direction*width; incoming?.alpha=.65f+.35f*fraction
        }
    }
    fun finish(dx:Float,cancel:Boolean=false) {
        val next=incoming ?: return
        if(settling) return
        val commit=!cancel && dx*direction < -width*.18f
        settling=true
        fun done() {
            if(commit) committed(page+direction)
            else { removeView(next); incoming=null; old?.translationX=0f; old?.alpha=1f; settling=false }
        }
        if(!Motion.enabled(activity)) { done(); return }
        old?.animate()?.translationX(if(commit) -direction*width.toFloat() else 0f)?.alpha(if(commit) .65f else 1f)
            ?.setDuration(340)?.setInterpolator(DecelerateInterpolator())?.start()
        next.animate().translationX(if(commit) 0f else direction*width.toFloat()).alpha(if(commit) 1f else .65f)
            .setDuration(340).setInterpolator(DecelerateInterpolator()).withEndAction { done() }.start()
    }
    fun connect(board:GridBoard) { board.onPageDrag={ dx,up,cancel -> if(up) finish(dx,cancel) else drag(dx) } }
    fun connectPlaceholder(view:View) {
        var x=0f; var y=0f; var swiping=false
        view.setOnTouchListener { v,e ->
            val dx=e.x-x
            when(e.actionMasked) {
                MotionEvent.ACTION_DOWN -> { x=e.x; y=e.y; swiping=false; true }
                MotionEvent.ACTION_MOVE -> {
                    if(abs(dx)>resources.displayMetrics.density*12 && abs(dx)>abs(e.y-y)*1.3f) swiping=true
                    if(swiping) drag(dx); true
                }
                MotionEvent.ACTION_UP -> { if(swiping) finish(dx) else v.performClick(); true }
                MotionEvent.ACTION_CANCEL -> { finish(dx,true); true }
                else -> true
            }
        }
    }
    override fun dispatchTouchEvent(e:MotionEvent):Boolean = if(settling) true else super.dispatchTouchEvent(e)
    override fun onWindowVisibilityChanged(visibility:Int) {
        super.onWindowVisibilityChanged(visibility)
        if(visibility!=View.VISIBLE) {
            old?.animate()?.cancel(); incoming?.animate()?.cancel()
            incoming?.let { removeView(it) }; incoming=null; settling=false
            old?.translationX=0f; old?.alpha=1f
        }
    }
    override fun onDetachedFromWindow() {
        old?.animate()?.cancel(); incoming?.animate()?.cancel()
        super.onDetachedFromWindow()
    }
}
