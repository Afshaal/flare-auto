package local.civic.home;
import android.content.*;import java.util.*;import org.json.*;
final class DockConfig {
 static final String KEY="dock_items";static final String[] NAMES={"Audio","Radio","Bluetooth","Screen off","Apps"};static final String[] ICONS={"audio","radio","bluetooth","screen","apps"};
 static final class Item{int type;String target;boolean visible;Item(int t,String p,boolean v){type=t;target=p;visible=v;}}
 static ArrayList<Item> read(SharedPreferences p){ArrayList<Item> out=new ArrayList<>();String raw=p.getString(KEY,"");if(!raw.isEmpty())try{JSONArray a=new JSONArray(raw);boolean hasApps=false;for(int i=0;i<a.length();i++){JSONObject o=a.getJSONObject(i);int type=o.getInt("type");if(type<0||type>5)continue;if(type==4){if(hasApps)continue;hasApps=true;}out.add(new Item(type,o.optString("target",""),type==4||o.optBoolean("visible",true)));}if(hasApps)return out;}catch(Exception ignored){}out.clear();for(int i:DockOrder.read(p))out.add(new Item(i,"",true));return out;}
 static void save(SharedPreferences p,ArrayList<Item> items){JSONArray a=new JSONArray();for(Item i:items)try{JSONObject o=new JSONObject();o.put("type",i.type);o.put("target",i.target);o.put("visible",i.type==4||i.visible);a.put(o);}catch(JSONException ignored){}p.edit().putString(KEY,a.toString()).apply();}
 static int maxSlots(Context c){int width=(int)(c.getResources().getDisplayMetrics().widthPixels/c.getResources().getDisplayMetrics().density);int spacing=c.getSharedPreferences("home",0).getInt("spacing",14);return Math.max(1,(width-spacing*2)/56);}
 static int size(Context c,int count,int desired){int width=(int)(c.getResources().getDisplayMetrics().widthPixels/c.getResources().getDisplayMetrics().density)-2*c.getSharedPreferences("home",0).getInt("spacing",14);return Math.max(1,Math.min(desired,Math.max(1,width/Math.max(1,count)-16)));}
 static String name(Context c,Item i){if(i.type<5)return NAMES[i.type];try{return c.getPackageManager().getActivityInfo(ComponentName.unflattenFromString(i.target),0).loadLabel(c.getPackageManager()).toString();}catch(Exception e){return "App unavailable";}}
}
