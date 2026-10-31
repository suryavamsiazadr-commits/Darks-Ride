package com.darksoul.darksride

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.util.Base64
import androidx.compose.runtime.Composable
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import com.darksoul.darksride.link.LinkState
import com.darksoul.darksride.link.LinkStatus
import com.darksoul.darksride.media.RideMedia
import com.darksoul.darksride.nav.RideNav
import com.darksoul.darksride.phone.RideCall
import com.darksoul.darksride.phone.RideNotifications
import com.darksoul.darksride.ui.InternetCard
import com.darksoul.darksride.ui.RideDashboard
import com.darksoul.darksride.ui.SendToBikeCard
import com.darksoul.darksride.ui.RideColumn
import com.darksoul.darksride.ui.SettingsScreen
import com.darksoul.darksride.ui.theme.DarksRideTheme
import com.darksoul.darksride.ui.theme.RidePalette
import com.darksoul.darksride.ui.theme.ThemeChoice
import com.darksoul.darksride.ui.theme.ThemeController
import com.darksoul.darksride.ui.theme.UiStyle
import org.json.JSONObject
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * Renders the ride screens at real phone sizes so the layout can be checked without a phone.
 * Output: app/build/screens/<name>.png (uploaded by the "screens" CI job).
 *
 * Sizes (dp): LG Wing main screen 411×937 (portrait) / 937×411 (landscape), Wing small screen
 * ~472×411, split screen beside Maps: half ~465×411, third ~300×411. Samsung S25 Ultra 412×891.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ScreenshotTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val ctx: Context get() = ApplicationProvider.getApplicationContext()

    // ---------- Bike phone ----------

    @Test @Config(qualifiers = "w411dp-h891dp-port-xhdpi")
    fun ridePortraitDark() = shoot("ride_portrait_dark") { music(); route(); RideScreen() }

    @Test @Config(qualifiers = "w411dp-h891dp-port-xhdpi")
    fun ridePortraitLightGlacier() = shoot("ride_portrait_light_glacier", dark = false, palette = RidePalette.GLACIER) {
        music(); route(); message(); RideScreen()
    }

    @Test @Config(qualifiers = "w411dp-h891dp-port-xhdpi")
    fun ridePortraitCall() = shoot("ride_portrait_call") { music(); call(); RideScreen() }

    @Test @Config(qualifiers = "w937dp-h411dp-land-xhdpi")
    fun rideWideDark() = shoot("ride_wide_dark") { music(); route(); RideScreen() }

    @Test @Config(qualifiers = "w937dp-h411dp-land-xhdpi")
    fun rideWideMessageRedline() = shoot("ride_wide_message_redline", palette = RidePalette.REDLINE) {
        music(); route(); message(); RideScreen()
    }

    @Test @Config(qualifiers = "w937dp-h411dp-land-xhdpi")
    fun rideWideCall() = shoot("ride_wide_call") { music(); route(); call(); RideScreen() }

    @Test @Config(qualifiers = "w937dp-h411dp-land-xhdpi")
    fun rideWideLightVolt() = shoot("ride_wide_light_volt", dark = false, palette = RidePalette.VOLT) {
        music(); route(); RideScreen()
    }

    @Test @Config(qualifiers = "w472dp-h411dp-land-xhdpi")
    fun wingSmallScreen() = shoot("wing_small_screen") { music(); route(); message(); RideScreen(panel = true) }

    @Test @Config(qualifiers = "w465dp-h411dp-land-xhdpi")
    fun splitHalf() = shoot("split_half") { music(); route(); RideScreen() }

    @Test @Config(qualifiers = "w300dp-h411dp-port-xhdpi")
    fun splitThird() = shoot("split_third") { music(); route(); RideScreen() }

    @Test @Config(qualifiers = "w300dp-h411dp-port-xhdpi")
    fun splitThirdMessage() = shoot("split_third_message") { music(); message(); RideScreen() }

    @Test @Config(qualifiers = "w465dp-h411dp-land-xhdpi")
    fun splitHalfCall() = shoot("split_half_call") { music(); call(); RideScreen() }

    @Test @Config(qualifiers = "w937dp-h411dp-land-xhdpi")
    fun rideWidePaused() = shoot("ride_wide_paused") { pausedMusic(); route(); RideScreen() }

    @Test @Config(qualifiers = "w411dp-h891dp-port-xhdpi")
    fun rideNoMusicYet() = shoot("ride_waiting_music") { RideScreen() }

    // ---------- Soft (neumorphism) and full-screen music ----------

    @Test @Config(qualifiers = "w411dp-h891dp-port-xhdpi")
    fun softPortraitDark() = shoot("soft_portrait_dark", style = UiStyle.SOFT) { music(); route(); message(); RideScreen() }

    @Test @Config(qualifiers = "w411dp-h891dp-port-xhdpi")
    fun softPortraitLight() = shoot("soft_portrait_light", dark = false, style = UiStyle.SOFT) { music(); route(); RideScreen() }

    @Test @Config(qualifiers = "w937dp-h411dp-land-xhdpi")
    fun softWideLight() = shoot("soft_wide_light", dark = false, style = UiStyle.SOFT, palette = RidePalette.GLACIER) {
        music(); route(); RideScreen()
    }

    @Test @Config(qualifiers = "w937dp-h411dp-land-xhdpi")
    fun softWideDark() = shoot("soft_wide_dark", style = UiStyle.SOFT) { music(); route(); RideScreen() }

    @Test @Config(qualifiers = "w465dp-h411dp-land-xhdpi")
    fun fullMusicHalf() = shoot("full_music_half") { music(); route(); RideScreen() }

    @Test @Config(qualifiers = "w300dp-h411dp-port-xhdpi")
    fun fullMusicThirdMessage() = shoot("full_music_third_message") { music(); message(); RideScreen() }

    @Test @Config(qualifiers = "w472dp-h411dp-land-xhdpi")
    fun fullMusicSmallCallSoft() = shoot("full_music_small_call_soft", style = UiStyle.SOFT) { music(); call(); RideScreen(panel = true) }

    @Test @Config(qualifiers = "w300dp-h411dp-port-xhdpi")
    fun fullMusicThirdLight() = shoot("full_music_third_light", dark = false, palette = RidePalette.REDLINE) { music(); RideScreen() }

    @Test @Config(qualifiers = "w412dp-h2200dp-port-xhdpi")
    fun softSettingsDark() = shoot("soft_settings_dark", style = UiStyle.SOFT) {
        SettingsScreen(mode = AppMode.MAIN, onChangeRole = {})
    }

    // ---------- Main phone ----------

    @Test @Config(qualifiers = "w412dp-h891dp-port-xhdpi")
    fun mainRideTab() = shoot("main_ride_tab") {
        LinkState.setStatus(LinkStatus.CONNECTED, "LG Wing")
        RideColumn {
            SendToBikeCard()
            InternetCard(AppMode.MAIN)
        }
    }

    @Test @Config(qualifiers = "w412dp-h2200dp-port-xhdpi")
    fun mainSettingsLight() = shoot("main_settings_light", dark = false) {
        SettingsScreen(mode = AppMode.MAIN, onChangeRole = {})
    }

    @Test @Config(qualifiers = "w411dp-h2200dp-port-xhdpi")
    fun rideSettingsDark() = shoot("ride_settings_dark") {
        SettingsScreen(mode = AppMode.RIDE, onChangeRole = {}, onBack = {})
    }

    // ---------- Helpers ----------

    @Composable
    private fun RideScreen(panel: Boolean = false) {
        if (panel) RideDashboard(title = "Dark's Ride", onClose = {})
        else RideDashboard(title = "Handlebar HQ", onSettings = {}, portraitExtras = {})
    }

    /** Sets the theme and the ride state, renders, waits for the slide-ins, saves a PNG. */
    private fun shoot(
        name: String,
        dark: Boolean = true,
        palette: RidePalette = RidePalette.EMBER,
        style: UiStyle = UiStyle.GLASS,
        content: @Composable () -> Unit
    ) {
        reset()
        com.darksoul.darksride.ui.CardMemory.reset()
        ThemeController.setStyle(ctx, style)
        AppPrefs.setMode(ctx, AppMode.RIDE)
        ThemeController.init(ctx)
        ThemeController.setChoice(ctx, if (dark) ThemeChoice.DARK else ThemeChoice.LIGHT)
        ThemeController.setPalette(ctx, palette)
        LinkState.setStatus(LinkStatus.CONNECTED, "Galaxy S25 Ultra")
        rule.mainClock.autoAdvance = false // the equalizer and ring pulse never stop
        rule.setContent { DarksRideTheme { content() } }
        rule.mainClock.advanceTimeBy(1_500)
        // Draw the window ourselves: the test helper's capture waits for animations to stop.
        val view = rule.activity.window.decorView
        val bitmap = Bitmap.createBitmap(view.width.coerceAtLeast(1), view.height.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        val dir = File("build/screens").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun reset() {
        RideCall.clear(ctx)
        RideNotifications.clear()
        RideMedia.clear()
    }

    private fun music() {
        RideMedia.update(
            JSONObject().put("t", "media").put("app", "YouTube Music").put("title", "Kesariya (From \"Brahmastra\")")
                .put("artist", "Pritam, Arijit Singh").put("playing", true).put("vol", 9).put("volMax", 15)
                .put("pos", 83_000L).put("dur", 268_000L)
                .put("artNew", true).put("art", fakeArt())
        )
    }

    private fun pausedMusic() {
        RideMedia.update(JSONObject().put("t", "media").put("app", "YouTube Music").put("title", "Kesariya")
            .put("artist", "Arijit Singh").put("playing", false).put("vol", 9).put("volMax", 15))
    }

    private fun route() {
        RideNav.received(
            ctx, JSONObject().put("t", "nav").put("name", "Nandi Hills").put("via", "NH 44")
                .put("from", "My location").put("address", "Karnataka 562103")
                .put("lat", 13.3702).put("lng", 77.6835)
        )
    }

    private fun message() {
        RideNotifications.add(
            JSONObject().put("id", "m1").put("app", "WhatsApp").put("title", "Rahul")
                .put("text", "Reached the fuel station, waiting near the tea stall. Take your time!")
                .put("time", System.currentTimeMillis())
        )
    }

    private fun call() {
        RideCall.update(ctx, JSONObject().put("t", "call").put("state", "ringing").put("caller", "Amma").put("app", "Phone"))
    }

    /** A colourful square standing in for album art. */
    private fun fakeArt(): String {
        val b = Bitmap.createBitmap(160, 160, Bitmap.Config.ARGB_8888)
        Canvas(b).drawPaint(Paint().apply {
            shader = LinearGradient(0f, 0f, 160f, 160f, 0xFFE0457B.toInt(), 0xFF3A1C71.toInt(), Shader.TileMode.CLAMP)
        })
        val out = ByteArrayOutputStream()
        b.compress(Bitmap.CompressFormat.JPEG, 85, out)
        return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    }
}
