package com.darksoul.darksride.ui

import android.hardware.display.DisplayManager
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.darksoul.darksride.AppPrefs
import com.darksoul.darksride.panel.RidePanel

/**
 * Ride screen: the second-screen panel (LG Wing in Swivel mode).
 * Only shown while the phone actually has a second screen.
 */
@Composable
fun SmallScreenCard() {
    val context = LocalContext.current
    var hasSecond by remember { mutableStateOf(RidePanel.secondaryDisplayId(context) != null) }
    var auto by remember { mutableStateOf(AppPrefs.flag(context, AppPrefs.PANEL_AUTO)) }

    // Follow the Wing being swivelled open or closed.
    DisposableEffect(Unit) {
        val dm = context.getSystemService(DisplayManager::class.java)
        val listener = object : DisplayManager.DisplayListener {
            override fun onDisplayAdded(displayId: Int) { hasSecond = RidePanel.secondaryDisplayId(context) != null }
            override fun onDisplayRemoved(displayId: Int) { hasSecond = RidePanel.secondaryDisplayId(context) != null }
            override fun onDisplayChanged(displayId: Int) { hasSecond = RidePanel.secondaryDisplayId(context) != null }
        }
        dm.registerDisplayListener(listener, null)
        onDispose { dm.unregisterDisplayListener(listener) }
    }

    if (!hasSecond) return
    Card {
        Text("Small screen", fontSize = 20.sp, fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface)
        Body("Keep Google Maps on the big screen and the ride panel (music, messages, " +
            "calls, destination) on the small one.", dim = true)
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Open it by itself", fontSize = 17.sp, color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f))
            Switch(checked = auto, onCheckedChange = {
                auto = it
                AppPrefs.setFlag(context, AppPrefs.PANEL_AUTO, it)
            })
        }
    }
    BigButton("Open ride panel on small screen", onClick = { RidePanel.open(context) })
}
