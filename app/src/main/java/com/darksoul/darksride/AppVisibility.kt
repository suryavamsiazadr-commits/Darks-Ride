package com.darksoul.darksride

import android.app.Activity
import android.app.Application
import android.os.Bundle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Is the full Dark's Ride screen showing? Pop-ups (calls, messages, destinations) then stay
 * inside the app instead of floating over it. The small-screen ride panel doesn't count:
 * Maps is on the main screen then, so the pop-ups are still wanted there.
 */
object AppVisibility {
    private val _open = MutableStateFlow(false)
    val open: StateFlow<Boolean> = _open.asStateFlow()
    private var started = 0
    private var tracking = false

    /** Called whenever it changes (main thread). */
    var onChange: ((Boolean) -> Unit)? = null

    fun track(app: Application) {
        if (tracking) return
        tracking = true
        app.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityStarted(a: Activity) {
                if (a !is MainActivity) return
                started++
                set(true)
            }
            override fun onActivityStopped(a: Activity) {
                if (a !is MainActivity) return
                started = (started - 1).coerceAtLeast(0)
                if (started == 0) set(false)
            }
            override fun onActivityCreated(a: Activity, b: Bundle?) = Unit
            override fun onActivityResumed(a: Activity) = Unit
            override fun onActivityPaused(a: Activity) = Unit
            override fun onActivitySaveInstanceState(a: Activity, b: Bundle) = Unit
            override fun onActivityDestroyed(a: Activity) = Unit
        })
    }

    private fun set(v: Boolean) {
        if (_open.value == v) return
        _open.value = v
        onChange?.invoke(v)
    }
}
