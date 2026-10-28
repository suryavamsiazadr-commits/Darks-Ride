package com.darksoul.darksride.media

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.view.KeyEvent
import com.darksoul.darksride.link.LinkState
import com.darksoul.darksride.phone.PhoneListenerService
import org.json.JSONObject
import java.io.ByteArrayOutputStream

/**
 * Main phone side of music control.
 *
 * Watches every app's media session, picks the one that is playing (or was last active),
 * sends what it plays to the ride screen, and carries out the buttons pressed there.
 * Needs notification access (see PhoneListenerService).
 */
class MediaRelay(
    private val context: Context,
    private val send: (JSONObject) -> Unit
) {
    private val handler = Handler(Looper.getMainLooper())
    private val sessions = context.getSystemService(MediaSessionManager::class.java)
    private val audio = context.getSystemService(AudioManager::class.java)

    @Volatile private var started = false
    private var controllers: List<MediaController> = emptyList()
    private var lastKey: String? = null
    private var lastArtKey: String? = null

    private val sessionsListener =
        MediaSessionManager.OnActiveSessionsChangedListener { list -> attach(list.orEmpty()) }

    private val callback = object : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackState?) = publish(force = false)
        override fun onMetadataChanged(metadata: MediaMetadata?) = publish(force = false)
        override fun onSessionDestroyed() = publish(force = false)
    }

    /** Safe to call often: starts watching once notification access has been granted. */
    fun ensureStarted() {
        if (started || !PhoneListenerService.hasAccess(context)) return
        handler.post {
            if (started) return@post
            try {
                val component = PhoneListenerService.component(context)
                sessions.addOnActiveSessionsChangedListener(sessionsListener, component, handler)
                started = true
                attach(sessions.getActiveSessions(component))
                LinkState.log("Music control ready")
            } catch (e: SecurityException) {
                // Access not active yet; the next call tries again.
            }
        }
    }

    fun stop() {
        handler.post {
            if (started) runCatching { sessions.removeOnActiveSessionsChangedListener(sessionsListener) }
            controllers.forEach { c -> runCatching { c.unregisterCallback(callback) } }
            controllers = emptyList()
            started = false
        }
    }

    /** Sends the current music state now, e.g. right after the ride screen connects. */
    fun refresh() {
        handler.post { publish(force = true) }
    }

    /** A button pressed on the ride screen. */
    fun command(action: String) {
        handler.post {
            val c = current()
            val controls = c?.transportControls
            when (action) {
                "toggle" -> when {
                    c?.playbackState?.state == PlaybackState.STATE_PLAYING -> controls?.pause()
                    controls != null -> controls.play()
                    else -> mediaKey(KeyEvent.KEYCODE_MEDIA_PLAY)
                }
                "next" -> if (controls != null) controls.skipToNext()
                    else mediaKey(KeyEvent.KEYCODE_MEDIA_NEXT)
                "prev" -> if (controls != null) controls.skipToPrevious()
                    else mediaKey(KeyEvent.KEYCODE_MEDIA_PREVIOUS)
                "vol_up" -> audio.adjustStreamVolume(
                    AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, 0
                )
                "vol_down" -> audio.adjustStreamVolume(
                    AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, 0
                )
            }
            // Volume has no change callback: report it right away. Track changes report themselves.
            if (action.startsWith("vol")) publish(force = false)
            else handler.postDelayed({ publish(force = false) }, 400)
        }
    }

    // ---------- Internals (always on the main thread) ----------

    private fun attach(list: List<MediaController>) {
        controllers.forEach { c -> runCatching { c.unregisterCallback(callback) } }
        controllers = list
        list.forEach { c -> c.registerCallback(callback, handler) }
        publish(force = false)
    }

    private fun current(): MediaController? =
        controllers.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
            ?: controllers.firstOrNull()

    private fun publish(force: Boolean) {
        val vol = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
        val volMax = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val c = current()

        if (c == null) {
            val key = "none|$vol"
            if (!force && key == lastKey) return
            lastKey = key
            lastArtKey = null
            send(
                JSONObject().put("t", "media").put("none", true)
                    .put("vol", vol).put("volMax", volMax)
            )
            return
        }

        val meta = c.metadata
        val title = meta?.getString(MediaMetadata.METADATA_KEY_TITLE)
            ?: meta?.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE)
            ?: ""
        val artist = meta?.getString(MediaMetadata.METADATA_KEY_ARTIST)
            ?: meta?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
            ?: meta?.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE)
            ?: ""
        val playing = c.playbackState?.state == PlaybackState.STATE_PLAYING
        val app = appName(c.packageName)
        val art = meta?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: meta?.getBitmap(MediaMetadata.METADATA_KEY_ART)
            ?: meta?.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)

        val artKey = "$app|$title|$artist|${art != null}"
        val key = "$artKey|$playing|$vol"
        if (!force && key == lastKey) return
        lastKey = key

        val msg = JSONObject().put("t", "media")
            .put("app", app).put("title", title).put("artist", artist)
            .put("playing", playing).put("vol", vol).put("volMax", volMax)

        // Album art is only sent when the track changes, to keep messages small.
        if (force || artKey != lastArtKey) {
            val trackChanged = lastArtKey?.substringBeforeLast('|') != artKey.substringBeforeLast('|')
            lastArtKey = artKey
            msg.put("artNew", true)
            art?.let { encodeArt(it) }?.let { msg.put("art", it) }
            if (trackChanged && title.isNotEmpty()) {
                LinkState.log("Music: $title" + if (artist.isNotEmpty()) " – $artist" else "")
            }
        }
        send(msg)
    }

    private fun mediaKey(code: Int) {
        audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code))
        audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code))
    }

    private fun appName(pkg: String): String = KNOWN_APPS[pkg] ?: try {
        val pm = context.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
    } catch (e: PackageManager.NameNotFoundException) {
        pkg
    }

    /** Small JPEG (max 192 px) as one line of Base64. */
    private fun encodeArt(source: Bitmap): String? = runCatching {
        val soft = if (source.config == Bitmap.Config.HARDWARE)
            source.copy(Bitmap.Config.ARGB_8888, false) else source
        val scale = ART_SIZE.toFloat() / maxOf(soft.width, soft.height)
        val small = if (scale < 1f) Bitmap.createScaledBitmap(
            soft,
            (soft.width * scale).toInt().coerceAtLeast(1),
            (soft.height * scale).toInt().coerceAtLeast(1),
            true
        ) else soft
        ByteArrayOutputStream().use { out ->
            small.compress(Bitmap.CompressFormat.JPEG, 75, out)
            Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
        }
    }.getOrNull()

    private companion object {
        const val ART_SIZE = 192
        val KNOWN_APPS = mapOf(
            "com.google.android.apps.youtube.music" to "YouTube Music",
            "com.google.android.youtube" to "YouTube",
            "com.spotify.music" to "Spotify"
        )
    }
}
