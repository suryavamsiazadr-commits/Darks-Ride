package com.darksoul.darksride

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.darksoul.darksride.link.LinkService
import com.darksoul.darksride.link.LinkState
import com.darksoul.darksride.phone.PhoneListenerService
import com.darksoul.darksride.ui.LinkScreen
import com.darksoul.darksride.ui.MainShell
import com.darksoul.darksride.ui.ModeChoiceScreen
import com.darksoul.darksride.ui.theme.DarksRideTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Ride mode decides what runs in the background; opening the app doesn't change it.
        if (savedInstanceState == null && AppPrefs.linkEnabled(this)) {
            PhoneListenerService.resume(this)
        }
        // Ride screen: a call can open the app over the lock screen and wake the display.
        if (AppPrefs.mode(this) == AppMode.RIDE) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }
        enableEdgeToEdge()
        setContent {
            DarksRideTheme {
                AppRoot()
            }
        }
    }
}

@Composable
fun AppRoot() {
    val context = LocalContext.current
    var mode by remember { mutableStateOf(AppPrefs.mode(context)) }

    val changeRole = {
        LinkService.stop(context)
        LinkState.clear()
        AppPrefs.setMode(context, null)
        mode = null
    }

    when (mode) {
        null -> ModeChoiceScreen(onPick = { picked ->
            AppPrefs.setMode(context, picked)
            AppPrefs.setLinkEnabled(context, true)
            mode = picked
        })
        AppMode.MAIN -> MainShell(onChangeRole = changeRole)
        AppMode.RIDE -> LinkScreen(mode = AppMode.RIDE, onChangeRole = changeRole)
    }
}
