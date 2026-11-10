package app.flare.auto;
import android.graphics.*;import android.graphics.drawable.Drawable;

final class HomeGlassDrawable extends Drawable {
 private final int fill,border;private final float radius;private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);private final RectF rect=new RectF();private int alpha=255;private LinearGradient wash,rim;
 @Override protected void onBoundsChange(Rect b){rect.set(b.left+1,b.top+1,b.right-1,b.bottom-1);wash=new LinearGradient(0,rect.top,0,rect.bottom,0x08ffffff,0x00ffffff,Shader.TileMode.CLAMP);rim=new LinearGradient(0,rect.top,0,rect.bottom,0x28ffffff,0x06ffffff,Shader.TileMode.CLAMP);}
 HomeGlassDrawable(int color,int border,float radius){this.fill=0xe6000000|((Color.red(color)*3/4)<<16)|((Color.green(color)*3/4)<<8)|(Color.blue(color)*3/4);this.border=border;this.radius=radius;}
 @Override public void draw(Canvas canvas){Rect b=getBounds();if(b.width()<=2||b.height()<=2)return;rect.set(b.left+1,b.top+1,b.right-1,b.bottom-1);paint.setStyle(Paint.Style.FILL);paint.setShader(null);paint.setColor(fill);paint.setAlpha(Color.alpha(fill)*alpha/255);canvas.drawRoundRect(rect,radius,radius,paint);paint.setShader(wash);paint.setAlpha(alpha);canvas.drawRoundRect(rect,radius,radius,paint);paint.setShader(null);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(1);paint.setColor(border);paint.setAlpha(Color.alpha(border)*alpha/255);canvas.drawRoundRect(rect,radius,radius,paint);paint.setShader(rim);paint.setAlpha(alpha);canvas.drawRoundRect(rect,radius,radius,paint);paint.setShader(null);}
 @Override public void setAlpha(int value){alpha=value;invalidateSelf();}@Override public void setColorFilter(ColorFilter filter){paint.setColorFilter(filter);invalidateSelf();}@Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
}
