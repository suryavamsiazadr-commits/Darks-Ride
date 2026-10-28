package com.darksoul.darksride.panel

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.darksoul.darksride.AppMode
import com.darksoul.darksride.link.LinkState
import com.darksoul.darksride.link.LinkStatus
import com.darksoul.darksride.ui.RideColumn
import com.darksoul.darksride.ui.RideContent
import com.darksoul.darksride.ui.Header
import com.darksoul.darksride.ui.theme.DarksRideTheme

/** The ride panel shown on the second screen: calls, destination, music and messages. */
class RidePanelActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        enableEdgeToEdge()
        setContent {
            DarksRideTheme {
                PanelScreen(onClose = { finish() })
            }
        }
    }
}

@Composable
private fun PanelScreen(onClose: () -> Unit) {
    val status by LinkState.status.collectAsState()
    val peer by LinkState.peerName.collectAsState()

    RideColumn {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.weight(1f)) {
                Header(title = "Dark's Ride", status = status, peer = peer, mode = AppMode.RIDE)
            }
            TextButton(onClick = onClose) { Text("Close") }
        }
        if (status == LinkStatus.CONNECTED) RideContent()
    }
}
