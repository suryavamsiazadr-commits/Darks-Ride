package com.darksoul.darksride.ui

import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.matchParentSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.darksoul.darksride.AppPrefs
import com.darksoul.darksride.R
import com.darksoul.darksride.link.LinkService
import com.darksoul.darksride.media.NowPlaying
import com.darksoul.darksride.media.RideMedia
import com.darksoul.darksride.ui.theme.LocalGlass

// ---------- Ride screen: now playing + controls ----------

/**
 * The music card: album art blurred into the background, big art and title, a glowing
 * play button in the theme colours, and a segmented volume bar. Tap the top row to shrink it
 * to a mini player (art, title, play).
 */
@Composable
fun NowPlayingCard() {
    val context = LocalContext.current
    val now by RideMedia.now.collectAsState()
    val send = { action: String -> LinkService.sendCommand(context, action) }
    var open by remember { mutableStateOf(AppPrefs.cardOpen(context, "music", true)) }
    val n = now
    val colors = MaterialTheme.colorScheme
    val glass = LocalGlass.current
    val shape = RoundedCornerShape(28.dp)
    val playing = n?.playing == true

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(if (glass.isDark) 0.dp else 12.dp, shape, ambientColor = glass.cardShadow, spotColor = glass.cardShadow)
            .clip(shape)
            .background(glass.card)
            .border(1.dp, Brush.verticalGradient(listOf(colors.primary.copy(alpha = 0.55f), glass.cardBorderBottom)), shape)
    ) {
        // Backdrop: the album art, blurred and dimmed; or a soft wash of the theme colours.
        val art = n?.art
        if (art != null) {
            Image(
                bitmap = art, contentDescription = null, contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize().blur(36.dp).alpha(if (glass.isDark) 0.55f else 0.35f)
            )
        }
        Box(
            Modifier.matchParentSize().background(
                Brush.verticalGradient(
                    listOf(colors.primary.copy(alpha = 0.18f), glass.accent2.copy(alpha = 0.06f),
                        glass.backgroundBottom.copy(alpha = if (art != null) 0.75f else 0.0f))
                )
            )
        )

        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            // Top row: label, live equalizer, open / close.
            Row(
                modifier = Modifier.fillMaxWidth().clickable {
                    open = !open
                    AppPrefs.setCardOpen(context, "music", open)
                },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "NOW PLAYING", fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.6.sp,
                    color = colors.primary
                )
                if (n != null && !n.nothingPlaying && n.app.isNotEmpty()) {
                    Text("  ·  ${n.app}", fontSize = 12.sp, color = colors.onSurface.copy(alpha = 0.6f),
                        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                }
                Box(Modifier.weight(1f))
                Equalizer(playing)
                Text(if (open) "  ▾" else "  ▸", fontSize = 22.sp, color = colors.primary)
            }

            if (!open) {
                MiniPlayer(n, playing) { send("toggle") }
                return@Column
            }

            TrackHeader(n)

            // Transport: prev · play / pause · next
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                RoundButton(R.drawable.ic_prev, "Previous", 72.dp) { send("prev") }
                PlayButton(playing, 100.dp) { send("toggle") }
                RoundButton(R.drawable.ic_next, "Next", 72.dp) { send("next") }
            }

            // Volume: − [segments] +
            Row(verticalAlignment = Alignment.CenterVertically) {
                RoundButton(R.drawable.ic_volume_down, "Volume down", 60.dp, repeat = true) {
                    RideMedia.nudgeVolume(-1) // show the new level at once
                    send("vol_down")
                }
                VolumeBar(n?.volume ?: 0, n?.volumeMax ?: 15, Modifier.weight(1f).padding(horizontal = 12.dp))
                RoundButton(R.drawable.ic_volume_up, "Volume up", 60.dp, repeat = true) {
                    RideMedia.nudgeVolume(+1)
                    send("vol_up")
                }
            }
        }
    }
}

@Composable
private fun TrackHeader(n: NowPlaying?) {
    val colors = MaterialTheme.colorScheme
    Row(verticalAlignment = Alignment.CenterVertically) {
        Art(n, 116.dp)
        Column(modifier = Modifier.padding(start = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            AnimatedContent(
                targetState = n?.title to n?.artist,
                transitionSpec = { fadeIn(tween(250)) togetherWith fadeOut(tween(150)) },
                label = "track"
            ) { (title, artist) ->
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    when {
                        n == null -> {
                            Text("Waiting for music", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = colors.onSurface)
                            Text("from your ${RoleNames.MAIN}…", fontSize = 15.sp, color = colors.onSurface.copy(alpha = 0.65f))
                        }
                        n.nothingPlaying -> {
                            Text("Nothing playing", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = colors.onSurface)
                            Text("Press play to resume your last music app", fontSize = 15.sp,
                                color = colors.onSurface.copy(alpha = 0.65f))
                        }
                        else -> {
                            Text(title.orEmpty().ifEmpty { "Unknown track" }, fontSize = 23.sp,
                                fontWeight = FontWeight.Bold, color = colors.onSurface,
                                maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 27.sp)
                            if (!artist.isNullOrEmpty()) {
                                Text(artist, fontSize = 16.sp, color = colors.onSurface.copy(alpha = 0.75f),
                                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MiniPlayer(n: NowPlaying?, playing: Boolean, onToggle: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(verticalAlignment = Alignment.CenterVertically) {
        Art(n, 56.dp)
        Column(modifier = Modifier.weight(1f).padding(horizontal = 14.dp)) {
            Text(
                when {
                    n == null -> "Waiting for music"
                    n.nothingPlaying -> "Nothing playing"
                    else -> n.title.ifEmpty { "Unknown track" }
                },
                fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = colors.onSurface,
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            if (n != null && n.artist.isNotEmpty() && !n.nothingPlaying) {
                Text(n.artist, fontSize = 14.sp, color = colors.onSurface.copy(alpha = 0.7f),
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        PlayButton(playing, 60.dp, onToggle)
    }
}

/** Album art with a glow in the theme colour, or a note icon on a gradient. */
@Composable
private fun Art(n: NowPlaying?, size: Dp) {
    val colors = MaterialTheme.colorScheme
    val glass = LocalGlass.current
    val shape = RoundedCornerShape(size / 5)
    Box(
        modifier = Modifier
            .size(size)
            .shadow(14.dp, shape, ambientColor = colors.primary, spotColor = colors.primary)
            .clip(shape)
            .background(Brush.linearGradient(listOf(colors.primary, glass.accent2))),
        contentAlignment = Alignment.Center
    ) {
        val art = n?.art
        if (art != null) {
            Image(bitmap = art, contentDescription = "Album art", contentScale = ContentScale.Crop,
                modifier = Modifier.size(size))
        } else {
            Icon(painterResource(R.drawable.ic_music_note), contentDescription = null,
                tint = colors.onPrimary, modifier = Modifier.size(size * 0.42f))
        }
    }
}

/** The big play / pause: a glowing gradient circle. */
@Composable
private fun PlayButton(playing: Boolean, size: Dp, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val glass = LocalGlass.current
    Box(
        modifier = Modifier
            .size(size)
            .shadow(18.dp, CircleShape, ambientColor = glass.glow, spotColor = glass.glow)
            .clip(CircleShape)
            .background(Brush.linearGradient(listOf(colors.primary, glass.accent2)))
            .rideTap(onTap = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painterResource(if (playing) R.drawable.ic_pause else R.drawable.ic_play),
            contentDescription = if (playing) "Pause" else "Play",
            tint = colors.onPrimary, modifier = Modifier.size(size * 0.46f)
        )
    }
}

/** Previous / next / volume: frosted circles. */
@Composable
private fun RoundButton(
    @DrawableRes icon: Int,
    label: String,
    size: Dp,
    repeat: Boolean = false,
    onClick: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(colors.onSurface.copy(alpha = 0.09f))
            .border(1.dp, colors.onSurface.copy(alpha = 0.12f), CircleShape)
            .rideTap(repeat = repeat, onTap = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(painterResource(icon), contentDescription = label, tint = colors.onSurface,
            modifier = Modifier.size(size * 0.46f))
    }
}

/** Volume as lit segments, easy to read at a glance. */
@Composable
private fun VolumeBar(volume: Int, max: Int, modifier: Modifier) {
    val colors = MaterialTheme.colorScheme
    val glass = LocalGlass.current
    val segments = max.coerceIn(1, 15)
    val lit = ((volume.toFloat() / max.coerceAtLeast(1)) * segments + 0.001f).toInt()
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Row(horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.Bottom) {
            repeat(segments) { i ->
                val on = i < lit
                Box(
                    Modifier
                        .weight(1f)
                        .height((10 + i * 18 / segments).dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(
                            if (on) Brush.verticalGradient(listOf(glass.accent2, colors.primary))
                            else Brush.verticalGradient(listOf(colors.onSurface.copy(alpha = 0.12f), colors.onSurface.copy(alpha = 0.12f)))
                        )
                )
            }
        }
        Text("Volume $volume / $max", fontSize = 13.sp, color = colors.onSurface.copy(alpha = 0.65f),
            modifier = Modifier.padding(top = 4.dp))
    }
}

/** Three little bars that dance while music plays. */
@Composable
private fun Equalizer(playing: Boolean) {
    val colors = MaterialTheme.colorScheme
    val t = rememberInfiniteTransition(label = "eq")
    val bars = listOf(420, 300, 520).map { ms ->
        t.animateFloat(0.25f, 1f, infiniteRepeatable(tween(ms), RepeatMode.Reverse), label = "bar$ms")
    }
    Row(horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.Bottom,
        modifier = Modifier.height(16.dp)) {
        bars.forEach { b ->
            Box(
                Modifier.width(4.dp)
                    .height((16 * if (playing) b.value else 0.25f).dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(colors.primary)
            )
        }
    }
}
