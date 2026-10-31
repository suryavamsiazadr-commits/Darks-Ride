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
    val glow: Color,
    /** Shadow under cards (light theme: cards float on the page). */
    val cardShadow: Color = Color.Transparent,
    /** Warm light in the top corner of the page. */
    val ambient: Color = Color.Transparent,
    /** The faint specks behind the page: stars at night, dust in daylight. */
    val speck: Color = Color.White,
    /** Second colour of the theme, for gradients (music card). */
    val accent2: Color = Color.White,
    /** Neumorphism ("Soft"): cards are the page colour, raised by a light and a dark shadow. */
    val soft: Boolean = false,
    val softLight: Color = Color.Transparent,
    val softDark: Color = Color.Transparent
)

/** Neumorphism: one calm base colour; light from the top left, shade to the bottom right. */
private fun softDark(p: RidePalette) = GlassColors(
    isDark = true,
    backgroundTop = Color(0xFF23262C),
    backgroundBottom = Color(0xFF23262C),
    card = Color(0xFF23262C),
    cardHighlight = Color.Transparent,
    cardBorderTop = Color.Transparent,
    cardBorderBottom = Color.Transparent,
    glow = p.dark.copy(alpha = 0.45f),
    accent2 = p.second,
    soft = true,
    softLight = Color(0x1FFFFFFF),
    softDark = Color(0xB3000000)
)

private fun softLight(p: RidePalette) = GlassColors(
    isDark = false,
    backgroundTop = Color(0xFFE3E7ED),
    backgroundBottom = Color(0xFFE3E7ED),
    card = Color(0xFFE3E7ED),
    cardHighlight = Color.Transparent,
    cardBorderTop = Color.Transparent,
    cardBorderBottom = Color.Transparent,
    glow = p.light.copy(alpha = 0.35f),
    accent2 = p.light,
    speck = Color.Transparent,
    soft = true,
    softLight = Color(0xFFFFFFFF),
    softDark = Color(0x8C9AA8BE)
)

// True black (no blue or grey tint): kind to OLED screens and to the eyes at night.
private fun darkGlass(p: RidePalette) = GlassColors(
    isDark = true,
    backgroundTop = Color(0xFF000000),
    backgroundBottom = Color(0xFF050505),
    card = Color(0x0DFFFFFF),
    cardHighlight = Color(0x0AFFFFFF),
    cardBorderTop = Color(0x2EFFFFFF),
    cardBorderBottom = Color(0x0AFFFFFF),
    glow = p.dark.copy(alpha = 0.55f),
    accent2 = p.second
)

// Daylight glass: pearl page with a soft glow in the theme colour, frosted white cards with a
// bright glossy top and a soft shadow, so the same glass feel reads in sunlight.
private fun lightGlass(p: RidePalette) = GlassColors(
    isDark = false,
    backgroundTop = Color(0xFFFDFBF9),
    backgroundBottom = Color(0xFFE7E3DF),
    card = Color(0xB3FFFFFF),
    cardHighlight = Color(0xF2FFFFFF),
    cardBorderTop = Color(0xFFFFFFFF),
    cardBorderBottom = Color(0x1F3A2A20),
    glow = p.light.copy(alpha = 0.45f),
    cardShadow = Color(0x403A2A20),
    ambient = p.dark.copy(alpha = 0.16f),
    speck = Color(0xFF5A4A40),
    accent2 = p.light
)

val LocalGlass = staticCompositionLocalOf { darkGlass(RidePalette.EMBER) }

private fun darkScheme(p: RidePalette) = darkColorScheme(
    primary = p.dark,
    onPrimary = Color.Black,
    secondary = p.second,
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

private fun lightScheme(p: RidePalette) = lightColorScheme(
    primary = p.light,
    onPrimary = Color.White,
    secondary = p.light,
    onSecondary = Color.White,
    background = Color(0xFFF6F6F5),
    onBackground = Ink,
    surface = Color(0xCCFFFFFF),
    onSurface = Ink,
    surfaceContainer = Color(0xFFF7F4F1),
    surfaceContainerHigh = Color(0xFFFFFFFF),
    surfaceContainerHighest = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFEFEAE5),
    outline = Color(0xFFCDBFB3)
)

@Composable
fun DarksRideTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    ThemeController.init(context)
    LightSensor()
    val dark by ThemeController.isDark.collectAsState()
    val palette by ThemeController.palette.collectAsState()
    val style by ThemeController.style.collectAsState()
    val soft = style == UiStyle.SOFT
    val glass = when {
        soft && dark -> softDark(palette)
        soft -> softLight(palette)
        dark -> darkGlass(palette)
        else -> lightGlass(palette)
    }
    val scheme = (if (dark) darkScheme(palette) else lightScheme(palette)).let {
        if (soft) it.copy(background = glass.card, surface = glass.card, surfaceContainer = glass.card,
            surfaceContainerHigh = glass.card, surfaceContainerHighest = glass.card) else it
    }
    CompositionLocalProvider(LocalGlass provides glass) {
        MaterialTheme(
            colorScheme = scheme,
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
