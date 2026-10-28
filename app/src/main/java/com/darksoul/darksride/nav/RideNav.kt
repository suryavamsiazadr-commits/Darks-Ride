package com.darksoul.darksride.nav

import android.app.ActivityOptions
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.Display
import androidx.core.app.NotificationCompat
import com.darksoul.darksride.R
import com.darksoul.darksride.link.LinkState
import com.darksoul.darksride.overlay.RideOverlay
import com.darksoul.darksride.phone.RideNotif
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject

/** A destination sent from the main phone. */
data class RideDestination(
    val name: String,
    val query: String?,
    val url: String?,
    val lat: Double?,
    val lng: Double?
)

/** Ride screen side of "Send to bike": starts Google Maps navigation. */
object RideNav {
    private const val MAPS = "com.google.android.apps.maps"
    /** Google Maps travel mode: l = two-wheeler (falls back to driving where unavailable). */
    private const val MODE = "l"
    private const val CHANNEL_ID = "nav"
    private const val NOTIFICATION_ID = 3

    private val _last = MutableStateFlow<RideDestination?>(null)
    val last: StateFlow<RideDestination?> = _last.asStateFlow()

    fun received(c: Context, msg: JSONObject) {
        val d = RideDestination(
            name = msg.optString("name").ifEmpty { "Destination" },
            query = msg.optString("query").ifEmpty { null },
            url = msg.optString("url").ifEmpty { null },
            lat = if (msg.has("lat")) msg.optDouble("lat") else null,
            lng = if (msg.has("lng")) msg.optDouble("lng") else null
        )
        _last.value = d
        LinkState.log("Destination: ${d.name}")
        // Opening Maps from the background is only allowed with "Display over other apps".
        val opened = navigate(c, d) && RideOverlay.canShow(c)
        RideOverlay.showMessage(
            c, RideNotif(
                id = "nav-${System.currentTimeMillis()}",
                app = "Navigation",
                title = d.name,
                text = if (opened) "Starting Google Maps" else "Tap the notification to navigate",
                time = System.currentTimeMillis()
            )
        )
        if (!opened) notify(c, d)
    }

    /** Opens Google Maps turn-by-turn on the main screen. Returns false if it couldn't. */
    fun navigate(c: Context, d: RideDestination): Boolean {
        val intent = Intent(Intent.ACTION_VIEW, uriFor(d)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val options = ActivityOptions.makeBasic().setLaunchDisplayId(Display.DEFAULT_DISPLAY).toBundle()
        return try {
            c.startActivity(Intent(intent).setPackage(MAPS), options)
            true
        } catch (e: ActivityNotFoundException) {
            // Google Maps not installed: let any maps app handle it.
            runCatching { c.startActivity(intent, options) }.isSuccess
        } catch (e: Exception) {
            false
        }
    }

    private fun uriFor(d: RideDestination): Uri = when {
        d.lat != null && d.lng != null ->
            Uri.parse("google.navigation:q=${d.lat},${d.lng}&mode=$MODE")
        d.query != null ->
            Uri.parse("google.navigation:q=${Uri.encode(d.query)}&mode=$MODE")
        d.url != null -> Uri.parse(d.url)
        else -> Uri.parse("google.navigation:q=${Uri.encode(d.name)}&mode=$MODE")
    }

    /** Fallback when Android blocks opening Maps from the background. */
    private fun notify(c: Context, d: RideDestination) {
        val nm = c.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Destinations", NotificationManager.IMPORTANCE_HIGH)
        )
        val open = PendingIntent.getActivity(
            c, 30,
            Intent(Intent.ACTION_VIEW, uriFor(d)).setPackage(MAPS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val n = NotificationCompat.Builder(c, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_ride)
            .setContentTitle("Navigate to ${d.name}")
            .setContentText("Sent from your main phone. Tap to start.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(open)
            .build()
        try {
            nm.notify(NOTIFICATION_ID, n)
        } catch (e: SecurityException) {
            // Notifications not allowed; the Destination card in the app still works.
        }
    }
}
