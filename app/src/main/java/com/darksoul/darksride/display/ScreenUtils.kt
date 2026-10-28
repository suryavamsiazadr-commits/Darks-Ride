package com.darksoul.darksride.display

import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.hardware.display.DisplayManager
import android.view.Display
import com.darksoul.darksride.SecondScreenActivity

/** One screen (display) the phone reports. Confirmed on the LG Wing: main = 0, small = 4. */
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

/** Opens the test page on another screen and says what happened. Used again in step 7. */
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
