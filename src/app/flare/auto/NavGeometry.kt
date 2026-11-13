package app.flare.auto

import kotlin.math.*

/** One instruction: [text] (spoken), GraphHopper [sign] (turn arrow), distance [at] along the route, [road] name. */
internal data class NavStep(val text:String,val sign:Int,val at:Double,val road:String="")
/**
 * [roads]: (road name, metres) per instruction, for labelling route options ([RouteOptions]); [via]: the point a
 * route option was routed through (null for the fastest route). [millis]: GraphHopper's travel time, never adjusted;
 * [stepMillis]: its time per instruction (same order as [steps], summing to [millis]) so the remaining time follows
 * the speed of each stretch ([TrafficEta.baseAt]); null for routes built without it (average speed is used).
 */
internal data class NavRoute(val points:DoubleArray,val cumulative:DoubleArray,val steps:List<NavStep>,val distance:Double,val millis:Long,
                             val roads:List<Pair<String,Double>> = emptyList(),val via:Pair<Double,Double>? = null,
                             val stepMillis:LongArray? = null)
internal object NavGeometry {
    data class Position(val segment:Int,val along:Double,val away:Double)
    fun distance(a:Double,b:Double,c:Double,d:Double):Double {
        val x=Math.toRadians(c-a);val y=Math.toRadians(d-b)
        val h=sin(x/2).pow(2)+cos(Math.toRadians(a))*cos(Math.toRadians(c))*sin(y/2).pow(2)
        return 12742000*asin(sqrt(h.coerceIn(0.0,1.0)))
    }
    fun cumulative(points:DoubleArray):DoubleArray {
        val result=DoubleArray(points.size/2)
        for(i in 1 until result.size) result[i]=result[i-1]+distance(points[(i-1)*2],points[(i-1)*2+1],points[i*2],points[i*2+1])
        return result
    }
    fun nearest(route:NavRoute,lat:Double,lon:Double,previous:Int):Position {
        val points=route.points;val count=points.size/2
        if(count<2) return Position(0,0.0,Double.POSITIVE_INFINITY)
        val xScale=111320*cos(Math.toRadians(lat));val yScale=111320.0
        fun scan(first:Int,last:Int):Position {
            var best=Double.POSITIVE_INFINITY;var segment=first;var along=0.0
            for(i in first..last) {
                val ax=(points[i*2+1]-lon)*xScale;val ay=(points[i*2]-lat)*yScale
                val bx=(points[i*2+3]-lon)*xScale;val by=(points[i*2+2]-lat)*yScale
                val dx=bx-ax;val dy=by-ay;val length=dx*dx+dy*dy
                val t=if(length==0.0) 0.0 else (-(ax*dx+ay*dy)/length).coerceIn(0.0,1.0)
                val squared=(ax+t*dx).pow(2)+(ay+t*dy).pow(2)
                if(squared<best) { best=squared;segment=i;along=route.cumulative[i]+t*(route.cumulative[i+1]-route.cumulative[i]) }
            }
            return Position(segment,along,sqrt(best))
        }
        val nearby=scan((previous-15).coerceIn(0,count-2),(previous+160).coerceIn(0,count-2))
        return if(nearby.away<=100) nearby else scan(0,count-2)
    }
    /**
     * The part of [route] still ahead of a position [along] metres from the start on [segment] (from [nearest]):
     * the snapped point followed by the remaining vertices, as lat,lon pairs. The whole route for along <= 0.
     */
    fun remaining(route:NavRoute,segment:Int,along:Double):DoubleArray {
        val points=route.points;val count=points.size/2
        if(count<2 || along<=0.0) return points
        val i=segment.coerceIn(0,count-2)
        val length=route.cumulative[i+1]-route.cumulative[i]
        val t=if(length<=0.0) 0.0 else ((along-route.cumulative[i])/length).coerceIn(0.0,1.0)
        val lat=points[i*2]+t*(points[i*2+2]-points[i*2]);val lon=points[i*2+1]+t*(points[i*2+3]-points[i*2+1])
        val out=DoubleArray(2+(count-i-1)*2)
        out[0]=lat;out[1]=lon
        System.arraycopy(points,(i+1)*2,out,2,(count-i-1)*2)
        return out
    }
}

/** Text for the navigation cards; pure so it can be unit tested. */
internal object TripFormat {
    /** Distance to the next turn: "40 m", "350 m", "1.2 km", "12 km". */
    fun distance(metres:Double):String = when {
        metres<100 -> "${(metres/10).roundToInt()*10} m"
        metres<1000 -> "${(metres/50).roundToInt()*50} m".let { if(it=="1000 m") "1.0 km" else it }
        metres<10000 -> String.format(java.util.Locale.US,"%.1f km",metres/1000)
        else -> "${(metres/1000).roundToInt()} km"
    }
    /** Remaining travel time: "1 min", "45 min", "1 h 05 min". */
    fun duration(millis:Long):String {
        val minutes=maxOf(1L,(millis+30000)/60000)
        return if(minutes<60) "$minutes min" else "${minutes/60} h ${(minutes%60).toString().padStart(2,'0')} min"
    }
    /** Arrival clock time for a trip of [millis] starting at [now]. */
    fun arrival(now:Long,millis:Long,clock24:Boolean,zone:java.util.TimeZone=java.util.TimeZone.getDefault()):String =
        java.text.SimpleDateFormat(if(clock24) "HH:mm" else "h:mm a",java.util.Locale.US).apply { timeZone=zone }.format(java.util.Date(now+millis))
    /** Arrival clock time without AM/PM ("11:58" in 12-hour mode, "23:58" in 24-hour mode). */
    fun clock(now:Long,millis:Long,clock24:Boolean,zone:java.util.TimeZone=java.util.TimeZone.getDefault()):String =
        java.text.SimpleDateFormat(if(clock24) "HH:mm" else "h:mm",java.util.Locale.US).apply { timeZone=zone }.format(java.util.Date(now+millis))
    /** Remaining time as value and unit for the trip card: "16" / "min", or "1:05" / "hr" from one hour. */
    fun durationParts(millis:Long):Pair<String,String> {
        val minutes=maxOf(1L,(millis+30000)/60000)
        return if(minutes<60) "$minutes" to "min" else "${minutes/60}:${(minutes%60).toString().padStart(2,'0')}" to "hr"
    }
    /** Remaining distance as value and unit for the trip card: "850" / "m", "4.3" / "km", "15" / "km". */
    fun distanceParts(metres:Double):Pair<String,String> = when {
        metres<100 -> "${(metres/10).roundToInt()*10}" to "m"
        metres<950 -> "${(metres/50).roundToInt()*50}" to "m"
        metres<9950 -> String.format(java.util.Locale.US,"%.1f",metres/1000) to "km"
        else -> "${(metres/1000).roundToInt()}" to "km"
    }
    /** Road name for the turn card; long names are cut with "…" by the view, not here. Falls back to [text]. */
    fun road(step:NavStep):String = step.road.trim().ifEmpty { step.text.trim().replaceFirstChar { it.uppercaseChar() } }
}
