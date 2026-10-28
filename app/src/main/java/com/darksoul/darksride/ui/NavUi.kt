package com.darksoul.darksride.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import com.darksoul.darksride.nav.RideNav

/** Ride screen: the last destination sent from the main phone, with a Navigate button. */
@Composable
fun DestinationCard() {
    val context = LocalContext.current
    val last by RideNav.last.collectAsState()
    val d = last ?: return
    Card {
        Text("Destination from your main phone", fontSize = 14.sp,
            color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
        Text(d.name, fontSize = 22.sp, fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface, maxLines = 2,
            overflow = TextOverflow.Ellipsis)
        d.query?.takeIf { it != d.name }?.let {
            Text(it, fontSize = 15.sp, maxLines = 2, overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
        }
    }
    BigButton("Navigate", onClick = { RideNav.navigate(context, d) })
}
