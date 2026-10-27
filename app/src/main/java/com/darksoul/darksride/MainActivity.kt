package com.darksoul.darksride

import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.hardware.display.DisplayManager
import android.os.Bundle
import android.view.Display
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.darksoul.darksride.ui.theme.DarksRideTheme

/** One screen (display) the phone reports. */
data class ScreenInfo(
    val id: Int,
    val name: String,
    val isMain: Boolean,
    val width: Int,
    val height: Int
)

fun readScreens(dm: DisplayManager): List<ScreenInfo> =
    dm.displays.map { d ->
        val mode = d.mode
        ScreenInfo(
            id = d.displayId,
            name = d.name,
            isMain = d.displayId == Display.DEFAULT_DISPLAY,
            width = mode.physicalWidth,
            height = mode.physicalHeight
        )
    }

/** Tries to open the test page on another screen and says what happened. */
fun openOnScreen(context: Context, displayId: Int): String = try {
    val options = ActivityOptions.makeBasic().setLaunchDisplayId(displayId)
    val intent = Intent(context, SecondScreenActivity::class.java)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
    context.startActivity(intent, options.toBundle())
    "Sent to screen $displayId. Check the small screen."
} catch (e: SecurityException) {
    "The phone blocked it: ${e.message}"
} catch (e: Exception) {
    "It failed: ${e.message}"
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            DarksRideTheme {
                WelcomeScreen()
            }
        }
    }
}

@Composable
fun WelcomeScreen() {
    val context = LocalContext.current
    val dm = remember { context.getSystemService(DisplayManager::class.java) }
    var screens by remember { mutableStateOf(readScreens(dm)) }
    var status by remember { mutableStateOf<String?>(null) }

    // Refresh the list when a screen appears or disappears (e.g. swivelling the Wing).
    DisposableEffect(dm) {
        val listener = object : DisplayManager.DisplayListener {
            override fun onDisplayAdded(displayId: Int) { screens = readScreens(dm) }
            override fun onDisplayRemoved(displayId: Int) { screens = readScreens(dm) }
            override fun onDisplayChanged(displayId: Int) { screens = readScreens(dm) }
        }
        dm.registerDisplayListener(listener, null)
        onDispose { dm.unregisterDisplayListener(listener) }
    }

    val colors = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Welcome to Dark's Ride",
            fontSize = 30.sp,
            fontWeight = FontWeight.Bold,
            color = colors.primary
        )
        Text(
            text = "Sample build: checking the screens on this phone",
            fontSize = 16.sp,
            color = colors.onBackground.copy(alpha = 0.7f)
        )

        Spacer(Modifier.height(8.dp))

        Text(
            text = "Screens found: ${screens.size}",
            fontSize = 22.sp,
            fontWeight = FontWeight.SemiBold,
            color = colors.onBackground
        )
        screens.forEach { s ->
            Text(
                text = "Screen ${s.id} · ${if (s.isMain) "Main" else "Extra"} · " +
                    "${s.width}×${s.height} · ${s.name}",
                fontSize = 16.sp,
                color = colors.onBackground
            )
        }

        val extras = screens.filterNot { it.isMain }
        if (extras.isEmpty()) {
            Text(
                text = "Only one screen right now. On the LG Wing, swivel it open " +
                    "and this list updates by itself.",
                fontSize = 16.sp,
                color = colors.onBackground.copy(alpha = 0.7f)
            )
        } else {
            extras.forEach { s ->
                Button(
                    onClick = { status = openOnScreen(context, s.id) },
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                ) {
                    Text("Open test page on screen ${s.id}", fontSize = 18.sp)
                }
            }
        }

        status?.let {
            Text(text = it, fontSize = 16.sp, color = colors.primary)
        }
    }
}
