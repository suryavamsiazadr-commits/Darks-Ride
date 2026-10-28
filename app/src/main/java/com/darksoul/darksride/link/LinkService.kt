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

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val channel = NotificationChannel(
            CHANNEL_ID, "Phone link", NotificationManager.IMPORTANCE_LOW
        ).apply { description = "Shows while Dark's Ride keeps your phones linked" }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_SEND -> {
                val text = intent.getStringExtra(EXTRA_TEXT).orEmpty()
                if (loopJob?.isActive == true) {
                    scope.launch { sendChat(text) }
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
                if (loopJob?.isActive != true) startLoop(restart = false)
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        loopJob?.cancel()
        closeSockets()
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
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_ride)
            .setContentTitle("Dark's Ride")
            .setContentText(text)
            .setContentIntent(open)
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

    /** Main phone: connect to the saved ride phone, retry with a growing pause. */
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
                if (!missLogged) LinkState.log("Ride phone not reachable yet, retrying")
                missLogged = true
                setStatus(LinkStatus.WAITING)
                delay(pause)
                pause = (pause * 2).coerceAtMost(15_000L)
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

        val heartbeat = launch {
            while (isActive) {
                delay(10_000)
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
        private const val EXTRA_TEXT = "text"

        private fun intent(c: Context, action: String) =
            Intent(c, LinkService::class.java).setAction(action)

        fun start(c: Context) =
            ContextCompat.startForegroundService(c, intent(c, ACTION_START))

        fun restart(c: Context) =
            ContextCompat.startForegroundService(c, intent(c, ACTION_RESTART))

        fun stop(c: Context) {
            c.stopService(Intent(c, LinkService::class.java))
        }

        fun sendTest(c: Context, text: String) {
            c.startService(intent(c, ACTION_SEND).putExtra(EXTRA_TEXT, text))
        }
    }
}
