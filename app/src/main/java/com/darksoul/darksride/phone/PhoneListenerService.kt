package com.darksoul.darksride.phone

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.darksoul.darksride.AppPrefs
import org.json.JSONObject

/**
 * Notification access for the main phone.
 *
 * - Step 4: having this access lets Dark's Ride see and control other apps' music.
 * - Step 5: forwards WhatsApp / SMS (and optionally other) notifications to the bike,
 *   and tells the bike when one is cleared on the phone.
 */
class PhoneListenerService : NotificationListenerService() {

    private val handler = Handler(Looper.getMainLooper())

    /** Notification key -> what was last sent, so repeated updates are not re-sent. */
    private val lastSent = LinkedHashMap<String, String>()

    override fun onListenerConnected() {
        instance = this
    }

    override fun onListenerDisconnected() {
        if (instance === this) instance = null
    }

    override fun onDestroy() {
        if (instance === this) instance = null
        super.onDestroy()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn != null) forward(sbn)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        if (sbn == null) return
        if (lastSent.remove(sbn.key) != null) {
            NotifyBridge.send(JSONObject().put("t", "notif_gone").put("id", sbn.key))
        }
    }

    /** After the bike connects: send the latest few notifications still on the phone. */
    private fun resendActive() {
        lastSent.clear()
        val active = runCatching { activeNotifications }.getOrNull() ?: return
        active.sortedBy { it.postTime }.takeLast(RESEND_MAX).forEach { forward(it) }
    }

    private fun forward(sbn: StatusBarNotification) {
        if (sbn.packageName == packageName || sbn.isOngoing) return
        val n = sbn.notification ?: return
        if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) return
        if (!NotifyFilter.allowed(this, NotifyFilter.groupOf(sbn.packageName))) return

        // Chat apps use "messaging style": read the newest message and who sent it.
        val style = NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(n)
        val latest = style?.messages?.lastOrNull()
        val extras = n.extras
        val title = (style?.conversationTitle ?: extras.getCharSequence(Notification.EXTRA_TITLE))
            ?.toString().orEmpty()
        var text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
        if (latest != null) {
            val body = latest.text?.toString().orEmpty()
            val sender = latest.person?.name?.toString()
            text = if (style?.isGroupConversation == true && !sender.isNullOrEmpty()) "$sender: $body"
            else body
        }
        if (title.isEmpty() && text.isEmpty()) return

        val fingerprint = "$title|$text"
        if (lastSent[sbn.key] == fingerprint) return
        lastSent[sbn.key] = fingerprint
        while (lastSent.size > 50) lastSent.remove(lastSent.keys.first())

        val showText = AppPrefs.flag(this, AppPrefs.NOTIFY_SHOW_TEXT)
        NotifyBridge.send(
            JSONObject().put("t", "notif")
                .put("id", sbn.key)
                .put("app", appLabel(sbn.packageName))
                .put("title", title)
                .put("text", if (showText) text else "New message")
                .put("time", sbn.postTime)
        )
    }

    private fun appLabel(pkg: String): String = when (NotifyFilter.groupOf(pkg)) {
        NotifyGroup.WHATSAPP -> "WhatsApp"
        NotifyGroup.SMS -> "SMS"
        NotifyGroup.OTHER -> try {
            packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
        } catch (e: PackageManager.NameNotFoundException) {
            pkg
        }
    }

    companion object {
        private const val RESEND_MAX = 5

        @Volatile private var instance: PhoneListenerService? = null

        fun component(c: Context) = ComponentName(c, PhoneListenerService::class.java)

        fun hasAccess(c: Context): Boolean =
            NotificationManagerCompat.getEnabledListenerPackages(c).contains(c.packageName)

        /** Off switch: ask Android to stop running this listener until resume(). */
        fun pause() {
            val service = instance ?: return
            service.handler.post { runCatching { service.requestUnbind() } }
        }

        /** App opened again: bring the listener back (only if access is granted). */
        fun resume(c: Context) {
            if (hasAccess(c)) runCatching { NotificationListenerService.requestRebind(component(c)) }
        }

        /** Called by the link when the bike connects. Safe from any thread. */
        fun resendToBike() {
            val service = instance ?: return
            service.handler.post { service.resendActive() }
        }
    }
}
