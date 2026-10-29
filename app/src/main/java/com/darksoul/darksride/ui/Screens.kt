package com.darksoul.darksride.ui

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.darksoul.darksride.AppMode
import com.darksoul.darksride.AppPrefs
import com.darksoul.darksride.link.LinkService
import com.darksoul.darksride.link.LinkState
import com.darksoul.darksride.link.LinkStatus
import com.darksoul.darksride.link.LogLine
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ---------- Screen 1: what is this phone for? ----------

@Composable
fun ModeChoiceScreen(onPick: (AppMode) -> Unit) {
    RideColumn {
        Title("Welcome to Dark's Ride")
        Body("What is this phone for?")
        Card(onClick = { onPick(AppMode.MAIN) }) {
            Text(RoleNames.MAIN, fontSize = 22.sp, fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary)
            Text("Your main phone, the one with the SIM. It rides in your pocket and carries " +
                "music, calls and messages up to the bike.",
                fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface)
        }
        Card(onClick = { onPick(AppMode.RIDE) }) {
            Text(RoleNames.RIDE, fontSize = 22.sp, fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary)
            Text("The phone mounted on the bike: maps, music controls, calls and alerts.",
                fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface)
        }
        Body("You can change this later.", dim = true)
    }
}

// ---------- Screen 2: the link ----------

@Composable
fun LinkScreen(
    mode: AppMode,
    onOpenSettings: (() -> Unit)? = null,
    insets: WindowInsets = WindowInsets.safeDrawing
) {
    val context = LocalContext.current
    val adapter = remember { context.getSystemService(BluetoothManager::class.java)?.adapter }

    val status by LinkState.status.collectAsState()
    val peer by LinkState.peerName.collectAsState()

    var hasAccess by remember { mutableStateOf(hasBluetoothAccess(context)) }
    val accessLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { hasAccess = hasBluetoothAccess(context) }

    var bluetoothOn by remember { mutableStateOf(adapter?.isEnabled == true) }
    val enableLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { bluetoothOn = adapter?.isEnabled == true }

    var rideAddress by remember { mutableStateOf(AppPrefs.rideAddress(context)) }
    var rideName by remember { mutableStateOf(AppPrefs.rideName(context)) }
    var picking by remember { mutableStateOf(false) }
    val rideOn by AppPrefs.rideMode(context).collectAsState()

    // With Ride mode on, start the link by itself once everything it needs is in place.
    val ready = hasAccess && bluetoothOn && (mode == AppMode.RIDE || rideAddress != null)
    LaunchedEffect(ready, rideOn) {
        if (ready && rideOn && LinkState.status.value == LinkStatus.STOPPED) {
            LinkService.start(context)
        }
    }

    RideColumn(insets = insets) {
        Header(
            title = RoleNames.of(mode),
            status = if (ready) status else null,
            peer = peer,
            mode = mode,
            onSettings = onOpenSettings
        )

        when {
            adapter == null -> Body("This phone has no Bluetooth, so it cannot be used.")

            !hasAccess -> {
                Card {
                    Body("Dark's Ride needs Bluetooth access to talk to your other phone, " +
                        "and notification access to keep the link running in the background.")
                }
                BigButton("Allow access", onClick = { accessLauncher.launch(linkPermissions()) })
            }

            !bluetoothOn -> {
                Card { Body("Bluetooth is off.") }
                BigButton("Turn on Bluetooth", onClick = {
                    enableLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
                })
            }

            adapter != null && mode == AppMode.MAIN && (rideAddress == null || picking) -> {
                RidePhonePicker(
                    adapter = adapter,
                    onPick = { device ->
                        AppPrefs.setRidePhone(context, device.address, device.name)
                        rideAddress = device.address
                        rideName = device.name
                        picking = false
                        if (LinkState.status.value != LinkStatus.STOPPED) LinkService.restart(context)
                        else LinkService.start(context)
                    },
                    onCancel = if (rideAddress != null) ({ picking = false }) else null
                )
            }

            else -> {
                RideModeCard(mode = mode, on = rideOn)
                // Setup lives in Settings; it only shows here while something is missing.
                SetupCard(mode, hideWhenComplete = true)

                if (mode == AppMode.RIDE) {
                    if (status == LinkStatus.CONNECTED) RideContent()
                    else if (rideOn) Body("Waiting for your Pocket Sherpa…", dim = true)
                } else {
                    if (rideOn && status == LinkStatus.STOPPED) {
                        BigButton("Start link", onClick = { LinkService.turnOn(context) })
                    }
                    SendToBikeCard()
                }
            }
        }
        // Main phone shows the version above its tabs; the bike phone shows it here.
        if (mode == AppMode.RIDE) VersionLabel(modifier = Modifier.padding(top = 8.dp))
    }
}

/**
 * Ride mode: the one switch for everything that runs in the background (Bluetooth link,
 * music, messages, calls). Off stays off after restarts; the rest of the app still works.
 */
@Composable
private fun RideModeCard(mode: AppMode, on: Boolean) {
    val context = LocalContext.current
    Card {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Ride mode", fontSize = 20.sp, fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface)
                Text(
                    if (on) {
                        if (mode == AppMode.MAIN) "On: music, messages and calls go to the bike"
                        else "On: showing what your main phone sends"
                    } else "Off: nothing runs in the background",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }
            Switch(
                checked = on,
                onCheckedChange = { turnOn ->
                    if (turnOn) LinkService.turnOn(context) else LinkService.turnOff(context)
                }
            )
        }
    }
}

/** Link details and controls, closed by default (Settings). */
@Composable
internal fun LinkCard(mode: AppMode) {
    val context = LocalContext.current
    val adapter = remember { context.getSystemService(BluetoothManager::class.java)?.adapter }
    val status by LinkState.status.collectAsState()
    val rideOn by AppPrefs.rideMode(context).collectAsState()
    val reconnects by LinkState.reconnects.collectAsState()
    var rideName by remember { mutableStateOf(AppPrefs.rideName(context)) }
    var picking by remember { mutableStateOf(false) }
    val onStart = { LinkService.turnOn(context) }
    CollapsibleCard(
        title = "Link",
        stateKey = "link",
        defaultOpen = false,
        summary = if (mode == AppMode.MAIN) "Bike: ${rideName ?: "not set"} · reconnects $reconnects"
        else "Reconnects since start: $reconnects"
    ) {
        if (mode == AppMode.MAIN) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Bike phone: ${rideName ?: "not set"}", fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
                TextButton(onClick = { picking = !picking }) { Text(if (picking) "Cancel" else "Change") }
            }
            if (picking && adapter != null) {
                RidePhonePicker(
                    adapter = adapter,
                    onPick = { device ->
                        AppPrefs.setRidePhone(context, device.address, device.name)
                        rideName = device.name
                        picking = false
                        if (LinkState.status.value != LinkStatus.STOPPED) LinkService.restart(context)
                        else LinkService.turnOn(context)
                    },
                    onCancel = null
                )
            }
        }
        if (rideOn && status == LinkStatus.STOPPED) BigButton("Start link", onClick = onStart)
        BigButton(
            "Send test message",
            secondary = true,
            enabled = status == LinkStatus.CONNECTED,
            onClick = {
                val time = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())
                LinkService.sendTest(context, "Hello from ${Build.MODEL} at $time")
            }
        )
        Body(
            if (!rideOn) "Ride mode is off: nothing runs in the background."
            else "Starts by itself after a phone restart. You don't need to open the app.",
            dim = true
        )
        Body("Reconnects since start: $reconnects", dim = true)
    }
}

/** The activity log in a small window with its own scroll, closed by default. */
@Composable
internal fun ActivityCard() {
    val lines by LinkState.log.collectAsState()
    CollapsibleCard(
        title = "Activity",
        stateKey = "activity",
        defaultOpen = false,
        summary = lines.firstOrNull()?.let { "${it.time}  ${it.text}" } ?: "Nothing yet"
    ) {
        Body(if (lines.isEmpty()) "Nothing yet." else "Saved on this phone and never cleared. Newest first.", dim = true)
        Column(
            modifier = Modifier
                .heightIn(max = 320.dp)
                .verticalScroll(rememberScrollState())
        ) {
            lines.forEach { line -> LogRow(line) }
        }
    }
}

@Composable
private fun LogRow(line: LogLine) {
    Text(
        "${line.time}  ${line.text}",
        fontSize = 13.sp,
        fontFamily = FontFamily.Monospace,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
        modifier = Modifier.padding(vertical = 2.dp)
    )
}

@Composable
internal fun RidePhonePicker(
    adapter: BluetoothAdapter,
    onPick: (PairedDevice) -> Unit,
    onCancel: (() -> Unit)?
) {
    val context = LocalContext.current
    var refresh by remember { mutableIntStateOf(0) }
    val devices = remember(refresh) { pairedDevices(adapter) }

    Heading("Which phone is on the bike?")
    Body("This list shows devices already paired in Bluetooth settings. " +
        "Pair the two phones there once if the ride phone is missing.", dim = true)

    if (devices.isEmpty()) Body("No paired devices found.")
    devices.forEach { d ->
        Card(onClick = { onPick(d) }) {
            Text(d.name, fontSize = 18.sp, fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface)
            Text(if (d.isPhone) "Phone" else "Other device", fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f))
        }
    }

    BigButton("Open Bluetooth settings", secondary = true, onClick = {
        context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
    })
    BigButton("Refresh list", secondary = true, onClick = { refresh++ })
    if (onCancel != null) TextButton(onClick = onCancel) { Text("Cancel") }
}

// ---------- Helpers ----------

data class PairedDevice(val address: String, val name: String, val isPhone: Boolean)

@SuppressLint("MissingPermission") // Only called after Bluetooth access is granted.
private fun pairedDevices(adapter: BluetoothAdapter): List<PairedDevice> = try {
    adapter.bondedDevices.orEmpty()
        .map { d ->
            PairedDevice(
                address = d.address,
                name = d.name ?: d.address,
                isPhone = d.bluetoothClass?.majorDeviceClass == BluetoothClass.Device.Major.PHONE
            )
        }
        .sortedWith(compareByDescending<PairedDevice> { it.isPhone }.thenBy { it.name })
} catch (e: SecurityException) {
    emptyList()
}

private fun linkPermissions(): Array<String> =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        arrayOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.POST_NOTIFICATIONS)
    } else {
        arrayOf(Manifest.permission.BLUETOOTH_CONNECT)
    }

private fun hasBluetoothAccess(c: Context): Boolean =
    ContextCompat.checkSelfPermission(c, Manifest.permission.BLUETOOTH_CONNECT) ==
        PackageManager.PERMISSION_GRANTED
