package com.darksoul.darksride.phone

import android.Manifest
import android.annotation.SuppressLint
import android.app.ActivityOptions
import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.service.notification.StatusBarNotification
import android.telecom.TelecomManager
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat
import com.darksoul.darksride.link.LinkState
import org.json.JSONObject

/**
 * Main phone side of calls.
 *
 * - Phone (SIM) calls: call state from Android (needs phone access); the caller's name is
 *   read from the dialer's incoming-call notification.
 * - WhatsApp and other app calls: read from their call notifications only.
 * - Answer / Reject / End from the ride screen: Android's call controls first, and the
 *   buttons on the call notification as a fallback.
 *
 * Sends {"t":"call","state":"ringing"|"active"|"ended", "caller", "app", "missed"}.
 */
@SuppressLint("MissingPermission") // Every call is guarded by hasCallAccess() or a try/catch.
class CallRelay(
    private val context: Context,
    private val send: (JSONObject) -> Unit
) {
    private enum class State { IDLE, RINGING, ACTIVE }

    private val handler = Handler(Looper.getMainLooper())
    private val telephony = context.getSystemService(TelephonyManager::class.java)
    private val telecom = context.getSystemService(TelecomManager::class.java)

    @Volatile private var registered = false

    // Phone (SIM) call
    private var simState = State.IDLE
    private var simCaller: String? = null
    private var dialerNotification: Notification? = null

    // App call (WhatsApp...)
    private var appState = State.IDLE
    private var appCaller: String? = null
    private var appName = ""
    private var appKey: String? = null
    private var appNotification: Notification? = null

    private var rejectedByRider = false
    private var lastSentKey: String? = null

    private val callStateCallback = object : TelephonyCallback(), TelephonyCallback.CallStateListener {
        override fun onCallStateChanged(state: Int) {
            handler.post { onSimState(state) }
        }
    }

    init {
        active = this
    }

    /** Safe to call often: starts following phone calls once phone access is granted. */
    fun ensureStarted() {
        if (registered || !hasCallAccess(context)) return
        handler.post {
            if (registered) return@post
            try {
                telephony.registerTelephonyCallback(context.mainExecutor, callStateCallback)
                registered = true
                LinkState.log("Call control ready")
            } catch (e: SecurityException) {
                // Access not active yet; the next call tries again.
            }
        }
    }

    fun stop() {
        handler.post {
            if (registered) runCatching { telephony.unregisterTelephonyCallback(callStateCallback) }
            registered = false
        }
        if (active === this) active = null
    }

    /** After the ride screen connects: send any call that is going on now. */
    fun refresh() {
        handler.post {
            lastSentKey = null
            publish()
        }
    }

    /** Answer / reject / end pressed on the ride screen. */
    fun command(action: String) {
        handler.post {
            when (action) {
                "answer" -> answer()
                "reject" -> {
                    rejectedByRider = true
                    hangUp()
                }
                "end" -> hangUp()
            }
        }
    }

    // ---------- From the notification listener (main thread) ----------

    fun onCallNotification(sbn: StatusBarNotification) {
        val n = sbn.notification ?: return
        val title = n.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
            ?.takeIf { it.isNotBlank() }

        if (sbn.packageName in DIALERS) {
            dialerNotification = n
            if (title != null) simCaller = title
            if (simState != State.IDLE) publish()
            return
        }

        // A call in another app: ringing while it offers "Answer", otherwise in progress.
        val callType = n.extras.getInt(Notification.EXTRA_CALL_TYPE, 0)
        val ringing = when (callType) {
            Notification.CallStyle.CALL_TYPE_INCOMING -> true
            Notification.CallStyle.CALL_TYPE_ONGOING -> false
            else -> styleIntent(n, Notification.EXTRA_ANSWER_INTENT) != null ||
                n.actions.orEmpty().any { matches(it, ANSWER_WORDS) }
        }
        val newState = if (ringing) State.RINGING else State.ACTIVE
        if (appState == State.IDLE && newState == State.RINGING) rejectedByRider = false
        handler.removeCallbacks(appCallEnded)
        appKey = sbn.key
        appNotification = n
        appName = labelOf(sbn.packageName)
        if (title != null) appCaller = title
        appState = newState
        publish()
    }

    fun onCallNotificationRemoved(sbn: StatusBarNotification) {
        if (sbn.packageName in DIALERS || sbn.key != appKey) return
        // Apps often swap the ringing notification for an "ongoing call" one; wait a moment.
        handler.removeCallbacks(appCallEnded)
        handler.postDelayed(appCallEnded, 1_500)
    }

    private val appCallEnded = Runnable {
        val old = appState
        val caller = appCaller ?: "Caller"
        appState = State.IDLE
        appKey = null
        appNotification = null
        appCaller = null
        if (old != State.IDLE) {
            sendEnded(missed = old == State.RINGING && !rejectedByRider, caller = caller, app = appName)
        }
    }

    // ---------- Phone (SIM) call state ----------

    private fun onSimState(state: Int) {
        val new = when (state) {
            TelephonyManager.CALL_STATE_RINGING -> State.RINGING
            TelephonyManager.CALL_STATE_OFFHOOK -> State.ACTIVE
            else -> State.IDLE
        }
        val old = simState
        if (new == old) return
        simState = new
        when (new) {
            State.RINGING -> {
                rejectedByRider = false
                publish()
            }
            State.ACTIVE -> publish()
            State.IDLE -> {
                val caller = simCaller ?: "Unknown caller"
                simCaller = null
                dialerNotification = null
                sendEnded(missed = old == State.RINGING && !rejectedByRider, caller = caller, app = "Phone")
            }
        }
    }

    // ---------- Sending ----------

    private fun publish() {
        val state: State
        val caller: String
        val app: String
        when {
            simState != State.IDLE -> {
                state = simState
                caller = simCaller ?: if (simState == State.RINGING) "Incoming call" else "On a call"
                app = "Phone"
            }
            appState != State.IDLE -> {
                state = appState
                caller = appCaller ?: "Incoming call"
                app = appName
            }
            else -> return
        }
        val stateName = if (state == State.RINGING) "ringing" else "active"
        val key = "$stateName|$caller|$app"
        if (key == lastSentKey) return
        lastSentKey = key
        send(
            JSONObject().put("t", "call").put("state", stateName)
                .put("caller", caller).put("app", app)
        )
        LinkState.log(if (state == State.RINGING) "Call from $caller ($app)" else "On call with $caller")
    }

    private fun sendEnded(missed: Boolean, caller: String, app: String) {
        lastSentKey = null
        send(
            JSONObject().put("t", "call").put("state", "ended")
                .put("caller", caller).put("app", app).put("missed", missed)
        )
        LinkState.log(if (missed) "Missed call from $caller" else "Call ended")
        publish() // another call may still be going on
    }

    // ---------- Answer / hang up ----------

    private fun answer() {
        if (simState == State.RINGING) {
            val done = hasCallAccess(context) && runCatching {
                @Suppress("DEPRECATION")
                telecom.acceptRingingCall()
                true
            }.getOrDefault(false)
            if (!done) press(dialerNotification, Button.ANSWER)
            return
        }
        if (appState == State.RINGING) {
            if (!press(appNotification, Button.ANSWER)) {
                LinkState.log("Could not find $appName's Answer button")
            }
        }
    }

    private fun hangUp() {
        if (simState != State.IDLE) {
            val done = hasCallAccess(context) && runCatching {
                @Suppress("DEPRECATION")
                telecom.endCall()
            }.getOrDefault(false)
            if (!done) {
                press(dialerNotification, Button.DECLINE) || press(dialerNotification, Button.HANG_UP)
            }
            return
        }
        if (appState != State.IDLE) {
            val first = if (appState == State.RINGING) Button.DECLINE else Button.HANG_UP
            val second = if (first == Button.DECLINE) Button.HANG_UP else Button.DECLINE
            if (!press(appNotification, first) && !press(appNotification, second)) {
                LinkState.log("Could not find $appName's ${first.label} button")
            }
        }
    }

    /** The three buttons a call notification can have. */
    private enum class Button(val label: String, val extraKey: String, val words: List<String>) {
        ANSWER("Answer", Notification.EXTRA_ANSWER_INTENT, ANSWER_WORDS),
        DECLINE("Decline", Notification.EXTRA_DECLINE_INTENT, DECLINE_WORDS),
        HANG_UP("Hang up", Notification.EXTRA_HANG_UP_INTENT, HANG_UP_WORDS)
    }

    /**
     * Presses a button on a call notification. Apps like WhatsApp use Android's "call style",
     * which keeps Answer / Decline / Hang up in the notification's extras rather than as
     * normal buttons, so look there first.
     */
    private fun press(n: Notification?, button: Button): Boolean {
        if (n == null) return false
        val intent = styleIntent(n, button.extraKey)
            ?: n.actions.orEmpty().firstOrNull { matches(it, button.words) }?.actionIntent
            ?: return false
        return fire(intent)
    }

    private fun styleIntent(n: Notification, key: String): PendingIntent? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            n.extras.getParcelable(key, PendingIntent::class.java)
        } else {
            @Suppress("DEPRECATION")
            n.extras.getParcelable(key) as? PendingIntent
        }

    /**
     * Sends the app's button. Answering usually opens the app's call screen, which Android
     * only allows from the background when Dark's Ride may "Display over other apps".
     */
    private fun fire(intent: PendingIntent): Boolean = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val options = ActivityOptions.makeBasic().setPendingIntentBackgroundActivityStartMode(
                ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
            )
            intent.send(context, 0, null, null, null, null, options.toBundle())
        } else {
            intent.send()
        }
        true
    } catch (e: PendingIntent.CanceledException) {
        false
    } catch (e: SecurityException) {
        false
    }

    private fun matches(action: Notification.Action, words: List<String>): Boolean {
        val title = action.title?.toString()?.lowercase() ?: return false
        return words.any { title.contains(it) }
    }

    private fun labelOf(pkg: String): String = when (pkg) {
        "com.whatsapp", "com.whatsapp.w4b" -> "WhatsApp"
        else -> try {
            val pm = context.packageManager
            pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
        } catch (e: PackageManager.NameNotFoundException) {
            "App call"
        }
    }

    companion object {
        /** The running relay, so the notification listener can hand it call notifications. */
        @Volatile var active: CallRelay? = null
            private set

        /** The phone's own dialer apps (Samsung, Google, stock Android). */
        val DIALERS = setOf(
            "com.samsung.android.incallui",
            "com.samsung.android.dialer",
            "com.android.incallui",
            "com.android.dialer",
            "com.google.android.dialer",
            "com.android.server.telecom"
        )

        private val ANSWER_WORDS = listOf("answer", "accept", "pick up")
        private val DECLINE_WORDS = listOf("decline", "reject", "dismiss", "ignore")
        private val HANG_UP_WORDS = listOf("hang up", "hangup", "end", "leave")

        val PERMISSIONS = arrayOf(
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.ANSWER_PHONE_CALLS
        )

        fun hasCallAccess(c: Context): Boolean = PERMISSIONS.all {
            ContextCompat.checkSelfPermission(c, it) == PackageManager.PERMISSION_GRANTED
        }
    }
}
