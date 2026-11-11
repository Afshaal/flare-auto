package app.flare.auto

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.view.View

internal class EditorIcon(c:Context,kind:String,color:Int,private val filled:Boolean=false):View(c) {
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color=color;strokeWidth=1.7f;strokeCap=Paint.Cap.ROUND;strokeJoin=Paint.Join.ROUND }
    private val path=Path().apply {
        when(kind) {
            "back","left"->{ moveTo(14f,5f);lineTo(7f,12f);lineTo(14f,19f);if(kind=="back") { moveTo(7f,12f);lineTo(21f,12f) } }
            "right"->{ moveTo(9f,5f);lineTo(16f,12f);lineTo(9f,19f) }
            "add"->{ moveTo(12f,5f);lineTo(12f,19f);moveTo(5f,12f);lineTo(19f,12f) }
            "save"->{ moveTo(5f,12f);lineTo(10f,17f);lineTo(20f,7f) }
            "layouts"->{ addRoundRect(4f,4f,11f,20f,1.5f,1.5f,Path.Direction.CW);addRoundRect(14f,4f,20f,11f,1.5f,1.5f,Path.Direction.CW);addRoundRect(14f,14f,20f,20f,1.5f,1.5f,Path.Direction.CW) }
            "main"->{ moveTo(12f,3f);lineTo(14.8f,8.7f);lineTo(21f,9.6f);lineTo(16.5f,14f);lineTo(17.6f,20.3f);lineTo(12f,17.3f);lineTo(6.4f,20.3f);lineTo(7.5f,14f);lineTo(3f,9.6f);lineTo(9.2f,8.7f);close() }
            "reset"->{ arcTo(4f,4f,20f,20f,210f,300f,false);moveTo(4f,3f);lineTo(4f,9f);lineTo(10f,9f) }
            "appearance"->{
                moveTo(5f,4f);lineTo(5f,20f);moveTo(12f,4f);lineTo(12f,20f);moveTo(19f,4f);lineTo(19f,20f)
                addCircle(5f,9f,2f,Path.Direction.CW);addCircle(12f,15f,2f,Path.Direction.CW);addCircle(19f,8f,2f,Path.Direction.CW)
            }
        }
    }
    override fun onDraw(c:Canvas) {
        val side=minOf(width,height)*.48f;val s=side/24f
        c.save();c.translate((width-side)/2,(height-side)/2);c.scale(s,s)
        paint.style=if(filled) Paint.Style.FILL else Paint.Style.STROKE;c.drawPath(path,paint);c.restore()
    }
}
