package com.darksoul.darksride.net

import android.accessibilityservice.AccessibilityService
import android.app.ActivityOptions
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.darksoul.darksride.link.LinkState

/**
 * The auto-tap helper (an accessibility service, opt-in). It only ever acts inside Android's
 * Settings app, only when Dark's Ride starts a job, and only on two switches:
 *  - main phone: "Bluetooth tethering" on the tethering page,
 *  - bike phone: "Internet access" for your main phone in Bluetooth settings.
 * It reads nothing else and goes back to where you were when done.
 */
class AutoTapService : AccessibilityService() {
    override fun onServiceConnected() {
        AutoTap.service = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit // AutoTap polls the screen

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        if (AutoTap.service === this) AutoTap.service = null
        super.onDestroy()
    }
}

object AutoTap {
    enum class Job { MAIN_TETHER, RIDE_INTERNET }

    private const val SETTINGS = "com.android.settings"
    private const val STEP_MS = 400L
    private const val TIMEOUT_MS = 12_000L

    internal var service: AutoTapService? = null
    private val main = Handler(Looper.getMainLooper())

    private var job: Job? = null
    private var target: String? = null
    private var stage = 0
    private var started = 0L
    private var waitUntil = 0L
    private var backs = 1
    private var flips = 0
    private var scrolled = false
    private var done: ((Boolean) -> Unit)? = null

    fun isEnabled(c: Context): Boolean {
        val me = ComponentName(c, AutoTapService::class.java).flattenToString()
        val on = Settings.Secure.getString(c.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: ""
        return on.split(':').any { it.equals(me, ignoreCase = true) }
    }

    fun settingsIntent() = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)

    /** Opens the right Settings page and taps the switch. [onDone] gets true if it was found. */
    fun start(c: Context, which: Job, targetName: String?, onDone: (Boolean) -> Unit) {
        val svc = service
        if (svc == null) { onDone(false); return }
        if (job != null) { onDone(false); return } // one at a time
        job = which
        target = targetName
        stage = 0
        flips = 0
        backs = 1
        scrolled = false
        started = System.currentTimeMillis()
        waitUntil = started + 1_200
        done = onDone
        val intent = when (which) {
            Job.MAIN_TETHER -> BikeNet.tetherIntent(c)
            Job.RIDE_INTERNET -> Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
        }.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val options = ActivityOptions.makeBasic().setLaunchDisplayId(Display.DEFAULT_DISPLAY).toBundle()
        // Started from the accessibility service: Android allows that from the background.
        if (runCatching { svc.startActivity(intent, options) }.isFailure) { finish(false); return }
        main.postDelayed(::step, STEP_MS)
    }

    private fun step() {
        val svc = service
        val j = job ?: return
        if (svc == null || System.currentTimeMillis() - started > TIMEOUT_MS) { finish(false); return }
        if (System.currentTimeMillis() < waitUntil) { again(); return }
        val root = svc.rootInActiveWindow
        if (root == null || root.packageName?.toString() != SETTINGS) { again(); return }
        when (j) {
            Job.MAIN_TETHER -> tether(root)
            Job.RIDE_INTERNET -> internet(root)
        }
    }

    private fun again() = main.postDelayed(::step, STEP_MS)

    /** Main phone: the "Bluetooth tethering" switch. */
    private fun tether(root: AccessibilityNodeInfo) {
        val label = find(root) { Regex("(?i)bluetooth\\s*tether").containsMatchIn(it) }
        if (label == null) { scrollOnce(root); again(); return }
        val row = clickable(label) ?: label
        val sw = checkable(row) ?: checkable(label.parent)
        when {
            sw?.isChecked == true -> finish(true)
            stage == 0 -> {
                (sw?.takeIf { it.isClickable } ?: row).performAction(AccessibilityNodeInfo.ACTION_CLICK)
                stage = 1
                waitUntil = System.currentTimeMillis() + 1_500
                again()
            }
            else -> finish(sw == null) // clicked; no switch to read back means we can't tell
        }
    }

    /** Bike phone: main phone's row in Bluetooth settings → its settings → "Internet access". */
    private fun internet(root: AccessibilityNodeInfo) {
        val name = target ?: run { finish(false); return }
        when (stage) {
            0 -> { // the paired-devices list
                val label = find(root) { it.trim().equals(name.trim(), ignoreCase = true) }
                if (label == null) { scrollOnce(root); again(); return }
                val row = clickable(label) ?: label
                val gear = gearNear(row)
                if (gear != null) {
                    gear.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    stage = 1
                    backs = 2
                } else {
                    // No gear: tapping the name connects every allowed profile, internet included.
                    row.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    stage = 9
                }
                waitUntil = System.currentTimeMillis() + 1_200
                again()
            }
            1 -> { // the device's page
                val label = find(root) { Regex("(?i)internet").containsMatchIn(it) }
                if (label == null) { scrollOnce(root); again(); return }
                val row = clickable(label) ?: label
                val sw = checkable(row) ?: checkable(label.parent)
                if (sw?.isChecked == true && flips == 0 && BikeNet.btInternet.value) { finish(true); return }
                if (sw?.isChecked == true && flips == 0) {
                    // On but not connected: switch it off and on again to reconnect.
                    row.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    flips = 1
                    waitUntil = System.currentTimeMillis() + 1_500
                    again()
                    return
                }
                if (sw?.isChecked != true) row.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                stage = 9
                waitUntil = System.currentTimeMillis() + 2_500
                again()
            }
            else -> finish(true)
        }
    }

    private fun finish(ok: Boolean) {
        main.removeCallbacksAndMessages(null)
        val svc = service
        val count = backs
        job = null
        // Back out of Settings only (never press Back on Maps).
        if (svc != null) {
            repeat(count) { i ->
                main.postDelayed({
                    if (svc.rootInActiveWindow?.packageName?.toString() == SETTINGS) {
                        svc.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
                    }
                }, 300L * (i + 1))
            }
        }
        val cb = done
        done = null
        main.postDelayed({ cb?.invoke(ok) }, 300L * (count + 1))
        if (!ok) LinkState.log("Auto-tap gave up")
    }

    // ---------- Finding things on screen ----------

    private fun find(root: AccessibilityNodeInfo, match: (String) -> Boolean): AccessibilityNodeInfo? {
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue += root
        while (queue.isNotEmpty()) {
            val n = queue.removeFirst()
            val t = n.text?.toString()
            if (t != null && match(t)) return n
            for (i in 0 until n.childCount) n.getChild(i)?.let { queue += it }
        }
        return null
    }

    private fun clickable(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        var n = node
        repeat(5) {
            if (n == null) return null
            if (n!!.isClickable) return n
            n = n!!.parent
        }
        return null
    }

    private fun checkable(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        node ?: return null
        if (node.isCheckable) return node
        for (i in 0 until node.childCount) checkable(node.getChild(i))?.let { return it }
        return null
    }

    /** The ⚙ next to a paired device (its description says settings / details). */
    private fun gearNear(row: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val words = Regex("(?i)setting|detail|config|gear")
        fun search(n: AccessibilityNodeInfo?, depth: Int): AccessibilityNodeInfo? {
            if (n == null || depth > 4) return null
            if (n != row && n.isClickable && n.contentDescription?.let { words.containsMatchIn(it) } == true) return n
            for (i in 0 until n.childCount) search(n.getChild(i), depth + 1)?.let { return it }
            return null
        }
        return search(row, 0) ?: search(row.parent, 0)
    }

    private fun scrollOnce(root: AccessibilityNodeInfo) {
        if (scrolled) return
        scrolled = true
        fun scrollable(n: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
            n ?: return null
            if (n.isScrollable) return n
            for (i in 0 until n.childCount) scrollable(n.getChild(i))?.let { return it }
            return null
        }
        scrollable(root)?.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
    }
}
