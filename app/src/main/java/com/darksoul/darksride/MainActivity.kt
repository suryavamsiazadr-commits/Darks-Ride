package com.darksoul.darksride

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Process
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
import com.darksoul.darksride.ui.ModeChoiceScreen
import com.darksoul.darksride.ui.theme.DarksRideTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) {
            // Opening the app is the "on" switch: run again, and keep running after restarts.
            AppPrefs.setLinkEnabled(this, true)
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
                AppRoot(onTurnOff = ::turnOff)
            }
        }
    }

    /** The off switch: stop everything, close the app, and end its process. */
    private fun turnOff() {
        LinkService.turnOff(this)
        finishAndRemoveTask()
        // Give the link a moment to close cleanly, then make sure nothing is left running.
        Handler(Looper.getMainLooper()).postDelayed({ Process.killProcess(Process.myPid()) }, 700)
    }
}

@Composable
fun AppRoot(onTurnOff: () -> Unit) {
    val context = LocalContext.current
    var mode by remember { mutableStateOf(AppPrefs.mode(context)) }

    when (val m = mode) {
        null -> ModeChoiceScreen(onPick = { picked ->
            AppPrefs.setMode(context, picked)
            AppPrefs.setLinkEnabled(context, true)
            mode = picked
        })
        else -> LinkScreen(
            mode = m,
            onChangeRole = {
                LinkService.stop(context)
                LinkState.clear()
                AppPrefs.setMode(context, null)
                mode = null
            },
            onTurnOff = onTurnOff
        )
    }
}
