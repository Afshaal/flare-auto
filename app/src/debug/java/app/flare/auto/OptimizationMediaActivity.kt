package app.flare.auto

import android.app.Activity
import android.os.Bundle
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState

class OptimizationMediaActivity : Activity() {
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        if (session == null) {
            session = MediaSession(applicationContext, "Optimization media test").apply {
                setCallback(object : MediaSession.Callback() {
                    override fun onPlay() { playback(PlaybackState.STATE_PLAYING) }
                    override fun onPause() { playback(PlaybackState.STATE_PAUSED) }
                })
                isActive = true
            }
        }
        if (intent.getBooleanExtra("stop", false)) {
            session?.release(); session = null
        } else {
            val image = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.rgb(90, 40, 45)) }
            session?.setMetadata(MediaMetadata.Builder().putString(MediaMetadata.METADATA_KEY_TITLE, "Optimization test track")
                .putString(MediaMetadata.METADATA_KEY_ARTIST, "Local media fixture")
                .putLong(MediaMetadata.METADATA_KEY_DURATION, 180000)
                .putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, image).build())
            playback(PlaybackState.STATE_PLAYING)
        }
        startActivity(Intent(this, HomeActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP))
        finish()
    }
    companion object {
        private var session: MediaSession? = null
        private fun playback(state: Int) {
            session?.setPlaybackState(PlaybackState.Builder().setActions(PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or PlaybackState.ACTION_PLAY_PAUSE or PlaybackState.ACTION_SEEK_TO)
                .setState(state, 1000, if (state == PlaybackState.STATE_PLAYING) 1f else 0f).build())
        }
    }
}
