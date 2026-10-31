package com.darksoul.darksride.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.darksoul.darksride.AppPrefs

/** Settings (bike phone): the order of the ride screen's cards, with ▲ / ▼ to move them. */
@Composable
fun CardOrderCard() {
    val context = LocalContext.current
    val order by AppPrefs.cardOrder(context).collectAsState()
    val colors = MaterialTheme.colorScheme

    fun move(i: Int, by: Int) {
        val j = i + by
        if (j !in order.indices) return
        val list = order.toMutableList()
        list[i] = order[j].also { list[j] = order[i] }
        AppPrefs.setCardOrder(context, list)
    }

    CollapsibleCard(
        title = "Card order",
        stateKey = "card_order",
        defaultOpen = false,
        summary = order.joinToString(" · ") { it.label }
    ) {
        Body("Your order for the ride screen. While something is live it jumps to the top, " +
            "then goes back to its place: a call until it ends, a new message for its spotlight " +
            "time, and music while it plays.", dim = true)
        order.forEachIndexed { i, card ->
            Row(
                modifier = Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(colors.onSurface.copy(alpha = 0.05f))
                    .padding(start = 14.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("${i + 1}", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = colors.primary)
                Text(card.label, fontSize = 17.sp, color = colors.onSurface, modifier = Modifier.weight(1f))
                ArrowButton("▲", enabled = i > 0) { move(i, -1) }
                ArrowButton("▼", enabled = i < order.lastIndex) { move(i, +1) }
            }
        }
        TextButton(onClick = { AppPrefs.setCardOrder(context, RideCard.DEFAULT) }) { Text("Reset to default") }
    }
}

@Composable
private fun ArrowButton(symbol: String, enabled: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier = Modifier.size(48.dp).clip(CircleShape)
            .background(if (enabled) colors.primary.copy(alpha = 0.15f) else colors.onSurface.copy(alpha = 0.04f))
            .border(1.dp, if (enabled) colors.primary.copy(alpha = 0.5f) else colors.onSurface.copy(alpha = 0.08f), CircleShape)
            .rideTap(onTap = { if (enabled) onClick() }),
        contentAlignment = Alignment.Center
    ) {
        Text(symbol, fontSize = 16.sp, color = if (enabled) colors.primary else colors.onSurface.copy(alpha = 0.25f))
    }
}
