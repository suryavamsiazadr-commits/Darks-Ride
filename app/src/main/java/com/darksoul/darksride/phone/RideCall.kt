package com.darksoul.darksride.phone

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.darksoul.darksride.MainActivity
import com.darksoul.darksride.R
import com.darksoul.darksride.link.LinkService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject

/** A call on the main phone, as shown on the ride screen. */
data class CallInfo(
    val ringing: Boolean,
    val caller: String,
    val app: String,
    /** When ringing or the conversation started, for the call timer. */
    val since: Long
)

/** Ride screen side of calls: the current call and the pop-up that shows over Maps. */
object RideCall {
    private val _call = MutableStateFlow<CallInfo?>(null)
    val call: StateFlow<CallInfo?> = _call.asStateFlow()

    fun update(c: Context, msg: JSONObject) {
        val caller = msg.optString("caller").ifEmpty { "Caller" }
        val app = msg.optString("app").ifEmpty { "Phone" }
        val now = System.currentTimeMillis()
        when (msg.optString("state")) {
            "ringing" -> {
                _call.value = CallInfo(ringing = true, caller = caller, app = app, since = now)
                CallAlert.showRinging(c, caller, app)
            }
            "active" -> {
                val prev = _call.value
                val since = if (prev != null && !prev.ringing) prev.since else now
                _call.value = CallInfo(ringing = false, caller = caller, app = app, since = since)
                CallAlert.showActive(c, caller, app)
            }
            "ended" -> {
                _call.value = null
                CallAlert.cancel(c)
                if (msg.optBoolean("missed")) {
                    RideNotifications.addLocal(app, caller, "Missed call")
                }
            }
        }
    }

    /** Link dropped: nothing to show any more. */
    fun clear(c: Context) {
        _call.value = null
        CallAlert.cancel(c)
    }
}

/**
 * The call pop-up on the ride screen. A high-priority notification shows on top of Google Maps
 * with Answer / Reject buttons, and opens Dark's Ride full screen when the screen is off.
 */
object CallAlert {
    private const val CHANNEL_ID = "calls"
    private const val NOTIFICATION_ID = 2

    private fun manager(c: Context): NotificationManager {
        val nm = c.getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID, "Calls on the main phone", NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Pops up over other apps when your main phone rings"
            setSound(null, null)
            enableVibration(false)
        }
        nm.createNotificationChannel(channel)
        return nm
    }

    private fun openApp(c: Context): PendingIntent = PendingIntent.getActivity(
        c, 20,
        Intent(c, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    private fun button(c: Context, code: Int, action: String): PendingIntent =
        PendingIntent.getService(
            c, code, LinkService.commandIntent(c, action),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

    fun showRinging(c: Context, caller: String, app: String) {
        val open = openApp(c)
        val n = NotificationCompat.Builder(c, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_ride)
            .setContentTitle(caller)
            .setContentText("Incoming call · $app")
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setOngoing(true)
            .setContentIntent(open)
            .setFullScreenIntent(open, true)
            .addAction(0, "Reject", button(c, 21, "reject"))
            .addAction(0, "Answer", button(c, 22, "answer"))
            .build()
        post(c, n)
    }

    fun showActive(c: Context, caller: String, app: String) {
        val n = NotificationCompat.Builder(c, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_ride)
            .setContentTitle("On call with $caller")
            .setContentText(app)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setUsesChronometer(true)
            .setContentIntent(openApp(c))
            .addAction(0, "End call", button(c, 23, "end"))
            .build()
        post(c, n)
    }

    fun cancel(c: Context) {
        c.getSystemService(NotificationManager::class.java).cancel(NOTIFICATION_ID)
    }

    private fun post(c: Context, n: android.app.Notification) {
        try {
            manager(c).notify(NOTIFICATION_ID, n)
        } catch (e: SecurityException) {
            // Notifications not allowed on this phone; the in-app call card still works.
        }
    }
}
