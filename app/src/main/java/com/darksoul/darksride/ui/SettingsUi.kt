package com.darksoul.darksride.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import com.darksoul.darksride.AppMode

/**
 * Settings: everything you set once and rarely touch, so the Ride page stays clean.
 * Main phone: a tab. Ride phone: the gear on the Ride page (Back returns).
 */
@Composable
fun SettingsScreen(
    mode: AppMode,
    onChangeRole: () -> Unit,
    onBack: (() -> Unit)? = null,
    insets: WindowInsets = WindowInsets.safeDrawing
) {
    if (onBack != null) BackHandler(onBack = onBack)
    RideColumn(insets = insets) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (onBack != null) TextButton(onClick = onBack) { Text("‹ Back") }
            Title("Settings")
        }

        SetupCard(mode)
        if (mode == AppMode.MAIN) {
            NotificationFilterCard()
        } else {
            SmallScreenCard()
        }
        NavHistoryCard(mode)
        LinkCard(mode)
        AppearanceCard()
        if (mode == AppMode.MAIN) BackupCard()
        ActivityCard()

        TextButton(onClick = onChangeRole) { Text("Change this phone's role") }
        if (mode == AppMode.RIDE) VersionLabel()
    }
}
