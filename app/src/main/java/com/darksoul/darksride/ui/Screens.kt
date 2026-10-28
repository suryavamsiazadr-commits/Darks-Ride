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
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import com.darksoul.darksride.AppMode
import com.darksoul.darksride.AppPrefs
import com.darksoul.darksride.link.LinkService
import com.darksoul.darksride.link.LinkState
import com.darksoul.darksride.link.LinkStatus
import com.darksoul.darksride.link.LogLine
import com.darksoul.darksride.ui.theme.Go
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ---------- Shared building blocks ----------

@Composable
fun RideColumn(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        content = content
    )
}

@Composable
internal fun BigButton(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    secondary: Boolean = false
) {
    val modifier = Modifier
        .fillMaxWidth()
        .height(60.dp)
    val shape = RoundedCornerShape(16.dp)
    if (secondary) {
        OutlinedButton(onClick = onClick, enabled = enabled, shape = shape, modifier = modifier) {
            Text(text, fontSize = 18.sp)
        }
    } else {
        Button(onClick = onClick, enabled = enabled, shape = shape, modifier = modifier) {
            Text(text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
internal fun Card(onClick: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    val colors = MaterialTheme.colorScheme
    var modifier = Modifier
        .fillMaxWidth()
        .clip(shape)
        .background(colors.surface)
        .border(1.dp, colors.primary.copy(alpha = 0.35f), shape)
    if (onClick != null) modifier = modifier.clickable(onClick = onClick)
    Column(
        modifier = modifier.padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        content = content
    )
}

@Composable
internal fun Title(text: String) = Text(
    text, fontSize = 28.sp, fontWeight = FontWeight.Bold,
    color = MaterialTheme.colorScheme.primary
)

@Composable
internal fun Heading(text: String) = Text(
    text, fontSize = 20.sp, fontWeight = FontWeight.SemiBold,
    color = MaterialTheme.colorScheme.onBackground
)

@Composable
internal fun Body(text: String, dim: Boolean = false) = Text(
    text, fontSize = 16.sp,
    color = MaterialTheme.colorScheme.onBackground.copy(alpha = if (dim) 0.65f else 1f)
)

// ---------- Screen 1: what is this phone for? ----------

@Composable
fun ModeChoiceScreen(onPick: (AppMode) -> Unit) {
    RideColumn {
        Title("Welcome to Dark's Ride")
        Body("What is this phone for?")
        Card(onClick = { onPick(AppMode.MAIN) }) {
            Text("Main phone", fontSize = 22.sp, fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary)
            Text("The phone with your SIM card. It stays in your pocket and sends " +
                "music, calls and notifications to the bike.",
                fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface)
        }
        Card(onClick = { onPick(AppMode.RIDE) }) {
            Text("Ride screen", fontSize = 22.sp, fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary)
            Text("The phone mounted on the bike. It shows maps, music controls and alerts.",
                fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface)
        }
        Body("You can change this later.", dim = true)
    }
}

// ---------- Screen 2: the link ----------

@Composable
fun LinkScreen(mode: AppMode, onChangeRole: () -> Unit, onTurnOff: () -> Unit) {
    val context = LocalContext.current
    val adapter = remember { context.getSystemService(BluetoothManager::class.java)?.adapter }

    val status by LinkState.status.collectAsState()
    val peer by LinkState.peerName.collectAsState()
    val reconnects by LinkState.reconnects.collectAsState()
    val log by LinkState.log.collectAsState()

    var hasAccess by remember { mutableStateOf(hasBluetoothAccess(context)) }
    val accessLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { hasAccess = hasBluetoothAccess(context) }

    var bluetoothOn by remember { mutableStateOf(adapter?.isEnabled == true) }
    val enableLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { bluetoothOn = adapter?.isEnabled == true }

    var backgroundAllowed by remember { mutableStateOf(isBackgroundAllowed(context)) }
    val batteryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { backgroundAllowed = isBackgroundAllowed(context) }

    var rideAddress by remember { mutableStateOf(AppPrefs.rideAddress(context)) }
    var rideName by remember { mutableStateOf(AppPrefs.rideName(context)) }
    var picking by remember { mutableStateOf(false) }
    var userStopped by rememberSaveable { mutableStateOf(!AppPrefs.linkEnabled(context)) }

    // Start the link by itself once everything it needs is in place.
    val ready = hasAccess && bluetoothOn && (mode == AppMode.RIDE || rideAddress != null)
    LaunchedEffect(ready) {
        if (ready && !userStopped && LinkState.status.value == LinkStatus.STOPPED) {
            LinkService.start(context)
        }
    }

    RideColumn {
        Title(if (mode == AppMode.MAIN) "Main phone" else "Ride screen")
        Body("Dark's Ride · version 1", dim = true)

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
                        userStopped = false
                        if (LinkState.status.value != LinkStatus.STOPPED) LinkService.restart(context)
                        else LinkService.start(context)
                    },
                    onCancel = if (rideAddress != null) ({ picking = false }) else null
                )
            }

            else -> {
                StatusCard(status = status, peer = peer, mode = mode)
                SetupChecklist(mode)

                if (mode == AppMode.RIDE) {
                    OverlayAccessCard()
                    SmallScreenCard()
                }
                if (mode == AppMode.RIDE && status == LinkStatus.CONNECTED) {
                    RideContent()
                }

                if (mode == AppMode.MAIN) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Body("Ride phone: ${rideName ?: "not set"}", dim = true)
                        TextButton(onClick = { picking = true }) { Text("Change") }
                    }
                    CallAccessCard()
                    AppCallAnswerCard()
                    MusicAccessCard()
                    NotificationFilterCard()
                }

                if (status == LinkStatus.STOPPED) {
                    BigButton("Start link", onClick = {
                        userStopped = false
                        LinkService.start(context)
                    })
                }

                BigButton(
                    "Send test message",
                    enabled = status == LinkStatus.CONNECTED,
                    onClick = {
                        val time = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())
                        LinkService.sendTest(context, "Hello from ${Build.MODEL} at $time")
                    }
                )

                if (!backgroundAllowed) {
                    Card {
                        Body("Let Dark's Ride run in the background, so the link survives " +
                            "with the screen off.")
                    }
                    BigButton("Allow background running", secondary = true, onClick = {
                        batteryLauncher.launch(batteryIntent(context))
                    })
                }

                Body(
                    if (userStopped) "The link stays off until you start it."
                    else "Starts by itself after a phone restart. You don't need to open the app.",
                    dim = true
                )
                Body("Reconnects since start: $reconnects", dim = true)

                BigButton("Turn off Dark's Ride", secondary = true, onClick = onTurnOff)
                Body("Stops everything on this phone and closes the app. It stays off, even " +
                    "after a restart, until you open Dark's Ride again.", dim = true)

                ActivityLog(log)
            }
        }

        TextButton(onClick = onChangeRole) { Text("Change this phone's role") }
    }
}

@Composable
internal fun StatusCard(status: LinkStatus, peer: String?, mode: AppMode) {
    val (label, dot) = when (status) {
        LinkStatus.CONNECTED -> "Connected to ${peer ?: "your phone"}" to Go
        LinkStatus.CONNECTING -> "Connecting…" to MaterialTheme.colorScheme.primary
        LinkStatus.WAITING ->
            (if (mode == AppMode.RIDE) "Waiting for the main phone…"
            else "Looking for the ride phone…") to MaterialTheme.colorScheme.primary
        LinkStatus.STOPPED -> "Link is off" to Color.Gray
    }
    Card {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(dot)
            )
            Text(
                label, fontSize = 20.sp, fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(start = 12.dp)
            )
        }
    }
}

@Composable
private fun RidePhonePicker(
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

@Composable
private fun ActivityLog(lines: List<LogLine>) {
    Heading("Activity")
    if (lines.isEmpty()) Body("Nothing yet.", dim = true)
    lines.forEach { line ->
        Text(
            "${line.time}  ${line.text}",
            fontSize = 14.sp,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.85f)
        )
    }
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

private fun isBackgroundAllowed(c: Context): Boolean =
    c.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(c.packageName)

@SuppressLint("BatteryLife") // Sideloaded app for riders; the link must survive screen-off.
private fun batteryIntent(c: Context): Intent =
    Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, "package:${c.packageName}".toUri())
