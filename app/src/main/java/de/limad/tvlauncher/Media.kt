package de.limad.tvlauncher

import android.content.ComponentName
import android.content.Context
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.SystemClock
import android.service.notification.NotificationListenerService
import android.view.KeyEvent

/**
 * Only exists so the launcher may read the active media sessions (title/artist).
 * Must be allowed once via ADB: cmd notification allow_listener de.limad.tvlauncher/de.limad.tvlauncher.MediaListener
 */
class MediaListener : NotificationListenerService()

data class NowPlaying(val app: String, val title: String, val artist: String, val playing: Boolean)

object Media {

    /** Currently active media session, or null (also null if the listener is not allowed). */
    fun nowPlaying(ctx: Context): NowPlaying? = runCatching {
        val msm = ctx.getSystemService(MediaSessionManager::class.java) ?: return null
        val sessions = msm.getActiveSessions(ComponentName(ctx, MediaListener::class.java))
        val c = sessions.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING } ?: sessions.firstOrNull() ?: return null
        val md = c.metadata
        val title = md?.getString(MediaMetadata.METADATA_KEY_TITLE) ?: md?.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE) ?: return null
        val artist = md?.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: md?.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE) ?: ""
        val label = runCatching {
            ctx.packageManager.getApplicationLabel(ctx.packageManager.getApplicationInfo(c.packageName, 0)).toString()
        }.getOrDefault(c.packageName)
        NowPlaying(label, title, artist, c.playbackState?.state == PlaybackState.STATE_PLAYING)
    }.getOrNull()

    /** Media key to whatever is playing; works without any permission. */
    fun key(ctx: Context, code: Int) {
        val am = ctx.getSystemService(AudioManager::class.java) ?: return
        val t = SystemClock.uptimeMillis()
        am.dispatchMediaKeyEvent(KeyEvent(t, t, KeyEvent.ACTION_DOWN, code, 0))
        am.dispatchMediaKeyEvent(KeyEvent(t, t, KeyEvent.ACTION_UP, code, 0))
    }
}
