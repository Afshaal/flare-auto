package app.flare.auto
import android.content.ComponentName
import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.os.Handler
import android.os.Looper
internal class SaverMusic(private val context:Context,private val changed:(String,String,String)->Unit) {
    private val handler=Handler(Looper.getMainLooper())
    private val manager=context.getSystemService(Context.MEDIA_SESSION_SERVICE) as? MediaSessionManager
    private var controller:MediaController?=null
    private var last=""
    private val callback=object:MediaController.Callback() {
        override fun onMetadataChanged(m:MediaMetadata?) { read(m,false) }
        override fun onSessionDestroyed() { select(false) }
    }
    private val sessions=MediaSessionManager.OnActiveSessionsChangedListener { select(false) }
    fun start() {
        try { manager?.addOnActiveSessionsChangedListener(sessions,ComponentName(context,MediaAccess::class.java),handler);select(true) }
        catch(_:SecurityException) { }
    }
    private fun select(initial:Boolean) {
        val next=NowPlaying.active(context)
        if(controller?.sessionToken!=next?.sessionToken) {
            controller?.unregisterCallback(callback);controller=next;next?.registerCallback(callback,handler)
        }
        read(next?.metadata,initial)
    }
    private fun read(m:MediaMetadata?,initial:Boolean) {
        val title=NowPlaying.value(m,MediaMetadata.METADATA_KEY_TITLE)
        val artist=NowPlaying.value(m,MediaMetadata.METADATA_KEY_ARTIST)
        if(title.isBlank()) return
        val key=title+"\u0000"+artist
        if(!initial && key!=last) changed(controller?.packageName.orEmpty(),title,artist)
        last=key
    }
    fun stop() { manager?.removeOnActiveSessionsChangedListener(sessions);controller?.unregisterCallback(callback);controller=null;handler.removeCallbacksAndMessages(null) }
}
