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
    /** Told when a swipe starts (true) and ends (false), so the live page can pause its own updates meanwhile. */
    var onDragging:((Boolean)->Unit)?=null
    /** A swipe is in progress (finger down or settling). */
    val dragging get()=incoming!=null
    init { clipChildren=true; old=factory(page,this); addView(old,LayoutParams(MATCH,MATCH)) }
    fun drag(dx:Float) {
        if(settling || width==0) return
        val dir=if(dx<0) 1 else -1
        if(incoming==null) {
            if(page+dir !in first..last || abs(dx)<resources.displayMetrics.density*12) return
            direction=dir
            // Animations off: nothing slides, so the next page is not built or shown during the drag (it used to be
            // drawn unmoved on top of this one); a stand-in marks the swipe and the release switches pages directly.
            incoming=if(Motion.enabled(activity)) factory(page+dir,this).also { addView(it,LayoutParams(MATCH,MATCH)) } else View(activity)
            // While the two pages move and fade, draw each from a GPU layer instead of redrawing every card per frame;
            // not for a page with a live map surface, whose every map frame would re-render the whole layer.
            if(Motion.enabled(activity)) listOf(old,incoming).forEach { v-> if(v!=null && !hasSurface(v)) v.setLayerType(View.LAYER_TYPE_HARDWARE,null) }
            onDragging?.invoke(true)
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
            old?.setLayerType(View.LAYER_TYPE_NONE,null);next.setLayerType(View.LAYER_TYPE_NONE,null)
            // Commit: Home rebuilds the page (live cards); posted so the last frame of the slide is drawn first.
            if(commit) post { committed(page+direction) }
            else { removeView(next); incoming=null; old?.translationX=0f; old?.alpha=1f; settling=false;onDragging?.invoke(false) }
        }
        if(!Motion.enabled(activity)) { done(); return }
        old?.animate()?.translationX(if(commit) -direction*width.toFloat() else 0f)?.alpha(if(commit) .65f else 1f)
            ?.setDuration(340)?.setInterpolator(DecelerateInterpolator())?.start()
        next.animate().translationX(if(commit) 0f else direction*width.toFloat()).alpha(if(commit) 1f else .65f)
            .setDuration(340).setInterpolator(DecelerateInterpolator()).withEndAction { done() }.start()
    }
    private fun hasSurface(v:View):Boolean = v is android.view.TextureView || v is android.view.SurfaceView ||
        (v is android.view.ViewGroup && (0 until v.childCount).any { hasSurface(v.getChildAt(it)) })
    fun connect(board:GridBoard) { board.onPageDrag={ dx,up,cancel -> if(up) finish(dx,cancel) else drag(dx) } }
    fun connectPlaceholder(view:View) {
        var x=0f; var y=0f; var swiping=false
        // Screen coordinates: this view is the page being moved, so its own coordinates shift with the drag.
        fun scale():Float { var s=1f; var p:View?=view; while(p!=null) { s*=p.scaleX; p=p.parent as? View }; return s.coerceAtLeast(.01f) }
        view.setOnTouchListener { v,e ->
            val dx=(e.rawX-x)/scale()
            when(e.actionMasked) {
                MotionEvent.ACTION_DOWN -> { x=e.rawX; y=e.rawY; swiping=false; true }
                MotionEvent.ACTION_MOVE -> {
                    if(abs(dx)>resources.displayMetrics.density*12 && abs(dx)>abs(e.rawY-y)/scale()*1.3f) swiping=true
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
            old?.setLayerType(View.LAYER_TYPE_NONE,null)
            val wasDragging=incoming!=null
            incoming?.let { removeView(it) }; incoming=null; settling=false
            if(wasDragging) onDragging?.invoke(false)
            old?.translationX=0f; old?.alpha=1f
        }
    }
    override fun onDetachedFromWindow() {
        old?.animate()?.cancel(); incoming?.animate()?.cancel()
        super.onDetachedFromWindow()
    }
}
