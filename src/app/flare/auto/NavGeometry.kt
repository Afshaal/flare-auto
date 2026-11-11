package app.flare.auto

import kotlin.math.*

internal data class NavStep(val text:String,val sign:Int,val at:Double)
internal data class NavRoute(val points:DoubleArray,val cumulative:DoubleArray,val steps:List<NavStep>,val distance:Double,val millis:Long)
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
}
