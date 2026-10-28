package com.darksoul.darksride.panel

import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.hardware.display.DisplayManager
import android.provider.Settings
import android.view.Display
import com.darksoul.darksride.AppPrefs
import com.darksoul.darksride.link.LinkState

/**
 * The ride panel on a second screen. On the LG Wing in Swivel mode, the small screen is
 * reported as an extra display (id 4 on the Wing), so Maps keeps the big screen and the
 * panel shows music, messages, calls and the destination below it.
 */
object RidePanel {

    /** The first screen that isn't the main one, or null on single-screen phones. */
    fun secondaryDisplayId(c: Context): Int? =
        c.getSystemService(DisplayManager::class.java).displays
            .firstOrNull { it.displayId != Display.DEFAULT_DISPLAY && it.state != Display.STATE_OFF }
            ?.displayId

    /**
     * Opens the panel on the second screen. Returns false if there is none or it failed.
     * Always a fresh window on the small screen: reusing the old one made Android bring it
     * back wherever it last was (often the main screen, on top of Maps).
     */
    fun open(c: Context): Boolean {
        val id = secondaryDisplayId(c) ?: return false
        return try {
            RidePanelActivity.closeOpenPanel()
            val options = ActivityOptions.makeBasic().setLaunchDisplayId(id)
            val intent = Intent(c, RidePanelActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
            c.startActivity(intent, options.toBundle())
            // Dark's Ride steps back on the main screen, so Google Maps keeps it.
            RideMainHandle.sendToBack()
            true
        } catch (e: Exception) {
            LinkState.log("Could not open the panel on the small screen")
            false
        }
    }

    /**
     * Called when the link connects or a second screen appears. Opening a screen from the
     * background is only allowed with "Display over other apps", which the ride screen asks for.
     */
    fun autoOpen(c: Context) {
        if (!AppPrefs.flag(c, AppPrefs.PANEL_AUTO)) return
        if (!Settings.canDrawOverlays(c)) return
        if (secondaryDisplayId(c) == null) return
        if (open(c)) LinkState.log("Ride panel opened on the small screen")
    }
}
