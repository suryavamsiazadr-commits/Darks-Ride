package com.darksoul.darksride.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.darksoul.darksride.AppMode
import com.darksoul.darksride.AppPrefs
import com.darksoul.darksride.overlay.RideOverlay
import com.darksoul.darksride.phone.CallRelay
import com.darksoul.darksride.phone.PhoneListenerService
import com.darksoul.darksride.ui.theme.Go

/** The things a phone needs before Dark's Ride works fully. */
private enum class Step { BLUETOOTH, RIDE_PHONE, BACKGROUND, NOTIFICATIONS, CALLS, OVERLAY }

private data class SetupItem(val step: Step, val label: String, val done: Boolean, val optional: Boolean = false)

/**
 * Setup checklist. Every missing row can be tapped: a short pop-up says what to do, then
 * it opens the right Android screen. Ticks refresh whenever you come back to the app.
 * Closes itself once everything is done.
 */
@Composable
fun SetupCard(mode: AppMode, hideWhenComplete: Boolean = false) {
    val context = LocalContext.current
    var refresh by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { refresh++ }

    val items = remember(refresh, mode) { setupItems(context, mode) }
    val missing = items.count { !it.done && !it.optional }
    var asking by remember { mutableStateOf<Step?>(null) }

    val settings = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { refresh++ }
    val permissions = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        refresh++
        if (!CallRelay.hasCallAccess(context)) {
            // Android stops asking after two "Don't allow": send the rider to App info instead.
            Toast.makeText(context, "Tap Permissions → Phone → Allow", Toast.LENGTH_LONG).show()
            settings.launch(appInfo(context))
        }
    }

    fun open(step: Step) {
        when (step) {
            Step.CALLS -> permissions.launch(CallRelay.PERMISSIONS)
            Step.BACKGROUND -> launchFirst({ settings.launch(it) }, batteryRequest(context),
                Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS), appInfo(context))
            Step.NOTIFICATIONS -> launchFirst({ settings.launch(it) }, listenerDetail(context),
                Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS), appInfo(context))
            Step.OVERLAY -> launchFirst({ settings.launch(it) }, RideOverlay.permissionIntent(context),
                appInfo(context))
            Step.BLUETOOTH, Step.RIDE_PHONE -> Unit // handled earlier on the screen
        }
    }

    if (hideWhenComplete && missing == 0) return

    CollapsibleCard(
        title = if (missing == 0) "Setup complete" else "Setup: $missing step(s) left",
        stateKey = "setup",
        defaultOpen = missing > 0,
        persist = false,
        titleColor = if (missing == 0) Go else MaterialTheme.colorScheme.primary,
        summary = if (missing == 0) "Everything is allowed" else "Tap a step to allow it"
    ) {
        items.forEach { item ->
            SetupRow(item, onClick = { if (!item.done) asking = item.step })
        }
        if (missing > 0) Body("Tap a step without a tick to set it up.", dim = true)
        BrandTip()
    }

    asking?.let { step ->
        SetupDialog(
            step = step,
            mode = mode,
            onGo = { asking = null; open(step) },
            onAppInfo = { asking = null; settings.launch(appInfo(context)) },
            onDismiss = { asking = null }
        )
    }
}

@Composable
private fun SetupRow(item: SetupItem, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !item.done, onClick = onClick)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            if (item.done) "✓" else "○", fontSize = 20.sp, fontWeight = FontWeight.Bold,
            color = if (item.done) Go else colors.primary
        )
        Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
            Text(
                item.label + if (item.optional) " (optional)" else "",
                fontSize = 16.sp,
                color = colors.onSurface.copy(alpha = if (item.done) 0.6f else 1f)
            )
        }
        if (!item.done) {
            Text("Set up ›", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = colors.primary)
        }
    }
}

@Composable
private fun SetupDialog(
    step: Step,
    mode: AppMode,
    onGo: () -> Unit,
    onAppInfo: () -> Unit,
    onDismiss: () -> Unit
) {
    val restricted = "If the switch is greyed out (\"Restricted setting\"): tap App info below, " +
        "then ⋮ at the top right → Allow restricted settings, and try again."
    val (title, text, button) = when (step) {
        Step.BACKGROUND -> Triple(
            "Background running",
            "Keeps the link running with the screen off and the app closed.\n\n" +
                "On the next screen tap Allow. If a list opens instead, find Dark's Ride and " +
                "choose \"Don't optimise\" or \"Unrestricted\".",
            "Continue"
        )
        Step.NOTIFICATIONS -> Triple(
            "Music and messages",
            "Lets Dark's Ride see what is playing and your WhatsApp / SMS messages, to show " +
                "them on the bike.\n\nOn the next screen turn on Dark's Ride and confirm.\n\n$restricted",
            "Open settings"
        )
        Step.CALLS -> Triple(
            "Calls",
            "Shows who is calling on the bike and lets you answer or reject from there.\n\n" +
                "Android will ask twice: allow both.",
            "Allow"
        )
        Step.OVERLAY -> if (mode == AppMode.RIDE) Triple(
            "Cards over Google Maps",
            "Shows calls and messages on top of Google Maps while you ride.\n\n" +
                "On the next screen find Dark's Ride and turn on \"Allow display over other " +
                "apps\" (LG: \"Appear on top\").\n\n$restricted",
            "Open settings"
        ) else Triple(
            "Answer WhatsApp calls",
            "Android only lets WhatsApp's call screen open from the bike if Dark's Ride may " +
                "\"Display over other apps\". Nothing appears on this phone.\n\n" +
                "On the next screen turn it on for Dark's Ride.\n\n$restricted",
            "Open settings"
        )
        Step.BLUETOOTH, Step.RIDE_PHONE -> Triple("Setup", "", "OK")
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = { TextButton(onClick = onGo) { Text(button) } },
        dismissButton = {
            Row {
                if (step == Step.NOTIFICATIONS || step == Step.OVERLAY) {
                    TextButton(onClick = onAppInfo) { Text("App info") }
                }
                TextButton(onClick = onDismiss) { Text("Not now") }
            }
        }
    )
}

private fun setupItems(c: Context, mode: AppMode): List<SetupItem> {
    val bluetooth = granted(c, Manifest.permission.BLUETOOTH_CONNECT)
    val background = c.getSystemService(PowerManager::class.java)
        .isIgnoringBatteryOptimizations(c.packageName)
    return when (mode) {
        AppMode.MAIN -> listOf(
            SetupItem(Step.BLUETOOTH, "Bluetooth access", bluetooth),
            SetupItem(Step.RIDE_PHONE, "Ride phone picked", AppPrefs.rideAddress(c) != null),
            SetupItem(Step.BACKGROUND, "Background running", background),
            SetupItem(Step.NOTIFICATIONS, "Music and messages", PhoneListenerService.hasAccess(c)),
            SetupItem(Step.CALLS, "Calls", CallRelay.hasCallAccess(c)),
            SetupItem(Step.OVERLAY, "Answer WhatsApp calls", RideOverlay.canShow(c), optional = true)
        )
        AppMode.RIDE -> listOf(
            SetupItem(Step.BLUETOOTH, "Bluetooth access", bluetooth),
            SetupItem(Step.BACKGROUND, "Background running", background),
            SetupItem(Step.OVERLAY, "Cards over Google Maps", RideOverlay.canShow(c))
        )
    }
}

/** Tries each screen in turn: some phones don't have the most direct one. */
private fun launchFirst(launch: (Intent) -> Unit, vararg intents: Intent) {
    for (intent in intents) {
        try {
            launch(intent)
            return
        } catch (e: ActivityNotFoundException) {
            // try the next one
        }
    }
}

private fun granted(c: Context, permission: String) =
    ContextCompat.checkSelfPermission(c, permission) == PackageManager.PERMISSION_GRANTED

@SuppressLint("BatteryLife") // Sideloaded app for riders; the link must survive screen-off.
private fun batteryRequest(c: Context) =
    Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, "package:${c.packageName}".toUri())

private fun listenerDetail(c: Context) =
    Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS).putExtra(
        Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME,
        PhoneListenerService.component(c).flattenToString()
    )

internal fun appInfo(c: Context) =
    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:${c.packageName}".toUri())

/** Brands known to stop background apps: one extra step in their own settings. */
private val STRICT_BRANDS = setOf(
    "xiaomi", "redmi", "poco", "oppo", "realme", "vivo", "iqoo",
    "oneplus", "huawei", "honor", "infinix", "tecno"
)

@Composable
private fun BrandTip() {
    val context = LocalContext.current
    val brand = Build.MANUFACTURER.lowercase()
    if (brand !in STRICT_BRANDS) return
    val name = Build.MANUFACTURER.replaceFirstChar { it.uppercase() }
    Body("$name phones may stop Dark's Ride in the background. In App info set Battery to " +
        "\"No restrictions\" and turn on \"Autostart\" if you see it.", dim = true)
    BigButton("Open App info", secondary = true, onClick = {
        context.startActivity(appInfo(context).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    })
}
