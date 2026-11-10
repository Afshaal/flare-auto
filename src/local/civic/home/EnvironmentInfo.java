package local.civic.home;
import android.content.*;import android.content.pm.PackageManager;import android.location.*;import android.widget.*;import java.util.*;
final class EnvironmentInfo {
 final Context context;final TextView sun;final SolarIcon icon;int lastMinute=-1,lastDay=-1;int sunrise=-1,sunset=-1,nextRise=-1;double lat=Double.NaN,lon=Double.NaN;TimeZone zone=TimeZone.getDefault();
 EnvironmentInfo(Context c,TextView sun,SolarIcon icon){context=c;this.sun=sun;this.icon=icon;}
 void start(){lastDay=-1;updateSolar();}
 void stop(){}
 private boolean locate(){
  SharedPreferences p=context.getSharedPreferences("home",0);
  if(p.getBoolean("solar_manual",false))try{double a=Double.parseDouble(p.getString("solar_lat","")),b=Double.parseDouble(p.getString("solar_lon",""));if(Double.isFinite(a)&&Double.isFinite(b)&&a>=-90&&a<=90&&b>=-180&&b<=180){lat=a;lon=b;return true;}}catch(RuntimeException ignored){}
  else if(context.checkSelfPermission("android.permission.ACCESS_FINE_LOCATION")==PackageManager.PERMISSION_GRANTED||context.checkSelfPermission("android.permission.ACCESS_COARSE_LOCATION")==PackageManager.PERMISSION_GRANTED){
   LocationManager lm=(LocationManager)context.getSystemService(Context.LOCATION_SERVICE);if(lm!=null)for(String provider:new String[]{LocationManager.GPS_PROVIDER,LocationManager.PASSIVE_PROVIDER,LocationManager.NETWORK_PROVIDER})try{Location loc=lm.getLastKnownLocation(provider);if(loc!=null){lat=loc.getLatitude();lon=loc.getLongitude();return true;}}catch(IllegalArgumentException|SecurityException ignored){}
  }
  lat=Double.NaN;lon=Double.NaN;return false;
 }
 void updateSolar(){TimeZone current=TimeZone.getDefault();if(!current.equals(zone)){zone=current;lastDay=-1;}Calendar date=Calendar.getInstance(zone);int key=date.get(Calendar.YEAR)*1000+date.get(Calendar.DAY_OF_YEAR);int minute=date.get(Calendar.HOUR_OF_DAY)*60+date.get(Calendar.MINUTE);if(key!=lastDay||Double.isNaN(lat)){
  lastDay=key;lastMinute=-1;if(locate()){sunrise=SolarTimes.event(date,lat,lon,zone,true);sunset=SolarTimes.event(date,lat,lon,zone,false);Calendar tomorrow=(Calendar)date.clone();tomorrow.add(Calendar.DAY_OF_YEAR,1);nextRise=SolarTimes.event(tomorrow,lat,lon,zone,true);}else sunrise=sunset=nextRise=-1;
 }if(lastMinute==minute)return;lastMinute=minute;if(Double.isNaN(lat)){sun.setText("--:--");sun.setContentDescription("Sunrise and sunset unavailable; set a location");icon.setDaytime(false);return;}SolarState state=new SolarState(minute,sunrise,sunset,nextRise);String event=state.day?"Sunset":"Sunrise";sun.setText(format(state.next));sun.setContentDescription(state.next<0?"Sunrise or sunset unavailable":event+" "+format(state.next));icon.setDaytime(state.day);}
 String format(int mins){if(mins<0)return "--:--";int h=mins/60;if(context.getSharedPreferences("home",0).getBoolean("clock24",false))return String.format(Locale.US,"%02d:%02d",h,mins%60);return String.format(Locale.US,"%02d:%02d %s",(h+11)%12+1,mins%60,h<12?"AM":"PM");}
 boolean night(){if(sunrise<0||sunset<0)return false;Calendar now=Calendar.getInstance(zone);int m=now.get(Calendar.HOUR_OF_DAY)*60+now.get(Calendar.MINUTE);return m>=sunset||m<sunrise;}
}
