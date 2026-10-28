package com.darksoul.darksride.ui.theme

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

/** "Glass-lite": translucent cards with a bright top edge and soft glow, no real blur. */
@Immutable
data class GlassColors(
    val isDark: Boolean,
    val backgroundTop: Color,
    val backgroundBottom: Color,
    /** Card body (translucent). */
    val card: Color,
    /** Light catching the top of a card. */
    val cardHighlight: Color,
    val cardBorderTop: Color,
    val cardBorderBottom: Color,
    /** Soft glow under main buttons. */
    val glow: Color
)

// True black (no blue or grey tint): kind to OLED screens and to the eyes at night.
private val DarkGlass = GlassColors(
    isDark = true,
    backgroundTop = Color(0xFF000000),
    backgroundBottom = Color(0xFF050505),
    card = Color(0x0DFFFFFF),
    cardHighlight = Color(0x0AFFFFFF),
    cardBorderTop = Color(0x2EFFFFFF),
    cardBorderBottom = Color(0x0AFFFFFF),
    glow = Ember.copy(alpha = 0.55f)
)

private val LightGlass = GlassColors(
    isDark = false,
    backgroundTop = Color(0xFFF6F6F5),
    backgroundBottom = Color(0xFFE4E4E2),
    card = Color(0xCCFFFFFF),
    cardHighlight = Color(0xB3FFFFFF),
    cardBorderTop = Color(0xFFFFFFFF),
    cardBorderBottom = Color(0x1A000000),
    glow = EmberDeep.copy(alpha = 0.40f)
)

val LocalGlass = staticCompositionLocalOf { DarkGlass }

private val DarkScheme = darkColorScheme(
    primary = Ember,
    onPrimary = Color.Black,
    secondary = Ember,
    onSecondary = Color.Black,
    background = Color(0xFF000000),
    onBackground = Ash,
    surface = Color(0x0DFFFFFF),
    onSurface = Ash,
    surfaceContainer = Color(0xFF0E0E0E),
    surfaceContainerHigh = Color(0xFF141414),
    surfaceContainerHighest = Color(0xFF1A1A1A),
    surfaceVariant = Color(0xFF1A1A1A)
)

private val LightScheme = lightColorScheme(
    primary = EmberDeep,
    onPrimary = Color.White,
    secondary = EmberDeep,
    onSecondary = Color.White,
    background = Color(0xFFF6F6F5),
    onBackground = Ink,
    surface = Color(0xCCFFFFFF),
    onSurface = Ink,
    surfaceContainerHigh = Color(0xFFFFFFFF)
)

@Composable
fun DarksRideTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    ThemeController.init(context)
    LightSensor()
    val dark by ThemeController.isDark.collectAsState()
    CompositionLocalProvider(LocalGlass provides if (dark) DarkGlass else LightGlass) {
        MaterialTheme(
            colorScheme = if (dark) DarkScheme else LightScheme,
            typography = Typography,
            content = content
        )
    }
}

/** Feeds the phone's light sensor to the theme while a Dark's Ride screen is showing. */
@Composable
private fun LightSensor() {
    val context = LocalContext.current
    val choice by ThemeController.choice.collectAsState()
    DisposableEffect(choice) {
        val sensors = context.getSystemService(SensorManager::class.java)
        val light = sensors?.getDefaultSensor(Sensor.TYPE_LIGHT)
        if (choice != ThemeChoice.AUTO || sensors == null || light == null) {
            onDispose { }
        } else {
            val listener = object : SensorEventListener {
                override fun onSensorChanged(event: SensorEvent) {
                    ThemeController.onLux(event.values[0])
                }
                override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
            }
            sensors.registerListener(listener, light, SensorManager.SENSOR_DELAY_NORMAL)
            onDispose { sensors.unregisterListener(listener) }
        }
    }
}
