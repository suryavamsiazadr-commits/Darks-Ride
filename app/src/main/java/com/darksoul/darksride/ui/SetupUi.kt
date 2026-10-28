package com.darksoul.darksride.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import com.darksoul.darksride.AppMode
import com.darksoul.darksride.AppPrefs
import com.darksoul.darksride.overlay.RideOverlay
import com.darksoul.darksride.phone.CallRelay
import com.darksoul.darksride.phone.PhoneListenerService
import com.darksoul.darksride.ui.theme.Go

/** One line of the setup checklist. */
private data class SetupItem(val label: String, val done: Boolean, val optional: Boolean = false)

/**
 * Setup at a glance, so a friend installing Dark's Ride can see what is still missing.
 * Each missing item has its own button further down the screen.
 */
@Composable
fun SetupChecklist(mode: AppMode) {
    val context = LocalContext.current
    val items = setupItems(context, mode)
    val missing = items.count { !it.done && !it.optional }

    Card {
        Text(
            if (missing == 0) "Setup complete" else "Setup: $missing step(s) left",
            fontSize = 20.sp, fontWeight = FontWeight.SemiBold,
            color = if (missing == 0) Go else MaterialTheme.colorScheme.primary
        )
        items.forEach { item ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (item.done) "✓" else "○",
                    fontSize = 18.sp, fontWeight = FontWeight.Bold,
                    color = if (item.done) Go else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
                Text(
                    item.label + if (item.optional) " (optional)" else "",
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (item.done) 0.65f else 1f),
                    modifier = Modifier.padding(start = 12.dp)
                )
            }
        }
    }
    BrandTip()
}

private fun setupItems(c: Context, mode: AppMode): List<SetupItem> {
    val bluetooth = granted(c, Manifest.permission.BLUETOOTH_CONNECT)
    val background = c.getSystemService(PowerManager::class.java)
        .isIgnoringBatteryOptimizations(c.packageName)
    return when (mode) {
        AppMode.MAIN -> listOf(
            SetupItem("Bluetooth access", bluetooth),
            SetupItem("Ride phone picked", AppPrefs.rideAddress(c) != null),
            SetupItem("Background running", background),
            SetupItem("Music and messages (notification access)", PhoneListenerService.hasAccess(c)),
            SetupItem("Calls (phone access)", CallRelay.hasCallAccess(c)),
            SetupItem("Answer WhatsApp calls (display over apps)", RideOverlay.canShow(c), optional = true)
        )
        AppMode.RIDE -> listOf(
            SetupItem("Bluetooth access", bluetooth),
            SetupItem("Background running", background),
            SetupItem("Cards over Google Maps (display over apps)", RideOverlay.canShow(c))
        )
    }
}

private fun granted(c: Context, permission: String) =
    ContextCompat.checkSelfPermission(c, permission) == PackageManager.PERMISSION_GRANTED

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
    Card {
        Text("One more step on $name phones", fontSize = 18.sp, fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary)
        Body("$name may stop Dark's Ride in the background. In App info: set Battery to " +
            "\"No restrictions\" (or \"Allow background activity\") and turn on " +
            "\"Autostart\" if you see it.")
    }
    BigButton("Open App info", secondary = true, onClick = {
        context.startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:${context.packageName}".toUri())
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    })
}
