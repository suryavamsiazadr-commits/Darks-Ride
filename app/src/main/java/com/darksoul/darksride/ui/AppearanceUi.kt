package com.darksoul.darksride.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.sp
import com.darksoul.darksride.ui.theme.ThemeChoice
import com.darksoul.darksride.ui.theme.ThemeController

/** Light / dark: automatic from the light sensor (like the bike's dash), or fixed. */
@Composable
fun AppearanceCard() {
    val context = LocalContext.current
    val choice by ThemeController.choice.collectAsState()
    val dark by ThemeController.isDark.collectAsState()
    val lux by ThemeController.lux.collectAsState()
    val now = if (dark) "dark" else "light"

    CollapsibleCard(
        title = "Appearance",
        stateKey = "appearance",
        defaultOpen = false,
        summary = "${choice.label} · now $now"
    ) {
        ThemeChoice.entries.forEach { option ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .selectable(
                        selected = option == choice,
                        role = Role.RadioButton,
                        onClick = { ThemeController.setChoice(context, option) }
                    ),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(selected = option == choice, onClick = null)
                Text(option.label, fontSize = 17.sp, color = MaterialTheme.colorScheme.onSurface)
            }
        }
        if (choice == ThemeChoice.AUTO) {
            Body(
                (lux?.let { "Light now: ${it.toInt()} lux. " } ?: "") +
                    "Bright sun switches to light, tunnels and night to dark, after 3 seconds.",
                dim = true
            )
        }
    }
}
