package com.darksoul.darksride.net

import android.app.ActivityOptions
import android.app.KeyguardManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.view.Display
import androidx.core.app.NotificationCompat
import androidx.core.content.edit
import com.darksoul.darksride.AppMode
import com.darksoul.darksride.AppPrefs
import com.darksoul.darksride.R
import com.darksoul.darksride.link.LinkService
import com.darksoul.darksride.link.LinkState
import com.darksoul.darksride.link.LinkStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject

/**
 * Internet for the bike phone through the main phone's Bluetooth tethering (no hotspot).
 *
 * Android doesn't let ordinary apps switch tethering on or connect "Internet access", so:
 *  1. The bike phone watches its own internet (Bluetooth / Wi-Fi) and tells the main phone.
 *  2. If it has none, it asks the main phone to make sure Bluetooth tethering is on.
 *     With the auto-tap helper allowed, Dark's Ride flips the switch itself (needs the phone
 *     unlocked); otherwise a notification takes you to the switch.
 *  3. The bike phone then connects "Internet access" to the main phone: first directly, then
 *     with its own auto-tap helper (opens Bluetooth settings for a second and taps it).
 * Stops after 3 failed tries in a row so Settings never keeps popping up over Maps.
 */
object BikeNet {
    private const val PAN = 5 // BluetoothProfile.PAN (hidden constant)
    private const val PREFS = "darks_ride"
    private const val KEY_AUTO = "net_auto"
    private const val KEY_WORKED = "net_worked_once"
    private const val CHANNEL = "internet"
    private const val NOTE_ID = 7
    private const val MAX_TRIES = 3
    private const val GAP_MS = 2 * 60_000L

    private val main = Handler(Looper.getMainLooper())

    // ---- Bike phone: its own internet ----
    private val _btInternet = MutableStateFlow(false)
    /** Bike phone: online through the main phone's Bluetooth tethering. */
    val btInternet: StateFlow<Boolean> = _btInternet.asStateFlow()
    private val _wifi = MutableStateFlow(false)
    val wifi: StateFlow<Boolean> = _wifi.asStateFlow()

    // ---- Main phone: what the bike reported ----
    private val _bikeOnline = MutableStateFlow<Boolean?>(null)
    /** Main phone: true/false as last reported by the bike, null while not linked. */
    val bikeOnline: StateFlow<Boolean?> = _bikeOnline.asStateFlow()

    private val _status = MutableStateFlow("")
    /** One line for the Internet card: what's happening right now. */
    val status: StateFlow<String> = _status.asStateFlow()

    private var app: Context? = null
    private var watching = false
    private var tries = 0
    private var lastTry = 0L
    private var linkUp = false

    fun autoEnabled(c: Context) = c.getSharedPreferences(PREFS, 0).getBoolean(KEY_AUTO, true)
    fun setAutoEnabled(c: Context, on: Boolean) = c.getSharedPreferences(PREFS, 0).edit { putBoolean(KEY_AUTO, on) }

    /** Setup tick: Bluetooth internet has worked at least once on this phone pair. */
    fun workedOnce(c: Context) = c.getSharedPreferences(PREFS, 0).getBoolean(KEY_WORKED, false)
    private fun markWorked(c: Context) = c.getSharedPreferences(PREFS, 0).edit { putBoolean(KEY_WORKED, true) }

    /** App start: follow this phone's Bluetooth and Wi-Fi internet. */
    fun watch(c: Context) {
        if (watching) return
        watching = true
        app = c.applicationContext
        val cm = c.getSystemService(ConnectivityManager::class.java) ?: return
        track(cm, NetworkCapabilities.TRANSPORT_BLUETOOTH) { up ->
            val was = _btInternet.value
            _btInternet.value = up
            if (up) {
                markWorked(c)
                tries = 0
                cancelNote(c)
                say("Online through your main phone")
            }
            if (up != was) {
                LinkState.log(if (up) "Internet via Bluetooth: connected" else "Internet via Bluetooth: lost")
                report(c)
                if (!up) main.postDelayed({ maybeAsk(c, "internet dropped") }, 20_000)
            }
        }
        track(cm, NetworkCapabilities.TRANSPORT_WIFI) { up ->
            if (_wifi.value != up) {
                _wifi.value = up
                report(c)
                if (!up) main.postDelayed({ maybeAsk(c, "Wi-Fi gone") }, 20_000)
            }
        }
    }

    private fun track(cm: ConnectivityManager, transport: Int, onChange: (Boolean) -> Unit) {
        val request = NetworkRequest.Builder().addTransportType(transport)
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET).build()
        val live = mutableSetOf<Network>()
        runCatching {
            cm.registerNetworkCallback(request, object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    live += network
                    main.post { onChange(true) }
                }
                override fun onLost(network: Network) {
                    live -= network
                    main.post { onChange(live.isNotEmpty()) }
                }
            })
        }
    }

    // ---------- Link events ----------

    /** The link just connected (both phones). */
    fun onLinkUp(c: Context, mode: AppMode, peer: BluetoothDevice?) {
        linkUp = true
        if (mode == AppMode.RIDE) {
            peer?.let { AppPrefs.setPeer(c, it.address, runCatching { it.name }.getOrNull()) }
            tries = 0
            report(c)
            main.postDelayed({ maybeAsk(c, "link up") }, 3_000)
        }
    }

    fun onLinkDown() {
        linkUp = false
        _bikeOnline.value = null
    }

    /** Messages about internet from the other phone. */
    fun handle(c: Context, msg: JSONObject) {
        when (msg.optString("t")) {
            "net" -> {                                  // main phone
                val bt = msg.optBoolean("bt")
                _bikeOnline.value = bt || msg.optBoolean("wifi")
                if (bt) markWorked(c)
                say(when {
                    bt -> "Bike is online through this phone"
                    msg.optBoolean("wifi") -> "Bike is on Wi-Fi"
                    else -> "Bike has no internet"
                })
            }
            "net_need" -> ensureTethering(c) {          // main phone
                LinkService.sendJson(c, JSONObject().put("t", "net_ready"))
            }
            "net_ready" -> connectInternet(c)           // bike phone
        }
    }

    // ---------- Bike phone ----------

    private fun report(c: Context) {
        if (AppPrefs.mode(c) != AppMode.RIDE || LinkState.status.value != LinkStatus.CONNECTED) return
        LinkService.sendJson(c, JSONObject().put("t", "net").put("bt", _btInternet.value).put("wifi", _wifi.value))
    }

    private fun maybeAsk(c: Context, why: String) {
        if (AppPrefs.mode(c) != AppMode.RIDE || !linkUp) return
        if (_btInternet.value || _wifi.value) return
        if (!autoEnabled(c)) { say("No internet. Tap Connect now."); return }
        if (tries >= MAX_TRIES) { say("Couldn't connect by itself. Tap Connect now."); return }
        if (System.currentTimeMillis() - lastTry < GAP_MS) return
        LinkState.log("No internet ($why): asking the main phone for Bluetooth tethering")
        request(c)
    }

    /** "Connect now" on the bike, or automatic: main phone checks tethering, then we connect. */
    fun request(c: Context) {
        tries++
        lastTry = System.currentTimeMillis()
        if (AppPrefs.mode(c) == AppMode.RIDE) {
            if (LinkState.status.value == LinkStatus.CONNECTED) {
                say("Asking your main phone to share internet…")
                LinkService.sendJson(c, JSONObject().put("t", "net_need"))
            } else {
                connectInternet(c)
            }
        } else {
            ensureTethering(c) { LinkService.sendJson(c, JSONObject().put("t", "net_ready")) }
        }
    }

    /** Bike phone: connect "Internet access" to the main phone. */
    private fun connectInternet(c: Context) {
        if (_btInternet.value) return
        say("Connecting internet…")
        panConnect(c)
        main.postDelayed({
            if (_btInternet.value) return@postDelayed
            val name = AppPrefs.peerName(c)
            if (AutoTap.isEnabled(c) && name != null && screenUsable(c)) {
                say("Turning on Internet access…")
                LinkState.log("Auto-tap: Bluetooth settings → $name → Internet access")
                AutoTap.start(c, AutoTap.Job.RIDE_INTERNET, name) { ok ->
                    main.postDelayed({
                        if (!_btInternet.value) {
                            LinkState.log(if (ok) "Internet access turned on, waiting for tethering"
                                else "Auto-tap couldn't find Internet access")
                            say("Not online yet. Check Bluetooth tethering on the main phone.")
                        }
                    }, 6_000)
                }
            } else {
                note(c, "Get internet from your main phone",
                    "Tap, then tap ⚙ next to ${name ?: "your main phone"} → Internet access",
                    Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
                say("Tap the notification, or allow the auto-tap helper")
            }
        }, 5_000)
    }

    /** Tries the hidden direct connect. Most phones refuse it for normal apps; then auto-tap does it. */
    private fun panConnect(c: Context) {
        val address = AppPrefs.peerAddress(c) ?: return
        val adapter = c.getSystemService(BluetoothManager::class.java)?.adapter ?: return
        runCatching {
            adapter.getProfileProxy(c, object : BluetoothProfile.ServiceListener {
                override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
                    runCatching {
                        val device = adapter.getRemoteDevice(address)
                        proxy.javaClass.getMethod("connect", BluetoothDevice::class.java).invoke(proxy, device)
                        LinkState.log("Asked Bluetooth for Internet access directly")
                    }.onFailure { LinkState.log("Direct connect not allowed, using auto-tap") }
                    runCatching { adapter.closeProfileProxy(PAN, proxy) }
                }
                override fun onServiceDisconnected(profile: Int) = Unit
            }, PAN)
        }
    }

    // ---------- Main phone ----------

    /** Makes sure Bluetooth tethering is on, then calls [then]. */
    private fun ensureTethering(c: Context, then: () -> Unit) {
        tetheringOn(c) { on ->
            when {
                on == true -> {
                    LinkState.log("Bluetooth tethering is on")
                    then()
                }
                AutoTap.isEnabled(c) && screenUsable(c) -> {
                    LinkState.log("Auto-tap: turning on Bluetooth tethering")
                    say("Turning on Bluetooth tethering…")
                    AutoTap.start(c, AutoTap.Job.MAIN_TETHER, null) { ok ->
                        LinkState.log(if (ok) "Bluetooth tethering on" else "Auto-tap couldn't find Bluetooth tethering")
                        main.postDelayed(then, 1_500)
                    }
                }
                else -> {
                    // Locked phone or no helper: a nudge, and the bike tries anyway (it may already be on).
                    if (on == false || !AutoTap.isEnabled(c)) {
                        note(c, "Bike needs internet", "Tap to turn on Bluetooth tethering", tetherIntent(c))
                    }
                    LinkState.log("Bike asked for internet: " +
                        if (on == false) "tethering is off, notification shown" else "phone locked, bike will try")
                    then()
                }
            }
        }
    }

    /** True/false if this phone tells us, null if it keeps tethering state private. */
    private fun tetheringOn(c: Context, result: (Boolean?) -> Unit) {
        val adapter = c.getSystemService(BluetoothManager::class.java)?.adapter
        if (adapter == null) { result(null); return }
        var answered = false
        val answer = { v: Boolean? -> if (!answered) { answered = true; main.post { result(v) } } }
        runCatching {
            adapter.getProfileProxy(c, object : BluetoothProfile.ServiceListener {
                override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
                    val on = runCatching { proxy.javaClass.getMethod("isTetheringOn").invoke(proxy) as Boolean }.getOrNull()
                    runCatching { adapter.closeProfileProxy(PAN, proxy) }
                    answer(on)
                }
                override fun onServiceDisconnected(profile: Int) = Unit
            }, PAN)
        }.onFailure { answer(null) }
        main.postDelayed({ answer(null) }, 2_000)
    }

    /** The phone's tethering page (name differs by brand). */
    fun tetherIntent(c: Context): Intent {
        val candidates = listOf(
            Intent().setClassName("com.android.settings", "com.android.settings.Settings\$TetherSettingsActivity"),
            Intent().setClassName("com.android.settings", "com.android.settings.TetherSettings"),
            Intent(Settings.ACTION_WIRELESS_SETTINGS)
        )
        return candidates.firstOrNull { it.resolveActivity(c.packageManager) != null } ?: candidates.last()
    }

    // ---------- Helpers ----------

    private fun screenUsable(c: Context): Boolean =
        c.getSystemService(PowerManager::class.java).isInteractive &&
            !c.getSystemService(KeyguardManager::class.java).isKeyguardLocked

    private fun say(text: String) { _status.value = text }

    /** Opens a settings page on the main screen (used by the cards' buttons). */
    fun open(c: Context, intent: Intent) {
        val options = ActivityOptions.makeBasic().setLaunchDisplayId(Display.DEFAULT_DISPLAY).toBundle()
        runCatching { c.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), options) }
    }

    private fun note(c: Context, title: String, text: String, intent: Intent) {
        val nm = c.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Internet for the bike", NotificationManager.IMPORTANCE_HIGH))
        val tap = PendingIntent.getActivity(c, 70, intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val n = NotificationCompat.Builder(c, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_ride)
            .setContentTitle(title).setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true).setContentIntent(tap)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        runCatching { nm.notify(NOTE_ID, n) }
    }

    private fun cancelNote(c: Context) = runCatching {
        c.getSystemService(NotificationManager::class.java).cancel(NOTE_ID)
    }
}
