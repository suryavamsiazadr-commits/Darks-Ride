package com.darksoul.darksride.ui

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.darksoul.darksride.AppMode
import com.darksoul.darksride.AppPrefs
import com.darksoul.darksride.link.LinkState
import com.darksoul.darksride.link.LinkStatus
import com.darksoul.darksride.net.AutoTap
import com.darksoul.darksride.net.BikeNet
import com.darksoul.darksride.ui.theme.Go

/**
 * Ride page, both phones: is the bike online through Bluetooth tethering, the
 * "Connect automatically" switch, Connect now, and the one-time steps.
 */
@Composable
fun InternetCard(mode: AppMode) {
    val context = LocalContext.current
    var refresh by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { refresh++ }
    val bt by BikeNet.btInternet.collectAsState()
    val wifi by BikeNet.wifi.collectAsState()
    val bike by BikeNet.bikeOnline.collectAsState()
    val status by BikeNet.status.collectAsState()
    val link by LinkState.status.collectAsState()
    val helper = remember(refresh) { AutoTap.isEnabled(context) }
    var auto by remember { mutableStateOf(BikeNet.autoEnabled(context)) }

    val online = if (mode == AppMode.RIDE) bt || wifi else bike == true
    val headline = when {
        mode == AppMode.RIDE && bt -> "Online through your ${RoleNames.MAIN}"
        mode == AppMode.RIDE && wifi -> "On Wi-Fi"
        mode == AppMode.RIDE -> "No internet"
        link != LinkStatus.CONNECTED -> "Bike not linked"
        bike == true -> "Bike is online through this phone"
        bike == false -> "Bike has no internet"
        else -> "Checking…"
    }

    CollapsibleCard(
        title = "Bike internet",
        stateKey = "internet",
        attention = (mode == AppMode.RIDE || link == LinkStatus.CONNECTED) && !online || !helper,
        titleColor = if (online) Go else null,
        summary = headline + if (!helper) " · auto-tap off" else ""
    ) {
        Text(headline, fontSize = 18.sp, color = if (online) Go else MaterialTheme.colorScheme.onSurface)
        if (status.isNotEmpty() && !online) Body(status, dim = true)

        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Connect automatically", fontSize = 17.sp, color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f))
            Switch(checked = auto, onCheckedChange = {
                auto = it
                BikeNet.setAutoEnabled(context, it)
            })
        }
        if (!online) BigButton("Connect now", onClick = { BikeNet.request(context) })

        if (!helper) {
            Body("Turn on the auto-tap helper so Dark's Ride can flip the switch for you. " +
                (if (mode == AppMode.MAIN) "Bluetooth tethering" else "Internet access") +
                " can't be turned on by apps any other way.", dim = true)
            BigButton("Turn on auto-tap helper", secondary = true, onClick = {
                BikeNet.open(context, AutoTap.settingsIntent())
            })
        }

        var steps by remember { mutableStateOf(false) }
        TextButton(onClick = { steps = !steps }) { Text(if (steps) "Hide how it works" else "How it works ›") }
        if (steps) Body(internetSteps(mode, AppPrefs.peerName(context)), dim = true)
        if (steps) BigButton(
            if (mode == AppMode.MAIN) "Open tethering settings" else "Open Bluetooth settings",
            secondary = true,
            onClick = {
                BikeNet.open(context,
                    if (mode == AppMode.MAIN) BikeNet.tetherIntent(context) else Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
            }
        )
    }
}

/** The one-time steps, also shown in the setup checklist. */
internal fun internetSteps(mode: AppMode, mainName: String?): String = if (mode == AppMode.MAIN)
    "How it works: the bike gets internet over Bluetooth from this phone (Bluetooth tethering), " +
        "so the hotspot can stay off.\n" +
        "• Switch: Settings → Connections → Mobile Hotspot and Tethering → Bluetooth tethering.\n" +
        "• When the bike asks, Dark's Ride turns it on for you (auto-tap helper, phone unlocked). " +
        "If the phone is locked you get a notification instead.\n" +
        "• Turn the same helper on in Dark's Ride on the ${RoleNames.RIDE}."
else
    "How it works: this phone gets internet over Bluetooth from ${mainName ?: "your main phone"}.\n" +
        "• Once: Bluetooth → ⚙ next to ${mainName ?: "your main phone"} → turn on Internet access.\n" +
        "• Android doesn't reconnect it by itself after a break, so Dark's Ride does it each time " +
        "the link comes back (auto-tap helper: Settings opens for a second, then goes back to Maps).\n" +
        "• Bluetooth tethering must be on on the main phone; its Dark's Ride handles that."
