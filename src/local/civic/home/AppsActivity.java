package local.civic.home;

import android.app.*;
import android.os.*;
import android.content.*;
import android.content.pm.*;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.*;
import android.widget.*;
import java.util.*;

public class AppsActivity extends Activity {
 LinearLayout page;java.util.Map<String,?> settingsSnapshot;int loadGeneration;
 LinearLayout grid; TextView pageIndicator; ThemeTokens theme;boolean loaded;IconPacks.Pack activePack;String packKey="",pendingImagePackage;
 final android.os.Handler main=new android.os.Handler(android.os.Looper.getMainLooper());

 static final java.util.Map<String,Integer> COLORS=new java.util.HashMap<>();
 static {COLORS.put("carplay",0xffa64350);COLORS.put("androidauto",0xff3b7e70);COLORS.put("audio",0xff7c5e91);COLORS.put("radio",0xffa26443);COLORS.put("bluetooth",0xff456d92);COLORS.put("file",0xff957640);COLORS.put("browser",0xff78609b);COLORS.put("settings",0xff5f636b);COLORS.put("carsettings",0xff655c63);COLORS.put("default",0xff52545b);COLORS.put("wifi",0xff467c78);COLORS.put("custom",0xff986079);COLORS.put("camera",0xff527b74);COLORS.put("keyboard",0xff526b79);COLORS.put("ipod",0xff886c89);COLORS.put("playpause",0xff9c555b);COLORS.put("sim",0xff71804c);COLORS.put("terminal",0xff52616c);COLORS.put("guide",0xff90754f);}
 static int color(String key){Integer n=COLORS.get(key);return n==null?COLORS.get("default"):n;}
int pageNo;
float startX;
ArrayList<Entry> entries=new ArrayList<>();

 class Entry{String label,icon,autoIcon,baseIcon,pkg,cls;
int color;
boolean internal;
Entry(String l,String i,String p,String c,int co){label=l;
icon=i;autoIcon=i;baseIcon=i;
pkg=p;
cls=c;
color=co;
}}
 int dp(int n){return (int)(n*getResources().getDisplayMetrics().density+.5f);
}TextView text(String s,int size){TextView t=new TextView(this);
t.setText(s);
t.setTextColor(theme.textPrimary);
t.setTextSize(size);
t.setGravity(Gravity.CENTER);
return t;
}
 public void onCreate(Bundle b){super.onCreate(b);if(b!=null)pendingImagePackage=b.getString("pendingIconImage");getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(ThemeTokens.get(this).background));theme=ThemeTokens.get(this);
getWindow().getDecorView().setSystemUiVisibility(UiFlags.IMMERSIVE);
page=new LinearLayout(this);
page.setOrientation(LinearLayout.VERTICAL);
page.setPadding(dp(26),dp(14),dp(26),dp(20));
SharedPreferences backgrounds=getSharedPreferences("home",0);android.graphics.drawable.Drawable custom=backgrounds.getBoolean("drawer_custom",false)?CustomImages.load(this,"drawer"):null;if(custom!=null)page.setBackground(custom);else if(backgrounds.getBoolean("drawer_wallpaper",false))page.setBackground(WallpaperPack.home(this,theme));else page.setBackgroundColor(theme.background);
setContentView(page);
UiKit ui=new UiKit(this);
page.addView(ui.header("Apps"),new LinearLayout.LayoutParams(-1,dp(56)));
grid=new LinearLayout(this);
grid.setOrientation(LinearLayout.VERTICAL);
page.addView(grid,new LinearLayout.LayoutParams(-1,0,1));
pageIndicator=text("",12);pageIndicator.setTextColor(theme.textSecondary);page.addView(pageIndicator,new LinearLayout.LayoutParams(-1,dp(22)));
reloadApps();
}
 void reloadApps(){loaded=false;int generation=++loadGeneration;settingsSnapshot=new HashMap<>(getSharedPreferences("home",0).getAll());new Thread(()->loadApps(generation)).start();}
 void loadApps(int generation){
 ArrayList<Entry> entries=new ArrayList<>();
 org.json.JSONObject catalog=loadIconCatalog();
 entries.add(new Entry("CarPlay","carplay","com.shihab.diplay",null,color("carplay")));
entries.add(new Entry("Android Auto","androidauto","com.andrerinas.headunitrevived",null,color("androidauto")));
entries.add(new Entry("Audio","audio","com.bw.mediaplayer","com.bw.mediaplayer.ui.BtActivity",color("audio")));
entries.add(new Entry("Radio","radio","com.bw.mediaplayer","com.bw.mediaplayer.ui.RadioActivity",color("radio")));
entries.add(new Entry("Bluetooth","bluetooth","com.bw.phone","com.bw.phone.MainActivity",color("bluetooth")));
entries.add(new Entry("Files","file","me.zhanghai.android.files",null,color("file")));
entries.add(new Entry("Browser","browser","org.mozilla.focus",null,color("browser")));
Entry settings=new Entry("Launcher Settings","settings",null,null,color("settings"));
settings.internal=true;
entries.add(settings);
Entry device=new Entry("Car settings","carsettings","com.bw.carsetting",null,color("carsettings"));
entries.add(device);

 List<ResolveInfo> apps=getPackageManager().queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER),0);
Collections.sort(apps,new ResolveInfo.DisplayNameComparator(getPackageManager()));
for(ResolveInfo r:apps){String pkg=r.activityInfo.packageName;
if(pkg.equals(getPackageName())||pkg.equals("local.civic.dash")||pkg.equals("local.civic.btmusic")||pkg.equals("local.civic.radio"))continue;
boolean found=false;
for(Entry e:entries)if(pkg.equals(e.pkg)){found=true;
break;
}if(!found){String label=r.loadLabel(getPackageManager()).toString();String icon="original";int color=color("default");
if(label.equalsIgnoreCase("Basic Settings")){label="WiFi Settings";icon="wifi";color=color("wifi");}
else if(pkg.equals("com.android.settings")){label="System Settings";icon="systemsettings";}
else if(label.equalsIgnoreCase("Customization")){icon="custom";color=color("custom");}
else if(pkg.equals("local.civic.btmusic")){icon="audio";color=color("audio");}
else if(pkg.equals("local.civic.radio")){icon="radio";color=color("radio");}
else if(pkg.equals("com.android.camera2")||pkg.equals("com.bw.adas")){icon="camera";color=color("camera");}
else if(pkg.equals("com.android.gallery3d")||label.equalsIgnoreCase("Gallery")||label.equalsIgnoreCase("Photos")){icon="gallery";color=color("camera");}
else if(pkg.equals("rkr.simplekeyboard.inputmethod")){label="Simple Keyboard";icon="keyboard";color=color("keyboard");}
else if(label.equalsIgnoreCase("Files")){icon="original";color=color("file");}
else if(pkg.equals("com.bw.ipod")){icon="ipod";color=color("ipod");}
else if(pkg.equals("local.civic.button")){icon="playpause";color=color("playpause");}
else if(pkg.startsWith("com.android.stk")){icon="sim";color=color("sim");}
else if(pkg.equals("com.termux")){icon="terminal";color=color("terminal");}
else if(label.equalsIgnoreCase("User Guide")){icon="guide";color=color("guide");}
String brand=brandIcon(pkg);if(brand==null&&icon.equals("original"))brand=findCatalogIcon(catalog,label,pkg);if(brand!=null)icon=brand;
for(Entry existing:entries)if(existing.label.equalsIgnoreCase(label)){label=label+" ("+pkg+")";break;}
entries.add(new Entry(label,icon,pkg,r.activityInfo.name,color));}
}SharedPreferences saved=getSharedPreferences("home",0);String packKey=saved.getString(IconPacks.KEY,"");IconPacks.Pack activePack=IconPacks.load(this);applyPack(saved,entries,activePack);Set<String> hidden=saved.getStringSet("hidden_apps",Collections.emptySet());for(int i=entries.size()-1;i>=0;i--)if(!entries.get(i).internal&&hidden.contains(entries.get(i).pkg))entries.remove(i);main.post(()->{if(!isFinishing()&&!isDestroyed()&&generation==loadGeneration){this.entries=entries;this.packKey=packKey;this.activePack=activePack;loaded=true;if(!settingsSnapshot.equals(getSharedPreferences("home",0).getAll())){reloadApps();return;}theme=ThemeTokens.get(this);new UiKit(this).recolor(page);SharedPreferences p=getSharedPreferences("home",0);android.graphics.drawable.Drawable custom=p.getBoolean("drawer_custom",false)?CustomImages.load(this,"drawer"):null;page.setBackground(custom!=null?custom:p.getBoolean("drawer_wallpaper",false)?WallpaperPack.home(this,theme):new android.graphics.drawable.ColorDrawable(theme.background));pageIndicator.setTextColor(theme.textSecondary);render();}});
}
 void applyPack(SharedPreferences saved,ArrayList<Entry> entries,IconPacks.Pack activePack){for(Entry e:entries){e.autoIcon=e.baseIcon;boolean protectedIcon=e.internal||e.pkg==null||e.baseIcon.equals("carplay")||e.baseIcon.equals("androidauto");if(!protectedIcon&&!e.pkg.startsWith("com.bw.")&&activePack!=null){String match=activePack.lookup(this,e.pkg,e.cls);if(match!=null&&activePack.drawableId(match)!=0)e.autoIcon="pack:"+match;}e.icon=e.autoIcon;if(!protectedIcon){String override=saved.getString("icon_override_"+e.pkg,"");if(override.startsWith("si_")&&getResources().getIdentifier(override,"drawable",getPackageName())!=0)e.icon=override;}}}
 @Override protected void onResume(){super.onResume();if(loaded&&!settingsSnapshot.equals(getSharedPreferences("home",0).getAll()))reloadApps();}
 @Override protected void onDestroy(){loadGeneration++;main.removeCallbacksAndMessages(null);super.onDestroy();}
 @Override protected void onSaveInstanceState(Bundle out){out.putString("pendingIconImage",pendingImagePackage);super.onSaveInstanceState(out);}
 @Override public boolean dispatchTouchEvent(android.view.MotionEvent e){if(!loaded)return super.dispatchTouchEvent(e);if(e.getAction()==0)startX=e.getX();
if(e.getAction()==1&&Math.abs(e.getX()-startX)>dp(70)){int per=Math.max(4,Math.min(6,getSharedPreferences("home",0).getInt("columns",5)))*2;
pageNo=Math.max(0,Math.min((entries.size()-1)/per,pageNo+(e.getX()<startX?1:-1)));
MotionEvent cancel=MotionEvent.obtain(e);cancel.setAction(MotionEvent.ACTION_CANCEL);super.dispatchTouchEvent(cancel);cancel.recycle();render();
return true;
}return super.dispatchTouchEvent(e);
}
 void render(){grid.removeAllViews();
int cols=Math.max(4,Math.min(6,getSharedPreferences("home",0).getInt("columns",5)));int pages=Math.max(1,(entries.size()+cols*2-1)/(cols*2));pageNo=Math.max(0,Math.min(pageNo,pages-1));StringBuilder dots=new StringBuilder();for(int n=0;n<pages;n++)dots.append(n==pageNo?"● ":"○ ");pageIndicator.setText(dots.toString().trim());
for(int y=0;
y<2;
y++){LinearLayout row=new LinearLayout(this);
grid.addView(row,new LinearLayout.LayoutParams(-1,0,1));
for(int x=0;
x<cols;
x++){int i=pageNo*cols*2+y*cols+x;
LinearLayout cell=new LinearLayout(this);
cell.setOrientation(LinearLayout.VERTICAL);
cell.setGravity(Gravity.CENTER);
row.addView(cell,new LinearLayout.LayoutParams(0,-1,1));
if(i>=entries.size())continue;
Entry e=entries.get(i);
android.widget.FrameLayout square=new android.widget.FrameLayout(this);
GradientDrawable d=new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{e.color,shade(e.color)});
d.setCornerRadius(dp(21));
square.setBackground(d);
android.widget.FrameLayout.LayoutParams ip=new android.widget.FrameLayout.LayoutParams(dp(37),dp(37),Gravity.CENTER);
android.graphics.drawable.Drawable imported=e.pkg==null?null:(getSharedPreferences("home",0).getBoolean("icon_image_"+e.pkg,false)?CustomImages.load(this,CustomImages.iconRole(e.pkg)):null);android.graphics.drawable.Drawable packed=activePack!=null&&e.icon.startsWith("pack:")?activePack.drawable(e.icon.substring(5)):null;if(imported!=null||packed!=null){ImageView icon=new ImageView(this);icon.setImageDrawable(imported!=null?imported:packed);square.addView(icon,ip);}else if(e.icon.equals("carplay")||e.icon.equals("androidauto")||e.icon.startsWith("brand_")||e.icon.startsWith("si_")){ImageView icon=new ImageView(this);String resource=e.icon.equals("carplay")?"ic_carplay_mark":e.icon.equals("androidauto")?"ic_android_auto_mark":e.icon;icon.setImageResource(getResources().getIdentifier(resource,"drawable",getPackageName()));int tint=(e.icon.equals("carplay")||e.icon.equals("androidauto"))?theme.textPrimary:getSharedPreferences("home",0).getInt("icon_color_"+e.pkg,theme.textPrimary);icon.setColorFilter(tint);square.addView(icon,ip);}else if(e.icon.equals("original")){TextView monogram=text(e.label.isEmpty()?"?":e.label.substring(0,1).toUpperCase(java.util.Locale.getDefault()),24);monogram.setTypeface(android.graphics.Typeface.create("sans-serif-medium",android.graphics.Typeface.NORMAL));monogram.setTextColor(theme.textPrimary);square.addView(monogram,ip);}else{square.addView(new HomeActivity.NavIcon(this,e.icon,theme.textPrimary),ip);}
cell.addView(square,new LinearLayout.LayoutParams(dp(78),dp(78)));
TextView label=text(e.label,15);
LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(42));
lp.topMargin=dp(4);
cell.addView(label,lp);
cell.setContentDescription(e.label);
cell.setOnClickListener(v->open(e));
if(!e.internal&&e.pkg!=null&&!e.icon.equals("carplay")&&!e.icon.equals("androidauto"))cell.setOnLongClickListener(v->{new ThemedDialogs.Builder(this).setTitle(e.label).setItems(new String[]{"Choose from Simple Icons","Choose image","Use automatic icon"},(dialog,n)->{if(n==0)startActivityForResult(new Intent(this,IconPickerActivity.class).putExtra("package",e.pkg),413);else if(n==1){pendingImagePackage=e.pkg;CustomImages.choose(this);}else{getSharedPreferences("home",0).edit().remove("icon_override_"+e.pkg).remove("icon_color_"+e.pkg).remove("icon_image_"+e.pkg).apply();for(Entry item:entries)if(e.pkg.equals(item.pkg))item.icon=item.autoIcon;render();}}).show();return true;});
}}}
 @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(request==CustomImages.PICK&&result==RESULT_OK&&data!=null&&data.getData()!=null&&pendingImagePackage!=null){String pkg=pendingImagePackage;CustomImages.importImage(this,data.getData(),CustomImages.iconRole(pkg),()->{getSharedPreferences("home",0).edit().putBoolean("icon_image_"+pkg,true).apply();pendingImagePackage=null;render();});return;}if(request==413&&result==RESULT_OK&&data!=null){String pkg=data.getStringExtra("package"),icon=data.getStringExtra("icon");if(pkg!=null&&icon!=null){for(Entry e:entries)if(pkg.equals(e.pkg))e.icon=icon;render();}}}
 org.json.JSONObject loadIconCatalog(){try(java.io.InputStream in=getAssets().open("simple-icons-index.json");java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream()){byte[] buf=new byte[8192];for(int n;(n=in.read(buf))!=-1;)out.write(buf,0,n);return new org.json.JSONObject(out.toString("UTF-8"));}catch(Exception e){return new org.json.JSONObject();}}
 String findCatalogIcon(org.json.JSONObject catalog,String label,String pkg){String name=label.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9]","");String icon=catalog.optString(name,"");if(!icon.isEmpty())return icon;String[] parts=pkg.toLowerCase(java.util.Locale.ROOT).split("\\.");for(String part:parts){if(part.length()<5||part.equals("android")||part.equals("google"))continue;icon=catalog.optString(part.replaceAll("[^a-z0-9]",""),"");if(!icon.isEmpty())return icon;}return null;}
 String brandIcon(String pkg){switch(pkg){case "com.android.vending":return "brand_googleplay";case "com.google.android.apps.photos":return "brand_googlephotos";case "com.google.android.apps.docs":return "brand_googledrive";case "com.google.android.gm":return "brand_gmail";case "com.google.android.googlequicksearchbox":return "brand_google";case "com.google.android.calendar":return "brand_googlecalendar";case "com.apple.android.music":return "brand_applemusic";case "deezer.android.app":return "brand_deezer";case "com.aspiro.tidal":return "brand_tidal";case "com.shazam.android":return "brand_shazam";case "au.com.shiftyjelly.pocketcasts":return "brand_pocketcasts";case "com.plexapp.android":return "brand_plex";case "com.discord":return "brand_discord";case "com.spotify.music":return "brand_spotify";case "com.google.android.youtube":return "brand_youtube";case "com.google.android.apps.youtube.music":return "brand_youtubemusic";case "com.google.android.apps.maps":return "brand_googlemaps";case "com.waze":return "brand_waze";case "com.android.chrome":return "brand_googlechrome";case "org.mozilla.firefox":return "brand_firefox";case "com.whatsapp":return "brand_whatsapp";case "org.telegram.messenger":return "brand_telegram";case "com.netflix.mediaclient":return "brand_netflix";case "org.videolan.vlc":return "brand_vlcmediaplayer";case "com.soundcloud.android":return "brand_soundcloud";default:return null;}}
 int shade(int c){return Color.rgb((int)(Color.red(c)*.65),(int)(Color.green(c)*.65),(int)(Color.blue(c)*.65));
}
 void open(Entry e){if(e.internal){startActivity(new Intent(this,SettingsActivity.class));
return;
}try{Intent i;
if(e.cls!=null)i=new Intent(Intent.ACTION_MAIN).setClassName(e.pkg,e.cls);
else i=getPackageManager().getLaunchIntentForPackage(e.pkg);
if(i==null)throw new Exception();
startActivity(i);
}catch(Exception ex){Toast.makeText(this,e.label+" is not installed on this device",Toast.LENGTH_SHORT).show();
}}
}
