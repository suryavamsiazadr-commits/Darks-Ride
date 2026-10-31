package com.darksoul.darksride.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import com.darksoul.darksride.ui.theme.LocalGlass
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.darksoul.darksride.AppPrefs
import com.darksoul.darksride.link.LinkService
import com.darksoul.darksride.phone.RideNotifications
import com.darksoul.darksride.overlay.RideOverlay
import com.darksoul.darksride.phone.CallInfo
import com.darksoul.darksride.phone.CallRelay
import com.darksoul.darksride.phone.RideCall
import com.darksoul.darksride.ui.theme.Go
import com.darksoul.darksride.ui.theme.Stop
import kotlinx.coroutines.delay

/**
 * The ride screen's cards, in order of what matters right now:
 *  1. a call, from the first ring until it ends,
 *  2. a new message, for the spotlight time set in Settings (5–60 s, default 10),
 *  3. music (always first otherwise), then navigation, then older messages.
 */
@Composable
fun RideContent() {
    val context = LocalContext.current
    val call by RideCall.call.collectAsState()
    val popup by RideNotifications.popup.collectAsState()
    val fresh = popup
    LaunchedEffect(fresh?.id, fresh?.text) {
        if (fresh == null) return@LaunchedEffect
        delay(AppPrefs.spotlightSeconds(context) * 1_000L)
        RideNotifications.dismissPopup(fresh.id)
    }
    val spotlight = fresh != null

    AnimatedVisibility(visible = call != null, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
        call?.let { CallCard(it) }
    }
    if (spotlight) MessagesCard(spotlight = true)
    NowPlayingCard()
    DestinationCard()
    if (!spotlight) MessagesCard()
}

@Composable
private fun CallCard(call: CallInfo) {
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme
    val glass = LocalGlass.current
    val shape = RoundedCornerShape(28.dp)
    val send = { action: String -> LinkService.sendCommand(context, action) }
    val tint = if (call.ringing) Go else colors.primary

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(16.dp, shape, ambientColor = tint.copy(alpha = 0.5f), spotColor = tint.copy(alpha = 0.5f))
            .clip(shape)
            .background(Brush.verticalGradient(listOf(tint.copy(alpha = 0.22f), glass.backgroundTop.copy(alpha = 0.9f))))
            .border(1.dp, tint.copy(alpha = 0.6f), shape)
            .padding(22.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Avatar(call.caller, tint, pulse = call.ringing)
        Text(
            ((if (call.ringing) "INCOMING" else "ON CALL") + " · ${call.app}").uppercase(),
            fontSize = 13.sp, color = tint, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp,
            modifier = Modifier.clip(RoundedCornerShape(50)).background(tint.copy(alpha = 0.15f))
                .padding(horizontal = 12.dp, vertical = 4.dp)
        )
        Text(
            call.caller, fontSize = 34.sp, fontWeight = FontWeight.Bold,
            color = colors.onSurface, textAlign = TextAlign.Center,
            maxLines = 2, overflow = TextOverflow.Ellipsis
        )

        if (call.ringing) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                CallButton("✕  Decline", Stop, Modifier.weight(1f)) { send("reject") }
                CallButton("✓  Answer", Go, Modifier.weight(1f)) { send("answer") }
            }
        } else {
            CallTimer(call.since)
            CallButton("End call", Stop, Modifier.fillMaxWidth().padding(top = 12.dp), height = 88.dp) { send("end") }
        }
    }
}

/** First letter of the caller in a gradient circle; a soft ring pulses while it rings. */
@Composable
private fun Avatar(name: String, tint: Color, pulse: Boolean) {
    val ring = rememberInfiniteTransition(label = "ring")
    val scale by ring.animateFloat(
        initialValue = 1f, targetValue = if (pulse) 1.35f else 1f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "scale"
    )
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(104.dp)) {
        if (pulse) {
            Box(
                Modifier.size(80.dp).scale(scale).clip(CircleShape)
                    .background(tint.copy(alpha = (1.35f - scale).coerceIn(0f, 0.4f)))
            )
        }
        Box(
            modifier = Modifier.size(80.dp).clip(CircleShape)
                .background(Brush.linearGradient(listOf(tint, tint.copy(alpha = 0.6f)))),
            contentAlignment = Alignment.Center
        ) {
            Text(
                name.trim().firstOrNull { it.isLetterOrDigit() }?.uppercase() ?: "•",
                fontSize = 34.sp, fontWeight = FontWeight.Bold, color = Color.White
            )
        }
    }
}

@Composable
private fun CallTimer(since: Long) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(since) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1_000)
        }
    }
    val seconds = ((now - since) / 1000).coerceAtLeast(0)
    Text(
        "%d:%02d".format(seconds / 60, seconds % 60),
        fontSize = 22.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
    )
}

@Composable
private fun CallButton(
    label: String,
    color: Color,
    modifier: Modifier,
    height: Dp = 100.dp,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .height(height)
            .shadow(8.dp, RoundedCornerShape(50), ambientColor = color, spotColor = color)
            .clip(RoundedCornerShape(50))
            .background(Brush.verticalGradient(listOf(color, color.copy(red = color.red * 0.8f, green = color.green * 0.8f, blue = color.blue * 0.8f))))
            .rideTap(onTap = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(label, fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Color.White)
    }
}
