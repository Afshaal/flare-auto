package app.flare.auto;
import android.graphics.*;import android.graphics.drawable.Drawable;

final class ThemeBackdrop extends Drawable {
 final Path line=new Path();final ThemeTokens theme;final Paint paint=new Paint(3);ThemeBackdrop(ThemeTokens t){theme=t;}
 @Override protected void onBoundsChange(Rect b){float w=b.width(),h=b.height();line.rewind();line.moveTo(-10,h*.61f);line.cubicTo(w*.2f,h*.50f,w*.35f,h*.72f,w*.6f,h*.56f);line.cubicTo(w*.8f,h*.38f,w*.9f,h*.32f,w+10,h*.43f);}
 public void draw(Canvas c){c.drawColor(theme.background);paint.setStyle(Paint.Style.STROKE);for(int i=18;i>1;i-=2){paint.setStrokeWidth(i);paint.setColor(Color.argb(5,Color.red(theme.accent),Color.green(theme.accent),Color.blue(theme.accent)));c.drawPath(line,paint);}paint.setStrokeWidth(1.5f);paint.setColor(theme.accent);c.drawPath(line,paint);}
 public void setAlpha(int a){}public void setColorFilter(ColorFilter f){}public int getOpacity(){return PixelFormat.OPAQUE;}
}
