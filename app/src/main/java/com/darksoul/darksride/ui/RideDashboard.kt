package com.darksoul.darksride.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.darksoul.darksride.AppMode
import com.darksoul.darksride.AppPrefs
import com.darksoul.darksride.R
import com.darksoul.darksride.link.LinkState
import com.darksoul.darksride.link.LinkStatus
import com.darksoul.darksride.nav.RideNav
import com.darksoul.darksride.net.BikeNet
import com.darksoul.darksride.phone.RideCall
import com.darksoul.darksride.phone.RideNotif
import com.darksoul.darksride.phone.RideNotifications
import com.darksoul.darksride.ui.theme.Go
import com.darksoul.darksride.ui.theme.LocalGlass
import kotlinx.coroutines.delay

/** How the ride screen arranges itself for the space it has. */
enum class RideLayout { PORTRAIT, WIDE, COMPACT }

fun rideLayoutFor(width: Dp, height: Dp): RideLayout = when {
    width > height && width >= 600.dp -> RideLayout.WIDE          // landscape, full screen
    height < 560.dp || width < 360.dp -> RideLayout.COMPACT       // small screen, or beside Maps
    else -> RideLayout.PORTRAIT
}

/**
 * The bike phone while linked. Built so nothing needs scrolling while riding:
 *  - portrait: music right under the title, other cards below it,
 *  - landscape: music on the left, call / message / route on the right (like Android Auto),
 *  - compact (the Wing's small screen, or split screen beside Google Maps): only what fits.
 * A call always takes the top spot, then a new message for its spotlight time, then music.
 */
@Composable
fun RideDashboard(
    title: String,
    onSettings: (() -> Unit)? = null,
    onClose: (() -> Unit)? = null,
    insets: WindowInsets = WindowInsets.safeDrawing,
    portraitExtras: @Composable ColumnScope.() -> Unit = {}
) {
    val context = LocalContext.current
    val popup by RideNotifications.popup.collectAsState()
    // A new message stays in the spotlight for the time set in Settings.
    val fresh = popup
    LaunchedEffect(fresh?.id, fresh?.text) {
        if (fresh == null) return@LaunchedEffect
        delay(AppPrefs.spotlightSeconds(context) * 1_000L)
        RideNotifications.dismissPopup(fresh.id)
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize().rideBackground().windowInsetsPadding(insets)) {
        when (rideLayoutFor(maxWidth, maxHeight)) {
            RideLayout.PORTRAIT -> PortraitRide(title, onSettings, onClose, insets, portraitExtras)
            RideLayout.WIDE -> WideRide(title, onSettings, onClose)
            RideLayout.COMPACT -> CompactRide(title, onSettings, onClose, maxWidth, maxHeight)
        }
    }
}

@Composable
private fun PortraitRide(
    title: String,
    onSettings: (() -> Unit)?,
    onClose: (() -> Unit)?,
    insets: WindowInsets,
    extras: @Composable ColumnScope.() -> Unit
) {
    val scroll = rememberScrollState()
    val call by RideCall.call.collectAsState()
    val popup by RideNotifications.popup.collectAsState()
    // Whatever just arrived is at the top: jump back up so it's in view without scrolling.
    LaunchedEffect(call != null, popup?.id) { scroll.animateScrollTo(0) }
    RideColumn(insets = WindowInsets(0), scroll = scroll, background = false) {
        RideTopBar(title, onSettings, onClose, big = true)
        RideContent()
        extras()
    }
}

@Composable
private fun WideRide(title: String, onSettings: (() -> Unit)?, onClose: (() -> Unit)?) {
    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
        RideTopBar(title, onSettings, onClose, big = false)
        Row(modifier = Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MusicPanel(modifier = Modifier.weight(1.1f).fillMaxHeight())
            Column(modifier = Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SidePanels(Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun CompactRide(title: String, onSettings: (() -> Unit)?, onClose: (() -> Unit)?, width: Dp, height: Dp) {
    val call by RideCall.call.collectAsState()
    val c = call
    Column(modifier = Modifier.fillMaxSize().padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        RideTopBar(title, onSettings, onClose, big = false)
        when {
            c != null -> CallPanel(c, Modifier.fillMaxWidth().weight(1f))
            width >= 440.dp && width >= height * 0.95f -> Row(
                modifier = Modifier.fillMaxWidth().weight(1f),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MusicPanel(Modifier.weight(1.15f).fillMaxHeight())
                Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SidePanels(Modifier.fillMaxWidth(), compact = true)
                }
            }
            else -> {
                TopStrip()
                MusicPanel(Modifier.fillMaxWidth().weight(1f))
            }
        }
    }
}

/** Right-hand side in landscape: call, else new message, then the route and messages. */
@Composable
private fun ColumnScope.SidePanels(modifier: Modifier, compact: Boolean = false) {
    val call by RideCall.call.collectAsState()
    val popup by RideNotifications.popup.collectAsState()
    val items by RideNotifications.items.collectAsState()
    val c = call
    val fresh = popup
    when {
        c != null -> CallPanel(c, modifier.weight(1f))
        fresh != null -> {
            MessagePanel(fresh, spotlight = true, modifier = modifier.weight(1f))
            if (!compact) RoutePanel(modifier)
        }
        else -> {
            RoutePanel(modifier.weight(1f))
            val latest = items.firstOrNull()
            if (latest != null) MessagePanel(latest, spotlight = false, more = items.size - 1, modifier = modifier)
            else if (!compact) QuietTile("No new messages", modifier)
        }
    }
}

/** Narrow layouts: one slim line above the music for a new message or the route. */
@Composable
private fun TopStrip() {
    val popup by RideNotifications.popup.collectAsState()
    val fresh = popup
    if (fresh != null) MessagePanel(fresh, spotlight = true, modifier = Modifier.fillMaxWidth())
    else RoutePanel(Modifier.fillMaxWidth(), slim = true)
}

// ---------- Pieces ----------

/** Title, link status, internet dot and the gear (or Close on the small screen). */
@Composable
private fun RideTopBar(title: String, onSettings: (() -> Unit)?, onClose: (() -> Unit)?, big: Boolean) {
    val status by LinkState.status.collectAsState()
    val peer by LinkState.peerName.collectAsState()
    val bt by BikeNet.btInternet.collectAsState()
    val wifi by BikeNet.wifi.collectAsState()
    val colors = MaterialTheme.colorScheme
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, fontSize = if (big) 26.sp else 18.sp, fontWeight = FontWeight.Bold, color = colors.primary,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        StatusPill(status, peer, AppMode.RIDE)
        // Internet: green when online through the main phone or Wi-Fi.
        val online = bt || wifi
        Row(
            modifier = Modifier.padding(start = 8.dp).clip(RoundedCornerShape(50))
                .background(LocalGlass.current.card)
                .border(1.dp, (if (online) Go else Color.Gray).copy(alpha = 0.6f), RoundedCornerShape(50))
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(if (online) Go else Color.Gray))
            Text(if (bt) " Net" else if (wifi) " Wi-Fi" else " No net", fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold, color = colors.onSurface, maxLines = 1)
        }
        if (onSettings != null) {
            IconButton(onClick = onSettings) {
                Icon(painterResource(R.drawable.ic_settings), contentDescription = "Settings",
                    tint = colors.onSurface.copy(alpha = 0.8f))
            }
        }
        if (onClose != null) TextButton(onClick = onClose) { Text("Close") }
    }
}

/** A message, Android Auto style: who, what, and one big Dismiss. */
@Composable
private fun MessagePanel(n: RideNotif, spotlight: Boolean, modifier: Modifier, more: Int = 0) {
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme
    val glass = LocalGlass.current
    val shape = RoundedCornerShape(24.dp)
    Column(
        modifier = modifier
            .clip(shape)
            .background(if (spotlight) colors.primary.copy(alpha = 0.14f) else glass.card)
            .border(if (spotlight) 2.dp else 1.dp,
                if (spotlight) colors.primary else glass.cardBorderTop, shape)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Avatar(n.title, colors.primary, pulse = false, size = 44.dp)
            Column(modifier = Modifier.weight(1f).padding(start = 8.dp)) {
                Text(n.title, fontSize = 19.sp, fontWeight = FontWeight.Bold, color = colors.onSurface,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    (if (spotlight) "New message · " else "") + n.app + if (more > 0) " · +$more more" else "",
                    fontSize = 13.sp, color = colors.primary, maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }
            TextButton(onClick = { RideNotifications.dismissEverywhere(context, n.id) }) { Text("Dismiss") }
        }
        if (n.text.isNotBlank()) {
            Text(n.text, fontSize = 16.sp, color = colors.onSurface.copy(alpha = 0.9f),
                maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** The shared route with a big Navigate button; or how to share one. */
@Composable
private fun RoutePanel(modifier: Modifier, slim: Boolean = false) {
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme
    val glass = LocalGlass.current
    val last by RideNav.last.collectAsState()
    val d = last
    val shape = RoundedCornerShape(24.dp)
    Column(
        modifier = modifier
            .clip(shape)
            .background(Brush.verticalGradient(listOf(Go.copy(alpha = 0.16f), glass.card)))
            .border(1.dp, glass.cardBorderTop, shape)
            .padding(if (slim) 12.dp else 14.dp),
        verticalArrangement = if (slim) Arrangement.Top else Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(if (d != null) "GOING TO" else "NAVIGATION", fontSize = 11.sp, fontWeight = FontWeight.Bold,
                    letterSpacing = 1.4.sp, color = Go)
                Text(d?.name ?: "No route yet", fontSize = if (slim) 18.sp else 21.sp, fontWeight = FontWeight.Bold,
                    color = colors.onSurface, maxLines = if (slim) 1 else 2, overflow = TextOverflow.Ellipsis)
                val sub = when {
                    d == null -> "Share a place from Google Maps on your ${RoleNames.MAIN}"
                    d.via != null -> "via ${d.via}"
                    else -> d.address
                }
                if (sub != null) Text(sub, fontSize = 13.sp, color = colors.onSurface.copy(alpha = 0.7f),
                    maxLines = if (slim) 1 else 2, overflow = TextOverflow.Ellipsis)
            }
            if (slim) {
                PillButton(if (d != null) "Go" else "Maps", Go) {
                    if (d != null) RideNav.navigate(context, d) else RideNav.openMaps(context)
                }
            }
        }
        if (!slim) {
            PillButton(if (d != null) "Navigate" else "Open Maps", Go, Modifier.fillMaxWidth().padding(top = 8.dp)) {
                if (d != null) RideNav.navigate(context, d) else RideNav.openMaps(context)
            }
        }
    }
}

@Composable
private fun QuietTile(text: String, modifier: Modifier) {
    val glass = LocalGlass.current
    val shape = RoundedCornerShape(24.dp)
    Box(
        modifier = modifier.clip(shape).background(glass.card).border(1.dp, glass.cardBorderTop, shape).padding(16.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Text(text, fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
    }
}

@Composable
private fun PillButton(label: String, color: Color, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .height(56.dp)
            .clip(RoundedCornerShape(50))
            .background(Brush.verticalGradient(listOf(color, color.copy(red = color.red * 0.8f, green = color.green * 0.8f, blue = color.blue * 0.8f))))
            .rideTap(onTap = onClick)
            .padding(horizontal = 22.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.Black)
    }
}

/** True while the ride screen should be the dashboard (linked, on the bike phone). */
@Composable
fun rideDashboardActive(): Boolean {
    val status by LinkState.status.collectAsState()
    return status == LinkStatus.CONNECTED
}
