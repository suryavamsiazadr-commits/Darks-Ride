package com.darksoul.darksride.panel

import android.app.Activity
import android.hardware.display.DisplayManager
import android.os.Bundle
import android.view.Display
import java.lang.ref.WeakReference
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.darksoul.darksride.AppMode
import com.darksoul.darksride.link.LinkState
import com.darksoul.darksride.link.LinkStatus
import com.darksoul.darksride.ui.RideColumn
import com.darksoul.darksride.ui.RideContent
import com.darksoul.darksride.ui.Header
import com.darksoul.darksride.ui.theme.DarksRideTheme

/** The ride panel shown on the second screen: calls, destination, music and messages. */
class RidePanelActivity : ComponentActivity() {

    private val displayWatcher = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) = Unit
        override fun onDisplayChanged(displayId: Int) = closeIfOnMainScreen()
        override fun onDisplayRemoved(displayId: Int) = closeIfOnMainScreen()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // The panel only belongs on the small screen; never cover Maps on the main one.
        if (display?.displayId == Display.DEFAULT_DISPLAY) {
            finish()
            return
        }
        open = WeakReference(this)
        getSystemService(DisplayManager::class.java).registerDisplayListener(displayWatcher, null)
        setShowWhenLocked(true)
        enableEdgeToEdge()
        setContent {
            DarksRideTheme {
                PanelScreen(onClose = { finish() })
            }
        }
    }

    override fun onResume() {
        super.onResume()
        closeIfOnMainScreen()
    }

    override fun onDestroy() {
        runCatching { getSystemService(DisplayManager::class.java).unregisterDisplayListener(displayWatcher) }
        if (open?.get() === this) open = null
        super.onDestroy()
    }

    /** Swivelled back (small screen gone): Android moves us to the main screen, so close. */
    private fun closeIfOnMainScreen() {
        if (!isFinishing && display?.displayId == Display.DEFAULT_DISPLAY) finish()
    }

    companion object {
        private var open: WeakReference<RidePanelActivity>? = null

        fun closeOpenPanel() {
            open?.get()?.finish()
            open = null
        }
    }
}

/** Lets the ride panel send the full Dark's Ride screen behind Google Maps. */
object RideMainHandle {
    private var main: WeakReference<Activity>? = null

    fun attach(activity: Activity) {
        main = WeakReference(activity)
    }

    fun detach(activity: Activity) {
        if (main?.get() === activity) main = null
    }

    fun sendToBack() {
        main?.get()?.moveTaskToBack(true)
    }
}

@Composable
private fun PanelScreen(onClose: () -> Unit) {
    val status by LinkState.status.collectAsState()
    val peer by LinkState.peerName.collectAsState()

    RideColumn {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.weight(1f)) {
                Header(title = "Dark's Ride", status = status, peer = peer, mode = AppMode.RIDE)
            }
            TextButton(onClick = onClose) { Text("Close") }
        }
        if (status == LinkStatus.CONNECTED) RideContent()
    }
}
