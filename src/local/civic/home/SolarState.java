package local.civic.home;

final class SolarState {
 final boolean day;final int next;final float progress;
 SolarState(int minute,int rise,int set,int tomorrowRise){day=rise>=0&&set>rise&&minute>=rise&&minute<set;next=day?set:(set>=0&&minute>=set?tomorrowRise:rise);progress=day?(float)(minute-rise)/(set-rise):0;}
}
