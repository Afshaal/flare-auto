package app.flare.auto

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.graphhopper.GHRequest
import com.graphhopper.GraphHopper
import com.graphhopper.config.CHProfile
import com.graphhopper.config.Profile
import java.io.File
import java.util.Locale
import java.util.concurrent.Executors

internal class OfflineRouting(c:Context) {
    private val app=c.applicationContext
    private val main=Handler(Looper.getMainLooper())
    private var hopper:GraphHopper?=null
    private var generation=0
    fun route(fromLat:Double,fromLon:Double,toLat:Double,toLon:Double,done:(Result<NavRoute>)->Unit) {
        val token=++generation
        worker.execute {
            val result=runCatching {
                check(NavigationPack.covers(fromLat,fromLon) && NavigationPack.covers(toLat,toLon)) { "Offline driving coverage is Lahore and its surrounding approaches" }
                var engine=hopper
                if(engine==null) {
                    val folder=NavigationPack.ensure(app)
                    engine=GraphHopper().forMobile().setProfiles(Profile("car").setVehicle("car").setWeighting("fastest").setTurnCosts(true))
                    engine.chPreparationHandler.setCHProfiles(CHProfile("car"));engine.setAllowWrites(false)
                    check(engine.load(File(folder,"gh").absolutePath)) { "Lahore routing data could not be loaded" };hopper=engine
                }
                val response=engine.route(GHRequest(fromLat,fromLon,toLat,toLon).setProfile("car").setLocale(Locale.ENGLISH))
                check(!response.hasErrors()) { response.errors.firstOrNull()?.message ?: "No driving route found" }
                val path=response.best;val geometry=path.points
                check(geometry.size()>=2) { "No driving route found" }
                check(NavGeometry.distance(fromLat,fromLon,geometry.getLat(0),geometry.getLon(0))<1000 && NavGeometry.distance(toLat,toLon,geometry.getLat(geometry.size()-1),geometry.getLon(geometry.size()-1))<1000) { "No nearby drivable road for the start or destination" }
                val points=DoubleArray(geometry.size()*2)
                for(i in 0 until geometry.size()) { points[i*2]=geometry.getLat(i);points[i*2+1]=geometry.getLon(i) }
                val distances=NavGeometry.cumulative(points);var index=0
                val steps=path.instructions.map { instruction->
                    NavStep(instruction.getTurnDescription(path.instructions.tr),instruction.sign,distances[index.coerceAtMost(distances.lastIndex)]).also { index+=instruction.length }
                }
                NavRoute(points,distances,steps,path.distance,path.time)
            }
            main.post { if(token==generation) done(result) }
        }
    }
    fun close() { generation++;worker.execute { hopper?.close();hopper=null } }
    companion object { val worker=Executors.newSingleThreadExecutor() }
}
