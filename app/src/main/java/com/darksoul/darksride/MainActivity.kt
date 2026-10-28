package com.darksoul.darksride

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.fragment.app.FragmentActivity
import com.darksoul.darksride.link.LinkService
import com.darksoul.darksride.link.LinkState
import com.darksoul.darksride.panel.RideMainHandle
import com.darksoul.darksride.phone.PhoneListenerService
import com.darksoul.darksride.ui.LinkScreen
import com.darksoul.darksride.ui.MainShell
import com.darksoul.darksride.ui.ModeChoiceScreen
import com.darksoul.darksride.ui.SettingsScreen
import com.darksoul.darksride.ui.theme.DarksRideTheme

/** FragmentActivity (a ComponentActivity) because the fingerprint prompt needs it. */
class MainActivity : FragmentActivity() {
    override fun onResume() {
        super.onResume()
        if (AppPrefs.mode(this) == AppMode.RIDE) RideMainHandle.attach(this)
    }

    override fun onPause() {
        RideMainHandle.detach(this)
        super.onPause()
    }

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
    var rideSettings by rememberSaveable { mutableStateOf(false) }

    val changeRole = {
        LinkService.stop(context)
        LinkState.clear()
        AppPrefs.setMode(context, null)
        rideSettings = false
        mode = null
    }

    when (mode) {
        null -> ModeChoiceScreen(onPick = { picked ->
            AppPrefs.setMode(context, picked)
            AppPrefs.setLinkEnabled(context, true)
            mode = picked
        })
        AppMode.MAIN -> MainShell(onChangeRole = changeRole)
        AppMode.RIDE ->
            if (rideSettings) {
                SettingsScreen(mode = AppMode.RIDE, onChangeRole = changeRole, onBack = { rideSettings = false })
            } else {
                LinkScreen(mode = AppMode.RIDE, onOpenSettings = { rideSettings = true })
            }
    }
}
