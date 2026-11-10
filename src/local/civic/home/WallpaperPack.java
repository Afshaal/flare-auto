package local.civic.home;

import android.content.Context;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;

final class WallpaperPack {
 static final String[] HOME_NAMES={"Original red wave", "Ember", "Wine", "Midnight", "Forest", "Charcoal", "Custom image"};
 static final String[] SAVER_NAMES={"Black", "Ember", "Wine", "Midnight", "Forest", "Charcoal", "Deep Ember", "Deep Wine", "Deep Midnight", "Deep Forest", "Soft Charcoal", "Dark Grey", "Medium Grey", "Warm Grey", "Black to Warm Grey", "Dark Grey to Warm Grey", "Black to Warm Red", "Black to Burgundy", "Black to Copper", "Black to Home Ember", "Custom image"};
 private static final int[] SOURCE={0,0xffff8177,0xffb12a5b,0xffa1c4fd,0xff96e6a1,0xffa6c1ee};
 static int index(Context c,boolean saver){int n=c.getSharedPreferences("home",0).getInt(saver?"saver_wallpaper":"home_wallpaper",0);return n>=0&&n<(saver?SAVER_NAMES.length:HOME_NAMES.length)?n:0;}
 static String name(Context c,boolean saver){return (saver?SAVER_NAMES:HOME_NAMES)[index(c,saver)];}
 static Drawable home(Context c,ThemeTokens theme){return preview(c,theme,false,index(c,false));}
 static Drawable preview(Context c,ThemeTokens theme,boolean saver,int i){if(saver&&i==SAVER_NAMES.length-1){Drawable custom=CustomImages.load(c,"saverwall");return custom!=null?custom:new ColorDrawable(theme.background);}if(!saver&&i==HOME_NAMES.length-1){Drawable custom=CustomImages.load(c,"home");return custom!=null?custom:new ThemeBackdrop(theme);}return saver?saverAt(c,i):(i==0?new ThemeBackdrop(theme):gradient(c,i,false));}
 static Drawable saver(Context c,ThemeTokens theme){return preview(c,theme,true,index(c,true));}
 private static Drawable saverAt(Context c,int i){if(i==0)return new ColorDrawable(0xff000000);if(i==11)return new ColorDrawable(0xff242424);if(i==12)return new ColorDrawable(0xff383838);if(i==13)return new ColorDrawable(0xff393530);if(i==14)return new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,new int[]{0xff000000,0xff000000,0xff050505,0xff121110,0xff282521,0xff393530});if(i==15)return new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,new int[]{0xff242424,0xff393530,0xff393530});if(i==16)return new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,new int[]{0xff000000,0xff000000,0xff080505,0xff191110,0xff362421,0xff523532});if(i==17)return new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,new int[]{0xff000000,0xff000000,0xff070506,0xff171013,0xff302126,0xff49333a});if(i==18)return new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,new int[]{0xff000000,0xff000000,0xff080605,0xff19130f,0xff35281f,0xff503a30});if(i==19)return new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,new int[]{0xff000000,0xff000000,0xff080404,0xff1c0e0d,0xff3c1d1b,0xff5b2e2a});return gradient(c,i,true);}
 private static Drawable gradient(Context c,int index,boolean saver){boolean deep=saver&&index>=6;int base=SOURCE[deep?index-5:index];float strength=deep?.075f:(saver?.19f:.36f);int r=(int)(((base>>16)&255)*strength),g=(int)(((base>>8)&255)*strength),b=(int)((base&255)*strength);int top=0xff000000|(r<<16)|(g<<8)|b;int middle=0xff000000|((r/4)<<16)|((g/4)<<8)|(b/4);android.content.SharedPreferences preferences=c.getSharedPreferences("home",0);boolean flip=saver?preferences.getBoolean("saver_flip",preferences.getBoolean("flip_gradients",false)):preferences.getBoolean("flip_gradients",false);GradientDrawable d=new GradientDrawable(flip?GradientDrawable.Orientation.BOTTOM_TOP:GradientDrawable.Orientation.TOP_BOTTOM,new int[]{top,middle,deep?0xff000000:0xff050505});d.setGradientType(GradientDrawable.LINEAR_GRADIENT);return d;}
}
