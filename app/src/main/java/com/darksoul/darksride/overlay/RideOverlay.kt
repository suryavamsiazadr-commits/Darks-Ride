package com.darksoul.darksride.overlay

import android.animation.ValueAnimator
import android.annotation.SuppressLint
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
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.view.animation.LinearInterpolator
import android.widget.FrameLayout
import android.widget.Chronometer
import android.widget.LinearLayout
import android.widget.TextView
import com.darksoul.darksride.AppPrefs
import com.darksoul.darksride.AppVisibility
import com.darksoul.darksride.link.LinkService
import com.darksoul.darksride.phone.CallInfo
import com.darksoul.darksride.phone.RideNotif
import com.darksoul.darksride.ui.theme.ThemeController
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Floating cards drawn at the top of the ride phone's main screen, over other apps (Google Maps):
 * - a call card with Answer / Reject (or a slim "on call" bar with End call),
 * - a message banner for 8 seconds.
 * Needs "Display over other apps". Without it, the app falls back to notifications.
 */
@SuppressLint("ClickableViewAccessibility") // ride buttons fire on touch-down on purpose
object RideOverlay {
    private const val MAX_WIDTH_DP = 640

    private val main = Handler(Looper.getMainLooper())

    private var windowContext: Context? = null
    private var windows: WindowManager? = null
    private var callView: View? = null
    private var messageView: View? = null
    private val hideMessage = Runnable { removeMessage() }

    // Colours follow the app: light / dark (light sensor in Auto) and the chosen ride theme.
    private val dark: Boolean get() = ThemeController.isDark.value
    private val SURFACE_TOP: Int get() = if (dark) 0xF21A1A1C.toInt() else 0xFAFFFFFF.toInt()
    private val SURFACE_BOTTOM: Int get() = if (dark) 0xF7070708.toInt() else 0xF5F3F1EF.toInt()
    private val EDGE: Int get() = if (dark) 0x33FFFFFF else 0x1A000000
    private val TEXT: Int get() = if (dark) 0xFFFFFFFF.toInt() else 0xFF15161A.toInt()
    private val SUBTLE: Int get() = if (dark) 0xB3FFFFFF.toInt() else 0x9915161A.toInt()
    private val ACCENT: Int get() = ThemeController.palette.value.argb(dark)
    private val ACCENT2: Int get() = ThemeController.palette.value.secondArgb()
    private val GO = 0xFF2FD27A.toInt()
    private val GO_2 = 0xFF19A35A.toInt()
    private val STOP = 0xFFFF4D57.toInt()
    private val STOP_2 = 0xFFD12B3A.toInt()
    private val WHITE = 0xFFFFFFFF.toInt()

    fun canShow(c: Context): Boolean = Settings.canDrawOverlays(c)

    fun permissionIntent(c: Context): Intent = Intent(
        Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${c.packageName}")
    )

    // ---------- Calls ----------

    fun showCall(c: Context, call: CallInfo) {
        val app = c.applicationContext
        main.post {
            if (!canShow(app) || AppVisibility.open.value) return@post
            removeMessage()
            removeCall()
            val ctx = context(app) ?: return@post
            val view = if (call.ringing) ringingCard(ctx, app, call) else onCallBar(ctx, app, call)
            callView = add(ctx, view, topMarginDp = 44)
        }
    }

    fun hideCall() {
        main.post { removeCall() }
    }

    // ---------- Messages ----------

    fun showMessage(c: Context, n: RideNotif) {
        val app = c.applicationContext
        main.post {
            if (!canShow(app) || callView != null || AppVisibility.open.value) return@post
            removeMessage()
            val ctx = context(app) ?: return@post
            val millis = AppPrefs.spotlightSeconds(app) * 1_000L
            messageView = add(ctx, messageCard(ctx, n, millis), topMarginDp = 44)
            main.postDelayed(hideMessage, millis)
        }
    }

    fun hideAll() {
        main.post {
            removeCall()
            removeMessage()
        }
    }

    // ---------- Building the cards (plain Android views: work outside the app) ----------

    /** Incoming call: avatar with a pulsing ring, the caller, big Decline / Answer pills. */
    private fun ringingCard(ctx: Context, app: Context, call: CallInfo): View =
        card(ctx).apply {
            addView(LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                addView(avatar(ctx, call.caller, 64, pulse = true))
                addView(LinearLayout(ctx).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(dp(ctx, 16), 0, 0, 0)
                    addView(chip(ctx, "INCOMING · ${call.app.uppercase()}", GO))
                    addView(label(ctx, call.caller, TEXT, 28f, bold = true).apply { setPadding(0, dp(ctx, 4), 0, 0) })
                }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            })
            addView(LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, dp(ctx, 18), 0, 0)
                addView(pill(ctx, "✕  Decline", STOP, STOP_2, 80) { LinkService.sendCommand(app, "reject") },
                    weighted(ctx, endMarginDp = 7))
                addView(pill(ctx, "✓  Answer", GO, GO_2, 80) { LinkService.sendCommand(app, "answer") },
                    weighted(ctx, startMarginDp = 7))
            })
        }

    /** During a call: a slim bar with a live dot, the caller, the timer and a round End button. */
    private fun onCallBar(ctx: Context, app: Context, call: CallInfo): View =
        card(ctx, padDp = 14).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(View(ctx).apply {
                background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(GO) }
                pulse(this)
            }, LinearLayout.LayoutParams(dp(ctx, 12), dp(ctx, 12)).apply { marginStart = dp(ctx, 6); marginEnd = dp(ctx, 14) })
            addView(LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                addView(label(ctx, call.caller, TEXT, 20f, bold = true))
                addView(Chronometer(ctx).apply {
                    setTextColor(SUBTLE)
                    textSize = 16f
                    base = SystemClock.elapsedRealtime() - (System.currentTimeMillis() - call.since)
                    start()
                })
            }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(pill(ctx, "End", STOP, STOP_2, 60) { LinkService.sendCommand(app, "end") }.apply {
                setPadding(dp(ctx, 30), 0, dp(ctx, 30), 0)
            })
        }

    /** A message: avatar, app and time, sender, text, and a bar showing how long it stays. */
    private fun messageCard(ctx: Context, n: RideNotif, millis: Long): View =
        card(ctx, padDp = 16).apply {
            val time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(n.time))
            addView(LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.TOP
                addView(avatar(ctx, n.title, 52, pulse = false))
                addView(LinearLayout(ctx).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(dp(ctx, 14), 0, 0, 0)
                    addView(LinearLayout(ctx).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                        addView(chip(ctx, n.app.uppercase(), ACCENT))
                        addView(label(ctx, "  $time", SUBTLE, 13f, bold = false))
                    })
                    addView(label(ctx, n.title, TEXT, 20f, bold = true).apply { setPadding(0, dp(ctx, 4), 0, 0) })
                    if (n.text.isNotBlank()) addView(label(ctx, n.text, TEXT, 17f, bold = false, lines = 2).apply { alpha = 0.9f })
                }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            })
            // Time left: a thin bar in the theme colours that shrinks to nothing.
            addView(View(ctx).apply {
                background = GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, intArrayOf(ACCENT, ACCENT2)).apply {
                    cornerRadius = dp(ctx, 2).toFloat()
                }
                pivotX = 0f
                ValueAnimator.ofFloat(1f, 0f).apply {
                    duration = millis
                    interpolator = LinearInterpolator()
                    addUpdateListener { scaleX = it.animatedValue as Float }
                    start()
                }
            }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(ctx, 4)).apply { topMargin = dp(ctx, 14) })
            setOnClickListener { removeMessage() }
        }

    /** The card: dark (or frosted white) glass with a hairline edge and a soft shadow. */
    private fun card(ctx: Context, padDp: Int = 20) = LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL
        val pad = dp(ctx, padDp)
        setPadding(pad, pad, pad, pad)
        background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(SURFACE_TOP, SURFACE_BOTTOM)).apply {
            cornerRadius = dp(ctx, 28).toFloat()
            setStroke(dp(ctx, 1), EDGE)
        }
        elevation = dp(ctx, 18).toFloat()
        // Slide down and fade in.
        alpha = 0f
        translationY = -dp(ctx, 24).toFloat()
        animate().alpha(1f).translationY(0f).setDuration(220).setInterpolator(DecelerateInterpolator()).start()
    }

    /** Round badge with the first letter of the name, in the theme gradient. */
    private fun avatar(ctx: Context, name: String, sizeDp: Int, pulse: Boolean): View {
        val size = dp(ctx, sizeDp)
        val frame = FrameLayout(ctx)
        if (pulse) {
            frame.addView(View(ctx).apply {
                background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(GO) }
                alpha = 0.35f
                ValueAnimator.ofFloat(1f, 1.35f).apply {
                    duration = 900
                    repeatMode = ValueAnimator.REVERSE
                    repeatCount = ValueAnimator.INFINITE
                    addUpdateListener { scaleX = it.animatedValue as Float; scaleY = scaleX; alpha = 1.35f - scaleX }
                }.stopWith(this).start()
            }, FrameLayout.LayoutParams(size, size))
        }
        frame.addView(TextView(ctx).apply {
            text = name.trim().firstOrNull { it.isLetterOrDigit() }?.uppercase() ?: "•"
            setTextColor(WHITE)
            textSize = sizeDp * 0.42f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            background = GradientDrawable(GradientDrawable.Orientation.TL_BR,
                intArrayOf(if (pulse) GO else ACCENT, if (pulse) GO_2 else ACCENT2)).apply { shape = GradientDrawable.OVAL }
        }, FrameLayout.LayoutParams(size, size))
        return frame
    }

    /** Small rounded tag: "WHATSAPP", "INCOMING · PHONE". */
    private fun chip(ctx: Context, text: String, color: Int) = TextView(ctx).apply {
        this.text = text
        setTextColor(color)
        textSize = 11f
        letterSpacing = 0.08f
        typeface = Typeface.DEFAULT_BOLD
        setPadding(dp(ctx, 10), dp(ctx, 3), dp(ctx, 10), dp(ctx, 3))
        background = GradientDrawable().apply {
            cornerRadius = dp(ctx, 50).toFloat()
            setColor((color and 0x00FFFFFF) or 0x26000000)
        }
        maxLines = 1
    }

    private fun pulse(v: View) {
        ValueAnimator.ofFloat(1f, 0.25f).apply {
            duration = 700
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ValueAnimator.INFINITE
            addUpdateListener { v.alpha = it.animatedValue as Float }
        }.stopWith(v).start()
    }

    /** Endless animations end when their card is taken off the screen. */
    private fun ValueAnimator.stopWith(v: View): ValueAnimator = also { anim ->
        v.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(view: View) = Unit
            override fun onViewDetachedFromWindow(view: View) = anim.cancel()
        })
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

    /** A big pill button with a gradient. Fires on touch-down with a tick: easy with gloves. */
    private fun pill(
        ctx: Context, text: String, color: Int, color2: Int, heightDp: Int, onClick: () -> Unit
    ) = TextView(ctx).apply {
        this.text = text
        setTextColor(WHITE)
        textSize = 21f
        typeface = Typeface.DEFAULT_BOLD
        gravity = Gravity.CENTER
        minHeight = dp(ctx, heightDp)
        background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(color, color2)).apply {
            cornerRadius = dp(ctx, heightDp).toFloat()
        }
        elevation = dp(ctx, 4).toFloat()
        isClickable = true
        setOnTouchListener { v, event ->
            if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                v.animate().scaleX(0.96f).scaleY(0.96f).setDuration(80).withEndAction {
                    v.animate().scaleX(1f).scaleY(1f).setDuration(120).start()
                }.start()
                onClick()
            }
            true
        }
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

    private fun add(ctx: Context, view: View, topMarginDp: Int): View? {
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
            // Top of the screen, just below the status bar, like a normal notification.
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = dp(ctx, topMarginDp)
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
