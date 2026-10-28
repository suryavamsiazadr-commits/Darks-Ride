package com.darksoul.darksride.phone

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import android.content.Context
import com.darksoul.darksride.link.LinkService
import org.json.JSONArray
import org.json.JSONObject

/** One message shown on the ride screen. */
data class RideNotif(
    val id: String,
    val app: String,
    val title: String,
    val text: String,
    val time: Long
)

/** Ride screen side of notifications: recent messages and the current pop-up. */
object RideNotifications {
    private const val KEEP = 8

    private val _items = MutableStateFlow<List<RideNotif>>(emptyList())
    val items: StateFlow<List<RideNotif>> = _items.asStateFlow()

    private val _popup = MutableStateFlow<RideNotif?>(null)
    val popup: StateFlow<RideNotif?> = _popup.asStateFlow()

    fun add(msg: JSONObject): RideNotif {
        val n = RideNotif(
            id = msg.optString("id"),
            app = msg.optString("app"),
            title = msg.optString("title"),
            text = msg.optString("text"),
            time = msg.optLong("time", System.currentTimeMillis())
        )
        _items.update { list -> (listOf(n) + list.filterNot { it.id == n.id }).take(KEEP) }
        _popup.value = n
        return n
    }

    /** An entry made on the ride screen itself, e.g. a missed call. */
    fun addLocal(app: String, title: String, text: String): RideNotif {
        val now = System.currentTimeMillis()
        return add(
            JSONObject().put("id", "local-$now").put("app", app)
                .put("title", title).put("text", text).put("time", now)
        )
    }

    /** Cleared on the main phone (read or swiped away). */
    fun remove(id: String) {
        _items.update { list -> list.filterNot { it.id == id } }
        if (_popup.value?.id == id) _popup.value = null
    }

    /** Tapped on the ride screen: gone here and on the main phone. */
    fun dismissEverywhere(c: Context, id: String) {
        remove(id)
        tellMainPhone(c, listOf(id))
    }

    /** "Clear" on the ride screen: all gone here and on the main phone. */
    fun clearEverywhere(c: Context) {
        val ids = _items.value.map { it.id }
        clear()
        tellMainPhone(c, ids)
    }

    private fun tellMainPhone(c: Context, ids: List<String>) {
        val keys = ids.filterNot { it.startsWith("local-") } // missed calls etc. exist only here
        if (keys.isEmpty()) return
        LinkService.sendJson(c, JSONObject().put("t", "notif_clear").put("ids", JSONArray(keys)))
    }

    fun dismissPopup(id: String) {
        if (_popup.value?.id == id) _popup.value = null
    }

    fun clear() {
        _items.value = emptyList()
        _popup.value = null
    }
}
