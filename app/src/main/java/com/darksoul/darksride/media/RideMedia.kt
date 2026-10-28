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
    val volumeMax: Int
)

/** Ride screen side of music control: holds the latest state sent by the main phone. */
object RideMedia {
    private val _now = MutableStateFlow<NowPlaying?>(null)
    val now: StateFlow<NowPlaying?> = _now.asStateFlow()

    fun update(msg: JSONObject) {
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
            playing = msg.optBoolean("playing"),
            art = art,
            volume = msg.optInt("vol"),
            volumeMax = msg.optInt("volMax", 15).coerceAtLeast(1)
        )
    }

    /** Volume buttons: show the new level straight away; the main phone confirms it shortly. */
    fun nudgeVolume(delta: Int) {
        val n = _now.value ?: return
        _now.value = n.copy(volume = (n.volume + delta).coerceIn(0, n.volumeMax))
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
