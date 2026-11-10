package local.civic.home;
import android.content.Context;import android.graphics.*;import android.view.View;

final class SolarIcon extends View {
 final Paint paint=new Paint(3);final int color;boolean daytime;SolarIcon(Context c,int color){super(c);this.color=color;paint.setColor(color);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(1.3f);paint.setStrokeCap(Paint.Cap.ROUND);}
 void setDaytime(boolean value){if(daytime!=value){daytime=value;invalidate();}}
 protected void onDraw(Canvas c){super.onDraw(c);c.save();float s=Math.min(getWidth()/24f,getHeight()/24f);c.translate((getWidth()-24*s)/2,(getHeight()-24*s)/2);c.scale(s,s);paint.setColor(color);c.drawArc(7,10,17,20,180,180,false,paint);c.drawLine(2.5f,15.5f,21.5f,15.5f,paint);c.drawLine(12,2,12,7,paint);float tip=daytime?7:2;float tail=4.5f;c.drawLine(10,tail,12,tip,paint);c.drawLine(14,tail,12,tip,paint);c.drawLine(5.5f,7.5f,7.2f,9.2f,paint);c.drawLine(18.5f,7.5f,16.8f,9.2f,paint);c.restore();}
}
