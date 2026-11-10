package app.flare.auto;
import android.graphics.*;import android.graphics.drawable.Drawable;
final class GlassDrawable extends Drawable {
 final int fill,border;final float radius;final Paint p=new Paint(3);final RectF r=new RectF();
 GlassDrawable(int fill,float radius,int border){this.fill=fill;this.radius=radius;this.border=border;}
 public void draw(Canvas c){Rect b=getBounds();r.set(b.left+1,b.top+1,b.right-1,b.bottom-1);p.setShader(null);p.setStyle(Paint.Style.FILL);p.setColor(fill);c.drawRoundRect(r,radius,radius,p);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(1);p.setColor(border);c.drawRoundRect(r,radius,radius,p);}
 public void setAlpha(int a){}public void setColorFilter(ColorFilter f){}public int getOpacity(){return PixelFormat.TRANSLUCENT;}
}
