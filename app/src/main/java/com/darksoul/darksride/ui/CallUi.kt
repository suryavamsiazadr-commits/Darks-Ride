package com.darksoul.darksride.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import com.darksoul.darksride.link.LinkService
import com.darksoul.darksride.overlay.RideOverlay
import com.darksoul.darksride.phone.CallInfo
import com.darksoul.darksride.phone.CallRelay
import com.darksoul.darksride.phone.RideCall
import com.darksoul.darksride.ui.theme.Go
import com.darksoul.darksride.ui.theme.Stop
import kotlinx.coroutines.delay

/** Everything below the status on the ride screen: a call takes over while it lasts. */
@Composable
fun RideContent() {
    val call by RideCall.call.collectAsState()
    val c = call
    if (c != null) {
        CallCard(c)
    } else {
        MessagePopup()
        NowPlayingCard()
        MessagesCard()
    }
}

@Composable
private fun CallCard(call: CallInfo) {
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(24.dp)
    val send = { action: String -> LinkService.sendCommand(context, action) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.surface)
            .border(2.dp, if (call.ringing) Go else colors.primary, shape)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            (if (call.ringing) "Incoming call" else "On call") + " · ${call.app}",
            fontSize = 18.sp, color = colors.primary, fontWeight = FontWeight.SemiBold
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
                    .padding(top = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                CallButton("Reject", Stop, Modifier.weight(1f)) { send("reject") }
                CallButton("Answer", Go, Modifier.weight(1f)) { send("answer") }
            }
        } else {
            CallTimer(call.since)
            CallButton("End call", Stop, Modifier.fillMaxWidth().padding(top = 16.dp)) { send("end") }
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
    height: Dp = 110.dp,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .height(height)
            .clip(RoundedCornerShape(24.dp))
            .background(color)
            .clickable(onClickLabel = label, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(label, fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Color.White)
    }
}

// ---------- Main phone: call access ----------

@Composable
fun CallAccessCard() {
    val context = LocalContext.current
    var allowed by remember { mutableStateOf(CallRelay.hasCallAccess(context)) }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { allowed = CallRelay.hasCallAccess(context) }

    if (allowed) {
        Body("Call control: on", dim = true)
        return
    }
    Card {
        Body("To see who is calling and answer or reject calls from the bike, " +
            "allow Dark's Ride to manage phone calls.")
    }
    BigButton("Allow call access", onClick = { launcher.launch(CallRelay.PERMISSIONS) })
}

// ---------- Ride screen: allow floating cards over Maps ----------

@Composable
fun OverlayAccessCard() {
    val context = LocalContext.current
    var allowed by remember { mutableStateOf(RideOverlay.canShow(context)) }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { allowed = RideOverlay.canShow(context) }

    if (allowed) {
        Body("Calls and messages float on top of Maps: on", dim = true)
        return
    }
    Card {
        Body("Show calls and messages on top of Google Maps while you ride. " +
            "Turn on \"Display over other apps\" for Dark's Ride.")
    }
    BigButton("Allow display over other apps", onClick = {
        launcher.launch(RideOverlay.permissionIntent(context))
    })
}

// ---------- Main phone: let WhatsApp calls be answered from the bike ----------

@Composable
fun AppCallAnswerCard() {
    val context = LocalContext.current
    var allowed by remember { mutableStateOf(RideOverlay.canShow(context)) }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { allowed = RideOverlay.canShow(context) }

    if (allowed) {
        Body("Answer WhatsApp calls from the bike: on", dim = true)
        return
    }
    Card {
        Body("To answer WhatsApp calls from the bike, Android needs Dark's Ride to be " +
            "allowed to \"Display over other apps\" on this phone. Nothing is shown here; " +
            "it only lets WhatsApp's call screen open.")
    }
    BigButton("Allow for WhatsApp calls", secondary = true, onClick = {
        launcher.launch(RideOverlay.permissionIntent(context))
    })
}
