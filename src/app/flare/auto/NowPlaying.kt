// SPDX-License-Identifier: GPL-3.0-only
// Media-session selection and metadata helpers adapted from Dashline MediaMonitor.
// Copyright 2026 Metehan Kaygisiz. Modified for Flare Auto, 2026-11-10.
// https://github.com/metehankaygsz/dashline

package app.flare.auto

import android.content.ComponentName
import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState

internal object NowPlaying {
    @JvmStatic
    fun active(context: Context): MediaController? {
        return try {
            val manager = context.getSystemService(Context.MEDIA_SESSION_SERVICE) as? MediaSessionManager
                ?: return null
            val sessions = manager.getActiveSessions(ComponentName(context, MediaAccess::class.java))
            sessions.firstOrNull {
                it.playbackState?.state == PlaybackState.STATE_PLAYING
            } ?: sessions.firstOrNull()
        } catch (_: SecurityException) {
            null
        }
    }

    @JvmStatic
    fun value(metadata: MediaMetadata?, key: String): String =
        metadata?.getText(key)?.toString() ?: ""
}
