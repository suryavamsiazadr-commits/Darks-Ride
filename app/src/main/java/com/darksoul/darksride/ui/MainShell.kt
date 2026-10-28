package com.darksoul.darksride.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import com.darksoul.darksride.AppMode
import com.darksoul.darksride.R
import com.darksoul.darksride.ui.theme.LocalGlass

private enum class Tab(val label: String, @DrawableRes val icon: Int) {
    RIDE("Ride", R.drawable.ic_tab_ride),
    GARAGE("Garage", R.drawable.ic_tab_garage),
    DOCUMENTS("Documents", R.drawable.ic_tab_documents)
}

/** Main phone: Ride (the bike link), Garage (fuel, service, trips) and Documents. */
@Composable
fun MainShell(onChangeRole: () -> Unit) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    // The bottom bar handles the bottom edge itself, so pages only pad the top and sides.
    val pageInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)

    Column(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.weight(1f)) {
            when (Tab.entries[tab]) {
                Tab.RIDE -> LinkScreen(mode = AppMode.MAIN, onChangeRole = onChangeRole, insets = pageInsets)
                Tab.GARAGE -> ComingSoon(
                    "Garage",
                    "Fuel log with your real km/l, expenses, service and document reminders, " +
                        "Trip A / Trip B from GPS, and distance to empty.",
                    pageInsets
                )
                Tab.DOCUMENTS -> ComingSoon(
                    "Documents",
                    "Scan or import your licence, RC, insurance and PUC. Kept encrypted on this " +
                        "phone, opened with your fingerprint, with expiry reminders.",
                    pageInsets
                )
            }
        }
        NavigationBar(containerColor = LocalGlass.current.backgroundBottom) {
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

@Composable
private fun ComingSoon(title: String, text: String, insets: WindowInsets) {
    RideColumn(insets = insets) {
        Title(title)
        Card {
            Text("Coming in the next update", color = MaterialTheme.colorScheme.primary)
            Body(text)
        }
    }
}
