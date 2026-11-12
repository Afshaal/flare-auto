package app.flare.auto
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.View
internal class ProfileAvatar(c:Context,private val style:Int,private val color:Int):View(c) {
    private val p=Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeWidth=2f; strokeCap=Paint.Cap.ROUND; strokeJoin=Paint.Join.ROUND }
    override fun onDraw(canvas:Canvas) {
        canvas.save(); val size=minOf(width,height).toFloat(); canvas.translate((width-size)/2,(height-size)/2); canvas.scale(size/48,size/48)
        p.color=color;p.style=Paint.Style.STROKE
        // "Add New": a plus in a circle, drawn on the same 48-unit grid and size as the avatars.
        if(style==ADD) { canvas.drawCircle(24f,24f,19f,p);canvas.drawLine(24f,15f,24f,33f,p);canvas.drawLine(15f,24f,33f,24f,p);canvas.restore();return }
        when(style%6) {
            0->{ canvas.drawCircle(24f,15f,7f,p); canvas.drawArc(10f,24f,38f,47f,180f,180f,false,p) }
            1->{ canvas.drawCircle(24f,24f,17f,p);canvas.drawOval(8f,15f,40f,27f,p);canvas.drawLine(24f,25f,24f,41f,p);canvas.drawLine(9f,23f,19f,33f,p);canvas.drawLine(39f,23f,29f,33f,p) }
            2->{ canvas.drawCircle(24f,24f,15f,p); canvas.drawOval(4f,18f,44f,30f,p);canvas.drawCircle(36f,8f,2f,p) }
            3->{ canvas.drawLine(7f,38f,22f,10f,p);canvas.drawLine(22f,10f,39f,38f,p);canvas.drawLine(7f,38f,39f,38f,p);canvas.drawLine(17f,20f,27f,20f,p) }
            4->{ canvas.drawCircle(24f,24f,12f,p);for(i in 0..7){ val a=i*Math.PI/4;canvas.drawLine((24+16*kotlin.math.cos(a)).toFloat(),(24+16*kotlin.math.sin(a)).toFloat(),(24+20*kotlin.math.cos(a)).toFloat(),(24+20*kotlin.math.sin(a)).toFloat(),p) } }
            else->{ canvas.drawRoundRect(7f,16f,41f,37f,5f,5f,p);canvas.drawLine(12f,16f,18f,8f,p);canvas.drawLine(18f,8f,30f,8f,p);canvas.drawLine(30f,8f,36f,16f,p);canvas.drawCircle(15f,29f,2f,p);canvas.drawCircle(33f,29f,2f,p) }
        };canvas.restore()
    }
    companion object { const val ADD=-1 }
}
