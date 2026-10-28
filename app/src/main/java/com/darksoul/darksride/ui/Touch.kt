package com.darksoul.darksride.ui

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView

/**
 * A ride button: reacts the moment a finger (or glove) touches it, not when it lifts,
 * with a short vibration so you feel it worked. A touch that drifts a little on a bumpy
 * road still counts, and it never gets mistaken for a scroll.
 *
 * @param repeat keep firing while held (volume): first repeat after 0.4 s, then every 0.15 s
 */
fun Modifier.rideTap(repeat: Boolean = false, onTap: () -> Unit): Modifier = composed {
    val view = LocalView.current
    val action by rememberUpdatedState(onTap)
    pointerInput(repeat) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            down.consume()
            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            action()
            if (repeat) {
                var wait = 400L
                while (true) {
                    // Released (or cancelled) before the timeout: stop. Otherwise fire again.
                    val released = withTimeoutOrNull(wait) {
                        waitForUpOrCancellation()
                        true
                    }
                    if (released != null) break
                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    action()
                    wait = 150L
                }
            } else {
                // Swallow the rest of this touch so it can't scroll the page.
                do {
                    val event = awaitPointerEvent()
                    event.changes.forEach { it.consume() }
                } while (event.changes.any { it.pressed })
            }
        }
    }
}
