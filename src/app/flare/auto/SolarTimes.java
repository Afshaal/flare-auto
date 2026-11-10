package app.flare.auto;
import java.util.*;

final class SolarTimes {
 static int event(Calendar day,double lat,double lon,TimeZone zone,boolean sunrise){int n=day.get(Calendar.DAY_OF_YEAR);double lng=lon/15.0,t=n+((sunrise?6:18)-lng)/24.0,m=.9856*t-3.289;double l=norm(m+1.916*sin(m)+.020*sin(2*m)+282.634);double ra=norm(Math.toDegrees(Math.atan(.91764*Math.tan(Math.toRadians(l)))));ra+=(Math.floor(l/90)*90-Math.floor(ra/90)*90);ra/=15;double sinDec=.39782*sin(l),cosDec=Math.cos(Math.asin(sinDec));double cosH=(Math.cos(Math.toRadians(90.833))-sinDec*sin(lat))/(cosDec*Math.cos(Math.toRadians(lat)));if(cosH>1||cosH< -1)return -1;double h=(sunrise?360-Math.toDegrees(Math.acos(cosH)):Math.toDegrees(Math.acos(cosH)))/15;double utc=(h+ra-.06571*t-6.622-lng)%24;if(utc<0)utc+=24;Calendar noon=(Calendar)day.clone();noon.set(Calendar.HOUR_OF_DAY,12);int offset=zone.getOffset(noon.getTimeInMillis())/60000;return ((int)Math.round(utc*60)+offset+1440)%1440;}
 static double sin(double d){return Math.sin(Math.toRadians(d));}static double norm(double d){d%=360;return d<0?d+360:d;}
}
