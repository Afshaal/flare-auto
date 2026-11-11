package app.flare.auto

import android.content.Context
import android.os.Handler
import android.os.Looper
import org.mapsforge.map.reader.MapFile
import java.io.File
import java.io.InputStream
import java.lang.ref.WeakReference
import java.net.HttpURLConnection
import java.net.URL
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.concurrent.Executors

internal object OfflineMapStore {
    interface Listener { fun mapStatusChanged() }
    private const val MAX_BYTES=512L*1024*1024
    private const val RESERVE_BYTES=16L*1024*1024
    val worker=Executors.newSingleThreadExecutor()
    val reader=Executors.newSingleThreadExecutor()
    private val main=Handler(Looper.getMainLooper())
    private val listeners=ArrayList<WeakReference<Listener>>()
    @Volatile var busy=false;private set
    @Volatile private var cancelled=false
    @Volatile private var connection:HttpURLConnection?=null
    var status="";private set
    fun add(l:Listener) { listeners.add(WeakReference(l)) }
    fun remove(l:Listener) { listeners.removeAll { it.get()==null || it.get()===l } }
    private fun notify(value:String) { main.post { status=value;listeners.removeAll { it.get()==null };listeners.mapNotNull { it.get() }.forEach { it.mapStatusChanged() } } }
    fun region(c:Context)=File(c.filesDir,"offline_maps/region.map")
    fun ensure(c:Context):File {
        region(c).takeIf { it.isFile }?.let { return it }
        val world=File(c.filesDir,"offline_maps/world.map")
        if(!world.isFile) {
            world.parentFile?.mkdirs()
            val tmp=File(world.parentFile,"world.tmp")
            c.assets.open("maps/world.map").use { source->tmp.outputStream().use { source.copyTo(it) } }
            Files.move(tmp.toPath(),world.toPath(),StandardCopyOption.REPLACE_EXISTING)
        }
        return world
    }
    fun cancel() { cancelled=true;connection?.disconnect() }
    fun download(c:Context,url:String) {
        val app=c.applicationContext
        val parsed=try { URL(url) } catch(_:Exception) { notify("Enter a valid HTTPS .map URL");return }
        if(parsed.protocol!="https") { notify("Map downloads require an HTTPS URL");return }
        transfer(app) {
            val request=parsed.openConnection() as HttpURLConnection
            connection=request;request.connectTimeout=20000;request.readTimeout=30000;request.instanceFollowRedirects=false
            request.setRequestProperty("User-Agent","FlareAuto/1.1.0 offline-map-download")
            val code=request.responseCode
            if(code !in 200..299) { request.disconnect();error("Map server returned HTTP $code") }
            if(request.contentLengthLong>MAX_BYTES) { request.disconnect();error("Region exceeds 512 MB; choose a smaller region or subregion") }
            val free=region(app).parentFile?.usableSpace ?: 0L
            if(request.contentLengthLong>0 && request.contentLengthLong>free-RESERVE_BYTES) { request.disconnect();error("Not enough free storage for this region") }
            request.inputStream
        }
    }
    fun importMap(c:Context,uri:android.net.Uri) { val app=c.applicationContext;transfer(app) { app.contentResolver.openInputStream(uri) ?: error("Cannot open map file") } }
    private fun transfer(c:Context,open:()->InputStream) {
        if(busy) return
        val app=c.applicationContext;busy=true;cancelled=false;notify("Preparing map…")
        worker.execute {
            val target=region(app);target.parentFile?.mkdirs();val temp=File(target.parentFile,"region.part")
            try {
                if(cancelled) error("Download cancelled")
                check(target.parentFile!!.usableSpace>RESERVE_BYTES) { "Not enough free storage" }
                var bytes=0L;var last=0L;var checkAt=1048576L
                open().use { input->temp.outputStream().use { output->
                    val buffer=ByteArray(65536)
                    while(true) {
                        if(cancelled) error("Download cancelled")
                        val n=input.read(buffer);if(n<0) break
                        bytes+=n;check(bytes<=MAX_BYTES) { "Map exceeds the 512 MB limit; choose a smaller region" }
                        if(bytes>=checkAt) { check(target.parentFile!!.usableSpace>RESERVE_BYTES) { "Not enough free storage" };checkAt=bytes+1048576L }
                        output.write(buffer,0,n)
                        val now=android.os.SystemClock.elapsedRealtime()
                        if(now-last>500) { last=now;notify("Saving %.1f MB…".format(java.util.Locale.US,bytes/1048576.0)) }
                    }
                } }
                if(cancelled) error("Download cancelled")
                MapFile(temp).close()
                Files.move(temp.toPath(),target.toPath(),StandardCopyOption.REPLACE_EXISTING)
                app.getSharedPreferences("offline_map_data",0).edit().putLong("revision",System.currentTimeMillis()).apply()
                notify("Offline region ready · %.1f MB".format(java.util.Locale.US,bytes/1048576.0))
            } catch(e:Exception) { notify(if(cancelled) "Download cancelled · previous map kept" else "Map not saved: ${e.message ?: "invalid file"}") }
            finally { connection?.disconnect();connection=null;temp.delete();busy=false;main.post { listeners.mapNotNull { it.get() }.forEach { it.mapStatusChanged() } } }
        }
    }
    fun delete(c:Context) { if(!busy) { region(c).delete();c.getSharedPreferences("offline_map_data",0).edit().putLong("revision",System.currentTimeMillis()).apply();notify("Using bundled world overview") } }
}
