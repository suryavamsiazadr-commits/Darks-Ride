package com.darksoul.darksride.media

import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import kotlin.math.abs

/**
 * Picks the two most vivid, distinct colours from album art (like a music app's background),
 * so the player can glow in the song's own colours instead of a stretched, blurry picture.
 */
object ArtColors {
    fun from(art: ImageBitmap): Pair<Int, Int>? = runCatching {
        val small = Bitmap.createScaledBitmap(art.asAndroidBitmap().copy(Bitmap.Config.ARGB_8888, false), 24, 24, true)
        data class Px(val color: Int, val hue: Float, val score: Float)
        val hsv = FloatArray(3)
        val px = buildList {
            for (y in 0 until small.height) for (x in 0 until small.width) {
                val c = small.getPixel(x, y)
                Color.colorToHSV(c, hsv)
                val s = hsv[1]
                val v = hsv[2]
                // Vivid and neither near-black nor washed-out white.
                val lightOk = if (v in 0.25f..0.97f) 1f else 0.25f
                add(Px(c, hsv[0], s * lightOk + v * 0.15f))
            }
        }.sortedByDescending { it.score }
        val first = px.first()
        // Second: the best colour with a clearly different hue, else a darker take on the first.
        val second = px.firstOrNull { hueGap(it.hue, first.hue) > 35f && it.score > first.score * 0.45f }?.color
            ?: shade(first.color, 0.55f)
        first.color to second
    }.getOrNull()

    private fun hueGap(a: Float, b: Float): Float = abs(a - b).let { minOf(it, 360f - it) }

    private fun shade(c: Int, f: Float) = Color.rgb((Color.red(c) * f).toInt(), (Color.green(c) * f).toInt(), (Color.blue(c) * f).toInt())
}
