package local.civic.home;
import java.util.*;
public final class SolarTimesCheck {
 public static void main(String[] args){TimeZone z=TimeZone.getTimeZone("Asia/Karachi");Calendar c=Calendar.getInstance(z);c.clear();c.set(2026,Calendar.NOVEMBER,8,12,0);int rise=SolarTimes.event(c,31.5497,74.3436,z,true),set=SolarTimes.event(c,31.5497,74.3436,z,false);if(rise<370||rise>410||set<1000||set>1040)throw new AssertionError("Unexpected Lahore solar times: "+rise+" "+set);System.out.println("Lahore sunrise="+rise/60+":"+rise%60+" sunset="+set/60+":"+set%60);}
}
