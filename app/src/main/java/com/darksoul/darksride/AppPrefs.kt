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

    // ---- Which notifications go to the bike (main phone) ----
    const val NOTIFY_WHATSAPP = "notify_whatsapp"
    const val NOTIFY_SMS = "notify_sms"
    const val NOTIFY_OTHER = "notify_other"
    const val NOTIFY_SHOW_TEXT = "notify_show_text"
    /** Ride screen: open the ride panel on a second screen by itself. */
    const val PANEL_AUTO = "panel_auto"

    private val FLAG_DEFAULTS = mapOf(
        NOTIFY_WHATSAPP to true,
        NOTIFY_SMS to true,
        NOTIFY_OTHER to false,
        NOTIFY_SHOW_TEXT to true,
        PANEL_AUTO to true
    )

    fun flag(c: Context, key: String): Boolean =
        prefs(c).getBoolean(key, FLAG_DEFAULTS[key] ?: false)

    fun setFlag(c: Context, key: String, value: Boolean) = prefs(c).edit {
        putBoolean(key, value)
    }

    // ---- Destination shared while the bike was not connected (main phone) ----
    private const val KEY_PENDING_NAV = "pending_nav"
    private const val KEY_PENDING_NAV_TIME = "pending_nav_time"
    private const val PENDING_NAV_MAX_AGE = 3 * 60 * 60 * 1000L // 3 hours

    fun setPendingNav(c: Context, sharedText: String) = prefs(c).edit {
        putString(KEY_PENDING_NAV, sharedText)
        putLong(KEY_PENDING_NAV_TIME, System.currentTimeMillis())
    }

    /** Returns the waiting destination (if still fresh) and forgets it. */
    fun takePendingNav(c: Context): String? {
        val p = prefs(c)
        val text = p.getString(KEY_PENDING_NAV, null) ?: return null
        val age = System.currentTimeMillis() - p.getLong(KEY_PENDING_NAV_TIME, 0L)
        p.edit { remove(KEY_PENDING_NAV); remove(KEY_PENDING_NAV_TIME) }
        return if (age <= PENDING_NAV_MAX_AGE) text else null
    }

    /** Everything the link needs to start without the rider opening the app. */
    fun readyToLink(c: Context): Boolean {
        val mode = mode(c) ?: return false
        return mode == AppMode.RIDE || rideAddress(c) != null
    }
}
