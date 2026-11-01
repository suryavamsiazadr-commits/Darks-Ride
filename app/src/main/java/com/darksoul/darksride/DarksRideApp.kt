package com.darksoul.darksride

import android.app.Application
import com.darksoul.darksride.link.LinkState
import com.darksoul.darksride.overlay.RideOverlay
import com.darksoul.darksride.phone.RideCall

/** Runs first whenever the app starts (screen, service or boot): loads the activity log. */
class DarksRideApp : Application() {
    override fun onCreate() {
        super.onCreate()
        CrashCatcher.install(this)
        LinkState.init(this)
        com.darksoul.darksride.ui.theme.ThemeController.init(this)
        com.darksoul.darksride.net.BikeNet.watch(this)
        AppVisibility.track(this)
        // Ride mode off: clear a status notification an older version may have left behind.
        if (!AppPrefs.linkEnabled(this)) com.darksoul.darksride.link.LinkService.clearNotifications(this)
        // App opened: floating cards step aside (the app shows it all); closed: they come back.
        AppVisibility.onChange = { open ->
            if (open) RideOverlay.hideAll()
            RideCall.refresh(this)
        }
    }
}
