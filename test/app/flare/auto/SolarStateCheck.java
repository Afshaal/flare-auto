package app.flare.auto;
public final class SolarStateCheck {
 public static void main(String[] args){check(383,384,1028,385,false,384);check(384,384,1028,385,true,1028);check(706,384,1028,385,true,1028);check(1027,384,1028,385,true,1028);check(1028,384,1028,385,false,385);check(0,384,1028,385,false,384);System.out.println("Solar boundary checks passed");}
 static void check(int minute,int rise,int set,int tomorrow,boolean day,int next){SolarState s=new SolarState(minute,rise,set,tomorrow);if(s.day!=day||s.next!=next||s.progress<0||s.progress>1)throw new AssertionError("minute="+minute);}
}
