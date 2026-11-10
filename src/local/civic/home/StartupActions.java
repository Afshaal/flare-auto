package local.civic.home;
import android.app.Activity;import android.content.*;import android.os.Handler;import java.io.*;

final class StartupActions {
 final Activity activity;final SharedPreferences prefs;final Handler handler;boolean running,ownTransition;final Runnable source=()->runSource(),app=()->runApp(),saver=()->runSaver();
 StartupActions(Activity a,SharedPreferences p,Handler h){activity=a;prefs=p;handler=h;}
 void checkBoot(){String id=bootId();if(id.isEmpty())return;String previous=prefs.getString("startup_last_boot","");if(id.equals(previous))return;prefs.edit().putString("startup_last_boot",id).apply();if(previous.isEmpty())return;String source=prefs.getString("startup_source","none"),app=prefs.getString("startup_app","");boolean saver=prefs.getBoolean("startup_saver",false);if(source.equals("none")&&app.isEmpty()&&!saver)return;running=true;long delay=Math.max(5,Math.min(30,prefs.getInt("startup_delay",10)))*1000L;handler.postDelayed(this.source,delay);}
 static String bootId(){try(BufferedReader in=new BufferedReader(new FileReader("/proc/sys/kernel/random/boot_id"))){String id=in.readLine();return id==null?"":id.trim();}catch(Exception ignored){return "";}}
 void runSource(){if(!running)return;String source=prefs.getString("startup_source","none");if(source.equals("radio")||source.equals("bluetooth")){String target=prefs.getString(source.equals("radio")?"app_radio":"app_audio","");ComponentName component=target.isEmpty()?null:ComponentName.unflattenFromString(target);if(component!=null)launch(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setComponent(component));else{String cls=source.equals("radio")?"com.bw.mediaplayer.ui.RadioActivity":"com.bw.mediaplayer.ui.BtActivity";launch(new Intent().setClassName("com.bw.mediaplayer",cls));}handler.postDelayed(app,1200);}else runApp();}
 void runApp(){if(!running)return;String target=prefs.getString("startup_app","");if(!target.isEmpty()){ComponentName name=ComponentName.unflattenFromString(target);if(name!=null)launch(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setComponent(name));handler.postDelayed(saver,1200);}else runSaver();}
 void runSaver(){if(!running)return;if(prefs.getBoolean("startup_saver",false))launch(new Intent(activity,ScreenSaverActivity.class));running=false;ownTransition=false;}
 void launch(Intent intent){try{ownTransition=true;activity.startActivity(intent);}catch(Exception ignored){ownTransition=false;}}
 void onPause(){if(!ownTransition)cancel();}void onResume(){ownTransition=false;}void cancel(){running=false;ownTransition=false;handler.removeCallbacks(source);handler.removeCallbacks(app);handler.removeCallbacks(saver);}
}
