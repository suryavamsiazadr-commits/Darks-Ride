package com.darksoul.darksride.ui

import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.darksoul.darksride.R
import com.darksoul.darksride.link.LinkService
import com.darksoul.darksride.media.NowPlaying
import com.darksoul.darksride.media.RideMedia
import com.darksoul.darksride.ui.theme.LocalGlass
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.sin

private fun send(c: android.content.Context, action: String) = LinkService.sendCommand(c, action)

// ---------- Portrait card ----------

/**
 * The music card in the portrait list. Closed: a mini player with the running time.
 * Open: big art, title, a live wave progress line with times, controls and volume.
 */
@Composable
fun NowPlayingCard(onExpand: (() -> Unit)? = null) {
    val context = LocalContext.current
    val now by RideMedia.now.collectAsState()
    var open by remember { mutableStateOf(CardMemory.get("music")) }
    val n = now
    val colors = MaterialTheme.colorScheme
    val playing = n?.playing == true

    Box(modifier = Modifier.fillMaxWidth().rideSurface(28.dp, edge = colors.primary.copy(alpha = 0.45f))) {
        ArtBackdrop(n)
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            MusicTopRow(n, playing, open, onToggleOpen = { open = !open; CardMemory.set("music", open) }, onExpand = onExpand)
            if (!open) {
                MiniPlayer(n, playing) { send(context, "toggle") }
                WaveProgress(n, Modifier.fillMaxWidth(), showTimes = false)
                return@Column
            }
            TrackHeader(n, artSize = 116.dp)
            WaveProgress(n, Modifier.fillMaxWidth())
            Transport(playing, side = 72.dp, play = 100.dp)
            VolumeRow(n)
        }
    }
}

// ---------- Landscape / small-screen panel ----------

/**
 * Music for the landscape and small-screen layouts: fills the space it's given.
 * Big art and a volume bar when there's room, just art, title and controls when there isn't.
 */
@Composable
fun MusicPanel(modifier: Modifier = Modifier, onExpand: (() -> Unit)? = null) {
    val now by RideMedia.now.collectAsState()
    val n = now
    val colors = MaterialTheme.colorScheme
    val playing = n?.playing == true

    BoxWithConstraints(modifier = modifier.rideSurface(26.dp, edge = colors.primary.copy(alpha = 0.45f))) {
        val h = maxHeight
        val w = maxWidth
        val roomy = h >= 300.dp
        val tight = h < 210.dp
        val artSize = minOf(when { roomy -> 104.dp; tight -> 52.dp; else -> 72.dp }, w * 0.28f)
        val playSize = when { roomy -> 88.dp; tight -> 56.dp; else -> 72.dp }
        val sideSize = when { roomy -> 64.dp; tight -> 48.dp; else -> 56.dp }

        ArtBackdrop(n)
        Column(
            modifier = Modifier.fillMaxSize().padding(if (tight) 12.dp else 16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Art(n, artSize)
                Column(modifier = Modifier.weight(1f).padding(start = 14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("NOW PLAYING", fontSize = 11.sp, fontWeight = FontWeight.Bold,
                            letterSpacing = 1.4.sp, color = colors.primary, maxLines = 1, softWrap = false,
                            overflow = TextOverflow.Clip, modifier = Modifier.weight(1f))
                        Equalizer(playing)
                        if (onExpand != null) ExpandButton(onExpand)
                    }
                    TrackTitle(n, if (tight) 17.sp else 20.sp, maxLines = if (roomy) 2 else 1)
                    TrackSubtitle(n)
                }
            }
            if (!tight) WaveProgress(n, Modifier.fillMaxWidth(), showTimes = roomy)
            Transport(playing, side = sideSize, play = playSize)
            if (roomy) VolumeRow(n)
        }
    }
}

// ---------- Full screen ----------

/**
 * Music filling the whole screen (beside Maps, or the Wing's small screen): the album art
 * blurred behind everything, big art, a live progress wave with times, big controls.
 * Calls and messages pop up over it (see RideDashboard).
 */
@Composable
fun MusicFullScreen(onCollapse: () -> Unit, modifier: Modifier = Modifier) {
    val now by RideMedia.now.collectAsState()
    val n = now
    val colors = MaterialTheme.colorScheme
    val playing = n?.playing == true

    BoxWithConstraints(modifier = modifier.fillMaxSize().rideBackground()) {
        ArtBackdrop(n, strong = true)
        val wide = maxWidth > maxHeight * 1.05f
        val pad = 14.dp
        Column(Modifier.fillMaxSize().padding(pad), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            // Top: what's playing on which app, and the way back to the cards.
            Row(verticalAlignment = Alignment.CenterVertically) {
                Equalizer(playing)
                Text("  " + (n?.app?.ifEmpty { null } ?: "Music"), fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                    color = colors.primary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Box(
                    modifier = Modifier.rideButtonSurface(circle = false).clickable(onClick = onCollapse)
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) { Text("Cards ▾", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = colors.onSurface) }
            }
            if (wide) {
                // Square-ish or landscape: art on the left, everything else on the right.
                val art = minOf(maxHeight - 90.dp, maxWidth * 0.42f)
                Row(Modifier.fillMaxWidth().weight(1f), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Art(n, art, breathe = true)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        TrackTitle(n, 22.sp, maxLines = 2)
                        TrackSubtitle(n)
                        WaveProgress(n, Modifier.fillMaxWidth())
                        Transport(playing, side = 60.dp, play = 84.dp)
                    }
                }
            } else {
                // Tall: art on top, then title, progress and controls.
                val art = minOf(maxWidth - pad * 2 - 40.dp, maxHeight * 0.36f)
                Column(Modifier.fillMaxWidth().weight(1f), horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceEvenly) {
                    Art(n, art, breathe = true)
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        TrackTitle(n, 22.sp, maxLines = 1, center = true)
                        TrackSubtitle(n, center = true)
                    }
                    WaveProgress(n, Modifier.fillMaxWidth())
                    Transport(playing, side = 60.dp, play = 84.dp)
                }
            }
            VolumeRow(n, button = 48.dp)
        }
    }
}

// ---------- Pieces ----------

/** The album art, blurred big behind the card (glass style). */
@Composable
private fun BoxScope.ArtBackdrop(n: NowPlaying?, strong: Boolean = false) {
    val glass = LocalGlass.current
    val colors = MaterialTheme.colorScheme
    val art = n?.art
    if (art != null && (!glass.soft || strong)) {
        Image(
            bitmap = art, contentDescription = null, contentScale = ContentScale.Crop,
            modifier = Modifier.matchParentSize().blur(40.dp)
                .alpha(if (glass.isDark) (if (strong) 0.7f else 0.55f) else (if (strong) 0.45f else 0.32f))
        )
    }
    Box(
        Modifier.matchParentSize().background(
            Brush.verticalGradient(
                listOf(
                    colors.primary.copy(alpha = if (glass.soft) 0.06f else 0.16f),
                    glass.backgroundBottom.copy(alpha = if (art != null) (if (strong) 0.55f else 0.72f) else 0f)
                )
            )
        )
    )
}

@Composable
private fun MusicTopRow(n: NowPlaying?, playing: Boolean, open: Boolean, onToggleOpen: () -> Unit, onExpand: (() -> Unit)?) {
    val colors = MaterialTheme.colorScheme
    Row(modifier = Modifier.fillMaxWidth().clickable(onClick = onToggleOpen), verticalAlignment = Alignment.CenterVertically) {
        Text("NOW PLAYING", fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.6.sp, color = colors.primary)
        if (n != null && !n.nothingPlaying && n.app.isNotEmpty()) {
            Text("  ·  ${n.app}", fontSize = 12.sp, color = colors.onSurface.copy(alpha = 0.6f),
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
        }
        Box(Modifier.weight(1f))
        Equalizer(playing)
        if (onExpand != null) ExpandButton(onExpand)
        Text(if (open) "  ▾" else "  ▸", fontSize = 22.sp, color = colors.primary)
    }
}

/** ⤢ : music full screen. */
@Composable
private fun ExpandButton(onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier = Modifier.padding(start = 10.dp).size(34.dp).rideButtonSurface(circle = true).clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) { Text("⤢", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = colors.primary) }
}

@Composable
private fun TrackTitle(n: NowPlaying?, size: androidx.compose.ui.unit.TextUnit, maxLines: Int, center: Boolean = false) {
    val colors = MaterialTheme.colorScheme
    AnimatedContent(targetState = n?.title to n?.nothingPlaying, label = "title",
        transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(150)) }) { (title, nothing) ->
        Text(
            when {
                n == null -> "Waiting for music"
                nothing == true -> "Nothing playing"
                else -> title.orEmpty().ifEmpty { "Unknown track" }
            },
            fontSize = size, fontWeight = FontWeight.Bold, color = colors.onSurface,
            maxLines = maxLines, overflow = TextOverflow.Ellipsis, lineHeight = size * 1.18f,
            textAlign = if (center) TextAlign.Center else TextAlign.Start,
            modifier = if (center) Modifier.fillMaxWidth() else Modifier
        )
    }
}

@Composable
private fun TrackSubtitle(n: NowPlaying?, center: Boolean = false) {
    val sub = when {
        n == null -> "from your ${RoleNames.MAIN}"
        n.nothingPlaying -> "Press play to resume your last music app"
        else -> n.artist
    }
    if (sub.isEmpty()) return
    Text(sub, fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
        maxLines = 1, overflow = TextOverflow.Ellipsis,
        textAlign = if (center) TextAlign.Center else TextAlign.Start,
        modifier = if (center) Modifier.fillMaxWidth() else Modifier)
}

@Composable
private fun TrackHeader(n: NowPlaying?, artSize: Dp) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Art(n, artSize, breathe = true)
        Column(modifier = Modifier.padding(start = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            TrackTitle(n, 23.sp, maxLines = 2)
            TrackSubtitle(n)
        }
    }
}

@Composable
private fun MiniPlayer(n: NowPlaying?, playing: Boolean, onToggle: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Art(n, 56.dp)
        Column(modifier = Modifier.weight(1f).padding(horizontal = 14.dp)) {
            TrackTitle(n, 18.sp, maxLines = 1)
            TrackSubtitle(n)
        }
        PlayButton(playing, 60.dp, onToggle)
    }
}

@Composable
private fun Transport(playing: Boolean, side: Dp, play: Dp) {
    val context = LocalContext.current
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically) {
        RoundButton(R.drawable.ic_prev, "Previous", side) { send(context, "prev") }
        PlayButton(playing, play) { send(context, "toggle") }
        RoundButton(R.drawable.ic_next, "Next", side) { send(context, "next") }
    }
}

@Composable
private fun VolumeRow(n: NowPlaying?, button: Dp = 56.dp) {
    val context = LocalContext.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        RoundButton(R.drawable.ic_volume_down, "Volume down", button, repeat = true) {
            RideMedia.nudgeVolume(-1) // show the new level at once
            send(context, "vol_down")
        }
        VolumeBar(n?.volume ?: 0, n?.volumeMax ?: 15, Modifier.weight(1f).padding(horizontal = 12.dp))
        RoundButton(R.drawable.ic_volume_up, "Volume up", button, repeat = true) {
            RideMedia.nudgeVolume(+1)
            send(context, "vol_up")
        }
    }
}

/**
 * Song progress: the played part is a moving wave while it plays (flat when paused), the rest
 * a thin line, with the running time and the length underneath.
 */
@Composable
internal fun WaveProgress(n: NowPlaying?, modifier: Modifier, showTimes: Boolean = true) {
    val colors = MaterialTheme.colorScheme
    val glass = LocalGlass.current
    val playing = n?.playing == true
    var pos by remember { mutableLongStateOf(n?.positionNow() ?: 0L) }
    LaunchedEffect(n) {
        while (true) {
            pos = n?.positionNow() ?: 0L
            delay(500)
        }
    }
    val dur = n?.durationMs ?: 0L
    val fraction = if (dur > 0) (pos.toFloat() / dur).coerceIn(0f, 1f) else 0f
    val t = rememberInfiniteTransition(label = "wave")
    val phase by t.animateFloat(0f, (2 * PI).toFloat(), infiniteRepeatable(tween(1400, easing = LinearEasing)), label = "phase")
    val accent = colors.primary
    val accent2 = glass.accent2
    val track = colors.onSurface.copy(alpha = 0.18f)

    Column(modifier) {
        Canvas(Modifier.fillMaxWidth().height(18.dp)) {
            val mid = size.height / 2
            val playedX = size.width * fraction
            // Unplayed: thin straight line.
            drawLine(track, Offset(playedX, mid), Offset(size.width, mid), strokeWidth = 4.dp.toPx(), cap = StrokeCap.Round)
            // Played: a wave (moving while it plays).
            if (playedX > 1f) {
                val amp = if (playing) 4.dp.toPx() else 0f
                val wavelength = 26.dp.toPx()
                val path = Path().apply {
                    moveTo(0f, mid)
                    var x = 0f
                    while (x <= playedX) {
                        lineTo(x, mid + amp * sin((x / wavelength) * 2 * PI.toFloat() - phase))
                        x += 2f
                    }
                }
                drawPath(path, Brush.horizontalGradient(listOf(accent2, accent), endX = playedX.coerceAtLeast(1f)),
                    style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round))
            }
            // The thumb.
            drawCircle(accent, radius = 7.dp.toPx(), center = Offset(playedX.coerceAtLeast(7.dp.toPx()), mid))
        }
        if (showTimes) {
            Row(Modifier.fillMaxWidth().padding(top = 2.dp)) {
                Text(if (dur > 0) clock(pos) else "--:--", fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                    color = colors.onSurface.copy(alpha = 0.8f), modifier = Modifier.weight(1f))
                Text(if (dur > 0) clock(dur) else "--:--", fontSize = 13.sp,
                    color = colors.onSurface.copy(alpha = 0.6f))
            }
        }
    }
}

private fun clock(ms: Long): String {
    val s = ms / 1000
    return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, (s / 60) % 60, s % 60) else "%d:%02d".format(s / 60, s % 60)
}

/**
 * Album art with a soft glow in the theme colour. With [breathe] it gently swells in time
 * with the music while playing.
 */
@Composable
internal fun Art(n: NowPlaying?, size: Dp, breathe: Boolean = false) {
    val colors = MaterialTheme.colorScheme
    val glass = LocalGlass.current
    val shape = RoundedCornerShape(size / 5)
    val playing = n?.playing == true
    val t = rememberInfiniteTransition(label = "art")
    val pulse by t.animateFloat(1f, 1.035f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "pulse")
    val scale = if (breathe && playing) pulse else 1f
    Box(
        modifier = Modifier
            .size(size)
            .scale(scale)
            .then(
                if (glass.soft) Modifier.rideButtonSurface(circle = false, corner = size / 5)
                else Modifier.shadow(if (playing) 22.dp else 12.dp, shape, ambientColor = colors.primary, spotColor = colors.primary)
            )
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
internal fun PlayButton(playing: Boolean, size: Dp, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val glass = LocalGlass.current
    Box(
        modifier = Modifier
            .size(size)
            .then(if (glass.soft) Modifier else Modifier.shadow(18.dp, CircleShape, ambientColor = glass.glow, spotColor = glass.glow))
            .rideButtonSurface(circle = true, fill = Brush.linearGradient(listOf(colors.primary, glass.accent2)))
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

/** Previous / next / volume: frosted (glass) or raised (soft) circles. */
@Composable
internal fun RoundButton(
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
            .rideButtonSurface(circle = true)
            .rideTap(repeat = repeat, onTap = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(painterResource(icon), contentDescription = label, tint = colors.onSurface,
            modifier = Modifier.size(size * 0.46f))
    }
}

/** Volume as lit segments, easy to read at a glance. */
@Composable
internal fun VolumeBar(volume: Int, max: Int, modifier: Modifier) {
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
        Text("$volume / $max", fontSize = 13.sp, color = colors.onSurface.copy(alpha = 0.65f),
            maxLines = 1, softWrap = false, modifier = Modifier.padding(top = 4.dp))
    }
}

/** Three little bars that dance while music plays. */
@Composable
internal fun Equalizer(playing: Boolean) {
    val colors = MaterialTheme.colorScheme
    val t = rememberInfiniteTransition(label = "eq")
    val bars = listOf(420, 300, 520, 360).map { ms ->
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
