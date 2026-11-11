package app.flare.auto

import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.widget.LinearLayout
import kotlin.math.abs
import kotlin.math.roundToInt

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
    private val hold=Runnable {
        if(source>=0 && !moved) {
            dragging=true;duration=if(Motion.enabled(dock.context)) 160L else 0L
            cells[source].apply {
                elevation=lift*2
                animate().scaleX(1.08f).scaleY(1.08f).translationY(-lift).setDuration(duration).start()
                performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
            }
        }
    }
    init {
        dock.clipChildren=false;dock.clipToPadding=false
        cells.forEachIndexed { index,cell->cell.setOnTouchListener { _,e->touch(index,e) } }
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
                    cell.translationX=((e.rawX-downX)/scale().coerceAtLeast(.01f)).coerceIn(-cell.left.toFloat(),(dock.width-cell.right).toFloat())
                    val next=(source+cell.translationX/cell.width.coerceAtLeast(1)).roundToInt().coerceIn(cells.indices)
                    if(next!=target) {
                        target=next
                        cells.forEachIndexed { n,v->if(n!=source) {
                            val shift=when {
                                source<target && n in source+1..target->-cell.width.toFloat()
                                target<source && n in target until source->cell.width.toFloat()
                                else->0f
                            }
                            v.animate().translationX(shift).setDuration(duration).start()
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
                    if(to==from) cells.forEach { it.animate().translationX(0f).setDuration(duration).start() }
                    cells[from].animate().translationX((cells[to].left-cells[from].left).toFloat()).translationY(0f).scaleX(1f).scaleY(1f)
                        .setDuration(duration).withEndAction {
                            cells[from].elevation=0f;settling=false;source=-1
                            if(to!=from) settled()
                        }.start()
                } else if(e.actionMasked==MotionEvent.ACTION_UP && !moved) cells[index].performClick()
                dragging=false
            }
        }
        return true
    }
}
