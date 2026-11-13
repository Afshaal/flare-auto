package app.flare.auto

import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.widget.LinearLayout
import kotlin.math.abs
import kotlin.math.roundToInt

/** Long-press drag to reorder dock cells along the dock's own axis (across for the bottom dock, down for a side dock). */
internal class EditorDockDrag(private val dock:LinearLayout,private val scale:()->Float,private val commit:(Int,Int)->Unit,private val settled:()->Unit) {
    private val cells=List(dock.childCount) { dock.getChildAt(it) }
    private val slop=ViewConfiguration.get(dock.context).scaledTouchSlop
    private val lift=6*dock.resources.displayMetrics.density
    private var source=-1
    private var target=-1
    private var downX=0f
    private var downY=0f
    private var dragging=false
    private var moved=false
    private var settling=false
    private var duration=0L
    /** Set by [touch] when the gesture was a plain tap; the touch listener then performs the click. */
    private var tapped=false
    private val vertical=dock.orientation==LinearLayout.VERTICAL
    private fun start(v:View)=if(vertical) v.top else v.left
    private fun end(v:View)=if(vertical) v.bottom else v.right
    private fun length(v:View)=if(vertical) v.height else v.width
    private fun shift(v:View)=if(vertical) v.translationY else v.translationX
    private fun shift(v:View,value:Float) { if(vertical) v.translationY=value else v.translationX=value }
    private fun along(e:MotionEvent)=if(vertical) e.rawY-downY else e.rawX-downX
    private val hold=Runnable {
        if(source>=0 && !moved) {
            dragging=true;duration=if(Motion.enabled(dock.context)) 160L else 0L
            cells[source].apply {
                elevation=lift*2
                // The bottom dock lifts its cell; a side dock keeps it in its column, which is the drag axis.
                animate().scaleX(1.08f).scaleY(1.08f).translationY(if(vertical) 0f else -lift).setDuration(duration).start()
                performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
            }
        }
    }
    init {
        dock.clipChildren=false;dock.clipToPadding=false
        cells.forEachIndexed { index,cell->cell.setOnTouchListener { v,e->
            val handled=touch(index,e)
            if(tapped) { tapped=false;v.performClick() }
            handled
        } }
        dock.addOnAttachStateChangeListener(object:View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v:View)=Unit
            override fun onViewDetachedFromWindow(v:View) {
                dock.removeCallbacks(hold)
                cells.forEach { it.animate().withEndAction(null).cancel() }
            }
        })
    }
    private fun touch(index:Int,e:MotionEvent):Boolean {
        if(settling) return true
        when(e.actionMasked) {
            MotionEvent.ACTION_DOWN->{
                source=index;target=index;downX=e.rawX;downY=e.rawY;dragging=false;moved=false
                dock.parent?.requestDisallowInterceptTouchEvent(true)
                dock.postDelayed(hold,ViewConfiguration.getLongPressTimeout().toLong())
            }
            MotionEvent.ACTION_MOVE->{
                if(!dragging) {
                    if(abs(e.rawX-downX)+abs(e.rawY-downY)>slop) { moved=true;dock.removeCallbacks(hold) }
                } else {
                    val cell=cells[source]
                    val room=if(vertical) dock.height else dock.width
                    shift(cell,(along(e)/scale().coerceAtLeast(.01f)).coerceIn(-start(cell).toFloat(),(room-end(cell)).toFloat()))
                    val next=(source+shift(cell)/length(cell).coerceAtLeast(1)).roundToInt().coerceIn(cells.indices)
                    if(next!=target) {
                        target=next
                        cells.forEachIndexed { n,v->if(n!=source) {
                            val shift=when {
                                source<target && n in source+1..target->-length(cell).toFloat()
                                target<source && n in target until source->length(cell).toFloat()
                                else->0f
                            }
                            if(vertical) v.animate().translationY(shift).setDuration(duration).start()
                            else v.animate().translationX(shift).setDuration(duration).start()
                        } }
                    }
                }
            }
            MotionEvent.ACTION_UP,MotionEvent.ACTION_CANCEL->{
                dock.removeCallbacks(hold);dock.parent?.requestDisallowInterceptTouchEvent(false)
                if(dragging) {
                    val from=source;val to=if(e.actionMasked==MotionEvent.ACTION_CANCEL) source else target
                    settling=true
                    if(to!=from) commit(cells[from].tag as Int,cells[to].tag as Int)
                    if(to==from) cells.forEach { (if(vertical) it.animate().translationY(0f) else it.animate().translationX(0f)).setDuration(duration).start() }
                    val offset=(start(cells[to])-start(cells[from])).toFloat()
                    (if(vertical) cells[from].animate().translationY(offset) else cells[from].animate().translationX(offset).translationY(0f))
                        .scaleX(1f).scaleY(1f).setDuration(duration).withEndAction {
                            cells[from].elevation=0f;settling=false;source=-1
                            if(to!=from) settled()
                        }.start()
                } else if(e.actionMasked==MotionEvent.ACTION_UP && !moved) tapped=true
                dragging=false
            }
        }
        return true
    }
}
