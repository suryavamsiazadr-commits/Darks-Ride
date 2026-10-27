package com.darksoul.darksride.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Always dark: the app is used on a bike, often at night.
private val RideColorScheme = darkColorScheme(
    primary = Ember,
    onPrimary = Color.Black,
    secondary = Ember,
    onSecondary = Color.Black,
    background = Night,
    onBackground = Ash,
    surface = NightSurface,
    onSurface = Ash
)

@Composable
fun DarksRideTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = RideColorScheme,
        typography = Typography,
        content = content
    )
}
