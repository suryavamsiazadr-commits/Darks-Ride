package com.darksoul.darksride

import android.app.Application
import com.darksoul.darksride.link.LinkState

/** Runs first whenever the app starts (screen, service or boot): loads the activity log. */
class DarksRideApp : Application() {
    override fun onCreate() {
        super.onCreate()
        LinkState.init(this)
    }
}
