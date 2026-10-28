package com.darksoul.darksride.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.darksoul.darksride.AppMode
import com.darksoul.darksride.R
import com.darksoul.darksride.ui.theme.LocalGlass

private enum class Tab(val label: String, @DrawableRes val icon: Int) {
    RIDE("Ride", R.drawable.ic_tab_ride),
    GARAGE("Garage", R.drawable.ic_tab_garage),
    DOCUMENTS("Documents", R.drawable.ic_tab_documents),
    SETTINGS("Settings", R.drawable.ic_settings)
}

/** Main phone: Ride (the bike link), Garage, Documents and Settings. */
@Composable
fun MainShell(onChangeRole: () -> Unit) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    // The bottom bar handles the bottom edge itself, so pages only pad the top and sides.
    val pageInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)
    val bar = LocalGlass.current.backgroundBottom

    Column(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.weight(1f)) {
            when (Tab.entries[tab]) {
                Tab.RIDE -> LinkScreen(mode = AppMode.MAIN, insets = pageInsets)
                Tab.GARAGE -> GarageScreen(insets = pageInsets)
                Tab.DOCUMENTS -> DocumentsScreen(insets = pageInsets)
                Tab.SETTINGS -> SettingsScreen(mode = AppMode.MAIN, onChangeRole = onChangeRole, insets = pageInsets)
            }
        }
        // App version just above the tabs.
        VersionLabel(modifier = Modifier.background(bar).padding(top = 6.dp, bottom = 2.dp))
        NavigationBar(containerColor = bar) {
            Tab.entries.forEachIndexed { i, t ->
                NavigationBarItem(
                    selected = tab == i,
                    onClick = { tab = i },
                    icon = { Icon(painterResource(t.icon), contentDescription = null) },
                    label = { Text(t.label) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.onPrimary,
                        indicatorColor = MaterialTheme.colorScheme.primary
                    )
                )
            }
        }
    }
}
