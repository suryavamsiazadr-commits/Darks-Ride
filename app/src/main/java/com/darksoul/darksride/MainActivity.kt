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
import com.darksoul.darksride.ui.LinkScreen
import com.darksoul.darksride.ui.ModeChoiceScreen
import com.darksoul.darksride.ui.theme.DarksRideTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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

    when (val m = mode) {
        null -> ModeChoiceScreen(onPick = { picked ->
            AppPrefs.setMode(context, picked)
            mode = picked
        })
        else -> LinkScreen(mode = m, onChangeRole = {
            LinkService.stop(context)
            LinkState.clear()
            AppPrefs.setMode(context, null)
            mode = null
        })
    }
}
