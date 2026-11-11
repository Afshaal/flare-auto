package app.flare.auto
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.SeekBar
import kotlin.math.abs
import kotlin.math.roundToInt

internal class GridBoard(context: Context, val tiles: MutableList<GridTile>, private val editing: Boolean = false) : FrameLayout(context) {
    var selection=-1
    var placement:GridTile?=null
    var onPlacement:((Int,Int)->Unit)?=null
    var onCorner:((Boolean)->Unit)?=null
    var onChanged: (() -> Unit)? = null
    var beforeChange: (() -> Unit)? = null
    var onChoose: ((Int) -> Unit)? = null
    var onSwipe: ((Int) -> Unit)? = null
    var onPageDrag: ((Float,Boolean,Boolean) -> Unit)? = null
    var onPageDeleteDrag: ((Float,Boolean,Boolean) -> Unit)? = null
    private var deleting = false
    private var deleteDownY = 0f
    private var corner = -1
    private var moving = false
    private var paging = false
    private val hold = Runnable {
        if(selected >= 0 && !paging && !deleting) { moving=true; performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS) }
    }
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val accent = ThemeTokens.get(context).accent
    private val density = resources.displayMetrics.density
    private var downX = 0f; private var downY = 0f
    private var selected = -1; private var resizing = false; private var seek = false
    private var candidate: GridTile? = null
    init { setWillNotDraw(false) }
    override fun onMeasure(ws: Int, hs: Int) {
        val w = MeasureSpec.getSize(ws); val h = MeasureSpec.getSize(hs)
        setMeasuredDimension(w, h)
        for (i in 0 until childCount) {
            val t = tiles.getOrNull(i) ?: continue
            getChildAt(i).measure(MeasureSpec.makeMeasureSpec((w * t.w / 8 - density * 8).toInt().coerceAtLeast(1), MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec((h * t.h / 6 - density * 8).toInt().coerceAtLeast(1), MeasureSpec.EXACTLY))
        }
    }
    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val gap = (density * 4).toInt()
        for (i in 0 until childCount) {
            val tile = tiles.getOrNull(i) ?: continue
            val child = getChildAt(i); val x = width * tile.x / 8 + gap; val y = height * tile.y / 6 + gap
            child.layout(x, y, x + child.measuredWidth, y + child.measuredHeight)
        }
    }
    override fun onDraw(c: Canvas) {
        super.onDraw(c)
        if (!editing) return
        paint.color = ThemeTokens.get(context).cardBorder; paint.strokeWidth = density
        for (x in 0..8) c.drawLine(width * x / 8f, 0f, width * x / 8f, height.toFloat(), paint)
        for (y in 0..6) c.drawLine(0f, height * y / 6f, width.toFloat(), height * y / 6f, paint)
    }
    override fun dispatchDraw(c: Canvas) {
        super.dispatchDraw(c)
        if (!editing) return
        paint.color = accent; paint.style = Paint.Style.STROKE; paint.strokeWidth = density * 2
        candidate?.let { c.drawRect(width * it.x / 8f, height * it.y / 6f, width * (it.x + it.w) / 8f, height * (it.y + it.h) / 6f, paint) }
        paint.style = Paint.Style.FILL
        tiles.getOrNull(selection)?.let {
            val l=width*it.x/8f+density*4; val r=width*(it.x+it.w)/8f-density*4
            val t=height*it.y/6f+density*4; val b=height*(it.y+it.h)/6f-density*4
            paint.style=Paint.Style.STROKE; c.drawRoundRect(l,t,r,b,density*12,density*12,paint); paint.style=Paint.Style.FILL
            c.drawCircle(l,(t+b)/2,density*6,paint); c.drawCircle((l+r)/2,t,density*6,paint)
            c.drawCircle(r,(t+b)/2,density*6,paint); c.drawCircle((l+r)/2,b,density*6,paint)
        }
        placement?.let { tile ->
            paint.color=(accent and 0x00ffffff) or 0x22000000
            for(y in 0..6-tile.h) for(x in 0..8-tile.w) {
                val next=tile.copy(x=x,y=y)
                if(tiles.filter { it!==placement }.none { it.overlaps(next) }) c.drawRect(width*x/8f,height*y/6f,width*(x+tile.w)/8f,height*(y+tile.h)/6f,paint)
            }
        }
    }

    private fun hitsSeek(view: View, x: Float, y: Float): Boolean {
        if ((view is SeekBar || view is android.appwidget.AppWidgetHostView || view is OfflineMapCard) && view.visibility == View.VISIBLE) return true
        if (view is ViewGroup) for (i in 0 until view.childCount) {
            val v = view.getChildAt(i)
            if (x >= v.left && x < v.left+v.width*v.scaleX && y >= v.top && y < v.top+v.height*v.scaleY && hitsSeek(v, (x-v.left)/v.scaleX.coerceAtLeast(.01f), (y-v.top)/v.scaleY.coerceAtLeast(.01f))) return true
        }
        return false
    }
    override fun onInterceptTouchEvent(e: MotionEvent): Boolean {
        if (editing) return true
        if (e.actionMasked == MotionEvent.ACTION_DOWN) { downX=e.x; downY=e.y; seek=hitsSeek(this,e.x,e.y) }
        return !seek && e.actionMasked == MotionEvent.ACTION_MOVE && abs(e.x-downX)>density*12 && abs(e.x-downX)>abs(e.y-downY)*1.3f
    }
    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (!editing) {
            val dx=e.x-downX
            if(e.actionMasked==MotionEvent.ACTION_MOVE) onPageDrag?.invoke(dx,false,false)
            if(e.actionMasked==MotionEvent.ACTION_UP) {
                if(onPageDrag!=null) onPageDrag?.invoke(dx,true,false)
                else if(abs(dx)>density*60) onSwipe?.invoke(if(dx<0) 1 else -1)
            }
            if(e.actionMasked==MotionEvent.ACTION_CANCEL) onPageDrag?.invoke(dx,true,true)
            return true
        }
        if (width == 0 || height == 0) return true
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX=e.x; downY=e.y; deleteDownY=e.rawY
                selected=tiles.indexOfFirst { e.x>=width*it.x/8f && e.x<width*(it.x+it.w)/8f && e.y>=height*it.y/6f && e.y<height*(it.y+it.h)/6f }
                candidate=tiles.getOrNull(selected)
                moving=false; paging=false; deleting=false; corner=-1
                if(selected==selection) candidate?.let {
                    val l=width*it.x/8f; val r=width*(it.x+it.w)/8f; val t=height*it.y/6f; val b=height*(it.y+it.h)/6f
                    val hit=density*26
                    corner=when {
                        abs(e.x-l)<hit && abs(e.y-(t+b)/2)<hit -> 0
                        abs(e.y-t)<hit && abs(e.x-(l+r)/2)<hit -> 1
                        abs(e.x-r)<hit && abs(e.y-(t+b)/2)<hit -> 2
                        abs(e.y-b)<hit && abs(e.x-(l+r)/2)<hit -> 3
                        else -> -1
                    }
                }
                resizing=corner>=0
                if(!resizing && placement==null) postDelayed(hold,350)
                parent?.requestDisallowInterceptTouchEvent(true)
            }
            MotionEvent.ACTION_MOVE -> {
                val dx=e.x-downX; val dy=e.y-downY
                if(!moving && !resizing && !paging && !deleting && placement==null && onPageDeleteDrag!=null && dy>density*12 && dy>abs(dx)*1.3f) { removeCallbacks(hold);deleting=true;candidate=null }
                if(!deleting && !moving && !resizing && abs(dx)>density*12 && abs(dx)>abs(dy)*1.3f) { removeCallbacks(hold); paging=true }
                if(deleting) onPageDeleteDrag?.invoke((e.rawY-deleteDownY).coerceAtLeast(0f),false,false)
                else if(paging) onPageDrag?.invoke(dx,false,false)
                else if(moving || resizing) tiles.getOrNull(selected)?.let { old ->
                    val gx=(dx*8/width).roundToInt(); val gy=(dy*6/height).roundToInt()
                    candidate=if(resizing) old.resizeEdge(corner,gx,gy)
                        else old.copy(x=(old.x+gx).coerceIn(0,8-old.w),y=(old.y+gy).coerceIn(0,6-old.h))
                    invalidate()
                }
            }
            MotionEvent.ACTION_UP -> {
                removeCallbacks(hold)
                if(deleting) { onPageDeleteDrag?.invoke((e.rawY-deleteDownY).coerceAtLeast(0f),true,false);deleting=false;parent?.requestDisallowInterceptTouchEvent(false);return true }
                if(paging) { onPageDrag?.invoke(e.x-downX,true,false); candidate=null; return true }
                val moved=abs(e.x-downX)+abs(e.y-downY)>density*12
                val next=candidate
                if (moved && next!=null && selected>=0 && next!=tiles[selected]) {
                    val copy=tiles.toMutableList().apply { set(selected,next) }
                    if (GridTile.validPage(copy)) { beforeChange?.invoke(); tiles[selected]=next; onChanged?.invoke() }
                    else android.widget.Toast.makeText(context,"Cards cannot overlap",android.widget.Toast.LENGTH_SHORT).show()
                } else if (!moved) {
                    performClick()
                    if(placement!=null) onPlacement?.invoke((e.x*8/width).toInt(),(e.y*6/height).toInt())
                    else {
                        selection=selected; onChoose?.invoke(selected)
                        tiles.getOrNull(selected)?.let { tile ->
                            if(tile.type.startsWith("clock") && e.y<height*(tile.y+1)/6f) onCorner?.invoke(e.x<width*(tile.x+tile.w/2f)/8f)
                        }; invalidate()
                    }
                }
                candidate=null; invalidate(); parent?.requestDisallowInterceptTouchEvent(false)
            }
            MotionEvent.ACTION_CANCEL -> { removeCallbacks(hold);if(deleting) onPageDeleteDrag?.invoke(0f,true,true);deleting=false; onPageDrag?.invoke(e.x-downX,true,true); candidate=null; invalidate(); parent?.requestDisallowInterceptTouchEvent(false) }
        }
        return true
    }
    override fun onDetachedFromWindow() { removeCallbacks(hold); super.onDetachedFromWindow() }
    override fun performClick(): Boolean { super.performClick(); return true }
}
