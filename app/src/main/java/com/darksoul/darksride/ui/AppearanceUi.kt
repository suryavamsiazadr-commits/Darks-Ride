package com.darksoul.darksride.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.darksoul.darksride.ui.theme.RidePalette
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
    val palette by ThemeController.palette.collectAsState()
    val now = if (dark) "dark" else "light"

    CollapsibleCard(
        title = "Appearance",
        stateKey = "appearance",
        defaultOpen = false,
        summary = "${palette.label} · ${choice.label} · now $now"
    ) {
        Text("Ride theme", fontSize = 17.sp, fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface)
        RidePalette.entries.chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                pair.forEach { p ->
                    PaletteTile(p, selected = p == palette, dark = dark, modifier = Modifier.weight(1f)) {
                        ThemeController.setPalette(context, p)
                    }
                }
            }
        }
        Text("Light or dark", fontSize = 17.sp, fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(top = 6.dp))
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

/** One theme: a glowing swatch in its colours, the name and what it's good for. */
@Composable
private fun PaletteTile(p: RidePalette, selected: Boolean, dark: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val accent = if (dark) p.dark else p.light
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier = modifier
            .clip(shape)
            .background(accent.copy(alpha = if (selected) 0.16f else 0.06f))
            .border(if (selected) 2.dp else 1.dp, if (selected) accent else colors.onSurface.copy(alpha = 0.12f), shape)
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(36.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Brush.horizontalGradient(listOf(accent, p.second))),
            contentAlignment = Alignment.CenterEnd
        ) {
            if (selected) Text("✓ ", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.Black)
        }
        Text(p.label, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = colors.onSurface)
        Text(p.tagline, fontSize = 12.sp, color = colors.onSurface.copy(alpha = 0.65f), maxLines = 2)
    }
}
