package com.darksoul.darksride.ui.theme

import android.content.Context
import android.content.res.Configuration
import android.os.Handler
import android.os.Looper
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** How the rider wants the theme chosen. */
enum class ThemeChoice(val label: String) {
    AUTO("Auto (light sensor)"),
    DARK("Always dark"),
    LIGHT("Always light")
}

/**
 * Light or dark, like the bike's dash: in Auto it follows the phone's light sensor.
 * Bright sun → light theme (dark text on white, easiest to read); tunnel or night → dark.
 * A change only happens after the light has stayed on the new side for 3 seconds,
 * so riding under trees doesn't make the screen flicker.
 */
object ThemeController {
    private const val PREFS = "darks_ride"
    private const val KEY = "theme_choice"

    /** Above this it's daylight outside (lux): switch to light. */
    private const val BRIGHT_LUX = 2_000f
    /** Below this it's a tunnel, indoors or night: switch to dark. */
    private const val DIM_LUX = 400f
    private const val SETTLE_MS = 3_000L

    private val main = Handler(Looper.getMainLooper())

    private val _choice = MutableStateFlow(ThemeChoice.AUTO)
    val choice: StateFlow<ThemeChoice> = _choice.asStateFlow()

    private val _isDark = MutableStateFlow(true)
    val isDark: StateFlow<Boolean> = _isDark.asStateFlow()

    /** Last light reading, for the Appearance card. Null until the sensor reports. */
    private val _lux = MutableStateFlow<Float?>(null)
    val lux: StateFlow<Float?> = _lux.asStateFlow()

    private var initialised = false
    private var pendingDark: Boolean? = null
    private val settle = Runnable {
        val want = pendingDark ?: return@Runnable
        val now = _lux.value ?: return@Runnable
        if (wantDark(now) == want) _isDark.value = want
        pendingDark = null
    }

    /** Called when a screen opens: loads the saved choice and a sensible first guess. */
    fun init(c: Context) {
        if (initialised) return
        initialised = true
        val saved = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)
        _choice.value = ThemeChoice.entries.firstOrNull { it.name == saved } ?: ThemeChoice.AUTO
        val systemDark = (c.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
        _isDark.value = when (_choice.value) {
            ThemeChoice.DARK -> true
            ThemeChoice.LIGHT -> false
            ThemeChoice.AUTO -> systemDark
        }
    }

    fun setChoice(c: Context, choice: ThemeChoice) {
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit { putString(KEY, choice.name) }
        _choice.value = choice
        main.removeCallbacks(settle)
        pendingDark = null
        when (choice) {
            ThemeChoice.DARK -> _isDark.value = true
            ThemeChoice.LIGHT -> _isDark.value = false
            ThemeChoice.AUTO -> _lux.value?.let { lux -> wantDark(lux)?.let { _isDark.value = it } }
        }
    }

    /** A new light-sensor reading (main thread). */
    fun onLux(lux: Float) {
        _lux.value = lux
        if (_choice.value != ThemeChoice.AUTO) return
        val want = wantDark(lux)
        if (want == null || want == _isDark.value) {
            // In the middle band, or already right: nothing to change.
            main.removeCallbacks(settle)
            pendingDark = null
            return
        }
        if (pendingDark != want) {
            pendingDark = want
            main.removeCallbacks(settle)
            main.postDelayed(settle, SETTLE_MS)
        }
    }

    /** True = dark, false = light, null = in between (keep what we have). */
    private fun wantDark(lux: Float): Boolean? = when {
        lux >= BRIGHT_LUX -> false
        lux <= DIM_LUX -> true
        else -> null
    }
}
