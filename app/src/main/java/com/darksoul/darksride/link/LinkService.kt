package com.darksoul.darksride.link

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.darksoul.darksride.AppMode
import com.darksoul.darksride.AppPrefs
import com.darksoul.darksride.MainActivity
import com.darksoul.darksride.R
import com.darksoul.darksride.media.MediaRelay
import com.darksoul.darksride.media.RideMedia
import com.darksoul.darksride.nav.Destination
import com.darksoul.darksride.nav.RideNav
import com.darksoul.darksride.overlay.RideOverlay
import com.darksoul.darksride.phone.CallRelay
import com.darksoul.darksride.phone.NotifyBridge
import com.darksoul.darksride.phone.PhoneListenerService
import com.darksoul.darksride.phone.RideCall
import com.darksoul.darksride.phone.RideNotifications
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.IOException
import java.io.OutputStream
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong

/**
 * Keeps the Bluetooth link between the two phones alive, even with the screen off.
 *
 * Ride screen: waits for the main phone to connect (Bluetooth server).
 * Main phone: connects to the saved ride phone and retries until it is reachable (client).
 * Both sides send a small "ping" every 10 s; 45 s of silence means the link is dead and
 * gets rebuilt. Messages are one JSON object per line.
 */
@SuppressLint("MissingPermission") // The screen only starts this after Bluetooth access is granted.
class LinkService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var loopJob: Job? = null

    @Volatile private var serverSocket: BluetoothServerSocket? = null
    @Volatile private var socket: BluetoothSocket? = null

    private val writeLock = Any()
    private var output: OutputStream? = null

    private var connectionsThisRun = 0
    private var lastStatus: LinkStatus? = null

    /** Main phone only: reads the music playing here and obeys the ride screen's buttons. */
    @Volatile private var mediaRelay: MediaRelay? = null

    /** Main phone only: follows calls and answers / rejects them for the ride screen. */
    @Volatile private var callRelay: CallRelay? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val channel = NotificationChannel(
            CHANNEL_ID, "Phone link", NotificationManager.IMPORTANCE_LOW
        ).apply { description = "Shows while Dark's Ride keeps your phones linked" }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Turned off by the rider: never come back by itself (e.g. Android restarting us).
        if (intent == null && !AppPrefs.linkEnabled(this)) {
            stopSelf()
            return START_NOT_STICKY
        }
        when (intent?.action) {
            ACTION_SHUTDOWN -> { // "Turn off" in the notification
                LinkState.log("Turned off from the notification")
                prepareTurnOff(this)
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_NAV -> { // "Send to bike" from the share menu (main phone)
                val text = intent.getStringExtra(EXTRA_NAV_TEXT).orEmpty()
                if (loopJob?.isActive == true) {
                    scope.launch { deliverDestination(text) }
                    return START_STICKY
                }
                AppPrefs.setPendingNav(this, text)
                stopSelf(startId)
                return START_NOT_STICKY
            }
            ACTION_SEND -> {
                if (loopJob?.isActive == true) {
                    val json = intent.getStringExtra(EXTRA_JSON)
                    if (json != null) {
                        scope.launch {
                            val sent = runCatching { send(JSONObject(json)) }.getOrDefault(false)
                            if (!sent) LinkState.log("Not connected, button not sent")
                        }
                    } else {
                        val text = intent.getStringExtra(EXTRA_TEXT).orEmpty()
                        scope.launch { sendChat(text) }
                    }
                    return START_STICKY
                }
                LinkState.log("Link is off, message not sent")
                stopSelf(startId)
                return START_NOT_STICKY
            }
            ACTION_RESTART -> {
                if (!goForeground()) return START_NOT_STICKY
                startLoop(restart = true)
            }
            else -> { // ACTION_START, or the system restarting us after a kill
                if (!goForeground()) return START_NOT_STICKY
                if (loopJob?.isActive != true) {
                    val reason = if (intent == null) "Link restarted by Android"
                    else intent.getStringExtra(EXTRA_REASON)
                    if (reason != null) LinkState.log(reason)
                    startLoop(restart = false)
                }
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        loopJob?.cancel()
        closeSockets()
        mediaRelay?.stop()
        mediaRelay = null
        callRelay?.stop()
        callRelay = null
        RideCall.clear(this)
        RideOverlay.hideAll()
        NotifyBridge.sink = null
        RideMedia.clear()
        RideNotifications.clear()
        scope.cancel()
        LinkState.setStatus(LinkStatus.STOPPED)
        LinkState.log("Link stopped")
        super.onDestroy()
    }

    // ---------- Foreground notification ----------

    private fun goForeground(): Boolean = try {
        ServiceCompat.startForeground(
            this, NOTIFICATION_ID, buildNotification("Starting the phone link"),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
        )
        true
    } catch (e: Exception) {
        // Android can refuse this when the app is in the background (e.g. after a restart).
        LinkState.log("Could not start the link: ${e.javaClass.simpleName}")
        stopSelf()
        false
    }

    private fun buildNotification(text: String): Notification {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val turnOff = PendingIntent.getService(
            this, 1, intent(this, ACTION_SHUTDOWN),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_ride)
            .setContentTitle("Dark's Ride")
            .setContentText(text)
            .setContentIntent(open)
            .addAction(0, "Turn off", turnOff)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    private fun setStatus(status: LinkStatus, peer: String? = null) {
        LinkState.setStatus(status, peer)
        if (status == lastStatus && status != LinkStatus.CONNECTED) return
        lastStatus = status
        val text = when (status) {
            LinkStatus.CONNECTED -> "Connected to ${peer ?: "your phone"}"
            LinkStatus.CONNECTING -> "Connecting…"
            LinkStatus.WAITING -> "Waiting for your other phone"
            LinkStatus.STOPPED -> "Link is off"
        }
        getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID, buildNotification(text))
    }

    // ---------- Connection loops ----------

    private fun startLoop(restart: Boolean) {
        val old = loopJob
        if (restart) {
            old?.cancel()
            closeSockets()
        }
        connectionsThisRun = 0
        LinkState.resetReconnects()
        if (AppPrefs.mode(this) == AppMode.MAIN && mediaRelay == null) {
            mediaRelay = MediaRelay(this) { msg -> scope.launch { send(msg) } }
        }
        if (AppPrefs.mode(this) == AppMode.MAIN) {
            NotifyBridge.sink = { msg -> scope.launch { send(msg) } }
            if (callRelay == null) callRelay = CallRelay(this) { msg -> scope.launch { send(msg) } }
        }
        callRelay?.ensureStarted()
        mediaRelay?.ensureStarted()
        loopJob = scope.launch {
            if (restart) old?.cancelAndJoin()
            val adapter = getSystemService(BluetoothManager::class.java)?.adapter
            if (adapter == null) {
                LinkState.log("This phone has no Bluetooth")
                return@launch
            }
            try {
                if (AppPrefs.mode(this@LinkService) == AppMode.RIDE) rideLoop(adapter)
                else mainLoop(adapter)
            } catch (e: SecurityException) {
                LinkState.log("Bluetooth access was removed. Open the app to allow it again.")
                setStatus(LinkStatus.STOPPED)
            }
        }
    }

    /** Ride screen: listen, accept one phone, talk until it drops, listen again. */
    private suspend fun rideLoop(adapter: BluetoothAdapter) {
        LinkState.log("Ride screen ready, waiting for the main phone")
        var offLogged = false
        while (currentCoroutineContext().isActive) {
            if (!adapter.isEnabled) {
                if (!offLogged) LinkState.log("Bluetooth is off, waiting")
                offLogged = true
                setStatus(LinkStatus.WAITING)
                delay(3_000)
                continue
            }
            offLogged = false
            setStatus(LinkStatus.WAITING)
            val accepted: BluetoothSocket = try {
                val server = adapter.listenUsingRfcommWithServiceRecord(SERVICE_NAME, APP_UUID)
                serverSocket = server
                try {
                    server.accept()
                } finally {
                    runCatching { server.close() }
                    serverSocket = null
                }
            } catch (e: IOException) {
                delay(2_000)
                continue
            }
            talk(accepted)
        }
    }

    /**
     * Main phone: connect to the saved ride phone, retry with a growing pause.
     * Retries every 15 s at most for the first 5 minutes, then every 60 s to save battery
     * (e.g. a whole day at the office). Coming back in range links within about a minute.
     */
    private suspend fun mainLoop(adapter: BluetoothAdapter) {
        val address = AppPrefs.rideAddress(this)
        if (address == null) {
            LinkState.log("Pick the ride phone first")
            setStatus(LinkStatus.STOPPED)
            return
        }
        LinkState.log("Looking for ${AppPrefs.rideName(this) ?: "the ride phone"}")
        var pause = 2_000L
        var offLogged = false
        var missLogged = false
        var missingSince = 0L
        while (currentCoroutineContext().isActive) {
            if (!adapter.isEnabled) {
                if (!offLogged) LinkState.log("Bluetooth is off, waiting")
                offLogged = true
                setStatus(LinkStatus.WAITING)
                delay(3_000)
                continue
            }
            offLogged = false
            setStatus(LinkStatus.CONNECTING)
            val connected: BluetoothSocket = try {
                val s = adapter.getRemoteDevice(address).createRfcommSocketToServiceRecord(APP_UUID)
                socket = s
                s.connect()
                s
            } catch (e: IOException) {
                runCatching { socket?.close() }
                socket = null
                if (!missLogged) {
                    LinkState.log("Ride phone not reachable yet, retrying")
                    missingSince = System.currentTimeMillis()
                }
                missLogged = true
                setStatus(LinkStatus.WAITING)
                delay(pause)
                val longAway = System.currentTimeMillis() - missingSince > 5 * 60_000L
                pause = if (longAway) 60_000L else (pause * 2).coerceAtMost(15_000L)
                continue
            }
            pause = 2_000L
            missLogged = false
            talk(connected)
        }
    }

    /** Runs one live connection until it drops. */
    private suspend fun talk(sock: BluetoothSocket) = coroutineScope {
        socket = sock
        synchronized(writeLock) { output = sock.outputStream }
        val peer = runCatching { sock.remoteDevice.name }.getOrNull() ?: "your phone"
        connectionsThisRun++
        if (connectionsThisRun > 1) LinkState.countReconnect()
        setStatus(LinkStatus.CONNECTED, peer)
        LinkState.log("Connected to $peer")

        val lastHeard = AtomicLong(System.currentTimeMillis())
        val myRole = if (AppPrefs.mode(this@LinkService) == AppMode.RIDE) "ride" else "main"
        send(JSONObject().put("t", "hello").put("name", Build.MODEL).put("role", myRole))
        // Main phone: tell the ride screen what is playing right away.
        mediaRelay?.ensureStarted()
        mediaRelay?.refresh()
        if (myRole == "main") {
            PhoneListenerService.resendToBike()
            // A destination shared while the bike was away goes out now.
            AppPrefs.takePendingNav(this@LinkService)?.let { text ->
                scope.launch { deliverDestination(text) }
            }
        }
        callRelay?.ensureStarted()
        callRelay?.refresh()

        val heartbeat = launch {
            while (isActive) {
                delay(10_000)
                mediaRelay?.ensureStarted() // picks up notification access granted later
                callRelay?.ensureStarted()  // picks up call access granted later
                if (!send(JSONObject().put("t", "ping"))) break
                if (System.currentTimeMillis() - lastHeard.get() > 45_000) {
                    LinkState.log("No reply for 45 s, rebuilding the link")
                    runCatching { sock.close() }
                    break
                }
            }
        }

        try {
            val reader = sock.inputStream.bufferedReader()
            while (true) {
                val line = reader.readLine() ?: break
                lastHeard.set(System.currentTimeMillis())
                handle(line)
            }
        } catch (e: IOException) {
            // Link dropped: fall through and reconnect.
        } finally {
            heartbeat.cancel()
            synchronized(writeLock) { output = null }
            runCatching { sock.close() }
            socket = null
            RideMedia.clear()
            RideNotifications.clear()
            RideCall.clear(this@LinkService)
            LinkState.log("Disconnected from $peer")
            setStatus(LinkStatus.WAITING)
        }
    }

    private fun handle(line: String) {
        val msg = runCatching { JSONObject(line) }.getOrNull() ?: return
        when (msg.optString("t")) {
            "ping" -> Unit
            "hello" -> {
                val role = if (msg.optString("role") == "ride") "ride screen" else "main phone"
                LinkState.log("Linked with ${msg.optString("name")} ($role)")
            }
            "chat" -> LinkState.log("Received: ${msg.optString("text")}")
            "media" -> RideMedia.update(msg)                    // ride screen
            "cmd" -> {                                          // main phone
                when (val action = msg.optString("a")) {
                    "answer", "reject", "end" -> callRelay?.command(action)
                    else -> mediaRelay?.command(action)
                }
            }
            "call" -> RideCall.update(this, msg)                // ride screen
            "nav" -> RideNav.received(this, msg)                // ride screen
            "notif" -> {                                        // ride screen
                RideOverlay.showMessage(this, RideNotifications.add(msg))
                LinkState.log("Message: ${msg.optString("title")} (${msg.optString("app")})")
            }
            "notif_gone" -> RideNotifications.remove(msg.optString("id"))
        }
    }

    private fun send(msg: JSONObject): Boolean = synchronized(writeLock) {
        val out = output ?: return false
        try {
            out.write((msg.toString() + "\n").toByteArray(Charsets.UTF_8))
            out.flush()
            true
        } catch (e: IOException) {
            false
        }
    }

    /** Main phone: work out the destination (may use internet) and send it to the bike. */
    private fun deliverDestination(text: String) {
        val json = runCatching { Destination.build(text) }.getOrNull()
        if (json == null) {
            LinkState.log("Could not read that place")
            return
        }
        if (send(json)) {
            LinkState.log("Sent to bike: ${json.optString("name")}")
        } else {
            AppPrefs.setPendingNav(this, text)
            LinkState.log("Bike not connected, destination saved")
        }
    }

    private fun sendChat(text: String) {
        if (send(JSONObject().put("t", "chat").put("text", text))) LinkState.log("Sent: $text")
        else LinkState.log("Not connected, message not sent")
    }

    private fun closeSockets() {
        runCatching { serverSocket?.close() }
        runCatching { socket?.close() }
        serverSocket = null
        socket = null
    }

    companion object {
        /** Shared by both phones so they find each other's Dark's Ride service. */
        val APP_UUID: UUID = UUID.fromString("6f1d2c3b-8a4e-4b7f-9c2d-1e5a7b9c3d42")
        private const val SERVICE_NAME = "DarksRide"
        private const val CHANNEL_ID = "link"
        private const val NOTIFICATION_ID = 1

        private const val ACTION_START = "com.darksoul.darksride.START"
        private const val ACTION_RESTART = "com.darksoul.darksride.RESTART"
        private const val ACTION_SEND = "com.darksoul.darksride.SEND"
        private const val ACTION_SHUTDOWN = "com.darksoul.darksride.SHUTDOWN"
        private const val EXTRA_TEXT = "text"
        private const val EXTRA_REASON = "reason"
        private const val EXTRA_JSON = "json"
        private const val ACTION_NAV = "com.darksoul.darksride.NAV"
        private const val EXTRA_NAV_TEXT = "nav_text"

        private fun intent(c: Context, action: String) =
            Intent(c, LinkService::class.java).setAction(action)

        /** Started by the rider: also remember to start again after a restart. */
        fun start(c: Context) {
            AppPrefs.setLinkEnabled(c, true)
            ContextCompat.startForegroundService(c, intent(c, ACTION_START))
        }

        fun restart(c: Context) {
            AppPrefs.setLinkEnabled(c, true)
            ContextCompat.startForegroundService(c, intent(c, ACTION_RESTART))
        }

        /** Stopped by the rider: stays off until started again, even after a restart. */
        /**
         * The off switch: stops the link and music/message watching, and keeps it all off
         * (also after a phone restart) until the rider opens Dark's Ride again.
         */
        fun turnOff(c: Context) {
            prepareTurnOff(c)
            c.stopService(Intent(c, LinkService::class.java))
        }

        private fun prepareTurnOff(c: Context) {
            AppPrefs.setLinkEnabled(c, false)
            PhoneListenerService.pause()
        }

        fun stop(c: Context) {
            AppPrefs.setLinkEnabled(c, false)
            c.stopService(Intent(c, LinkService::class.java))
        }

        /** Started by the phone itself (after a restart or an app update). */
        fun resume(c: Context, reason: String) {
            ContextCompat.startForegroundService(
                c, intent(c, ACTION_START).putExtra(EXTRA_REASON, reason)
            )
        }

        /** Ride screen button (play, next, volume, answer...) for the main phone. */
        fun sendCommand(c: Context, action: String) {
            c.startService(commandIntent(c, action))
        }

        /** The same button as an Intent, for notification buttons (e.g. Answer on the call pop-up). */
        fun commandIntent(c: Context, action: String): Intent {
            val json = JSONObject().put("t", "cmd").put("a", action).toString()
            return intent(c, ACTION_SEND).putExtra(EXTRA_JSON, json)
        }

        /** Main phone: a place shared with "Send to bike". */
        fun sendDestination(c: Context, sharedText: String) {
            c.startService(intent(c, ACTION_NAV).putExtra(EXTRA_NAV_TEXT, sharedText))
        }

        fun sendTest(c: Context, text: String) {
            c.startService(intent(c, ACTION_SEND).putExtra(EXTRA_TEXT, text))
        }
    }
}
