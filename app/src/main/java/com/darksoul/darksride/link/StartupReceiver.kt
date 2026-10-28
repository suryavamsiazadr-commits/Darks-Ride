package com.darksoul.darksride.link

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.darksoul.darksride.AppPrefs

/**
 * Starts the phone link without opening the app:
 * after the phone restarts, and after Dark's Ride is updated (an update stops the old link).
 */
class StartupReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val reason = when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED -> "Started after the phone restarted"
            Intent.ACTION_MY_PACKAGE_REPLACED -> "Started after an app update"
            else -> return
        }
        if (!AppPrefs.linkEnabled(context) || !AppPrefs.readyToLink(context)) return
        val granted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.BLUETOOTH_CONNECT
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) return
        try {
            LinkService.resume(context, reason)
        } catch (e: Exception) {
            // Android refused a background start; the rider can open the app to start it.
            LinkState.log("Could not start by itself: ${e.javaClass.simpleName}")
        }
    }
}
