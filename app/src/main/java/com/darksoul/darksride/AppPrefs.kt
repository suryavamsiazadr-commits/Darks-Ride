package com.darksoul.darksride

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

/** Which job this phone does. */
enum class AppMode { MAIN, RIDE }

/** Small saved settings: the phone's role and, on the main phone, which ride phone to link to. */
object AppPrefs {
    private const val FILE = "darks_ride"
    private const val KEY_MODE = "mode"
    private const val KEY_RIDE_ADDRESS = "ride_address"
    private const val KEY_RIDE_NAME = "ride_name"
    private const val KEY_LINK_ENABLED = "link_enabled"

    private fun prefs(c: Context): SharedPreferences =
        c.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun mode(c: Context): AppMode? =
        prefs(c).getString(KEY_MODE, null)?.let { saved ->
            AppMode.entries.firstOrNull { it.name == saved }
        }

    fun setMode(c: Context, mode: AppMode?) = prefs(c).edit {
        if (mode == null) remove(KEY_MODE) else putString(KEY_MODE, mode.name)
    }

    fun rideAddress(c: Context): String? = prefs(c).getString(KEY_RIDE_ADDRESS, null)

    fun rideName(c: Context): String? = prefs(c).getString(KEY_RIDE_NAME, null)

    fun setRidePhone(c: Context, address: String, name: String) = prefs(c).edit {
        putString(KEY_RIDE_ADDRESS, address)
        putString(KEY_RIDE_NAME, name)
    }

    /** False only after the rider taps "Stop link"; then the link stays off until started again. */
    fun linkEnabled(c: Context): Boolean = prefs(c).getBoolean(KEY_LINK_ENABLED, true)

    fun setLinkEnabled(c: Context, enabled: Boolean) = prefs(c).edit {
        putBoolean(KEY_LINK_ENABLED, enabled)
    }

    /** Everything the link needs to start without the rider opening the app. */
    fun readyToLink(c: Context): Boolean {
        val mode = mode(c) ?: return false
        return mode == AppMode.RIDE || rideAddress(c) != null
    }
}
