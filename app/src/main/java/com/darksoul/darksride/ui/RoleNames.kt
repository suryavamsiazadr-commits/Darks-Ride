package com.darksoul.darksride.ui

import com.darksoul.darksride.AppMode

/** Friendly names for the two phones. */
object RoleNames {
    /** The main phone: rides in your pocket and carries everything up to the bike. */
    const val MAIN = "Pocket Sherpa"

    /** The phone on the bike. */
    const val RIDE = "Handlebar HQ"

    fun of(mode: AppMode) = if (mode == AppMode.MAIN) MAIN else RIDE
}
