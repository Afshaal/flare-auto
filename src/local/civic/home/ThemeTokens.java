package local.civic.home;
import android.content.Context;
import android.graphics.Color;

final class ThemeTokens {
 static final String[] NAMES={"Pitch Black","Dark Navy","Warm Charcoal","Burgundy Black","Dark Forest"};
 static final int[][] VALUES={{0xff08090a,0xff121417,0xff1f2228,0xffffffff,0xff8a909a,0xffe60012},{0xff0a0e16,0xff121826,0x1affffff,0xfff2f5fa,0xff8a909a,0xffe60012},{0xff100e0e,0xff1a1717,0x1affffff,0xfff2f5fa,0xff8a909a,0xffe60012},{0xff120a0c,0xff1d1013,0x1affffff,0xfff2f5fa,0xff8a909a,0xffe60012},{0xff08100e,0xff101b18,0x1affffff,0xfff2f5fa,0xff8a909a,0xff2dd4bf}};
 final int background,card,cardBorder,textPrimary,textSecondary,accent;
 private ThemeTokens(int[] v,int chosenAccent){background=v[0];card=v[1];cardBorder=v[2];textPrimary=v[3];textSecondary=v[4];accent=chosenAccent;}
 static int index(Context c){String n=c.getSharedPreferences("home",0).getString("theme",NAMES[0]);for(int i=0;i<NAMES.length;i++)if(NAMES[i].equals(n))return i;return 0;}
 private static ThemeTokens cached;
 static synchronized ThemeTokens get(Context c){if(cached==null){int[] values=VALUES[index(c)];cached=new ThemeTokens(values,c.getSharedPreferences("home",0).getInt("accent_color",values[5]));}return cached;}
 static synchronized void invalidate(){cached=null;}
 static synchronized void select(Context c,int i){c.getSharedPreferences("home",0).edit().putString("theme",NAMES[Math.floorMod(i,NAMES.length)]).apply();invalidate();}
}
