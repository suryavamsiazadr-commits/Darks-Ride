package com.darksoul.darksride.phone

import android.content.Context
import com.darksoul.darksride.AppPrefs
import org.json.JSONObject

/** Hands notifications from the listener to the running phone link. */
object NotifyBridge {
    /** Set by the link service on the main phone while it runs. */
    @Volatile var sink: ((JSONObject) -> Unit)? = null

    fun send(msg: JSONObject) {
        sink?.invoke(msg)
    }
}

enum class NotifyGroup { WHATSAPP, SMS, OTHER }

/** Decides which apps' notifications reach the bike. */
object NotifyFilter {
    private val WHATSAPP = setOf("com.whatsapp", "com.whatsapp.w4b")
    private val SMS = setOf(
        "com.samsung.android.messaging",
        "com.google.android.apps.messaging",
        "com.android.mms"
    )

    fun groupOf(pkg: String): NotifyGroup = when (pkg) {
        in WHATSAPP -> NotifyGroup.WHATSAPP
        in SMS -> NotifyGroup.SMS
        else -> NotifyGroup.OTHER
    }

    fun allowed(c: Context, group: NotifyGroup): Boolean = when (group) {
        NotifyGroup.WHATSAPP -> AppPrefs.flag(c, AppPrefs.NOTIFY_WHATSAPP)
        NotifyGroup.SMS -> AppPrefs.flag(c, AppPrefs.NOTIFY_SMS)
        NotifyGroup.OTHER -> AppPrefs.flag(c, AppPrefs.NOTIFY_OTHER)
    }
}
