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
import com.darksoul.darksride.ui.theme.UiStyle
import androidx.compose.foundation.layout.fillMaxWidth
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
    val style by ThemeController.style.collectAsState()
    val now = if (dark) "dark" else "light"

    CollapsibleCard(
        title = "Appearance",
        stateKey = "appearance",
        defaultOpen = false,
        summary = "${style.label} · ${palette.label} · ${choice.label} · now $now"
    ) {
        Text("Style", fontSize = 17.sp, fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            UiStyle.entries.forEach { st ->
                StyleTile(st, selected = st == style, modifier = Modifier.weight(1f)) {
                    ThemeController.setStyle(context, st)
                }
            }
        }
        Text("Ride colours", fontSize = 17.sp, fontWeight = FontWeight.SemiBold,
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

/** Glass or Soft, each drawn in its own look so you can see the difference. */
@Composable
private fun StyleTile(st: UiStyle, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val glass = com.darksoul.darksride.ui.theme.LocalGlass.current
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier = modifier
            .clip(shape)
            .background(colors.primary.copy(alpha = if (selected) 0.14f else 0.04f))
            .border(if (selected) 2.dp else 1.dp, if (selected) colors.primary else colors.onSurface.copy(alpha = 0.12f), shape)
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Mini preview: a card in that style.
        val base = if (glass.isDark) Color(0xFF23262C) else Color(0xFFE3E7ED)
        Box(
            modifier = Modifier.fillMaxWidth().height(44.dp).clip(RoundedCornerShape(12.dp))
                .background(if (st == UiStyle.SOFT) base else Color.Transparent),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier.fillMaxWidth(0.7f).height(26.dp)
                    .then(
                        if (st == UiStyle.SOFT) Modifier.neu(10.dp, 4.dp, base,
                            if (glass.isDark) Color(0x1FFFFFFF) else Color.White,
                            if (glass.isDark) Color(0xB3000000) else Color(0x8C9AA8BE))
                            .clip(RoundedCornerShape(10.dp)).background(base)
                        else Modifier.clip(RoundedCornerShape(10.dp))
                            .background(Brush.verticalGradient(listOf(colors.onSurface.copy(alpha = 0.16f), colors.onSurface.copy(alpha = 0.04f))))
                            .border(1.dp, colors.onSurface.copy(alpha = 0.25f), RoundedCornerShape(10.dp))
                    )
            )
        }
        Text(st.label + if (selected) "  ✓" else "", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = colors.onSurface)
        Text(st.tagline, fontSize = 12.sp, color = colors.onSurface.copy(alpha = 0.65f), maxLines = 2)
    }
}
