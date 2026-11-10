package local.civic.home;
import android.content.Context;import android.graphics.*;import android.view.View;

final class CivicBadge extends View {
 final Paint p=new Paint(3);final ThemeTokens theme;final Path letters=new Path();
 CivicBadge(Context c,boolean rs){super(c);theme=ThemeTokens.get(c);setContentDescription("RS");}
 protected void onDraw(Canvas c){super.onDraw(c);c.save();float scale=Math.min(getWidth()/46f,getHeight()/28f);c.translate((getWidth()-46*scale)/2,(getHeight()-28*scale)/2);c.scale(scale,scale);letters.rewind();letters.moveTo(6,4);letters.lineTo(19,4);letters.cubicTo(24,4,25,7,23,11);letters.cubicTo(22,14,20,15,17,16);letters.lineTo(21,24);letters.lineTo(15,24);letters.lineTo(12,17);letters.lineTo(9,17);letters.lineTo(7,24);letters.lineTo(2,24);letters.close();letters.moveTo(11,8);letters.lineTo(10,13);letters.lineTo(16,13);letters.cubicTo(19,13,20,11,20,9);letters.lineTo(19,8);letters.close();letters.moveTo(30,4);letters.lineTo(44,4);letters.lineTo(43,8);letters.lineTo(32,8);letters.cubicTo(29,8,29,11,32,11);letters.lineTo(36,11);letters.cubicTo(45,11,42,24,33,24);letters.lineTo(23,24);letters.lineTo(24,20);letters.lineTo(34,20);letters.cubicTo(37,20,38,16,34,16);letters.lineTo(30,16);letters.cubicTo(23,16,24,4,30,4);letters.close();letters.setFillType(Path.FillType.EVEN_ODD);p.setStyle(Paint.Style.FILL);p.setColor(theme.background);c.drawPath(letters,p);p.setStyle(Paint.Style.STROKE);p.setStrokeJoin(Paint.Join.ROUND);p.setStrokeWidth(1.15f);p.setColor(theme.accent);c.drawPath(letters,p);c.restore();}
}
