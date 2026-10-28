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

/** Big pop-up for a new message; disappears after 8 seconds or when tapped. */
@Composable
fun MessagePopup() {
    val popup by RideNotifications.popup.collectAsState()
    val p = popup ?: return

    LaunchedEffect(p.id, p.text) {
        delay(8_000)
        RideNotifications.dismissPopup(p.id)
    }

    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.primary.copy(alpha = 0.16f))
            .border(2.dp, colors.primary, shape)
            .clickable { RideNotifications.dismissPopup(p.id) }
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text("${p.app} · ${clock(p.time)}", fontSize = 15.sp, color = colors.primary,
            fontWeight = FontWeight.SemiBold)
        Text(p.title, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = colors.onSurface,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(p.text, fontSize = 20.sp, color = colors.onSurface,
            maxLines = 3, overflow = TextOverflow.Ellipsis)
    }
}

/**
 * Recent messages from the main phone. Tap one to dismiss it, or Clear for all:
 * both also clear the notification on the main phone.
 */
@Composable
fun MessagesCard() {
    val context = LocalContext.current
    val items by RideNotifications.items.collectAsState()
    CollapsibleCard(
        title = "Messages",
        stateKey = "messages",
        summary = when (items.size) {
            0 -> "No new messages"
            1 -> "1 new: ${items.first().title}"
            else -> "${items.size} new · latest: ${items.first().title}"
        },
        trailing = { _ ->
            if (items.isNotEmpty()) {
                TextButton(onClick = { RideNotifications.clearEverywhere(context) }) { Text("Clear") }
            }
        }
    ) {
        if (items.isEmpty()) {
            Body("No new messages", dim = true)
        } else {
            Body("Tap a message to dismiss it here and on your main phone.", dim = true)
        }
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
