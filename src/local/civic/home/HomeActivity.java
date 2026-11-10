package local.civic.home;


import android.app.*;
import android.os.*;
import android.content.*;
import android.content.pm.*;
import android.graphics.Color;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.*;
import android.widget.*;
import android.media.AudioManager;
import java.text.SimpleDateFormat;
import java.util.*;


public class HomeActivity extends Activity {
 private int bg,panel,edge,white,muted,cyan; private ThemeTokens theme; private EnvironmentInfo environment; private View dimOverlay; private boolean resumed;

 private SharedPreferences prefs;
 static java.lang.ref.WeakReference<HomeActivity> instance=new java.lang.ref.WeakReference<>(null);
 private boolean uiDirty=false;
 private ImageView playbackButton;
 private LinearLayout root;
 private TextView date;private StartupActions startup;
 private final Runnable screenOff=()->{if(resumed)startActivity(new Intent(this,ScreenSaverActivity.class));};
 private void scheduleScreenOff(){handler.removeCallbacks(screenOff);int minutes=prefs.getInt("saver_timeout",0);if(resumed&&minutes>0)handler.postDelayed(screenOff,minutes*60000L);}
 @Override public void onUserInteraction(){super.onUserInteraction();if(startup!=null)startup.cancel();scheduleScreenOff();}
 private final Handler handler=new Handler(Looper.getMainLooper());
 private final SimpleDateFormat dateFormat=new SimpleDateFormat("EEE, MMM d",Locale.getDefault());
 private final SharedPreferences.OnSharedPreferenceChangeListener settingsChanged=(p,k)->{if(k==null||Arrays.asList("theme","accent_color","brand_text","badge_text","solar_manual","solar_lat","solar_lon","custom_revision","home_wallpaper","glass_cards","flip_gradients","split","spacing","dock","dock_order","dock_items","dock_mono","dock_labels","dock_icons","text","projection","media","clock24","layout_left","layout_right","layout_apps").contains(k))uiDirty=true;};

 private final Runnable tick=new Runnable(){public void run(){date.setText(dateFormat.format(new Date()));if(environment!=null){environment.updateSolar();if(dimOverlay!=null)dimOverlay.setVisibility(prefs.getBoolean("auto_dim",false)&&environment.night()?View.VISIBLE:View.GONE);}handler.postDelayed(this,60000);}};
 private final Runnable progressTick=new Runnable(){public void run(){if(!resumed||playbackButton==null)return;updateProgress();if(isPlaying(selectedController)&&lastDuration>0)handler.postDelayed(this,1000);}};

 private int dp(float n){return (int)(getResources().getDisplayMetrics().density*n+0.5f);
} 
 private android.graphics.drawable.Drawable shape(int color,int radius){return prefs.getBoolean("glass_cards",true)?new HomeGlassDrawable(color,edge,dp(radius)):new GlassDrawable(color,dp(radius),edge);
}
 private TextView text(String s,int size,int color,boolean bold){TextView v=new TextView(this);
v.setText(s);
v.setTextSize(size);
v.setTextColor(color);
v.setGravity(Gravity.CENTER_VERTICAL);
v.setTextSize(size*prefs.getInt("text",100)/100f);
if(bold)v.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
return v;
}
 private LinearLayout column(){LinearLayout x=new LinearLayout(this);
x.setOrientation(LinearLayout.VERTICAL);
return x;
}
 private LinearLayout row(){LinearLayout x=new LinearLayout(this);
x.setOrientation(LinearLayout.HORIZONTAL);
x.setGravity(Gravity.CENTER_VERTICAL);
return x;
}
 private void pad(View v,int h,int vert){v.setPadding(dp(h),dp(vert),dp(h),dp(vert));
}
 private GradientDrawable accentGradient(){int end=(theme.accent&0x00ffffff)|0x18000000;int mid=Color.rgb((Color.red(theme.accent)*7+Color.red(muted)*3)/10,(Color.green(theme.accent)*7+Color.green(muted)*3)/10,(Color.blue(theme.accent)*7+Color.blue(muted)*3)/10);return new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,new int[]{end,mid,end});}
 private View projectionButton(String label,String icon,Runnable action){LinearLayout pill=row();pill.setGravity(Gravity.CENTER);pill.setMinimumHeight(dp(56));pill.setContentDescription(label);GradientDrawable bg=new GradientDrawable();bg.setColor((theme.background&0x00ffffff)|0xb3000000);bg.setCornerRadius(dp(7));bg.setStroke(1,theme.cardBorder);pill.setBackground(bg);ImageView mark=new ImageView(this);int id=getResources().getIdentifier(icon,"drawable",getPackageName());if(id!=0){mark.setImageResource(id);mark.setColorFilter(white);pill.addView(mark,new LinearLayout.LayoutParams(dp(18),dp(18)));}TextView name=text(label,12,white,false);name.setTypeface(android.graphics.Typeface.create("sans-serif-medium",android.graphics.Typeface.NORMAL));name.setGravity(Gravity.CENTER);LinearLayout.LayoutParams labelParams=new LinearLayout.LayoutParams(-2,-1);labelParams.leftMargin=dp(5);pill.addView(name,labelParams);pill.setOnClickListener(v->action.run());return pill;}
 private void launch(String pkg,String cls){String key=pkg.equals("com.bw.phone")?"bluetooth":(cls!=null&&cls.endsWith("RadioActivity"))?"radio":(cls!=null&&cls.endsWith("BtActivity"))?"audio":"";
String target=prefs.getString("app_"+key,"");
if(!target.isEmpty()){launchTarget(target);
return;
}try{Intent i;
if(cls!=null){i=new Intent();
i.setClassName(pkg,cls);
}else{i=getPackageManager().getLaunchIntentForPackage(pkg);
}if(i==null)throw new Exception();
startActivity(i);
}catch(Exception e){Toast.makeText(this,"App unavailable on this device",Toast.LENGTH_SHORT).show();
}}
 private void launchTarget(String target){try{ComponentName c=ComponentName.unflattenFromString(target);
startActivity(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setComponent(c));
}catch(Exception e){Toast.makeText(this,"Selected app is unavailable",Toast.LENGTH_SHORT).show();
}}
 private void launchFirst(String[] pkgs){String target=prefs.getString(pkgs[0].equals("com.shihab.diplay")?"app_carplay":pkgs[0].equals("org.schabi.newpipe")?"app_newpipe":"app_androidauto","");
if(!target.isEmpty()){launchTarget(target);
return;
}for(String p:pkgs){Intent i=getPackageManager().getLaunchIntentForPackage(p);
if(i!=null){startActivity(i);
return;
}}Toast.makeText(this,"App unavailable on this device",Toast.LENGTH_SHORT).show();
}
 private void media(int key){android.media.session.MediaController c=selectedController;if(c!=null){android.media.session.MediaController.TransportControls t=c.getTransportControls();if(key==88)t.skipToPrevious();else if(key==87)t.skipToNext();else if(c.getPlaybackState()!=null&&c.getPlaybackState().getState()==3)t.pause();else t.play();return;}AudioManager a=(AudioManager)getSystemService(AUDIO_SERVICE);
a.dispatchMediaKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN,key));
a.dispatchMediaKeyEvent(new KeyEvent(KeyEvent.ACTION_UP,key));
}
 @Override public void onCreate(Bundle b){super.onCreate(b);getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(ThemeTokens.get(this).background));
prefs=getSharedPreferences("home",0);startup=new StartupActions(this,prefs,handler);instance=new java.lang.ref.WeakReference<>(this);
getWindow().getDecorView().setSystemUiVisibility(UiFlags.IMMERSIVE);
prefs.registerOnSharedPreferenceChangeListener(settingsChanged);
build();
}
 private void build(){
 if(environment!=null)environment.stop();environment=null;playbackButton=null;albumArt=null;trackTitle=null;trackArtist=null;elapsed=null;total=null;progress=null;seekRow=null;sourceLabel=null;mediaAccessHint=null;dragging=false;mediaNarrow=false;theme=ThemeTokens.get(this);bg=theme.background;panel=theme.card;edge=theme.cardBorder;white=theme.textPrimary;muted=theme.textSecondary;cyan=theme.accent;resetMediaCache();
root=column();
root.setBackground(WallpaperPack.home(this,theme));
pad(root,prefs.getInt("spacing",14),10);
FrameLayout frame=new FrameLayout(this);frame.addView(root);dimOverlay=new View(this);dimOverlay.setBackgroundColor(0x4d000000);dimOverlay.setClickable(false);dimOverlay.setVisibility(View.GONE);frame.addView(dimOverlay,new FrameLayout.LayoutParams(-1,-1));setContentView(frame);

 buildTopBar();
 LinearLayout content=row();
 LinearLayout.LayoutParams contentP=new LinearLayout.LayoutParams(-1,0,1);
contentP.topMargin=dp(12);
contentP.bottomMargin=dp(12);
root.addView(content,contentP);

 buildColumns(content);
 buildDock();
 if(resumed)refreshPlayback();
 }
 private void buildTopBar(){
 LinearLayout top=row();
date=text("",19,white,true);
top.addView(date,new LinearLayout.LayoutParams(0,dp(40),1));
top.addView(new DeviceStatus(this,false),new LinearLayout.LayoutParams(dp(24),dp(24)));LinearLayout.LayoutParams wifiStatus=new LinearLayout.LayoutParams(dp(24),dp(24));wifiStatus.leftMargin=dp(14);top.addView(new WifiStatus(this),wifiStatus);LinearLayout.LayoutParams btStatus=new LinearLayout.LayoutParams(dp(24),dp(24));btStatus.leftMargin=dp(14);top.addView(new DeviceStatus(this,true),btStatus);
root.addView(top);

 LinearLayout divider=new LinearLayout(this);
divider.setBackground(accentGradient());
root.addView(divider,new LinearLayout.LayoutParams(-1,dp(1)));

 }
 static String card(SharedPreferences p,boolean left){String key=left?"layout_left":"layout_right";if(p.contains(key))return p.getString(key,left?"clock":"media");return left?(p.getBoolean("projection",true)?"clock":"hidden"):(p.getBoolean("media",true)?"media":"hidden");}
 void buildColumns(LinearLayout content){String left=card(prefs,true),right=card(prefs,false);boolean showLeft=!left.equals("hidden"),showRight=!right.equals("hidden");int split=Math.max(35,Math.min(65,prefs.getInt("split",58)));if(showLeft){LinearLayout box=row();content.addView(box,new LinearLayout.LayoutParams(0,-1,showRight?split:1));buildCard(box,left);}if(showLeft&&showRight){View gap=new View(this);content.addView(gap,new LinearLayout.LayoutParams(dp(12),1));}if(showRight){LinearLayout box=row();content.addView(box,new LinearLayout.LayoutParams(0,-1,showLeft?100-split:1));buildCard(box,right);}}
 void buildCard(LinearLayout content,String card){if(card.equals("clock"))buildClockCard(content);else if(card.equals("media"))buildMediaCard(content);else if(card.equals("apps"))buildAppCard(content);}
 void buildAppCard(LinearLayout content){LinearLayout card=column();card.setBackground(shape(panel,12));content.addView(card,new LinearLayout.LayoutParams(0,-1,1));android.widget.GridLayout grid=new android.widget.GridLayout(this);grid.setColumnCount(4);card.addView(grid,new LinearLayout.LayoutParams(-1,-1));try{org.json.JSONArray apps=new org.json.JSONArray(prefs.getString("layout_apps","[]"));for(int n=0;n<Math.min(8,apps.length());n++){String target=apps.getString(n);LinearLayout cell=column();cell.setGravity(Gravity.CENTER);ImageView image=new ImageView(this);try{image.setImageDrawable(getPackageManager().getActivityIcon(ComponentName.unflattenFromString(target)));}catch(Exception ignored){}cell.addView(image,new LinearLayout.LayoutParams(dp(36),dp(36)));String name="App";try{name=getPackageManager().getActivityInfo(ComponentName.unflattenFromString(target),0).loadLabel(getPackageManager()).toString();}catch(Exception ignored){}TextView label=text(name,12,white,false);label.setGravity(Gravity.CENTER);label.setSingleLine(true);cell.addView(label);cell.setOnClickListener(v->launchTarget(target));android.widget.GridLayout.LayoutParams p=new android.widget.GridLayout.LayoutParams();p.width=0;p.height=0;p.columnSpec=android.widget.GridLayout.spec(android.widget.GridLayout.UNDEFINED,1f);p.rowSpec=android.widget.GridLayout.spec(android.widget.GridLayout.UNDEFINED,1f);grid.addView(cell,p);}}catch(Exception ignored){}}
 private void buildClockCard(LinearLayout content){
 LinearLayout left=column();
pad(left,22,12);
left.setBackground(shape(panel,12));
LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-1,1);
content.addView(left,lp);

LinearLayout badges=row();TextView solar=text("",11,muted,false);solar.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);SolarIcon solarIcon=new SolarIcon(this,muted);badges.addView(solarIcon,new LinearLayout.LayoutParams(dp(24),dp(38)));badges.addView(solar,new LinearLayout.LayoutParams(0,dp(38),1));String badgeText=prefs.getString("badge_text","RS").trim();if(badgeText.equalsIgnoreCase("RS"))badges.addView(new CivicBadge(this,true),new LinearLayout.LayoutParams(dp(37),dp(27)));else if(!badgeText.isEmpty()){TextView badge=text(badgeText,13,theme.accent,true);badge.setSingleLine(true);badge.setEllipsize(android.text.TextUtils.TruncateAt.END);badge.setGravity(Gravity.END|Gravity.CENTER_VERTICAL);badges.addView(badge,new LinearLayout.LayoutParams(dp(76),dp(38)));}FrameLayout header=new FrameLayout(this);header.addView(badges,new FrameLayout.LayoutParams(-1,-1));left.addView(header,new LinearLayout.LayoutParams(-1,dp(38)));environment=new EnvironmentInfo(this,solar,solarIcon);if(resumed)environment.start();

 
LinearLayout center=column();
center.setGravity(Gravity.CENTER);
left.addView(center,new LinearLayout.LayoutParams(-1,0,1));
String brand=prefs.getString("brand_text","CIVIC");TextView mark=text(brand,13,theme.accent,true);mark.setVisibility(brand.isEmpty()?View.GONE:View.VISIBLE);mark.setLetterSpacing(0.35f);mark.setIncludeFontPadding(false);
mark.setGravity(Gravity.CENTER);
center.addView(mark);
android.widget.TextClock title=new android.widget.TextClock(this);String clockFormat=prefs.getBoolean("clock24",false)?"HH:mm":"h:mm";title.setFormat12Hour(clockFormat);title.setFormat24Hour(clockFormat);title.setTextSize(76);title.setAutoSizeTextTypeUniformWithConfiguration(32,76,1,android.util.TypedValue.COMPLEX_UNIT_SP);title.setMaxLines(1);title.setIncludeFontPadding(false);title.setTextColor(white);title.setTypeface(Typeface.create("sans-serif-light",Typeface.NORMAL));
title.setGravity(Gravity.CENTER);
LinearLayout.LayoutParams clockParams=new LinearLayout.LayoutParams(-1,0,1);center.addView(title,clockParams);
View clockAccent=new View(this);clockAccent.setBackground(accentGradient());LinearLayout.LayoutParams accentParams=new LinearLayout.LayoutParams(dp(68),dp(2));accentParams.topMargin=dp(3);accentParams.bottomMargin=dp(5);center.addView(clockAccent,accentParams);
LinearLayout projection=row();
projection.setGravity(Gravity.CENTER);
projection.setPadding(dp(12),0,dp(12),0);
LinearLayout.LayoutParams projp=new LinearLayout.LayoutParams(-1,-2);
projection.setMinimumHeight(dp(56));
projp.topMargin=dp(14);
left.addView(projection,projp);
LinearLayout.LayoutParams half=new LinearLayout.LayoutParams(0,dp(56),1);
half.rightMargin=dp(5);
projection.addView(projectionButton("CarPlay","ic_carplay_mark",()->launchFirst(new String[]{"com.shihab.diplay","com.shihab.diplay.hudtest"})),half);
LinearLayout.LayoutParams half2=new LinearLayout.LayoutParams(0,dp(56),1);
half2.leftMargin=dp(5);
projection.addView(projectionButton("Android Auto","ic_android_auto_mark",()->launchFirst(new String[]{"com.andrerinas.headunitrevived"})),half2);

 }
 private void buildMediaCard(LinearLayout content){
 LinearLayout right=column();
LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(0,-1,1);
content.addView(right,rp);

 LinearLayout audio=column();
pad(audio,20,14);
audio.setBackground(shape(panel,12));
right.addView(audio,new LinearLayout.LayoutParams(-1,0,1));


sourceLabel=text("",11,muted,false);sourceLabel.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);sourceLabel.setVisibility(View.GONE);audio.addView(sourceLabel,new LinearLayout.LayoutParams(-1,-2));
TextView mediaHint=text("Enable media access",12,theme.accent,false);mediaHint.setGravity(Gravity.CENTER);mediaHint.setOnClickListener(v->startActivity(new Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")));audio.addView(mediaHint,new LinearLayout.LayoutParams(-1,-2));this.mediaAccessHint=mediaHint;
LinearLayout metadata=column();metadata.setGravity(Gravity.CENTER);audio.addView(metadata,new LinearLayout.LayoutParams(-1,0,1));
albumArt=new ImageView(this);albumArt.setScaleType(ImageView.ScaleType.CENTER_CROP);android.graphics.drawable.GradientDrawable artBg=new android.graphics.drawable.GradientDrawable();artBg.setColor(theme.background);artBg.setCornerRadius(dp(8));albumArt.setBackground(artBg);albumArt.setClipToOutline(true);FrameLayout artArea=new FrameLayout(this);metadata.addView(artArea,new LinearLayout.LayoutParams(-1,0,1));artArea.addView(albumArt,new FrameLayout.LayoutParams(dp(160),dp(160),Gravity.CENTER));final ImageView cardArt=albumArt;artArea.addOnLayoutChangeListener((v,l,t,r2,b,ol,ot,orr,ob)->{int size=Math.max(1,Math.min(r2-l,b-t)-dp(10));android.view.ViewGroup.LayoutParams ap=cardArt.getLayoutParams();if(ap.width!=size||ap.height!=size){ap.width=size;ap.height=size;cardArt.setLayoutParams(ap);}});LinearLayout labels=column();labels.setPadding(0,dp(4),0,dp(6));metadata.addView(labels,new LinearLayout.LayoutParams(-1,-2));trackTitle=text("No media playing",18,white,true);trackTitle.setSingleLine(true);trackTitle.setEllipsize(android.text.TextUtils.TruncateAt.MARQUEE);trackTitle.setMarqueeRepeatLimit(-1);trackTitle.setSelected(true);trackTitle.setGravity(Gravity.CENTER);labels.addView(trackTitle);trackArtist=text("",13,muted,false);trackArtist.setSingleLine(true);trackArtist.setGravity(Gravity.CENTER);labels.addView(trackArtist);
seekRow=row();elapsed=text("0:00",11,muted,false);total=text("0:00",11,muted,false);progress=new SeekBar(this);progress.setProgressTintList(android.content.res.ColorStateList.valueOf(theme.accent));progress.setThumbTintList(android.content.res.ColorStateList.valueOf(theme.accent));progress.setProgressBackgroundTintList(android.content.res.ColorStateList.valueOf(theme.textSecondary));seekRow.addView(elapsed);seekRow.addView(progress,new LinearLayout.LayoutParams(0,dp(40),1));seekRow.addView(total);seekRow.setMinimumHeight(dp(40));audio.addView(seekRow);progress.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onProgressChanged(SeekBar b,int n,boolean user){}public void onStartTrackingTouch(SeekBar b){dragging=true;}public void onStopTrackingTouch(SeekBar b){dragging=false;android.media.session.MediaController c=selectedController;if(c!=null)c.getTransportControls().seekTo(b.getProgress());}});
LinearLayout controls=row();controls.setGravity(Gravity.CENTER);
controls.setMinimumHeight(dp(56));audio.addView(controls,new LinearLayout.LayoutParams(-1,dp(56)));boolean preview=previewBuild;LinearLayout cardSeek=seekRow;TextView cardTitle=trackTitle;audio.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,orr,ob)->{if(r-l!=orr-ol)updateMediaWidth(r-l,controls,cardSeek,cardTitle,preview);});
for(int k:new int[]{KeyEvent.KEYCODE_MEDIA_PREVIOUS,KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,KeyEvent.KEYCODE_MEDIA_NEXT}){String label=k==85?"▶":k==88?"◀◀":"▶▶";
LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-1,1);
p.rightMargin=dp(6);
ImageView control=new ImageView(this);control.setPadding(dp(9),dp(9),dp(9),dp(9));control.setImageDrawable(new MediaGlyph(k==85?"play":k==88?"previous":"next",white));control.setOnClickListener(v->media(k));if(k==85){playbackButton=control;}controls.addView(control,p);
}
 }
 void updateMediaWidth(int pixels,LinearLayout controls,LinearLayout cardSeek,TextView cardTitle,boolean preview){int width=(int)(pixels/getResources().getDisplayMetrics().density);boolean narrow=width<270;if(!preview)mediaNarrow=narrow;cardSeek.setVisibility(!narrow&&lastDuration>0?View.VISIBLE:View.GONE);cardTitle.setTextSize((width<230?14:18)*prefs.getInt("text",100)/100f);controls.setVisibility(width<208?View.GONE:View.VISIBLE);}
 LinearLayout makeCardPreview(String type){EnvironmentInfo oldEnvironment=environment;ImageView oldPlayback=playbackButton,oldArt=albumArt;TextView oldTitle=trackTitle,oldArtist=trackArtist,oldElapsed=elapsed,oldTotal=total,oldHint=mediaAccessHint,oldSource=sourceLabel;SeekBar oldProgress=progress;LinearLayout oldSeek=seekRow;boolean oldNarrow=mediaNarrow;ThemeTokens oldTheme=theme;int oldBg=bg,oldPanel=panel,oldEdge=edge,oldWhite=white,oldMuted=muted,oldCyan=cyan;theme=ThemeTokens.get(this);bg=theme.background;panel=theme.card;edge=theme.cardBorder;white=theme.textPrimary;muted=theme.textSecondary;cyan=theme.accent;LinearLayout holder=row();previewBuild=true;try{buildCard(holder,type);if(environment!=null&&environment!=oldEnvironment)environment.start();}finally{previewBuild=false;theme=oldTheme;bg=oldBg;panel=oldPanel;edge=oldEdge;white=oldWhite;muted=oldMuted;cyan=oldCyan;}environment=oldEnvironment;playbackButton=oldPlayback;albumArt=oldArt;trackTitle=oldTitle;trackArtist=oldArtist;elapsed=oldElapsed;total=oldTotal;mediaAccessHint=oldHint;sourceLabel=oldSource;progress=oldProgress;seekRow=oldSeek;mediaNarrow=oldNarrow;return holder;}
 private void buildDock(){
 LinearLayout nav=row();
nav.setBackground(shape(panel,14));
root.addView(nav,new LinearLayout.LayoutParams(-1,dp(prefs.getInt("dock",70))));
Runnable[] actions={()->launch("com.bw.mediaplayer","com.bw.mediaplayer.ui.BtActivity"),()->launch("com.bw.mediaplayer","com.bw.mediaplayer.ui.RadioActivity"),()->launch("com.bw.phone","com.bw.phone.MainActivity"),()->startActivity(new Intent(this,ScreenSaverActivity.class)),()->apps()};
ArrayList<DockConfig.Item> items=DockConfig.read(prefs);int count=0;for(DockConfig.Item item:items)if(item.visible)count++;int oldSize=prefs.getInt("dock_icons",24);int ceiling=oldSize<23?20:oldSize<30?26:32;boolean showLabels=prefs.getBoolean("dock_labels",true);int desired=showLabels?ceiling:Math.min(32,ceiling);int size=DockConfig.size(this,count,desired);
for(DockConfig.Item item:items){if(!item.visible)continue;LinearLayout v=column();v.setGravity(Gravity.CENTER);String name=DockConfig.name(this,item);v.setContentDescription(name);v.setOnClickListener(w->{if(item.type==5)launchTarget(item.target);else actions[item.type].run();});View icon;if(item.type==5){ImageView app=new ImageView(this);try{app.setImageDrawable(getPackageManager().getActivityIcon(ComponentName.unflattenFromString(item.target)));}catch(Exception ignored){}if(prefs.getBoolean("dock_mono",false)){android.graphics.ColorMatrix matrix=new android.graphics.ColorMatrix();matrix.setSaturation(0);app.setColorFilter(new android.graphics.ColorMatrixColorFilter(matrix));}icon=app;}else icon=new NavIcon(this,DockConfig.ICONS[item.type],white);v.addView(icon,new LinearLayout.LayoutParams(dp(size),dp(size)));
TextView label=text(name,14,white,false);label.setGravity(Gravity.CENTER);label.setIncludeFontPadding(false);label.setSingleLine(true);label.setEllipsize(android.text.TextUtils.TruncateAt.END);LinearLayout.LayoutParams labelp=new LinearLayout.LayoutParams(-1,LinearLayout.LayoutParams.WRAP_CONTENT);labelp.topMargin=dp(4);label.setVisibility(prefs.getBoolean("dock_labels",true)?View.VISIBLE:View.GONE);v.addView(label,labelp);nav.addView(v,new LinearLayout.LayoutParams(0,-1,1));}

 }
 static class NavIcon extends View {
  private final String kind;
 private final Paint paint=new Paint(3);
 private static final java.util.Map<String,float[]> centers=new java.util.HashMap<>();
 private float shiftX,shiftY;private final Path glyphPath=new Path();

  NavIcon(Context c,String k,int color){super(c);
kind=k;
paint.setColor(color);
paint.setStyle(Paint.Style.STROKE);
paint.setStrokeWidth(1.7f);
paint.setStrokeCap(Paint.Cap.ROUND);
paint.setStrokeJoin(Paint.Join.ROUND);
 synchronized(centers){float[] offset=centers.get(kind);if(offset==null){android.graphics.Bitmap bitmap=android.graphics.Bitmap.createBitmap(256,256,android.graphics.Bitmap.Config.ARGB_8888);Canvas measure=new Canvas(bitmap);measure.translate(32,32);measure.scale(8,8);drawGlyph(measure);int[] pixels=new int[256*256];bitmap.getPixels(pixels,0,256,0,0,256,256);int minX=256,minY=256,maxX=-1,maxY=-1;for(int y=0;y<256;y++)for(int x=0;x<256;x++)if((pixels[y*256+x]>>>24)!=0){minX=Math.min(minX,x);maxX=Math.max(maxX,x);minY=Math.min(minY,y);maxY=Math.max(maxY,y);}bitmap.recycle();offset=maxX<0?new float[]{0,0}:new float[]{12-((minX+maxX+1)/2f-32)/8f,12-((minY+maxY+1)/2f-32)/8f};centers.put(kind,offset);}shiftX=offset[0];shiftY=offset[1];}
}
  protected void onDraw(Canvas c){super.onDraw(c);c.save();c.scale(getWidth()/24f,getHeight()/24f);c.translate(shiftX,shiftY);drawGlyph(c);c.restore();}
  private void drawGlyph(Canvas c){Path p=glyphPath;p.rewind();

   switch(kind){
    case "audio":p.moveTo(8,17);
p.lineTo(8,5);
p.lineTo(19,3);
p.lineTo(19,15);
c.drawPath(p,paint);
c.drawOval(3,15,8,20,paint);
c.drawOval(14,13,19,18,paint);
break;

    case "youtube":c.drawRoundRect(2,5,22,19,4,4,paint);
p.moveTo(10,9);
p.lineTo(16,12);
p.lineTo(10,15);
p.close();
c.drawPath(p,paint);
break;

    case "file":p.moveTo(2,6);
p.lineTo(9,6);
p.lineTo(11,9);
p.lineTo(22,9);
p.lineTo(22,20);
p.lineTo(2,20);
p.close();
c.drawPath(p,paint);
break;

    case "projection": c.drawRoundRect(2,4,22,19,3,3,paint);
p.moveTo(10,8);
p.lineTo(16,11.5f);
p.lineTo(10,15);
p.close();
c.drawPath(p,paint);
break;

    case "radio":c.drawRoundRect(3,8,21,20,3,3,paint);
c.drawLine(6,7,16,3,paint);
c.drawLine(6,12,12,12,paint);
c.drawLine(6,15,12,15,paint);
c.drawCircle(17,14,2,paint);
break;

    case "bluetooth":p.moveTo(12,2);
p.lineTo(12,22);
p.lineTo(18,17);
p.lineTo(6,7);
p.moveTo(6,17);
p.lineTo(18,7);
p.lineTo(12,2);
c.drawPath(p,paint);
break;

    case "screen":c.drawRoundRect(3,4,21,19,3,3,paint);
c.drawLine(9,22,15,22,paint);
c.drawLine(12,19,12,22,paint);
break;

    case "cellular":paint.setStyle(Paint.Style.FILL);for(int bar=0;bar<4;bar++){float x=3+bar*5;c.drawRoundRect(x,17-bar*4,x+3,21,0.7f,0.7f,paint);}paint.setStyle(Paint.Style.STROKE);break;
    case "wifi":c.drawArc(-1,5,25,31,225,90,false,paint);c.drawArc(3,9,21,27,225,90,false,paint);c.drawArc(7,13,17,23,225,90,false,paint);paint.setStyle(Paint.Style.FILL);c.drawCircle(12,20,1.35f,paint);paint.setStyle(Paint.Style.STROKE);break;
    case "custom":c.drawLine(4,6,20,6,paint);c.drawLine(4,12,20,12,paint);c.drawLine(4,18,20,18,paint);c.drawCircle(8,6,2,paint);c.drawCircle(16,12,2,paint);c.drawCircle(10,18,2,paint);break;
    case "gallery":c.drawRoundRect(2,4,22,20,3,3,paint);c.drawCircle(8,9,2,paint);p.moveTo(4,18);p.lineTo(10,12);p.lineTo(14,16);p.lineTo(17,13);p.lineTo(21,18);c.drawPath(p,paint);break;
    case "camera":c.drawRoundRect(2,6,22,20,3,3,paint);c.drawCircle(12,13,4,paint);c.drawLine(8,3,16,3,paint);break;
    case "keyboard":c.drawRoundRect(2,5,22,19,3,3,paint);for(int row=0;row<2;row++)for(int col=0;col<4;col++)c.drawPoint(6+col*4,9+row*3,paint);c.drawLine(8,16,16,16,paint);break;
    case "browser":c.drawCircle(12,12,9,paint);c.drawOval(8,3,16,21,paint);c.drawLine(3,12,21,12,paint);c.drawArc(4,5,20,14,0,180,false,paint);break;
    case "carsettings":c.drawRoundRect(3,10,21,17,2,2,paint);c.drawLine(6,10,8,6,paint);c.drawLine(8,6,16,6,paint);c.drawLine(16,6,18,10,paint);c.drawCircle(7,17,1.5f,paint);c.drawCircle(17,17,1.5f,paint);c.drawLine(10,13,14,13,paint);break;
    case "systemsettings":c.drawCircle(12,12,7,paint);c.drawCircle(12,12,2.5f,paint);for(int n=0;n<8;n++){double a=n*Math.PI/4;c.drawLine(12+(float)Math.cos(a)*8,12+(float)Math.sin(a)*8,12+(float)Math.cos(a)*10,12+(float)Math.sin(a)*10,paint);}break;
    case "ipod":c.drawRoundRect(5,2,19,22,3,3,paint);c.drawRect(8,5,16,11,paint);c.drawCircle(12,16,3,paint);break;
    case "playpause":p.moveTo(3,5);p.lineTo(11,12);p.lineTo(3,19);p.close();c.drawPath(p,paint);c.drawLine(16,5,16,19,paint);c.drawLine(21,5,21,19,paint);break;
    case "sim":p.moveTo(8,2);p.lineTo(20,2);p.lineTo(20,22);p.lineTo(4,22);p.lineTo(4,6);p.close();c.drawPath(p,paint);c.drawRect(8,10,16,18,paint);break;
    case "terminal":c.drawRoundRect(2,4,22,20,3,3,paint);p.moveTo(6,9);p.lineTo(10,12);p.lineTo(6,15);c.drawPath(p,paint);c.drawLine(13,16,18,16,paint);break;
    case "guide":c.drawRoundRect(4,2,20,22,2,2,paint);c.drawLine(8,2,8,22,paint);c.drawLine(11,8,17,8,paint);c.drawLine(11,12,17,12,paint);break;
    case "phone":p.moveTo(6,3);
p.lineTo(9,3);
p.lineTo(11,7);
p.lineTo(9,9);
p.cubicTo(10,12,12,14,15,15);
p.lineTo(17,13);
p.lineTo(21,15);
p.lineTo(21,18);
p.cubicTo(20,22,14,21,8,16);
p.cubicTo(3,11,2,5,6,3);
c.drawPath(p,paint);
break;

    case "apps":for(int y=4;
y<19;
y+=7)for(int x=4;
x<19;
x+=7)c.drawRoundRect(x,y,x+4,y+4,1,1,paint);
break;

    default:c.drawCircle(12,12,7,paint);
c.drawCircle(12,12,2.5f,paint);
for(int n=0;
n<8;
n++){double a=n*Math.PI/4;
c.drawLine((float)(12+8*Math.cos(a)),(float)(12+8*Math.sin(a)),(float)(12+10*Math.cos(a)),(float)(12+10*Math.sin(a)),paint);
}
   }
}
 }
 private TextView mediaAccessHint,sourceLabel;private boolean mediaAccessGranted;
 private ImageView albumArt;private TextView trackTitle,trackArtist,elapsed,total;private SeekBar progress;private LinearLayout seekRow;private boolean dragging;private boolean mediaNarrow,previewBuild;
 private android.media.session.MediaSessionManager sessionManager;
 private final ArrayList<android.media.session.MediaController> controllers=new ArrayList<>();
 private android.media.session.MediaController selectedController;
 private String lastTitle,lastArtist,lastSource,lastElapsed,lastTotal;private android.graphics.Bitmap lastArt;private Boolean lastPlaying;private long lastDuration=-1;
 private android.graphics.drawable.Drawable playGlyph,pauseGlyph,noteGlyph;
 private final android.media.session.MediaController.Callback mediaCallback=new android.media.session.MediaController.Callback(){
  @Override public void onMetadataChanged(android.media.MediaMetadata metadata){selectController();}
  @Override public void onPlaybackStateChanged(android.media.session.PlaybackState state){selectController();}
  @Override public void onSessionDestroyed(){if(resumed)try{updateSessions(sessionManager.getActiveSessions(new ComponentName(HomeActivity.this,MediaAccess.class)));}catch(SecurityException e){updateSessions(null);}}
 };
 private final android.media.session.MediaSessionManager.OnActiveSessionsChangedListener sessionsChanged=list->updateSessions(list);
 private boolean isPlaying(android.media.session.MediaController c){android.media.session.PlaybackState state=c==null?null:c.getPlaybackState();return state!=null&&state.getState()==android.media.session.PlaybackState.STATE_PLAYING;}
 private void startMedia(){sessionManager=(android.media.session.MediaSessionManager)getSystemService(MEDIA_SESSION_SERVICE);try{ComponentName listener=new ComponentName(this,MediaAccess.class);sessionManager.addOnActiveSessionsChangedListener(sessionsChanged,listener,handler);mediaAccessGranted=true;updateSessions(sessionManager.getActiveSessions(listener));}catch(SecurityException e){mediaAccessGranted=false;updateSessions(null);}}
 private void stopMedia(){if(sessionManager!=null)try{sessionManager.removeOnActiveSessionsChangedListener(sessionsChanged);}catch(SecurityException ignored){}for(android.media.session.MediaController c:controllers)c.unregisterCallback(mediaCallback);controllers.clear();selectedController=null;handler.removeCallbacks(progressTick);}
 private void updateSessions(java.util.List<android.media.session.MediaController> list){if(!resumed)return;for(android.media.session.MediaController c:controllers)c.unregisterCallback(mediaCallback);controllers.clear();if(list!=null)for(android.media.session.MediaController c:list){controllers.add(c);c.registerCallback(mediaCallback,handler);}selectController();}
 private void selectController(){if(!resumed)return;android.media.session.MediaController next=null;for(android.media.session.MediaController c:controllers)if(isPlaying(c)){next=c;break;}if(next==null&&!controllers.isEmpty())next=controllers.get(0);selectedController=next;refreshPlayback();}
 private void resetMediaCache(){lastTitle=null;lastArtist=null;lastSource=null;lastElapsed=null;lastTotal=null;lastArt=null;lastPlaying=null;lastDuration=-1;playGlyph=new MediaGlyph("play",white);pauseGlyph=new MediaGlyph("pause",white);noteGlyph=new MediaGlyph("note",theme.textSecondary);}
 private String sourceName(android.media.session.MediaController c){if(c==null)return "";String pkg=c.getPackageName();if(pkg.equals("com.bw.source")){String source=android.provider.Settings.System.getString(getContentResolver(),"bw_current_source_name");if("RADIO".equals(source))return "RADIO";if("MUSIC_BT".equals(source))return "BLUETOOTH";return "";}if(pkg.equals("com.android.bluetooth"))return "BLUETOOTH";if(pkg.equals("com.andrerinas.headunitrevived"))return "ANDROID AUTO";if(pkg.equals("com.shihab.diplay")||pkg.equals("com.shihab.diplay.hudtest"))return "DIPLAY";try{return getPackageManager().getApplicationLabel(getPackageManager().getApplicationInfo(pkg,0)).toString();}catch(Exception e){return "";}}
 private void refreshPlayback(){if(playbackButton==null){handler.removeCallbacks(progressTick);return;}if(mediaAccessHint!=null)mediaAccessHint.setVisibility(mediaAccessGranted?View.GONE:View.VISIBLE);String source=sourceName(selectedController);if(!source.equals(lastSource)){sourceLabel.setText(source);sourceLabel.setVisibility(source.isEmpty()?View.GONE:View.VISIBLE);lastSource=source;}android.media.MediaMetadata md=selectedController==null?null:selectedController.getMetadata();String title=NowPlaying.value(md,android.media.MediaMetadata.METADATA_KEY_TITLE);if(title.isEmpty())title="No media playing";String artist=NowPlaying.value(md,android.media.MediaMetadata.METADATA_KEY_ARTIST);if(artist.isEmpty())artist=NowPlaying.value(md,android.media.MediaMetadata.METADATA_KEY_ALBUM_ARTIST);if(!title.equals(lastTitle)){trackTitle.setText(title);lastTitle=title;}if(!artist.equals(lastArtist)){trackArtist.setText(artist);lastArtist=artist;}
 android.graphics.Bitmap art=md==null?null:md.getBitmap(android.media.MediaMetadata.METADATA_KEY_ALBUM_ART);if(art==null&&md!=null)art=md.getBitmap(android.media.MediaMetadata.METADATA_KEY_ART);if(art==null&&md!=null)art=md.getBitmap(android.media.MediaMetadata.METADATA_KEY_DISPLAY_ICON);if(art!=lastArt||lastDuration<0){if(art!=null){albumArt.setPadding(0,0,0,0);albumArt.setImageBitmap(art);}else{albumArt.setPadding(dp(12),dp(12),dp(12),dp(12));albumArt.setImageDrawable(noteGlyph);}lastArt=art;}
 long duration=md==null?0:md.getLong(android.media.MediaMetadata.METADATA_KEY_DURATION);if(duration!=lastDuration){seekRow.setVisibility(duration>0&&!mediaNarrow?View.VISIBLE:View.GONE);progress.setMax((int)Math.min(duration,Integer.MAX_VALUE));lastDuration=duration;}boolean playing=isPlaying(selectedController);if(lastPlaying==null||lastPlaying!=playing){playbackButton.setImageDrawable(playing?pauseGlyph:playGlyph);playbackButton.setContentDescription(playing?"Pause":"Play");lastPlaying=playing;}handler.removeCallbacks(progressTick);updateProgress();if(resumed&&playing&&duration>0)handler.postDelayed(progressTick,1000);}
 private void updateProgress(){if(lastDuration<=0||dragging||selectedController==null)return;android.media.session.PlaybackState st=selectedController.getPlaybackState();long pos=st==null?0:st.getPosition();if(st!=null&&st.getState()==android.media.session.PlaybackState.STATE_PLAYING&&st.getLastPositionUpdateTime()>0)pos+=(long)((android.os.SystemClock.elapsedRealtime()-st.getLastPositionUpdateTime())*st.getPlaybackSpeed());pos=Math.max(0,Math.min(pos,lastDuration));progress.setProgress((int)Math.min(pos,Integer.MAX_VALUE));String e=formatDuration(pos),t=formatDuration(lastDuration);if(!e.equals(lastElapsed)){elapsed.setText(e);lastElapsed=e;}if(!t.equals(lastTotal)){total.setText(t);lastTotal=t;}}
 static String formatDuration(long ms){long seconds=ms/1000%60;return ms/60000+":"+(seconds<10?"0":"")+seconds;}
 void invalidateSettings(){uiDirty=true;}
 private void apps(){startActivity(new Intent(this,AppsActivity.class));
}
 @Override protected void onNewIntent(Intent intent){super.onNewIntent(intent);setIntent(intent);}
 @Override public void onWindowFocusChanged(boolean focused){super.onWindowFocusChanged(focused);if(focused)getWindow().getDecorView().setSystemUiVisibility(UiFlags.IMMERSIVE);}
 @Override public void onBackPressed(){}
 @Override protected void onResume(){super.onResume();resumed=true;if(uiDirty){build();uiDirty=false;}else if(environment!=null)environment.start();handler.removeCallbacks(tick);handler.post(tick);startMedia();startup.onResume();startup.checkBoot();scheduleScreenOff();getWindow().addFlags(prefs.getBoolean("awake",true)?WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON:0);if(!prefs.getBoolean("awake",true))getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);}
 @Override protected void onPause(){resumed=false;handler.removeCallbacks(tick);handler.removeCallbacks(screenOff);startup.onPause();stopMedia();if(environment!=null)environment.stop();super.onPause();}
 @Override protected void onDestroy(){prefs.unregisterOnSharedPreferenceChangeListener(settingsChanged);startup.cancel();handler.removeCallbacksAndMessages(null);super.onDestroy();}
}
