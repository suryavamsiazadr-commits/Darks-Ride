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

@Composable
internal fun CallCard(call: CallInfo) {
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme
    val glass = LocalGlass.current
    val shape = RoundedCornerShape(28.dp)
    val send = { action: String -> LinkService.sendCommand(context, action) }
    val tint = if (call.ringing) Go else colors.primary

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .rideSurface(28.dp, tint = tint, edge = tint.copy(alpha = 0.7f))
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
internal fun Avatar(name: String, tint: Color, pulse: Boolean, size: Dp = 80.dp) {
    val ring = rememberInfiniteTransition(label = "ring")
    val scale by ring.animateFloat(
        initialValue = 1f, targetValue = if (pulse) 1.35f else 1f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "scale"
    )
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(size * 1.3f)) {
        if (pulse) {
            Box(
                Modifier.size(size).scale(scale).clip(CircleShape)
                    .background(tint.copy(alpha = (1.35f - scale).coerceIn(0f, 0.4f)))
            )
        }
        Box(
            modifier = Modifier.size(size).clip(CircleShape)
                .background(Brush.linearGradient(listOf(tint, tint.copy(alpha = 0.6f)))),
            contentAlignment = Alignment.Center
        ) {
            Text(
                name.trim().firstOrNull { it.isLetterOrDigit() }?.uppercase() ?: "•",
                fontSize = (size.value * 0.42f).sp, fontWeight = FontWeight.Bold, color = Color.White
            )
        }
    }
}

/**
 * The call for the landscape and small-screen layouts: fills its space, buttons as big as fit.
 */
@Composable
fun CallPanel(call: CallInfo, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme
    val glass = LocalGlass.current
    val shape = RoundedCornerShape(26.dp)
    val send = { action: String -> LinkService.sendCommand(context, action) }
    val tint = if (call.ringing) Go else colors.primary
    Column(
        modifier = modifier
            .rideSurface(26.dp, tint = tint, edge = tint.copy(alpha = 0.7f))
            .padding(16.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Avatar(call.caller, tint, pulse = call.ringing, size = 56.dp)
            Column(modifier = Modifier.weight(1f).padding(start = 10.dp)) {
                Text(((if (call.ringing) "INCOMING" else "ON CALL") + " · ${call.app}").uppercase(),
                    fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp, color = tint)
                Text(call.caller, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = colors.onSurface,
                    maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 28.sp)
                if (!call.ringing) CallTimer(call.since)
            }
        }
        if (call.ringing) {
            Row(modifier = Modifier.fillMaxWidth().weight(1f, fill = false).padding(top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CallButton("✕  Decline", Stop, Modifier.weight(1f), height = 84.dp) { send("reject") }
                CallButton("✓  Answer", Go, Modifier.weight(1f), height = 84.dp) { send("answer") }
            }
        } else {
            CallButton("End call", Stop, Modifier.fillMaxWidth().padding(top = 12.dp), height = 76.dp) { send("end") }
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
