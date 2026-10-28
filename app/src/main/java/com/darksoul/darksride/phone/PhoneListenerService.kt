package com.darksoul.darksride.phone

import android.content.ComponentName
import android.content.Context
import android.service.notification.NotificationListenerService
import androidx.core.app.NotificationManagerCompat

/**
 * Notification access for the main phone.
 *
 * Step 4: granting access to this service is what lets Dark's Ride see and control the
 * music playing in other apps (YouTube Music, YouTube, Spotify...).
 * Step 5 will forward notifications from here.
 */
class PhoneListenerService : NotificationListenerService() {

    companion object {
        fun component(c: Context) = ComponentName(c, PhoneListenerService::class.java)

        fun hasAccess(c: Context): Boolean =
            NotificationManagerCompat.getEnabledListenerPackages(c).contains(c.packageName)
    }
}
