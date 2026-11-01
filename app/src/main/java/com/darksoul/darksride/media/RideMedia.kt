package com.darksoul.darksride.media

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject

/** What the main phone is playing, as shown on the ride screen. */
data class NowPlaying(
    val nothingPlaying: Boolean,
    val app: String,
    val title: String,
    val artist: String,
    val playing: Boolean,
    val art: ImageBitmap?,
    val volume: Int,
    val volumeMax: Int,
    /** Song length and where it was when the main phone reported it (0 if the app doesn't say). */
    val durationMs: Long = 0L,
    val positionMs: Long = 0L,
    /** When that report arrived here (elapsedRealtime), to keep the clock running locally. */
    val reportedAt: Long = 0L,
    val speed: Float = 1f
) {
    /** Where the song is right now. */
    fun positionNow(): Long {
        if (durationMs <= 0L) return 0L
        val moved = if (playing) ((android.os.SystemClock.elapsedRealtime() - reportedAt) * speed).toLong() else 0L
        return (positionMs + moved).coerceIn(0L, durationMs)
    }
}

/** Ride screen side of music control: holds the latest state sent by the main phone. */
object RideMedia {
    private val _now = MutableStateFlow<NowPlaying?>(null)
    val now: StateFlow<NowPlaying?> = _now.asStateFlow()

    // After a tap on the bike, the main phone's next reports can still carry the old value
    // (e.g. volume 6, 7, 8 while you're already at 10). Keep what you set for a moment.
    private const val HOLD_MS = 1_500L
    private var holdVolumeUntil = 0L
    private var holdPlayingUntil = 0L
    private var lastMsg: JSONObject? = null
    private val main = android.os.Handler(android.os.Looper.getMainLooper())
    private val applyLast = Runnable { lastMsg?.let { apply(it) } }

    fun update(msg: JSONObject) {
        lastMsg = msg
        apply(msg)
    }

    private fun apply(msg: JSONObject) {
        val nowMs = android.os.SystemClock.elapsedRealtime()
        val prev = _now.value
        val nothing = msg.optBoolean("none")
        val art = when {
            nothing -> null
            msg.optBoolean("artNew") -> decodeArt(msg.optString("art"))
            else -> _now.value?.art
        }
        _now.value = NowPlaying(
            nothingPlaying = nothing,
            app = msg.optString("app"),
            title = msg.optString("title"),
            artist = msg.optString("artist"),
            playing = if (prev != null && nowMs < holdPlayingUntil) prev.playing else msg.optBoolean("playing"),
            art = art,
            volume = if (prev != null && nowMs < holdVolumeUntil) prev.volume else msg.optInt("vol"),
            volumeMax = msg.optInt("volMax", 15).coerceAtLeast(1),
            durationMs = msg.optLong("dur"),
            positionMs = msg.optLong("pos"),
            reportedAt = android.os.SystemClock.elapsedRealtime(),
            speed = msg.optDouble("speed", 1.0).toFloat().takeIf { it > 0f } ?: 1f
        )
    }

    /** Volume buttons: show the new level straight away; the main phone confirms it shortly. */
    fun nudgeVolume(delta: Int) {
        val n = _now.value ?: return
        _now.value = n.copy(volume = (n.volume + delta).coerceIn(0, n.volumeMax))
        hold { holdVolumeUntil = it }
    }

    /** Play / pause tapped: flip at once (and stop or restart the song clock). */
    fun togglePlaying() {
        val n = _now.value ?: return
        val t = android.os.SystemClock.elapsedRealtime()
        _now.value = n.copy(playing = !n.playing, positionMs = n.positionNow(), reportedAt = t)
        hold { holdPlayingUntil = it }
    }

    /** Hold the local value, then settle on whatever the main phone said last. */
    private fun hold(set: (Long) -> Unit) {
        set(android.os.SystemClock.elapsedRealtime() + HOLD_MS)
        main.removeCallbacks(applyLast)
        main.postDelayed(applyLast, HOLD_MS + 50)
    }

    fun clear() {
        _now.value = null
    }

    private fun decodeArt(base64: String): ImageBitmap? {
        if (base64.isEmpty()) return null
        return runCatching {
            val bytes = Base64.decode(base64, Base64.NO_WRAP)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
        }.getOrNull()
    }
}
