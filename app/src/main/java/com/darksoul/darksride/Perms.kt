package com.darksoul.darksride

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/** Permission checks that differ between Android versions. */
object Perms {
    /**
     * Bluetooth: Android 12+ asks the user ("Nearby devices"); Android 11 grants it at install
     * (the old BLUETOOTH permission), so there's nothing to ask for there.
     */
    fun bluetooth(c: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            ContextCompat.checkSelfPermission(c, Manifest.permission.BLUETOOTH_CONNECT) ==
            PackageManager.PERMISSION_GRANTED

    /** What to ask for so the link can run (empty on Android 11: nothing to ask). */
    fun linkPermissions(): Array<String> = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU ->
            arrayOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.POST_NOTIFICATIONS)
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> arrayOf(Manifest.permission.BLUETOOTH_CONNECT)
        else -> emptyArray()
    }
}
