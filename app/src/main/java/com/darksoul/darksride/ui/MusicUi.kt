package com.darksoul.darksride.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.darksoul.darksride.R
import com.darksoul.darksride.link.LinkService
import com.darksoul.darksride.media.NowPlaying
import com.darksoul.darksride.media.RideMedia
import com.darksoul.darksride.phone.PhoneListenerService

// ---------- Ride screen: now playing + controls ----------

@Composable
fun NowPlayingCard() {
    val context = LocalContext.current
    val now by RideMedia.now.collectAsState()
    val send = { action: String -> LinkService.sendCommand(context, action) }

    Card {
        val n = now
        if (n == null) {
            Body("Waiting for music info from the main phone…", dim = true)
        } else {
            NowPlayingHeader(n)
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            ControlButton(R.drawable.ic_prev, "Previous", size = 72.dp) { send("prev") }
            ControlButton(
                if (n?.playing == true) R.drawable.ic_pause else R.drawable.ic_play,
                if (n?.playing == true) "Pause" else "Play",
                size = 92.dp,
                main = true
            ) { send("toggle") }
            ControlButton(R.drawable.ic_next, "Next", size = 72.dp) { send("next") }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            ControlButton(R.drawable.ic_volume_down, "Volume down", size = 60.dp) { send("vol_down") }
            Text(
                if (n == null) "Volume" else "Volume ${n.volume} / ${n.volumeMax}",
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)
            )
            ControlButton(R.drawable.ic_volume_up, "Volume up", size = 60.dp) { send("vol_up") }
        }
    }
}

@Composable
private fun NowPlayingHeader(n: NowPlaying) {
    val colors = MaterialTheme.colorScheme
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(96.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(colors.background),
            contentAlignment = Alignment.Center
        ) {
            val art = n.art
            if (art != null) {
                Image(
                    bitmap = art,
                    contentDescription = "Album art",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(96.dp)
                )
            } else {
                Icon(
                    painterResource(R.drawable.ic_music_note), contentDescription = null,
                    tint = colors.primary, modifier = Modifier.size(40.dp)
                )
            }
        }
        Column(
            modifier = Modifier.padding(start = 16.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            if (n.nothingPlaying) {
                Text("Nothing playing", fontSize = 20.sp, fontWeight = FontWeight.SemiBold,
                    color = colors.onSurface)
                Text("Press play to resume your last music app", fontSize = 14.sp,
                    color = colors.onSurface.copy(alpha = 0.65f))
            } else {
                Text(
                    n.title.ifEmpty { "Unknown track" },
                    fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = colors.onSurface,
                    maxLines = 2, overflow = TextOverflow.Ellipsis
                )
                if (n.artist.isNotEmpty()) {
                    Text(n.artist, fontSize = 16.sp, color = colors.onSurface.copy(alpha = 0.8f),
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text(
                    (if (n.playing) "Playing" else "Paused") + " · ${n.app}",
                    fontSize = 14.sp, color = colors.primary
                )
            }
        }
    }
}

@Composable
private fun ControlButton(
    @DrawableRes icon: Int,
    label: String,
    size: Dp,
    main: Boolean = false,
    onClick: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(if (main) colors.primary else Color.White.copy(alpha = 0.08f))
            .clickable(onClickLabel = label, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painterResource(icon), contentDescription = label,
            tint = if (main) colors.onPrimary else colors.onSurface,
            modifier = Modifier.size(size * 0.5f)
        )
    }
}

// ---------- Main phone: music access ----------

@Composable
fun MusicAccessCard() {
    val context = LocalContext.current
    var allowed by remember { mutableStateOf(PhoneListenerService.hasAccess(context)) }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { allowed = PhoneListenerService.hasAccess(context) }

    if (allowed) {
        Body("Music and messages access: on", dim = true)
        return
    }
    Card {
        Body("To control music and see messages on the bike, turn on notification access for Dark's Ride.")
        Body("If the switch is greyed out: open App info for Dark's Ride, tap ⋮ (top right), " +
            "choose \"Allow restricted settings\", then try again.", dim = true)
    }
    BigButton("Allow notification access", onClick = {
        try {
            launcher.launch(listenerDetailIntent(context))
        } catch (e: ActivityNotFoundException) {
            launcher.launch(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }
    })
}

private fun listenerDetailIntent(c: Context): Intent =
    Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS).putExtra(
        Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME,
        PhoneListenerService.component(c).flattenToString()
    )
