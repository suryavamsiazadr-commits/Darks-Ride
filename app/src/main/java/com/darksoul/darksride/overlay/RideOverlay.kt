package com.darksoul.darksride.overlay

import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.hardware.display.DisplayManager
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.text.TextUtils
import android.view.Display
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Chronometer
import android.widget.LinearLayout
import android.widget.TextView
import com.darksoul.darksride.link.LinkService
import com.darksoul.darksride.phone.CallInfo
import com.darksoul.darksride.phone.RideNotif
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Floating cards drawn on top of other apps (Google Maps) on the ride phone's main screen:
 * - a call card with Answer / Reject (or a slim "on call" bar with End call),
 * - a message banner for 8 seconds.
 * Needs "Display over other apps". Without it, the app falls back to notifications.
 */
object RideOverlay {
    private const val MESSAGE_SECONDS = 8_000L
    private const val MAX_WIDTH_DP = 640

    private val main = Handler(Looper.getMainLooper())

    private var windowContext: Context? = null
    private var windows: WindowManager? = null
    private var callView: View? = null
    private var messageView: View? = null
    private val hideMessage = Runnable { removeMessage() }

    // Palette (matches the app theme)
    private val SURFACE = 0xF216161B.toInt()
    private val EMBER = 0xFFFF7A1A.toInt()
    private val GO = 0xFF3DDC84.toInt()
    private val STOP = 0xFFE5484D.toInt()
    private val WHITE = 0xFFFFFFFF.toInt()
    private val DIM = 0xB3FFFFFF.toInt()

    fun canShow(c: Context): Boolean = Settings.canDrawOverlays(c)

    fun permissionIntent(c: Context): Intent = Intent(
        Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${c.packageName}")
    )

    // ---------- Calls ----------

    fun showCall(c: Context, call: CallInfo) {
        val app = c.applicationContext
        main.post {
            if (!canShow(app)) return@post
            removeMessage()
            removeCall()
            val ctx = context(app) ?: return@post
            val view = if (call.ringing) ringingCard(ctx, app, call) else onCallBar(ctx, app, call)
            callView = add(ctx, view, bottomMarginDp = 24)
        }
    }

    fun hideCall() {
        main.post { removeCall() }
    }

    // ---------- Messages ----------

    fun showMessage(c: Context, n: RideNotif) {
        val app = c.applicationContext
        main.post {
            if (!canShow(app) || callView != null) return@post
            removeMessage()
            val ctx = context(app) ?: return@post
            messageView = add(ctx, messageCard(ctx, n), bottomMarginDp = 24)
            main.postDelayed(hideMessage, MESSAGE_SECONDS)
        }
    }

    fun hideAll() {
        main.post {
            removeCall()
            removeMessage()
        }
    }

    // ---------- Building the cards (plain Android views: work outside the app) ----------

    private fun ringingCard(ctx: Context, app: Context, call: CallInfo): View =
        card(ctx, borderColor = GO).apply {
            addView(label(ctx, "Incoming call · ${call.app}", EMBER, 16f, bold = true))
            addView(label(ctx, call.caller, WHITE, 30f, bold = true))
            addView(LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, dp(ctx, 16), 0, 0)
                addView(button(ctx, "Reject", STOP, 84) { LinkService.sendCommand(app, "reject") },
                    weighted(ctx, endMarginDp = 8))
                addView(button(ctx, "Answer", GO, 84) { LinkService.sendCommand(app, "answer") },
                    weighted(ctx, startMarginDp = 8))
            })
        }

    private fun onCallBar(ctx: Context, app: Context, call: CallInfo): View =
        card(ctx, borderColor = EMBER).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                addView(label(ctx, call.caller, WHITE, 22f, bold = true))
                addView(Chronometer(ctx).apply {
                    setTextColor(DIM)
                    textSize = 18f
                    base = SystemClock.elapsedRealtime() - (System.currentTimeMillis() - call.since)
                    start()
                })
            }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(button(ctx, "End call", STOP, 64) { LinkService.sendCommand(app, "end") }.apply {
                setPadding(dp(ctx, 28), 0, dp(ctx, 28), 0)
            })
        }

    private fun messageCard(ctx: Context, n: RideNotif): View =
        card(ctx, borderColor = EMBER).apply {
            val time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(n.time))
            addView(label(ctx, "${n.app} · $time", EMBER, 15f, bold = true))
            addView(label(ctx, n.title, WHITE, 22f, bold = true))
            addView(label(ctx, n.text, WHITE, 18f, bold = false, lines = 2))
            setOnClickListener { removeMessage() }
        }

    private fun card(ctx: Context, borderColor: Int) = LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL
        val pad = dp(ctx, 20)
        setPadding(pad, pad, pad, pad)
        background = GradientDrawable().apply {
            setColor(SURFACE)
            cornerRadius = dp(ctx, 24).toFloat()
            setStroke(dp(ctx, 3), borderColor)
        }
        elevation = dp(ctx, 8).toFloat()
    }

    private fun label(
        ctx: Context, text: String, color: Int, sizeSp: Float, bold: Boolean, lines: Int = 1
    ) = TextView(ctx).apply {
        this.text = text
        setTextColor(color)
        textSize = sizeSp
        if (bold) typeface = Typeface.DEFAULT_BOLD
        maxLines = lines
        ellipsize = TextUtils.TruncateAt.END
    }

    private fun button(
        ctx: Context, text: String, color: Int, heightDp: Int, onClick: () -> Unit
    ) = TextView(ctx).apply {
        this.text = text
        setTextColor(WHITE)
        textSize = 22f
        typeface = Typeface.DEFAULT_BOLD
        gravity = Gravity.CENTER
        minHeight = dp(ctx, heightDp)
        background = GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(ctx, 20).toFloat()
        }
        isClickable = true
        setOnClickListener { onClick() }
    }

    private fun weighted(ctx: Context, startMarginDp: Int = 0, endMarginDp: Int = 0) =
        LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
            marginStart = dp(ctx, startMarginDp)
            marginEnd = dp(ctx, endMarginDp)
        }

    // ---------- Window plumbing ----------

    /** A context tied to the main screen (where Maps runs), set up for overlay windows. */
    private fun context(app: Context): Context? {
        windowContext?.let { return it }
        return runCatching {
            val display = app.getSystemService(DisplayManager::class.java)
                .getDisplay(Display.DEFAULT_DISPLAY)
            app.createDisplayContext(display)
                .createWindowContext(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, null)
        }.getOrNull()?.also {
            windowContext = it
            windows = it.getSystemService(WindowManager::class.java)
        }
    }

    private fun add(ctx: Context, view: View, bottomMarginDp: Int): View? {
        val wm = windows ?: return null
        val screenWidth = ctx.resources.displayMetrics.widthPixels
        val width = minOf(screenWidth - dp(ctx, 24), dp(ctx, MAX_WIDTH_DP))
        val params = WindowManager.LayoutParams(
            width,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            y = dp(ctx, bottomMarginDp)
        }
        return runCatching { wm.addView(view, params); view }.getOrNull()
    }

    private fun removeCall() {
        callView?.let { v -> runCatching { windows?.removeView(v) } }
        callView = null
    }

    private fun removeMessage() {
        main.removeCallbacks(hideMessage)
        messageView?.let { v -> runCatching { windows?.removeView(v) } }
        messageView = null
    }

    private fun dp(ctx: Context, value: Int): Int =
        (value * ctx.resources.displayMetrics.density).toInt()
}
