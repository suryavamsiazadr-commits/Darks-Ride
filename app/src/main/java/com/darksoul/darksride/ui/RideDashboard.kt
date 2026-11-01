package com.darksoul.darksride.ui

import androidx.compose.foundation.background
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
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
import com.darksoul.darksride.media.RideMedia
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

/** The cards on the bike phone's ride screen. The rider sets their order in Settings. */
enum class RideCard(val label: String) {
    MUSIC("Music"),
    NAVIGATION("Navigation"),
    MESSAGES("Messages"),
    INTERNET("Bike internet"),
    RIDE_MODE("Ride mode");

    companion object {
        val DEFAULT = listOf(MUSIC, NAVIGATION, MESSAGES, INTERNET, RIDE_MODE)
    }
}

/** What actually shows, top to bottom (CALL isn't in the rider's list: it only exists during a call). */
private sealed interface Slot {
    data object Call : Slot
    data class Card(val card: RideCard, val live: Boolean) : Slot
}

/**
 * The rider's order, with whatever is live lifted to the top for as long as it's live:
 * a call (until it ends), then a new message (for its spotlight time), then music while it plays.
 * When that ends the card drops back to its own place.
 */
@Composable
private fun rideSlots(): List<Slot> {
    val context = LocalContext.current
    val call by RideCall.call.collectAsState()
    val popup by RideNotifications.popup.collectAsState()
    val now by RideMedia.now.collectAsState()
    val order by AppPrefs.cardOrder(context).collectAsState()
    val live = buildList {
        if (popup != null) add(RideCard.MESSAGES)
        if (now?.playing == true) add(RideCard.MUSIC)
    }
    return buildList {
        if (call != null) add(Slot.Call)
        live.forEach { add(Slot.Card(it, live = true)) }
        order.filterNot { it in live }.forEach { add(Slot.Card(it, live = false)) }
    }
}

/**
 * The bike phone while linked. Every card is there in every layout, in the rider's order:
 *  - portrait: one column,
 *  - landscape: the top card big on the left, the rest listed on the right,
 *  - compact (the Wing's small screen, or split screen beside Maps): one column, each card the
 *    full width so the buttons stay big.
 * Whatever is live (call, new message, playing music) is first, and the list jumps back to the
 * top when something new arrives, so there's nothing to scroll for while riding.
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
    val slots = rideSlots()
    val now by RideMedia.now.collectAsState()
    val playing = now?.playing == true
    // Full-screen music: on by itself when music plays beside Maps / on the small screen
    // (Settings), or any time with ⤢. "Cards ▾" goes back until the next time music starts.
    var focus by remember { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(playing) { if (playing) focus = null }
    val autoFull = remember { AppPrefs.flag(context, AppPrefs.MUSIC_FULL) }

    BoxWithConstraints(modifier = Modifier.fillMaxSize().rideBackground().windowInsetsPadding(insets)) {
        val layout = rideLayoutFor(maxWidth, maxHeight)
        val h = maxHeight
        val full = focus ?: (autoFull && playing && layout == RideLayout.COMPACT)
        if (full) {
            MusicFullScreen(onCollapse = { focus = false })
            FullScreenPopups(h)
            return@BoxWithConstraints
        }
        CompositionLocalProvider(LocalExpandMusic provides { focus = true }) {
            when (layout) {
                RideLayout.PORTRAIT -> ListRide(title, onSettings, onClose, slots, compact = false, h, portraitExtras)
                RideLayout.COMPACT -> ListRide(title, onSettings, onClose, slots, compact = true, h, portraitExtras)
                RideLayout.WIDE -> WideRide(title, onSettings, onClose, slots, portraitExtras)
            }
        }
    }
}

/** ⤢ on the music card: switch to full-screen music. */
private val LocalExpandMusic = staticCompositionLocalOf<(() -> Unit)?> { null }

/**
 * Over full-screen music: a call (until it ends) or a new message (for its spotlight time)
 * slides in at the top. The music stays underneath.
 */
@Composable
private fun BoxScope.FullScreenPopups(height: Dp) {
    val call by RideCall.call.collectAsState()
    val popup by RideNotifications.popup.collectAsState()
    val c = call
    val fresh = popup
    val showing = c != null || fresh != null
    if (showing) {
        Box(Modifier.matchParentSize().background(Color.Black.copy(alpha = 0.45f)))
    }
    AnimatedVisibility(
        visible = showing,
        enter = slideInVertically { -it } + fadeIn(),
        exit = slideOutVertically { -it } + fadeOut(),
        modifier = Modifier.align(Alignment.TopCenter).padding(10.dp)
    ) {
        // A solid backing so the music underneath doesn't show through the pop-up.
        val glass = LocalGlass.current
        Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp)).background(glass.backgroundTop)) {
            when {
                c != null -> CallPanel(c, Modifier.fillMaxWidth().height((height * 0.62f).coerceIn(200.dp, 300.dp)))
                fresh != null -> MessagePanel(fresh, spotlight = true, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

/** One column (portrait and compact). Jumps to the top when something becomes live. */
@Composable
private fun ListRide(
    title: String,
    onSettings: (() -> Unit)?,
    onClose: (() -> Unit)?,
    slots: List<Slot>,
    compact: Boolean,
    height: Dp,
    extras: @Composable ColumnScope.() -> Unit
) {
    val scroll = rememberScrollState()
    val first = slots.firstOrNull()
    LaunchedEffect(first) { scroll.animateScrollTo(0) }
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(scroll)
            .padding(horizontal = if (compact) 10.dp else 20.dp, vertical = if (compact) 10.dp else 16.dp),
        verticalArrangement = Arrangement.spacedBy(if (compact) 10.dp else 14.dp)
    ) {
        RideTopBar(title, onSettings, onClose, big = !compact)
        slots.forEach { slot ->
            key(slotKey(slot)) {
                if (compact) CompactSlot(slot, height) else PortraitSlot(slot)
            }
        }
        extras()
    }
}

/** Landscape: the top card fills the left half; every other card is listed on the right. */
@Composable
private fun WideRide(
    title: String,
    onSettings: (() -> Unit)?,
    onClose: (() -> Unit)?,
    slots: List<Slot>,
    extras: @Composable ColumnScope.() -> Unit
) {
    val scroll = rememberScrollState()
    val lead = slots.firstOrNull()
    LaunchedEffect(lead) { scroll.animateScrollTo(0) }
    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
        RideTopBar(title, onSettings, onClose, big = false)
        Row(modifier = Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.weight(1.1f).fillMaxHeight()) {
                if (lead != null) LeadSlot(lead, Modifier.fillMaxSize())
            }
            Column(
                modifier = Modifier.weight(1f).fillMaxHeight().verticalScroll(scroll),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                slots.drop(1).forEach { slot -> key(slotKey(slot)) { CompactSlot(slot, 300.dp) } }
                extras()
            }
        }
    }
}

private fun slotKey(slot: Slot): String = when (slot) {
    Slot.Call -> "call"
    is Slot.Card -> slot.card.name
}

/** Portrait: the full cards. */
@Composable
private fun PortraitSlot(slot: Slot) {
    val call by RideCall.call.collectAsState()
    val popup by RideNotifications.popup.collectAsState()
    when (slot) {
        Slot.Call -> call?.let { CallCard(it) }
        is Slot.Card -> when (slot.card) {
            RideCard.MUSIC -> NowPlayingCard(onExpand = LocalExpandMusic.current)
            RideCard.NAVIGATION -> DestinationCard()
            RideCard.MESSAGES -> MessagesCard(spotlight = popup != null)
            RideCard.INTERNET -> InternetCard(AppMode.RIDE)
            RideCard.RIDE_MODE -> RideModeCard(AppMode.RIDE)
        }
    }
}

/** Compact and the landscape list: full-width cards sized for a short screen. */
@Composable
private fun CompactSlot(slot: Slot, height: Dp) {
    val call by RideCall.call.collectAsState()
    val popup by RideNotifications.popup.collectAsState()
    val items by RideNotifications.items.collectAsState()
    val panelHeight = (height * 0.62f).coerceIn(190.dp, 300.dp)
    when (slot) {
        Slot.Call -> call?.let { CallPanel(it, Modifier.fillMaxWidth().height(panelHeight)) }
        is Slot.Card -> when (slot.card) {
            RideCard.MUSIC -> MusicPanel(Modifier.fillMaxWidth().height(panelHeight), onExpand = LocalExpandMusic.current)
            RideCard.NAVIGATION -> RoutePanel(Modifier.fillMaxWidth())
            RideCard.MESSAGES -> {
                val fresh = popup
                val latest = items.firstOrNull()
                when {
                    fresh != null -> MessagePanel(fresh, spotlight = true, modifier = Modifier.fillMaxWidth())
                    latest != null -> MessagePanel(latest, spotlight = false, more = items.size - 1, modifier = Modifier.fillMaxWidth())
                    else -> QuietTile("No new messages", Modifier.fillMaxWidth())
                }
            }
            RideCard.INTERNET -> InternetCard(AppMode.RIDE)
            RideCard.RIDE_MODE -> RideModeCard(AppMode.RIDE)
        }
    }
}

/** Landscape left half: the top card, as big as the space. */
@Composable
private fun LeadSlot(slot: Slot, modifier: Modifier) {
    val call by RideCall.call.collectAsState()
    val popup by RideNotifications.popup.collectAsState()
    val items by RideNotifications.items.collectAsState()
    when (slot) {
        Slot.Call -> call?.let { CallPanel(it, modifier) }
        is Slot.Card -> when (slot.card) {
            RideCard.MUSIC -> MusicPanel(modifier, onExpand = LocalExpandMusic.current)
            RideCard.NAVIGATION -> RoutePanel(modifier)
            RideCard.MESSAGES -> {
                val n = popup ?: items.firstOrNull()
                if (n != null) MessagePanel(n, spotlight = popup != null, more = if (popup == null) items.size - 1 else 0, modifier = modifier)
                else QuietTile("No new messages", modifier)
            }
            RideCard.INTERNET, RideCard.RIDE_MODE -> Column(modifier.verticalScroll(rememberScrollState())) {
                if (slot.card == RideCard.INTERNET) InternetCard(AppMode.RIDE) else RideModeCard(AppMode.RIDE)
            }
        }
    }
}

// ---------- Pieces ----------

/** Title, one status chip (link + internet) and the gear (or Close on the small screen). */
@Composable
private fun RideTopBar(title: String, onSettings: (() -> Unit)?, onClose: (() -> Unit)?, big: Boolean) {
    val status by LinkState.status.collectAsState()
    val bt by BikeNet.btInternet.collectAsState()
    val wifi by BikeNet.wifi.collectAsState()
    val colors = MaterialTheme.colorScheme
    val linked = status == LinkStatus.CONNECTED
    val online = bt || wifi
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val showTitle = maxWidth >= 340.dp
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            if (showTitle) {
                Text(title, fontSize = if (big) 24.sp else 18.sp, fontWeight = FontWeight.Bold, color = colors.primary,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            } else {
                Box(Modifier.weight(1f))
            }
            Row(
                modifier = Modifier.rideButtonSurface(circle = false)
                    .padding(horizontal = 12.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Dot(if (linked) Go else colors.primary)
                Text(if (linked) " Linked" else " Waiting", fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                    color = colors.onSurface, maxLines = 1)
                Text("   ", fontSize = 13.sp)
                Dot(if (online) Go else Color.Gray)
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
}

@Composable
private fun Dot(color: Color) = Box(Modifier.size(8.dp).clip(CircleShape).background(color))

/** Green that stays readable on white in daylight. */
@Composable
private fun routeGreen(): Color = if (LocalGlass.current.isDark) Go else Color(0xFF138A4A)

/** A message, Android Auto style: who, what, and one big Dismiss. */
@Composable
private fun MessagePanel(n: RideNotif, spotlight: Boolean, modifier: Modifier, more: Int = 0) {
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme
    val glass = LocalGlass.current
    Column(
        modifier = modifier
            .rideSurface(24.dp, tint = if (spotlight) colors.primary else null, edge = if (spotlight) colors.primary else null)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Avatar(n.title, colors.primary, pulse = false, size = 44.dp)
            Column(modifier = Modifier.weight(1f).padding(start = 6.dp)) {
                Text(n.title, fontSize = 19.sp, fontWeight = FontWeight.Bold, color = colors.onSurface,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    (if (spotlight) "New message · " else "") + n.app + if (more > 0) " · +$more more" else "",
                    fontSize = 13.sp, color = colors.primary, maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (n.text.isNotBlank()) {
            Text(n.text, fontSize = 16.sp, color = colors.onSurface.copy(alpha = 0.9f),
                maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Box(
            modifier = Modifier.fillMaxWidth().height(48.dp).rideButtonSurface(circle = false)
                .rideTap(onTap = { RideNotifications.dismissEverywhere(context, n.id) }),
            contentAlignment = Alignment.Center
        ) {
            Text("Dismiss", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = colors.onSurface)
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
    val green = routeGreen()
    Column(
        modifier = modifier
            .rideSurface(24.dp, tint = green)
            .padding(if (slim) 12.dp else 14.dp),
        verticalArrangement = if (slim) Arrangement.Top else Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(if (d != null) "GOING TO" else "NAVIGATION", fontSize = 11.sp, fontWeight = FontWeight.Bold,
                    letterSpacing = 1.4.sp, color = green)
                Text(d?.name ?: "No route yet", fontSize = if (slim) 18.sp else 21.sp, fontWeight = FontWeight.Bold,
                    color = colors.onSurface, maxLines = if (slim) 1 else 2, overflow = TextOverflow.Ellipsis)
                val sub = when {
                    d == null -> "Share a place from Google Maps on your ${RoleNames.MAIN}"
                    d.via != null -> "via ${d.via}"
                    else -> d.address
                }
                if (sub != null) Text(sub, fontSize = 13.sp, color = colors.onSurface.copy(alpha = 0.7f),
                    maxLines = if (slim) 1 else 2, overflow = TextOverflow.Ellipsis)
                if (!slim && d != null) {
                    val details = listOfNotNull(
                        d.address?.takeIf { d.via != null },
                        d.from?.let { "From $it" },
                        "Sent " + java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date(d.sentAt))
                    )
                    details.forEach {
                        Text(it, fontSize = 13.sp, color = colors.onSurface.copy(alpha = 0.6f),
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
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
        modifier = modifier.rideSurface(24.dp).padding(16.dp),
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
            .rideButtonSurface(circle = false, fill = Brush.verticalGradient(listOf(color, color.copy(red = color.red * 0.8f, green = color.green * 0.8f, blue = color.blue * 0.8f))))
            .rideTap(onTap = onClick)
            .padding(horizontal = 22.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.Black)
    }
}
