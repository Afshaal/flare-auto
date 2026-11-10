package local.civic.home;
import android.graphics.*;import android.graphics.drawable.Drawable;
final class MediaGlyph extends Drawable {
 final String kind;final int color;final Paint p=new Paint(3);final Path q=new Path();MediaGlyph(String k,int c){kind=k;color=c;}
 public void draw(Canvas c){c.save();Rect b=getBounds();float size=Math.min(b.width(),b.height());c.translate(b.left+(b.width()-size)/2,b.top+(b.height()-size)/2);c.scale(size/24f,size/24f);p.setColor(color);q.rewind();if(kind.equals("pause")){c.drawRect(6,4,10,20,p);c.drawRect(14,4,18,20,p);}else if(kind.equals("note")){q.moveTo(12,3);q.lineTo(12,14);q.cubicTo(6,11,4,20,10,21);q.cubicTo(13,22,14,19,14,17);q.lineTo(14,7);q.lineTo(18,7);q.lineTo(18,3);q.close();c.drawPath(q,p);}else{boolean prev=kind.equals("previous");if(prev){c.translate(24,0);c.scale(-1,1);}q.moveTo(6,4);q.lineTo(18,12);q.lineTo(6,20);q.close();c.drawPath(q,p);if(!kind.equals("play"))c.drawRect(18,4,21,20,p);}c.restore();}
 public void setAlpha(int a){}public void setColorFilter(ColorFilter f){}public int getOpacity(){return PixelFormat.TRANSLUCENT;}
}
