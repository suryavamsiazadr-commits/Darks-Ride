package com.darksoul.darksride.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.darksoul.darksride.AppPrefs
import com.darksoul.darksride.phone.RideNotif
import com.darksoul.darksride.phone.RideNotifications
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private fun clock(time: Long): String = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(time))

// ---------- Ride screen ----------

/**
 * Recent messages from the main phone. Tap one to dismiss it, or Clear for all:
 * both also clear the notification on the main phone.
 */
@Composable
fun MessagesCard(spotlight: Boolean = false) {
    val context = LocalContext.current
    val items by RideNotifications.items.collectAsState()
    // Nothing to show: a still card, so tapping it doesn't open and close an empty box.
    if (items.isEmpty()) {
        Card {
            Text("Messages", fontSize = 20.sp, fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface)
            Text("No new messages", fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f))
        }
        return
    }
    CollapsibleCard(
        title = "Messages",
        stateKey = "messages",
        summary = when (items.size) {
            0 -> "No new messages"
            1 -> "1 new: ${items.first().title}"
            else -> "${items.size} new · latest: ${items.first().title}"
        },
        highlight = spotlight,
        attention = items.isNotEmpty(),
        trailing = { _ ->
            if (items.isNotEmpty()) {
                TextButton(onClick = { RideNotifications.clearEverywhere(context) }) { Text("Clear") }
            }
        }
    ) {
        Body("Tap a message to dismiss it here and on your main phone.", dim = true)
        items.take(5).forEachIndexed { i, n ->
            if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
            MessageRow(n, onDismiss = { RideNotifications.dismissEverywhere(context, n.id) })
        }
    }
}

@Composable
private fun MessageRow(n: RideNotif, onDismiss: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onDismiss)
            .padding(vertical = 6.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(n.title, fontSize = 18.sp, fontWeight = FontWeight.SemiBold,
                color = colors.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f))
            Text("${n.app} · ${clock(n.time)}", fontSize = 13.sp, color = colors.primary)
        }
        Text(n.text, fontSize = 16.sp, color = colors.onSurface.copy(alpha = 0.85f),
            maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

// ---------- Main phone: which notifications go to the bike ----------

@Composable
fun NotificationFilterCard() {
    CollapsibleCard(
        title = "Notifications on the bike",
        stateKey = "notif_filter",
        defaultOpen = false,
        summary = "Choose which apps show on the bike"
    ) {
        FlagRow("WhatsApp", AppPrefs.NOTIFY_WHATSAPP)
        FlagRow("SMS / Messages", AppPrefs.NOTIFY_SMS)
        FlagRow("All other apps", AppPrefs.NOTIFY_OTHER)
        FlagRow("Show message text", AppPrefs.NOTIFY_SHOW_TEXT)
    }
}

@Composable
private fun FlagRow(label: String, key: String) {
    val context = LocalContext.current
    var on by remember { mutableStateOf(AppPrefs.flag(context, key)) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontSize = 17.sp, color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f))
        Switch(checked = on, onCheckedChange = {
            on = it
            AppPrefs.setFlag(context, key, it)
        })
    }
}

/** Settings (bike phone): how long a new message stays at the top of the ride screen. */
@Composable
fun SpotlightCard() {
    val context = LocalContext.current
    var seconds by remember { mutableStateOf(AppPrefs.spotlightSeconds(context).toFloat()) }
    CollapsibleCard(
        title = "New message spotlight",
        stateKey = "spotlight",
        defaultOpen = false,
        summary = "Stays on top for ${seconds.toInt()} s"
    ) {
        Body("A new message moves to the top of the ride screen, then drops back below the " +
            "music. Calls always stay on top until they end.", dim = true)
        Text("${seconds.toInt()} seconds", fontSize = 22.sp, fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary)
        Slider(
            value = seconds,
            onValueChange = { seconds = it },
            onValueChangeFinished = { AppPrefs.setSpotlightSeconds(context, seconds.toInt()) },
            valueRange = 5f..60f,
            steps = 10 // 5, 10, 15 … 60
        )
    }
}
