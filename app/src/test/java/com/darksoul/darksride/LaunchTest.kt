package com.darksoul.darksride

import android.Manifest
import android.app.Application
import android.content.Context
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.darksoul.darksride.link.LinkState
import com.darksoul.darksride.link.LinkStatus
import com.darksoul.darksride.media.RideMedia
import com.darksoul.darksride.ui.theme.ThemeController
import com.darksoul.darksride.ui.theme.UiStyle
import org.json.JSONObject
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowLooper

/**
 * Opens the real app (MainActivity) as each phone, in both styles, portrait and landscape,
 * and fails if anything crashes on the way up.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LaunchTest {
    private val ctx: Context get() = ApplicationProvider.getApplicationContext()

    private fun launch(mode: AppMode?, style: UiStyle, linked: Boolean = false) {
        shadowOf(ctx as Application).grantPermissions(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.POST_NOTIFICATIONS)
        AppPrefs.setMode(ctx, mode)
        ThemeController.init(ctx)
        ThemeController.setStyle(ctx, style)
        if (linked) {
            LinkState.setStatus(LinkStatus.CONNECTED, "Galaxy S25 Ultra")
            RideMedia.update(JSONObject().put("t", "media").put("app", "YouTube Music").put("title", "Song")
                .put("artist", "Artist").put("playing", true).put("vol", 5).put("volMax", 15)
                .put("pos", 10_000L).put("dur", 200_000L))
        } else {
            LinkState.setStatus(LinkStatus.STOPPED)
            RideMedia.clear()
        }
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            ShadowLooper.idleMainLooper(3, java.util.concurrent.TimeUnit.SECONDS)
            scenario.onActivity { }
        }
        assertNull("App crashed", CrashCatcher.lastCrash(ctx))
    }

    @Test fun firstStart() = launch(null, UiStyle.GLASS)
    @Test fun mainGlass() = launch(AppMode.MAIN, UiStyle.GLASS)
    @Test fun mainSoft() = launch(AppMode.MAIN, UiStyle.SOFT)
    @Test fun rideGlass() = launch(AppMode.RIDE, UiStyle.GLASS)
    @Test fun rideSoftLinked() = launch(AppMode.RIDE, UiStyle.SOFT, linked = true)
    @Test fun rideGlassLinked() = launch(AppMode.RIDE, UiStyle.GLASS, linked = true)

    @Test @Config(qualifiers = "w937dp-h411dp-land")
    fun rideLandscapeLinked() = launch(AppMode.RIDE, UiStyle.GLASS, linked = true)

    @Test @Config(qualifiers = "w465dp-h411dp-land")
    fun rideCompactLinkedSoft() = launch(AppMode.RIDE, UiStyle.SOFT, linked = true)

    @Test @Config(qualifiers = "w300dp-h411dp-port")
    fun rideThirdLinked() = launch(AppMode.RIDE, UiStyle.GLASS, linked = true)
}
