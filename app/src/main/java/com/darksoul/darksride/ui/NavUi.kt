package com.darksoul.darksride.ui

import android.text.format.DateUtils
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.darksoul.darksride.AppMode
import com.darksoul.darksride.link.LinkService
import com.darksoul.darksride.link.LinkState
import com.darksoul.darksride.link.LinkStatus
import com.darksoul.darksride.nav.NavHistory
import com.darksoul.darksride.nav.RideDestination
import com.darksoul.darksride.nav.RideNav
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private fun ago(time: Long): String =
    DateUtils.getRelativeTimeSpanString(time, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString()

private fun clockTime(time: Long): String = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(time))

/**
 * Bike phone: the route shared from the main phone (with details and Navigate / Show on map),
 * and recent destinations below it: tap one to navigate there again.
 */
@Composable
fun DestinationCard() {
    val context = LocalContext.current
    LaunchedEffect(Unit) { NavHistory.load(context) }
    val last by RideNav.last.collectAsState()
    val history by NavHistory.items.collectAsState()
    val current = last
    val recent = history.filterNot { current != null && it.samePlace(current) }.take(5)
    if (current == null && recent.isEmpty()) return

    CollapsibleCard(
        title = "Navigation",
        stateKey = "destination",
        summary = current?.let { "Shared route: ${it.name}" } ?: "Recent: ${recent.first().name}",
        trailing = { open ->
            if (!open && current != null) {
                TextButton(onClick = { RideNav.navigate(context, current) }) { Text("Navigate") }
            }
        }
    ) {
        if (current != null) {
            Text("SHARED ROUTE", fontSize = 12.sp, fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary, letterSpacing = 1.2.sp)
            Text(current.name, fontSize = 24.sp, fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface, maxLines = 2, overflow = TextOverflow.Ellipsis)
            current.address?.let {
                Text(it, fontSize = 15.sp, maxLines = 3, overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f))
            }
            Detail("Sent", "${clockTime(current.sentAt)} · ${ago(current.sentAt)}")
            Detail("Mode", "Two-wheeler, Google Maps")
            if (current.lat != null && current.lng != null) {
                Detail("Location", "%.5f, %.5f".format(current.lat, current.lng))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Column(Modifier.weight(1f)) { BigButton("Navigate", onClick = { RideNav.navigate(context, current) }) }
                Column(Modifier.weight(1f)) {
                    BigButton("Show on map", secondary = true, onClick = { RideNav.showOnMap(context, current) })
                }
            }
        }
        if (recent.isNotEmpty()) {
            Text("RECENT", fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                modifier = Modifier.padding(top = if (current != null) 8.dp else 0.dp))
            recent.forEachIndexed { i, d ->
                if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                HistoryRow(d, action = "Go", onClick = { RideNav.goAgain(context, d) })
            }
        }
    }
}

@Composable
private fun Detail(label: String, value: String) {
    Row {
        Text(label, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
            modifier = Modifier.padding(end = 10.dp))
        Text(value, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun HistoryRow(d: RideDestination, action: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(d.name, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1,
                overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurface)
            Text(
                listOfNotNull(d.address, ago(d.sentAt)).joinToString(" · "),
                fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }
        Text(action, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
    }
}

/** Main phone: send a place again (it goes to the bike if the link is up). */
private fun sendAgain(context: android.content.Context, d: RideDestination) {
    if (LinkState.status.value != LinkStatus.CONNECTED) {
        Toast.makeText(context, "Bike not connected", Toast.LENGTH_SHORT).show()
        return
    }
    val again = d.copy(sentAt = System.currentTimeMillis())
    LinkService.sendJson(context, again.toJson())
    NavHistory.add(context, again)
    Toast.makeText(context, "Sent to bike: ${d.name}", Toast.LENGTH_SHORT).show()
}

/** Main phone Ride page: how to send a place, plus the last few sent (tap to send again). */
@Composable
fun SendToBikeCard() {
    val context = LocalContext.current
    LaunchedEffect(Unit) { NavHistory.load(context) }
    val history by NavHistory.items.collectAsState()
    Card {
        Text("Plan a ride", fontSize = 18.sp, fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface)
        Body("In Google Maps, find a place and tap Share → Send to bike. " +
            "${RoleNames.RIDE} starts navigating.", dim = true)
        if (history.isNotEmpty()) {
            Text("RECENTLY SENT", fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                modifier = Modifier.padding(top = 6.dp))
            history.take(3).forEach { d -> HistoryRow(d, action = "Send", onClick = { sendAgain(context, d) }) }
        }
    }
}

/** Settings: the full navigation history (bike: tap to navigate; main phone: tap to send again). */
@Composable
fun NavHistoryCard(mode: AppMode) {
    val context = LocalContext.current
    LaunchedEffect(Unit) { NavHistory.load(context) }
    val history by NavHistory.items.collectAsState()
    CollapsibleCard(
        title = "Navigation history",
        stateKey = "nav_history",
        defaultOpen = false,
        summary = if (history.isEmpty()) "Nothing yet" else "${history.size} places · last: ${history.first().name}"
    ) {
        if (history.isEmpty()) {
            Body(if (mode == AppMode.MAIN) "Places you send to the bike appear here."
            else "Places sent from your ${RoleNames.MAIN} appear here.", dim = true)
        }
        history.forEachIndexed { i, d ->
            if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
            if (mode == AppMode.RIDE) HistoryRow(d, action = "Go", onClick = { RideNav.goAgain(context, d) })
            else HistoryRow(d, action = "Send", onClick = { sendAgain(context, d) })
        }
        if (history.isNotEmpty()) {
            TextButton(onClick = { NavHistory.clear(context) }) { Text("Clear history") }
        }
    }
}
